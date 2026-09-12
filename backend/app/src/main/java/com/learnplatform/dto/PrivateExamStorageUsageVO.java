package com.learnplatform.dto;

public class PrivateExamStorageUsageVO {
    private Long usedBytes;
    private Long limitBytes;
    private Long remainingBytes;
    private Long fileCount;

    public Long getUsedBytes() { return usedBytes; }
    public void setUsedBytes(Long usedBytes) { this.usedBytes = usedBytes; }
    public Long getLimitBytes() { return limitBytes; }
    public void setLimitBytes(Long limitBytes) { this.limitBytes = limitBytes; }
    public Long getRemainingBytes() { return remainingBytes; }
    public void setRemainingBytes(Long remainingBytes) { this.remainingBytes = remainingBytes; }
    public Long getFileCount() { return fileCount; }
    public void setFileCount(Long fileCount) { this.fileCount = fileCount; }
}
