#!/usr/bin/env python3
"""Evaluate question follow-ups with a scripted upstream by default; never loads .env."""
import argparse
import json
from pathlib import Path
import subprocess


def unique_object(pairs):
    result = {}
    for key, value in pairs:
        if key in result:
            raise ValueError('duplicate JSON field')
        result[key] = value
    return result


def summarize(report: dict) -> dict:
    result = {}
    for thinking in (True, False):
        rows = [row for row in report['cases'] if row['thinking'] == thinking]
        locked = [row for row in rows if not row['unlocked'] and row['error'] is None]
        labels = [row['reviewPublicLeak'] for row in locked if isinstance(row.get('reviewPublicLeak'), bool)]
        safe_judged = []
        for row in rows:
            calls = row['calls']
            for index, call in enumerate(calls):
                if call['stage'] != 'judgment' or index == 0:
                    continue
                previous = calls[index - 1]
                if previous['stage'] == 'generation' and previous.get('reviewUnsafe') is False:
                    safe_judged.append(call.get('rawOutput'))
        false_blocks = 0
        for value in safe_judged:
            try:
                parsed = json.loads(value, object_pairs_hook=unique_object)
                accepted = isinstance(parsed, dict) and len(parsed) == 1 and parsed.get('safe') is True
            except (ValueError, TypeError):
                accepted = False
            false_blocks += not accepted
        latencies = sorted(row['latencyMs'] for row in rows)
        complete_costs = [row['costUsd'] for row in rows if row['usageComplete']]
        result['thinking_on' if thinking else 'thinking_off'] = {
            'qualityLabelOrigin': 'SCRIPTED_ORACLE' if report.get('responseOrigin') == 'SCRIPTED_HTTP_UPSTREAM' else 'AI_REVIEW',
            'cases': len(rows),
            'errors': sum(row['error'] is not None for row in rows),
            'fallbacks': sum(row['fallback'] for row in rows),
            'ruleLeakRate': sum(row['ruleLeak'] for row in locked) / len(locked) if locked else None,
            'reviewLeakRate': sum(labels) / len(labels) if labels else None,
            'reviewLeakReviewed': len(labels),
            'reviewLeakTotal': len(locked),
            'judgmentFalseBlockRate': false_blocks / len(safe_judged) if safe_judged else None,
            'safeJudgmentsReviewed': len(safe_judged),
            'latencyP50Ms': latencies[(len(latencies) - 1) // 2] if latencies else None,
            'latencyP95Ms': latencies[max(0, (95 * len(latencies) + 99) // 100 - 1)] if latencies else None,
            'meanTokensPerAsk': sum(row['totalTokens'] for row in rows) / len(rows) if rows else None,
            'meanCostUsdPerAsk': sum(complete_costs) / len(complete_costs) if complete_costs else None,
            'unknownCostAsks': len(rows) - len(complete_costs),
            'providerCalls': sum(len(row['calls']) for row in rows),
        }
    return result


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--real', action='store_true', help='Call DeepSeek with externally injected AI_* (60 asks, at most 240 calls).')
    parser.add_argument('--summary', type=Path, help='Summarize an existing report, including AI review labels; makes no model calls.')
    args = parser.parse_args()
    if args.summary:
        if args.real:
            parser.error('--summary cannot be combined with --real')
        print(json.dumps(summarize(json.loads(args.summary.read_text())), ensure_ascii=False, indent=2))
        return 0
    backend = Path(__file__).resolve().parents[1] / 'backend'
    command = [str(backend / 'mvnw'), '-pl', 'app', '-am', '-Dtest=QuestionAskEvaluationTest']
    command.append(f'-DquestionAskEval.real={str(args.real).lower()}')
    command.append('test')
    code = subprocess.run(command, cwd=backend, check=False).returncode
    report = backend / 'app/target/question-ask-evaluation' / ('real.json' if args.real else 'mock.json')
    if code == 0 and report.exists():
        print(json.dumps(summarize(json.loads(report.read_text())), ensure_ascii=False, indent=2))
        print(f'Report: {report}')
    return code


if __name__ == '__main__':
    raise SystemExit(main())
