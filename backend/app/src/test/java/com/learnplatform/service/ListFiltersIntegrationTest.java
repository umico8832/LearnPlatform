package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.IntegrationTestBase;
import com.learnplatform.security.CustomUserDetails;
import org.springframework.boot.webmvc.test.autoconfigure.AutoConfigureMockMvc;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.test.web.servlet.MockMvc;
import java.util.List;
import static org.springframework.security.test.web.servlet.request.SecurityMockMvcRequestPostProcessors.authentication;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
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
@AutoConfigureMockMvc
@ActiveProfiles("integration")
@Transactional
class ListFiltersIntegrationTest extends IntegrationTestBase {
    private static final long USER = 98765431L;
    @Autowired JdbcTemplate db;
    @Autowired MockMvc mvc;
    @Autowired QuestionService questions;
    @Autowired FavoriteService favorites;
    @Autowired PracticeService practices;
    @Autowired QuestionSubmissionService submissions;

    @Test void questionKeywordIsAppliedBeforePaginationAndKeepsPublicScope() {
        long course = course();
        long id = question(course, "H4-needle-%_!\\'-public", "SINGLE_CHOICE", "PUBLIC");
        question(course, "H4-other-public", "SINGLE_CHOICE", "PUBLIC");
        question(course, "H4-private-needle", "SINGLE_CHOICE", "PRIVATE");
        db.update("INSERT INTO question(content,question_type,course_id,difficulty,status,visibility,deleted) VALUES ('H4-draft-needle','SINGLE_CHOICE',?,3,1,'DRAFT',0)", course);
        var result = questions.getEnabledQuestionPage(new Page<>(1, 10), null, course, null, null, null,
                null, USER, "H4-needle-%_!\\'");
        assertEquals(java.util.List.of(id), result.getRecords().stream().map(row -> row.getId()).toList());
        assertEquals(1, result.getTotal());
        assertEquals(0, questions.getEnabledQuestionPage(new Page<>(2, 1), null, course, null, null, null,
                null, USER, "H4-needle-%_!\\'").getRecords().size());
    }

    @Test void favoriteFiltersAreAndScopedToCurrentUser() {
        long course = course();
        long otherCourse = course();
        long matching = question(course, "H4-favorite-%_!\\", "SINGLE_CHOICE", "PUBLIC");
        long wrongType = question(course, "H4-favorite-%_!\\", "TRUE_FALSE", "PUBLIC");
        long wrongCourse = question(otherCourse, "H4-favorite-%_!\\", "SINGLE_CHOICE", "PUBLIC");
        long wrongKeyword = question(course, "H4-favorite-other", "SINGLE_CHOICE", "PUBLIC");
        db.update("INSERT INTO user_favorite_question(user_id,question_id) VALUES (?,?),(?,?),(?,?),(?,?)",
                USER, matching, USER, wrongType, USER, wrongCourse, USER, wrongKeyword);
        db.update("INSERT INTO user_favorite_question(user_id,question_id) VALUES (?,?)", USER + 1, matching);
        var result = favorites.getFavorites(USER, new Page<>(1, 1), course, "SINGLE_CHOICE", "H4-favorite-%_!\\");
        assertEquals(1, result.getTotal());
        assertEquals(matching, result.getRecords().getFirst().getQuestionId());
        assertEquals(0, favorites.getFavorites(USER, new Page<>(1, 10), Long.MAX_VALUE, "SINGLE_CHOICE", "H4-favorite-%_!\\").getTotal());
    }

    @Test void practiceRecordKeywordDoesNotExposeOtherUsersOrIgnoreKeyword() {
        long course = course();
        long otherCourse = course();
        long matching = question(course, "H4-record-%_!\\", "SINGLE_CHOICE", "PUBLIC");
        long wrongType = question(course, "H4-record-%_!\\", "TRUE_FALSE", "PUBLIC");
        long wrongCourse = question(otherCourse, "H4-record-%_!\\", "SINGLE_CHOICE", "PUBLIC");
        long wrongKeyword = question(course, "H4-record-other", "SINGLE_CHOICE", "PUBLIC");
        db.update("INSERT INTO practice_record(user_id,question_id,user_answer,is_correct) VALUES (?,?,?,1),(?,?,?,1),(?,?,?,1),(?,?,?,1),(?,?,?,0),(?,?,?,1)",
                USER, matching, "A", USER, wrongType, "A", USER, wrongCourse, "A", USER, wrongKeyword, "A",
                USER, matching, "B", USER + 1, matching, "A");
        var result = practices.getUserPracticeRecords(USER, new Page<>(1, 1), "SINGLE_CHOICE", course, 1, "H4-record-%_!\\");
        assertEquals(1, result.getTotal());
        assertEquals(matching, result.getRecords().getFirst().getQuestionId());
        var incorrect = practices.getUserPracticeRecords(USER, new Page<>(1, 10), "SINGLE_CHOICE", course, 0,
                "H4-record-%_!\\");
        assertEquals(1, incorrect.getTotal());
        assertEquals(0, incorrect.getRecords().getFirst().getIsCorrect());
    }

