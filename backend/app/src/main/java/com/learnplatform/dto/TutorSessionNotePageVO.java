package com.learnplatform.dto;

import java.util.List;

public record TutorSessionNotePageVO(List<TutorSessionNoteVO> records, long total, int current, int size) { }
