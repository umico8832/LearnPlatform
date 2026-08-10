-- DualArrayDeque 模块：再平衡的线性重建成本可由两次重建之间的更新次数摊还。
INSERT INTO knowledge_point (name, description, course_id, parent_id, content_key, content_source, content_version, content_review_status, sort_order, deleted)
SELECT 'DualArrayDeque 再平衡的摊还成本', '重建后两栈近似等大；再次达到三倍失衡前需要 Ω(n) 次更新，因此多次 balance 的总成本为 O(m)。', course.id, parent.id, 'ods-dualarraydeque-amortized-balance', 'AISTU', 1, 'REVIEWED', 27, 0
FROM course JOIN knowledge_point parent ON parent.course_id = course.id AND parent.content_key = '408-stacks-queues-arrays' LEFT JOIN knowledge_point existing ON existing.content_key = 'ods-dualarraydeque-amortized-balance'
WHERE course.content_key = 'cs408-data-structures' AND existing.id IS NULL;
INSERT INTO tutor_content (knowledge_point_id, content_key, content_version, review_status, title, lesson_json, check_json)
SELECT point.id, 'ods-dualarraydeque-amortized-balance', 1, 'REVIEWED', 'DualArrayDeque 再平衡的摊还成本',
JSON_OBJECT('summary', '虽然单次 balance 重建为 O(n)，但从空结构开始执行 m 次 add/remove，全部 balance 的总成本为 O(m)。重建后两栈近似等大，再次三倍失衡前需要 Ω(n) 次更新。', 'steps', JSON_ARRAY('重建后 front 与 back 的大小差至多为 1', '一次未重建更新至多让 |front.size()-back.size()| 增加 1', '触发三倍失衡时，大小差已达到与 n 同阶', '因此两次 O(n) 重建之间有 Ω(n) 次更新，重建成本可摊还'), 'prerequisite', JSON_OBJECT('contentKey', 'ods-dualarraydeque-balance', 'title', 'DualArrayDeque 的再平衡', 'description', '先理解触发条件与一次重建的线性成本。'), 'nextStep', JSON_OBJECT('contentKey', 'ods-dualarraydeque-performance', 'title', 'DualArrayDeque 的操作复杂度', 'description', '汇总随机访问、按位更新与重建的复杂度。')),
JSON_OBJECT('id', 'dualarraydeque-amortized-balance-v1', 'prompt', '为什么 O(n) 的 balance 重建不会使长期平均更新成本变成 O(n)？', 'options', JSON_ARRAY(JSON_OBJECT('id', 'TOTAL_LINEAR', 'text', '重建后需经过 Ω(n) 次更新才会再次严重失衡，全部重建总成本为 O(m)'), JSON_OBJECT('id', 'EVERY_REBUILD_CONSTANT', 'text', '每次重建本身只复制常数个元素')), 'correctOptionId', 'TOTAL_LINEAR', 'correctExplanation', '正确：重建不频繁发生；两次重建之间足够多的更新可以共同分摊一次线性复制。', 'incorrectExplanation', '不正确：单次重建确实会移动 O(n) 个元素，常数摊还来自触发之间的间隔。')
FROM knowledge_point point LEFT JOIN tutor_content existing ON existing.content_key = 'ods-dualarraydeque-amortized-balance' AND existing.content_version = 1
WHERE point.content_key = 'ods-dualarraydeque-amortized-balance' AND existing.id IS NULL;
