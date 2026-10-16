#!/usr/bin/env python3
"""Run the question follow-up corpus; external calls require the explicit --real flag."""
import argparse
from pathlib import Path
import subprocess


def main() -> int:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument('--real', action='store_true', help='Call DeepSeek using externally injected AI_* configuration (50 calls).')
    args = parser.parse_args()
    backend = Path(__file__).resolve().parents[1] / 'backend'
    command = [str(backend / 'mvnw'), '-pl', 'app', '-am', '-Dtest=QuestionAskEvaluationTest']
    command.append(f'-DquestionAskEval.real={str(args.real).lower()}')
    command.append('test')
    return subprocess.run(command, cwd=backend, check=False).returncode


if __name__ == '__main__':
    raise SystemExit(main())
