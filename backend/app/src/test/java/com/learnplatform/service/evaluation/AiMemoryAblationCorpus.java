package com.learnplatform.service.evaluation;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;

import java.io.IOException;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

record AiMemoryAblationCorpus(int version, String provenance, List<Case> cases) {
    static final String RESOURCE = "/ai-evaluation/memory-cases.json";
    private static final ObjectMapper JSON = new ObjectMapper();

    static AiMemoryAblationCorpus load() throws IOException {
        try (var input = AiMemoryAblationCorpus.class.getResourceAsStream(RESOURCE)) {
            if (input == null) { throw new IOException("Missing memory ablation corpus"); }
            var corpus = JSON.readValue(input, AiMemoryAblationCorpus.class);
            Set<String> ids = new HashSet<>();
            if (corpus.version() < 1 || corpus.provenance() == null || corpus.provenance().isBlank()
                    || corpus.cases() == null || corpus.cases().isEmpty()) {
                throw new IOException("Invalid memory ablation corpus");
            }
            for (Case sample : corpus.cases()) {
                if (sample.id() == null || !sample.id().matches("[a-z0-9-]+") || !ids.add(sample.id())
                        || sample.question() == null || sample.question().isBlank()
                        || sample.memory() == null || !sample.memory().isObject()
                        || !sample.memory().path("revision").canConvertToLong()
                        || !sample.memory().has("explanationStyle") || !sample.memory().has("goal")
                        || !sample.memory().path("sessionNotes").isArray()
                        || sample.manualCriteria() == null || sample.manualCriteria().isEmpty()) {
                    throw new IOException("Invalid memory ablation case");
                }
            }
            return corpus;
        }
    }

    record Case(String id, String question, JsonNode memory, List<String> manualCriteria) {
        List<String> activeFactors() {
            var factors = new java.util.ArrayList<String>();
            if (!memory.path("explanationStyle").isNull() || !memory.path("goal").isNull()) {
                factors.add("PROFILE");
            }
            if (!memory.path("sessionNotes").isEmpty()) { factors.add("NOTES_WITH_SOURCE"); }
            return List.copyOf(factors);
        }
    }

    enum Condition {
        NO_MEMORY(false, false), PROFILE_ONLY(true, false), NOTES_ONLY(false, true), FULL_MEMORY(true, true);

        private final boolean profile;
        private final boolean notes;

        Condition(boolean profile, boolean notes) {
            this.profile = profile;
            this.notes = notes;
        }

        String context(Case sample) {
            ObjectNode memory = sample.memory().deepCopy();
            if (!profile) {
                memory.put("revision", 0);
                memory.putNull("explanationStyle");
                memory.putNull("goal");
            }
            if (!notes) { memory.putArray("sessionNotes"); }
            return memory.toString();
        }
    }
}
