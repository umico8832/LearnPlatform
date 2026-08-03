package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.core.conditions.update.LambdaUpdateWrapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorCheckAnswerRequest;
import com.learnplatform.dto.TutorCheckResultVO;
import com.learnplatform.dto.TutorSessionVO;
import com.learnplatform.entity.KnowledgePoint;
import com.learnplatform.entity.TutorContent;
import com.learnplatform.entity.TutorSession;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.KnowledgePointMapper;
import com.learnplatform.mapper.TutorContentMapper;
import com.learnplatform.mapper.TutorSessionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import java.time.LocalDateTime;
import java.util.UUID;

/** 已审查内容驱动的确定性 Tutor，会话与判分均不信任客户端。 */
@Service
public class TutorSessionService {
    private final UserCourseMapper userCourseMapper; private final KnowledgePointMapper knowledgePointMapper;
    private final TutorContentMapper contentMapper; private final TutorSessionMapper sessionMapper; private final ObjectMapper objectMapper; private final CourseLearningEventService events;
    public TutorSessionService(UserCourseMapper users, KnowledgePointMapper points, TutorContentMapper contents, TutorSessionMapper sessions, ObjectMapper json, CourseLearningEventService learningEvents) {
        userCourseMapper = users; knowledgePointMapper = points; contentMapper = contents; sessionMapper = sessions; objectMapper = json; events = learningEvents;
    }
    @Transactional
    public TutorSessionVO start(Long userId, Long courseId, Long knowledgePointId) {
        requireCourse(userId, courseId);
        KnowledgePoint point = knowledgePointMapper.selectById(knowledgePointId);
        if (point == null || !courseId.equals(point.getCourseId()) || !"REVIEWED".equals(point.getContentReviewStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "教学目标不存在、未属于该课程或尚未审查");
        }
        TutorContent content = contentMapper.selectOne(new LambdaQueryWrapper<TutorContent>()
                .eq(TutorContent::getKnowledgePointId, knowledgePointId).eq(TutorContent::getReviewStatus, "REVIEWED"));
        if (content == null) throw new BusinessException(ResultCode.NOT_FOUND, "教学内容尚未发布");
        TutorSession session = new TutorSession(); session.setSessionKey(UUID.randomUUID().toString()); session.setUserId(userId);
        session.setCourseId(courseId); session.setKnowledgePointId(knowledgePointId); session.setTutorContentId(content.getId()); sessionMapper.insert(session);
        return view(session, content);
    }
    @Transactional
    public TutorCheckResultVO answer(Long userId, String sessionKey, TutorCheckAnswerRequest request) {
        TutorSession session = sessionMapper.selectOne(new LambdaQueryWrapper<TutorSession>().eq(TutorSession::getSessionKey, sessionKey));
        if (session == null || !userId.equals(session.getUserId())) throw new BusinessException(ResultCode.NOT_FOUND, "Tutor 会话不存在");
        if (session.getCheckCorrect() != null) return result(session.getCheckCorrect());
        TutorContent content = contentMapper.selectById(session.getTutorContentId());
        JsonNode check = parse(content.getCheckJson()); String correctOption = check.path("correctOptionId").asText();
        boolean correct = correctOption.equals(request.getOptionId());
        int updated = sessionMapper.update(null, new LambdaUpdateWrapper<TutorSession>()
                .eq(TutorSession::getId, session.getId()).isNull(TutorSession::getCheckCorrect)
                .set(TutorSession::getCheckAnswer, request.getOptionId()).set(TutorSession::getCheckCorrect, correct)
                .set(TutorSession::getCheckAnsweredAt, LocalDateTime.now()));
        if (updated == 0) {
            TutorSession persisted = sessionMapper.selectById(session.getId());
            return result(Boolean.TRUE.equals(persisted.getCheckCorrect()));
        }
        events.recordTutorCheck(userId, session.getCourseId(), session.getKnowledgePointId(), session.getId(), correct);
        return result(correct);
    }
    private TutorSessionVO view(TutorSession session, TutorContent content) {
        TutorSessionVO view = new TutorSessionVO(); view.setSessionKey(session.getSessionKey()); view.setTitle(content.getTitle()); view.setLesson(parse(content.getLessonJson()));
        JsonNode check = parse(content.getCheckJson()).deepCopy(); ((com.fasterxml.jackson.databind.node.ObjectNode) check).remove("correctOptionId"); view.setCheck(check); return view;
    }
    private TutorCheckResultVO result(boolean correct) { TutorCheckResultVO result = new TutorCheckResultVO(); result.setCorrect(correct); result.setExplanation(correct ? "正确：从右向左先腾出后面的槽位，避免覆盖尚未搬移的元素。" : "不正确：从左向右会先覆盖 a[i+1] 等尚未搬走的元素。"); return result; }
    private JsonNode parse(String value) { try { return objectMapper.readTree(value); } catch (Exception e) { throw new IllegalStateException("已审查教学内容格式无效", e); } }
    private void requireCourse(Long userId, Long courseId) { if (userCourseMapper.selectCount(new LambdaQueryWrapper<UserCourse>().eq(UserCourse::getUserId, userId).eq(UserCourse::getCourseId, courseId)) == 0) throw new BusinessException(ResultCode.FORBIDDEN, "请先将课程加入个人课程库"); }
}
