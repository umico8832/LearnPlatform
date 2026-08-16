package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.dto.KnowledgeGraphVO;
import com.learnplatform.dto.KnowledgeGraphVO.CourseInfo;
import com.learnplatform.dto.KnowledgeGraphVO.GraphEdge;
import com.learnplatform.dto.KnowledgeGraphVO.GraphNode;
import com.learnplatform.entity.*;
import com.learnplatform.mapper.*;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

/**
 * 知识图谱服务
 * 构建知识点关系图谱数据，关联用户练习表现
 */
@Service
public class KnowledgeGraphService {

    private final KnowledgePointMapper knowledgePointMapper;
    private final CourseMapper courseMapper;
    private final QuestionKnowledgePointMapper questionKpMapper;
    private final PracticeRecordMapper practiceRecordMapper;
    private final WrongQuestionMapper wrongQuestionMapper;

    public KnowledgeGraphService(KnowledgePointMapper knowledgePointMapper,
                                 CourseMapper courseMapper,
                                 QuestionKnowledgePointMapper questionKpMapper,
                                 PracticeRecordMapper practiceRecordMapper,
                                 WrongQuestionMapper wrongQuestionMapper) {
        this.knowledgePointMapper = knowledgePointMapper;
        this.courseMapper = courseMapper;
        this.questionKpMapper = questionKpMapper;
        this.practiceRecordMapper = practiceRecordMapper;
        this.wrongQuestionMapper = wrongQuestionMapper;
    }

