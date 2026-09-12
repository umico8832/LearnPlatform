ALTER TABLE `exam_answer`
  ADD UNIQUE KEY `uk_record_question` (`exam_record_id`, `question_id`);
