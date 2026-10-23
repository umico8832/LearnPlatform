package com.learnplatform.service;

import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.entity.Question;
import com.learnplatform.entity.QuestionKnowledgePoint;
import com.learnplatform.entity.QuestionOption;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class QuestionImportRowWriter {
    private final QuestionMapper questions;
    private final QuestionOptionMapper options;
    private final QuestionKnowledgePointMapper relations;
    private final ContentReferenceLockService locks;

    public QuestionImportRowWriter(QuestionMapper questions, QuestionOptionMapper options,
                                   QuestionKnowledgePointMapper relations, ContentReferenceLockService locks) {
        this.questions = questions;
        this.options = options;
        this.relations = relations;
        this.locks = locks;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void write(Question question, List<QuestionCreateRequest.OptionItem> items, List<Long> pointIds) {
        locks.lockForReference(List.of(question.getCourseId()), pointIds);
        questions.insert(question);
        for (var item : items) {
            var option = new QuestionOption();
            option.setQuestionId(question.getId());
            option.setContent(item.getContent());
            option.setOptionLabel(item.getOptionLabel());
            option.setIsCorrect(item.getIsCorrect());
            option.setSortOrder(item.getSortOrder());
            option.setDeleted(0);
            options.insert(option);
        }
        for (Long id : pointIds.stream().distinct().sorted().toList()) {
            var relation = new QuestionKnowledgePoint();
            relation.setQuestionId(question.getId());
            relation.setKnowledgePointId(id);
            relations.insert(relation);
        }
    }
}
