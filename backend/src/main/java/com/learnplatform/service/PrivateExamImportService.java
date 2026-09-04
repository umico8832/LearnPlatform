package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamSourceVO;
import com.learnplatform.entity.Course;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.CourseMapper;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.List;

@Service
public class PrivateExamImportService {
    private final CourseMapper courseMapper;
    private final UserExamSourceMapper sourceMapper;
    private final PrivateExamSourceStorageService sourceStorageService;
    private final ExamPaperMapper paperMapper;
    private final PrivateExamImportParserService parserService;
    private final PrivateExamConfirmedPaperService confirmedPaperService;

    public PrivateExamImportService(
            CourseMapper courseMapper,
            UserExamSourceMapper sourceMapper,
            PrivateExamSourceStorageService sourceStorageService,
            ExamPaperMapper paperMapper,
            PrivateExamImportParserService parserService,
            PrivateExamConfirmedPaperService confirmedPaperService) {
        this.courseMapper = courseMapper;
        this.sourceMapper = sourceMapper;
        this.sourceStorageService = sourceStorageService;
        this.paperMapper = paperMapper;
        this.parserService = parserService;
        this.confirmedPaperService = confirmedPaperService;
    }

    public PrivateExamImportPreviewVO preview(PrivateExamImportRequest request) {
        return previewWithSourceHash(request, sha256(request.getContent()));
    }

    public PrivateExamImportPreviewVO previewWithSourceHash(
            PrivateExamImportRequest request, String sourceHash) {
        ensureCourseExists(request.getCourseId());
        return parserService.parse(request, sourceHash, false);
    }

    @Transactional
    public ExamPaperVO confirm(PrivateExamImportConfirmRequest request, Long userId) {
        return confirmWithSourceHash(request, userId, sha256(request.getContent()));
    }

    @Transactional
    public ExamPaperVO confirmWithSourceHash(
            PrivateExamImportConfirmRequest request, Long userId, String sourceHash) {
        return confirmWithSourceFile(request, userId, sourceHash, null, null);
    }

    @Transactional
    public ExamPaperVO confirmWithSourceFile(
            PrivateExamImportConfirmRequest request, Long userId, String sourceHash,
            byte[] sourceFile, String sourceMediaType) {
        ensureCourseExists(request.getCourseId());
        if (!sourceHash.equalsIgnoreCase(request.getExpectedContentHash())) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "原始资料已变化，请重新预览确认");
        }
        PrivateExamImportPreviewVO preview = parserService.parse(request, sourceHash, true);
        UserExamSource source = saveSource(request, userId, sourceHash, sourceFile, sourceMediaType);
        return createConfirmedPaper(preview.getTitle(), preview.getCourseId(), preview.getDuration(),
                source, preview.getQuestions(), userId);
    }

    public PrivateExamSourceVO getSource(Long paperId, Long userId) {
        ExamPaper paper = paperMapper.selectById(paperId);
        if (!isOwnedPrivatePaper(paper, userId) || paper.getSourceRecordId() == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "私有试卷不存在");
        }
        UserExamSource source = sourceMapper.selectById(paper.getSourceRecordId());
        if (source == null || !userId.equals(source.getOwnerUserId())) {
            throw new BusinessException(ResultCode.NOT_FOUND, "原始资料不存在");
        }
        PrivateExamSourceVO view = new PrivateExamSourceVO();
        view.setPaperId(paperId);
        view.setSourceName(source.getSourceName());
        view.setSourceFormat(source.getSourceFormat());
        view.setContentHash(source.getContentSha256());
        view.setOriginalContent(source.getOriginalContent());
        view.setOriginalFileAvailable(source.getSourceSize() != null && source.getSourceSize() > 0);
        view.setCreateTime(source.getCreateTime());
        return view;
    }

    ExamPaperVO createConfirmedPaper(String title, Long courseId, Integer duration,
                                     UserExamSource source,
                                     List<PrivateExamImportPreviewVO.QuestionPreview> questions,
                                     Long userId) {
        return confirmedPaperService.create(title, courseId, duration, source, questions, userId);
    }

    ExamPaperVO getConfirmedPaper(Long paperId, Long userId) {
        return confirmedPaperService.get(paperId, userId);
    }

    private UserExamSource saveSource(
            PrivateExamImportConfirmRequest request, Long userId, String sourceHash,
            byte[] sourceFile, String sourceMediaType) {
        UserExamSource source = new UserExamSource();
        source.setOwnerUserId(userId);
        source.setSourceName(request.getSourceName().trim());
        source.setSourceFormat(request.getSourceFormat());
        source.setContentSha256(sourceHash);
        source.setOriginalContent(request.getContent());
        sourceStorageService.attachFileWithinQuota(source, userId, sourceFile, sourceMediaType);
        sourceMapper.insert(source);
        return source;
    }

    private void ensureCourseExists(Long courseId) {
        Course course = courseMapper.selectById(courseId);
        if (course == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "课程不存在");
        }
    }

    private String sha256(String content) {
        try {
            byte[] digest = MessageDigest.getInstance("SHA-256")
                    .digest(content.getBytes(StandardCharsets.UTF_8));
            return java.util.HexFormat.of().formatHex(digest);
        } catch (Exception exception) {
            throw new IllegalStateException("无法计算内容哈希", exception);
        }
    }

    private boolean isOwnedPrivatePaper(ExamPaper paper, Long userId) {
        return paper != null && "PRIVATE".equals(paper.getVisibility())
                && userId.equals(paper.getOwnerUserId());
    }
}
