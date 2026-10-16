SET @b15_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-09' AND deleted=0);
SET @b15_c=(SELECT id FROM question_understanding_content WHERE question_id=@b15_q AND content_version=1);
UPDATE question SET content='非空单链表只有头指针和尾指针，结点只含 `next`，没有前驱指针。要删除尾结点并保持尾指针正确，最坏情况下需要多少时间？'
WHERE id=@b15_q AND content='非空单链表只有头指针和尾指针，结点只含 next，没有前驱指针。要删除尾结点并保持尾指针正确，最坏情况下需要多少时间？';
UPDATE question_understanding_misconception SET explanation='尾指针只指向待删结点；单链表要重设前驱的 `next`，仍须从头遍历找前驱。'
WHERE content_id=@b15_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b15_q AND option_label='A' AND deleted=0) AND explanation='尾指针只指向待删结点；单链表要重设前驱的 next，仍须从头遍历找前驱。';
UPDATE question_understanding_misconception SET explanation='只需从头沿 `next` 走一遍找尾结点前驱，最坏时间与结点数成正比。'
WHERE content_id=@b15_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b15_q AND option_label='D' AND deleted=0) AND explanation='只需从头沿 next 走一遍找尾结点前驱，最坏时间与结点数成正比。';
UPDATE question_understanding_hint SET hint_text='删除尾结点后，谁的 `next` 必须变成 NULL？'
WHERE content_id=@b15_c AND hint_level=1 AND hint_text='删除尾结点后，谁的 next 必须变成 NULL？';
UPDATE ai_variant_question SET changed_condition='结点从只有 `next` 的单链表改为含 prev 的双向链表，其余条件不变。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b15_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式') AND changed_condition='结点从只有 next 的单链表改为含 prev 的双向链表，其余条件不变。';
UPDATE ai_variant_question SET analysis='尾结点的 prev 可直接定位前驱，改其 `next` 并更新尾指针即可，时间 `O(1)`。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b15_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式') AND analysis='尾结点的 prev 可直接定位前驱，改其 next 并更新尾指针即可，时间 `O(1)`。';
UPDATE question_understanding_content c JOIN question q ON q.id=c.question_id
SET c.question_fingerprint=SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
   ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
WHERE c.id=@b15_c AND q.content='非空单链表只有头指针和尾指针，结点只含 `next`，没有前驱指针。要删除尾结点并保持尾指针正确，最坏情况下需要多少时间？';

SET @b15_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-05' AND deleted=0);
SET @b15_c=(SELECT id FROM question_understanding_content WHERE question_id=@b15_q AND content_version=1);
UPDATE question_understanding_misconception SET explanation='把 p 所指结点的 `next` 改为 `q->next` 会跳过 q；再令 `s->next=q` 也没有把 s 接到 p 后面。指针变量 p 本身没有移动。'
WHERE content_id=@b15_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b15_q AND option_label='C' AND deleted=0) AND explanation='把 p 所指结点的 next 改为 `q->next` 会跳过 q；再令 `s->next=q` 也没有把 s 接到 p 后面。指针变量 p 本身没有移动。';

SET @b15_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-06' AND deleted=0);
SET @b15_c=(SELECT id FROM question_understanding_content WHERE question_id=@b15_q AND content_version=1);
UPDATE question_understanding_misconception SET explanation='令 `p=q->next` 只改指针变量 p，不会修改结点 2 的 `next` 域。'
WHERE content_id=@b15_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b15_q AND option_label='D' AND deleted=0) AND explanation='令 `p=q->next` 只改指针变量 p，不会修改结点 2 的 next 域。';
UPDATE question_understanding_hint SET hint_text='分别画出改变指针变量与改变结点 `next` 域后的图，沿头结点出发的路径检查哪些结点仍可达。'
WHERE content_id=@b15_c AND hint_level=2 AND hint_text='分别画出改变指针变量与改变结点 next 域后的图，沿头结点出发的路径检查哪些结点仍可达。';
UPDATE ai_variant_question SET analysis='原 `q->next=NULL`，删除前令 `p->next=q->next`，再释放 q；结点 4 成为尾结点，其 `next` 为 NULL。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b15_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式') AND analysis='原 `q->next=NULL`，删除前令 `p->next=q->next`，再释放 q；结点 4 成为尾结点，其 next 为 NULL。';

SET @b15_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-07' AND deleted=0);
SET @b15_c=(SELECT id FROM question_understanding_content WHERE question_id=@b15_q AND content_version=1);
UPDATE question SET content='带头结点的单链表 L 原为 2→4→8。循环中所有数据结点的 `next` 都已反向，prev 指向 8。要让从 L 出发可访问完整的新链表，最后还必须执行什么？'
WHERE id=@b15_q AND content='带头结点的单链表 L 原为 2→4→8。循环中所有数据结点的 next 都已反向，prev 指向 8。要让从 L 出发可访问完整的新链表，最后还必须执行什么？';
UPDATE ai_variant_question SET question_content='带头结点的单链表 L 为空，`L->next=NULL`。逆置算法令 `prev=NULL`，从首元结点开始循环反转 `next`，循环结束后统一执行 `L->next=prev`。最终 `L->next` 指向哪里？'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b15_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式') AND question_content='带头结点的单链表 L 为空，`L->next=NULL`。逆置算法令 `prev=NULL`，从首元结点开始循环反转 next，循环结束后统一执行 `L->next=prev`。最终 `L->next` 指向哪里？';
UPDATE question_understanding_content c JOIN question q ON q.id=c.question_id
SET c.question_fingerprint=SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
   ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
WHERE c.id=@b15_c AND q.content='带头结点的单链表 L 原为 2→4→8。循环中所有数据结点的 `next` 都已反向，prev 指向 8。要让从 L 出发可访问完整的新链表，最后还必须执行什么？';
