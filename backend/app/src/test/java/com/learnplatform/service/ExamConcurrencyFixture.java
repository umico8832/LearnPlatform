package com.learnplatform.service;

import com.learnplatform.dto.ExamSubmitRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;

import java.sql.Statement;
import java.util.List;
import java.util.Map;
import java.util.UUID;

final class ExamConcurrencyFixture {
    final JdbcTemplate jdbc;
    final long userId;
    final long otherUserId;
    final long courseId;
    final long paperId;
    final long firstQuestion;
    final long secondQuestion;

    ExamConcurrencyFixture(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
        userId = createUser();
        otherUserId = createUser();
        courseId = insert("INSERT INTO course(name) VALUES ('考试事务课程')");
        jdbc.update("INSERT INTO user_course(user_id, course_id) VALUES (?, ?)", userId, courseId);
        paperId = insert("""
                INSERT INTO exam_paper(title, course_id, total_score, duration, question_count, status,
                    visibility, owner_user_id, paper_type)
                VALUES ('考试事务试卷', ?, 20, 60, 2, 1, 'PRIVATE', ?, 'USER_PRIVATE')
                """, courseId, userId);
        firstQuestion = createQuestion(1);
        secondQuestion = createQuestion(2);
    }

    void seedLearningState() {
        jdbc.update("""
                INSERT INTO wrong_question(user_id, question_id, wrong_count, mastery_level, last_wrong_answer)
                VALUES (?, ?, 2, 1, 'previous')
                """, userId, firstQuestion);
        jdbc.update("""
                INSERT INTO question_review_schedule(user_id, question_id, next_review_date, total_reviews)
                VALUES (?, ?, '2026-09-09', 3)
                """, userId, firstQuestion);
    }

    ExamSubmitRequest request(long recordId, String firstAnswer, String secondAnswer) {
        ExamSubmitRequest request = new ExamSubmitRequest();
        request.setExamRecordId(recordId);
        request.setAnswers(List.of(answer(firstQuestion, firstAnswer), answer(secondQuestion, secondAnswer)));
        return request;
    }

    static ExamSubmitRequest.AnswerItem answer(long questionId, String text) {
        ExamSubmitRequest.AnswerItem answer = new ExamSubmitRequest.AnswerItem();
        answer.setQuestionId(questionId);
        answer.setUserAnswer(text);
        return answer;
    }

    Map<String, List<Map<String, Object>>> facts(long recordId) {
        return Map.of(
                "record", jdbc.queryForList("SELECT * FROM exam_record WHERE id = ?", recordId),
                "answers", jdbc.queryForList("SELECT * FROM exam_answer WHERE exam_record_id = ? ORDER BY id", recordId),
                "wrong", jdbc.queryForList("SELECT * FROM wrong_question WHERE user_id = ? ORDER BY id", userId),
                "review", jdbc.queryForList("SELECT * FROM question_review_schedule WHERE user_id = ? ORDER BY id", userId),
                "events", jdbc.queryForList("SELECT * FROM course_learning_event WHERE user_id = ? ORDER BY id", userId));
    }

    private long createQuestion(int order) {
        long id = insert("""
                INSERT INTO question(content, question_type, course_id, analysis, visibility, owner_user_id)
                VALUES ('服务端判分题', 'SINGLE_CHOICE', ?, '服务端解析', 'PRIVATE', ?)
                """, courseId, userId);
        jdbc.update("""
                INSERT INTO question_option(question_id, option_label, content, is_correct, sort_order)
                VALUES (?, 'A', '正确选项', 1, 1), (?, 'B', '错误选项', 0, 2)
                """, id, id);
        jdbc.update("INSERT INTO exam_question(exam_paper_id, question_id, score, sort_order) VALUES (?, ?, 10, ?)",
                paperId, id, order);
        return id;
    }

    private long createUser() {
        return insert("INSERT INTO user(username, password) VALUES (?, 'test-hash')", "race_" + UUID.randomUUID());
    }

    private long insert(String sql, Object... parameters) {
        GeneratedKeyHolder key = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            var statement = connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
            for (int index = 0; index < parameters.length; index++) {
                statement.setObject(index + 1, parameters[index]);
            }
            return statement;
        }, key);
        return key.getKey().longValue();
    }
}
