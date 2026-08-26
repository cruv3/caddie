package com.caddie.agent.core

/** Lists the lifecycle states of an agent run. */
enum class RunState {
    CREATED,
    RUNNING,
    PAUSED_OVERSIGHT,
    PAUSED_NETWORK,
    PAUSED_RECOVERABLE,
    COMPLETED,
    ABORTED,
}

/** Lists events that can change an agent run state. */
enum class RunEvent {
    START,
    AWAIT_OVERSIGHT,
    NETWORK_UNAVAILABLE,
    PROCESS_RECOVERED,
    RESUME,
    COMPLETE,
    ABORT,
}

fun transition(state: RunState, event: RunEvent): RunState =
    when (state to event) {
        RunState.CREATED to RunEvent.START -> RunState.RUNNING
        RunState.RUNNING to RunEvent.AWAIT_OVERSIGHT -> RunState.PAUSED_OVERSIGHT
        RunState.RUNNING to RunEvent.NETWORK_UNAVAILABLE -> RunState.PAUSED_NETWORK
        RunState.RUNNING to RunEvent.PROCESS_RECOVERED -> RunState.PAUSED_RECOVERABLE
        RunState.PAUSED_OVERSIGHT to RunEvent.RESUME,
        RunState.PAUSED_NETWORK to RunEvent.RESUME,
        RunState.PAUSED_RECOVERABLE to RunEvent.RESUME,
        -> RunState.RUNNING
        RunState.CREATED to RunEvent.ABORT,
        RunState.RUNNING to RunEvent.ABORT,
        RunState.PAUSED_OVERSIGHT to RunEvent.ABORT,
        RunState.PAUSED_NETWORK to RunEvent.ABORT,
        RunState.PAUSED_RECOVERABLE to RunEvent.ABORT,
        -> RunState.ABORTED
        RunState.RUNNING to RunEvent.COMPLETE -> RunState.COMPLETED
        else -> error("Illegal run transition: $state + $event")
    }
