package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.entity.UserCourse;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import com.learnplatform.mapper.UserCourseMapper;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ExamPaperLearningContextService {

    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final UserCourseMapper userCourseMapper;

    public ExamPaperLearningContextService(ExamPaperMapper examPaperMapper,
                                           ExamQuestionMapper examQuestionMapper,
                                           QuestionMapper questionMapper,
                                           QuestionOptionMapper questionOptionMapper,
                                           UserCourseMapper userCourseMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.userCourseMapper = userCourseMapper;
    }

    ExamPaper loadEligiblePaper(Long paperId, Long userId) {
        ExamPaper paper = examPaperMapper.selectById(paperId);
        if (paper == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (!(paper.getVisibility() == null || "PUBLIC".equals(paper.getVisibility())
                || ("PRIVATE".equals(paper.getVisibility()) && userId.equals(paper.getOwnerUserId())))) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        if (!Integer.valueOf(1).equals(paper.getStatus())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷未发布");
        }
        if (paper.getCourseId() == null) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷未关联课程，无法进入学习模式");
        }
        long relationshipCount = userCourseMapper.selectCount(new LambdaQueryWrapper<UserCourse>()
                .eq(UserCourse::getUserId, userId)
                .eq(UserCourse::getCourseId, paper.getCourseId()));
        if (relationshipCount <= 0) {
            throw new BusinessException(ResultCode.FORBIDDEN, "请先将课程加入课程库");
        }
        return paper;
    }

    List<ExamQuestion> loadPaperQuestions(ExamPaper paper) {
        List<ExamQuestion> questions = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, paper.getId())
                .orderByAsc(ExamQuestion::getSortOrder));
        if (questions.isEmpty()) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "空试卷不能开始学习");
        }
        for (ExamQuestion item : questions) {
            loadPaperQuestion(paper, item);
        }
        return questions;
    }

    Question loadPaperQuestion(ExamPaper paper, ExamQuestion item) {
        Question question = questionMapper.selectById(item.getQuestionId());
        if (question == null || !Integer.valueOf(1).equals(question.getStatus())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷题目不存在或未开放");
        }
        if (!paper.getCourseId().equals(question.getCourseId())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "试卷题目不属于试卷课程");
        }
        return question;
    }

    List<QuestionOption> loadOptions(Long questionId) {
        return questionOptionMapper.selectList(new LambdaQueryWrapper<QuestionOption>()
                .eq(QuestionOption::getQuestionId, questionId)
                .orderByAsc(QuestionOption::getSortOrder));
    }
}
