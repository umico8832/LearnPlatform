package com.learnplatform.service;

import com.learnplatform.dto.QuestionCreateRequest;
import com.learnplatform.mapper.QuestionKnowledgePointMapper;
import com.learnplatform.mapper.QuestionOptionMapper;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.mock;

class QuestionExcelRowServiceTest {
    private final QuestionExcelRowService service = new QuestionExcelRowService(
            mock(QuestionOptionMapper.class), mock(QuestionKnowledgePointMapper.class));

    @Test
    void normalizesSupportedQuestionTypes() {
        assertEquals("SINGLE_CHOICE", service.normalizeQuestionType("单选题"));
        assertEquals("TRUE_FALSE", service.normalizeQuestionType("JUDGMENT"));
        assertNull(service.normalizeQuestionType("论述题"));
    }

    @Test
    void parsesLabeledOptionsAndMultipleAnswers() {
        List<QuestionCreateRequest.OptionItem> options = service.parseOptions(
                "A.栈|B.队列|C.数组", "A,C", "MULTIPLE_CHOICE");

        assertEquals(3, options.size());
        assertEquals(1, options.get(0).getIsCorrect());
        assertEquals(0, options.get(1).getIsCorrect());
        assertEquals(1, options.get(2).getIsCorrect());
    }

    @Test
    void suppliesTrueFalseOptionsWhenCellIsEmpty() {
        List<QuestionCreateRequest.OptionItem> options = service.parseOptions(null, "错", "TRUE_FALSE");

        assertEquals(List.of("对", "错"), options.stream().map(QuestionCreateRequest.OptionItem::getContent).toList());
        assertEquals(List.of(0, 1), options.stream().map(QuestionCreateRequest.OptionItem::getIsCorrect).toList());
    }
}
