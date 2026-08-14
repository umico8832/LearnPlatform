package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.PrivateExamSourceStorageItemVO;
import com.learnplatform.dto.PrivateExamStorageUsageVO;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
public class PrivateExamSourceStorageService {
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;

    private final UserExamSourceMapper sourceMapper;
    private final long limitBytes;

    public PrivateExamSourceStorageService(
            UserExamSourceMapper sourceMapper,
            @Value("${private-exam.source-storage-limit-bytes:104857600}") long limitBytes) {
        if (limitBytes < 1) {
            throw new IllegalArgumentException("private exam source storage limit must be positive");
        }
        this.sourceMapper = sourceMapper;
        this.limitBytes = limitBytes;
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public void attachFileWithinQuota(UserExamSource source, Long ownerUserId,
                                      byte[] sourceFile, String sourceMediaType) {
        if (sourceFile == null) return;
        if (sourceFile.length == 0 || sourceFile.length > MAX_FILE_SIZE
                || !expectedMediaType(source.getSourceFormat()).equals(sourceMediaType)) {
            throw validation("原始文件无效");
        }
        if (sourceMapper.lockOwner(ownerUserId) == null) {
            throw new BusinessException(ResultCode.NOT_FOUND, "用户不存在");
        }
        long usedBytes = sourceMapper.sumOwnedFileSize(ownerUserId);
        if (usedBytes > limitBytes - sourceFile.length) {
            throw new BusinessException(ResultCode.QUOTA_EXCEEDED, "私有试卷原文件存储空间不足");
        }
        source.setSourceMediaType(sourceMediaType);
        source.setSourceSize((long) sourceFile.length);
        source.setSourceFile(sourceFile);
    }

    public PrivateExamStorageUsageVO getUsage(Long ownerUserId) {
        long usedBytes = sourceMapper.sumOwnedFileSize(ownerUserId);
        PrivateExamStorageUsageVO vo = new PrivateExamStorageUsageVO();
        vo.setUsedBytes(usedBytes);
        vo.setLimitBytes(limitBytes);
        vo.setRemainingBytes(Math.max(0, limitBytes - usedBytes));
        vo.setFileCount(sourceMapper.countOwnedFiles(ownerUserId));
        return vo;
    }

    public Page<PrivateExamSourceStorageItemVO> listFiles(Long ownerUserId, int pageNum, int pageSize) {
        int safePageNum = Math.max(1, pageNum);
        int safePageSize = Math.min(50, Math.max(1, pageSize));
        return sourceMapper.selectOwnedStoredFiles(new Page<>(safePageNum, safePageSize), ownerUserId);
    }

    private String expectedMediaType(String sourceFormat) {
        if ("PDF".equals(sourceFormat)) return "application/pdf";
        if ("DOCX".equals(sourceFormat)) {
            return "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
        }
        return "";
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }
}
