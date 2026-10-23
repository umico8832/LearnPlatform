#!/usr/bin/env python3
"""Capture structural and aggregate evidence from the isolated E2E database."""
from pathlib import Path
import json
import os
import subprocess

SOURCE = Path(__file__).resolve().parent
HERE = Path(os.environ['LOADTEST_OUTPUT']).resolve()
HERE.mkdir(parents=True, exist_ok=True)
metadata = HERE / 'fixture-metadata.json'
question_id = json.loads(metadata.read_text())['question_id'] if metadata.exists() else 1
statements = {
    'database-summary': """
      SELECT VERSION();
      SELECT 'course',COUNT(*) FROM course UNION ALL SELECT 'question',COUNT(*) FROM question
      UNION ALL SELECT 'knowledge_point',COUNT(*) FROM knowledge_point
      UNION ALL SELECT 'practice_record',COUNT(*) FROM practice_record
      UNION ALL SELECT 'question_ask_record',COUNT(*) FROM question_ask_record;
      SELECT status,COUNT(*) FROM question_ask_record GROUP BY status;
      SHOW GLOBAL STATUS LIKE 'Threads%';
      SHOW VARIABLES LIKE 'max_connections';
      SHOW VARIABLES LIKE 'slow_query_log';
    """,
    'explain': """
      EXPLAIN ANALYZE SELECT * FROM course WHERE status=1 AND deleted=0 ORDER BY sort_order,create_time DESC;
      EXPLAIN ANALYZE SELECT * FROM question_understanding_content WHERE question_id=1 ORDER BY content_version DESC LIMIT 1;
      EXPLAIN ANALYZE SELECT id,option_label,content,is_correct FROM question_option
        WHERE question_id=1 AND deleted=0 ORDER BY sort_order,id;
      SHOW INDEX FROM ai_call_log;
      SHOW INDEX FROM question_understanding_content;
      SHOW INDEX FROM question_option;
    """,
    'statement-digests': """
      SELECT DIGEST_TEXT,COUNT_STAR,ROUND(SUM_TIMER_WAIT/1000000000000,3) total_seconds,
        ROUND(AVG_TIMER_WAIT/1000000000,3) average_ms,ROUND(MAX_TIMER_WAIT/1000000000,3) max_ms,
        SUM_ROWS_EXAMINED FROM performance_schema.events_statements_summary_by_digest
        WHERE SCHEMA_NAME='learn_platform' ORDER BY SUM_TIMER_WAIT DESC LIMIT 20;
    """,
    'gamification-explain': """
      SET @u=(SELECT user_id FROM gamification_xp_ledger GROUP BY user_id ORDER BY COUNT(*) DESC LIMIT 1);
      EXPLAIN ANALYZE SELECT COUNT(*) FROM gamification_xp_ledger WHERE user_id=@u AND course_id=6
        AND subject_type='QUESTION' AND subject_id=17 AND event_date=CURRENT_DATE;
      EXPLAIN ANALYZE SELECT EXISTS(SELECT 1 FROM gamification_xp_ledger WHERE user_id=@u AND course_id=6
        AND subject_type='QUESTION' AND subject_id=17 AND event_date=CURRENT_DATE);
      EXPLAIN ANALYZE SELECT COUNT(*) FROM gamification_xp_ledger WHERE user_id=@u AND event_date=CURRENT_DATE AND xp>0;
    """,
}
for name, sql in statements.items():
    sql = sql.replace('question_id=1', f'question_id={question_id}')
    result = subprocess.run(['docker', 'exec', '-e', 'MYSQL_PWD=root123', 'learnplatform-e2e-mysql',
        'mysql', '-uroot', '--table', 'learn_platform', '-e', sql], text=True, capture_output=True)
    (HERE / f'{name}.txt').write_text(result.stdout + result.stderr)
    print(name, 'exit', result.returncode)
