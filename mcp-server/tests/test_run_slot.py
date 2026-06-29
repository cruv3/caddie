import threading
from caddie.agent.agent_loop import AgentLoop

class _Ctx:
    # minimal stand-in for ServerContext fields AgentLoop.__init__ reads
    pass

def _loop():
    # AgentLoop.__init__ touches context.events/backend + ToolDispatcher;
    # construct via __new__ and set just the slot to test acquisition in isolation.
    loop = AgentLoop.__new__(AgentLoop)
    loop._init_run_slot()
    return loop

def test_slot_is_exclusive():
    loop = _loop()
    assert loop.try_acquire_slot() is True
    assert loop.try_acquire_slot() is False   # second fails while held
    loop.release_slot()
    assert loop.try_acquire_slot() is True
