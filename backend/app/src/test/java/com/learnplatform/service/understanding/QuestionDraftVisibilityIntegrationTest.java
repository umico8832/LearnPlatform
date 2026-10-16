package com.learnplatform.service.understanding;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.AiAssetType;
import com.learnplatform.dto.PracticeSubmitRequest;
import com.learnplatform.service.AiAssetEngagementService;
import com.learnplatform.service.ExamPaperViewService;
import com.learnplatform.service.FavoriteService;
import com.learnplatform.service.GlobalSearchService;
import com.learnplatform.service.LearningQuestionErrorAnalysisService;
import com.learnplatform.service.PracticeAnswerService;
import com.learnplatform.service.PracticeQuestionQueryService;
import com.learnplatform.service.QuestionService;
import com.learnplatform.service.ReviewScheduleQueryService;
import com.learnplatform.service.WrongQuestionService;
import java.util.List;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.mybatis.spring.SqlSessionTemplate;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class QuestionDraftVisibilityIntegrationTest extends IntegrationTestBase {
    @Autowired JdbcTemplate db;
    @Autowired ObjectMapper json;
    @Autowired QuestionService questions;
    @Autowired PracticeQuestionQueryService practice;
    @Autowired PracticeAnswerService answers;
    @Autowired GlobalSearchService search;
    @Autowired WrongQuestionService wrong;
    @Autowired ReviewScheduleQueryService reviews;
    @Autowired QuestionUnderstandingService formal;
    @Autowired QuestionUnderstandingPreviewService preview;
    @Autowired FavoriteService favorites;
    @Autowired LearningQuestionErrorAnalysisService errors;
    @Autowired ExamPaperViewService papers;
    @Autowired SqlSessionTemplate sqlSession;
    @Autowired AiAssetEngagementService assets;
    @Autowired com.learnplatform.service.StatisticsService stats;

    @Test void everyMigratedDraftIsHiddenFromLearnersAndFullyPreviewableOnlyByAdmin() throws Exception {
        var rows = db.queryForList("SELECT id,content,course_id FROM question WHERE source_reference LIKE 'm5-draft:%'");
        assertEquals(25, rows.size());
        for (var row : rows) {
            long id = ((Number) row.get("id")).longValue();
            long course = ((Number) row.get("course_id")).longValue();
            assertNotFound(() -> questions.getEnabledQuestionById(id, 1L));
            assertNotFound(() -> formal.question(1L, id));
            assertNotFound(() -> preview.preview(1L, "USER", id, json.createObjectNode()));
            assertNotFound(() -> favorites.addFavorite(1L, id));
            assertNotFound(() -> assets.recordAssetView(id, AiAssetType.VARIANT, 1L));
            assertNotFound(() -> assets.submitVariantAnswer(id, 1L, "A"));
            assertNotFound(() -> assets.completeVariantTraining(id, 1L));
            assertTrue(questions.getEnabledQuestionPage(1, 50, null, course, null, id, null).getRecords().isEmpty());
            assertTrue(search.search(row.get("content").toString(), 20).getQuestions().isEmpty());
            assertTrue(practice.getPracticeQuestions(course, null, null, null, 50).stream().noneMatch(q -> q.getId() == id));
            var request = new PracticeSubmitRequest();
            request.setQuestionId(id); request.setUserAnswer("A");
            assertNotFound(() -> answers.submitAnswer(request, 1L));
            assertEquals("题目不存在", errors.analyzeQuestionError(1L, id).getQuestionContent());
            var state = preview.preview(1L, "ADMIN", id, json.createObjectNode());
            assertEquals(true, state.get("preview"));
            String correct = db.queryForObject("SELECT option_label FROM question_option WHERE question_id=? AND is_correct=1 AND deleted=0", String.class, id);
            String answer = "A".equals(correct) ? "B" : "A";
            var body = json.createObjectNode().put("answer", answer).put("hintLevel", 3).put("revealSolution", true);
            var full = preview.preview(1L, "ADMIN", id, body);
            assertEquals(3, ((List<?>) full.get("hints")).size());
            assertNotNull(full.get("solution")); assertNotNull(full.get("variant"));
        }
        assertEquals(0, db.queryForObject("SELECT COUNT(*) FROM practice_record WHERE question_id IN (SELECT id FROM question WHERE visibility='DRAFT')", Integer.class));
    }

    @Test void evenExistingWrongAndReviewReferencesCannotExposeDraftsOrCountThem() {
        long id = db.queryForObject("SELECT id FROM question WHERE source_reference='m5-draft:ll-01'", Long.class);
        db.update("INSERT INTO wrong_question(user_id,question_id,wrong_count,mastery_level,last_wrong_answer) VALUES (1,?,1,0,'A')", id);
        db.update("INSERT INTO question_review_schedule(user_id,question_id,next_review_date,total_reviews) VALUES (1,?,CURRENT_DATE,0)", id);
        assertEquals(0, wrong.getWrongQuestions(1L, 1, 10, null, id, null).getTotal());
        assertEquals(0, wrong.getWrongQuestionStats(1L).get("total"));
        assertTrue(reviews.getDueReviewCards(1L, null, id, 10).isEmpty());
        assertTrue(reviews.getAllReviewCards(1L, null).stream().noneMatch(v -> v.getQuestionId() == id));
        assertEquals(0, reviews.getReviewStats(1L).getTotalCards());
        assertEquals(0, stats.getUserStatistics(1L).getWrongQuestionCount());
    }

    @Test void reviewingChildrenAloneDoesNotPublishDraftAndPaperReferencesCannotLeakIt() {
        long id = db.queryForObject("SELECT id FROM question WHERE source_reference='m5-draft:ll-01'", Long.class);
        db.update("UPDATE question_understanding_content SET review_status='REVIEWED' WHERE question_id=?", id);
        sqlSession.clearCache();
        assertNotFound(() -> formal.question(1L, id));
        assertNotFound(() -> questions.getEnabledQuestionById(id, 1L));
        long paper = db.queryForObject("SELECT MIN(id) FROM exam_paper WHERE status=1 AND visibility='PUBLIC' AND deleted=0", Long.class);
        db.update("INSERT INTO exam_question(exam_paper_id,question_id,sort_order,score) VALUES (?,?,999,2)", paper, id);
        assertNotFound(() -> papers.getAccessiblePublishedById(paper, 1L));
    }

    private void assertNotFound(Runnable action) {
        assertEquals(1004, assertThrows(BusinessException.class, action::run).getCode());
    }
}
