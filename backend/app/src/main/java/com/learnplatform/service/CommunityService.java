package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.QuestionSubmissionRequest;
import com.learnplatform.dto.QuestionSubmissionVO;
import com.learnplatform.dto.community.CommunityContracts.Attachment;
import com.learnplatform.dto.community.CommunityContracts.Category;
import com.learnplatform.dto.community.CommunityContracts.Comment;
import com.learnplatform.dto.community.CommunityContracts.Draft;
import com.learnplatform.dto.community.CommunityContracts.Page;
import com.learnplatform.dto.community.CommunityContracts.Post;
import com.learnplatform.dto.community.CommunityContracts.Query;
import com.learnplatform.dto.community.CommunityContracts.Reply;
import com.learnplatform.dto.community.CommunityContracts.Review;
import com.learnplatform.dto.community.CommunityContracts.ReviewEvent;
import com.learnplatform.dto.community.CommunityContracts.School;
import com.learnplatform.mapper.CommunityMapper;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;

@Service
public class CommunityService {
    private static final Set<String> TYPES =
            Set.of(
                    "TOPIC",
                    "explanation",
                    "analogy",
                    "common_mistake",
                    "exam_method",
                    "syllabus",
                    "question_bank");
    private static final Set<String> FORMATS =
            Set.of(
                    "pdf", "doc", "docx", "wps", "xls", "xlsx", "et", "csv", "json", "txt", "md",
                    "zip");
    private final CommunityMapper mapper;
    private final QuestionSubmissionService submissions;

    public CommunityService(CommunityMapper mapper, QuestionSubmissionService submissions) {
        this.mapper = mapper;
        this.submissions = submissions;
    }

    public List<Category> categories(long actor) {
        role(actor, false);
        return mapper.categories();
    }

    public Page<Post> posts(long actor, Query query, boolean moderation) {
        String role = role(actor, false);
        if (moderation) {
            requireAdmin(role);
        }
        pagination(query.pageNum(), query.pageSize());
        if (query.keyword() != null && query.keyword().length() > 120) {
            invalid("搜索词不能超过120字");
        }
        return mapper.posts(actor, moderation, query);
    }

    public Post detail(long actor, long id) {
        Post post = readable(actor, id, false);
        return post.withDetails(mapper.attachments(id), mapper.questionLinks(id));
    }

    @Transactional(rollbackFor = IOException.class)
    public long create(long actor, Draft draft, List<MultipartFile> files) throws IOException {
        role(actor, true);
        validateDraft(draft);
        if (files.size() > 8
                || ("question_bank".equals(draft.contentType()) && files.isEmpty())
                || (!"question_bank".equals(draft.contentType()) && !files.isEmpty())) {
            invalid("题库投稿需要1至8个附件，其他内容不能附加文件");
        }
        long bytes = 0;
        for (MultipartFile file : files) {
            validateFile(file);
            bytes += file.getSize();
        }
        if (mapper.storedBytes(actor) + bytes > 240L * 1024 * 1024) {
            invalid("社区附件已达到240MB配额，请删除不再需要的投稿");
        }
        long id =
                mapper.insertPost(
                        actor, draft, "TOPIC".equals(draft.contentType()) ? "APPROVED" : "PENDING");
        for (MultipartFile file : files) {
            mapper.attach(id, file.getOriginalFilename(), file.getBytes());
        }
        return id;
    }

    @Transactional
    public void delete(long actor, long id) {
        Post post = readable(actor, id, true);
        ownerOrAdmin(actor, post.userId());
        mapper.deletePost(id);
    }

    @Transactional
    public void review(long actor, long id, Review review) {
        requireAdmin(role(actor, false));
        Post post = readable(actor, id, true);
        if (!Set.of("APPROVED", "REJECTED", "HIDDEN").contains(review.decision())) {
            invalid("审核状态无效");
        }
        if (review.decision().equals(post.status())) {
            invalid("该内容已经处于此状态");
        }
        mapper.review(id, actor, review.decision(), review.note().trim());
    }

    public List<ReviewEvent> reviews(long actor, long id) {
        Post post = readable(actor, id, false);
        ownerOrAdmin(actor, post.userId());
        return mapper.reviews(id);
    }

    @Transactional
    public String createSchool(long actor, School school) {
        requireAdmin(role(actor, false));
        String id = "school-" + UUID.randomUUID();
        try {
            mapper.addSchool(
                    id,
                    school.name().trim(),
                    school.description() == null ? "" : school.description().trim());
        } catch (DuplicateKeyException exception) {
            invalid("该学校分区已存在");
        }
        return id;
    }

    public Page<Comment> comments(long actor, long id, int page, int size) {
        readable(actor, id, false);
        pagination(page, size);
        return new Page<>(
                mapper.comments(id, actor, page, size), mapper.commentCount(id), page, size);
    }

    @Transactional
    public long comment(long actor, long id, Reply reply) {
        Post post = readable(actor, id, true);
        requirePublished(post);
        if (reply.parentId() != null) {
            long[] parent = mapper.commentIdentity(reply.parentId());
            if (parent == null || parent[0] != id || parent[2] != 0) {
                invalid("回复目标不存在或不属于当前话题");
            }
        }
        return mapper.insertComment(id, actor, reply.parentId(), reply.body());
    }

    @Transactional
    public void deleteComment(long actor, long id) {
        long[] comment = mapper.commentIdentity(id);
        if (comment == null) {
            missing();
        }
        readable(actor, comment[0], true);
        ownerOrAdmin(actor, comment[1]);
        mapper.deleteComment(id);
    }

