package com.learnplatform.service.tutor;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.TutorSessionNotePageVO;
import com.learnplatform.dto.TutorSessionNoteRow;
import com.learnplatform.dto.TutorSessionNoteSourceVO;
import com.learnplatform.dto.TutorSessionNoteUpdateRequest;
import com.learnplatform.dto.TutorSessionNoteVO;
import com.learnplatform.mapper.TutorMemoryMapper;
import com.learnplatform.mapper.TutorSessionNoteMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
public class TutorSessionNoteService {
    private final TutorMemoryMapper membership;
    private final TutorSessionNoteMapper notes;

    public TutorSessionNoteService(TutorMemoryMapper membership, TutorSessionNoteMapper notes) {
        this.membership = membership;
        this.notes = notes;
    }

    public TutorSessionNoteVO get(Long userId, Long courseId, String sessionKey) {
        requireMember(membership.member(userId, courseId));
        return view(required(notes.find(userId, courseId, sessionKey)));
    }

    public TutorSessionNotePageVO list(Long userId, Long courseId, int page) {
        if (page < 1) { throw invalid(); }
        requireMember(membership.member(userId, courseId));
        return new TutorSessionNotePageVO(notes.list(userId, courseId, (page - 1L) * 5).stream()
                .map(this::view).toList(), notes.count(userId, courseId), page, 5);
    }

    public List<TutorSessionNoteVO> forPrompt(Long userId, Long courseId) {
        requireMember(membership.member(userId, courseId));
        return notes.recentAvailable(userId, courseId).stream().map(this::view).toList();
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorSessionNoteVO save(Long userId, Long courseId, String sessionKey,
                                   TutorSessionNoteUpdateRequest request) {
        if (request == null || request.note() == null || request.note().isBlank() || request.note().length() > 500) {
            throw invalid();
        }
        return replace(userId, courseId, sessionKey, request.revision(), request.note().strip());
    }

    @Transactional(isolation = Isolation.READ_COMMITTED)
    public TutorSessionNoteVO delete(Long userId, Long courseId, String sessionKey, Long revision) {
        return replace(userId, courseId, sessionKey, revision, null);
    }

    private TutorSessionNoteVO replace(Long userId, Long courseId, String sessionKey, Long revision, String note) {
        if (revision == null || revision < 0 || revision == Long.MAX_VALUE) { throw invalid(); }
        requireMember(membership.lockMember(userId, courseId));
        TutorSessionNoteRow current = required(notes.lock(userId, courseId, sessionKey));
        if (current.revision() != revision) { throw conflict(); }
        if (note != null && !current.available()) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原课节已不可用，仍可删除已有复盘");
        }
        if (current.revision() == 0) {
            if (note == null) { return view(current); }
            notes.insert(current.sessionId(), note);
        } else if (notes.update(current.sessionId(), revision, note) != 1) {
            throw conflict();
        }
        return view(required(notes.find(userId, courseId, sessionKey)));
    }

    private TutorSessionNoteVO view(TutorSessionNoteRow row) {
        boolean available = row.available();
        String status = row.checkCorrect() == null ? "UNANSWERED" : row.checkCorrect() ? "CORRECT" : "INCORRECT";
        var source = new TutorSessionNoteSourceVO(available, available ? row.knowledgePointId() : null,
                available ? row.title() : null, available ? row.sessionStartedAt() : null, available ? status : null,
                available ? row.checkAnsweredAt() : null);
        return new TutorSessionNoteVO(row.sessionKey(), row.revision(), row.note(), row.updatedAt(), source);
    }

    private TutorSessionNoteRow required(TutorSessionNoteRow row) {
        if (row == null) { throw new BusinessException(ResultCode.NOT_FOUND, "Tutor 会话不存在"); }
        return row;
    }

    private void requireMember(Long memberId) {
        if (memberId == null) { throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在或尚未加入课程库"); }
    }

    private BusinessException invalid() {
        return new BusinessException(ResultCode.VALIDATION_ERROR, "请填写有效的复盘内容与版本");
    }

    private BusinessException conflict() {
        return new BusinessException(ResultCode.BUSINESS_ERROR, "复盘已在其他请求中更新，请重新读取后再修改");
    }
}
