#!/usr/bin/env python3
"""Bind human memory-ablation reviews to immutable local execution reports."""

from __future__ import annotations

import argparse
import copy
import hashlib
import json
import re
import sys
from datetime import datetime
from pathlib import Path
from typing import Any


CONDITIONS = ("NO_MEMORY", "PROFILE_ONLY", "NOTES_ONLY", "FULL_MEMORY")
CRITERION_VERDICTS = {"SATISFIED", "NOT_SATISFIED", "UNABLE_TO_JUDGE"}
CONTRAST_VERDICTS = {
    "FAVORS_FULL_MEMORY", "FAVORS_COMPARATOR", "NO_CLEAR_DIFFERENCE", "UNABLE_TO_JUDGE",
}
EVIDENCE_FIELDS = {"response", "publicOutput", "toolTrace", "memoryContext"}


class ReviewError(ValueError):
    pass


def require(condition: bool, rule: str) -> None:
    if not condition:
        raise ReviewError(rule)


def text(value: Any) -> bool:
    return isinstance(value, str) and bool(value.strip())


def unique_object(items: list[tuple[str, Any]]) -> dict[str, Any]:
    result: dict[str, Any] = {}
    for key, value in items:
        require(key not in result, "JSON contains duplicate keys")
        result[key] = value
    return result


def read_json(raw: bytes) -> dict[str, Any]:
    def invalid_constant(_: str) -> None:
        raise ReviewError("JSON contains a non-finite number")

    try:
        value = json.loads(raw.decode("utf-8"), object_pairs_hook=unique_object,
                           parse_constant=invalid_constant)
    except (UnicodeError, json.JSONDecodeError, RecursionError) as error:
        raise ReviewError("Invalid UTF-8 JSON document") from error
    require(isinstance(value, dict), "JSON root must be an object")
    return value


def strings(value: Any, nonempty: bool = False) -> bool:
    return isinstance(value, list) and (not nonempty or bool(value)) and all(text(item) for item in value)


def object_keys(value: Any, expected: set[str], rule: str) -> None:
    require(isinstance(value, dict) and set(value) == expected, rule)


def memory_factors(memory: dict[str, Any]) -> list[str]:
    factors = []
    if memory["explanationStyle"] is not None or memory["goal"] is not None:
        factors.append("PROFILE")
    if memory["sessionNotes"]:
        factors.append("NOTES_WITH_SOURCE")
    return factors


def validate_pair(pair: Any, repetitions: int) -> None:
    require(isinstance(pair, dict), "Report pair must be an object")
    require(text(pair.get("caseId")), "Report caseId is required")
    repeat = pair.get("repetition")
    require(type(repeat) is int and 1 <= repeat <= repetitions, "Report repetition is invalid")
    require(pair.get("pairId") == f"{pair['caseId']}/{repeat}", "Report pair identity is invalid")
    require(strings(pair.get("manualCriteria"), nonempty=True), "Report manualCriteria are required")
    require(strings(pair.get("pairingFailures")), "Report pairingFailures must be a list")
    trials = pair.get("trials")
    require(isinstance(trials, list) and len(trials) == 4, "Report must contain four trials per pair")
    by_condition = {}
    for trial in trials:
        require(isinstance(trial, dict), "Report trial must be an object")
        condition = trial.get("condition")
        require(isinstance(condition, str) and condition in CONDITIONS and condition not in by_condition,
                "Report conditions must be unique and complete")
        require(trial.get("contractStatus") in ("PASS", "FAIL") and strings(trial.get("failures")),
                "Report trial contract is invalid")
        require((trial["contractStatus"] == "PASS") == (not trial["failures"]),
                "Report trial status disagrees with failures")
        require(trial.get("responseOrigin") in ("REAL_PROVIDER", "SCRIPTED_FIXTURE"),
                "Report response origin is invalid")
        require(trial.get("response") is None or isinstance(trial.get("response"), str),
                "Report response must be text or null")
        require(isinstance(trial.get("publicOutput"), str) and isinstance(trial.get("toolTrace"), list),
                "Report publicOutput and toolTrace are required")
        if trial["contractStatus"] == "PASS":
            require(text(trial.get("response")) and text(trial["publicOutput"]),
                    "Successful trials require nonempty model and public outputs")
        memory = trial.get("memoryContext")
        object_keys(memory, {"revision", "explanationStyle", "goal", "sessionNotes"},
                    "Report memory context fields are invalid")
        require(type(memory["revision"]) is int and memory["revision"] >= 0,
                "Report memory revision is invalid")
        require(memory["explanationStyle"] in (None, "CONCISE", "EXAMPLES", "STEP_BY_STEP")
                and (memory["goal"] is None or text(memory["goal"]))
                and isinstance(memory["sessionNotes"], list)
                and all(isinstance(note, dict) for note in memory["sessionNotes"]),
                "Report memory context is invalid")
        by_condition[condition] = trial
    full = by_condition["FULL_MEMORY"]["memoryContext"]
    for condition, trial in by_condition.items():
        expected = copy.deepcopy(full)
        if condition in ("NO_MEMORY", "NOTES_ONLY"):
            expected.update(revision=0, explanationStyle=None, goal=None)
        if condition in ("NO_MEMORY", "PROFILE_ONLY"):
            expected["sessionNotes"] = []
        require(trial["memoryContext"] == expected, "Report memory intervention is inconsistent")
    factors = memory_factors(full)
    require(pair.get("activeFactors") == factors, "Report activeFactors disagree with memory")
    kind = "MEMORY_ABLATION" if factors else "EMPTY_MEMORY_CONTROL"
    require(pair.get("comparisonKind") == kind, "Report comparison kind disagrees with memory")
    passed = not pair["pairingFailures"] and all(t["contractStatus"] == "PASS" for t in trials)
    require(pair.get("contractStatus") == ("PASS" if passed else "FAIL"),
            "Report pair status disagrees with trials")


