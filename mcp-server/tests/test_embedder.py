import numpy as np
from caddie.memory.embedder import Embedder


def test_encode_uses_injected_fn_and_normalizes():
    # injizierte Funktion: 2D-Vektoren, NICHT normalisiert
    def fake(texts):
        return np.array([[3.0, 4.0]] * len(texts), dtype="float32")  # norm 5
    emb = Embedder(encode_fn=fake)
    out = emb.encode(["a", "b"])
    assert out.shape == (2, 2)
    assert out.dtype == np.float32
    # L2-normalisiert -> Norm ~1
    norms = np.linalg.norm(out, axis=1)
    assert np.allclose(norms, 1.0, atol=1e-5)
