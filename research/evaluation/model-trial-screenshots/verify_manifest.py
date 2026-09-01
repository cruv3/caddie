"""Verify the retained model-trial screenshot evidence package."""

from __future__ import annotations

import binascii
import csv
import hashlib
import json
import struct
import zlib
from collections import Counter
from pathlib import Path


PACKAGE_DIR = Path(__file__).resolve().parent
REPOSITORY_ROOT = PACKAGE_DIR.parents[2]
MANIFEST_PATH = PACKAGE_DIR / "manifest.csv"

SELF_HOSTED_MODELS = (
    "qwen/qwen3.6-35b-a3b",
    "qwen/qwen3-vl-8b",
    "qwen/qwen3-8b",
    "google/gemma-4-e4b",
    "gemma-4-e2b-it",
    "pixtral-12b",
)
TASKS = (
    "dark_mode_on",
    "dark_mode_off",
    "bluetooth_toggle_on",
    "brightness_set_50",
    "timer_set_5min",
    "pizza_search",
)
RUNS = ("0", "1")
PNG_SIGNATURE = b"\x89PNG\r\n\x1a\n"


def sha256(path: Path) -> str:
    digest = hashlib.sha256()
    with path.open("rb") as stream:
        for chunk in iter(lambda: stream.read(1024 * 1024), b""):
            digest.update(chunk)
    return digest.hexdigest()


def expected_slots() -> set[tuple[str, str, str, str]]:
    slots = {
        ("self-hosted", model, task, run)
        for model in SELF_HOSTED_MODELS
        for task in TASKS
        for run in RUNS
    }
    slots.update(
        ("claude-reference", "claude-opus-4.7", task, run)
        for task in TASKS
        for run in RUNS
    )
    return slots


def verify_png(path: Path) -> str | None:
    """Validate PNG chunks and decompress non-interlaced image data."""
    data = path.read_bytes()
    if not data.startswith(PNG_SIGNATURE):
        return "invalid PNG signature"

    offset = len(PNG_SIGNATURE)
    ihdr: tuple[int, int, int, int, int, int, int] | None = None
    image_data: list[bytes] = []
    saw_iend = False

    while offset < len(data):
        if offset + 12 > len(data):
            return "truncated PNG chunk header"
        length = struct.unpack(">I", data[offset : offset + 4])[0]
        chunk_type = data[offset + 4 : offset + 8]
        chunk_end = offset + 12 + length
        if chunk_end > len(data):
            return "truncated PNG chunk data"
        chunk_data = data[offset + 8 : offset + 8 + length]
        stored_crc = struct.unpack(">I", data[offset + 8 + length : chunk_end])[0]
        calculated_crc = binascii.crc32(chunk_type)
        calculated_crc = binascii.crc32(chunk_data, calculated_crc) & 0xFFFFFFFF
        if calculated_crc != stored_crc:
            return f"CRC mismatch in {chunk_type.decode('ascii', errors='replace')}"

        if chunk_type == b"IHDR":
            if ihdr is not None or length != 13:
                return "invalid IHDR chunk"
            ihdr = struct.unpack(">IIBBBBB", chunk_data)
        elif chunk_type == b"IDAT":
            image_data.append(chunk_data)
        elif chunk_type == b"IEND":
            if length != 0:
                return "invalid IEND chunk"
            saw_iend = True
            offset = chunk_end
            break
        offset = chunk_end

    if ihdr is None or not image_data or not saw_iend:
        return "missing required PNG chunk"
    if offset != len(data):
        return "unexpected data after IEND"

    width, height, bit_depth, color_type, compression, filter_method, interlace = ihdr
    if width <= 0 or height <= 0:
        return "invalid image dimensions"
    if compression != 0 or filter_method != 0 or interlace != 0:
        return "unsupported PNG encoding"

    allowed_depths = {
        0: {1, 2, 4, 8, 16},
        2: {8, 16},
        3: {1, 2, 4, 8},
        4: {8, 16},
        6: {8, 16},
    }
    channels = {0: 1, 2: 3, 3: 1, 4: 2, 6: 4}
    if color_type not in allowed_depths or bit_depth not in allowed_depths[color_type]:
        return "unsupported color type or bit depth"

    try:
        decoded = zlib.decompress(b"".join(image_data))
    except zlib.error as exc:
        return f"IDAT decompression failed: {exc}"
    row_bytes = (width * channels[color_type] * bit_depth + 7) // 8
    expected_size = height * (row_bytes + 1)
    if len(decoded) != expected_size:
        return f"unexpected decoded size {len(decoded)} (expected {expected_size})"
    return None


def load_result(path: Path, row: dict[str, str]) -> list[str]:
    failures: list[str] = []
    try:
        result = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, UnicodeError, json.JSONDecodeError) as exc:
        return [f"invalid result JSON {path}: {exc}"]

    recorded_model = (
        "claude-opus-4-7"
        if row["cohort"] == "claude-reference"
        else row["model"]
    )
    expected_fields = {
        "model": recorded_model,
        "task": row["task"],
        "run": int(row["run"]),
    }
    for field, expected in expected_fields.items():
        if result.get(field) != expected:
            failures.append(
                f"result mismatch {path}: {field}={result.get(field)!r}, "
                f"expected {expected!r}"
            )

    full_trial_id = result.get("trial_id")
    if full_trial_id != path.stem:
        failures.append(
            f"result trial ID mismatch {path}: {full_trial_id!r} != {path.stem!r}"
        )
    if not path.stem.startswith(f"{row['trial_id']}__"):
        failures.append(
            f"manifest timestamp prefix does not match result trial ID: {path}"
        )
    return failures


