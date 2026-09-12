package com.learnplatform.service;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.QuestionOptionVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamQuestion;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.ExamQuestionMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
public class ExamPaperViewService {
    private final ExamPaperMapper examPaperMapper;
    private final ExamQuestionMapper examQuestionMapper;
    private final QuestionMapper questionMapper;
    private final QuestionOptionMapper questionOptionMapper;
    private final CourseMapper courseMapper;

    public ExamPaperViewService(ExamPaperMapper examPaperMapper,
                                ExamQuestionMapper examQuestionMapper,
                                QuestionMapper questionMapper,
                                QuestionOptionMapper questionOptionMapper,
                                CourseMapper courseMapper) {
        this.examPaperMapper = examPaperMapper;
        this.examQuestionMapper = examQuestionMapper;
        this.questionMapper = questionMapper;
        this.questionOptionMapper = questionOptionMapper;
        this.courseMapper = courseMapper;
    }

    public Page<ExamPaperVO> getPublicPage(int pageNum, int pageSize, Long courseId, Integer status) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        if (courseId != null) { wrapper.eq(ExamPaper::getCourseId, courseId); }
        if (status != null) { wrapper.eq(ExamPaper::getStatus, status); }
        wrapper.eq(ExamPaper::getVisibility, "PUBLIC");
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        return toPage(examPaperMapper.selectPage(page, wrapper));
    }

    public Page<ExamPaperVO> getAccessiblePublishedPage(Long userId, int pageNum,
                                                        int pageSize, Long courseId) {
        Page<ExamPaper> page = new Page<>(pageNum, pageSize);
        LambdaQueryWrapper<ExamPaper> wrapper = new LambdaQueryWrapper<>();
        wrapper.eq(ExamPaper::getStatus, 1)
                .and(scope -> scope.eq(ExamPaper::getVisibility, "PUBLIC")
                        .or(privateScope -> privateScope.eq(ExamPaper::getVisibility, "PRIVATE")
                                .eq(ExamPaper::getOwnerUserId, userId)));
        if (courseId != null) { wrapper.eq(ExamPaper::getCourseId, courseId); }
        wrapper.orderByDesc(ExamPaper::getCreateTime);
        return toPage(examPaperMapper.selectPage(page, wrapper));
    }

    public ExamPaperVO getPublicById(Long id) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null || "PRIVATE".equals(paper.getVisibility())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return toDetail(paper, true);
    }

    public ExamPaperVO getAccessiblePublishedById(Long id, Long userId) {
        ExamPaper paper = examPaperMapper.selectById(id);
        if (paper == null || paper.getStatus() == null || paper.getStatus() != 1
                || !canAccess(paper, userId)) {
            throw new BusinessException(ResultCode.NOT_FOUND, "试卷不存在");
        }
        return toDetail(paper, false);
    }

    public boolean canAccess(ExamPaper paper, Long userId) {
        String visibility = paper.getVisibility();
        if (visibility == null || "PUBLIC".equals(visibility)) { return true; }
        return "PRIVATE".equals(visibility) && userId != null && userId.equals(paper.getOwnerUserId());
    }

    private Page<ExamPaperVO> toPage(Page<ExamPaper> result) {
        Page<ExamPaperVO> page = new Page<>(result.getCurrent(), result.getSize(), result.getTotal());
        page.setRecords(result.getRecords().stream().map(this::toView).toList());
        return page;
    }

    private ExamPaperVO toDetail(ExamPaper paper, boolean includeCorrectAnswer) {
        ExamPaperVO view = toView(paper);
        fillQuestions(view, includeCorrectAnswer);
        return view;
    }

    private ExamPaperVO toView(ExamPaper paper) {
        ExamPaperVO view = new ExamPaperVO();
        view.setId(paper.getId());
        view.setTitle(paper.getTitle());
        view.setDescription(paper.getDescription());
        view.setCourseId(paper.getCourseId());
        view.setTotalScore(paper.getTotalScore());
        view.setDuration(paper.getDuration());
        view.setQuestionCount(paper.getQuestionCount());
        view.setStatus(paper.getStatus());
        view.setCreateBy(paper.getCreateBy());
        view.setOwnerUserId(paper.getOwnerUserId());
        view.setVisibility(paper.getVisibility() != null ? paper.getVisibility() : "PUBLIC");
        view.setPaperType(ExamPaperValidationService.normalizePaperType(paper.getPaperType()));
        view.setExamName(paper.getExamName());
        view.setExamYear(paper.getExamYear());
        view.setSourceReference(paper.getSourceReference());
        view.setSourceVerified(Boolean.TRUE.equals(paper.getSourceVerified()));
        view.setImportStatus(paper.getImportStatus());
        view.setCreateTime(paper.getCreateTime());
        if (paper.getCourseId() != null) {
            Course course = courseMapper.selectById(paper.getCourseId());
            if (course != null) { view.setCourseName(course.getName()); }
        }
        return view;
    }

    private void fillQuestions(ExamPaperVO view, boolean includeCorrectAnswer) {
        List<ExamQuestion> relations = examQuestionMapper.selectList(new LambdaQueryWrapper<ExamQuestion>()
                .eq(ExamQuestion::getExamPaperId, view.getId()).orderByAsc(ExamQuestion::getSortOrder));
        List<ExamPaperVO.ExamQuestionItem> items = new ArrayList<>();
        for (ExamQuestion relation : relations) {
            ExamPaperVO.ExamQuestionItem item = new ExamPaperVO.ExamQuestionItem();
            item.setQuestionId(relation.getQuestionId());
            item.setSortOrder(relation.getSortOrder());
            item.setScore(relation.getScore());
            item.setSectionTitle(relation.getSectionTitle());
            item.setMajorQuestionNumber(relation.getMajorQuestionNumber());
            item.setMinorQuestionNumber(relation.getMinorQuestionNumber());
            item.setSubquestionNumber(relation.getSubquestionNumber());
            item.setDisplayNumber(relation.getDisplayNumber());
            Question question = questionMapper.selectById(relation.getQuestionId());
            if (question != null) {
                item.setContent(question.getContent());
                item.setQuestionType(question.getQuestionType());
                List<QuestionOption> options = questionOptionMapper.selectList(
                        new LambdaQueryWrapper<QuestionOption>()
                                .eq(QuestionOption::getQuestionId, question.getId())
                                .orderByAsc(QuestionOption::getSortOrder));
                item.setOptions(options.stream().map(option -> {
                    QuestionOptionVO optionView = QuestionOptionVO.fromEntity(option);
                    if (!includeCorrectAnswer) { optionView.setIsCorrect(null); }
                    return optionView;
                }).toList());
            }
            items.add(item);
        }
        view.setQuestions(items);
    }
}
