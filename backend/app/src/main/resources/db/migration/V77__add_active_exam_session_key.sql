-- 限时考试使用可释放的幂等键恢复同一进行中记录；完成或超时后允许开始新一轮。
ALTER TABLE `exam_record`
  ADD COLUMN `active_exam_key` VARCHAR(160) DEFAULT NULL
    COMMENT '进行中考试幂等键，完成或超时后置空' AFTER `status`;

-- 升级时先固化已经超过试卷时限的历史进行中记录，避免它们继续占用活动键。
UPDATE `exam_record` r
JOIN `exam_paper` p ON p.`id` = r.`exam_paper_id`
SET r.`status` = 2,
    r.`score` = COALESCE(r.`score`, 0),
    r.`end_time` = COALESCE(r.`end_time`,
        TIMESTAMPADD(MINUTE, COALESCE(p.`duration`, 60), r.`start_time`))
WHERE r.`status` = 0
  AND TIMESTAMPADD(MINUTE, COALESCE(p.`duration`, 60), r.`start_time`)
      <= TIMESTAMPADD(HOUR, 8, UTC_TIMESTAMP());

-- 旧库可能存在同一用户重复开始同一试卷的记录；只保留最近一条为可恢复状态。
UPDATE `exam_record` r
JOIN (
  SELECT `id`,
         ROW_NUMBER() OVER (
           PARTITION BY `user_id`, `exam_paper_id`
           ORDER BY `start_time` DESC, `id` DESC
         ) AS `row_no`
  FROM `exam_record`
  WHERE `status` = 0
) ranked ON ranked.`id` = r.`id`
SET r.`status` = 2,
    r.`score` = COALESCE(r.`score`, 0),
    r.`end_time` = COALESCE(r.`end_time`, TIMESTAMPADD(HOUR, 8, UTC_TIMESTAMP()))
WHERE ranked.`row_no` > 1;

UPDATE `exam_record`
SET `active_exam_key` = CONCAT('EXAM:', `user_id`, ':', `exam_paper_id`)
WHERE `status` = 0;

ALTER TABLE `exam_record`
  ADD UNIQUE KEY `uk_exam_record_active` (`active_exam_key`);
