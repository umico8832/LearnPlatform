package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.QuestionSubmission;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.UserMapper;
import org.springframework.stereotype.Service;

import java.util.stream.Collectors;

@Service
public class QuestionSubmissionViewService {

    private final UserMapper userMapper;
    private final CourseMapper courseMapper;

    public QuestionSubmissionViewService(UserMapper userMapper, CourseMapper courseMapper) {
        this.userMapper = userMapper;
        this.courseMapper = courseMapper;
    }

    QuestionSubmissionVO toView(QuestionSubmission submission) {
        QuestionSubmissionVO view = new QuestionSubmissionVO();
        view.setId(submission.getId());
        view.setUserId(submission.getUserId());
        view.setContent(submission.getContent());
        view.setQuestionType(submission.getQuestionType());
        view.setCourseId(submission.getCourseId());
        view.setDifficulty(submission.getDifficulty());
        view.setAnalysis(submission.getAnalysis());
        view.setOptionsJson(submission.getOptionsJson());
        view.setCorrectAnswer(submission.getCorrectAnswer());
        view.setKnowledgePointIds(submission.getKnowledgePointIds());
        view.setTags(submission.getTags());
        view.setSource(submission.getSource());
        view.setStatus(submission.getStatus());
        view.setReviewComment(submission.getReviewComment());
        view.setReviewedBy(submission.getReviewedBy());
        view.setReviewedTime(submission.getReviewedTime());
        view.setImportedQuestionId(submission.getImportedQuestionId());
        view.setCreateTime(submission.getCreateTime());
        view.setUpdateTime(submission.getUpdateTime());
        enrichSubmitter(view, submission.getUserId());
        enrichReviewer(view, submission.getReviewedBy());
        enrichCourse(view, submission.getCourseId());
        return view;
    }

    Page<QuestionSubmissionVO> toPage(Page<QuestionSubmission> page) {
        Page<QuestionSubmissionVO> result = new Page<>(page.getCurrent(), page.getSize(), page.getTotal());
        result.setRecords(page.getRecords().stream().map(this::toView).collect(Collectors.toList()));
        return result;
    }

    private void enrichSubmitter(QuestionSubmissionVO view, Long userId) {
        if (userId == null) {
            return;
        }
        User user = userMapper.selectById(userId);
        if (user != null) {
            view.setUsername(user.getUsername());
            view.setNickname(user.getNickname());
        }
    }

    private void enrichReviewer(QuestionSubmissionVO view, Long reviewerId) {
        if (reviewerId == null) {
            return;
        }
        User reviewer = userMapper.selectById(reviewerId);
        if (reviewer != null) {
            view.setReviewedByName(reviewer.getNickname() != null
                    ? reviewer.getNickname() : reviewer.getUsername());
        }
    }

    private void enrichCourse(QuestionSubmissionVO view, Long courseId) {
        if (courseId == null) {
            return;
        }
        Course course = courseMapper.selectById(courseId);
        if (course != null) {
            view.setCourseName(course.getName());
        }
    }
}
