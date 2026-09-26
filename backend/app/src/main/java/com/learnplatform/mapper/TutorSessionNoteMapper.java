package com.learnplatform.mapper;

import com.learnplatform.dto.TutorSessionNoteRow;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface TutorSessionNoteMapper {
    String AVAILABLE = """
            tc.review_status = 'REVIEWED' AND tc.knowledge_point_id = s.knowledge_point_id
            AND kp.deleted = 0 AND kp.course_id = s.course_id AND kp.content_review_status = 'REVIEWED'
            """;
    String ROW = """
            SELECT s.id AS session_id, s.session_key, COALESCE(n.revision, 0) AS revision, n.note, n.updated_at,
              s.knowledge_point_id, tc.title, s.create_time AS session_started_at,
              s.check_correct, s.check_answered_at,
            """ + "CASE WHEN " + AVAILABLE + " THEN 1 ELSE 0 END AS available " + """
            FROM tutor_session s LEFT JOIN tutor_session_note n ON n.session_id = s.id
            LEFT JOIN tutor_content tc ON tc.id = s.tutor_content_id
            LEFT JOIN knowledge_point kp ON kp.id = s.knowledge_point_id
            WHERE s.user_id = #{userId} AND s.course_id = #{courseId}
            """;

    @Select(ROW + " AND s.session_key = #{sessionKey}")
    TutorSessionNoteRow find(@Param("userId") Long userId, @Param("courseId") Long courseId,
                             @Param("sessionKey") String sessionKey);

    @Select(ROW + " AND s.session_key = #{sessionKey} FOR UPDATE")
    TutorSessionNoteRow lock(@Param("userId") Long userId, @Param("courseId") Long courseId,
                             @Param("sessionKey") String sessionKey);

    @Select(ROW + " AND n.note IS NOT NULL ORDER BY n.updated_at DESC, s.id DESC LIMIT 5 OFFSET #{offset}")
    List<TutorSessionNoteRow> list(@Param("userId") Long userId, @Param("courseId") Long courseId,
                                  @Param("offset") long offset);

    @Select("""
            SELECT COUNT(*) FROM tutor_session_note n JOIN tutor_session s ON s.id = n.session_id
            WHERE s.user_id = #{userId} AND s.course_id = #{courseId} AND n.note IS NOT NULL
            """)
    long count(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Select(ROW + " AND n.note IS NOT NULL AND " + AVAILABLE + " ORDER BY n.updated_at DESC, s.id DESC LIMIT 5")
    List<TutorSessionNoteRow> recentAvailable(@Param("userId") Long userId, @Param("courseId") Long courseId);

    @Insert("""
            INSERT INTO tutor_session_note (session_id, revision, note) VALUES (#{sessionId}, 1, #{note})
            """)
    void insert(@Param("sessionId") Long sessionId, @Param("note") String note);

    @Update("""
            UPDATE tutor_session_note SET note = #{note}, revision = revision + 1, updated_at = CURRENT_TIMESTAMP(6)
            WHERE session_id = #{sessionId} AND revision = #{revision}
            """)
    int update(@Param("sessionId") Long sessionId, @Param("revision") long revision, @Param("note") String note);
}
