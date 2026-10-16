-- V111 已进入共享历史；用增量迁移同步 C3 返修，保留原迁移校验和。

-- ll-09
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-09' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='尾指针能直接找到尾结点，却不能在单链表中反向找到其前驱；最坏需从头遍历到倒数第二个结点，耗时 `O(n)`。'
WHERE id=@b13_q;
UPDATE question_option SET content='`O(1)`'
WHERE question_id=@b13_q AND option_label='A' AND deleted=0;
UPDATE question_option SET content='`O(log n)`'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_option SET content='`O(n)`'
WHERE question_id=@b13_q AND option_label='C' AND deleted=0;
UPDATE question_option SET content='`O(n²)`'
WHERE question_id=@b13_q AND option_label='D' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='尾指针能直接找到尾结点，却不能在单链表中反向找到其前驱；最坏需从头遍历到倒数第二个结点，耗时 `O(n)`。'
WHERE id=@b13_c;
UPDATE ai_variant_question SET analysis='尾结点的 prev 可直接定位前驱，改其 next 并更新尾指针即可，时间 `O(1)`。',
    options_json=CAST('[{"label":"A","content":"`O(1)`"},{"label":"B","content":"`O(log n)`"},{"label":"C","content":"`O(n)`"},{"label":"D","content":"`O(n²)`"}]' AS JSON)
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-10
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-10' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='不扩容时直接写入 `data[n]` 并把长度加一，旧元素不移动，时间 `O(1)`。'
WHERE id=@b13_q;
UPDATE question_option SET content='`O(1)`'
WHERE question_id=@b13_q AND option_label='A' AND deleted=0;
UPDATE question_option SET content='`O(log n)`'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_option SET content='`O(n)`'
WHERE question_id=@b13_q AND option_label='C' AND deleted=0;
UPDATE question_option SET content='`O(n²)`'
WHERE question_id=@b13_q AND option_label='D' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='不扩容时直接写入 `data[n]` 并把长度加一，旧元素不移动，时间 `O(1)`。'
WHERE id=@b13_c;
UPDATE question_understanding_hint SET hint_text='例如长度为 3 的数组表且槽位充足，追加到 `data[3]` 只做一次写入。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET analysis='原 n 个元素都要后移一格，移动次数与 n 成正比，因此为 `O(n)`。',
    options_json=CAST('[{"label":"A","content":"`O(1)`"},{"label":"B","content":"`O(log n)`"},{"label":"C","content":"`O(n)`"},{"label":"D","content":"`O(n²)`"}]' AS JSON)
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- st-08
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:st-08' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='数组容量为 5 的顺序栈用 top 指向当前栈顶元素，空栈 `top=-1`。若 `top=4`，再执行一次 `Push`，正确结果是什么？',
    analysis='有效下标为 0 至 4。`top=4` 表示五个槽均已占用；`Push` 应报告满栈，不修改栈与 top。'