def report_data(raw: bytes) -> tuple[dict[str, Any], dict[str, Any]]:
    report = read_json(raw)
    require(type(report.get("schemaVersion")) is int and report["schemaVersion"] == 1,
            "Unsupported memory report schemaVersion")
    require(text(report.get("experimentId")), "Report experimentId is required")
    require(report.get("mode") in ("ONLINE", "OFFLINE"), "Report mode must be ONLINE or OFFLINE")
    for name in ("corpusHash", "fixtureImplementationHash"):
        require(isinstance(report.get(name), str) and re.fullmatch(r"[0-9a-f]{64}", report[name]) is not None,
                "Report provenance hashes are invalid")
    repeats = report.get("repetitions")
    require(type(repeats) is int and 1 <= repeats <= 4, "Report repetitions must be between 1 and 4")
    pairs = report.get("pairs")
    require(isinstance(pairs, list) and bool(pairs), "Report pairs must not be empty")
    seen: dict[str, set[int]] = {}
    for pair in pairs:
        validate_pair(pair, repeats)
        previous = seen.setdefault(pair["caseId"], set())
        require(pair["repetition"] not in previous, "Report contains duplicate pairs")
        previous.add(pair["repetition"])
    require(all(repeats_seen == set(range(1, repeats + 1)) for repeats_seen in seen.values()),
            "Report repetition matrix is incomplete")
    require(type(report.get("plannedTrials")) is int and report["plannedTrials"] == len(pairs) * 4,
            "Report plannedTrials disagrees with pairs")
    passed = all(pair["contractStatus"] == "PASS" for pair in pairs)
    require(report.get("contractStatus") == ("PASS" if passed else "FAIL"),
            "Report overall status disagrees with pairs")
    if report["mode"] == "OFFLINE":
        require(all(trial["responseOrigin"] == "SCRIPTED_FIXTURE" for pair in pairs for trial in pair["trials"]),
                "Offline report cannot contain real-provider trials")
    binding = {"reportSha256": hashlib.sha256(raw).hexdigest(), "experimentId": report["experimentId"],
               "reportSchemaVersion": report["schemaVersion"], "corpusHash": report["corpusHash"],
               "fixtureImplementationHash": report["fixtureImplementationHash"]}
    return report, binding


def exclusions(report: dict[str, Any], pair: dict[str, Any]) -> list[str]:
    reasons = []
    if report["mode"] != "ONLINE":
        reasons.append("OFFLINE_SCRIPTED")
    if any(trial["responseOrigin"] != "REAL_PROVIDER" for trial in pair["trials"]):
        reasons.append("SYNTHETIC_RESPONSE")
    if pair["contractStatus"] != "PASS":
        reasons.append("PAIR_CONTRACT_FAILED")
    if pair["comparisonKind"] == "EMPTY_MEMORY_CONTROL":
        reasons.append("EMPTY_MEMORY_CONTROL")
    return reasons


def comparators(report: dict[str, Any], pair: dict[str, Any]) -> list[str]:
    if exclusions(report, pair):
        return []
    factors = pair["activeFactors"]
    return [condition for condition in CONDITIONS[:-1]
            if condition == "NO_MEMORY"
            or condition == "PROFILE_ONLY" and "NOTES_WITH_SOURCE" in factors
            or condition == "NOTES_ONLY" and "PROFILE" in factors]