def main() -> None:
    with MANIFEST_PATH.open(newline="", encoding="utf-8-sig") as stream:
        rows = list(csv.DictReader(stream))

    actual_slots = [
        (row["cohort"], row["model"], row["task"], row["run"])
        for row in rows
    ]
    duplicate_slots = sorted(
        slot for slot, count in Counter(actual_slots).items() if count > 1
    )
    if duplicate_slots:
        raise SystemExit(f"Duplicate manifest slots: {duplicate_slots}")
    expected = expected_slots()
    if set(actual_slots) != expected:
        missing = sorted(expected - set(actual_slots))
        unexpected = sorted(set(actual_slots) - expected)
        raise SystemExit(
            f"Manifest slot mismatch; missing={missing}, unexpected={unexpected}"
        )

    trial_prefixes = [row["trial_id"] for row in rows if row["trial_id"]]
    duplicate_prefixes = sorted(
        prefix for prefix, count in Counter(trial_prefixes).items() if count > 1
    )
    if duplicate_prefixes:
        raise SystemExit(f"Duplicate trial timestamp prefixes: {duplicate_prefixes}")

    status_counts = Counter(row["status"] for row in rows)
    expected_status_counts = Counter(
        {"available": 79, "capture_failed": 2, "not_retained": 3}
    )
    if status_counts != expected_status_counts:
        raise SystemExit(f"Unexpected status counts: {dict(status_counts)}")

    cohort_counts = Counter(
        row["cohort"] for row in rows if row["status"] == "available"
    )
    if cohort_counts != {"self-hosted": 67, "claude-reference": 12}:
        raise SystemExit(f"Unexpected available cohort counts: {dict(cohort_counts)}")

    expected_capture_failures = {
        ("qwen/qwen3-vl-8b", "dark_mode_off", "0"),
        ("pixtral-12b", "dark_mode_on", "1"),
    }
    capture_failures = {
        (row["model"], row["task"], row["run"])
        for row in rows
        if row["status"] == "capture_failed"
    }
    if capture_failures != expected_capture_failures:
        raise SystemExit(f"Unexpected capture failures: {sorted(capture_failures)}")

    expected_missing = {
        ("gemma-4-e2b-it", "dark_mode_on", "1"),
        ("gemma-4-e2b-it", "dark_mode_off", "0"),
        ("gemma-4-e2b-it", "dark_mode_off", "1"),
    }
    missing_slots = {
        (row["model"], row["task"], row["run"])
        for row in rows
        if row["status"] == "not_retained"
    }
    if missing_slots != expected_missing:
        raise SystemExit(f"Unexpected missing slots: {sorted(missing_slots)}")

    failures: list[str] = []
    for row in rows:
        status = row["status"]
        screenshot_path = row["screenshot_path"]
        screenshot_hash = row["screenshot_sha256"]
        capture_error_path = row["capture_error_path"]
        result_path = row["result_json_path"]

        if status == "not_retained":
            if any(
                (
                    row["trial_id"],
                    screenshot_path,
                    screenshot_hash,
                    capture_error_path,
                    result_path,
                )
            ):
                failures.append(
                    f"not_retained slot unexpectedly references evidence: "
                    f"{row['model']} {row['task']} r{row['run']}"
                )
            continue

        if not row["trial_id"] or not result_path:
            failures.append(
                f"retained slot lacks trial prefix or result path: "
                f"{row['model']} {row['task']} r{row['run']}"
            )
            continue

        result_json = REPOSITORY_ROOT / result_path
        if not result_json.is_file():
            failures.append(f"missing result JSON: {result_json}")
        else:
            failures.extend(load_result(result_json, row))

        if status == "capture_failed":
            if screenshot_path or screenshot_hash or not capture_error_path:
                failures.append(
                    f"invalid capture_failed paths: "
                    f"{row['model']} {row['task']} r{row['run']}"
                )
                continue
            capture_error = REPOSITORY_ROOT / capture_error_path
            if not capture_error.is_file():
                failures.append(f"missing capture error: {capture_error}")
            else:
                try:
                    payload = json.loads(capture_error.read_text(encoding="utf-8"))
                except (OSError, UnicodeError, json.JSONDecodeError) as exc:
                    failures.append(f"invalid capture error JSON {capture_error}: {exc}")
                else:
                    if (
                        payload.get("ok") is not False
                        or payload.get("error") != "screenshot_failed"
                    ):
                        failures.append(f"unexpected capture error payload: {capture_error}")
                    if not capture_error.name.startswith(f"{result_json.stem}."):
                        failures.append(f"capture error trial ID mismatch: {capture_error}")
            continue

        if not screenshot_path or not screenshot_hash or capture_error_path:
            failures.append(
                f"invalid available paths: {row['model']} {row['task']} r{row['run']}"
            )
            continue
        screenshot = REPOSITORY_ROOT / screenshot_path
        if not screenshot.is_file():
            failures.append(f"missing screenshot: {screenshot}")
            continue
        if screenshot.stem != result_json.stem:
            failures.append(f"screenshot/result trial ID mismatch: {screenshot}")
        if sha256(screenshot) != screenshot_hash:
            failures.append(f"hash mismatch: {screenshot}")
        png_failure = verify_png(screenshot)
        if png_failure:
            failures.append(f"invalid PNG {screenshot}: {png_failure}")

    if failures:
        raise SystemExit("\n".join(failures))

    print(
        "MODEL-TRIAL EVIDENCE CHECK: PASS "
        "(84 exact unique slots; 79 hashed and decoded screenshots; "
        "2 failed captures; 3 explicitly not retained)"
    )


if __name__ == "__main__":
    main()