    @Transactional
    public void like(long actor, long postId, long commentId, boolean liked) {
        Post post = readable(actor, postId, true);
        requirePublished(post);
        if (commentId != 0) {
            long[] comment = mapper.commentIdentity(commentId);
            if (comment == null || comment[0] != postId || comment[2] != 0) {
                missing();
            }
        }
        mapper.like(postId, commentId, actor, liked);
    }

    public Attachment attachment(long actor, long id) {
        Long postId = mapper.attachmentPost(id);
        if (postId == null) {
            missing();
        }
        readable(actor, postId, false);
        return mapper.attachments(postId).stream()
                .filter(file -> file.id() == id)
                .findFirst()
                .orElseThrow(() -> new BusinessException(ResultCode.NOT_FOUND));
    }

    @Transactional(readOnly = true)
    public byte[] download(long actor, long id) {
        attachment(actor, id);
        return mapper.attachmentBytes(id);
    }

    @Transactional
    public QuestionSubmissionVO prepareQuestion(
            long actor, long id, QuestionSubmissionRequest request, String requestKey) {
        requireAdmin(role(actor, false));
        Post post = readable(actor, id, true);
        requirePublished(post);
        if (requestKey == null || !requestKey.matches("[A-Za-z0-9-]{16,64}")) {
            invalid("请求标识无效");
        }
        Long existing = mapper.linkedQuestion(id, requestKey);
        if (existing != null) {
            return submissions.getSubmissionById(existing);
        }
        if (!"question_bank".equals(post.contentType())) {
            invalid("只有题库投稿可以整理题目");
        }
        if (!mapper.courseAvailable(request.getCourseId())
                || (post.courseId() != null && !post.courseId().equals(request.getCourseId()))) {
            invalid("题目课程必须与投稿关联的可用课程一致");
        }
        if (request.getKnowledgePointIds() != null && !request.getKnowledgePointIds().isBlank()) {
            try {
                for (String key : request.getKnowledgePointIds().split(",")) {
                    if (!mapper.knowledgeMatches(Long.valueOf(key.trim()), request.getCourseId())) {
                        invalid("知识点不属于当前课程");
                    }
                }
            } catch (NumberFormatException exception) {
                invalid("知识点格式无效");
            }
        }
        request.setSource("community:" + id);
        QuestionSubmissionVO submission = submissions.submitQuestion(request, post.userId());
        mapper.linkQuestion(id, submission.getId(), requestKey);
        return submission;
    }

    private Post readable(long actor, long id, boolean lock) {
        String role = role(actor, false);
        Post post = mapper.post(id, actor, lock);
        if (post == null) {
            missing();
        }
        boolean privileged = post.userId() == actor || "ADMIN".equals(role);
        if (!privileged
                && (!"APPROVED".equals(post.status())
                        || (post.courseId() != null && !mapper.courseAvailable(post.courseId())))) {
            missing();
        }
        return post;
    }

    private void validateDraft(Draft draft) {
        if (!TYPES.contains(draft.contentType())) {
            invalid("内容类型无效");
        }
        List<Category> categories = mapper.categories();
        if (categories.stream()
                .noneMatch(c -> c.id().equals(draft.subjectId()) && "SUBJECT".equals(c.kind()))) {
            invalid("请选择有效的考试科目");
        }
        if (draft.schoolId() != null
                && categories.stream()
                        .noneMatch(
                                c ->
                                        c.id().equals(draft.schoolId())
                                                && "SCHOOL".equals(c.kind()))) {
            invalid("学校分区不存在");
        }
        if (draft.courseId() != null && !mapper.courseAvailable(draft.courseId())) {
            invalid("关联课程不可用");
        }
        if (draft.knowledgePointId() != null
                && (draft.courseId() == null
                        || !mapper.knowledgeMatches(draft.knowledgePointId(), draft.courseId()))) {
            invalid("知识点不属于关联课程");
        }
        if (!"TOPIC".equals(draft.contentType())
                && (draft.sourceNote() == null || draft.sourceNote().isBlank())) {
            invalid("投稿必须说明来源与使用许可");
        }
    }

    private static void validateFile(MultipartFile file) {
        String name = file.getOriginalFilename();
        if (name == null
                || name.length() > 180
                || name.contains("/")
                || name.contains("\\")
                || name.chars().anyMatch(Character::isISOControl)) {
            invalid("附件文件名无效");
        }
        String extension = name.substring(name.lastIndexOf('.') + 1).toLowerCase(Locale.ROOT);
        if (!FORMATS.contains(extension) || file.isEmpty() || file.getSize() > 30L * 1024 * 1024) {
            invalid("附件格式不受支持或文件超过30MB");
        }
    }

    private String role(long actor, boolean lock) {
        String role = mapper.actorRole(actor, lock);
        if (role == null) {
            throw new BusinessException(ResultCode.UNAUTHORIZED);
        }
        return role;
    }

    private void ownerOrAdmin(long actor, long owner) {
        if (actor != owner && !Objects.equals(role(actor, false), "ADMIN")) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private static void requireAdmin(String role) {
        if (!"ADMIN".equals(role)) {
            throw new BusinessException(ResultCode.FORBIDDEN);
        }
    }

    private static void requirePublished(Post post) {
        if (!"APPROVED".equals(post.status())) {
            invalid("该内容当前未公开，无法执行此操作");
        }
    }

    private static void pagination(int page, int size) {
        if (page < 1 || page > 100000 || size < 1 || size > 50) {
            invalid("分页参数无效");
        }
    }

    private static void invalid(String message) {
        throw new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }

    private static void missing() {
        throw new BusinessException(ResultCode.NOT_FOUND, "社区内容不存在或不可访问");
    }
}