WHERE id=@b13_q;
UPDATE question_option SET content='栈满，`Push` 应拒绝且 top 保持 4'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='有效下标为 0 至 4。`top=4` 表示五个槽均已占用；`Push` 应报告满栈，不修改栈与 top。'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='容量 5 的有效下标是 0..4；`top=4` 时没有下标 5 可写。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='`Push` 应新增元素而非覆盖当前栈顶，下标 4 已被占用。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='C' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='该表示下空栈 `top=-1`；`top=4` 是容量 5 的满栈。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='例如容量 2 时，`top=1` 已占用下标 0、1，再 `Push` 就没有空槽。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='同一顺序栈仍为容量 5，只把 `Push` 前的 top 改为 3。执行一次 `Push` 后 top 是多少？',
    analysis='下标 4 尚空，`Push` 可把新元素写入该槽，并令 top 从 3 增至 4。',
    options_json=CAST('[{"label":"A","content":"3"},{"label":"B","content":"4"},{"label":"C","content":"5"},{"label":"D","content":"`Push` 必须拒绝"}]' AS JSON)
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- qu-07
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:qu-07' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='带头结点的链队列中，front 指向保留的头结点；非空时 rear 指向最后一个数据结点，空队列约定 `rear=front` 且 `front->next=NULL`。当前队列只有一个数据结点 q。出队并释放 q 后，rear 应指向哪里？',
    analysis='删除唯一的数据结点后，头结点仍在，`front->next=NULL`，rear 必须重新指向 front，表示空队列。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='删除唯一的数据结点后，头结点仍在，`front->next=NULL`，rear 必须重新指向 front，表示空队列。'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='题干明确约定空队列 `rear=front`。删除唯一数据结点后，rear 应回到保留的头结点；置 NULL 不符合本题的空队列表示。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='头结点仍存在，队空只需让头结点 `next=NULL` 并让 rear 回到 front。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='例如队列有甲、乙两个数据结点，rear 指向乙。出队删除甲后，乙仍是尾结点，rear 无需移动。比较出队前后剩余结点，再按题干约定判断。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='带头结点的链队列中，front 指向保留的头结点，非空时 rear 指向最后一个数据结点，空队列约定 `rear=front` 且 `front->next=NULL`。当前有两个数据结点 q→r。只出队并释放 q 后，rear 应指向哪里？'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-01
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-01' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='插入位序为 i 时，原第 i 至第 n 个元素各后移一格，共 `n-i+1` 个。本题 `i=1`、`n=5`，所以原第 1 至第 5 个都移动，共 5 个。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='插入位序为 i 时，原第 i 至第 n 个元素各后移一格，共 `n-i+1` 个。本题 `i=1`、`n=5`，所以原第 1 至第 5 个都移动，共 5 个。'
WHERE id=@b13_c;

-- ll-02
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-02' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='删除第 i 个元素需要前移原第 `i+1` 至第 n 个元素，共 `n-i` 个；这里为 6-4=2 个。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='删除第 i 个元素需要前移原第 `i+1` 至第 n 个元素，共 `n-i` 个；这里为 6-4=2 个。'
WHERE id=@b13_c;

-- ll-03
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-03' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='逻辑第 i 个元素对应 `data[i-1]`；第 3 个元素 5 在 `data[2]`。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='逻辑第 i 个元素对应 `data[i-1]`；第 3 个元素 5 在 `data[2]`。'
WHERE id=@b13_c;
UPDATE question_understanding_hint SET hint_text='例如另一张表有 6 个元素，逻辑第 5 个元素对应 `data[4]`；从逻辑位序到数组下标的换算规则是什么？'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='C 数组 data 依次存放线性表的 8、3、5、1，数组下标从 0 开始。`data[2]` 存放的是逻辑上第几个元素？'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-04
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-04' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='第 1 个数据结点的前驱是头结点，所以 `p=L`；令 `s->next=p->next`，再令 `p->next=s`。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='第 1 个数据结点的前驱是头结点，所以 `p=L`；令 `s->next=p->next`，再令 `p->next=s`。',
    solution_viz_title='以 4→7→9 插入 6（第 1 个位置）为例：演示选定前驱后的插入结果'
WHERE id=@b13_c;
UPDATE question_understanding_hint SET hint_text='插入第 i 个位置，要让 p 停在原第 `i-1` 个结点；带头结点时它记第 0 个。'
WHERE content_id=@b13_c AND hint_level=2;
UPDATE ai_variant_question SET viz_title='以 4→7→9 插入 6（第 3 个位置）为例：演示选定前驱后的插入结果'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-05
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-05' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='先执行 `s->next=p->next` 保存 q，再执行 `p->next=s`；从头结点出发变为 p→s→q。'
WHERE id=@b13_q;
UPDATE question_option SET content='先 `p->next=s`，再 `s->next=p->next`'
WHERE question_id=@b13_q AND option_label='A' AND deleted=0;
UPDATE question_option SET content='先 `s->next=p->next`，再 `p->next=s`'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_option SET content='先 `p->next=q->next`，再 `s->next=q`'
WHERE question_id=@b13_q AND option_label='C' AND deleted=0;
UPDATE question_option SET content='先 `s->next=p`，再 `p->next=s`'
WHERE question_id=@b13_q AND option_label='D' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='先执行 `s->next=p->next` 保存 q，再执行 `p->next=s`；从头结点出发变为 p→s→q。',
    solution_viz_title='以 2→4→8 插入 6（第 2 个位置）为例：先接后继再改前驱'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='先令 `p->next=s` 后，再读 `p->next` 得到的是 s 自己，会令 `s->next=s`，原 q 从头结点不可达。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='把 p 所指结点的 next 改为 `q->next` 会跳过 q；再令 `s->next=q` 也没有把 s 接到 p 后面。指针变量 p 本身没有移动。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='C' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='`s->next=p` 会形成回指，无法按原顺序接上 q。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='逐句跟踪赋值：每一步后，旧后继的地址保存在哪里？再读 `p->next` 时，它与赋值前是否仍相同？'
