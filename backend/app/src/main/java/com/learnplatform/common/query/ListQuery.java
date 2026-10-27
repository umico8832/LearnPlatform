package com.learnplatform.common.query;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.common.result.ResultCode;

public final class ListQuery {
    private ListQuery() { }

    public static <T> Page<T> page(int pageNum, int pageSize) {
        if (pageNum < 1 || pageSize < 1) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "页码和每页数量必须大于0");
        }
        return new Page<>(pageNum, Math.min(pageSize, 500));
    }

    public static String keyword(String raw) {
        if (raw == null) { return null; }
        String value = raw.trim();
        if (value.length() > 100) {
            throw new BusinessException(ResultCode.VALIDATION_ERROR, "关键词不能超过100个字符");
        }
        return value.isEmpty() ? null : value;
    }

    public static String likePattern(String raw) {
        String value = keyword(raw);
        return value == null ? null : "%" + value.replace("!", "!!")
                .replace("%", "!%").replace("_", "!_") + "%";
    }
}
