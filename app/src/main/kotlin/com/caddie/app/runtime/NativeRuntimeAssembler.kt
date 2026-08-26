package com.caddie.app.runtime

import android.util.Log
import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.AgentLoop
import com.caddie.agent.core.ModelClient
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.OversightPolicy
import com.caddie.agent.core.RequestFactory
import com.caddie.agent.core.SessionStore
import com.caddie.agent.core.ToolCallTransformer
import com.caddie.app.runtime.context.ShortTermConversationContext
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.SemanticActionExecutor
import com.caddie.executor.accessibility.VerifiedActionExecutor
import com.caddie.runtime.persistence.ProcessSessionStore
import com.caddie.tool.android.AndroidToolRegistry
import com.caddie.tool.interaction.UserInteractionToolRegistry
import com.caddie.tool.interaction.TerminalToolRegistry
import com.caddie.tool.mcp.client.McpManager
import com.caddie.tool.registry.CompositeToolRegistry
import com.caddie.tool.registry.DynamicToolRegistry

/** Wires the process-owned model, tools, persistence, and agent loop. */
class NativeRuntimeAssembler(
    private val model: ModelClient,
    private val store: SessionStore,
    private val actionJournal: ActionAttemptJournal,
    private val mcp: McpManager,
    private val oversight: OversightPolicy,
    private val requestFactory: RequestFactory,
    private val normalRequestFactoryProvider: suspend (String, RequestFactory) -> RequestFactory =
        { _, delegate -> delegate },
    private val beforeFirstRun: suspend () -> Unit = {},
    private val shortTermContext: ShortTermConversationContext =
        ShortTermConversationContext(),
    private val closeAction: suspend () -> Unit = {},
) {
    fun create(gateway: ExecutionGateway): NativeRuntimeHost {
        val processStore = ProcessSessionStore(store)
        val interventionGate = NativeInterventionGate()
        val corrections = NativeRunCorrections()
        val stopSignal = NativeStopSignal()
        val androidExecutor = VerifiedActionExecutor(
            actionAttemptJournal = actionJournal,
            actionPerformer = SemanticActionExecutor(gateway),
            verificationErrorReporter = { error ->
                Log.w(
                    "VerifiedAction",
                    "Postcondition observation failed; polling continues",
                    error,
                )
            },
        )
        val normalAndroidTools = AndroidToolRegistry(
            gateway = gateway,
            executor = androidExecutor,
            resultReporter = { message -> Log.i("NativeAgent", message) },
            requireHumanNarration = true,
        )
        val studyAndroidTools = AndroidToolRegistry(
            gateway = gateway,
            executor = androidExecutor,
            resultReporter = { message -> Log.i("NativeAgent", message) },
        )
        lateinit var runner: NativeAgentRunner
        val normalTools = DynamicToolRegistry(
            CompositeToolRegistry(
                normalAndroidTools,
                UserInteractionToolRegistry { runId, question -> runner.askUser(runId, question) },
                TerminalToolRegistry(),
            ),
            mcp,
        )
        val studyTools = DynamicToolRegistry(studyAndroidTools, mcp)
        val normalCallBinder = NormalSemanticCallBinder(gateway)
        fun loopFor(
            activeOversight: OversightPolicy,
            activeRequestFactory: RequestFactory,
            transformer: ToolCallTransformer = ToolCallTransformer.Identity,
            activeTools: com.caddie.agent.core.ToolRegistry = normalTools,
            callPreprocessor: suspend (ModelDelta.ToolCall) -> ModelDelta.ToolCall = { it },
            requireTerminalCall: Boolean = false,
            publishNormalActions: Boolean = false,
        ) =
            AgentLoop(
                model = model,
                tools = activeTools,
                oversight = activeOversight,
                store = processStore,
                requestFactory = CorrectionAwareRequestFactory(
                    activeRequestFactory,
                    corrections,
                    transformer::onParticipantCorrection,
                ),
                transformer = transformer,
                callPreprocessor = callPreprocessor,
                failureReporter = { message -> Log.w("NativeAgent", message) },
                beginTurn = { interventionGate.awaitReleasedRevision() },
                isTurnCurrent = { runId, revision ->
                    !stopSignal.isRequested(runId) &&
                        !corrections.hasPending(runId) &&
                        interventionGate.revision() == revision
                },
                beforeToolDispatch = { runId ->
                    interventionGate.awaitRelease()
                    !stopSignal.isRequested(runId) && !corrections.hasPending(runId)
                },
                onToolDispatch = { runId, call ->
                    if (publishNormalActions) {
                        NormalToolNarration.humanLabel(call)?.let { narration ->
                            runner.publishCurrentAction(runId, narration)
                        }
                    }
                },
                requireTerminalCall = requireTerminalCall,
            )
        val loop = loopFor(
            oversight,
            requestFactory,
            callPreprocessor = normalCallBinder::bind,
            requireTerminalCall = true,
            publishNormalActions = true,
        )
        runner = NativeAgentRunner(
            store = processStore,
            step = loop::step,
            interventionGate = interventionGate,
            corrections = corrections,
            stopSignal = stopSignal,
        )
        return NativeRuntimeHost(
            runner = runner,
            normalStepForTask = { task ->
                val taskFactory = normalRequestFactoryProvider(task, requestFactory)
                loopFor(
                    oversight,
                    shortTermContext.decorate(taskFactory),
                    callPreprocessor = normalCallBinder::bind,
                    requireTerminalCall = true,
                    publishNormalActions = true,
                )::step
            },
            profileStep = { profile ->
                val profileLoop = loopFor(
                    profile.oversight,
                    profile.requestFactory,
                    profile.transformer,
                    studyTools,
                )
                profileLoop::step
            },
            afterNormalRun = shortTermContext::record,
            clearNormalContextAction = shortTermContext::clear,
            beforeFirstRun = beforeFirstRun,
            closeAction = {
                shortTermContext.clear()
                try {
                    mcp.close()
                } finally {
                    closeAction()
                }
            },
        )
    }
}
