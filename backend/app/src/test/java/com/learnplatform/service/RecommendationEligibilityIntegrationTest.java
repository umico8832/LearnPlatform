package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.LearningDiagnosisVO;
import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.dto.SimilarQuestionVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.PracticeRecord;
import com.learnplatform.entity.WrongQuestion;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertFalse;

@SpringBootTest
@ActiveProfiles("integration")
@Tag("integration")
class RecommendationEligibilityIntegrationTest extends IntegrationTestBase {
    private static final String PREFIX = "t14-08 推荐资格 ";
    private static final String USERNAME = "t14-08-recommendation-user";

    @Autowired private JdbcTemplate jdbc;
    @Autowired private LearningDiagnosisRecommendationService recommendationService;
    @Autowired private SimilarQuestionRecommendationService similarQuestionService;
    @Autowired private LearningDiagnosisService diagnosisService;
    @Autowired private QuestionService questionService;

    @AfterEach
    void cleanUp() {
        jdbc.update("DELETE FROM wrong_question WHERE question_id IN "
                + "(SELECT id FROM question WHERE content LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM question_knowledge_point WHERE question_id IN "
                + "(SELECT id FROM question WHERE content LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM question_option WHERE question_id IN "
                + "(SELECT id FROM question WHERE content LIKE ?)", PREFIX + "%");
        jdbc.update("DELETE FROM question WHERE content LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM knowledge_point WHERE name LIKE ?", PREFIX + "%");
        jdbc.update("DELETE FROM user WHERE username = ?", USERNAME);
    }

    @Test
    void diagnosisRecommendationsSkipInvalidCandidatesBeforeFiveSlotsAndRecoverWhenBasisReturns() {
        Long userId = insertUser(USERNAME);
        Long missing = insertQuestion("缺少正确项", "PUBLIC", null);
        Long blank = insertQuestion("空白正确标签", "PUBLIC", null);
        addOption(blank, "空白", "\t\n\r", 1, 0);
        Long deleted = insertQuestion("软删除正确项", "PUBLIC", null);
        addOption(deleted, "已删除", "A", 1, 1);
        Long otherUsersPrivate = insertQuestion("他人私有题", "PRIVATE", userId + 1000);
        addOption(otherUsersPrivate, "私有正确项", "A", 1, 0);
        Long ownPrivate = insertQuestion("本人私有题", "PRIVATE", userId);
        addOption(ownPrivate, "本人私有正确项", "A", 1, 0);
        Long valid = insertQuestion("有效候选", "PUBLIC", null);
        addOption(valid, "有效正确项", "A", 1, 0);
        Long secondValid = insertQuestion("后序有效候选", "PUBLIC", null);
        addOption(secondValid, "后序有效正确项", "A", 1, 0);

        List<WrongQuestion> repeatedWrongs = List.of(
                wrong(missing), wrong(blank), wrong(deleted), wrong(otherUsersPrivate),
                wrong(ownPrivate), wrong(valid), wrong(secondValid));
        assertEquals(List.of(ownPrivate, valid, secondValid), recommendationIds(recommendationService.recommend(
                userId, List.of(), repeatedWrongs, List.of(), Map.of())));

        addOption(missing, "恢复后的正确项", "A", 1, 0);
        assertEquals(List.of(missing, ownPrivate, valid, secondValid),
                recommendationIds(recommendationService.recommend(
                        userId, List.of(), repeatedWrongs, List.of(), Map.of())));
    }

