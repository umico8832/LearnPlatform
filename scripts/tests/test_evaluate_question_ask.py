import importlib.util
from pathlib import Path
import sys
import unittest
from unittest.mock import patch

MODULE = Path(__file__).resolve().parents[1] / 'evaluate-question-ask.py'
SPEC = importlib.util.spec_from_file_location('question_ask_eval', MODULE)
EVAL = importlib.util.module_from_spec(SPEC)
SPEC.loader.exec_module(EVAL)


class EvaluateQuestionAskTest(unittest.TestCase):
    def run_cli(self, arguments):
        with patch.object(sys, 'argv', ['evaluate-question-ask.py', *arguments]):
            with patch.object(EVAL.subprocess, 'run') as run:
                run.return_value.returncode = 0
                self.assertEqual(0, EVAL.main())
                return run.call_args

    def test_default_is_mock_and_uses_this_worktree(self):
        call = self.run_cli([])
        self.assertNotIn('-DquestionAskEval.real=true', call.args[0])
        self.assertEqual(MODULE.parents[1] / 'backend', call.kwargs['cwd'])

    def test_only_explicit_real_flag_enables_external_calls(self):
        self.assertIn('-DquestionAskEval.real=true', self.run_cli(['--real']).args[0])

    def test_unknown_argument_is_rejected(self):
        with patch.object(sys, 'argv', ['evaluate-question-ask.py', '--online']):
            with patch.object(EVAL.subprocess, 'run') as run:
                with self.assertRaises(SystemExit):
                    EVAL.main()
                run.assert_not_called()

    def test_summary_uses_all_call_costs_and_requires_review_labels(self):
        row = dict(thinking=True, unlocked=False, error=None, fallback=True, ruleLeak=False,
                   reviewPublicLeak=None, latencyMs=200, totalTokens=480, costUsd=0.000216,
                   usageComplete=True, calls=[
                       dict(stage='generation', reviewUnsafe=None),
                       dict(stage='judgment', rawOutput='{"safe":false}'),
                   ])
        summary = EVAL.summarize({'cases': [row]})['thinking_on']
        self.assertIsNone(summary['reviewLeakRate'])
        self.assertIsNone(summary['judgmentFalseBlockRate'])
        self.assertEqual(480, summary['meanTokensPerAsk'])
        self.assertEqual(0.000216, summary['meanCostUsdPerAsk'])
        row['reviewPublicLeak'] = False
        row['calls'][0]['reviewUnsafe'] = False
        summary = EVAL.summarize({'cases': [row]})['thinking_on']
        self.assertEqual(0, summary['reviewLeakRate'])
        self.assertEqual(1, summary['judgmentFalseBlockRate'])

    def test_summary_command_never_invokes_model(self):
        import json
        import tempfile
        with tempfile.TemporaryDirectory() as directory:
            report = Path(directory) / 'report.json'
            report.write_text(json.dumps({'cases': []}))
            with patch.object(sys, 'argv', ['evaluate-question-ask.py', '--summary', str(report)]):
                with patch.object(EVAL.subprocess, 'run') as run:
                    self.assertEqual(0, EVAL.main())
                    run.assert_not_called()
