from experiments.phase0_retrieval_ab import metrics_from_ranked


def test_metrics_correctness_known_rankings():
    # 4 Items, vorab bekannte Ranglisten (k=3) -> exakte Soll-Metriken
    items = [
        {"query": "a", "expected_id": "X", "kind": "paraphrase", "lang": "de"},   # top1 richtig
        {"query": "b", "expected_id": "Y", "kind": "paraphrase", "lang": "de"},   # Y auf Rang 2
        {"query": "c", "expected_id": "none", "kind": "negative", "lang": "de"},  # soll leer
        {"query": "d", "expected_id": "Z", "kind": "inverse", "lang": "de"},      # falscher top1
    ]
    ranked = [["X"], ["W", "Y"], ["Q"], ["W"]]   # was die Methode lieferte
    m = metrics_from_ranked(items, ranked, k=3)
    # positives = X,Y,Z (3 Stück)
    assert m["top1_accuracy"] == 1 / 3                # nur X top1 korrekt
    assert m["recall_at_k"] == 2 / 3                  # X und Y in Top-k, Z nicht
    assert abs(m["mrr"] - ((1.0 + 0.5 + 0.0) / 3)) < 1e-9
    assert m["abstention_accuracy"] == 0.0            # negatives: c lieferte ["Q"] -> nicht leer
    assert m["false_injection_rate"] == 1.0           # 1/1 negatives hat etwas geliefert
    assert m["inverse_correct_rate"] == 0.0           # d top1=W != Z


def test_metrics_abstention_when_empty():
    items = [{"query": "c", "expected_id": "none", "kind": "negative", "lang": "de"}]
    m = metrics_from_ranked(items, [[]], k=3)
    assert m["abstention_accuracy"] == 1.0
    assert m["false_injection_rate"] == 0.0
