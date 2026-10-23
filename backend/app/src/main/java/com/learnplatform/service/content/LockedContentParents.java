package com.learnplatform.service.content;

import com.learnplatform.entity.Course;
import com.learnplatform.entity.KnowledgePoint;
import java.util.Map;

/** Parent snapshots read while the caller's transaction holds their shared locks. */
public record LockedContentParents(Map<Long, Course> courses, Map<Long, KnowledgePoint> knowledgePoints) { }
