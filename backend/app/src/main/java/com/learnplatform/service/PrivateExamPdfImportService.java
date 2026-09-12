package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import com.learnplatform.dto.PrivateExamPdfConfirmRequest;
import com.learnplatform.dto.PrivateExamPdfDraftCreateRequest;
import com.learnplatform.dto.PrivateExamPdfRequest;
import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.encryption.InvalidPasswordException;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.HexFormat;
import java.util.Locale;

@Service
public class PrivateExamPdfImportService {
    private static final String MEDIA_TYPE = "application/pdf";
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_PAGES = 200;
    private static final int MAX_TEXT_LENGTH = 100000;

    private final PrivateExamImportService importService;
    private final PrivateExamDraftService draftService;

    public PrivateExamPdfImportService(PrivateExamImportService importService,
                                       PrivateExamDraftService draftService) {
        this.importService = importService;
        this.draftService = draftService;
    }

    public PrivateExamImportPreviewVO preview(PrivateExamPdfRequest metadata, MultipartFile file) {
        PreparedPdf pdf = prepare(metadata, file);
        return importService.previewWithSourceHash(pdf.request(), pdf.hash());
    }

    public ExamPaperVO confirm(PrivateExamPdfConfirmRequest metadata, MultipartFile file, Long userId) {
        PreparedPdf pdf = prepare(metadata, file);
        PrivateExamImportConfirmRequest request = new PrivateExamImportConfirmRequest();
        copy(pdf.request(), request);
        request.setExpectedContentHash(metadata.getExpectedContentHash());
        request.setConfirmed(metadata.isConfirmed());
        return importService.confirmWithSourceFile(request, userId, pdf.hash(), pdf.bytes(), MEDIA_TYPE);
    }

    public PrivateExamDraftVO createDraft(PrivateExamPdfDraftCreateRequest metadata,
                                          MultipartFile file, Long userId) {
        PreparedPdf pdf = prepare(metadata, file);
        PrivateExamDraftCreateRequest request = new PrivateExamDraftCreateRequest();
        copy(pdf.request(), request);
        request.setExpectedContentHash(metadata.getExpectedContentHash());
        return draftService.createWithSourceFile(request, userId, pdf.hash(), pdf.bytes(), MEDIA_TYPE);
    }

    private PreparedPdf prepare(PrivateExamPdfRequest metadata, MultipartFile file) {
        byte[] bytes = readFile(file);
        String filename = safeFilename(file.getOriginalFilename());
        String text = extractText(bytes);
        PrivateExamImportRequest request = new PrivateExamImportRequest();
        request.setTitle(metadata.getTitle());
        request.setCourseId(metadata.getCourseId());
        request.setDuration(metadata.getDuration());
        request.setSourceName(filename);
        request.setSourceFormat("PDF");
        request.setContent(text);
        return new PreparedPdf(request, sha256(bytes), bytes);
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) { throw validation("请选择 PDF 文件"); }
        if (file.getSize() > MAX_FILE_SIZE) { throw validation("PDF 文件不能超过10MB"); }
        String filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".pdf")) { throw validation("仅支持 PDF 文件"); }
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 5 || bytes[0] != '%' || bytes[1] != 'P' || bytes[2] != 'D'
                    || bytes[3] != 'F' || bytes[4] != '-') {
                throw validation("文件不是有效的 PDF");
            }
            return bytes;
        } catch (IOException exception) {
            throw validation("PDF 文件读取失败");
        }
    }

    private String extractText(byte[] bytes) {
        try (PDDocument document = Loader.loadPDF(bytes)) {
            if (document.isEncrypted()) { throw validation("加密 PDF 暂不支持"); }
            if (document.getNumberOfPages() > MAX_PAGES) { throw validation("PDF 不能超过200页"); }
            String text = new PDFTextStripper().getText(document).replace("\r\n", "\n").replace('\r', '\n').trim();
            if (text.isBlank()) { throw validation("PDF 未提取到文本；扫描件暂不支持 OCR"); }
            if (text.length() > MAX_TEXT_LENGTH) { throw validation("PDF 提取文本不能超过100000个字符"); }
            return text;
        } catch (InvalidPasswordException exception) {
            throw validation("加密 PDF 暂不支持");
        } catch (BusinessException exception) {
            throw exception;
        } catch (IOException exception) {
            throw validation("文件不是有效的 PDF");
        }
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) { throw validation("PDF 文件名不能为空"); }
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()) { throw validation("PDF 文件名不能为空"); }
        return filename;
    }

    private void copy(PrivateExamImportRequest source, PrivateExamImportRequest target) {
        target.setTitle(source.getTitle());
        target.setCourseId(source.getCourseId());
        target.setDuration(source.getDuration());
        target.setSourceName(source.getSourceName());
        target.setSourceFormat(source.getSourceFormat());
        target.setContent(source.getContent());
    }

    private String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 unavailable", exception);
        }
    }

    private BusinessException validation(String message) {
        return new BusinessException(ResultCode.VALIDATION_ERROR, message);
    }

    private record PreparedPdf(PrivateExamImportRequest request, String hash, byte[] bytes) { }
}
