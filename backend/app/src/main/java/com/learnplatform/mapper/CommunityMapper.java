package com.learnplatform.mapper;

import com.learnplatform.dto.community.CommunityContracts.Attachment;
import com.learnplatform.dto.community.CommunityContracts.Category;
import com.learnplatform.dto.community.CommunityContracts.Comment;
import com.learnplatform.dto.community.CommunityContracts.Draft;
import com.learnplatform.dto.community.CommunityContracts.Page;
import com.learnplatform.dto.community.CommunityContracts.Post;
import com.learnplatform.dto.community.CommunityContracts.Query;
import com.learnplatform.dto.community.CommunityContracts.QuestionLink;
import com.learnplatform.dto.community.CommunityContracts.ReviewEvent;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

@Repository
public class CommunityMapper {
    private final JdbcTemplate jdbc;
    private static final String FROM =
            """
            FROM community_post p JOIN user u ON u.id=p.user_id
            JOIN community_category s ON s.id=p.subject_id
            JOIN community_category e ON e.id=s.parent_id
            LEFT JOIN community_category school ON school.id=p.school_id
            LEFT JOIN course c ON c.id=p.course_id
            """;
    private static final String SELECT =
            """
            SELECT p.*, COALESCE(NULLIF(u.nickname,''),u.username) author_name,
            s.name subject_name,e.name exam_name,school.name school_name,c.name course_name,
            (SELECT COUNT(*) FROM community_like l WHERE l.post_id=p.id AND l.comment_id=0) like_count,
            (SELECT COUNT(*) FROM community_comment r WHERE r.post_id=p.id AND r.deleted=0) comment_count,
            EXISTS(SELECT 1 FROM community_like l WHERE l.post_id=p.id AND l.comment_id=0 AND l.user_id=?) liked
            """;
    private static final RowMapper<Post> POST =
            (r, row) ->
                    new Post(
                            r.getLong("id"),
                            r.getLong("user_id"),
                            r.getString("author_name"),
                            r.getString("title"),
                            r.getString("body"),
                            r.getString("content_type"),
                            r.getString("subject_id"),
                            r.getString("subject_name"),
                            r.getString("exam_name"),
                            r.getString("school_id"),
                            r.getString("school_name"),
                            r.getObject("course_id", Long.class),
                            r.getString("course_name"),
                            r.getObject("knowledge_point_id", Long.class),
                            r.getString("concept_name"),
                            r.getString("source_note"),
                            r.getString("status"),
                            r.getString("review_note"),
                            r.getTimestamp("created_at").toLocalDateTime(),
                            r.getLong("like_count"),
                            r.getLong("comment_count"),
                            r.getBoolean("liked"),
                            List.of(),
                            List.of());

    public CommunityMapper(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    public String actorRole(long userId, boolean lock) {
        return jdbc
                .query(
                        "SELECT role FROM user WHERE id=? AND deleted=0 AND status=1"
                                + (lock ? " FOR UPDATE" : ""),
                        (r, n) -> r.getString(1),
                        userId)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public List<Category> categories() {
        return jdbc.query(
                "SELECT * FROM community_category ORDER BY kind,id",
                (r, n) ->
                        new Category(
                                r.getString("id"),
                                r.getString("parent_id"),
                                r.getString("kind"),
                                r.getString("name"),
                                r.getString("description")));
    }

    public void addSchool(String id, String name, String description) {
        jdbc.update(
                "INSERT INTO community_category (id,kind,name,description) VALUES (?,'SCHOOL',?,?)",
                id,
                name,
                description);
    }

    public boolean courseAvailable(Long id) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM course WHERE id=? AND status=1 AND deleted=0",
                        Long.class,
                        id)
                > 0;
    }

    public boolean knowledgeMatches(Long id, Long courseId) {
        return jdbc.queryForObject(
                        "SELECT COUNT(*) FROM knowledge_point WHERE id=? AND course_id=? AND"
                            + " deleted=0",
                        Long.class,
                        id,
                        courseId)
                > 0;
    }

    public long insertPost(long userId, Draft d, String status) {
        return insert(
                """
                INSERT INTO community_post (user_id,title,body,content_type,subject_id,school_id,course_id,
                knowledge_point_id,concept_name,source_note,status) VALUES (?,?,?,?,?,?,?,?,?,?,?)
                """,
                userId,
                d.title().trim(),
                d.body().trim(),
                d.contentType(),
                d.subjectId(),
                d.schoolId(),
                d.courseId(),
                d.knowledgePointId(),
                d.conceptName() == null ? "" : d.conceptName().trim(),
                d.sourceNote() == null ? "" : d.sourceNote().trim(),
                status);
    }

