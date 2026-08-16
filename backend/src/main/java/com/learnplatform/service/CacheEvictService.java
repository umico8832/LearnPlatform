package com.learnplatform.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.cache.CacheManager;
import org.springframework.stereotype.Service;

/**
 * 缓存清除服务
 * 当核心业务数据（刷题、错题、考试）变更时，清除相关统计缓存
 */
@Service
public class CacheEvictService {

    private static final Logger log = LoggerFactory.getLogger(CacheEvictService.class);

    private final CacheManager cacheManager;

    public CacheEvictService(CacheManager cacheManager) {
        this.cacheManager = cacheManager;
    }

    /**
     * 清除指定用户的全部统计数据缓存
     * 在刷题提交、错题变更、考试提交后调用
     */
    public void evictUserStatistics(Long userId) {
        if (userId == null) { return; }
        log.debug("清除用户统计缓存: userId={}", userId);

        clearCache("statistics", userId.toString());
        clearCache("dailyTrend", userId.toString());
        clearCache("courseStats", userId.toString());
        clearCache("learningReport", userId.toString());

        // learningPath 和 knowledgeGraph 的 key 包含 courseId，需要清除整个缓存区域
        evictAll("learningPath");
        evictAll("knowledgeGraph");

        // 清除管理端统计缓存
        evictAll("adminStatistics");
    }

    /**
     * 清除管理端统计缓存
     */
    public void evictAdminStatistics() {
        log.debug("清除管理端统计缓存");
        evictAll("adminStatistics");
    }

    private void clearCache(String cacheName, String key) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.evict(key);
            }
        } catch (Exception e) {
            log.warn("清除缓存失败: cache={}, key={}, error={}", cacheName, key, e.getMessage());
        }
    }

    private void evictAll(String cacheName) {
        try {
            var cache = cacheManager.getCache(cacheName);
            if (cache != null) {
                cache.clear();
            }
        } catch (Exception e) {
            log.warn("清除缓存失败: cache={}, error={}", cacheName, e.getMessage());
        }
    }
}