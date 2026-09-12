package com.learnplatform.service.question;

import com.learnplatform.entity.Question;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * 无状态的重复题文本检测算法，不负责题目查询、权限或展示模型组装。
 */
public final class QuestionDuplicateDetector {

    private QuestionDuplicateDetector() { }

    public static List<DuplicateGroup> detect(List<Question> questions, int threshold) {
        if (questions.size() < 2) {
            return List.of();
        }

        Map<Long, String> normalizedMap = new HashMap<>();
        List<Question> candidates = new ArrayList<>();
        for (Question question : questions) {
            String normalized = normalizeQuestionContent(question.getContent());
            if (normalized.length() >= 8) {
                normalizedMap.put(question.getId(), normalized);
                candidates.add(question);
            }
        }
        if (candidates.size() < 2) {
            return List.of();
        }

        DuplicateUnionFind unionFind = new DuplicateUnionFind(candidates.stream().map(Question::getId).toList());
        Map<String, Integer> pairScores = new HashMap<>();
        Map<String, List<Question>> buckets = candidates.stream()
                .collect(Collectors.groupingBy(q -> q.getCourseId() + "|" + q.getQuestionType()));

        for (List<Question> bucket : buckets.values()) {
            compareBucket(bucket, normalizedMap, pairScores, unionFind, threshold);
        }

        Map<Long, List<Question>> grouped = new LinkedHashMap<>();
        for (Question question : candidates) {
            Long root = unionFind.find(question.getId());
            grouped.computeIfAbsent(root, key -> new ArrayList<>()).add(question);
        }

        return grouped.values().stream()
                .filter(group -> group.size() > 1)
                .map(group -> buildGroup(group, normalizedMap, pairScores))
                .sorted(Comparator
                        .comparing(DuplicateGroup::similarityScore, Comparator.reverseOrder())
                        .thenComparing(group -> group.questions().size(), Comparator.reverseOrder()))
                .toList();
    }

    private static void compareBucket(List<Question> bucket, Map<Long, String> normalizedMap,
                                      Map<String, Integer> pairScores, DuplicateUnionFind unionFind,
                                      int threshold) {
        for (int i = 0; i < bucket.size(); i++) {
            Question left = bucket.get(i);
            String leftText = normalizedMap.get(left.getId());
            for (int j = i + 1; j < bucket.size(); j++) {
                Question right = bucket.get(j);
                String rightText = normalizedMap.get(right.getId());
                int score = duplicateSimilarity(leftText, rightText);
                if (score >= threshold) {
                    unionFind.union(left.getId(), right.getId());
                    pairScores.put(pairKey(left.getId(), right.getId()), score);
                }
            }
        }
    }

    private static DuplicateGroup buildGroup(List<Question> group, Map<Long, String> normalizedMap,
                                             Map<String, Integer> pairScores) {
        List<Question> sortedQuestions = group.stream()
                .sorted(Comparator.comparing(Question::getId))
                .toList();
        Set<String> normalizedValues = sortedQuestions.stream()
                .map(question -> normalizedMap.get(question.getId()))
                .collect(Collectors.toCollection(HashSet::new));

        int bestScore = normalizedValues.size() == 1 ? 100 : 0;
        for (int i = 0; i < sortedQuestions.size(); i++) {
            for (int j = i + 1; j < sortedQuestions.size(); j++) {
                bestScore = Math.max(bestScore,
                        pairScores.getOrDefault(pairKey(sortedQuestions.get(i).getId(),
                                sortedQuestions.get(j).getId()), 0));
            }
        }
        return new DuplicateGroup(sortedQuestions, normalizedValues.size() == 1 ? "EXACT" : "SIMILAR", bestScore);
    }

    private static String normalizeQuestionContent(String content) {
        if (content == null) {
            return "";
        }
        StringBuilder builder = new StringBuilder();
        content.toLowerCase(Locale.ROOT).codePoints().forEach(codePoint -> {
            if (Character.isLetterOrDigit(codePoint)) {
                builder.appendCodePoint(codePoint);
            }
        });
        return builder.toString();
    }

    private static int duplicateSimilarity(String left, String right) {
        if (left.equals(right)) {
            return 100;
        }
        int longer = Math.max(left.length(), right.length());
        int shorter = Math.min(left.length(), right.length());
        if (shorter < 8) {
            return 0;
        }
        if (left.contains(right) || right.contains(left)) {
            return Math.round(shorter * 100f / longer);
        }
        int distance = levenshteinDistance(left, right);
        return Math.max(0, Math.round((longer - distance) * 100f / longer));
    }

    private static int levenshteinDistance(String left, String right) {
        int[] previous = new int[right.length() + 1];
        int[] current = new int[right.length() + 1];
        for (int j = 0; j <= right.length(); j++) {
            previous[j] = j;
        }
        for (int i = 1; i <= left.length(); i++) {
            current[0] = i;
            for (int j = 1; j <= right.length(); j++) {
                int cost = left.charAt(i - 1) == right.charAt(j - 1) ? 0 : 1;
                current[j] = Math.min(Math.min(current[j - 1] + 1, previous[j] + 1), previous[j - 1] + cost);
            }
            int[] temp = previous;
            previous = current;
            current = temp;
        }
        return previous[right.length()];
    }

    private static String pairKey(Long leftId, Long rightId) {
        return leftId < rightId ? leftId + ":" + rightId : rightId + ":" + leftId;
    }

    public record DuplicateGroup(List<Question> questions, String matchType, int similarityScore) {
        public DuplicateGroup {
            questions = List.copyOf(questions);
        }
    }

    private static final class DuplicateUnionFind {
        private final Map<Long, Long> parent = new HashMap<>();

        private DuplicateUnionFind(List<Long> ids) {
            ids.forEach(id -> parent.put(id, id));
        }

        private Long find(Long id) {
            Long currentParent = parent.get(id);
            if (currentParent == null || currentParent.equals(id)) {
                return id;
            }
            Long root = find(currentParent);
            parent.put(id, root);
            return root;
        }

        private void union(Long left, Long right) {
            Long leftRoot = find(left);
            Long rightRoot = find(right);
            if (!leftRoot.equals(rightRoot)) {
                parent.put(rightRoot, leftRoot);
            }
        }
    }
}
