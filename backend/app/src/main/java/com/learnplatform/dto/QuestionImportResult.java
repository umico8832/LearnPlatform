package com.learnplatform.dto;

import java.util.ArrayList;
import java.util.List;

/**
 * 题目导入结果
 */
public class QuestionImportResult {
    private int totalRows;
    private int successCount;
    private int failCount;
    private List<String> errors = new ArrayList<>();

    public int getTotalRows() { return totalRows; }
    public void setTotalRows(int totalRows) { this.totalRows = totalRows; }
    public int getSuccessCount() { return successCount; }
    public void setSuccessCount(int successCount) { this.successCount = successCount; }
    public int getFailCount() { return failCount; }
    public void setFailCount(int failCount) { this.failCount = failCount; }
    public List<String> getErrors() { return errors; }
    public void setErrors(List<String> errors) { this.errors = errors; }

    public void addError(String error) {
        this.errors.add(error);
    }
}