    @Test void submissionFiltersRemainOwnedByCurrentUser() {
        long course = course();
        long otherCourse = course();
        db.update("INSERT INTO question_submission(user_id,content,question_type,course_id,difficulty,status,deleted) VALUES (?,?,?, ?,3,0,0)",
                USER, "H4-submission-content", "SINGLE_CHOICE", course);
        db.update("INSERT INTO question_submission(user_id,content,question_type,course_id,difficulty,status,deleted) VALUES (?,?,?, ?,3,0,0),(?,?,?, ?,3,0,0),(?,?,?, ?,3,0,0),(?,?,?, ?,3,1,0),(?,?,?, ?,3,0,0)",
                USER, "H4-submission-content", "TRUE_FALSE", course,
                USER, "H4-submission-content", "SINGLE_CHOICE", otherCourse,
                USER, "H4-submission-other", "SINGLE_CHOICE", course,
                USER, "H4-submission-content", "SINGLE_CHOICE", course,
                USER + 1, "H4-submission-content", "SINGLE_CHOICE", course);
        var result = submissions.getMySubmissions(USER, new Page<>(1, 1), 0, course, "SINGLE_CHOICE", "H4-submission-content");
        assertEquals(1, result.getTotal());
        assertEquals("H4-submission-content", result.getRecords().getFirst().getContent());
        assertEquals(0, submissions.getMySubmissions(USER, new Page<>(1, 10), 0, Long.MAX_VALUE, "SINGLE_CHOICE",
                "H4-submission-content").getTotal());
    }

    @Test
    void filteredFavoritesCountOnlyReadableQuestionsBeforePaginating() {
        long course = course();
        long visible = question(course, "H4-scope", "SINGLE_CHOICE", "PUBLIC");
        long owned = question(course, "H4-scope", "SINGLE_CHOICE", "PRIVATE");
        long other = question(course, "H4-scope", "SINGLE_CHOICE", "PRIVATE");
        long draft = question(course, "H4-scope", "SINGLE_CHOICE", "DRAFT");
        long deleted = question(course, "H4-scope", "SINGLE_CHOICE", "PUBLIC");
        db.update("UPDATE question SET status=0 WHERE id=?", visible);
        db.update("UPDATE question SET owner_user_id=? WHERE id=?", USER, owned);
        db.update("UPDATE question SET owner_user_id=? WHERE id=?", USER + 1, other);
        db.update("UPDATE question SET deleted=1 WHERE id=?", deleted);
        for (long id : List.of(visible, owned, other, draft, deleted)) {
            db.update("INSERT INTO user_favorite_question(user_id,question_id) VALUES (?,?)", USER, id);
        }
        var result = favorites.getFavorites(USER, new Page<>(1, 10), course, "SINGLE_CHOICE", "H4-scope");
        assertEquals(2, result.getTotal());
        assertEquals(java.util.Set.of(visible, owned), result.getRecords().stream()
                .map(row -> row.getQuestionId()).collect(java.util.stream.Collectors.toSet()));
        var first = favorites.getFavorites(USER, new Page<>(1, 1), course, "SINGLE_CHOICE", "H4-scope");
        assertEquals(2, first.getTotal());
        assertEquals(1, first.getRecords().size());
    }

    @Test
    void validatesEveryHttpListBoundaryEvenWithoutNewFilters() throws Exception {
        var auth = new UsernamePasswordAuthenticationToken(new CustomUserDetails(USER, "list-user", "USER"),
                null, List.of(new SimpleGrantedAuthority("ROLE_USER")));
        for (String path : List.of("/api/questions", "/api/favorites", "/api/practice/records", "/api/submission/my")) {
            for (String[] invalid : new String[][]{{"pageNum", "0"}, {"pageSize", "0"},
                    {"keyword", "x".repeat(101)}}) {
                mvc.perform(get(path).param(invalid[0], invalid[1]).with(authentication(auth)))
                        .andExpect(jsonPath("$.code").value(1001));
            }
            mvc.perform(get(path).param("pageSize", "999").param("courseId", Long.toString(Long.MAX_VALUE))
                            .with(authentication(auth)))
                    .andExpect(jsonPath("$.code").value(0)).andExpect(jsonPath("$.data.size").value(500));
        }
    }

    private long course() {
        db.update("INSERT INTO course(name,status,deleted) VALUES (?,1,0)", "H4 list filter course " + System.nanoTime());
        return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }

    private long question(long course, String content, String questionType, String visibility) {
        db.update("INSERT INTO question(content,question_type,course_id,difficulty,status,visibility,deleted) VALUES (?,?,?,3,1,?,0)",
                content, questionType, course, visibility);
        return db.queryForObject("SELECT LAST_INSERT_ID()", Long.class);
    }
}
