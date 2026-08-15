-- 将 2026 真题中线性表客观题同时关联到已审查的原子知识点，使学习者可以从课程总览的
-- 已审查知识点发起限定范围阶段测评。原顶级知识点关联保持不变，仅追加更具体的已审查关联。
-- 两道题均来自 V78 固定种子数据，使用题干前缀定位并以 NOT EXISTS 保证幂等。

INSERT INTO question_knowledge_point (question_id, knowledge_point_id)
SELECT q.id, kp.id
FROM question q
JOIN course c ON c.id = q.course_id AND c.content_key = 'cs408-data-structures' AND c.deleted = 0
JOIN knowledge_point kp ON kp.course_id = c.id
     AND kp.content_key = 'cs408-sequential-list-insert-delete' AND kp.deleted = 0
WHERE q.content LIKE '当存储空间有足够的空闲空间时%' AND q.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM question_knowledge_point x
      WHERE x.question_id = q.id AND x.knowledge_point_id = kp.id
  );

INSERT INTO question_knowledge_point (question_id, knowledge_point_id)
SELECT q.id, kp.id
FROM question q
JOIN course c ON c.id = q.course_id AND c.content_key = 'cs408-data-structures' AND c.deleted = 0
JOIN knowledge_point kp ON kp.course_id = c.id
     AND kp.content_key = 'cs408-singly-linked-list' AND kp.deleted = 0
WHERE q.content LIKE 'while(cu!=NULL)%' AND q.deleted = 0
  AND NOT EXISTS (
      SELECT 1 FROM question_knowledge_point x
      WHERE x.question_id = q.id AND x.knowledge_point_id = kp.id
  );
