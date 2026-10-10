ALTER TABLE question_understanding_attempt
  ADD COLUMN hint_chain_enabled TINYINT NOT NULL DEFAULT 0;

ALTER TABLE question_understanding_content
  ADD COLUMN solution_content_source VARCHAR(20) NOT NULL DEFAULT 'CURATED';

CREATE TABLE question_understanding_hint (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  content_id BIGINT NOT NULL,
  hint_level TINYINT NOT NULL,
  hint_text TEXT NOT NULL,
  content_source VARCHAR(20) NOT NULL,
  review_status VARCHAR(20) NOT NULL,
  reviewed_by VARCHAR(100) NULL,
  reviewed_time DATETIME NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_hint_level (content_id, hint_level)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE question_understanding_hint_receipt (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id BIGINT NOT NULL,
  hint_id BIGINT NOT NULL,
  hint_level TINYINT NOT NULL,
  request_id CHAR(36) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_receipt_level (attempt_id, hint_level),
  UNIQUE KEY uk_understanding_receipt_request (attempt_id, request_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

CREATE TABLE question_understanding_viz_view (
  id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
  attempt_id BIGINT NOT NULL,
  context VARCHAR(20) NOT NULL,
  create_time DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP,
  UNIQUE KEY uk_understanding_viz_view (attempt_id, context)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_general_ci;

-- 只创建 AI 草稿；没有人工审核事实时不能进入公开提示链。
INSERT INTO question_understanding_hint
(content_id, hint_level, hint_text, content_source, review_status)
SELECT c.id, h.hint_level, h.hint_text, 'AI_GENERATED', 'PENDING'
FROM question_understanding_content c
JOIN (
  SELECT 1 AS hint_level, '先看层序遍历最早出现的结点：它确定当前子树的根。再用中序遍历判断根两侧各有哪些结点。' AS hint_text
  UNION ALL SELECT 2, '每切出一棵子树，都只在它自己的中序片段里找根的位置；依层序确定该片段的根，再分别处理左右片段。'
  UNION ALL SELECT 3, '试想中序片段为 p,q,r，层序里 q 先于 p、r。q 是这棵子树的根，后序应先处理 p 和 r，再写 q。把这个过程逐层应用到本题。'
) h
WHERE c.source_title='2026 年 408 真题 第 3 题';

INSERT INTO question_understanding_hint
(content_id, hint_level, hint_text, content_source, review_status)
SELECT c.id, h.hint_level, h.hint_text, 'AI_GENERATED', 'PENDING'
FROM question_understanding_content c
JOIN (
  SELECT 1 AS hint_level, '栈只能从顶端取出元素。判断一个出栈序列时，先问每一步要取的元素是否已在栈顶。' AS hint_text
  UNION ALL SELECT 2, '按固定入栈顺序推进：目标元素还没入栈就继续压入；已经入栈就检查它是不是当前栈顶。每弹出一个元素，再看目标序列的下一个。'
  UNION ALL SELECT 3, '例如顺序入栈 1、2、3 后，若先弹出 3，此时栈顶是 2。下一步能否弹出 1，要先看 2 是否已经离开。用同样的栈顶检查走完本题选项。'
) h
WHERE c.source_title='系统种子生成：合法出栈序列';