    @Test
    void weakKnowledgePointAndSimilarRecommendationsFilterTargetsButKeepSourceUsableForLookup() {
        Long userId = insertUser(USERNAME);
        Long pointId = insertKnowledgePoint();
        Long source = insertQuestion("来源题可无答案", "PUBLIC", null);
        Long blank = insertQuestion("候选空白标签", "PUBLIC", null);
        addOption(blank, "空白", "\t\n\r", 1, 0);
        Long deleted = insertQuestion("候选软删除项", "PUBLIC", null);
        addOption(deleted, "删除", "A", 1, 1);
        Long valid = insertQuestion("候选有效题", "PUBLIC", null);
        addOption(valid, "有效", "A", 1, 0);
        Long otherUsersPrivate = insertQuestion("候选他人私有题", "PRIVATE", userId + 1000);
        addOption(otherUsersPrivate, "有效", "A", 1, 0);
        Long ownPrivate = insertQuestion("候选本人私有题", "PRIVATE", userId);
        addOption(ownPrivate, "有效", "A", 1, 0);
        for (Long questionId : List.of(source, blank, deleted, valid, otherUsersPrivate, ownPrivate)) {
            jdbc.update("INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (?, ?)",
                    questionId, pointId);
        }

        KnowledgePoint point = new KnowledgePoint();
        point.setId(pointId);
        point.setCourseId(1L);
        PracticeRecord wrongAttempt = new PracticeRecord();
        wrongAttempt.setQuestionId(source);
        wrongAttempt.setIsCorrect(0);
        wrongAttempt.setCreateTime(LocalDateTime.now());
        List<Long> recommendationIds = recommendationIds(recommendationService.recommend(
                userId, List.of(wrongAttempt, wrongAttempt), List.of(), List.of(point),
                Map.of(source, Set.of(pointId))));
        assertEquals(Set.of(valid, ownPrivate), Set.copyOf(recommendationIds));
        assertFalse(recommendationIds.contains(otherUsersPrivate));

        SimilarQuestionVO similar = similarQuestionService.findSimilarQuestions(userId, source, 10);
        assertEquals(source, similar.getSourceQuestionId());
        List<Long> similarIds = similar.getSimilarQuestions().stream()
                .map(SimilarQuestionVO.SimilarItem::getQuestionId).toList();
        assertTrue(similarIds.contains(valid));
        assertFalse(similarIds.contains(blank));
        assertFalse(similarIds.contains(deleted));
    }

    @Test
    void similarQuestionsRequireSharedKnowledgePointWhenSourceHasOne() {
        Long userId = insertUser(USERNAME);
        Long sharedPoint = insertKnowledgePoint();
        Long disjointPoint = insertKnowledgePoint("不相交知识点", 1L);
        Long source = insertQuestion("相似来源有知识点", "PUBLIC", null);
        Long sharedDifferentType = insertQuestion(
                "跨题型共享知识点", "PUBLIC", null, 1L, "TRUE_FALSE", 2);
        Long sameCourseDisjoint = insertQuestion("同课程不同知识点", "PUBLIC", null);
        Long crossCourseSameFormat = insertQuestion(
                "跨课程同题型", "PUBLIC", null, 2L, "SINGLE_CHOICE", 2);
        for (Long questionId : List.of(source, sameCourseDisjoint, crossCourseSameFormat)) {
            addOption(questionId, "有效正确项", "A", 1, 0);
        }
        addOption(sharedDifferentType, "TRUE", "A", 1, 0);
        jdbc.update("INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (?, ?)",
                source, sharedPoint);
        jdbc.update("INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (?, ?)",
                sharedDifferentType, sharedPoint);
        jdbc.update("INSERT INTO question_knowledge_point (question_id, knowledge_point_id) VALUES (?, ?)",
                sameCourseDisjoint, disjointPoint);

        List<Long> similarIds = similarQuestionService.findSimilarQuestions(userId, source, 10)
                .getSimilarQuestions().stream().map(SimilarQuestionVO.SimilarItem::getQuestionId).toList();

        assertEquals(List.of(sharedDifferentType), similarIds);
    }

    @Test
    void similarQuestionsWithoutKnowledgePointStayInSourceCourseAndNeedAnAnchor() {
        Long userId = insertUser(USERNAME);
        Long source = insertQuestion("相似来源无知识点", "PUBLIC", null);
        Long sameCourse = insertQuestion("同课程候选", "PUBLIC", null);
        Long crossCourse = insertQuestion("跨课程候选", "PUBLIC", null, 2L, "SINGLE_CHOICE", 2);
        for (Long questionId : List.of(source, sameCourse, crossCourse)) {
            addOption(questionId, "有效正确项", "A", 1, 0);
        }

        List<Long> similarIds = similarQuestionService.findSimilarQuestions(userId, source, 10)
                .getSimilarQuestions().stream().map(SimilarQuestionVO.SimilarItem::getQuestionId).toList();
        assertTrue(similarIds.contains(sameCourse));
        assertFalse(similarIds.contains(crossCourse));
    }

