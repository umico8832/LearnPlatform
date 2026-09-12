package com.learnplatform.service.exam;

import com.learnplatform.dto.ExamPaperCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.service.AiExamGenerationService.SmartExamPreview;
import com.learnplatform.service.ExamPaperService;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

/** 将确认后的智能组卷预览转换为草稿试卷，并委托试卷服务在既有事务中持久化。 */
@Service
public class AiExamPaperCreationService {

    private final ExamPaperService examPaperService;

    public AiExamPaperCreationService(ExamPaperService examPaperService) {
        this.examPaperService = examPaperService;
    }

    public ExamPaperVO create(SmartExamPreview preview, Long adminUserId) {
        ExamPaperCreateRequest request = new ExamPaperCreateRequest();
        request.setTitle(preview.getTitle());
        request.setDescription(preview.getDescription());
        request.setCourseId(preview.getCourseId());
        request.setDuration(preview.getDuration());
        request.setStatus(0);

        List<ExamPaperCreateRequest.QuestionItem> items = new ArrayList<>();
        int order = 1;
        for (Long questionId : preview.getQuestionIds()) {
            ExamPaperCreateRequest.QuestionItem item = new ExamPaperCreateRequest.QuestionItem();
            item.setQuestionId(questionId);
            item.setSortOrder(order++);
            item.setScore(1);
            items.add(item);
        }
        request.setQuestions(items);
        return examPaperService.createExamPaper(request, adminUserId);
    }
}
