package com.learnplatform.common.time;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.ZoneId;

public final class BusinessTime {
    public static final String ZONE_ID = "America/New_York";
    public static final ZoneId ZONE = ZoneId.of(ZONE_ID);

    private BusinessTime() { }

    public static Clock clock() { return Clock.system(ZONE); }
    public static LocalDate today() { return LocalDate.now(ZONE); }
    public static LocalDateTime now() { return LocalDateTime.now(ZONE); }
}
