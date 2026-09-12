package com.learnplatform.dto;

import java.util.List;

public record AdminUserPageVO(List<UserVO> records, long total, long current, long size) {
}