    public Post post(long id, long userId, boolean lock) {
        if (lock) {
            jdbc.queryForList("SELECT id FROM community_post WHERE id=? FOR UPDATE", id);
        }
        return jdbc
                .query(SELECT + FROM + " WHERE p.id=? AND p.deleted=0", POST, userId, id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public Page<Post> posts(long userId, boolean admin, Query query) {
        StringBuilder where = new StringBuilder(" WHERE p.deleted=0");
        List<Object> args = new ArrayList<>();
        if (!admin) {
            where.append(
                    " AND ((p.status='APPROVED' AND (p.course_id IS NULL OR (c.status=1 AND"
                        + " c.deleted=0))) OR p.user_id=?)");
            args.add(userId);
        }
        if (query.mine()) {
            where.append(" AND p.user_id=?");
            args.add(userId);
        }
        filter(where, args, "p.content_type", query.contentType());
        filter(where, args, "s.parent_id", query.examId());
        filter(where, args, "p.subject_id", query.subjectId());
        filter(where, args, "p.school_id", query.schoolId());
        filter(where, args, "p.course_id", query.courseId());
        filter(where, args, "p.status", query.status());
        if (query.keyword() != null && !query.keyword().isBlank()) {
            String term =
                    "%"
                            + query.keyword()
                                    .trim()
                                    .replace("!", "!!")
                                    .replace("%", "!%")
                                    .replace("_", "!_")
                            + "%";
            where.append(
                    " AND (p.title LIKE ? ESCAPE '!' OR p.body LIKE ? ESCAPE '!' OR p.concept_name"
                        + " LIKE ? ESCAPE '!')");
            args.addAll(List.of(term, term, term));
        }
        long total =
                jdbc.queryForObject("SELECT COUNT(*) " + FROM + where, Long.class, args.toArray());
        List<Object> pageArgs = new ArrayList<>();
        pageArgs.add(userId);
        pageArgs.addAll(args);
        pageArgs.add(query.pageSize());
        pageArgs.add((query.pageNum() - 1L) * query.pageSize());
        List<Post> records =
                jdbc.query(
                        SELECT + FROM + where + " ORDER BY p.id DESC LIMIT ? OFFSET ?",
                        POST,
                        pageArgs.toArray());
        return new Page<>(records, total, query.pageNum(), query.pageSize());
    }

    public void deletePost(long id) {
        jdbc.update("UPDATE community_post SET deleted=1 WHERE id=?", id);
        jdbc.update("DELETE FROM community_attachment WHERE post_id=?", id);
    }

    public void review(long id, long actor, String decision, String note) {
        jdbc.update(
                "UPDATE community_post SET status=?,review_note=?,reviewed_by=?,reviewed_at=NOW()"
                    + " WHERE id=?",
                decision,
                note,
                actor,
                id);
        jdbc.update(
                "INSERT INTO community_review (post_id,reviewer_id,decision,note) VALUES (?,?,?,?)",
                id,
                actor,
                decision,
                note);
    }

    public List<ReviewEvent> reviews(long postId) {
        return jdbc.query(
                """
                SELECT r.*,COALESCE(NULLIF(u.nickname,''),u.username) author_name
                FROM community_review r JOIN user u ON u.id=r.reviewer_id WHERE post_id=? ORDER BY r.id
                """,
                (r, n) ->
                        new ReviewEvent(
                                r.getLong("id"),
                                r.getString("author_name"),
                                r.getString("decision"),
                                r.getString("note"),
                                r.getTimestamp("created_at").toLocalDateTime()),
                postId);
    }

    public long insertComment(long postId, long actor, Long parentId, String body) {
        return insert(
                "INSERT INTO community_comment (post_id,user_id,parent_id,body) VALUES (?,?,?,?)",
                postId,
                actor,
                parentId,
                body.trim());
    }

    public List<Comment> comments(long postId, long actor, int page, int size) {
        return jdbc.query(
                """
                SELECT r.*,COALESCE(NULLIF(u.nickname,''),u.username) author_name,
                COALESCE(NULLIF(pu.nickname,''),pu.username) reply_to_name,
                (SELECT COUNT(*) FROM community_like l WHERE l.post_id=r.post_id AND l.comment_id=r.id) like_count,
                EXISTS(SELECT 1 FROM community_like l
                    WHERE l.post_id=r.post_id AND l.comment_id=r.id AND l.user_id=?) liked
                FROM community_comment r JOIN user u ON u.id=r.user_id
                LEFT JOIN community_comment parent ON parent.id=r.parent_id LEFT JOIN user pu ON pu.id=parent.user_id
                WHERE r.post_id=? ORDER BY r.id LIMIT ? OFFSET ?
                """,
                (r, n) ->
                        new Comment(
                                r.getLong("id"),
                                r.getLong("post_id"),
                                r.getLong("user_id"),
                                r.getString("author_name"),
                                r.getObject("parent_id", Long.class),
                                r.getString("reply_to_name"),
                                r.getBoolean("deleted") ? "" : r.getString("body"),
                                r.getBoolean("deleted"),
                                r.getTimestamp("created_at").toLocalDateTime(),
                                r.getLong("like_count"),
                                r.getBoolean("liked")),
                actor,
                postId,
                size,
                (page - 1L) * size);
    }

    public long commentCount(long postId) {
        return jdbc.queryForObject(
                "SELECT COUNT(*) FROM community_comment WHERE post_id=?", Long.class, postId);
    }

    public long[] commentIdentity(long id) {
        return jdbc
                .query(
                        "SELECT post_id,user_id,deleted FROM community_comment WHERE id=?",
                        (r, n) -> new long[] {r.getLong(1), r.getLong(2), r.getLong(3)},
                        id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public void deleteComment(long id) {
        jdbc.update("UPDATE community_comment SET deleted=1,body='' WHERE id=?", id);
    }

    public void like(long postId, long commentId, long actor, boolean liked) {
        if (liked) {
            jdbc.update(
                    "INSERT INTO community_like (post_id,comment_id,user_id) VALUES (?,?,?) ON"
                        + " DUPLICATE KEY UPDATE user_id=user_id",
                    postId,
                    commentId,
                    actor);
        } else {
            jdbc.update(
                    "DELETE FROM community_like WHERE post_id=? AND comment_id=? AND user_id=?",
                    postId,
                    commentId,
                    actor);
        }
    }

    public long storedBytes(long userId) {
        return jdbc.queryForObject(
                "SELECT COALESCE(SUM(a.size_bytes),0) FROM community_attachment a JOIN"
                    + " community_post p ON p.id=a.post_id WHERE p.user_id=?",
                Long.class,
                userId);
    }

    public void attach(long postId, String name, byte[] content) {
        jdbc.update(
                "INSERT INTO community_attachment (post_id,name,size_bytes,content) VALUES"
                    + " (?,?,?,?)",
                postId,
                name,
                content.length,
                content);
    }

    public List<Attachment> attachments(long postId) {
        return jdbc.query(
                "SELECT id,name,size_bytes FROM community_attachment WHERE post_id=? ORDER BY id",
                (r, n) -> new Attachment(r.getLong(1), r.getString(2), r.getLong(3)),
                postId);
    }

    public Long attachmentPost(long id) {
        return jdbc
                .query(
                        "SELECT post_id FROM community_attachment WHERE id=?",
                        (r, n) -> r.getLong(1),
                        id)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public byte[] attachmentBytes(long id) {
        return jdbc.queryForObject(
                "SELECT content FROM community_attachment WHERE id=?", byte[].class, id);
    }

    public List<QuestionLink> questionLinks(long postId) {
        return jdbc.query(
                """
                SELECT s.id,s.status,s.imported_question_id FROM community_question_link l
                JOIN question_submission s ON s.id=l.submission_id WHERE l.post_id=? ORDER BY s.id
                """,
                (r, n) -> new QuestionLink(r.getLong(1), r.getInt(2), r.getObject(3, Long.class)),
                postId);
    }

    public Long linkedQuestion(long postId, String requestKey) {
        return jdbc
                .query(
                        "SELECT submission_id FROM community_question_link WHERE post_id=? AND"
                            + " request_key=?",
                        (r, n) -> r.getLong(1),
                        postId,
                        requestKey)
                .stream()
                .findFirst()
                .orElse(null);
    }

    public void linkQuestion(long postId, long submissionId, String requestKey) {
        jdbc.update(
                "INSERT INTO community_question_link (post_id,submission_id,request_key) VALUES"
                    + " (?,?,?)",
                postId,
                submissionId,
                requestKey);
    }

    private long insert(String sql, Object... args) {
        var key = new GeneratedKeyHolder();
        jdbc.update(
                connection -> {
                    var statement =
                            connection.prepareStatement(sql, Statement.RETURN_GENERATED_KEYS);
                    for (int i = 0; i < args.length; i++) {
                        statement.setObject(i + 1, args[i]);
                    }
                    return statement;
                },
                key);
        return Objects.requireNonNull(key.getKey()).longValue();
    }

    private static void filter(
            StringBuilder where, List<Object> args, String column, Object value) {
        if (value != null && !value.toString().isBlank()) {
            where.append(" AND ").append(column).append("=?");
            args.add(value);
        }
    }
}
