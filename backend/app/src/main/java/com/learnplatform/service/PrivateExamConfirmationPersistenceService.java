package com.learnplatform.service;

import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

/** File and text parsing finish before this persistence transaction takes parent locks. */
@Service
public class PrivateExamConfirmationPersistenceService {
    private final ContentReferenceLockService locks;
    private final UserExamSourceMapper sources;
    private final PrivateExamSourceStorageService storage;
    private final PrivateExamConfirmedPaperService papers;

    public PrivateExamConfirmationPersistenceService(ContentReferenceLockService locks, UserExamSourceMapper sources,
            PrivateExamSourceStorageService storage, PrivateExamConfirmedPaperService papers) {
        this.locks = locks;
        this.sources = sources;
        this.storage = storage;
        this.papers = papers;
    }

    @Transactional
    public ExamPaperVO persist(PrivateExamImportConfirmRequest request, Long userId, String hash,
                               byte[] file, String mediaType, PrivateExamImportPreviewVO preview) {
        locks.lockForReference(List.of(preview.getCourseId()), List.of());
        var source = new UserExamSource();
        source.setOwnerUserId(userId);
        source.setSourceName(request.getSourceName().trim());
        source.setSourceFormat(request.getSourceFormat());
        source.setContentSha256(hash);
        source.setOriginalContent(request.getContent());
        storage.attachFileWithinQuota(source, userId, file, mediaType);
        sources.insert(source);
        return papers.create(preview.getTitle(), preview.getCourseId(), preview.getDuration(), source,
                preview.getQuestions(), userId);
    }
}
