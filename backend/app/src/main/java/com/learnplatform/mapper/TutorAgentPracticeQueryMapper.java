package com.learnplatform.mapper;

import com.learnplatform.dto.TutorAgentPracticeQuestionRow;
import com.learnplatform.dto.TutorAgentPracticeQuestionVO;
import com.learnplatform.entity.TutorAgentMessage;
import com.learnplatform.entity.TutorSession;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/** Queries for Tutor Agent practice that enforce publication and session scope in SQL. */
@Mapper
public interface TutorAgentPracticeQueryMapper {

    @Select("""
            SELECT * FROM tutor_session
            WHERE user_id = #{userId} AND course_id = #{courseId} AND session_key = #{sessionKey}
            """)
    TutorSession findSession(@Param("userId") Long userId, @Param("courseId") Long courseId,
                             @Param("sessionKey") String sessionKey);

    @Select("""
            SELECT published.id, published.content, published.question_type AS questionType
            FROM ai_variant_question variant
            JOIN question published ON published.id = variant.published_question_id
            JOIN question_ai_asset asset ON asset.id = variant.asset_id AND asset.deleted = 0
            JOIN question mother ON mother.id = asset.question_id
            JOIN question_knowledge_point published_point ON published_point.question_id = published.id
            JOIN question_knowledge_point mother_point ON mother_point.question_id = mother.id
            JOIN tutor_session session ON session.id = #{sessionId}
            WHERE variant.review_status = 'APPROVED'
              AND variant.question_type = 'SINGLE_CHOICE'
              AND published.course_id = session.course_id
              AND published.question_type = 'SINGLE_CHOICE'
              AND published.visibility = 'PUBLIC' AND published.status = 1 AND published.deleted = 0
              AND mother.course_id = session.course_id
              AND mother.visibility = 'PUBLIC' AND mother.status = 1 AND mother.deleted = 0
              AND published_point.knowledge_point_id = session.knowledge_point_id
              AND mother_point.knowledge_point_id = session.knowledge_point_id
              AND NOT EXISTS (
                  SELECT 1 FROM tutor_agent_practice_attempt attempt
                  JOIN tutor_agent_message message ON message.id = attempt.message_id
                  JOIN tutor_agent_run run ON run.id = message.run_id
                  WHERE attempt.question_id = published.id AND run.tutor_session_id = session.id
              )
            ORDER BY variant.id ASC, published.id ASC
            LIMIT 1
            """)
    TutorAgentPracticeQuestionRow findRecommendedQuestion(@Param("sessionId") Long sessionId);

    @Select("""
            SELECT option_label AS label, content
            FROM question_option
            WHERE question_id = #{questionId} AND deleted = 0
            ORDER BY sort_order ASC, id ASC
            """)
    List<TutorAgentPracticeQuestionVO.Option> findPublicOptions(@Param("questionId") Long questionId);

    @Select("""
            SELECT message.*
            FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND run.tutor_session_id = #{sessionId}
              AND message.sequence_no = #{sequence} AND message.role = 'ASSISTANT'
            """)
    TutorAgentMessage findPracticeMessage(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                                           @Param("runKey") String runKey, @Param("sequence") Integer sequence);

    @Select("""
            SELECT message.*
            FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND run.tutor_session_id = #{sessionId}
              AND message.sequence_no = #{sequence} AND message.role = 'ASSISTANT'
            FOR UPDATE
            """)
    TutorAgentMessage findPracticeMessageForUpdate(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                                                    @Param("runKey") String runKey, @Param("sequence") Integer sequence);

    @Select("""
            SELECT message.*
            FROM tutor_agent_message message
            JOIN tutor_agent_run run ON run.id = message.run_id
            WHERE run.run_key = #{runKey} AND run.user_id = #{userId}
              AND run.tutor_session_id = #{sessionId} AND message.role = 'ASSISTANT'
              AND JSON_CONTAINS(message.actions_json, JSON_OBJECT('type', 'PRACTICE'))
            ORDER BY message.sequence_no DESC, message.id DESC
            LIMIT 1
            """)
    TutorAgentMessage findLatestPracticeMessage(@Param("userId") Long userId, @Param("sessionId") Long sessionId,
                                                 @Param("runKey") String runKey);

    @Select("""
            SELECT COUNT(*) > 0 FROM tutor_agent_run
            WHERE run_key = #{runKey} AND user_id = #{userId} AND tutor_session_id = #{sessionId}
            """)
    boolean ownsRun(@Param("userId") Long userId, @Param("sessionId") Long sessionId, @Param("runKey") String runKey);

    @Select("""
            SELECT published.id, published.content, published.question_type AS questionType
            FROM ai_variant_question variant
            JOIN question published ON published.id = variant.published_question_id
            JOIN question_ai_asset asset ON asset.id = variant.asset_id AND asset.deleted = 0
            JOIN question mother ON mother.id = asset.question_id
            JOIN question_knowledge_point published_point ON published_point.question_id = published.id
            JOIN question_knowledge_point mother_point ON mother_point.question_id = mother.id
            JOIN tutor_session session ON session.id = #{sessionId}
            WHERE published.id = #{questionId}
              AND variant.review_status = 'APPROVED' AND variant.question_type = 'SINGLE_CHOICE'
              AND published.course_id = session.course_id AND published.question_type = 'SINGLE_CHOICE'
              AND published.visibility = 'PUBLIC' AND published.status = 1 AND published.deleted = 0
              AND mother.course_id = session.course_id
              AND mother.visibility = 'PUBLIC' AND mother.status = 1 AND mother.deleted = 0
              AND published_point.knowledge_point_id = session.knowledge_point_id
              AND mother_point.knowledge_point_id = session.knowledge_point_id
            LIMIT 1
            """)
    TutorAgentPracticeQuestionRow findEligibleQuestion(@Param("sessionId") Long sessionId,
                                                        @Param("questionId") Long questionId);

    @Select("""
            SELECT COUNT(*) > 0 FROM question_option
            WHERE question_id = #{questionId} AND option_label = #{label} AND deleted = 0
            """)
    boolean hasOption(@Param("questionId") Long questionId, @Param("label") String label);
}
