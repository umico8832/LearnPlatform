package com.learnplatform.service;

import com.learnplatform.entity.ExamPaper;
import com.learnplatform.entity.ExamRecord;
import org.junit.jupiter.api.Test;
import java.time.LocalDateTime;
import java.time.Instant;
import java.time.ZoneOffset;
import static org.junit.jupiter.api.Assertions.*;

class ExamBusinessTimeTest {
    @Test void springJumpDoesNotShortenExam() {
        ExamRecord record = new ExamRecord();
        record.setStartTime(LocalDateTime.parse("2026-03-08T01:30:00"));
        ExamPaper paper = new ExamPaper();
        paper.setDuration(60);
        assertFalse(ExamSessionService.isExpired(record, paper, Instant.parse("2026-03-08T07:00:00Z")));
        assertTrue(ExamSessionService.isExpired(record, paper, Instant.parse("2026-03-08T07:30:00Z")));
    }

    @Test void fallRepeatedHourKeepsTheFullDurationAndCorrectOffset() {
        ExamRecord record = new ExamRecord();
        record.setStartTime(LocalDateTime.parse("2026-11-01T01:30:00"));
        record.setStartInstantUtc(LocalDateTime.parse("2026-11-01T05:30:00"));
        ExamPaper paper = new ExamPaper();
        paper.setDuration(60);
        assertFalse(ExamSessionService.isExpired(record, paper, Instant.parse("2026-11-01T06:29:59Z")));
        assertTrue(ExamSessionService.isExpired(record, paper, Instant.parse("2026-11-01T06:30:00Z")));
        assertEquals("2026-11-01T01:30-05:00", ExamSessionService.deadline(record, paper)
                .atZone(com.learnplatform.common.time.BusinessTime.ZONE).toOffsetDateTime().toString());
        record.setStartInstantUtc(LocalDateTime.ofInstant(Instant.parse("2026-11-01T06:30:00Z"), ZoneOffset.UTC));
        assertFalse(ExamSessionService.isExpired(record, paper, Instant.parse("2026-11-01T07:29:59Z")));
        assertTrue(ExamSessionService.isExpired(record, paper, Instant.parse("2026-11-01T07:30:00Z")));
    }
}