WHERE content_id=@b13_c AND hint_level=2;
UPDATE ai_variant_question SET question_content='同一带头结点链表中，改为把 s 插在尾结点 p 之后（原 `p->next=NULL`）。哪组赋值顺序仍正确？',
    analysis='仍先令 `s->next` 取原 `p->next`，即 NULL，再令 `p->next=s`；这样新尾结点的指针域确定为空。',
    options_json=CAST('[{"label":"A","content":"先 `p->next=s`，再 `s->next=p->next`"},{"label":"B","content":"先 `s->next=p->next`，再 `p->next=s`"},{"label":"C","content":"只令 `s->next=p`，不改 p"},{"label":"D","content":"只令 `p->next=s`，不初始化 `s->next`"}]' AS JSON),
    viz_title='以 2→4→8 插入 6（第 4 个位置）为例：先接后继再改前驱'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-06
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-06' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='带头结点的单链表 2→4→8 中，p 指向 2，`q=p->next` 指向 4。要删除 q 且保留后继，释放 q 前必须怎样更新链接？',
    analysis='先保存 `q= p->next`，令 `p->next=q->next`，再 `free(q)`；原结点 8 仍可达。'
WHERE id=@b13_q;
UPDATE question_option SET content='令 `p->next=NULL`'
WHERE question_id=@b13_q AND option_label='A' AND deleted=0;
UPDATE question_option SET content='令 `q->next=p`'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_option SET content='令 `p->next=q->next`'
WHERE question_id=@b13_q AND option_label='C' AND deleted=0;
UPDATE question_option SET content='令 `p=q->next`'
WHERE question_id=@b13_q AND option_label='D' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='先保存 `q= p->next`，令 `p->next=q->next`，再 `free(q)`；原结点 8 仍可达。',
    solution_viz_title='以 2→4→8 删除第 2 个数据结点为例：先接后继再释放'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='令 `p->next=NULL` 会让 8 从头结点不可达。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='`q->next=p` 会让待释放的 q 指回 p，没把 p 正确接到 8。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='B' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='令 `p=q->next` 只改指针变量 p，不会修改结点 2 的 next 域。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE ai_variant_question SET question_content='带头结点的单链表为 2→4→8→NULL，p 指向 4，`q=p->next` 指向尾结点 8。正确删除并释放 q 后，`p->next` 的值是什么？',
    analysis='原 `q->next=NULL`，删除前令 `p->next=q->next`，再释放 q；结点 4 成为尾结点，其 next 为 NULL。',
    viz_title='以 2→4→8 删除第 3 个数据结点为例：删除尾结点后的链接'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- ll-07
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:ll-07' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET analysis='逆置循环结束后 prev 指向新的首元结点 8；必须令 `L->next=prev`，使头结点接上 8→4→2。'
WHERE id=@b13_q;
UPDATE question_option SET content='令 `prev->next=L`'
WHERE question_id=@b13_q AND option_label='A' AND deleted=0;
UPDATE question_option SET content='令 `L=prev`'
WHERE question_id=@b13_q AND option_label='B' AND deleted=0;
UPDATE question_option SET content='令 `L->next=NULL`'
WHERE question_id=@b13_q AND option_label='C' AND deleted=0;
UPDATE question_option SET content='令 `L->next=prev`'
WHERE question_id=@b13_q AND option_label='D' AND deleted=0;
UPDATE question_understanding_content SET solution_explanation='逆置循环结束后 prev 指向新的首元结点 8；必须令 `L->next=prev`，使头结点接上 8→4→2。',
    solution_viz_title='以 2→4→8 逆置为例：谁来接回新首元'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='循环后数据链为 8→4→2→NULL，头结点仍指向 2。令 `8->next=L` 会覆盖 8→4，得到 8→头结点→2→NULL，并未形成环；头结点也没有接到新首元 8。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='L 是指向头结点的变量；仅令 `L=prev` 会丢开原头结点，不符合带头结点结构。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='B' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='令头结点 `next=NULL` 会使所有数据结点从 L 不可达。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='C' AND deleted=0);
