package com.learnplatform.service;

import com.baomidou.mybatisplus.core.MybatisConfiguration;
import com.baomidou.mybatisplus.core.metadata.TableInfoHelper;
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
import org.apache.ibatis.builder.MapperBuilderAssistant;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class QuestionVersionServiceTest {

    @Mock private QuestionVersionMapper questionVersionMapper;
    @Mock private QuestionOptionMapper questionOptionMapper;
    @Mock private QuestionKnowledgePointMapper questionKnowledgePointMapper;
    @Mock private UserMapper userMapper;

    private QuestionVersionService service;

    @BeforeEach
    void setUp() {
        TableInfoHelper.initTableInfo(new MapperBuilderAssistant(new MybatisConfiguration(), ""), QuestionVersion.class);
        service = new QuestionVersionService(
                questionVersionMapper,
                questionOptionMapper,
                questionKnowledgePointMapper,
                userMapper);
    }

    @Test
    void recordChange_firstVersion_insertsSnapshot() {
        when(questionVersionMapper.selectList(any())).thenReturn(List.of());
        QuestionOption option = new QuestionOption();
        option.setId(11L);
        option.setQuestionId(1L);
        option.setOptionLabel("A");
        option.setContent("选项 A");
        option.setIsCorrect(1);
        option.setSortOrder(1);
        when(questionOptionMapper.selectList(any())).thenReturn(List.of(option));

        QuestionKnowledgePoint relation = new QuestionKnowledgePoint();
        relation.setQuestionId(1L);
        relation.setKnowledgePointId(5L);
        when(questionKnowledgePointMapper.selectList(any())).thenReturn(List.of(relation));

        Question question = new Question();
        question.setId(1L);
        question.setContent("题干");
        question.setQuestionType("SINGLE_CHOICE");
        question.setCourseId(2L);
        question.setDifficulty(3);
        question.setScore(5);
        question.setStatus(1);

        service.recordChange(1L, "CREATE", 9L, "创建题目", null, question);

        ArgumentCaptor<QuestionVersion> captor = ArgumentCaptor.forClass(QuestionVersion.class);
        verify(questionVersionMapper).insert(captor.capture());
        QuestionVersion saved = captor.getValue();
        assertEquals(1, saved.getVersionNo());
        assertEquals("CREATE", saved.getChangeType());
        assertTrue(saved.getSnapshotAfter().contains("\"content\":\"题干\""));
        assertTrue(saved.getSnapshotAfter().contains("\"knowledgePointIds\":[5]"));
    }

    @Test
    void getQuestionVersions_fillsOperatorName() {
        QuestionVersion version = new QuestionVersion();
        version.setId(3L);
        version.setQuestionId(1L);
        version.setVersionNo(2);
        version.setChangeType("UPDATE");
        version.setOperatorId(9L);
        version.setChangeSummary("更新题目");
        when(questionVersionMapper.selectList(any())).thenReturn(List.of(version));

        User user = new User();
        user.setId(9L);
        user.setUsername("admin");
        user.setNickname("管理员");
        when(userMapper.selectById(9L)).thenReturn(user);

        List<QuestionVersionVO> versions = service.getQuestionVersions(1L);

        assertEquals(1, versions.size());
        assertEquals("管理员", versions.get(0).getOperatorName());
        assertEquals("UPDATE", versions.get(0).getChangeType());
    }
}
