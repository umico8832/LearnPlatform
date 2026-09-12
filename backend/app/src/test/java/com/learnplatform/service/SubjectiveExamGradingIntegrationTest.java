package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ExamRecordVO;
import com.learnplatform.dto.ExamSubmitRequest;
import com.learnplatform.dto.SubjectiveAnswerReviewVO;
import com.learnplatform.dto.SubjectiveGradingRequest;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.SubjectiveGradingPoint;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.SubjectiveGradingPointMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class SubjectiveExamGradingIntegrationTest extends IntegrationTestBase {
    @Autowired ExamService examService;
    @Autowired SubjectiveExamGradingService gradingService;
    @Autowired ExamPaperMapper paperMapper;
    @Autowired UserMapper userMapper;
    @Autowired SubjectiveGradingPointMapper gradingPointMapper;

    @Test
    void submit2026SubjectiveAnswersThenReviewEveryPointToFinalizeScore() {
        ExamPaper paper = paperMapper.selectOne(new LambdaQueryWrapper<ExamPaper>()
                .eq(ExamPaper::getTitle, "2026 年 408 真题·数据结构部分"));
        assertNotNull(paper);
        User learner = createLearner();
        ExamRecordVO started = examService.startExam(paper.getId(), learner.getId());
        ExamRecordVO paperDetail = examService.getExamSession(started.getId(), learner.getId());

        List<SubjectiveAnswerReviewVO> rubricCandidates = gradingService.listPending();
        assertTrue(rubricCandidates.isEmpty());

        // Resolve the two seeded subjective question IDs from their grading rubrics.
        List<Long> subjectiveIds = gradingPointMapper.selectList(new LambdaQueryWrapper<SubjectiveGradingPoint>()
                        .orderByAsc(SubjectiveGradingPoint::getQuestionId))
                .stream().map(SubjectiveGradingPoint::getQuestionId).distinct().toList();
        assertEquals(2, subjectiveIds.size());

        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(paperDetail.getId());
        request.setAnswers(subjectiveIds.stream().map(questionId -> {
            ExamSubmitRequest.AnswerItem item = new ExamSubmitRequest.AnswerItem();
            item.setQuestionId(questionId);
            item.setUserAnswer("考生按步骤写出的算法与推导");
            return item;
        }).toList());

        ExamRecordVO pendingResult = examService.submitExam(request, learner.getId());

        assertEquals(3, pendingResult.getStatus());
        assertEquals(0, pendingResult.getScore());
        assertEquals(2, pendingResult.getAnswers().stream()
                .filter(answer -> "PENDING".equals(answer.getGradingStatus())).count());
        pendingResult.getAnswers().stream().filter(answer -> "PENDING".equals(answer.getGradingStatus()))
                .forEach(answer -> {
                    assertNull(answer.getCorrectAnswer());
                    assertNull(answer.getAnalysis());
                });

        List<SubjectiveAnswerReviewVO> pending = gradingService.listPending().stream()
                .filter(answer -> answer.getExamRecordId().equals(started.getId())).toList();
        assertEquals(2, pending.size());

        SubjectiveAnswerReviewVO first = gradingService.grade(
                pending.get(0).getAnswerId(), fullScoreRequest(pending.get(0)), 1L);
        assertEquals(first.getFullScore(), first.getScore());
        assertEquals(3, examService.getExamResult(started.getId(), learner.getId()).getStatus());

        SubjectiveAnswerReviewVO second = gradingService.grade(
                pending.get(1).getAnswerId(), fullScoreRequest(pending.get(1)), 1L);
        assertEquals(second.getFullScore(), second.getScore());

        ExamRecordVO completed = examService.getExamResult(started.getId(), learner.getId());
        assertEquals(1, completed.getStatus());
        assertEquals(23, completed.getScore());
        assertEquals(45, completed.getTotalScore());
        assertTrue(completed.getAnswers().stream()
                .filter(answer -> "REVIEWED".equals(answer.getGradingStatus()))
                .allMatch(answer -> answer.getAnalysis() != null && answer.getReviewComment() != null));
    }

    private User createLearner() {
        User user = new User();
        user.setUsername("subjective_exam_" + System.nanoTime());
        user.setPassword("test-hash");
        user.setNickname("主观题测试用户");
        user.setRole("USER");
        user.setStatus(1);
        user.setDeleted(0);
        user.setCreateTime(LocalDateTime.now());
        user.setUpdateTime(LocalDateTime.now());
        userMapper.insert(user);
        return user;
    }

    private SubjectiveGradingRequest fullScoreRequest(SubjectiveAnswerReviewVO answer) {
        SubjectiveGradingRequest request = new SubjectiveGradingRequest();
        request.setReviewComment("评分点均满足");
        request.setPoints(answer.getGradingPoints().stream().map(point -> {
            SubjectiveGradingRequest.PointScore score = new SubjectiveGradingRequest.PointScore();
            score.setPointKey(point.getPointKey());
            score.setAwardedScore(point.getMaxScore());
            score.setComment("符合参考要求");
            return score;
        }).toList());
        return request;
    }
}
