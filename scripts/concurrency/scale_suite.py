#!/usr/bin/env python3
"""Run the same scale workload before/after against unchanged isolated fixture data."""
import concurrent.futures
import json
import sys
import threading
from http.server import ThreadingHTTPServer
import run_suite as suite
from database import sql


def main():
    users = []
    with concurrent.futures.ThreadPoolExecutor(max_workers=4) as pool:
        tokens = list(pool.map(lambda n: suite.login(f'gperf{n:04d}', 'IsolatedPerfFixture!2026'), range(64)))
    users = [{'token': token} for token in tokens]
    suite.fixture.update(base=suite.BASE, scaleQuestion=1000000000, users=users, upstreams=suite.discover_upstreams())
    server = ThreadingHTTPServer(('127.0.0.1', 0), suite.FixtureHandler)
    suite.fixture_url = f'http://127.0.0.1:{server.server_port}'
    threading.Thread(target=server.serve_forever, daemon=True).start()
    modes = sys.argv[1:] or ['practice_stats', 'records', 'review_due', 'questions', 'practice_write', 'review_write']
    if any(mode in ('practice_write', 'review_write') for mode in modes):
        for user in users:
            suite.api('/api/my-courses/1000000000', {}, user['token'])
    rows = []
    try:
        for mode in modes:
            suite.run_case(mode, 4, f'warmup-{mode}', '10s')
            for vus in (10, 50):
                rows.append(suite.run_case(mode, vus, f'{mode}-{vus}'))
    finally:
        (suite.HERE / 'summary.json').write_text(json.dumps(rows, ensure_ascii=False, indent=2))
        server.shutdown()
        suite.fixture.clear()
    digests = sql("""SELECT DIGEST_TEXT,COUNT_STAR,ROUND(SUM_TIMER_WAIT/1e12,3) seconds,
      ROUND(AVG_TIMER_WAIT/1e9,3) average_ms,SUM_ROWS_EXAMINED,SUM_ROWS_SENT
      FROM performance_schema.events_statements_summary_by_digest WHERE SCHEMA_NAME='learn_platform'
      ORDER BY SUM_TIMER_WAIT DESC LIMIT 25;""")
    (suite.HERE / 'statement-digests.txt').write_text(digests)
    return 0 if all(row['k6_exit'] == 0 for row in rows) else 1


if __name__ == '__main__':
    raise SystemExit(main())