UPDATE ai_variant_question SET question_content='带头结点的单链表 L 为空，`L->next=NULL`。逆置算法令 `prev=NULL`，从首元结点开始循环反转 next，循环结束后统一执行 `L->next=prev`。最终 `L->next` 指向哪里？',
    analysis='空表没有数据结点，循环一次也不执行，prev 保持 NULL；最后 `L->next=prev` 仍为 NULL，保留原头结点。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- qu-02
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:qu-02' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='长度为 `M=8` 的循环数组队列保留一个空槽。front 指向队头、rear 指向下一空位。若 `front=6`、`rear=2`，队中有几个元素？'
WHERE id=@b13_q;
UPDATE question_understanding_misconception SET explanation='`rear=2` 只是物理下标，不能代表队列元素个数。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='从下标 6、7、0、1 共四个有效位置，`rear=2` 是下一空位。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='B' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='`front=6` 是物理起点，不是元素数目；队列已跨过数组末端。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='从 front 数到 rear 之前，或使用 `(rear-front+M)%M`。'
WHERE content_id=@b13_c AND hint_level=2;
UPDATE question_understanding_hint SET hint_text='例如 `M=5`、`front=4`、`rear=1` 时，有下标 4、0 两个元素。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='循环数组队列长度 `M=8`，front 指向队头，rear 改为指向最后一个有效元素。用 count 区分满空：`count=0` 为空，`count=M` 为满。非空时元素占据从 front 到 rear 的连续循环区间（两端均计入）。若 `front=6`、`rear=2`，队中有几个元素？',
    analysis='有效下标为 6、7、0、1、2，共 5 个；非空时长度为 `(rear-front+M)%M+1`，不能照搬 rear 指下一空位时的公式。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- qu-03
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:qu-03' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='长度为 `M=6` 的循环数组队列用“保留一个空槽”区分队满与队空，最多可同时存放几个元素？',
    analysis='该表示必须始终留下一个空槽，因此 `M=6` 时最多存 `M-1=5` 个元素。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='该表示必须始终留下一个空槽，因此 `M=6` 时最多存 `M-1=5` 个元素。'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='若 6 个槽全放满，`front==rear` 会与空队列状态混淆。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='C' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='想想队空与队满若都满足 `front==rear`，会怎样区分。'
WHERE content_id=@b13_c AND hint_level=1;
UPDATE question_understanding_hint SET hint_text='例如 `M=3` 的循环队列必须留 1 个空槽，因此最多放 2 个元素。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='循环数组队列长度 `M=6`，改用计数器 count 区分满空：`count=0` 为空，`count=M` 为满，不保留空槽。最多可同时存放几个元素？',
    analysis='计数器可以区分同样指针位置下的满与空，无需浪费一个槽，6 个物理位置均可存元素，有效容量为 `M=6`。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- qu-04
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:qu-04' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='循环队列 `M=5`，`front=1`、`rear=4`，rear 指向下一空位。队列未满，执行一次 `EnQueue(x)` 后 rear 为多少？',
    analysis='先把 x 写入 `data[4]`，再令 `rear=(4+1)%5=0`；front 仍为 1。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='先把 x 写入 `data[4]`，再令 `rear=(4+1)%5=0`；front 仍为 1。'
