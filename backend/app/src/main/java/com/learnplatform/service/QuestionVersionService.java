package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.learnplatform.dto.QuestionVersionVO;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.QuestionVersion;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.QuestionVersionMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

/** 题目版本记录服务。 */
@Service
public class QuestionVersionService {

    private final QuestionVersionMapper questionVersionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final QuestionKnowledgePointMapper questionKnowledgePointMapper;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public QuestionVersionService(QuestionVersionMapper questionVersionMapper,
                                  QuestionOptionMapper questionOptionMapper,
                                  QuestionKnowledgePointMapper questionKnowledgePointMapper,
                                  UserMapper userMapper) {
        this.questionVersionMapper = questionVersionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.questionKnowledgePointMapper = questionKnowledgePointMapper;
        this.userMapper = userMapper;
        this.objectMapper = new ObjectMapper()
                .registerModule(new JavaTimeModule())
                .disable(SerializationFeature.WRITE_DATES_AS_TIMESTAMPS);
    }

    public void recordChange(Long questionId, String changeType, Long operatorId,
                             String changeSummary, Question before, Question after) {
        recordChangeSnapshots(questionId, changeType, operatorId, changeSummary,
                buildSnapshotJson(before), buildSnapshotJson(after));
    }

    public void recordChangeSnapshots(Long questionId, String changeType, Long operatorId,
                                      String changeSummary, String snapshotBefore, String snapshotAfter) {
        if (questionId == null) {
            return;
        }
        QuestionVersion version = new QuestionVersion();
        version.setQuestionId(questionId);
        version.setVersionNo(nextVersionNo(questionId));
        version.setChangeType(changeType);
        version.setOperatorId(operatorId);
        version.setChangeSummary(changeSummary);
        version.setSnapshotBefore(snapshotBefore);
        version.setSnapshotAfter(snapshotAfter);
        version.setDeleted(0);
        questionVersionMapper.insert(version);
    }

    public List<QuestionVersionVO> getQuestionVersions(Long questionId) {
        List<QuestionVersion> versions = questionVersionMapper.selectList(
                new LambdaQueryWrapper<QuestionVersion>()
                        .eq(QuestionVersion::getQuestionId, questionId)
                        .orderByDesc(QuestionVersion::getVersionNo));
        return versions.stream().map(this::toVO).collect(Collectors.toList());
    }

    private Integer nextVersionNo(Long questionId) {
        List<QuestionVersion> versions = questionVersionMapper.selectList(
                new LambdaQueryWrapper<QuestionVersion>()
                        .eq(QuestionVersion::getQuestionId, questionId)
                        .orderByDesc(QuestionVersion::getVersionNo)
                        .last("LIMIT 1"));
        if (versions.isEmpty() || versions.get(0).getVersionNo() == null) {
            return 1;
        }
        return versions.get(0).getVersionNo() + 1;
    }

    private QuestionVersionVO toVO(QuestionVersion version) {
        QuestionVersionVO vo = QuestionVersionVO.fromEntity(version);
        if (version.getOperatorId() != null) {
            User user = userMapper.selectById(version.getOperatorId());
            if (user != null) {
                vo.setOperatorName(user.getNickname() != null && !user.getNickname().isBlank()
                        ? user.getNickname()
                        : user.getUsername());
            }
        }
        return vo;
    }

    public String buildSnapshotJson(Question question) {
        if (question == null) {
            return null;
        }
        Map<String, Object> snapshot = new LinkedHashMap<>();
        snapshot.put("id", question.getId());
        snapshot.put("content", question.getContent());
        snapshot.put("questionType", question.getQuestionType());
        snapshot.put("courseId", question.getCourseId());
        snapshot.put("difficulty", question.getDifficulty());
        snapshot.put("analysis", question.getAnalysis());
        snapshot.put("tags", question.getTags());
        snapshot.put("score", question.getScore());
        snapshot.put("status", question.getStatus());
        snapshot.put("sourceType", question.getSourceType());
        snapshot.put("sourceReference", question.getSourceReference());
        snapshot.put("originQuestionId", question.getOriginQuestionId());
        snapshot.put("lastReviewTime", question.getLastReviewTime());
        snapshot.put("nextReviewTime", question.getNextReviewTime());
        snapshot.put("reviewRounds", question.getReviewRounds());
        snapshot.put("options", getOptionSnapshots(question.getId()));
        snapshot.put("knowledgePointIds", getKnowledgePointIds(question.getId()));
        try {
            return objectMapper.writeValueAsString(snapshot);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("题目版本快照序列化失败", e);
        }
    }

    private List<Map<String, Object>> getOptionSnapshots(Long questionId) {
        return questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                        .eq(QuestionOption::getQuestionId, questionId)
                        .orderByAsc(QuestionOption::getSortOrder))
                .stream()
                .map(option -> {
                    Map<String, Object> item = new LinkedHashMap<>();
                    item.put("id", option.getId());
                    item.put("content", option.getContent());
                    item.put("optionLabel", option.getOptionLabel());
                    item.put("isCorrect", option.getIsCorrect());
                    item.put("sortOrder", option.getSortOrder());
                    return item;
                })
                .collect(Collectors.toList());
    }

    private List<Long> getKnowledgePointIds(Long questionId) {
        return questionKnowledgePointMapper.selectList(new LambdaQueryWrapper<QuestionKnowledgePoint>()
                        .eq(QuestionKnowledgePoint::getQuestionId, questionId))
                .stream()
                .map(QuestionKnowledgePoint::getKnowledgePointId)
                .sorted(Comparator.naturalOrder())
                .collect(Collectors.toList());
    }
}
