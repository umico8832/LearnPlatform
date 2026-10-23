package com.learnplatform.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import java.util.List;

/** Current locking reads used after a parent row is locked for deletion. */
@Mapper
public interface ContentReferenceIntegrityMapper {
    @Select("<script>SELECT question_id FROM exam_question WHERE exam_paper_id=#{paper} ORDER BY question_id"
            + "<if test='lock'> FOR SHARE</if></script>")
    List<Long> paperQuestionIds(@Param("paper") Long paper, @Param("lock") boolean lock);

    @Select("SELECT question_id FROM course_stage_assessment_question WHERE assessment_id=#{id} ORDER BY question_id")
    List<Long> assessmentQuestionIds(@Param("id") Long id);
    @Select("SELECT id FROM knowledge_point WHERE course_id=#{courseId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long courseKnowledgePoint(@Param("courseId") Long courseId);
    @Select("SELECT id FROM question WHERE course_id=#{courseId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long courseQuestion(@Param("courseId") Long courseId);
    @Select("SELECT id FROM exam_paper WHERE course_id=#{courseId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long courseExamPaper(@Param("courseId") Long courseId);
    @Select("SELECT id FROM user_course WHERE course_id=#{courseId} LIMIT 1 FOR SHARE")
    Long courseUserCourse(@Param("courseId") Long courseId);
    @Select("SELECT id FROM course_learning_event WHERE course_id=#{courseId} LIMIT 1 FOR SHARE")
    Long courseLearningEvent(@Param("courseId") Long courseId);
    @Select("SELECT id FROM tutor_session WHERE course_id=#{courseId} LIMIT 1 FOR SHARE")
    Long courseTutorSession(@Param("courseId") Long courseId);
    @Select("SELECT id FROM course_stage_assessment WHERE course_id=#{courseId} LIMIT 1 FOR SHARE")
    Long courseStageAssessment(@Param("courseId") Long courseId);
    @Select("SELECT id FROM question_submission WHERE course_id=#{courseId} LIMIT 1 FOR SHARE")
    Long courseQuestionSubmission(@Param("courseId") Long courseId);
    @Select("SELECT id FROM community_post WHERE course_id=#{courseId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long courseCommunityPost(@Param("courseId") Long courseId);

    @Select("SELECT id FROM knowledge_point WHERE parent_id=#{knowledgePointId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long pointChild(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM question_knowledge_point WHERE knowledge_point_id=#{knowledgePointId} LIMIT 1 FOR SHARE")
    Long pointQuestionRelation(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM tutor_content WHERE knowledge_point_id=#{knowledgePointId} LIMIT 1 FOR SHARE")
    Long pointTutorContent(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM tutor_session WHERE knowledge_point_id=#{knowledgePointId} LIMIT 1 FOR SHARE")
    Long pointTutorSession(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM course_stage_assessment WHERE target_knowledge_point_id=#{knowledgePointId} LIMIT 1 FOR SHARE")
    Long pointStageAssessment(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM question_submission WHERE FIND_IN_SET(CAST(#{knowledgePointId} AS CHAR CHARACTER SET utf8mb4) COLLATE utf8mb4_unicode_ci, knowledge_point_ids COLLATE utf8mb4_unicode_ci)>0 LIMIT 1 FOR SHARE")
    Long pointQuestionSubmission(@Param("knowledgePointId") Long knowledgePointId);
    @Select("SELECT id FROM community_post WHERE knowledge_point_id=#{knowledgePointId} AND deleted=0 LIMIT 1 FOR SHARE")
    Long pointCommunityPost(@Param("knowledgePointId") Long knowledgePointId);
}
