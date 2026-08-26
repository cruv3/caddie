package com.caddie.model.gateway

import com.caddie.agent.core.AgentMessage
import com.caddie.agent.core.ActionAttemptJournal
import com.caddie.agent.core.ActionDispatchClaim
import com.caddie.agent.core.ModelDelta
import com.caddie.agent.core.ModelRequest
import com.caddie.agent.core.RunRecord
import com.caddie.agent.core.RunId
import com.caddie.executor.accessibility.ActionOutcome
import com.caddie.executor.accessibility.ActionPerformer
import com.caddie.executor.accessibility.ExecutionGateway
import com.caddie.executor.accessibility.RequestedAction
import com.caddie.executor.accessibility.SemanticTarget
import com.caddie.executor.accessibility.UiObservation
import com.caddie.executor.accessibility.VerifiedActionExecutor
import com.caddie.tool.android.AndroidToolRegistry
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.test.runTest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.OkHttpClient
import okhttp3.Request
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

class GatewayHomeserverSmokeTest {
    @Test
    fun `configured gateway streams a synthetic completion`() =
        runTest {
            assumeTrue(
                System.getenv("CADDIE_GATEWAY_SMOKE") ==
                    "1",
            )
            val baseUrl = checkNotNull(System.getenv("CADDIE_GATEWAY_BASE_URL")) {
                "CADDIE_GATEWAY_BASE_URL is required for a live gateway smoke test"
            }
            val modelId = checkNotNull(System.getenv("CADDIE_GATEWAY_MODEL_ID")) {
                "CADDIE_GATEWAY_MODEL_ID is required for a live gateway smoke test"
            }
            val http = OkHttpClient()
            val modelsRequest =
                Request
                    .Builder()
                    .url("$baseUrl/v1/models")
                    .get()
                    .build()
            val modelIds =
                http
                    .newCall(modelsRequest)
                    .execute()
                    .use { response ->
                        check(response.isSuccessful) {
                            "models probe failed with " +
                                "HTTP ${response.code}"
                        }
                        val body =
                            checkNotNull(
                                response.body,
                            ).string()
                        Json
                            .parseToJsonElement(body)
                            .jsonObject
                            .getValue("data")
                            .jsonArray
                            .map {
                                it
                                    .jsonObject
                                    .getValue("id")
                                    .jsonPrimitive
                                    .content
                            }
                    }
            assertTrue(modelId in modelIds)

            val deltas =
                GatewayModelClient(
                    GatewayConfiguration(
                        baseUrl,
                        ModelProfile(
                            "study-local",
                            modelId,
                        ),
                        retryPolicy =
                            RetryPolicy(
                                maxAttempts = 1,
                            ),
                    ),
                    http,
                ).stream(
                    ModelRequest(
                        RunId("synthetic-smoke"),
                        listOf(
                            AgentMessage(
                                AgentMessage.Role.USER,
                                "Reply with exactly: " +
                                    "CADDIE_OK",
                            ),
                        ),
                        emptyList(),
                    ),
                ).toList()

            assertTrue(
                deltas.any {
                    it is ModelDelta.Text
                },
            )
            assertTrue(
                deltas.last() is ModelDelta.Completed,
            )
        }

    @Test
    fun `configured model accepts the complete native Android tool catalog`() = runTest {
        assumeTrue(System.getenv("CADDIE_GATEWAY_SMOKE") == "1")
        val baseUrl = checkNotNull(System.getenv("CADDIE_GATEWAY_BASE_URL")) {
            "CADDIE_GATEWAY_BASE_URL is required for a live gateway smoke test"
        }
        val modelId = checkNotNull(System.getenv("CADDIE_GATEWAY_MODEL_ID")) {
            "CADDIE_GATEWAY_MODEL_ID is required for a live gateway smoke test"
        }
        val gateway = object : ExecutionGateway {
            override suspend fun observe(): UiObservation = error("not executed")
            override suspend fun performSemantic(
                target: SemanticTarget,
                action: RequestedAction,
            ): ActionOutcome = error("not executed")
        }
        val journal = object : ActionAttemptJournal {
            override suspend fun dispatched(record: RunRecord.ActionDispatched) =
                ActionDispatchClaim.CLAIMED
            override suspend fun executed(record: RunRecord.ActionExecuted) = Unit
            override suspend fun terminal(record: RunRecord.ActionTerminal) = Unit
        }
        val tools = AndroidToolRegistry(
            gateway,
            VerifiedActionExecutor(
                actionAttemptJournal = journal,
                actionPerformer = ActionPerformer { _, _ -> ActionOutcome.ActionRejected },
            ),
        ).definitions()
        val deltas = GatewayModelClient(
            GatewayConfiguration(
                baseUrl,
                ModelProfile("normal", modelId),
                retryPolicy = RetryPolicy(maxAttempts = 1),
                thinkingEnabled = false,
            ),
        ).stream(
            ModelRequest(
                RunId("native-tools-smoke"),
                listOf(
                    AgentMessage(
                        AgentMessage.Role.SYSTEM,
                        "You are Caddie, an Android assistant. Use only the provided tools and " +
                            "verify the current UI before proposing a semantic action.",
                    ),
                    AgentMessage(
                        AgentMessage.Role.USER,
                        "Reply exactly CADDIE_OK without using a tool.",
                    ),
                ),
                tools,
            ),
        ).toList()

        assertTrue(deltas.any { it is ModelDelta.Text })
        assertTrue(deltas.last() is ModelDelta.Completed)
    }
}
