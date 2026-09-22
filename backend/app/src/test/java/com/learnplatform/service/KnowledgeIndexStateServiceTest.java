package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.entity.KnowledgeContentBundle;
import com.learnplatform.entity.User;
import com.learnplatform.mapper.KnowledgeContentBundleMapper;
import com.learnplatform.mapper.KnowledgeContentIndexMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;

class KnowledgeIndexStateServiceTest {
    private final KnowledgeContentBundleMapper bundles = org.mockito.Mockito.mock(KnowledgeContentBundleMapper.class);
    private final KnowledgeContentIndexMapper indexes = org.mockito.Mockito.mock(KnowledgeContentIndexMapper.class);
    private final UserMapper users = org.mockito.Mockito.mock(UserMapper.class);
    private final KnowledgeIndexStateService state = new KnowledgeIndexStateService(bundles, indexes, users);

    @BeforeEach void setUp() {
        var admin = new User();
        admin.setRole("ADMIN");
        when(users.selectById(7L)).thenReturn(admin);
        when(bundles.update(any(), any())).thenReturn(1);
    }

    @Test void reviewedSourceStatusCannotReplaceManualBundleReview() {
        var bundle = bundle("PENDING");
        when(bundles.lockById(3L)).thenReturn(bundle);

        state.review(7L, 3L, "REVIEWED", "checked by reviewer");

        assertEquals("REVIEWED", bundle.getReviewStatus());
    }

    @Test void withdrawnBundleCannotBeReviewedAgain() {
        when(bundles.lockById(3L)).thenReturn(bundle("WITHDRAWN"));

        assertThrows(BusinessException.class, () -> state.review(7L, 3L, "REVIEWED", "try restore"));
    }

    @Test void renewRejectsAStaleFencingToken() {
        when(indexes.renew(anyLong(), anyString(), anyInt(), any())).thenReturn(0);

        assertFalse(state.renew(9L, "00000000-0000-0000-0000-000000000001", 3));
    }

    @Test void purgedOnlyAcknowledgesADeletedRemoteIndex() {
        when(indexes.purged(9L)).thenReturn(0);

        assertFalse(state.purged(9L));
    }

    private KnowledgeContentBundle bundle(String status) {
        var bundle = new KnowledgeContentBundle();
        bundle.setId(3L);
        bundle.setReviewStatus(status);
        return bundle;
    }
}
