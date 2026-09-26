package com.learnplatform.service.tutor;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorMemoryUpdateRequest;
import com.learnplatform.dto.TutorMemoryVO;
import com.learnplatform.mapper.TutorMemoryMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

/** Explicit user memory is stored separately from conversation and learning evidence. */
@Service
public class TutorMemoryService {
    private static final Set<String> STYLES = Set.of("STEP_BY_STEP", "CONCISE", "EXAMPLES");
    private final TutorMemoryMapper memories;
    private final ObjectMapper json;

    public TutorMemoryService(TutorMemoryMapper memories, ObjectMapper json) {
        this.memories = memories;
        this.json = json;
    }

    public TutorMemoryVO get(Long userId, Long courseId) {
        requireMember(memories.member(userId, courseId));
        return current(userId, courseId);
    }

    public String promptContext(Long userId, Long courseId) {
        try {
            return json.writeValueAsString(get(userId, courseId));
        } catch (JsonProcessingException exception) {
            throw new IllegalStateException("Cannot serialize Tutor memory", exception);
        }
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorMemoryVO save(Long userId, Long courseId, TutorMemoryUpdateRequest request) {
        if (request == null) { throw invalid(); }
        String goal = request.goal() == null ? null : request.goal().strip();
        if (goal != null && goal.isEmpty()) { goal = null; }
        String style = request.explanationStyle();
        if ((style != null && !STYLES.contains(style)) || (request.goal() != null && request.goal().length() > 500)
                || (style == null && goal == null)) {
            throw invalid();
        }
        return replace(userId, courseId, request.revision(), style, goal);
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorMemoryVO delete(Long userId, Long courseId, Long revision) {
        return replace(userId, courseId, revision, null, null);
    }

    private TutorMemoryVO replace(Long userId, Long courseId, Long revision, String style, String goal) {
        if (revision == null || revision < 0 || revision == Long.MAX_VALUE) { throw invalid(); }
        requireMember(memories.lockMember(userId, courseId));
        TutorMemoryVO previous = current(userId, courseId);
        if (previous.revision() != revision) { throw conflict(); }
        if (previous.revision() == 0) {
            if (style == null && goal == null) { return previous; }
            memories.insert(userId, courseId, style, goal);
        } else if (memories.update(userId, courseId, revision, style, goal) != 1) {
            throw conflict();
        }
        // Keep only a revision after deletion so a stale tab cannot recreate erased values.
        return current(userId, courseId);
    }

    private TutorMemoryVO current(Long userId, Long courseId) {
        TutorMemoryVO stored = memories.find(userId, courseId);
        return stored == null ? new TutorMemoryVO(0, null, null) : stored;
    }

    private void requireMember(Long membership) {
        if (membership == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在或尚未加入课程库");
        }
    }

    private BusinessException invalid() {
        return new BusinessException(ResultCode.VALIDATION_ERROR, "请填写有效的学习目标或讲解偏好");
    }

    private BusinessException conflict() {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "记忆已在其他请求中更新，请重新读取后再修改");
    }
}
