package com.learnplatform.mapper;

import org.apache.ibatis.annotations.Delete;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface PrivateExamContentLifecycleMapper {
    @Select("""
            SELECT (SELECT COUNT(*) FROM exam_record WHERE exam_paper_id = #{paperId})
                 + (SELECT COUNT(*) FROM exam_learning_session WHERE exam_paper_id = #{paperId})
                 + (SELECT COUNT(*) FROM exam_learning_ai_interaction WHERE exam_paper_id = #{paperId})
            """)
    long countPaperReferences(Long paperId);

    @Select("SELECT question_id FROM exam_question WHERE exam_paper_id = #{paperId} ORDER BY sort_order")
    List<Long> selectQuestionIds(Long paperId);

    @Select("""
            <script>
            SELECT
              (SELECT COUNT(*) FROM exam_question WHERE exam_paper_id != #{paperId}
                 AND question_id IN <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM practice_record WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM wrong_question WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM exam_answer WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM user_favorite_question WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_review_schedule WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM exam_learning_answer WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM exam_learning_ai_interaction WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM course_learning_event WHERE subject_type = 'QUESTION' AND subject_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_comment WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_ai_asset WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM ai_asset_feedback WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM ai_asset_view WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM ai_variant_training WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_correction_report WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_version WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_review_record WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM subjective_grading_point WHERE question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
              + (SELECT COUNT(*) FROM question_submission WHERE imported_question_id IN
                 <foreach collection='questionIds' item='id' open='(' separator=',' close=')'>#{id}</foreach>)
            </script>
            """)
    long countQuestionReferences(@Param("paperId") Long paperId,
                                 @Param("questionIds") List<Long> questionIds);

    @Delete("DELETE FROM private_exam_draft_question WHERE draft_id = #{draftId}")
    int deleteDraftQuestions(Long draftId);

    @Delete("DELETE FROM private_exam_import_draft WHERE id = #{draftId} AND owner_user_id = #{ownerUserId}")
    int deleteDraft(@Param("draftId") Long draftId, @Param("ownerUserId") Long ownerUserId);

    @Delete("DELETE FROM exam_question WHERE exam_paper_id = #{paperId}")
    int deleteExamQuestions(Long paperId);

    @Delete("""
            <script>DELETE FROM question_option WHERE question_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>
            """)
    int deleteQuestionOptions(@Param("ids") List<Long> questionIds);

    @Delete("""
            <script>DELETE FROM question_knowledge_point WHERE question_id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>
            """)
    int deleteQuestionKnowledgePoints(@Param("ids") List<Long> questionIds);

    @Delete("""
            <script>DELETE FROM question WHERE id IN
            <foreach collection='ids' item='id' open='(' separator=',' close=')'>#{id}</foreach></script>
            """)
    int deleteQuestions(@Param("ids") List<Long> questionIds);

    @Delete("DELETE FROM exam_paper WHERE id = #{paperId} AND owner_user_id = #{ownerUserId} AND visibility = 'PRIVATE'")
    int deletePrivatePaper(@Param("paperId") Long paperId, @Param("ownerUserId") Long ownerUserId);

    @Delete("""
            DELETE q FROM private_exam_draft_question q
            JOIN private_exam_import_draft d ON d.id = q.draft_id
            WHERE d.confirmed_paper_id = #{paperId} AND d.owner_user_id = #{ownerUserId}
            """)
    int deleteConfirmedDraftQuestions(@Param("paperId") Long paperId,
                                      @Param("ownerUserId") Long ownerUserId);

    @Delete("""
            DELETE FROM private_exam_import_draft
            WHERE confirmed_paper_id = #{paperId} AND owner_user_id = #{ownerUserId}
            """)
    int deleteConfirmedDrafts(@Param("paperId") Long paperId, @Param("ownerUserId") Long ownerUserId);

    @Delete("""
            DELETE FROM user_exam_source
            WHERE id = #{sourceId} AND owner_user_id = #{ownerUserId}
              AND NOT EXISTS (SELECT 1 FROM private_exam_import_draft WHERE source_record_id = #{sourceId})
              AND NOT EXISTS (SELECT 1 FROM exam_paper WHERE source_record_id = #{sourceId} AND deleted = 0)
            """)
    int deleteSourceIfUnreferenced(@Param("sourceId") Long sourceId, @Param("ownerUserId") Long ownerUserId);
}
