import http from 'k6/http';
import { classify } from './outcome.mjs';
import { check } from 'k6';
import exec from 'k6/execution';
import { Trend, Rate, Counter } from 'k6/metrics';

const mode = __ENV.MODE || 'courses';
const vus = Number(__ENV.VUS || 10);
const duration = __ENV.DURATION || '30s';
const latency = new Trend('endpoint_ms', true);
const errors = new Rate('true_failures');
const successful = new Counter('successful_operations');
const rejected = new Counter('rejected_operations');
const refusalRate = new Rate('capacity_refusal_rate');
const firstUpstream = new Counter('upstream_first_requests');
const secondUpstream = new Counter('upstream_second_requests');
const unknownUpstream = new Counter('upstream_unknown_requests');
const successMs = new Trend('successful_ms', true);
const refusalMs = new Trend('refused_ms', true);
export const options = {
  scenarios: { baseline: mode === 'ask'
    ? { executor: 'per-vu-iterations', vus, iterations: Number(__ENV.ITERATIONS || 3), maxDuration: '4m' }
    : { executor: 'constant-vus', vus, duration, gracefulStop: '20s' } },
  summaryTrendStats: ['avg', 'min', 'med', 'p(95)', 'p(99)', 'max'],
  setupTimeout: '1m',
  thresholds: { true_failures: ['rate==0'], ...(__ENV.EXPECT_UPSTREAMS === '1' ? {
    upstream_first_requests: ['count>0'], upstream_second_requests: ['count>0'], upstream_unknown_requests: ['count==0'] } : {}) },
};

function uuid() {
  return 'xxxxxxxx-xxxx-4xxx-yxxx-xxxxxxxxxxxx'.replace(/[xy]/g, c => {
    const n = Math.floor(Math.random() * 16);
    return (c === 'x' ? n : (n & 3) | 8).toString(16);
  });
}

export function setup() {
  // The controller supplies only isolated test identities in memory.
  const response = http.get(`${__ENV.FIXTURE_URL}/fixture`, { tags: { name: 'setup_fixture' } });
  if (response.status !== 200) throw new Error('Isolated fixture unavailable');
  return response.json();
}

export default function(data) {
  const user = data.users[(exec.vu.idInTest - 1) % data.users.length];
  const params = { headers: { Authorization: `Bearer ${user.token}`, 'Content-Type': 'application/json', 'Idempotency-Key': uuid() },
    tags: { name: mode }, timeout: '180s' };
  let response;
  if (mode === 'courses') response = http.get(`${data.base}/api/courses/list`, params);
  else if (mode === 'understanding') response = http.get(`${data.base}/api/understanding/questions/${data.questionId}`, params);
  else if (mode === 'submit') response = http.post(`${data.base}/api/understanding/questions/${data.questionId}/attempts`,
    JSON.stringify({ answer: 'A', answerTime: 3 }), params);
  else if (mode === 'practice_stats') response = http.get(`${data.base}/api/practice/stats`, params);
  else if (mode === 'records') response = http.get(`${data.base}/api/practice/records?pageSize=20`, params);
  else if (mode === 'review_due') response = http.get(`${data.base}/api/review/due?limit=20`, params);
  else if (mode === 'questions') response = http.get(`${data.base}/api/questions?courseId=1000000000&pageSize=20`, params);
  else if (mode === 'practice_write') response = http.post(`${data.base}/api/practice/submit`,
    JSON.stringify({ questionId: data.scaleQuestion, userAnswer: 'A', answerTime: 30 }), params);
  else if (mode === 'review_write') response = http.post(`${data.base}/api/review/submit`,
    JSON.stringify({ questionId: data.scaleQuestion, userAnswer: 'A', answerTime: 30 }), params);
  else if (mode === 'ask') {
    const requestId = '00000000-0000-4000-8000-' +
      String(exec.vu.idInTest * 10000 + exec.vu.iterationInScenario).padStart(12, '0');
    response = http.post(`${data.base}/api/understanding/attempts/${user.attemptId}/ask/stream`,
      JSON.stringify({ message: '请解释应该先关注什么概念。', requestId }), params);
  } else throw new Error('Unknown mode');
  if (data.upstreams && data.upstreams.length === 2) {
    const index = data.upstreams.indexOf(response.headers['X-Upstream']);
    firstUpstream.add(index === 0 ? 1 : 0);
    secondUpstream.add(index === 1 ? 1 : 0);
    unknownUpstream.add(index < 0 ? 1 : 0);
  }
  const outcome = classify(mode, response);
  check(response, { 'operation succeeded or capacity refused': () => outcome !== 'failure' });
  latency.add(response.timings.duration);
  errors.add(outcome === 'failure');
  refusalRate.add(outcome === 'rejected');
  if (outcome === 'success') { successful.add(1); successMs.add(response.timings.duration); }
  else if (outcome === 'rejected') { rejected.add(1); refusalMs.add(response.timings.duration); }

}

export function handleSummary(data) {
  // Response bodies and setup identities are deliberately never serialized.
  return { [__ENV.RESULT_FILE]: JSON.stringify(data, null, 2) };
}