def blank_assessment(identity: dict[str, Any]) -> dict[str, Any]:
    return {**identity, "verdict": "PENDING", "rationale": "", "evidence": []}


def make_review(report_bytes: bytes, reviewer: str) -> dict[str, Any]:
    report, binding = report_data(report_bytes)
    require(text(reviewer), "Reviewer alias is required")
    return {"schemaVersion": 1, "reportBinding": binding, "reviewer": reviewer, "reviewedAt": None,
            "pairs": [{"pairId": pair["pairId"],
                       "criteria": [blank_assessment({"criterionIndex": index})
                                    for index in range(len(pair["manualCriteria"]))],
                       "contrasts": [blank_assessment({"comparator": condition})
                                     for condition in comparators(report, pair)]} for pair in report["pairs"]]}


def resolve_evidence(report: dict[str, Any], pair_index: int, evidence: Any) -> tuple[str, str]:
    object_keys(evidence, {"condition", "pointer"}, "Evidence must contain condition and pointer")
    pointer = evidence["pointer"]
    require(isinstance(pointer, str) and pointer.startswith("/") and re.search(r"~(?![01])", pointer) is None,
            "Evidence JSON Pointer is invalid")
    parts = [part.replace("~1", "/").replace("~0", "~") for part in pointer[1:].split("/")]
    require(len(parts) >= 5 and parts[:3] == ["pairs", str(pair_index), "trials"]
            and re.fullmatch(r"[0-3]", parts[3]) is not None and parts[4] in EVIDENCE_FIELDS,
            "Evidence must reference an allowed field in the same pair")
    trial = report["pairs"][pair_index]["trials"][int(parts[3])]
    require(evidence["condition"] == trial["condition"], "Evidence condition disagrees with its trial")
    value: Any = report
    for part in parts:
        if isinstance(value, dict):
            require(part in value, "Evidence pointer does not exist")
            value = value[part]
        elif isinstance(value, list):
            require(re.fullmatch(r"0|[1-9][0-9]*", part) is not None and int(part) < len(value),
                    "Evidence array index is invalid")
            value = value[int(part)]
        else:
            raise ReviewError("Evidence pointer does not exist")
    if parts[4] in ("response", "publicOutput"):
        require(len(parts) == 5, "Output evidence must reference the complete text field")
    return trial["condition"], parts[4]


def assess(report: dict[str, Any], index: int, item: Any, identity: str, expected: Any) -> bool:
    object_keys(item, {identity, "verdict", "rationale", "evidence"}, "Assessment fields are invalid")
    require(type(item[identity]) is type(expected) and item[identity] == expected,
            "Assessment identity is missing, duplicated, or unexpected")
    allowed = CRITERION_VERDICTS if identity == "criterionIndex" else CONTRAST_VERDICTS
    require(isinstance(item["verdict"], str) and item["verdict"] in allowed | {"PENDING"},
            "Assessment verdict is invalid")
    require(isinstance(item["rationale"], str) and isinstance(item["evidence"], list),
            "Assessment rationale and evidence have invalid types")
    references = {resolve_evidence(report, index, evidence) for evidence in item["evidence"]}
    complete = item["verdict"] != "PENDING"
    if complete:
        require(text(item["rationale"]) and bool(references), "A completed assessment needs rationale and evidence")
        if identity == "comparator":
            outputs = {condition for condition, field in references if field in ("response", "publicOutput")}
            require({"FULL_MEMORY", expected} <= outputs, "A contrast must cite both compared outputs")
    return complete


