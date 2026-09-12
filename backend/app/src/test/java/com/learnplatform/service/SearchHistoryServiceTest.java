package com.learnplatform.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * SearchHistoryService 单元测试
 * Phase 18：搜索历史与热门搜索
 */
class SearchHistoryServiceTest {

    private SearchHistoryService searchHistoryService;

    @BeforeEach
    void setUp() {
        searchHistoryService = new SearchHistoryService();
    }

    @Nested
    @DisplayName("记录搜索")
    class RecordSearchTests {

        @Test
        @DisplayName("正常记录搜索后可获取历史")
        void recordSearch_thenGetHistory_returnsKeyword() {
            searchHistoryService.recordSearch(1L, "Java");

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertEquals(1, history.size());
            assertEquals("Java", history.get(0));
        }

        @Test
        @DisplayName("同一关键词多次搜索只保留一条")
        void recordSearch_duplicateKeyword_keepsOnlyOne() {
            searchHistoryService.recordSearch(1L, "Python");
            searchHistoryService.recordSearch(1L, "Java");
            searchHistoryService.recordSearch(1L, "Python");

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertEquals(2, history.size());
            assertTrue(history.contains("Python"));
            assertTrue(history.contains("Java"));
        }

        @Test
        @DisplayName("null userId 不记录")
        void recordSearch_nullUserId_noOp() {
            searchHistoryService.recordSearch(null, "Java");
            List<String> history = searchHistoryService.getUserHistory(null);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("null/空关键词不记录")
        void recordSearch_nullOrEmptyKeyword_noOp() {
            searchHistoryService.recordSearch(1L, null);
            searchHistoryService.recordSearch(1L, "  ");

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("历史条数超过上限后淘汰最旧的")
        void recordSearch_exceedsMaxHistory_evictsOldest() {
            // 每次插入后等待 1ms 确保时间戳不同，排序稳定
            for (int i = 0; i < 25; i++) {
                searchHistoryService.recordSearch(1L, "keyword" + i);
                try { Thread.sleep(1); } catch (InterruptedException ignored) {}
            }

            List<String> history = searchHistoryService.getUserHistory(1L);
            // getUserHistory 最多返回 10 条
            assertTrue(history.size() <= 10);
            // 最新的关键词应存在，最旧的应被淘汰
            assertTrue(history.contains("keyword24"));
            assertFalse(history.contains("keyword0"));
            assertFalse(history.contains("keyword1"));
            assertFalse(history.contains("keyword2"));
        }
    }

    @Nested
    @DisplayName("获取历史")
    class GetHistoryTests {

        @Test
        @DisplayName("无历史时返回空列表")
        void getHistory_noHistory_returnsEmpty() {
            List<String> history = searchHistoryService.getUserHistory(999L);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("null userId 返回空列表")
        void getHistory_nullUserId_returnsEmpty() {
            List<String> history = searchHistoryService.getUserHistory(null);
            assertNotNull(history);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("不同用户历史独立")
        void getHistory_differentUsers_independent() {
            searchHistoryService.recordSearch(1L, "Java");
            searchHistoryService.recordSearch(2L, "Python");

            List<String> history1 = searchHistoryService.getUserHistory(1L);
            List<String> history2 = searchHistoryService.getUserHistory(2L);

            assertEquals(1, history1.size());
            assertEquals("Java", history1.get(0));
            assertEquals(1, history2.size());
            assertEquals("Python", history2.get(0));
        }

        @Test
        @DisplayName("返回最多 10 条历史")
        void getHistory_returnsMax10() {
            for (int i = 0; i < 15; i++) {
                searchHistoryService.recordSearch(1L, "kw" + i);
            }

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertTrue(history.size() <= 10);
        }
    }

    @Nested
    @DisplayName("热门搜索")
    class HotKeywordsTests {

        @Test
        @DisplayName("记录搜索后热门关键词应包含该词")
        void recordSearch_thenHotKeywords_containsKeyword() {
            searchHistoryService.recordSearch(1L, "Java");
            searchHistoryService.recordSearch(2L, "Java");
            searchHistoryService.recordSearch(3L, "Python");

            List<String> hot = searchHistoryService.getHotKeywords();
            assertFalse(hot.isEmpty());
            // Java 被搜索 2 次，应该排在前面
            assertEquals("Java", hot.get(0));
        }

        @Test
        @DisplayName("无搜索记录时热门搜索为空")
        void getHotKeywords_noRecords_returnsEmpty() {
            List<String> hot = searchHistoryService.getHotKeywords();
            assertNotNull(hot);
            assertTrue(hot.isEmpty());
        }

        @Test
        @DisplayName("热门搜索按次数降序排列")
        void getHotKeywords_sortedByCountDesc() {
            for (int i = 0; i < 5; i++) {
                searchHistoryService.recordSearch(1L, "热门A");
            }
            for (int i = 0; i < 3; i++) {
                searchHistoryService.recordSearch(1L, "热门B");
            }
            searchHistoryService.recordSearch(1L, "热门C");

            List<String> hot = searchHistoryService.getHotKeywords();
            assertTrue(hot.size() >= 3);
            assertEquals("热门A", hot.get(0));
            assertEquals("热门B", hot.get(1));
            assertEquals("热门C", hot.get(2));
        }

        @Test
        @DisplayName("热门搜索最多返回 10 条")
        void getHotKeywords_max10() {
            for (int i = 0; i < 15; i++) {
                searchHistoryService.recordSearch(1L, "topic" + i);
            }

            List<String> hot = searchHistoryService.getHotKeywords();
            assertTrue(hot.size() <= 10);
        }
    }

    @Nested
    @DisplayName("清除历史")
    class ClearHistoryTests {

        @Test
        @DisplayName("清除用户历史后为空")
        void clearHistory_thenEmpty() {
            searchHistoryService.recordSearch(1L, "Java");
            searchHistoryService.recordSearch(1L, "Python");

            searchHistoryService.clearUserHistory(1L);

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertTrue(history.isEmpty());
        }

        @Test
        @DisplayName("清除 null userId 不抛异常")
        void clearHistory_nullUserId_noOp() {
            assertDoesNotThrow(() -> searchHistoryService.clearUserHistory(null));
        }

        @Test
        @DisplayName("删除单条历史项")
        void removeHistoryItem_removesOnlySpecified() {
            searchHistoryService.recordSearch(1L, "Java");
            searchHistoryService.recordSearch(1L, "Python");

            searchHistoryService.removeUserHistoryItem(1L, "Java");

            List<String> history = searchHistoryService.getUserHistory(1L);
            assertEquals(1, history.size());
            assertEquals("Python", history.get(0));
        }

        @Test
        @DisplayName("删除不存在的历史项不抛异常")
        void removeHistoryItem_nonExistent_noOp() {
            assertDoesNotThrow(() -> searchHistoryService.removeUserHistoryItem(1L, "不存在"));
        }
    }
}