WHERE id=@b13_c;
UPDATE question_understanding_hint SET hint_text='例如 `M=4`、`rear=3` 时入队后，新 `rear=(3+1)%4`。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='同样 `M=5`、`front=1`，只把入队前 rear 改为 3。执行一次 `EnQueue(x)` 后 rear 为多少？',
    analysis='把 x 写在 `data[3]`，随后 `rear=(3+1)%5=4`。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- qu-05
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:qu-05' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='循环队列 `M=5`，front 指向队头元素、rear 指向下一空位；空队列初始 `front=rear=3`。向空队列入队 x 后，(front,rear) 应为哪一对？'
WHERE id=@b13_q;
UPDATE question_understanding_hint SET hint_text='空队列的 `front=rear`；入队后新元素位于原 rear 指向的位置。'
WHERE content_id=@b13_c AND hint_level=1;
UPDATE question_understanding_hint SET hint_text='例如空队列 `front=rear=0`，放入甲后队头仍在 0，rear 前进到 1。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='循环数组队列 `M=5`，front 指向队头元素，rear 指向下一空位，保留一个空槽区分满空；初始空队列 `front=rear=4`。入队 x 后 (front,rear) 为哪一对？',
    analysis='x 写入下标 4，front 保持 4，`rear=(4+1)%5=0`。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- st-02
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:st-02' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='某顺序栈的 top 指向当前栈顶元素，空栈时 `top=-1`。连续入栈 3 个元素，再出栈 1 个元素后，top 的值是多少？'
WHERE id=@b13_q;
UPDATE question_understanding_misconception SET explanation='入栈三次后 `top=2`，只出栈一次，所以 `top=1`。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='`top=2` 是三次入栈后、尚未出栈时的值。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='C' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='例如空栈入栈两次后 `top=1`，再出栈一次就回到 0。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='某顺序栈的 top 指向下一个可写空位，空栈时 `top=0`，容量充足。连续入栈 3 个元素，再出栈 1 个元素后，top 的值是多少？',
    analysis='top 表示下一空位，也等于当前元素个数；由 0 入栈三次到 3，再出栈一次到 2。当前栈顶元素在下标 `top-1=1`。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- st-03
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:st-03' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='栈中从底到顶为 2、5、7。先执行 `GetTop(S)`（只读取不移除），再执行一次 `Pop(S)`。两次得到的元素依次是什么？',
    analysis='`GetTop(S)` 返回 7 且栈不变；`Pop(S)` 随后弹出同一个栈顶 7。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='`GetTop(S)` 返回 7 且栈不变；`Pop(S)` 随后弹出同一个栈顶 7。'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET title='误以为 `GetTop` 会弹出',
    explanation='`GetTop` 只读 7，不改变栈；紧接着 `Pop` 仍弹出 7。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='B' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='确认 `GetTop` 与 `Pop` 是否改变栈。'
WHERE content_id=@b13_c AND hint_level=1;
UPDATE ai_variant_question SET question_content='栈中从底到顶为 2、5、7。先执行 `Pop(S)`（弹出并返回栈顶元素），再执行 `GetTop(S)`（只读取不移除栈顶）。两次返回的元素依次是什么？',
    analysis='`Pop` 先返回并移除 7，栈变为底 2、5 顶；`GetTop` 随后返回 5，栈保持不变。',
    changed_condition='初始栈不变，只把 `GetTop` 与 `Pop` 的先后顺序交换。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- st-04
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:st-04' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='用栈检查括号串 `([)]`。从左到右读到第 3 个字符 )，尚未对这个字符执行任何栈操作时，栈顶是哪一个字符？'
WHERE id=@b13_q;
UPDATE ai_variant_question SET question_content='用栈检查括号串 `([])]`。从左到右读到第 5 个字符 ]，尚未对这个字符执行任何栈操作时，栈顶是什么？',
    analysis='前四个字符 `([])` 已匹配完毕：先用 `]` 弹出 `[`，再用 `)` 弹出 `(`。读到第五个字符 `]` 时栈已空，该右括号没有可匹配的左括号。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

-- st-06
SET @b13_q=(SELECT id FROM question WHERE source_reference='m5-draft:st-06' AND deleted=0);
SET @b13_c=(SELECT id FROM question_understanding_content WHERE question_id=@b13_q AND content_version=1);
UPDATE question SET content='空栈依次执行 `Push(1)`、`Push(2)`、`Pop`、`Push(3)`、`Pop`、`Pop`。三次 `Pop` 的返回值依次是什么？',
    analysis='第一次 `Pop` 弹出 2；此后 `Push(3)`，第二次 `Pop` 弹出 3；最后弹出剩余的 1。'
