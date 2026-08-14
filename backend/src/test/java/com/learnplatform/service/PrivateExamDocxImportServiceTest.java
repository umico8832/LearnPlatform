package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDocxConfirmRequest;
import com.learnplatform.dto.PrivateExamDocxDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDocxRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import org.apache.poi.xwpf.usermodel.Document;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFRun;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.security.MessageDigest;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PrivateExamDocxImportServiceTest {
    private static final byte[] ONE_PIXEL_PNG = Base64.getDecoder().decode(
            "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+A8AAQUBAScY42YAAAAASUVORK5CYII=");

    @Mock private PrivateExamImportService importService;
    @Mock private PrivateExamDraftService draftService;
    private PrivateExamDocxImportService service;

    @BeforeEach
    void setUp() {
        service = new PrivateExamDocxImportService(importService, draftService);
    }

    @Test
    void extractsParagraphsAndTablesAndUsesRawFileHashForPreview() throws Exception {
        byte[] bytes = docx(false);
        MockMultipartFile file = new MockMultipartFile(
                "file", "folder/paper.docx", "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
                bytes);
        PrivateExamImportPreviewVO expected = new PrivateExamImportPreviewVO();
        when(importService.previewWithSourceHash(any(PrivateExamImportRequest.class), anyString())).thenReturn(expected);

        assertEquals(expected, service.preview(metadata(), file));

        ArgumentCaptor<PrivateExamImportRequest> request = ArgumentCaptor.forClass(PrivateExamImportRequest.class);
        verify(importService).previewWithSourceHash(request.capture(), org.mockito.ArgumentMatchers.eq(sha256(bytes)));
        assertEquals("DOCX", request.getValue().getSourceFormat());
        assertEquals("paper.docx", request.getValue().getSourceName());
        assertEquals(true, request.getValue().getContent().contains("Question: Which access order?"));
        assertEquals(true, request.getValue().getContent().contains("A. First in, first out"));
    }

    @Test
    void rejectsEmptyDocx() throws Exception {
        MockMultipartFile file = file("empty.docx", emptyDocx());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("DOCX 未提取到普通段落或表格文本", exception.getMessage());
    }

    @Test
    void rejectsDocxContainingImagesOrComplexObjects() throws Exception {
        MockMultipartFile file = file("picture.docx", docx(true));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("DOCX 暂不支持图片、公式、文本框或复杂排版", exception.getMessage());
    }

    @Test
    void rejectsInvalidDocxPayload() {
        MockMultipartFile file = file("paper.docx", "PK-not-a-docx".getBytes());

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("文件不是有效的 DOCX", exception.getMessage());
    }

    @Test
    void rejectsDocxLargerThanTenMegabytes() {
        MockMultipartFile file = file("paper.docx", new byte[10 * 1024 * 1024 + 1]);

        BusinessException exception = assertThrows(BusinessException.class, () -> service.preview(metadata(), file));

        assertEquals("DOCX 文件不能超过10MB", exception.getMessage());
    }

    @Test
    void reextractsSameFileAndForwardsRawHashWhenConfirming() throws Exception {
        byte[] bytes = docx(false);
        MockMultipartFile file = file("paper.docx", bytes);
        PrivateExamDocxConfirmRequest metadata = new PrivateExamDocxConfirmRequest();
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
        byte[] bytes = docx(false);
        MockMultipartFile file = file("paper.docx", bytes);
        PrivateExamDocxDraftCreateRequest metadata = new PrivateExamDocxDraftCreateRequest();
        copyMetadata(metadata);
        metadata.setExpectedContentHash(sha256(bytes));
        PrivateExamDraftVO expected = new PrivateExamDraftVO();
        when(draftService.createWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L), anyString()))
                .thenReturn(expected);

        assertEquals(expected, service.createDraft(metadata, file, 7L));

        verify(draftService).createWithSourceHash(any(), org.mockito.ArgumentMatchers.eq(7L),
                org.mockito.ArgumentMatchers.eq(sha256(bytes)));
    }

    private byte[] docx(boolean withPicture) throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            paragraph(document, "Type: SINGLE_CHOICE");
            paragraph(document, "Question: Which access order?");
            var table = document.createTable(3, 1);
            table.getRow(0).getCell(0).setText("Options:");
            table.getRow(1).getCell(0).setText("A. First in, first out");
            table.getRow(2).getCell(0).setText("B. Last in, first out");
            paragraph(document, "Answer: A");
            paragraph(document, "Analysis: FIFO");
            paragraph(document, "Score: 2");
            if (withPicture) {
                XWPFRun run = document.createParagraph().createRun();
                run.addPicture(new ByteArrayInputStream(ONE_PIXEL_PNG), Document.PICTURE_TYPE_PNG,
                        "pixel.png", 1, 1);
            }
            document.write(output);
            return output.toByteArray();
        }
    }

    private byte[] emptyDocx() throws Exception {
        try (XWPFDocument document = new XWPFDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            document.write(output);
            return output.toByteArray();
        }
    }

    private void paragraph(XWPFDocument document, String text) {
        XWPFParagraph paragraph = document.createParagraph();
        paragraph.createRun().setText(text);
    }

    private MockMultipartFile file(String name, byte[] bytes) {
        return new MockMultipartFile("file", name,
                "application/vnd.openxmlformats-officedocument.wordprocessingml.document", bytes);
    }

    private PrivateExamDocxRequest metadata() {
        PrivateExamDocxRequest request = new PrivateExamDocxRequest();
        copyMetadata(request);
        return request;
    }

    private void copyMetadata(PrivateExamDocxRequest request) {
        request.setTitle("DOCX 私有试卷");
        request.setCourseId(3L);
        request.setDuration(60);
    }

    private String sha256(byte[] bytes) throws Exception {
        return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
    }
}
