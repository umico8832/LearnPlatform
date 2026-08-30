package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.exam.PrivateExamSourceFile;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.stereotype.Service;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Set;

@Service
public class PrivateExamSourceFileService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final Set<String> MEDIA_TYPES = Set.of(
            "application/pdf",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document");

    private final ExamPaperMapper paperMapper;
    private final PrivateExamImportDraftMapper draftMapper;
    private final UserExamSourceMapper sourceMapper;

    public PrivateExamSourceFileService(ExamPaperMapper paperMapper,
                                        PrivateExamImportDraftMapper draftMapper,
                                        UserExamSourceMapper sourceMapper) {
        this.paperMapper = paperMapper;
        this.draftMapper = draftMapper;
        this.sourceMapper = sourceMapper;
    }

    public PrivateExamSourceFile getForPaper(Long paperId, Long userId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (paper == null || !userId.equals(paper.getOwnerUserId())
                || !"PRIVATE".equals(paper.getVisibility())
                || !"USER_PRIVATE".equals(paper.getPaperType())
                || Integer.valueOf(1).equals(paper.getDeleted())
                || paper.getSourceRecordId() == null) {
            throw notFound("私有试卷不存在");
        }
        return verifiedFile(paper.getSourceRecordId(), userId);
    }

    public PrivateExamSourceFile getForDraft(Long draftId, Long userId) {
        PrivateExamImportDraft draft = draftMapper.selectById(draftId);
        if (draft == null || !userId.equals(draft.getOwnerUserId()) || draft.getSourceRecordId() == null) {
            throw notFound("私有试卷草稿不存在");
        }
        return verifiedFile(draft.getSourceRecordId(), userId);
    }

    private PrivateExamSourceFile verifiedFile(Long sourceId, Long userId) {
        UserExamSource source = sourceMapper.selectOwnedWithFile(sourceId, userId);
        if (source == null || source.getSourceFile() == null || source.getSourceFile().length == 0
                || source.getSourceSize() == null || source.getSourceMediaType() == null) {
            throw notFound("原始文件不可用");
        }
        byte[] content = source.getSourceFile();
        String expectedMediaType = expectedMediaType(source.getSourceFormat());
        if (source.getSourceSize().longValue() != content.length || content.length > MAX_FILE_SIZE
                || !MEDIA_TYPES.contains(source.getSourceMediaType())
                || !source.getSourceMediaType().equals(expectedMediaType)
                || !sha256(content).equalsIgnoreCase(source.getContentSha256())) {
            throw new BusinessException(ResultCode.BUSINESS_ERROR, "原始文件校验失败");
        }
        return new PrivateExamSourceFile(source.getSourceName(), source.getSourceMediaType(), content);
    }

    private String expectedMediaType(String sourceFormat) {
        if ("PDF".equals(sourceFormat)) { return "application/pdf"; }
        if ("DOCX".equals(sourceFormat)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return null;
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException notFound(String message) {
        return new BusinessException(ResultCode.NOT_FOUND, message);
    }
}