WHERE id=@b13_q;
UPDATE question_understanding_content SET solution_explanation='第一次 `Pop` 弹出 2；此后 `Push(3)`，第二次 `Pop` 弹出 3；最后弹出剩余的 1。'
WHERE id=@b13_c;
UPDATE question_understanding_misconception SET explanation='第一次 `Pop` 取的是后压入的 2，而非最早入栈的 1。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='A' AND deleted=0);
UPDATE question_understanding_misconception SET title='忘记 `Push(3)` 改变栈顶'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='B' AND deleted=0);
UPDATE question_understanding_misconception SET explanation='第一次 `Pop` 发生在 `Push(3)` 之前，不可能先弹出 3。'
WHERE content_id=@b13_c AND option_id=(SELECT id FROM question_option WHERE question_id=@b13_q AND option_label='D' AND deleted=0);
UPDATE question_understanding_hint SET hint_text='逐步画出栈，不要只看所有 `Push` 的总顺序。'
WHERE content_id=@b13_c AND hint_level=1;
UPDATE question_understanding_hint SET hint_text='在每一次 `Pop` 发生的时刻记录当时的栈顶。'
WHERE content_id=@b13_c AND hint_level=2;
UPDATE question_understanding_hint SET hint_text='例如 `Push(甲)`、`Push(乙)`、`Pop`、`Push(丙)`、`Pop` 会先后得到乙和丙。'
WHERE content_id=@b13_c AND hint_level=3;
UPDATE ai_variant_question SET question_content='仍从空栈开始，改为先 `Push(1)`、`Push(2)`、`Push(3)`，随后连续 `Pop` 三次。返回值依次是什么？',
    changed_condition='第一次 `Pop` 延后到 `Push(3)` 之后，其余元素及操作次数不变。'
WHERE asset_id IN (SELECT id FROM question_ai_asset WHERE question_id=@b13_q AND asset_type='VARIANT' AND content='M5 待审核草稿变式');

UPDATE question_understanding_content c JOIN question q ON q.id=c.question_id
SET c.question_fingerprint=SHA2(CONCAT(q.content,CHAR(10),q.question_type,CHAR(10),
  (SELECT GROUP_CONCAT(CONCAT(o.option_label,CHAR(9),o.content,CHAR(9),o.is_correct,CHAR(10))
   ORDER BY o.sort_order,o.id SEPARATOR '') FROM question_option o WHERE o.question_id=q.id AND o.deleted=0)),256)
WHERE q.source_reference LIKE 'm5-draft:%' AND c.content_version=1;

-- 审核依据：C3 逐题结论与 B13 独立冷读复核；内容保留 AI 来源。
UPDATE question_understanding_content c JOIN question q ON q.id=c.question_id
SET c.review_status='REVIEWED',c.reviewed_by='AI 初稿 + Codex 冷读 + Claude 页面审核',c.reviewed_time=CURRENT_TIMESTAMP
WHERE c.content_version=1 AND (q.source_reference IN (
    'm5-draft:ll-09',
    'm5-draft:ll-10',
    'm5-draft:st-08',
    'm5-draft:qu-07',
    'm5-draft:ll-01',
    'm5-draft:ll-02',
    'm5-draft:ll-03',
    'm5-draft:ll-04',
    'm5-draft:ll-05',
    'm5-draft:ll-06',
    'm5-draft:ll-07',
    'm5-draft:ll-08',
    'm5-draft:qu-01',
    'm5-draft:qu-02',
    'm5-draft:qu-03',
    'm5-draft:qu-04',
    'm5-draft:qu-05',
    'm5-draft:qu-06',
    'm5-draft:st-01',
    'm5-draft:st-02',
    'm5-draft:st-03',
    'm5-draft:st-04',
    'm5-draft:st-05',
    'm5-draft:st-06',
    'm5-draft:st-07'
  ) OR c.source_title IN ('2026 年 408 真题 第 3 题','系统种子生成：合法出栈序列')) AND c.review_status='PENDING';

