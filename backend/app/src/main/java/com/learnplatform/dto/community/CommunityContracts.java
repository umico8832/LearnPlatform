package com.learnplatform.dto.community;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

import java.time.LocalDateTime;
import java.util.List;

public final class CommunityContracts {
    private CommunityContracts() {}

    public record Draft(
            @NotBlank @Size(max = 120) String title,
            @NotBlank @Size(max = 4000) String body,
            @NotBlank String contentType,
            @NotBlank String subjectId,
            String schoolId,
            Long courseId,
            Long knowledgePointId,
            @Size(max = 80) String conceptName,
            @Size(max = 500) String sourceNote) {}

    public record Reply(@NotBlank @Size(max = 2000) String body, Long parentId) {}

    public record Review(@NotBlank String decision, @NotBlank @Size(max = 500) String note) {}

    public record School(
            @NotBlank @Size(max = 80) String name, @Size(max = 240) String description) {}

    public record Category(
            String id, String parentId, String kind, String name, String description) {}

    public record Attachment(long id, String name, long sizeBytes) {}

    public record QuestionLink(long submissionId, int status, Long questionId) {}

    public record ReviewEvent(
            long id, String reviewerName, String decision, String note, LocalDateTime createdAt) {}

    public record Query(
            int pageNum,
            int pageSize,
            String keyword,
            String contentType,
            String examId,
            String subjectId,
            String schoolId,
            Long courseId,
            boolean mine,
            String status) {}

    public record Page<T>(List<T> records, long total, int current, int size) {}

    public record Comment(
            long id,
            long postId,
            long userId,
            String authorName,
            Long parentId,
            String replyToName,
            String body,
            boolean deleted,
            LocalDateTime createdAt,
            long likeCount,
            boolean liked) {}

    public record Post(
            long id,
            long userId,
            String authorName,
            String title,
            String body,
            String contentType,
            String subjectId,
            String subjectName,
            String examName,
            String schoolId,
            String schoolName,
            Long courseId,
            String courseName,
            Long knowledgePointId,
            String conceptName,
            String sourceNote,
            String status,
            String reviewNote,
            LocalDateTime createdAt,
            long likeCount,
            long commentCount,
            boolean liked,
            List<Attachment> attachments,
            List<QuestionLink> questionLinks) {
        public Post withDetails(List<Attachment> files, List<QuestionLink> links) {
            return new Post(
                    id,
                    userId,
                    authorName,
                    title,
                    body,
                    contentType,
                    subjectId,
                    subjectName,
                    examName,
                    schoolId,
                    schoolName,
                    courseId,
                    courseName,
                    knowledgePointId,
                    conceptName,
                    sourceNote,
                    status,
                    reviewNote,
                    createdAt,
                    likeCount,
                    commentCount,
                    liked,
                    files,
                    links);
        }
    }
}
