package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorCheckResultVO;
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
import org.apache.ibatis.session.Configuration;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TutorSessionServiceTest {
    @BeforeEach
    void initializeMybatisMetadata() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new Configuration(), ""), TutorSession.class);
    }

    @Test void startsOnlyReviewedContentAndNeverReturnsCorrectOption() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        when(users.selectCount(any())).thenReturn(1L); KnowledgePoint point = new KnowledgePoint(); point.setId(3L); point.setCourseId(10L); point.setContentReviewStatus("REVIEWED"); when(points.selectById(3L)).thenReturn(point);
        TutorContent content = new TutorContent(); content.setId(8L); content.setTitle("ArrayStack"); content.setLessonJson("{\"summary\":\"x\"}"); content.setCheckJson("{\"correctOptionId\":\"RIGHT_TO_LEFT\",\"options\":[]}"); when(contents.selectOne(any())).thenReturn(content);
        doAnswer(i -> { ((TutorSession) i.getArgument(0)).setId(9L); return 1; }).when(sessions).insert(any());
        TutorSessionVO result = new TutorSessionService(users, points, contents, sessions, new ObjectMapper(), events).start(7L, 10L, 3L);
        assertFalse(result.getCheck().has("correctOptionId")); assertNotNull(result.getSessionKey());
    }
    @Test void rejectsTutorStartOutsideLibrary() {
        UserCourseMapper users = mock(UserCourseMapper.class); when(users.selectCount(any())).thenReturn(0L);
        TutorSessionService service = new TutorSessionService(users, mock(KnowledgePointMapper.class), mock(TutorContentMapper.class), mock(TutorSessionMapper.class), new ObjectMapper(), mock(CourseLearningEventService.class));
        assertThrows(BusinessException.class, () -> service.start(7L, 10L, 3L));
    }

    @Test void returnsReviewedPrerequisiteAfterIncorrectCheckAndNextTargetAfterCorrectCheck() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        TutorContent content = new TutorContent(); content.setId(8L);
        content.setCheckJson("{\"correctOptionId\":\"RIGHT_TO_LEFT\"}");
        content.setLessonJson("{\"prerequisite\":{\"contentKey\":\"ods-array-size-capacity\",\"title\":\"元素数量与数组容量\",\"description\":\"先区分 n 与 capacity。\"},\"nextStep\":{\"contentKey\":\"ods-arraystack-performance\",\"title\":\"ArrayStack 的操作复杂度\",\"description\":\"再分析搬移成本。\"}}");
        TutorSession session = new TutorSession(); session.setId(9L); session.setUserId(7L); session.setCourseId(10L); session.setKnowledgePointId(3L); session.setTutorContentId(8L);
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        when(sessions.update(any(), any())).thenReturn(1);
        KnowledgePoint prerequisite = new KnowledgePoint(); prerequisite.setId(30L);
        KnowledgePoint nextTarget = new KnowledgePoint(); nextTarget.setId(35L);
        when(points.selectOne(any())).thenReturn(prerequisite, nextTarget);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions, new ObjectMapper(), events);

        TutorCheckAnswerRequest incorrect = new TutorCheckAnswerRequest(); incorrect.setOptionId("LEFT_TO_RIGHT");
        TutorCheckResultVO incorrectResult = service.answer(7L, "session", incorrect);
        assertEquals("PREREQUISITE", incorrectResult.getGuidanceType());
        assertEquals("元素数量与数组容量", incorrectResult.getGuidanceTitle());
        assertEquals(30L, new ObjectMapper().valueToTree(incorrectResult)
                .path("guidanceKnowledgePointId").asLong());

        TutorCheckAnswerRequest correct = new TutorCheckAnswerRequest(); correct.setOptionId("RIGHT_TO_LEFT");
        TutorCheckResultVO correctResult = service.answer(7L, "session", correct);
        assertEquals("NEXT_TARGET", correctResult.getGuidanceType());
        assertEquals("ArrayStack 的操作复杂度", correctResult.getGuidanceTitle());
        assertEquals(35L, new ObjectMapper().valueToTree(correctResult)
                .path("guidanceKnowledgePointId").asLong());
    }

    @Test void usesReviewedContentFeedbackInsteadOfArrayStackInsertionSpecificText() {
        UserCourseMapper users = mock(UserCourseMapper.class); KnowledgePointMapper points = mock(KnowledgePointMapper.class);
        TutorContentMapper contents = mock(TutorContentMapper.class); TutorSessionMapper sessions = mock(TutorSessionMapper.class);
        CourseLearningEventService events = mock(CourseLearningEventService.class);
        TutorContent content = new TutorContent(); content.setId(8L);
        content.setCheckJson("{\"correctOptionId\":\"LEFT_TO_RIGHT\",\"correctExplanation\":\"正确：删除后从左向右搬移后缀，填补空位。\",\"incorrectExplanation\":\"不正确：从右向左会覆盖尚未读取的后继元素。\"}");
        content.setLessonJson("{}");
        TutorSession session = new TutorSession(); session.setId(9L); session.setUserId(7L); session.setCourseId(10L); session.setKnowledgePointId(3L); session.setTutorContentId(8L);
        when(sessions.selectOne(any())).thenReturn(session);
        when(contents.selectById(8L)).thenReturn(content);
        when(sessions.update(any(), any())).thenReturn(1);
        TutorSessionService service = new TutorSessionService(users, points, contents, sessions, new ObjectMapper(), events);

        TutorCheckAnswerRequest answer = new TutorCheckAnswerRequest(); answer.setOptionId("LEFT_TO_RIGHT");
        assertEquals("正确：删除后从左向右搬移后缀，填补空位。", service.answer(7L, "session", answer).getExplanation());
    }
}
