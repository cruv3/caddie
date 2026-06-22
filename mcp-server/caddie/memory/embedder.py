from __future__ import annotations

import numpy as np


class Embedder:
    """Local sentence embedder. `encode_fn` is injectable for tests so the real
    model is never loaded in unit tests."""

    def __init__(self, model_name: str = "paraphrase-multilingual-MiniLM-L12-v2",
                 encode_fn=None) -> None:
        self._model_name = model_name
        self._encode_fn = encode_fn
        self._model = None

    def _ensure_model(self):
        if self._model is None:
            from sentence_transformers import SentenceTransformer
            self._model = SentenceTransformer(self._model_name)
        return self._model

    def encode(self, texts: list[str]) -> np.ndarray:
        if self._encode_fn is not None:
            vecs = np.asarray(self._encode_fn(texts), dtype="float32")
        else:
            model = self._ensure_model()
            vecs = np.asarray(model.encode(texts), dtype="float32")
        if vecs.ndim == 1:
            vecs = vecs[None, :]
        norms = np.linalg.norm(vecs, axis=1, keepdims=True)
        norms[norms == 0] = 1.0
        return (vecs / norms).astype("float32")
