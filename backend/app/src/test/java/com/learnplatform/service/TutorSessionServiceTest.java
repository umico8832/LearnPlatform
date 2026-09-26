package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.config.AiConfig;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.dto.TutorLearningContextVO;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
import org.apache.ibatis.builder.MapperBuilderAssistant;
import com.baomidou.mybatisplus.core.MybatisConfiguration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TutorSessionServiceTest {
    @BeforeEach
    void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), TutorSession.class);
    }

    @Test void startsOnlyReviewedContentAndNeverReturnsCorrectOption() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        TutorLearningContextService contexts = mock(TutorLearningContextService.class);
        when(users.selectCount(any())).thenReturn(1L); KnowledgePoint point = new KnowledgePoint(); point.setId(3L); point.setCourseId(10L); point.setContentReviewStatus("REVIEWED"); when(points.selectById(3L)).thenReturn(point);
        TutorContent content = new TutorContent(); content.setId(8L); content.setTitle("ArrayStack"); content.setLessonJson("{\"summary\":\"x\"}"); content.setCheckJson("{\"id\":\"move-direction\",\"prompt\":\"如何搬移？\",\"options\":[{\"id\":\"RIGHT_TO_LEFT\",\"text\":\"从右向左\",\"isCorrect\":true}],\"correctOptionId\":\"RIGHT_TO_LEFT\",\"correctExplanation\":\"正确答案说明\",\"incorrectExplanation\":\"错误答案说明\"}"); when(contents.selectOne(any())).thenReturn(content);
        TutorLearningContextVO learningContext = new TutorLearningContextVO();
        learningContext.setPaperAnswerCount(3); learningContext.setUnresolvedWrongCount(2);
        when(contexts.summarize(7L, 10L, 3L)).thenReturn(learningContext);
        doAnswer(i -> { ((TutorSession) i.getArgument(0)).setId(9L); return 1; }).when(sessions).insert(any());
        AiConfig config = new AiConfig(); config.setEnabled(true); config.setToolsSupported(true);
        TutorSessionVO result = new TutorSessionService(
                users, points, contents, sessions, new ObjectMapper(), events, contexts, config).start(7L, 10L, 3L);
        assertEquals("如何搬移？", result.getCheck().path("prompt").asText());
        assertTrue(result.getCheck().has("options"));
        assertEquals(2, result.getCheck().path("options").get(0).size());
        assertFalse(result.getCheck().path("options").get(0).has("isCorrect"));
        assertEquals("move-direction", result.getCheck().path("id").asText());
        assertFalse(result.getCheck().has("correctOptionId"));
        assertFalse(result.getCheck().has("correctExplanation"));
        assertFalse(result.getCheck().has("incorrectExplanation"));
        assertNotNull(result.getSessionKey());
        assertTrue(result.isAgentAvailable());
        assertEquals(3, result.getLearningContext().getPaperAnswerCount());
        assertEquals(2, result.getLearningContext().getUnresolvedWrongCount());
        verify(sessions).insert(argThat(session -> session.getLearningContextJson().contains("\"paperAnswerCount\":3")));
    }
    @Test void rejectsTutorStartOutsideLibrary() {
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(0L);
        TutorSessionService service = new TutorSessionService(users, mock(KnowledgePointMapper.class),
                mock(TutorContentMapper.class), mock(TutorSessionMapper.class), new ObjectMapper(),
                mock(CourseLearningEventService.class), mock(TutorLearningContextService.class), new AiConfig());
        assertThrows(BusinessException.class, () -> service.start(7L, 10L, 3L));
    }

    @Test void returnsReviewedPrerequisiteAfterIncorrectCheckAndNextTargetAfterCorrectCheck() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        when(users.selectCount(any())).thenReturn(1L);
        TutorContent content = new TutorContent(); content.setId(8L); content.setReviewStatus("REVIEWED");
        content.setCheckJson("{\"correctOptionId\":\"RIGHT_TO_LEFT\",\"options\":[{\"id\":\"LEFT_TO_RIGHT\"},{\"id\":\"RIGHT_TO_LEFT\"}]}");
        content.setLessonJson("{\"prerequisite\":{\"contentKey\":\"ods-array-size-capacity\",\"title\":\"元素数量与数组容量\",\"description\":\"先区分 n 与 capacity。\"},\"nextStep\":{\"contentKey\":\"ods-arraystack-performance\",\"title\":\"ArrayStack 的操作复杂度\",\"description\":\"再分析搬移成本。\"}}");
        TutorSession session = new TutorSession(); session.setId(9L); session.setUserId(7L); session.setCourseId(10L); session.setKnowledgePointId(3L); session.setTutorContentId(8L);
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        when(sessions.update(any(), any())).thenReturn(1);
        KnowledgePoint prerequisite = new KnowledgePoint(); prerequisite.setId(30L);
        KnowledgePoint nextTarget = new KnowledgePoint(); nextTarget.setId(35L);
        when(points.selectOne(any())).thenReturn(prerequisite, nextTarget);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions,
                new ObjectMapper(), events, mock(TutorLearningContextService.class), new AiConfig());

        TutorCheckAnswerRequest incorrect = new TutorCheckAnswerRequest(); incorrect.setOptionId("LEFT_TO_RIGHT");
        TutorCheckResultVO incorrectResult = service.answer(7L, 10L, "session", incorrect);
        assertEquals("PREREQUISITE", incorrectResult.getGuidanceType());
        assertEquals("元素数量与数组容量", incorrectResult.getGuidanceTitle());
        assertEquals(30L, new ObjectMapper().valueToTree(incorrectResult)
                .path("guidanceKnowledgePointId").asLong());

        TutorCheckAnswerRequest correct = new TutorCheckAnswerRequest(); correct.setOptionId("RIGHT_TO_LEFT");
        TutorCheckResultVO correctResult = service.answer(7L, 10L, "session", correct);
        assertEquals("NEXT_TARGET", correctResult.getGuidanceType());
        assertEquals("ArrayStack 的操作复杂度", correctResult.getGuidanceTitle());
        assertEquals(35L, new ObjectMapper().valueToTree(correctResult)
                .path("guidanceKnowledgePointId").asLong());
    }

    @Test void usesReviewedContentFeedbackInsteadOfArrayStackInsertionSpecificText() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        when(users.selectCount(any())).thenReturn(1L);
        TutorContent content = new TutorContent(); content.setId(8L); content.setReviewStatus("REVIEWED");
        content.setCheckJson("{\"correctOptionId\":\"LEFT_TO_RIGHT\",\"options\":[{\"id\":\"LEFT_TO_RIGHT\"}],\"correctExplanation\":\"正确：删除后从左向右搬移后缀，填补空位。\",\"incorrectExplanation\":\"不正确：从右向左会覆盖尚未读取的后继元素。\"}");
        content.setLessonJson("{}");
        TutorSession session = new TutorSession(); session.setId(9L); session.setUserId(7L); session.setCourseId(10L); session.setKnowledgePointId(3L); session.setTutorContentId(8L);
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        when(sessions.update(any(), any())).thenReturn(1);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions,
                new ObjectMapper(), events, mock(TutorLearningContextService.class), new AiConfig());

        TutorCheckAnswerRequest answer = new TutorCheckAnswerRequest(); answer.setOptionId("LEFT_TO_RIGHT");
        assertEquals("正确：删除后从左向右搬移后缀，填补空位。", service.answer(7L, 10L, "session", answer).getExplanation());
    }

    @Test void restoresOnlyAnOwnedSessionWhoseContentRemainsReviewed() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        when(users.selectCount(any())).thenReturn(1L);
        TutorSession session = new TutorSession(); session.setUserId(7L); session.setCourseId(10L);
        session.setTutorContentId(8L); session.setSessionKey("session"); session.setLearningContextJson("{}");
        TutorContent content = new TutorContent(); content.setId(8L); content.setReviewStatus("REVIEWED");
        content.setLessonJson("{\"summary\":\"x\"}"); content.setCheckJson("{\"correctOptionId\":\"A\"}");
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions,
                new ObjectMapper(), mock(CourseLearningEventService.class),
                mock(TutorLearningContextService.class), new AiConfig());

        assertFalse(service.get(7L, 10L, "session").getCheck().has("correctOptionId"));
        content.setReviewStatus("REVIEW_PENDING");
        assertThrows(BusinessException.class, () -> service.get(7L, 10L, "session"));
    }

    @Test void restoresTheRecordedCheckAnswerAndServerGradedResultWithoutLeakingPrivateCheckFields() {
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(1L);
        KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        TutorSession session = new TutorSession(); session.setUserId(7L); session.setCourseId(10L);
        session.setTutorContentId(8L); session.setSessionKey("session"); session.setLearningContextJson("{}");
        session.setCheckAnswer("A"); session.setCheckCorrect(true);
        TutorContent content = new TutorContent(); content.setId(8L); content.setReviewStatus("REVIEWED");
        content.setLessonJson("{}"); content.setCheckJson("{\"prompt\":\"选择 A\",\"options\":[{\"id\":\"A\",\"text\":\"A\"}],\"correctOptionId\":\"A\",\"correctExplanation\":\"正确\",\"incorrectExplanation\":\"错误\"}");
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions,
                new ObjectMapper(), mock(CourseLearningEventService.class),
                mock(TutorLearningContextService.class), new AiConfig());

        var response = new ObjectMapper().valueToTree(service.get(7L, 10L, "session"));
        assertEquals("A", response.path("checkAnswer").asText());
        assertTrue(response.path("checkResult").path("correct").asBoolean());
        assertEquals("正确", response.path("checkResult").path("explanation").asText());
        assertFalse(response.path("check").has("correctOptionId"));
        assertFalse(response.path("check").has("correctExplanation"));
    }

    @Test void rejectsAnOptionThatIsNotPublishedInTheReviewedCheck() {
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(1L);
        TutorSessionMapper sessions = mock(TutorSessionMapper.class); TutorContentMapper contents = mock(TutorContentMapper.class);
        TutorSession session = session(); when(sessions.selectOne(any())).thenReturn(session);
        TutorContent content = reviewedContent("{\"options\":[{\"id\":\"A\"}],\"correctOptionId\":\"A\"}");
        when(contents.selectById(8L)).thenReturn(content);
        TutorSessionService service = service(users, mock(KnowledgePointMapper.class), contents, sessions);
        TutorCheckAnswerRequest request = new TutorCheckAnswerRequest(); request.setOptionId("B");

        assertThrows(BusinessException.class, () -> service.answer(7L, 10L, "session", request));
        verify(sessions, never()).update(any(), any());
    }

    @Test void returnsTheFirstGradedResultWithoutRewritingFactsOnRepeatSubmission() {
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(1L);
        TutorSessionMapper sessions = mock(TutorSessionMapper.class); TutorContentMapper contents = mock(TutorContentMapper.class);
        TutorSession session = session(); session.setCheckAnswer("A"); session.setCheckCorrect(true);
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(reviewedContent("{\"options\":[{\"id\":\"A\"}],\"correctOptionId\":\"A\",\"correctExplanation\":\"正确\"}"));
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        TutorSessionService service = new TutorSessionService(users, mock(KnowledgePointMapper.class), contents, sessions,
                new ObjectMapper(), events, mock(TutorLearningContextService.class), new AiConfig());
        TutorCheckAnswerRequest request = new TutorCheckAnswerRequest(); request.setOptionId("A");

        assertTrue(service.answer(7L, 10L, "session", request).isCorrect());
        verify(sessions, never()).update(any(), any());
        verifyNoInteractions(events);
    }

    @Test void rejectsCrossCourseRemovedAndWithdrawnTutorSessions() {
        TutorSessionMapper sessions = mock(TutorSessionMapper.class); TutorContentMapper contents = mock(TutorContentMapper.class);
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(1L);
        TutorSessionService service = service(users, mock(KnowledgePointMapper.class), contents, sessions);
        TutorCheckAnswerRequest request = new TutorCheckAnswerRequest(); request.setOptionId("A");

        assertThrows(BusinessException.class, () -> service.answer(7L, 99L, "session", request));

        TutorSession session = session(); when(sessions.selectOne(any())).thenReturn(session);
        when(users.selectCount(any())).thenReturn(0L);
        assertThrows(BusinessException.class, () -> service.answer(7L, 10L, "session", request));

        when(users.selectCount(any())).thenReturn(1L);
        TutorContent withdrawn = reviewedContent("{\"options\":[{\"id\":\"A\"}],\"correctOptionId\":\"A\"}");
        withdrawn.setReviewStatus("REVIEW_PENDING"); when(contents.selectById(8L)).thenReturn(withdrawn);
        assertThrows(BusinessException.class, () -> service.answer(7L, 10L, "session", request));
    }

    private TutorSessionService service(UserCourseMapper users, KnowledgePointMapper points,
                                        TutorContentMapper contents, TutorSessionMapper sessions) {
        return new TutorSessionService(users, points, contents, sessions, new ObjectMapper(),
                mock(CourseLearningEventService.class), mock(TutorLearningContextService.class), new AiConfig());
    }

    private TutorSession session() {
        TutorSession value = new TutorSession(); value.setId(9L); value.setUserId(7L); value.setCourseId(10L);
        value.setKnowledgePointId(3L); value.setTutorContentId(8L); value.setSessionKey("session");
        return value;
    }

    private TutorContent reviewedContent(String checkJson) {
        TutorContent value = new TutorContent(); value.setId(8L); value.setReviewStatus("REVIEWED");
        value.setLessonJson("{}"); value.setCheckJson(checkJson);
        return value;
    }
}