    @Test
    void questionOptionChangesEvictCachedDiagnosisAfterCommit() {
        Long userId = insertUser(USERNAME);
        Long questionId = insertQuestion("诊断缓存题", "PUBLIC", null);
        addOption(questionId, "初始正确项", "A", 1, 0);
        jdbc.update("INSERT INTO wrong_question "
                        + "(user_id, question_id, wrong_count, mastery_level, last_wrong_answer, deleted) "
                        + "VALUES (?, ?, 2, 0, 'B', 0)", userId, questionId);

        assertTrue(recommendationIds(diagnosisService.getDiagnosis(userId).getDailyRecommendations())
                .contains(questionId));

        questionService.updateQuestion(questionId, optionUpdate(0), 1L);

        assertFalse(recommendationIds(diagnosisService.getDiagnosis(userId).getDailyRecommendations())
                .contains(questionId));

        questionService.updateQuestion(questionId, optionUpdate(1), 1L);

        assertTrue(recommendationIds(diagnosisService.getDiagnosis(userId).getDailyRecommendations())
                .contains(questionId));
    }

    private List<Long> recommendationIds(List<LearningDiagnosisVO.RecommendedQuestion> recommendations) {
        return recommendations.stream().map(LearningDiagnosisVO.RecommendedQuestion::getQuestionId).toList();
    }

    private WrongQuestion wrong(Long questionId) {
        WrongQuestion wrong = new WrongQuestion();
        wrong.setQuestionId(questionId);
        wrong.setWrongCount(2);
        wrong.setMasteryLevel(0);
        return wrong;
    }

    private QuestionCreateRequest optionUpdate(int isCorrect) {
        QuestionCreateRequest.OptionItem option = new QuestionCreateRequest.OptionItem();
        option.setContent("更新选项");
        option.setOptionLabel("A");
        option.setIsCorrect(isCorrect);
        option.setSortOrder(1);
        QuestionCreateRequest request = new QuestionCreateRequest();
        request.setOptions(List.of(option));
        return request;
    }

    private Long insertUser(String username) {
        jdbc.update("INSERT INTO user (username, password, role, status, deleted) VALUES (?, 'test', 'USER', 1, 0)",
                username);
        return jdbc.queryForObject("SELECT id FROM user WHERE username = ?", Long.class, username);
    }

    private Long insertKnowledgePoint() {
        return insertKnowledgePoint("知识点", 1L);
    }

    private Long insertKnowledgePoint(String suffix, Long courseId) {
        String name = PREFIX + suffix;
        jdbc.update("INSERT INTO knowledge_point "
                        + "(name, description, course_id, parent_id, content_review_status, sort_order, deleted) "
                        + "VALUES (?, '集成测试', ?, 0, 'REVIEWED', 99, 0)",
                name, courseId);
        return jdbc.queryForObject("SELECT id FROM knowledge_point WHERE name = ?", Long.class, name);
    }

    private Long insertQuestion(String suffix, String visibility, Long ownerUserId) {
        return insertQuestion(suffix, visibility, ownerUserId, 1L, "SINGLE_CHOICE", 2);
    }

    private Long insertQuestion(
            String suffix, String visibility, Long ownerUserId, Long courseId, String type, int difficulty) {
        String content = PREFIX + suffix;
        jdbc.update("INSERT INTO question (content, question_type, course_id, difficulty, analysis, tags, score, "
                        + "status, visibility, owner_user_id, create_by, deleted) "
                        + "VALUES (?, ?, ?, ?, '测试解析', '集成测试', 2, 1, ?, ?, 1, 0)",
                content, type, courseId, difficulty, visibility, ownerUserId);
        return jdbc.queryForObject("SELECT id FROM question WHERE content = ?", Long.class, content);
    }

    private void addOption(Long questionId, String content, String label, int correct, int deleted) {
        jdbc.update("INSERT INTO question_option (question_id, content, option_label, is_correct, sort_order, deleted) "
                        + "VALUES (?, ?, ?, ?, 1, ?)",
                questionId, content, label, correct, deleted);
    }
}
