from __future__ import annotations

import hashlib
import importlib.util
import json
import subprocess
import sys
import tempfile
import unittest
from copy import deepcopy
from pathlib import Path


SCRIPT_PATH = Path(__file__).resolve().parents[1] / "review-memory-evaluation.py"
SPEC = importlib.util.spec_from_file_location("review_memory_evaluation", SCRIPT_PATH)
if SPEC is None or SPEC.loader is None:
    raise RuntimeError("无法加载记忆评审脚本")
reviewer = importlib.util.module_from_spec(SPEC)
sys.modules[SPEC.name] = reviewer
SPEC.loader.exec_module(reviewer)


def trial(
    condition: str,
    memory: dict[str, object],
    status: str = "PASS",
    origin: str = "REAL_PROVIDER",
) -> dict[str, object]:
    return {
        "condition": condition,
        "responseOrigin": origin,
        "contractStatus": status,
        "failures": [] if status == "PASS" else ["synthetic-failure"],
        "memoryContext": memory,
        "response": f"reply for {condition}",
        "publicOutput": f"public reply for {condition}",
        "toolTrace": [],
    }


def pair(
    pair_id: str,
    kind: str = "MEMORY_ABLATION",
    status: str = "PASS",
    origin: str = "REAL_PROVIDER",
) -> dict[str, object]:
    conditions = ["NO_MEMORY", "PROFILE_ONLY", "NOTES_ONLY", "FULL_MEMORY"]
    empty = {"revision": 0, "explanationStyle": None, "goal": None, "sessionNotes": []}
    if kind == "MEMORY_ABLATION":
        full = {
            "revision": 1,
            "explanationStyle": "EXAMPLES",
            "goal": "synthetic goal",
            "sessionNotes": [{"sessionKey": "synthetic-session", "note": "synthetic note"}],
        }
        memory_by_condition = {
            "NO_MEMORY": empty,
            "PROFILE_ONLY": {**empty, "revision": 1, "explanationStyle": "EXAMPLES", "goal": "synthetic goal"},
            "NOTES_ONLY": {**empty, "sessionNotes": full["sessionNotes"]},
            "FULL_MEMORY": full,
        }
    else:
        memory_by_condition = {
            "NO_MEMORY": empty,
            "PROFILE_ONLY": {**empty, "revision": 1},
            "NOTES_ONLY": empty,
            "FULL_MEMORY": {**empty, "revision": 1},
        }
    return {
        "pairId": pair_id,
        "caseId": pair_id.rsplit("/", 1)[0],
        "repetition": 1,
        "comparisonKind": kind,
        "activeFactors": ["PROFILE", "NOTES_WITH_SOURCE"] if kind == "MEMORY_ABLATION" else [],
        "contractStatus": status,
        "pairingFailures": [] if status == "PASS" else ["synthetic-pair-failure"],
        "manualCriteria": ["criterion one", "criterion two"],
        "trials": [trial(condition, memory_by_condition[condition], status, origin) for condition in conditions],
    }


def report(mode: str = "ONLINE", status: str = "PASS") -> dict[str, object]:
    origin = "REAL_PROVIDER" if mode == "ONLINE" else "SCRIPTED_FIXTURE"
    first = pair("sample-memory/1", status=status, origin=origin)
    control = pair("sample-deleted/1", kind="EMPTY_MEMORY_CONTROL", status=status, origin=origin)
    return {
        "schemaVersion": 1,
        "experimentId": "11111111-1111-1111-1111-111111111111",
        "mode": mode,
        "corpusHash": "a" * 64,
        "fixtureImplementationHash": "b" * 64,
        "contractStatus": status,
        "repetitions": 1,
        "plannedTrials": 8,
        "pairs": [first, control],
    }


def raw(value: dict[str, object]) -> bytes:
    return (json.dumps(value, ensure_ascii=False, separators=(",", ":")) + "\n").encode("utf-8")


def evidence(pair_index: int, condition: str) -> list[dict[str, str]]:
    trial_index = {"NO_MEMORY": 0, "PROFILE_ONLY": 1, "NOTES_ONLY": 2, "FULL_MEMORY": 3}[condition]
    return [{"condition": condition, "pointer": f"/pairs/{pair_index}/trials/{trial_index}/response"}]