    /**
     * 获取知识图谱数据
     *
     * @param userId   当前用户 ID
     * @param courseId 可选课程筛选
     * @return 图谱节点和边
     */
    @Cacheable(value = "knowledgeGraph", key = "#userId + ':' + #courseId")
    public KnowledgeGraphVO getKnowledgeGraph(Long userId, Long courseId) {
        // 1. 查询知识点
        LambdaQueryWrapper<KnowledgePoint> kpWrapper = new LambdaQueryWrapper<>();
        if (courseId != null) {
            kpWrapper.eq(KnowledgePoint::getCourseId, courseId);
        }
        kpWrapper.orderByAsc(KnowledgePoint::getSortOrder);
        List<KnowledgePoint> allKps = knowledgePointMapper.selectList(kpWrapper);

        if (allKps.isEmpty()) {
            KnowledgeGraphVO empty = new KnowledgeGraphVO();
            empty.setNodes(Collections.emptyList());
            empty.setEdges(Collections.emptyList());
            empty.setCourses(Collections.emptyList());
            return empty;
        }

        // 2. 查询相关课程
        Set<Long> courseIds = allKps.stream()
                .map(KnowledgePoint::getCourseId)
                .filter(Objects::nonNull)
                .collect(Collectors.toSet());

        List<Course> courses = courseMapper.selectBatchIds(courseIds);
        Map<Long, String> courseNameMap = courses.stream()
                .collect(Collectors.toMap(Course::getId, Course::getName, (a, b) -> a));

        // 3. 构建题目→知识点映射
        Set<Long> kpIds = allKps.stream()
                .map(KnowledgePoint::getId)
                .collect(Collectors.toSet());

        LambdaQueryWrapper<QuestionKnowledgePoint> qkpWrapper = new LambdaQueryWrapper<>();
        qkpWrapper.in(QuestionKnowledgePoint::getKnowledgePointId, kpIds);
        List<QuestionKnowledgePoint> qkpList = questionKpMapper.selectList(qkpWrapper);

        // 知识点ID → 题目ID集合
        Map<Long, Set<Long>> kpToQuestionIds = new HashMap<>();
        // 题目ID → 知识点ID集合
        Map<Long, Set<Long>> questionToKpIds = new HashMap<>();
        for (QuestionKnowledgePoint qkp : qkpList) {
            kpToQuestionIds.computeIfAbsent(qkp.getKnowledgePointId(), k -> new HashSet<>())
                    .add(qkp.getQuestionId());
            questionToKpIds.computeIfAbsent(qkp.getQuestionId(), k -> new HashSet<>())
                    .add(qkp.getKnowledgePointId());
        }

        // 4. 查询用户在相关题目上的练习记录
        Set<Long> allQuestionIds = kpToQuestionIds.values().stream()
                .flatMap(Set::stream)
                .collect(Collectors.toSet());

        // 题目ID → 练习记录列表
        Map<Long, List<PracticeRecord>> questionRecords = new HashMap<>();
        if (!allQuestionIds.isEmpty()) {
            LambdaQueryWrapper<PracticeRecord> prWrapper = new LambdaQueryWrapper<>();
            prWrapper.eq(PracticeRecord::getUserId, userId)
                    .in(PracticeRecord::getQuestionId, allQuestionIds);
            List<PracticeRecord> records = practiceRecordMapper.selectList(prWrapper);
            questionRecords = records.stream()
                    .collect(Collectors.groupingBy(PracticeRecord::getQuestionId));
        }

        // 5. 查询用户错题
        LambdaQueryWrapper<WrongQuestion> wqWrapper = new LambdaQueryWrapper<>();
        wqWrapper.eq(WrongQuestion::getUserId, userId);
        if (!allQuestionIds.isEmpty()) {
            wqWrapper.in(WrongQuestion::getQuestionId, allQuestionIds);
        }
        List<WrongQuestion> wrongQuestions = wrongQuestionMapper.selectList(wqWrapper);
        Set<Long> wrongQuestionIds = wrongQuestions.stream()
                .map(WrongQuestion::getQuestionId)
                .collect(Collectors.toSet());

        // 6. 计算每个知识点的练习统计
        Map<Long, int[]> kpStats = new HashMap<>(); // [totalPractice, correctCount, wrongCount]
        for (Map.Entry<Long, Set<Long>> entry : kpToQuestionIds.entrySet()) {
            Long kpId = entry.getKey();
            int totalPractice = 0;
            int correctCount = 0;
            int wrongCount = 0;

            for (Long qId : entry.getValue()) {
                List<PracticeRecord> records = questionRecords.getOrDefault(qId, Collections.emptyList());
                totalPractice += records.size();
                correctCount += (int) records.stream()
                        .filter(r -> r.getIsCorrect() != null && r.getIsCorrect() == 1).count();
                if (wrongQuestionIds.contains(qId)) {
                    wrongCount++;
                }
            }

            kpStats.put(kpId, new int[]{totalPractice, correctCount, wrongCount});
        }

        // 7. 构建图谱节点
        List<GraphNode> nodes = new ArrayList<>();
        for (KnowledgePoint kp : allKps) {
            GraphNode node = new GraphNode();
            node.setId(kp.getId());
            node.setName(kp.getName());
            node.setCourseId(kp.getCourseId());
            node.setCourseName(courseNameMap.getOrDefault(kp.getCourseId(), "未知课程"));
            node.setParentId(kp.getParentId());
            node.setCategory(courseNameMap.getOrDefault(kp.getCourseId(), "未知课程"));

            // 判断节点类型
            boolean hasChildren = allKps.stream().anyMatch(k -> kp.getId().equals(k.getParentId()));
            boolean isRoot = kp.getParentId() == null;
            if (isRoot && hasChildren) {
                node.setNodeType("parent");
            } else if (isRoot) {
                node.setNodeType("leaf");
            } else if (hasChildren) {
                node.setNodeType("parent");
            } else {
                node.setNodeType("leaf");
            }

            // 练习统计
            int[] stats = kpStats.getOrDefault(kp.getId(), new int[]{0, 0, 0});
            int totalPractice = stats[0];
            int correctCount = stats[1];
            int wrongCount = stats[2];

            node.setPracticeCount(totalPractice);
            node.setWrongCount(wrongCount);

            if (totalPractice > 0) {
                double accuracy = (double) correctCount / totalPractice * 100;
                node.setAccuracy(Math.round(accuracy * 10.0) / 10.0);

                // 掌握程度
                if (accuracy >= 70) {
                    node.setMasteryLevel(3); // 已掌握
                } else if (accuracy >= 50) {
                    node.setMasteryLevel(2); // 需复习
                } else {
                    node.setMasteryLevel(1); // 薄弱
                }
            } else {
                node.setAccuracy(0);
                node.setMasteryLevel(0); // 未练习
            }

            nodes.add(node);
        }

        // 8. 构建图谱边（父子关系）
        List<GraphEdge> edges = new ArrayList<>();
        for (KnowledgePoint kp : allKps) {
            if (kp.getParentId() != null) {
                edges.add(new GraphEdge(kp.getParentId(), kp.getId(), "parent-child"));
            }
        }

        // 9. 课程信息
        List<CourseInfo> courseInfos = courses.stream()
                .map(c -> new CourseInfo(c.getId(), c.getName()))
                .collect(Collectors.toList());

        KnowledgeGraphVO result = new KnowledgeGraphVO();
        result.setNodes(nodes);
        result.setEdges(edges);
        result.setCourses(courseInfos);
        return result;
    }
}