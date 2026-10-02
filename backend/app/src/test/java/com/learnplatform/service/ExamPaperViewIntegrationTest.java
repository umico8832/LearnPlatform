package com.learnplatform.service;

import com.learnplatform.IntegrationTestBase;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;

@SpringBootTest
@ActiveProfiles("integration")
@Transactional
@Tag("integration")
class ExamPaperViewIntegrationTest extends IntegrationTestBase {
    @Autowired private ExamPaperService examPaperService;
    @Autowired private ExamPaperMapper paperMapper;
    @Autowired private CourseMapper courseMapper;
    @Autowired private UserMapper userMapper;

    @Test
    void filtersByTypeAndTrimmedTitleWithoutLeakingOtherUsersPrivatePapers() {
        Long ownerId = user("paper_filter_owner").getId();
        Long otherUserId = user("paper_filter_other").getId();
        Course course = new Course();
        course.setName("试卷筛选课程");
        courseMapper.insert(course);

        ExamPaper official = paper("2026 真题·数据结构", course.getId(), "PUBLIC", null, "OFFICIAL_EXAM", 1);
        ExamPaper practice = paper("2026 真题练习", course.getId(), "PUBLIC", null, "PRACTICE", 1);
        ExamPaper ownedPrivate = paper("2026 真题私有笔记", course.getId(), "PRIVATE", ownerId, "USER_PRIVATE", 1);
        paper("2026 真题他人私卷", course.getId(), "PRIVATE", otherUserId, "USER_PRIVATE", 1);
        paper("2026 真题草稿", course.getId(), "PUBLIC", null, "OFFICIAL_EXAM", 0);

        List<Long> officialIds = examPaperService.getAccessiblePublishedExamPaperPage(
                        ownerId, 1, 10, course.getId(), "OFFICIAL_EXAM", " 2026 真题 ")
                .getRecords().stream().map(ExamPaperVO::getId).toList();
        List<Long> privateIds = examPaperService.getAccessiblePublishedExamPaperPage(
                        ownerId, 1, 10, course.getId(), "USER_PRIVATE", "2026 真题")
                .getRecords().stream().map(ExamPaperVO::getId).toList();

        assertEquals(List.of(official.getId()), officialIds);
        assertEquals(List.of(ownedPrivate.getId()), privateIds);
        assertEquals("PRACTICE", practice.getPaperType());
    }

    private User user(String username) {
        User user = new User();
        user.setUsername(username);
        user.setPassword("$2a$10$dummyHashForTest");
        user.setNickname(username);
        user.setRole("USER");
        user.setStatus(1);
        userMapper.insert(user);
        return user;
    }

    private ExamPaper paper(String title, Long courseId, String visibility, Long ownerUserId,
                            String paperType, int status) {
        ExamPaper paper = new ExamPaper();
        paper.setTitle(title);
        paper.setCourseId(courseId);
        paper.setDuration(30);
        paper.setTotalScore(10);
        paper.setQuestionCount(1);
        paper.setVisibility(visibility);
        paper.setOwnerUserId(ownerUserId);
        paper.setPaperType(paperType);
        paper.setStatus(status);
        paper.setDeleted(0);
        paper.setCreateTime(LocalDateTime.now());
        paper.setUpdateTime(LocalDateTime.now());
        paperMapper.insert(paper);
        return paper;
    }
}