def completed_review(report_bytes: bytes) -> dict[str, object]:
    review = reviewer.make_review(report_bytes, "reviewer-alias")
    review["reviewedAt"] = "2026-09-26T12:00:00+00:00"
    for pair_index, item in enumerate(review["pairs"]):
        for criterion in item["criteria"]:
            criterion.update({"verdict": "SATISFIED", "rationale": "checked",
                              "evidence": evidence(pair_index, "FULL_MEMORY")})
        for contrast in item["contrasts"]:
            comparator = contrast["comparator"]
            contrast.update(
                {
                    "verdict": "NO_CLEAR_DIFFERENCE",
                    "rationale": "same supported explanation",
                    "evidence": evidence(pair_index, "FULL_MEMORY") + evidence(pair_index, comparator),
                }
            )
    return review


class MemoryEvaluationReviewTest(unittest.TestCase):
    def test_template_binds_exact_report_and_generates_only_eligible_contrasts(self) -> None:
        content = raw(report())
        review = reviewer.make_review(content, "reviewer-alias")
        self.assertEqual(1, review["schemaVersion"])
        self.assertEqual(hashlib.sha256(content).hexdigest(), review["reportBinding"]["reportSha256"])
        self.assertEqual("11111111-1111-1111-1111-111111111111", review["reportBinding"]["experimentId"])
        self.assertIsNone(review["reviewedAt"])
        memory_pair, empty_control = review["pairs"]
        self.assertEqual(["NO_MEMORY", "PROFILE_ONLY", "NOTES_ONLY"],
                         [contrast["comparator"] for contrast in memory_pair["contrasts"]])
        self.assertTrue(all(item["verdict"] == "PENDING" for item in memory_pair["criteria"]))
        self.assertEqual([], empty_control["contrasts"])

    def test_complete_online_review_summarizes_only_memory_ablation_contrasts(self) -> None:
        content = raw(report())
        summary = reviewer.summarize(content, completed_review(content), require_complete=True)
        self.assertEqual("COMPLETE", summary["reviewStatus"])
        self.assertEqual("HUMAN_REVIEWED", summary["comparisonStatus"])
        self.assertEqual(2, summary["totalPairs"])
        self.assertEqual(3, summary["eligibleContrasts"])
        self.assertEqual(3, summary["reviewedContrasts"])
        self.assertEqual([], summary["failedCriteria"])
        self.assertEqual(3, len(summary["contrasts"]))
        self.assertTrue(all(item["eligible"] for item in summary["contrasts"]))
        self.assertTrue(any(item["pairId"] == "sample-deleted/1" for item in summary["excludedPairs"]))
        self.assertNotIn("same supported explanation", json.dumps(summary, ensure_ascii=False))

    def test_binding_missing_or_cross_pair_evidence_is_rejected(self) -> None:
        content = raw(report())
        review = completed_review(content)
        review["reportBinding"]["reportSha256"] = "0" * 64
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

        review = completed_review(content)
        review["pairs"][0]["criteria"][0]["evidence"] = [
            {"condition": "FULL_MEMORY", "pointer": "/pairs/0/trials/3/privateInternalField"},
        ]
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

        review = completed_review(content)
        review["pairs"][0]["contrasts"][0]["evidence"] = [
            {"condition": "FULL_MEMORY", "pointer": "/pairs/1/trials/3/response"},
            {"condition": "NO_MEMORY", "pointer": "/pairs/0/trials/0/response"},
        ]
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

    def test_omissions_invalid_conclusions_and_incomplete_required_review_are_rejected(self) -> None:
        content = raw(report())
        review = reviewer.make_review(content, "reviewer-alias")
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review, require_complete=True)

        review = completed_review(content)
        review["pairs"][0]["contrasts"].pop()
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

        review = completed_review(content)
        review["pairs"][1] = deepcopy(review["pairs"][0])
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

        review = completed_review(content)
        review["pairs"][0]["criteria"][0]["verdict"] = "FAVORS_FULL_MEMORY"
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

        review = completed_review(content)
        review["reviewedAt"] = None
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

    def test_offline_failed_and_empty_control_cannot_create_benefit_contrasts(self) -> None:
        offline = raw(report(mode="OFFLINE"))
        offline_review = reviewer.make_review(offline, "reviewer-alias")
        self.assertTrue(all(not item["contrasts"] for item in offline_review["pairs"]))
        offline_review["reviewedAt"] = "2026-09-26T12:00:00+00:00"
        for pair_index, item in enumerate(offline_review["pairs"]):
            for criterion in item["criteria"]:
                criterion.update({"verdict": "SATISFIED", "rationale": "boundary",
                                  "evidence": evidence(pair_index, "FULL_MEMORY")})
        summary = reviewer.summarize(offline, offline_review, require_complete=True)
        self.assertEqual("NOT_EVALUATED", summary["comparisonStatus"])
        self.assertEqual(0, summary["eligibleContrasts"])
        self.assertEqual(0, summary["reviewedContrasts"])

        failed = raw(report(status="FAIL"))
        failed_review = reviewer.make_review(failed, "reviewer-alias")
        self.assertTrue(all(not item["contrasts"] for item in failed_review["pairs"]))
        self.assertTrue(any(item["reasons"] for item in reviewer.summarize(failed, failed_review)["excludedPairs"]))

        online = raw(report())
        control_review = completed_review(online)
        control_review["pairs"][1]["contrasts"] = [{
            "comparator": "NO_MEMORY", "verdict": "FAVORS_FULL_MEMORY", "rationale": "invalid",
            "evidence": evidence(1, "FULL_MEMORY") + evidence(1, "NO_MEMORY"),
        }]
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(online, control_review)

    def test_cli_refuses_to_overwrite_and_never_echoes_report_content_in_errors(self) -> None:
        private_marker = "PRIVATE_MODEL_RESPONSE_DO_NOT_ECHO"
        value = report()
        value["pairs"][0]["trials"][0]["response"] = private_marker
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_path = root / "report.json"
            review_path = root / "review.json"
            report_path.write_bytes(raw(value))
            first = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), "init", str(report_path), str(review_path), "--reviewer", "alias"],
                text=True, capture_output=True, check=False,
            )
            self.assertEqual(0, first.returncode, first.stderr)
            second = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), "init", str(report_path), str(review_path), "--reviewer", "alias"],
                text=True, capture_output=True, check=False,
            )
            self.assertNotEqual(0, second.returncode)
            self.assertNotIn(private_marker, second.stdout + second.stderr)

            bad_review = json.loads(review_path.read_text(encoding="utf-8"))
            bad_review["reportBinding"]["experimentId"] = "mismatch"
            review_path.write_text(json.dumps(bad_review), encoding="utf-8")
            failed = subprocess.run(
                [sys.executable, str(SCRIPT_PATH), "summarize", str(report_path), str(review_path)],
                text=True, capture_output=True, check=False,
            )
            self.assertNotEqual(0, failed.returncode)
            self.assertNotIn(private_marker, failed.stdout + failed.stderr)

    def test_successful_trials_require_actual_outputs_before_human_comparison(self) -> None:
        for field, value in [("response", None), ("response", ""), ("response", "  "), ("publicOutput", "")]:
            with self.subTest(field=field, value=value):
                source = report()
                source["pairs"][0]["trials"][0][field] = value
                with self.assertRaises(reviewer.ReviewError):
                    reviewer.make_review(raw(source), "alias")

    def test_report_byte_changes_and_changed_provenance_invalidate_old_review(self) -> None:
        content = raw(report())
        review = completed_review(content)
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content + b"\n", review)
        for field in ("corpusHash", "fixtureImplementationHash", "experimentId"):
            with self.subTest(field=field):
                edited = deepcopy(review)
                edited["reportBinding"][field] = "changed"
                with self.assertRaises(reviewer.ReviewError):
                    reviewer.summarize(content, edited)

    def test_pending_and_failed_criteria_remain_visible_in_partial_summary(self) -> None:
        content = raw(report())
        review = completed_review(content)
        review["pairs"][0]["criteria"][0]["verdict"] = "NOT_SATISFIED"
        review["pairs"][0]["contrasts"][0]["verdict"] = "PENDING"
        summary = reviewer.summarize(content, review)
        self.assertEqual("PARTIAL", summary["reviewStatus"])
        self.assertEqual("PARTIALLY_REVIEWED", summary["comparisonStatus"])
        self.assertEqual(3, summary["eligibleContrasts"])
        self.assertEqual(2, summary["reviewedContrasts"])
        self.assertEqual(1, summary["pendingAssessments"])
        self.assertEqual([{"pairId": "sample-memory/1", "criterionIndex": 0}], summary["failedCriteria"])

    def test_rotated_trial_order_requires_actual_pointers_and_both_outputs(self) -> None:
        source = report()
        source["pairs"][0]["trials"].reverse()
        content = raw(source)
        review = completed_review(content)
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)
        for item in review["pairs"][0]["criteria"] + review["pairs"][0]["contrasts"]:
            for citation in item["evidence"]:
                index = next(i for i, trial_data in enumerate(source["pairs"][0]["trials"])
                             if trial_data["condition"] == citation["condition"])
                citation["pointer"] = f"/pairs/0/trials/{index}/response"
        self.assertEqual("COMPLETE", reviewer.summarize(content, review, True)["reviewStatus"])
        review["pairs"][0]["contrasts"][0]["evidence"][0]["pointer"] = "/pairs/0/trials/0/memoryContext"
        with self.assertRaises(reviewer.ReviewError):
            reviewer.summarize(content, review)

    def test_report_contract_matrix_and_interventions_cannot_be_forged_by_labels(self) -> None:
        for mutation in ("status", "intervention", "duplicate", "matrix", "empty_pairs", "factor"):
            with self.subTest(mutation=mutation):
                source = report()
                if mutation == "status":
                    source["pairs"][0]["trials"][0]["failures"] = ["failed"]
                elif mutation == "intervention":
                    source["pairs"][0]["trials"][0]["memoryContext"]["goal"] = "leaked"
                elif mutation == "duplicate":
                    source["pairs"][1] = deepcopy(source["pairs"][0])
                elif mutation == "matrix":
                    source["repetitions"] = 2
                elif mutation == "empty_pairs":
                    source["pairs"] = []
                    source["plannedTrials"] = 0
                else:
                    source["pairs"][0]["activeFactors"] = []
                with self.assertRaises(reviewer.ReviewError):
                    reviewer.make_review(raw(source), "alias")

    def test_mixed_failures_are_excluded_without_hiding_successful_or_synthetic_pairs(self) -> None:
        source = report()
        source["pairs"].append(pair("sample-failure/1", status="FAIL"))
        source["pairs"].append(pair("sample-synthetic/1", origin="SCRIPTED_FIXTURE"))
        source["contractStatus"] = "FAIL"
        source["plannedTrials"] = 16
        content = raw(source)
        summary = reviewer.summarize(content, completed_review(content), True)
        self.assertEqual(4, summary["totalPairs"])
        self.assertEqual(3, summary["eligibleContrasts"])
        self.assertEqual(3, len(summary["excludedPairs"]))
        self.assertTrue(any("SYNTHETIC_RESPONSE" in row["reasons"] for row in summary["excludedPairs"]))

    def test_cli_completion_and_summary_creation_preserve_all_inputs(self) -> None:
        content = raw(report())
        with tempfile.TemporaryDirectory() as directory:
            root = Path(directory)
            report_path, review_path, summary_path = [root / name for name in ("report.json", "review.json", "summary.json")]
            report_path.write_bytes(content)
            review_path.write_bytes(raw(reviewer.make_review(content, "alias")))
            command = [sys.executable, str(SCRIPT_PATH), "summarize", str(report_path), str(review_path),
                       "--output", str(summary_path), "--require-complete"]
            pending = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(2, pending.returncode)
            self.assertFalse(summary_path.exists())
            completed = raw(completed_review(content))
            review_path.write_bytes(completed)
            result = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(0, result.returncode, result.stderr)
            original_summary = summary_path.read_bytes()
            again = subprocess.run(command, text=True, capture_output=True, check=False)
            self.assertEqual(2, again.returncode)
            self.assertEqual(content, report_path.read_bytes())
            self.assertEqual(completed, review_path.read_bytes())
            self.assertEqual(original_summary, summary_path.read_bytes())

    def test_duplicate_json_keys_and_invalid_json_fail_without_echoing_values(self) -> None:
        for content in (b'{"schemaVersion":1,"schemaVersion":1}', b'{"secret":"SYNTHETIC_MARKER",',
                        b'{"schemaVersion":NaN}'):
            with self.subTest(content=content):
                with self.assertRaises(reviewer.ReviewError) as raised:
                    reviewer.make_review(content, "alias")
                self.assertNotIn("SYNTHETIC_MARKER", str(raised.exception))


if __name__ == "__main__":
    unittest.main()
