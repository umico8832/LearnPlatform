package com.learnplatform.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class TutorSessionServiceTest {
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
}
