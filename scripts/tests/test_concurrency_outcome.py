"""Verify capacity rejection cannot hide transport, quota or malformed SSE failures."""
import pathlib
import subprocess
import unittest

ROOT = pathlib.Path(__file__).resolve().parents[2]


class ConcurrencyOutcomeTest(unittest.TestCase):
    def test_outcomes(self):
        script = r'''
import { classify } from './scripts/concurrency/outcome.mjs';
import assert from 'node:assert/strict';
const outcome = (mode, body, status=200) => classify(mode, {status, body});
assert.equal(outcome('ask', 'event:done\ndata:{}\n\n'), 'success');
assert.equal(outcome('submit', '{"code":0}'), 'success');
assert.equal(outcome('ask', '{"code":1007,"message":"忙"}'), 'rejected');
assert.equal(outcome('ask', 'event:error\ndata:{"code":1007}\n\n'), 'rejected');
assert.equal(outcome('submit', '{"code":1007}'), 'failure');
for (const body of ['{"code":1012}', '{"code":1010}', 'event:error\ndata:{"code":1012}\n\n', 'event:status\ndata:{}\n\n', 'event:error\ndata:garbage\n\n', 'event:done\ndata:{}\n\nevent:error\ndata:{"code":1012}\n\n']) {
  assert.equal(outcome('ask', body), 'failure');
}
assert.equal(outcome('ask', '{"code":1007}', 503), 'failure');
assert.equal(outcome('ask', 'event:error\ndata:{"code":1007}\n\ndata:{"code":1012}\n\n'), 'failure');
'''
        result = subprocess.run(['node', '--input-type=module', '-e', script], cwd=ROOT,
                                capture_output=True, text=True)
        self.assertEqual(result.returncode, 0, result.stderr)
