package com.learnplatform.service;

import com.learnplatform.common.exception.BusinessException;
import com.learnplatform.dto.VerificationTicketResponse;
import com.learnplatform.entity.EmailVerification;
import com.learnplatform.mapper.EmailVerificationMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class EmailVerificationServiceTest {
    private EmailVerificationMapper mapper;
    private AuthMailService mailService;
    private AuthTokenHasher tokenHasher;
    private EmailVerificationService service;

    @BeforeEach
    void setUp() {
        mapper = mock(EmailVerificationMapper.class);
        mailService = mock(AuthMailService.class);
        tokenHasher = new AuthTokenHasher("test-auth-secret", "fallback-secret");
        service = new EmailVerificationService(mapper, tokenHasher, mailService);
    }

    @Test
    void sendsHashedRegistrationCodeWithoutPersistingPlaintext() {
        when(mapper.selectCount(any())).thenReturn(0L);

        service.sendRegistrationCode(" Learner@Example.com ", "127.0.0.1", "test-agent");

        ArgumentCaptor<EmailVerification> verificationCaptor = ArgumentCaptor.forClass(EmailVerification.class);
        ArgumentCaptor<String> codeCaptor = ArgumentCaptor.forClass(String.class);
        verify(mapper).insert(verificationCaptor.capture());
        verify(mailService).sendVerificationCode(any(String.class), codeCaptor.capture());
        assertEquals("learner@example.com", verificationCaptor.getValue().getEmail());
        assertEquals(tokenHasher.hash("register:learner@example.com:" + codeCaptor.getValue()),
                verificationCaptor.getValue().getCodeHash());
    }

    @Test
    void verifiesCodeAndIssuesShortLivedTicket() {
        String email = "learner@example.com";
        String code = "123456";
        EmailVerification verification = validVerification(email, code);
        when(mapper.selectOne(any())).thenReturn(verification);

        VerificationTicketResponse response = service.verifyRegistrationCode(email, code);

        assertNotNull(response.getVerificationTicket());
        assertEquals(300, response.getExpiresIn());
        assertNotNull(verification.getTicketHash());
        assertNotNull(verification.getVerifiedAt());
        verify(mapper).updateById(verification);
    }

    @Test
    void rejectsWrongCodeAndCountsAttempt() {
        EmailVerification verification = validVerification("learner@example.com", "123456");
        when(mapper.selectOne(any())).thenReturn(verification);

        assertThrows(BusinessException.class,
                () -> service.verifyRegistrationCode("learner@example.com", "654321"));

        assertEquals(1, verification.getAttemptCount());
        verify(mapper).updateById(verification);
        verify(mailService, never()).sendVerificationCode(any(), any());
    }

    private EmailVerification validVerification(String email, String code) {
        EmailVerification verification = new EmailVerification();
        verification.setEmail(email);
        verification.setPurpose("register");
        verification.setCodeHash(tokenHasher.hash("register:" + email + ":" + code));
        verification.setAttemptCount(0);
        verification.setExpiresAt(LocalDateTime.now().plusMinutes(5));
        return verification;
    }
}
