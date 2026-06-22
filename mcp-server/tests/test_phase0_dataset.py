from pathlib import Path
import yaml

DATASET = Path(__file__).resolve().parents[1] / "experiments" / "phase0_dataset.yaml"
VALID_KINDS = {"paraphrase", "trigger_exact", "negative", "inverse", "crosslang"}


def test_dataset_well_formed():
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    items = data["items"]
    assert len(items) >= 24
    for it in items:
        assert set(it) == {"query", "expected_id", "kind", "lang", "split"}
        assert it["kind"] in VALID_KINDS
        assert it["lang"] in {"de", "en"}
        assert it["split"] in {"calibration", "test"}
        assert it["query"].strip()


def test_both_splits_cover_all_kinds():
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    for split in ("calibration", "test"):
        kinds = {it["kind"] for it in data["items"] if it["split"] == split}
        # jeder Split muss positive, negative UND inverse Fälle enthalten
        assert {"paraphrase", "negative", "inverse"} <= kinds, f"{split} unvollständig"


def test_expected_ids_resolve_to_real_skills():
    from caddie.skills import SkillLibrary
    lib = SkillLibrary.load(Path(__file__).resolve().parents[1] / "skills")
    ids = {s.id for s in lib.all()}
    data = yaml.safe_load(DATASET.read_text(encoding="utf-8"))
    for it in data["items"]:
        if it["expected_id"] != "none":
            assert it["expected_id"] in ids, f"unknown skill id {it['expected_id']}"