def summarize(report_bytes: bytes, review: dict[str, Any], require_complete: bool = False) -> dict[str, Any]:
    report, binding = report_data(report_bytes)
    object_keys(review, {"schemaVersion", "reportBinding", "reviewer", "reviewedAt", "pairs"},
                "Review fields are invalid")
    require(type(review["schemaVersion"]) is int and review["schemaVersion"] == 1,
            "Unsupported review schemaVersion")
    require(review["reportBinding"] == binding, "Review report binding mismatch")
    require(text(review["reviewer"]), "Reviewer alias is required")
    require(isinstance(review["pairs"], list) and len(review["pairs"]) == len(report["pairs"]),
            "Review must contain every report pair exactly once")
    completed = pending = 0
    counts = {condition: {"comparator": condition, "eligible": 0, "reviewed": 0,
                          "verdictCounts": dict.fromkeys(sorted(CONTRAST_VERDICTS), 0)}
              for condition in CONDITIONS[:-1]}
    failed = []
    excluded = []
    for index, (pair, entry) in enumerate(zip(report["pairs"], review["pairs"])):
        object_keys(entry, {"pairId", "criteria", "contrasts"}, "Review pair fields are invalid")
        require(entry["pairId"] == pair["pairId"], "Review pair order or identity does not match")
        expected_contrasts = comparators(report, pair)
        require(isinstance(entry["criteria"], list) and len(entry["criteria"]) == len(pair["manualCriteria"]),
                "Review must contain every manual criterion exactly once")
        require(isinstance(entry["contrasts"], list) and len(entry["contrasts"]) == len(expected_contrasts),
                "Review contrasts do not match eligible comparisons")
        for criterion_index, item in enumerate(entry["criteria"]):
            done = assess(report, index, item, "criterionIndex", criterion_index)
            completed += done
            pending += not done
            if item["verdict"] == "NOT_SATISFIED":
                failed.append({"pairId": pair["pairId"], "criterionIndex": criterion_index})
        for condition, item in zip(expected_contrasts, entry["contrasts"]):
            done = assess(report, index, item, "comparator", condition)
            completed += done
            pending += not done
            count = counts[condition]
            count["eligible"] += 1
            count["reviewed"] += done
            if done:
                count["verdictCounts"][item["verdict"]] += 1
        reasons = exclusions(report, pair)
        if reasons:
            excluded.append({"pairId": pair["pairId"], "reasons": reasons})
    if review["reviewedAt"] is not None or completed:
        require(text(review["reviewedAt"]), "Completed reviews require reviewedAt with a timezone")
        try:
            date = datetime.fromisoformat(review["reviewedAt"].replace("Z", "+00:00"))
        except ValueError as error:
            raise ReviewError("reviewedAt must be an ISO timestamp with a timezone") from error
        require(date.tzinfo is not None and "T" in review["reviewedAt"],
                "reviewedAt must be an ISO timestamp with a timezone")
    require(not require_complete or pending == 0, "Review is incomplete")
    eligible = sum(count["eligible"] for count in counts.values())
    reviewed = sum(count["reviewed"] for count in counts.values())
    comparison_status = "NOT_EVALUATED" if eligible == 0 else "NOT_REVIEWED"
    if reviewed:
        comparison_status = "HUMAN_REVIEWED" if pending == 0 else "PARTIALLY_REVIEWED"
    return {"schemaVersion": 1, "reportBinding": binding,
            "reviewStatus": "COMPLETE" if pending == 0 else "PARTIAL" if completed else "PENDING",
            "comparisonStatus": comparison_status, "totalPairs": len(report["pairs"]),
            "eligibleContrasts": eligible, "reviewedContrasts": reviewed, "pendingAssessments": pending,
            "contrasts": list(counts.values()), "excludedPairs": excluded, "failedCriteria": failed,
            "scope": "Human judgments on synthetic cases; repeated trials are not independent learner samples. "
                     "Counts do not establish teaching improvement, long-term retention, or statistical significance."}


def write_new(path: Path, value: dict[str, Any]) -> None:
    payload = json.dumps(value, ensure_ascii=False, indent=2, allow_nan=False) + "\n"
    with path.open("x", encoding="utf-8") as output:
        output.write(payload)


def main(argv: list[str] | None = None) -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    commands = parser.add_subparsers(dest="command", required=True)
    initialize = commands.add_parser("init", help="Create a review template without overwriting files")
    initialize.add_argument("report", type=Path)
    initialize.add_argument("review", type=Path)
    initialize.add_argument("--reviewer", required=True, help="Reviewer alias; no account credentials")
    summary = commands.add_parser("summarize", help="Validate the review and summarize eligible judgments")
    summary.add_argument("report", type=Path)
    summary.add_argument("review", type=Path)
    summary.add_argument("--output", type=Path)
    summary.add_argument("--require-complete", action="store_true")
    args = parser.parse_args(argv)
    try:
        raw = args.report.read_bytes()
        if args.command == "init":
            write_new(args.review, make_review(raw, args.reviewer))
            print("Review template created; all judgments remain pending.")
        else:
            result = summarize(raw, read_json(args.review.read_bytes()), args.require_complete)
            if args.output:
                write_new(args.output, result)
                print("Validated review summary written.")
            else:
                print(json.dumps(result, ensure_ascii=False, indent=2, allow_nan=False))
        return 0
    except ReviewError as error:
        print(f"Review validation failed: {error}", file=sys.stderr)
        return 2
    except OSError:
        print("Unable to read input or create output; existing files are never overwritten.", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
