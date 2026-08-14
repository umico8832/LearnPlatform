package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamPdfRequest;
import com.learnplatform.dto.PrivateExamPdfConfirmRequest;
import com.learnplatform.dto.PrivateExamPdfDraftCreateRequest;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDraftVO;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamPdfImportServiceTest {
    @Mock private PrivateExamImportService importService;
    @Mock private PrivateExamDraftService draftService;
    private PrivateExamPdfImportService service;

    @BeforeEach
    void setUp() {
        service = new PrivateExamPdfImportService(importService, draftService);
    }

    @Test
    void extractsExistingTextAndUsesRawFileHashForPreview() throws Exception {
        byte[] bytes = pdfWithText("Question content A B");
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", bytes);
        PrivateExamImportPreviewVO expected = new PrivateExamImportPreviewVO();
        when(importService.previewWithSourceHash(any(PrivateExamImportRequest.class), anyString())).thenReturn(expected);

        assertEquals(expected, service.preview(metadata(), file));

        ArgumentCaptor<PrivateExamImportRequest> request = ArgumentCaptor.forClass(PrivateExamImportRequest.class);
        verify(importService).previewWithSourceHash(request.capture(), anyString());
        assertEquals("PDF", request.getValue().getSourceFormat());
        assertEquals("paper.pdf", request.getValue().getSourceName());
        assertEquals("Question content A B", request.getValue().getContent().trim());
        verify(importService).previewWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(sha256(bytes)));
    }

    @Test
    void rejectsImageOnlyOrEmptyPdfWithoutOcr() throws Exception {
        MockMultipartFile file = new MockMultipartFile("file", "scan.pdf", "application/pdf", pdfWithText(null));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("PDF 未提取到文本；扫描件暂不支持 OCR", exception.getMessage());
    }

    @Test
    void rejectsPasswordProtectedPdfExplicitly() throws Exception {
        MockMultipartFile file = new MockMultipartFile(
                "file", "protected.pdf", "application/pdf", encryptedPdf("Question content A B"));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("加密 PDF 暂不支持", exception.getMessage());
    }

    @Test
    void rejectsNonPdfPayload() {
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", "not-pdf".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("文件不是有效的 PDF", exception.getMessage());
    }

    @Test
    void rejectsPdfLargerThanTenMegabytes() {
        byte[] oversized = new byte[10 * 1024 * 1024 + 1];
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", oversized);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("PDF 文件不能超过10MB", exception.getMessage());
    }

    @Test
    void reextractsSameFileAndForwardsRawHashWhenConfirming() throws Exception {
        byte[] bytes = pdfWithText("Question content A B");
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", bytes);
        PrivateExamPdfConfirmRequest metadata = new PrivateExamPdfConfirmRequest();
        copyMetadata(metadata);
        metadata.setExpectedContentHash(sha256(bytes));
        metadata.setConfirmed(true);
        ExamPaperVO expected = new ExamPaperVO();
        when(importService.confirmWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L), anyString()))
                .thenReturn(expected);

        assertEquals(expected, service.confirm(metadata, file, 7L));

        verify(importService).confirmWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(sha256(bytes)));
    }

    @Test
    void reextractsSameFileAndForwardsRawHashWhenCreatingDraft() throws Exception {
        byte[] bytes = pdfWithText("Question content A B");
        MockMultipartFile file = new MockMultipartFile("file", "paper.pdf", "application/pdf", bytes);
        PrivateExamPdfDraftCreateRequest metadata = new PrivateExamPdfDraftCreateRequest();
        copyMetadata(metadata);
        metadata.setExpectedContentHash(sha256(bytes));
        PrivateExamDraftVO expected = new PrivateExamDraftVO();
        when(draftService.createWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L), anyString()))
                .thenReturn(expected);

        assertEquals(expected, service.createDraft(metadata, file, 7L));

        verify(draftService).createWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(sha256(bytes)));
    }

    private PrivateExamPdfRequest metadata() {
        PrivateExamPdfRequest request = new PrivateExamPdfRequest();
        request.setTitle("PDF 私有试卷");
        request.setCourseId(3L);
        request.setDuration(60);
        return request;
    }

    private void copyMetadata(PrivateExamPdfRequest request) {
        request.setTitle("PDF 私有试卷");
        request.setCourseId(3L);
        request.setDuration(60);
    }

    private byte[] pdfWithText(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            if (text != null) {
                try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                    content.beginText();
                    content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                    content.newLineAtOffset(72, 720);
                    content.showText(text);
                    content.endText();
                }
            }
            document.save(output);
            return output.toByteArray();
        }
    }

    private byte[] encryptedPdf(String text) throws Exception {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            PDPage page = new PDPage();
            document.addPage(page);
            try (PDPageContentStream content = new PDPageContentStream(document, page)) {
                content.beginText();
                content.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                content.newLineAtOffset(72, 720);
                content.showText(text);
                content.endText();
            }
            document.protect(new StandardProtectionPolicy("owner-password", "user-password", new AccessPermission()));
            document.save(output);
            return output.toByteArray();
        }
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
