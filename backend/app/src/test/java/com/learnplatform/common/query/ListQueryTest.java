package com.learnplatform.common.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class ListQueryTest {
    @Test
    void trimsKeywordsAndTreatsEmptyAsAbsent() {
        assertNull(ListQuery.keyword(null));
        assertNull(ListQuery.keyword(" \t\n "));
        assertEquals("条件", ListQuery.keyword("  条件  "));
        assertEquals("字".repeat(100), ListQuery.keyword("字".repeat(100)));
        BusinessException error = assertThrows(BusinessException.class,
                () -> ListQuery.keyword("字".repeat(101)));
        assertEquals(1001, error.getCode());
    }

    @Test
    void bindsLikePatternsWithExplicitBangEscapeAndLiteralBackslashes() {
        assertNull(ListQuery.likePattern(" "));
        assertEquals("%50!%!_!!\\题%", ListQuery.likePattern(" 50%_!\\题 "));
        assertEquals("%普通题干%", ListQuery.likePattern("普通题干"));
    }

    @Test
    void keepsTheExistingFiveHundredRowCapAndRejectsInvalidPages() {
        Page<String> page = ListQuery.page(2, 1000);
        assertEquals(2, page.getCurrent());
        assertEquals(500, page.getSize());
        assertEquals(10, ListQuery.page(1, 10).getSize());
        assertEquals(Integer.MAX_VALUE, ListQuery.page(Integer.MAX_VALUE, 1).getCurrent());
        for (int[] invalid : new int[][]{{0, 10}, {-1, 10}, {1, 0}, {1, -1}}) {
            assertEquals(1001, assertThrows(BusinessException.class,
                    () -> ListQuery.page(invalid[0], invalid[1])).getCode());
        }
    }
}
