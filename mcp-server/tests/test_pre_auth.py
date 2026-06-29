from caddie.agent.pre_auth import PreAuth

def test_one_shot_consume():
    p = PreAuth(description="send to papa")
    assert p.available() is True
    p.consume()
    assert p.available() is False
