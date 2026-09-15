package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.KnowledgePointMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.assertEquals;

@Tag("integration")
@SpringBootTest
@ActiveProfiles("integration")
@Transactional
class ContentReferenceIntegrityIntegrationTest extends IntegrationTestBase {

    private static final long COURSE_ID = 971010L;
    private static final long KNOWLEDGE_POINT_ID = 971020L;

    @Autowired
    private CourseMapper courseMapper;
    @Autowired
    private JdbcTemplate jdbc;
    @Autowired
    private KnowledgePointMapper knowledgePointMapper;

    @Test
    void referenceQueriesCoverCourseAndKnowledgePointDependencies() {
        jdbc.update("INSERT INTO course (id,name) VALUES (?, '引用完整性测试')", COURSE_ID);
        jdbc.update("INSERT INTO knowledge_point (id,name,course_id) VALUES (?, '受引用知识点', ?)",
                KNOWLEDGE_POINT_ID, COURSE_ID);
        jdbc.update("INSERT INTO tutor_content "
                        + "(knowledge_point_id,content_key,content_version,review_status,title,lesson_json,check_json) "
                        + "VALUES (?, 'reference-integrity-test', 1, 'REVIEWED', '引用测试', JSON_OBJECT(), JSON_OBJECT())",
                KNOWLEDGE_POINT_ID);

        assertEquals(1L, courseMapper.countReferences(COURSE_ID));
        assertEquals(1L, knowledgePointMapper.countReferences(KNOWLEDGE_POINT_ID));
    }
}
