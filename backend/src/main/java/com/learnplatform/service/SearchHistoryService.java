package com.learnplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.stream.Collectors;

/**
 * 搜索历史与热门搜索服务
 * Phase 18：全局搜索与快捷导航（后续迭代）
 *
 * 使用 ConcurrentHashMap 在内存中维护：
 * 1. 用户级搜索历史（每用户最多 20 条，按时间倒序）
 * 2. 全局热门搜索关键词（按搜索次数排序，定期衰减）
 */
@Service
public class SearchHistoryService {

    private static final Logger log = LoggerFactory.getLogger(SearchHistoryService.class);

    /** 每用户最大历史条数 */
    private static final int MAX_HISTORY_PER_USER = 20;
    /** 全局热门搜索最大条数 */
    private static final int MAX_HOT_KEYWORDS = 100;
    /** 返回给前端的热门搜索条数 */
    private static final int HOT_KEYWORDS_RETURN_LIMIT = 10;
    /** 返回给前端的历史条数 */
    private static final int HISTORY_RETURN_LIMIT = 10;
    /** 最小关键词长度 */
    private static final int MIN_KEYWORD_LENGTH = 1;

    /**
     * 用户搜索历史：userId -> (关键词 -> 搜索时间戳)
     * 使用 LinkedHashMap 保持插入顺序（最新在前）
     */
    private final ConcurrentHashMap<Long, LinkedHashMap<String, Long>> userHistories = new ConcurrentHashMap<>();

    /**
     * 全局热门搜索关键词计数
     */
    private final ConcurrentHashMap<String, AtomicInteger> globalHotKeywords = new ConcurrentHashMap<>();

    /**
     * 记录一次搜索（同时更新用户历史和全局热门）
     *
     * @param userId  用户 ID
     * @param keyword 搜索关键词
     */
    public void recordSearch(Long userId, String keyword) {
        if (userId == null || keyword == null) return;
        String trimmed = keyword.trim();
        if (trimmed.length() < MIN_KEYWORD_LENGTH) return;

        // 更新用户历史
        addToUserHistory(userId, trimmed);

        // 更新全局热门
        addToHotKeywords(trimmed);

        log.debug("记录搜索: userId={}, keyword={}", userId, trimmed);
    }

    /**
     * 获取用户搜索历史
     *
     * @param userId 用户 ID
     * @return 最近的搜索关键词列表（时间倒序）
     */
    public List<String> getUserHistory(Long userId) {
        if (userId == null) return Collections.emptyList();

        LinkedHashMap<String, Long> history = userHistories.get(userId);
        if (history == null || history.isEmpty()) return Collections.emptyList();

        synchronized (history) {
            return history.entrySet().stream()
                    .sorted(Map.Entry.<String, Long>comparingByValue().reversed())
                    .limit(HISTORY_RETURN_LIMIT)
                    .map(Map.Entry::getKey)
                    .collect(Collectors.toList());
        }
    }

    /**
     * 获取全局热门搜索关键词
     *
     * @return 热门关键词列表（按搜索次数降序）
     */
    public List<String> getHotKeywords() {
        if (globalHotKeywords.isEmpty()) return Collections.emptyList();

        return globalHotKeywords.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(HOT_KEYWORDS_RETURN_LIMIT)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    /**
     * 清除用户搜索历史
     *
     * @param userId 用户 ID
     */
    public void clearUserHistory(Long userId) {
        if (userId == null) return;
        userHistories.remove(userId);
        log.info("清除用户搜索历史: userId={}", userId);
    }

    /**
     * 删除用户历史中的某个关键词
     *
     * @param userId  用户 ID
     * @param keyword 要删除的关键词
     */
    public void removeUserHistoryItem(Long userId, String keyword) {
        if (userId == null || keyword == null) return;
        LinkedHashMap<String, Long> history = userHistories.get(userId);
        if (history == null) return;
        synchronized (history) {
            history.remove(keyword.trim());
        }
        log.debug("删除搜索历史项: userId={}, keyword={}", userId, keyword.trim());
    }

    // ---- 内部方法 ----

    /**
     * 添加到用户搜索历史（线程安全，LRU 淘汰最旧的）
     */
    private void addToUserHistory(Long userId, String keyword) {
        LinkedHashMap<String, Long> history = userHistories.computeIfAbsent(userId,
                k -> new LinkedHashMap<>(MAX_HISTORY_PER_USER + 1, 0.75f, false) {
                    @Override
                    protected boolean removeEldestEntry(Map.Entry<String, Long> eldest) {
                        return size() > MAX_HISTORY_PER_USER;
                    }
                });

        synchronized (history) {
            // 如果已存在，先移除再重新插入（更新时间）
            history.remove(keyword);
            history.put(keyword, System.currentTimeMillis());
        }
    }

    /**
     * 添加到全局热门关键词计数
     */
    private void addToHotKeywords(String keyword) {
        // 限制热门关键词池大小
        if (globalHotKeywords.size() >= MAX_HOT_KEYWORDS * 2) {
            evictLowCountKeywords();
        }
        globalHotKeywords.computeIfAbsent(keyword, k -> new AtomicInteger(0)).incrementAndGet();
    }

    /**
     * 淘汰低频关键词，保留 Top MAX_HOT_KEYWORDS
     */
    private void evictLowCountKeywords() {
        List<String> toKeep = globalHotKeywords.entrySet().stream()
                .sorted((a, b) -> Integer.compare(b.getValue().get(), a.getValue().get()))
                .limit(MAX_HOT_KEYWORDS)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());

        globalHotKeywords.keySet().retainAll(new HashSet<>(toKeep));
        log.debug("热门关键词池已清理，保留 {} 条", globalHotKeywords.size());
    }
}