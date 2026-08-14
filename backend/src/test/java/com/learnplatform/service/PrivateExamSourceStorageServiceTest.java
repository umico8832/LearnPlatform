package com.learnplatform.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.PrivateExamSourceStorageItemVO;
import com.learnplatform.dto.PrivateExamStorageUsageVO;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.inOrder;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamSourceStorageServiceTest {
    @Mock private UserExamSourceMapper sourceMapper;
    private PrivateExamSourceStorageService service;

    @BeforeEach
    void setUp() {
        service = new PrivateExamSourceStorageService(sourceMapper, 100L);
    }

    @Test
    void locksOwnerBeforeCheckingUsageAndAttachesFileWithinQuota() {
        UserExamSource source = new UserExamSource();
        source.setSourceFormat("PDF");
        byte[] bytes = "%PDF-file".getBytes();
        when(sourceMapper.lockOwner(7L)).thenReturn(7L);
        when(sourceMapper.sumOwnedFileSize(7L)).thenReturn(90L);

        service.attachFileWithinQuota(source, 7L, bytes, "application/pdf");

        assertEquals((long) bytes.length, source.getSourceSize());
        assertEquals("application/pdf", source.getSourceMediaType());
        InOrder order = inOrder(sourceMapper);
        order.verify(sourceMapper).lockOwner(7L);
        order.verify(sourceMapper).sumOwnedFileSize(7L);
    }

    @Test
    void rejectsFileWhenOwnerAggregateQuotaWouldBeExceeded() {
        UserExamSource source = new UserExamSource();
        source.setSourceFormat("PDF");
        when(sourceMapper.lockOwner(7L)).thenReturn(7L);
        when(sourceMapper.sumOwnedFileSize(7L)).thenReturn(99L);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.attachFileWithinQuota(source, 7L, "too-large".getBytes(), "application/pdf"));

        assertEquals("私有试卷原文件存储空间不足", exception.getMessage());
        assertEquals(null, source.getSourceFile());
    }

    @Test
    void rejectsMissingOwnerAndIgnoresTextOnlySources() {
        UserExamSource source = new UserExamSource();
        source.setSourceFormat("PDF");
        service.attachFileWithinQuota(source, 7L, null, null);
        verifyNoInteractions(sourceMapper);

        when(sourceMapper.lockOwner(8L)).thenReturn(null);
        BusinessException exception = assertThrows(BusinessException.class,
                () -> service.attachFileWithinQuota(source, 8L, "file".getBytes(), "application/pdf"));
        assertEquals("用户不存在", exception.getMessage());
    }

    @Test
    void returnsOwnerStorageUsageWithoutLoadingBinaryColumns() {
        when(sourceMapper.sumOwnedFileSize(7L)).thenReturn(30L);
        when(sourceMapper.countOwnedFiles(7L)).thenReturn(2L);

        PrivateExamStorageUsageVO usage = service.getUsage(7L);

        assertEquals(30L, usage.getUsedBytes());
        assertEquals(100L, usage.getLimitBytes());
        assertEquals(70L, usage.getRemainingBytes());
        assertEquals(2L, usage.getFileCount());
    }

    @Test
    void listsOnlyOwnerStoredFilesWithBusinessAssociationMetadata() {
        PrivateExamSourceStorageItemVO item = new PrivateExamSourceStorageItemVO();
        item.setId(11L);
        item.setSourceName("paper.pdf");
        item.setAssociationType("DRAFT");
        item.setAssociationId(31L);
        Page<PrivateExamSourceStorageItemVO> page = new Page<>(1, 10, 1);
        page.setRecords(java.util.List.of(item));
        when(sourceMapper.selectOwnedStoredFiles(any(Page.class), eq(7L))).thenReturn(page);

        Page<PrivateExamSourceStorageItemVO> result = service.listFiles(7L, 1, 10);

        assertEquals(1L, result.getTotal());
        assertEquals("paper.pdf", result.getRecords().get(0).getSourceName());
        assertEquals("DRAFT", result.getRecords().get(0).getAssociationType());
    }
}
