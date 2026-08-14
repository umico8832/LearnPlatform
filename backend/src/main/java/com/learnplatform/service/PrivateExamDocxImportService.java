package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;
import com.learnplatform.dto.ExamPaperVO;
import com.learnplatform.dto.PrivateExamDocxConfirmRequest;
import com.learnplatform.dto.PrivateExamDocxDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDocxRequest;
import com.learnplatform.dto.PrivateExamDraftCreateRequest;
import com.learnplatform.dto.PrivateExamDraftVO;
import com.learnplatform.dto.PrivateExamImportConfirmRequest;
import com.learnplatform.dto.PrivateExamImportPreviewVO;
import com.learnplatform.dto.PrivateExamImportRequest;
import org.apache.poi.xwpf.usermodel.IBodyElement;
import org.apache.poi.xwpf.usermodel.XWPFDocument;
import org.apache.poi.xwpf.usermodel.XWPFParagraph;
import org.apache.poi.xwpf.usermodel.XWPFTable;
import org.apache.poi.xwpf.usermodel.XWPFTableCell;
import org.apache.poi.xwpf.usermodel.XWPFTableRow;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.HexFormat;
import java.util.List;
import java.util.Locale;

@Service
public class PrivateExamDocxImportService {
    private static final String MEDIA_TYPE =
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document";
    private static final long MAX_FILE_SIZE = 10L * 1024 * 1024;
    private static final int MAX_TEXT_LENGTH = 100000;

    private final PrivateExamImportService importService;
    private final PrivateExamDraftService draftService;

    public PrivateExamDocxImportService(PrivateExamImportService importService,
                                        PrivateExamDraftService draftService) {
        this.importService = importService;
        this.draftService = draftService;
    }

    public PrivateExamImportPreviewVO preview(PrivateExamDocxRequest metadata, MultipartFile file) {
        PreparedDocx docx = prepare(metadata, file);
        return importService.previewWithSourceHash(docx.request(), docx.hash());
    }

    public ExamPaperVO confirm(PrivateExamDocxConfirmRequest metadata, MultipartFile file, Long userId) {
        PreparedDocx docx = prepare(metadata, file);
        PrivateExamImportConfirmRequest request = new PrivateExamImportConfirmRequest();
        copy(docx.request(), request);
        request.setExpectedContentHash(metadata.getExpectedContentHash());
        request.setConfirmed(metadata.isConfirmed());
        return importService.confirmWithSourceFile(request, userId, docx.hash(), docx.bytes(), MEDIA_TYPE);
    }

    public PrivateExamDraftVO createDraft(PrivateExamDocxDraftCreateRequest metadata,
                                          MultipartFile file, Long userId) {
        PreparedDocx docx = prepare(metadata, file);
        PrivateExamDraftCreateRequest request = new PrivateExamDraftCreateRequest();
        copy(docx.request(), request);
        request.setExpectedContentHash(metadata.getExpectedContentHash());
        return draftService.createWithSourceFile(request, userId, docx.hash(), docx.bytes(), MEDIA_TYPE);
    }

    private PreparedDocx prepare(PrivateExamDocxRequest metadata, MultipartFile file) {
        byte[] bytes = readFile(file);
        String filename = safeFilename(file.getOriginalFilename());
        String text = extractText(bytes);
        PrivateExamImportRequest request = new PrivateExamImportRequest();
        request.setTitle(metadata.getTitle());
        request.setCourseId(metadata.getCourseId());
        request.setDuration(metadata.getDuration());
        request.setSourceName(filename);
        request.setSourceFormat("DOCX");
        request.setContent(text);
        return new PreparedDocx(request, sha256(bytes), bytes);
    }

    private byte[] readFile(MultipartFile file) {
        if (file == null || file.isEmpty()) throw validation("请选择 DOCX 文件");
        if (file.getSize() > MAX_FILE_SIZE) throw validation("DOCX 文件不能超过10MB");
        String filename = safeFilename(file.getOriginalFilename());
        if (!filename.toLowerCase(Locale.ROOT).endsWith(".docx")) throw validation("仅支持 DOCX 文件");
        try {
            byte[] bytes = file.getBytes();
            if (bytes.length < 4 || bytes[0] != 'P' || bytes[1] != 'K') {
                throw validation("文件不是有效的 DOCX");
            }
            return bytes;
        } catch (IOException exception) {
            throw validation("DOCX 文件读取失败");
        }
    }

    private String extractText(byte[] bytes) {
        try (XWPFDocument document = new XWPFDocument(new ByteArrayInputStream(bytes))) {
            rejectComplexContent(document);
            List<String> lines = new ArrayList<>();
            for (IBodyElement element : document.getBodyElements()) {
                if (element instanceof XWPFParagraph paragraph) {
                    addLine(lines, paragraph.getText());
                } else if (element instanceof XWPFTable table) {
                    addTable(lines, table);
                } else {
                    throw complexContent();
                }
            }
            String text = String.join("\n", lines).trim();
            if (text.isBlank()) throw validation("DOCX 未提取到普通段落或表格文本");
            if (text.length() > MAX_TEXT_LENGTH) throw validation("DOCX 提取文本不能超过100000个字符");
            return text;
        } catch (BusinessException exception) {
            throw exception;
        } catch (Exception exception) {
            throw validation("文件不是有效的 DOCX");
        }
    }

    private void rejectComplexContent(XWPFDocument document) {
        String xml = document.getDocument().xmlText();
        if (!document.getAllPictures().isEmpty()
                || document.getHeaderList().stream().anyMatch(header -> !header.getText().isBlank())
                || document.getFooterList().stream().anyMatch(footer -> !footer.getText().isBlank())
                || xml.contains("oMath") || xml.contains("txbxContent")
                || xml.contains("<w:object") || xml.contains("<w:pict") || xml.contains("<w:sdt")) {
            throw complexContent();
        }
    }

    private void addTable(List<String> lines, XWPFTable table) {
        for (XWPFTableRow row : table.getRows()) {
            for (XWPFTableCell cell : row.getTableCells()) {
                if (!cell.getTables().isEmpty()) throw complexContent();
                for (XWPFParagraph paragraph : cell.getParagraphs()) {
                    addLine(lines, paragraph.getText());
                }
            }
        }
    }

    private void addLine(List<String> lines, String value) {
        if (value != null && !value.isBlank()) lines.add(value.trim());
    }

    private BusinessException complexContent() {
        return validation("DOCX 暂不支持图片、公式、文本框或复杂排版");
    }

    private String safeFilename(String originalFilename) {
        if (originalFilename == null || originalFilename.isBlank()) throw validation("DOCX 文件名不能为空");
        String normalized = originalFilename.replace('\\', '/');
        String filename = normalized.substring(normalized.lastIndexOf('/') + 1).trim();
        if (filename.isBlank()) throw validation("DOCX 文件名不能为空");
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

    private record PreparedDocx(PrivateExamImportRequest request, String hash, byte[] bytes) { }
}
