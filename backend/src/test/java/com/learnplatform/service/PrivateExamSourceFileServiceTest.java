package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.PrivateExamImportDraft;
import com.learnplatform.entity.UserExamSource;
import com.learnplatform.mapper.ExamPaperMapper;
import com.learnplatform.mapper.PrivateExamImportDraftMapper;
import com.learnplatform.mapper.UserExamSourceMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamSourceFileServiceTest {
    @Mock private ExamPaperMapper paperMapper;
    @Mock private PrivateExamImportDraftMapper draftMapper;
    @Mock private UserExamSourceMapper sourceMapper;
    private PrivateExamSourceFileService service;

    @BeforeEach
    void setUp() {
        service = new PrivateExamSourceFileService(paperMapper, draftMapper, sourceMapper);
    }

    @Test
    void returnsVerifiedOriginalFileForOwnedPrivatePaper() throws Exception {
        byte[] bytes = "%PDF-file".getBytes();
        ExamPaper paper = paper(7L, 31L);
        UserExamSource source = source(31L, 7L, "paper.pdf", "application/pdf", bytes);
        when(paperMapper.selectById(51L)).thenReturn(paper);
        when(sourceMapper.selectOwnedWithFile(31L, 7L)).thenReturn(source);

        PrivateExamSourceFile file = service.getForPaper(51L, 7L);

        assertEquals("paper.pdf", file.filename());
        assertEquals("application/pdf", file.mediaType());
        assertArrayEquals(bytes, file.content());
    }

    @Test
    void returnsVerifiedOriginalFileForOwnedDraft() throws Exception {
        byte[] bytes = "PK-docx".getBytes();
        PrivateExamImportDraft draft = new PrivateExamImportDraft();
        draft.setId(41L);
        draft.setOwnerUserId(7L);
        draft.setSourceRecordId(31L);
        when(draftMapper.selectById(41L)).thenReturn(draft);
        when(sourceMapper.selectOwnedWithFile(31L, 7L)).thenReturn(source(
                31L, 7L, "paper.docx",
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes));

        PrivateExamSourceFile file = service.getForDraft(41L, 7L);

        assertEquals("paper.docx", file.filename());
        assertArrayEquals(bytes, file.content());
    }

    @Test
    void hidesPaperAndDraftAcrossOwners() {
        when(paperMapper.selectById(51L)).thenReturn(paper(8L, 31L));
        PrivateExamImportDraft draft = new PrivateExamImportDraft();
        draft.setOwnerUserId(8L);
        draft.setSourceRecordId(31L);
        when(draftMapper.selectById(41L)).thenReturn(draft);

        assertEquals("私有试卷不存在",
                assertThrows(BusinessException.class, () -> service.getForPaper(51L, 7L)).getMessage());
        assertEquals("私有试卷草稿不存在",
                assertThrows(BusinessException.class, () -> service.getForDraft(41L, 7L)).getMessage());
    }

    @Test
    void rejectsMissingOrCorruptedOriginalFile() throws Exception {
        when(paperMapper.selectById(51L)).thenReturn(paper(7L, 31L));
        UserExamSource missing = new UserExamSource();
        missing.setId(31L);
        missing.setOwnerUserId(7L);
        when(sourceMapper.selectOwnedWithFile(31L, 7L)).thenReturn(missing);

        assertEquals("原始文件不可用",
                assertThrows(BusinessException.class, () -> service.getForPaper(51L, 7L)).getMessage());

        byte[] bytes = "%PDF-corrupt".getBytes();
        UserExamSource corrupted = source(31L, 7L, "paper.pdf", "application/pdf", bytes);
        corrupted.setContentSha256("0".repeat(64));
        when(sourceMapper.selectOwnedWithFile(31L, 7L)).thenReturn(corrupted);
        assertEquals("原始文件校验失败",
                assertThrows(BusinessException.class, () -> service.getForPaper(51L, 7L)).getMessage());
    }

    private ExamPaper paper(Long ownerId, Long sourceId) {
        ExamPaper paper = new ExamPaper();
        paper.setId(51L);
        paper.setOwnerUserId(ownerId);
        paper.setSourceRecordId(sourceId);
        paper.setVisibility("PRIVATE");
        paper.setPaperType("USER_PRIVATE");
        paper.setDeleted(0);
        return paper;
    }

    private UserExamSource source(Long id, Long ownerId, String filename, String mediaType, byte[] bytes)
            throws Exception {
        UserExamSource source = new UserExamSource();
        source.setId(id);
        source.setOwnerUserId(ownerId);
        source.setSourceName(filename);
        source.setSourceFormat(filename.endsWith(".pdf") ? "PDF" : "DOCX");
        source.setSourceMediaType(mediaType);
        source.setSourceSize((long) bytes.length);
        source.setSourceFile(bytes);
        source.setContentSha256(HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes)));
        return source;
    }
}
