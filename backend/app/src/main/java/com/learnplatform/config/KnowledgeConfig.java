package com.learnplatform.config;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "knowledge")
public class KnowledgeConfig {
    private String snapshotPath = "";
    private boolean vectorEnabled;
    private String vectorUrl = "";
    private String vectorApiKey = "";
    private String vectorCollection = "";
    private int vectorTimeoutSeconds = 20;
    private int embeddingBatchSize = 16;
    private boolean retrievalEnabled;
    private int topK = 4;

    public String getSnapshotPath() { return snapshotPath; }
    public void setSnapshotPath(String value) { snapshotPath = value; }
    public boolean isVectorEnabled() { return vectorEnabled; }
    public void setVectorEnabled(boolean value) { vectorEnabled = value; }
    public String getVectorUrl() { return vectorUrl; }
    public void setVectorUrl(String value) { vectorUrl = value; }
    public String getVectorApiKey() { return vectorApiKey; }
    public void setVectorApiKey(String value) { vectorApiKey = value; }
    public String getVectorCollection() { return vectorCollection; }
    public void setVectorCollection(String value) { vectorCollection = value; }
    public int getVectorTimeoutSeconds() { return vectorTimeoutSeconds; }
    public void setVectorTimeoutSeconds(int value) { vectorTimeoutSeconds = value; }
    public int getEmbeddingBatchSize() { return embeddingBatchSize; }
    public void setEmbeddingBatchSize(int value) { embeddingBatchSize = value; }
    public boolean isRetrievalEnabled() { return retrievalEnabled; }
    public void setRetrievalEnabled(boolean value) { retrievalEnabled = value; }
    public int getTopK() { return topK; }
    public void setTopK(int value) { topK = value; }
}