UPDATE question_understanding_hint h
JOIN question_understanding_content c ON c.id=h.content_id
JOIN question q ON q.id=c.question_id
SET h.review_status='REVIEWED',h.reviewed_by=c.reviewed_by,h.reviewed_time=CURRENT_TIMESTAMP
WHERE c.content_version=1 AND (q.source_reference IN (
    'm5-draft:ll-09',
    'm5-draft:ll-10',
    'm5-draft:st-08',
    'm5-draft:qu-07',
    'm5-draft:ll-01',
    'm5-draft:ll-02',
    'm5-draft:ll-03',
    'm5-draft:ll-04',
    'm5-draft:ll-05',
    'm5-draft:ll-06',
    'm5-draft:ll-07',
    'm5-draft:ll-08',
    'm5-draft:qu-01',
    'm5-draft:qu-02',
    'm5-draft:qu-03',
    'm5-draft:qu-04',
    'm5-draft:qu-05',
    'm5-draft:qu-06',
    'm5-draft:st-01',
    'm5-draft:st-02',
    'm5-draft:st-03',
    'm5-draft:st-04',
    'm5-draft:st-05',
    'm5-draft:st-06',
    'm5-draft:st-07'
  ) OR c.source_title IN ('2026 年 408 真题 第 3 题','系统种子生成：合法出栈序列')) AND c.review_status='REVIEWED' AND h.review_status='PENDING';

UPDATE ai_variant_question v
JOIN question_ai_asset a ON a.id=v.asset_id AND a.asset_type='VARIANT'
JOIN question_understanding_content c ON c.question_id=a.question_id
JOIN question q ON q.id=c.question_id
SET v.review_status='APPROVED',v.review_note=c.reviewed_by,v.reviewed_by=0,v.reviewed_time=CURRENT_TIMESTAMP
WHERE c.content_version=1 AND (q.source_reference IN (
    'm5-draft:ll-09',
    'm5-draft:ll-10',
    'm5-draft:st-08',
    'm5-draft:qu-07',
    'm5-draft:ll-01',
    'm5-draft:ll-02',
    'm5-draft:ll-03',
    'm5-draft:ll-04',
    'm5-draft:ll-05',
    'm5-draft:ll-06',
    'm5-draft:ll-07',
    'm5-draft:ll-08',
    'm5-draft:qu-01',
    'm5-draft:qu-02',
    'm5-draft:qu-03',
    'm5-draft:qu-04',
    'm5-draft:qu-05',
    'm5-draft:qu-06',
    'm5-draft:st-01',
    'm5-draft:st-02',
    'm5-draft:st-03',
    'm5-draft:st-04',
    'm5-draft:st-05',
    'm5-draft:st-06',
    'm5-draft:st-07'
  ) OR c.source_title IN ('2026 年 408 真题 第 3 题','系统种子生成：合法出栈序列')) AND c.review_status='REVIEWED' AND v.review_status='PENDING'
  AND a.content IN ('M5 待审核草稿变式','题目理解系统种子变式');

UPDATE question q JOIN question_understanding_content c ON c.question_id=q.id AND c.content_version=1
SET q.visibility='PUBLIC'
WHERE q.source_reference IN (
    'm5-draft:ll-09',
    'm5-draft:ll-10',
    'm5-draft:st-08',
    'm5-draft:qu-07',
    'm5-draft:ll-01',
    'm5-draft:ll-02',
    'm5-draft:ll-03',
    'm5-draft:ll-04',
    'm5-draft:ll-05',
    'm5-draft:ll-06',
    'm5-draft:ll-07',
    'm5-draft:ll-08',
    'm5-draft:qu-01',
    'm5-draft:qu-02',
    'm5-draft:qu-03',
    'm5-draft:qu-04',
    'm5-draft:qu-05',
    'm5-draft:qu-06',
    'm5-draft:st-01',
    'm5-draft:st-02',
    'm5-draft:st-03',
    'm5-draft:st-04',
    'm5-draft:st-05',
    'm5-draft:st-06',
    'm5-draft:st-07'
  ) AND c.review_status='REVIEWED' AND q.visibility='DRAFT';

-- 逐题核对计算机考研杂货铺的收录内容：题号 3、中序、层序、四个选项、答案 C 均一致。
UPDATE question_understanding_content
SET source_verification='VERIFIED',source_url='https://csgraduates.com/study_methods/408quiz/2026/',
    source_verified_by='Codex 内容核对；依据：计算机考研杂货铺收录（非官方原卷）',source_verified_time=CURRENT_TIMESTAMP
WHERE source_title='2026 年 408 真题 第 3 题' AND content_version=1 AND review_status='REVIEWED';
