-- Tutor 会话只保存课程学习证据的聚合快照，不复制原始答案、正确答案或 AI 输出。
ALTER TABLE tutor_session
  ADD COLUMN learning_context_json JSON DEFAULT NULL COMMENT '启动会话时消费的课程学习证据聚合快照'
  AFTER tutor_content_id;
