#!/usr/bin/env python3
"""Create deterministic scale data only in the locked, disposable E2E database."""
import json
import os
from pathlib import Path
import time
from database import sql
import run_suite as suite

BASE_ID = 1_000_000_000


def main():
    started = time.monotonic()
    # Login and credentials remain in memory; only synthetic aggregate metadata is retained.
    admin = suite.login('admin', 'admin123')
    existing = int(sql("SELECT COUNT(*) FROM user WHERE username REGEXP '^gperf[0-9]{4}$';").splitlines()[-1])
    for n in range(existing, 64):
        suite.api('/api/admin/users', {'username': f'gperf{n:04d}',
            'password': 'IsolatedPerfFixture!2026', 'nickname': '隔离容量夹具', 'role': 'USER'}, admin)
    setup = '''
    CREATE TEMPORARY TABLE g_seq(n INT PRIMARY KEY);
    INSERT INTO g_seq WITH g_digits AS (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3
      UNION ALL SELECT 4 UNION ALL SELECT 5 UNION ALL SELECT 6 UNION ALL SELECT 7 UNION ALL SELECT 8 UNION ALL SELECT 9)
      SELECT a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n
      FROM g_digits a CROSS JOIN g_digits b CROSS JOIN g_digits c
      CROSS JOIN g_digits d CROSS JOIN g_digits e CROSS JOIN g_digits f
      WHERE a.n+10*b.n+100*c.n+1000*d.n+10000*e.n+100000*f.n<300000;
    INSERT INTO user(id,username,password,nickname,status)
      SELECT 1000000000+n,CONCAT('gdisabled',n),'!isolated-no-login','隔离长尾夹具',0 FROM g_seq WHERE n>=64 AND n<1000;
    CREATE TEMPORARY TABLE g_users(n INT PRIMARY KEY, id BIGINT);
    INSERT INTO g_users SELECT CAST(SUBSTRING(username,6) AS UNSIGNED),id FROM user WHERE username REGEXP '^gperf[0-9]{4}$';
    INSERT INTO g_users SELECT n,1000000000+n FROM g_seq WHERE n>=64 AND n<1000;
    INSERT INTO course(id,name,sort_order) SELECT 1000000000+n,CONCAT('隔离课程-',n),100+n FROM g_seq WHERE n<200;
    INSERT INTO user_course(user_id,course_id) SELECT id,1000000000 FROM g_users WHERE n<64;
    INSERT INTO knowledge_point(id,name,course_id,parent_id,sort_order)
      SELECT 1000000000+n,CONCAT('隔离知识点-',n),1000000000+MOD(n,200),0,n FROM g_seq WHERE n<1000;
    INSERT INTO question(id,content,question_type,course_id,difficulty,visibility,status,create_time)
      SELECT 1000000000+n,CONCAT('隔离容量测试题 ',n,'：选择 A。'),'SINGLE_CHOICE',
        1000000000+IF(n<24000,MOD(n,12),12+MOD(n,188)),1+MOD(n,5),'PUBLIC',1,
        DATE_SUB('2026-10-23 12:00:00',INTERVAL MOD(n,180) DAY) FROM g_seq WHERE n<30000;
    INSERT INTO question_option(question_id,content,option_label,is_correct,sort_order)
      SELECT 1000000000+s.n,CONCAT('隔离选项-',d.n),CHAR(65+d.n),IF(d.n=0,1,0),d.n
      FROM g_seq s CROSS JOIN (SELECT 0 n UNION ALL SELECT 1 UNION ALL SELECT 2 UNION ALL SELECT 3) d WHERE s.n<30000;
    INSERT INTO question_knowledge_point(question_id,knowledge_point_id)
      SELECT 1000000000+n,1000000000+IF(n<24000,MOD(n,12),12+MOD(n,188)) FROM g_seq WHERE n<30000;
    CREATE TEMPORARY TABLE g_cards(n INT PRIMARY KEY,user_id BIGINT,question_id BIGINT);
    INSERT INTO g_cards SELECT s.n,u.id,1000000000+MOD(FLOOR(s.n/32)*97,30000)
      FROM g_seq s JOIN g_users u ON u.n=IF(s.n<80000,MOD(s.n,32),32+MOD(s.n,968)) WHERE s.n<100000;
    CREATE TEMPORARY TABLE g_facts(n INT PRIMARY KEY,user_id BIGINT,question_id BIGINT,at_time DATETIME);
    INSERT INTO g_facts SELECT s.n,c.user_id,c.question_id,
      DATE_SUB('2026-10-23 12:00:00',INTERVAL MOD(FLOOR(c.n/32),180) DAY) + INTERVAL FLOOR(s.n/100000) MINUTE
      FROM g_seq s JOIN g_cards c ON c.n=MOD(s.n,100000);
    '''
    # Each statement below commits independently; no long multi-table seed transaction.
    facts = '''
    INSERT INTO practice_record(id,user_id,question_id,user_answer,is_correct,answer_time,create_time)
      SELECT 1000000000+n,user_id,question_id,IF(MOD(FLOOR(n/32)+FLOOR(n/100000),4)=0,'B','A'),IF(MOD(FLOOR(n/32)+FLOOR(n/100000),4)=0,0,1),10+MOD(n,90),at_time FROM g_facts;
    INSERT INTO question_review_schedule(id,user_id,question_id,next_review_date,last_review_date,total_reviews,repetitions)
      SELECT 1000000000+c.n,c.user_id,c.question_id,
        DATE_ADD(DATE_SUB('2026-10-23',INTERVAL MOD(FLOOR(c.n/32),180) DAY),INTERVAL (1+MOD(c.n*37,60)) DAY),
        DATE_SUB('2026-10-23',INTERVAL MOD(FLOOR(c.n/32),180) DAY),3,2 FROM g_cards c;
    UPDATE question_review_schedule SET fsrs_card_json=JSON_OBJECT(
        'cardId',id,'state','REVIEW','step',NULL,'stability',1+MOD(id,600)/10.0,
        'difficulty',1+MOD(id,80)/10.0,
        'due',DATE_FORMAT(CONVERT_TZ(next_review_date,'America/New_York','UTC'),'%Y-%m-%dT%H:%i:%sZ'),
        'lastReview',DATE_FORMAT(CONVERT_TZ(TIMESTAMP(last_review_date,'12:02:00'),'America/New_York','UTC'),'%Y-%m-%dT%H:%i:%sZ')),
        fsrs_last_rating=IF(MOD(FLOOR((id-1000000000)/32),4)=0,'AGAIN','GOOD'),
        fsrs_version=1,ease_factor=NULL,interval_days=DATEDIFF(next_review_date,last_review_date) WHERE id>=1000000000;
    INSERT INTO fsrs_review_event(id,schedule_id,user_id,question_id,source_type,source_id,rating,reviewed_at)
      SELECT 1000000000+s.n,r.id,r.user_id,r.question_id,'PRACTICE',1000000000+s.n,
        IF(MOD(FLOOR(s.n/32)+FLOOR(s.n/100000),4)=0,'AGAIN','GOOD'),
        CONVERT_TZ(f.at_time,'America/New_York','UTC')
      FROM g_seq s JOIN question_review_schedule r ON r.id=1000000000+MOD(s.n,100000) JOIN g_facts f ON f.n=s.n;
    INSERT INTO course_learning_event(id,user_id,course_id,event_type,event_source,subject_type,subject_id,source_record_id,idempotency_key,payload_json,occurred_time)
      SELECT 1000000000+f.n,f.user_id,q.course_id,'PRACTICE_ANSWERED','PRACTICE','QUESTION',f.question_id,1000000000+f.n,
        CONCAT('g-fixture-',f.n),JSON_OBJECT('isCorrect',IF(MOD(FLOOR(f.n/32)+FLOOR(f.n/100000),4)=0,JSON_EXTRACT('false','$'),JSON_EXTRACT('true','$'))),f.at_time
      FROM g_facts f JOIN question q ON q.id=f.question_id;
    CREATE TEMPORARY TABLE g_rewards AS
      SELECT e.*,IF(e.id<1000100000,IF(JSON_EXTRACT(payload_json,'$.isCorrect')=TRUE,10,2),0) earned
      FROM course_learning_event e WHERE e.id>=1000000000;
    INSERT INTO gamification_xp_ledger(id,user_id,event_id,course_id,subject_type,subject_id,event_type,event_date,xp,reward_reason,occurred_time,total_xp_before,total_xp_after)
      SELECT id,user_id,id,course_id,'QUESTION',subject_id,'PRACTICE_ANSWERED',DATE(occurred_time),earned,
        IF(earned=0,'DUPLICATE_QUESTION_SAME_DAY',IF(earned=10,'CORRECT_ANSWER','INCORRECT_ANSWER')),occurred_time,
        SUM(earned) OVER(PARTITION BY user_id ORDER BY occurred_time,id)-earned,
        SUM(earned) OVER(PARTITION BY user_id ORDER BY occurred_time,id) FROM g_rewards;
    INSERT INTO gamification_user_profile(user_id,total_xp,answered_count,correct_count,backfill_cursor,backfill_complete,last_event_id)
      SELECT f.user_id,MAX(l.total_xp_after),COUNT(*),SUM(MOD(FLOOR(f.n/32)+FLOOR(f.n/100000),4)<>0),
        MAX(1000000000+f.n),TRUE,MAX(1000000000+f.n) FROM g_facts f JOIN gamification_xp_ledger l ON l.id=1000000000+f.n GROUP BY f.user_id;
    ANALYZE TABLE question,practice_record,question_review_schedule,fsrs_review_event,course_learning_event,gamification_xp_ledger;
    SELECT 'question',COUNT(*) FROM question UNION ALL SELECT 'practice_record',COUNT(*) FROM practice_record
      UNION ALL SELECT 'question_review_schedule',COUNT(*) FROM question_review_schedule
      UNION ALL SELECT 'fsrs_review_event',COUNT(*) FROM fsrs_review_event
      UNION ALL SELECT 'course_learning_event',COUNT(*) FROM course_learning_event
      UNION ALL SELECT 'gamification_xp_ledger',COUNT(*) FROM gamification_xp_ledger;
    SELECT 'orphan_practice',COUNT(*) FROM practice_record p LEFT JOIN question q ON q.id=p.question_id WHERE q.id IS NULL;
    SELECT 'active_32_answers',COUNT(*) FROM g_facts WHERE MOD(n,100000)<80000;
    SELECT 'tail_968_answers',COUNT(*) FROM g_facts WHERE MOD(n,100000)>=80000;
    '''
    output = sql(setup + facts)
    target = Path(os.environ['LOADTEST_OUTPUT'])
    (target / 'seed-result.txt').write_text(output)
    (target / 'seed-metadata.json').write_text(json.dumps({'additional_questions':30000,
        'additional_courses':200,'fixture_users':1000,'active_users':32,'active_answer_share':0.8,
        'additional_answers':300000,'additional_review_cards':100000,'additional_review_events':300000,'fsrs_fixture_state':'REVIEW',
        'additional_learning_events':300000,'additional_reward_audits':300000,
        'reward_positive_events':100000,'reward_duplicate_events':200000,'seed':20261023,'elapsed_s':time.monotonic()-started}, indent=2))
    print(output, flush=True)
    print(f'Seed completed in {time.monotonic()-started:.1f}s', flush=True)


if __name__ == '__main__':
    main()
