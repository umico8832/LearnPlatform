package com.learnplatform.service.knowledge.vector;

public record KnowledgeVectorSpace(String indexKey, String model,
                                    int dimensions, String collection, String endpointHash) { }
