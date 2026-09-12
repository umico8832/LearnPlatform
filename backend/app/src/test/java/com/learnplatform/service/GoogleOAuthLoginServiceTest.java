package com.learnplatform.service;

import com.learnplatform.common.exception.OAuthLoginException;
import com.learnplatform.dto.LoginResponse;
import com.learnplatform.entity.OAuthLoginTicket;
import com.learnplatform.entity.User;
import com.learnplatform.entity.UserIdentity;
import com.learnplatform.mapper.OAuthLoginTicketMapper;
import com.learnplatform.mapper.UserIdentityMapper;
import com.learnplatform.mapper.UserMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.core.oidc.user.OidcUser;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class GoogleOAuthLoginServiceTest {
    @Mock private UserIdentityMapper identityMapper;
    @Mock private OAuthLoginTicketMapper ticketMapper;
    @Mock private UserMapper userMapper;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private AuthTokenHasher tokenHasher;
    @Mock private AuthService authService;
    @Mock private OidcUser oidcUser;

    private GoogleOAuthLoginService service;

    @BeforeEach
    void setUp() {
        service = new GoogleOAuthLoginService(identityMapper, ticketMapper, userMapper, passwordEncoder,
                tokenHasher, authService);
    }

    @Test
    void createsGoogleUserAndOneTimeTicket() {
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("Learner@Example.com");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(oidcUser.getFullName()).thenReturn("Learner");
        when(oidcUser.getPicture()).thenReturn("https://example.com/avatar.png");
        when(passwordEncoder.encode(anyString())).thenReturn("password-hash");
        when(tokenHasher.randomToken()).thenReturn("random-password", "raw-ticket");
        when(tokenHasher.hash("raw-ticket")).thenReturn("ticket-hash");
        when(userMapper.insert(any(User.class))).thenAnswer(invocation -> {
            invocation.<User>getArgument(0).setId(42L);
            return 1;
        });

        String ticket = service.completeLogin(oidcUser);

        assertEquals("raw-ticket", ticket);
        verify(userMapper).insert(any(User.class));
        verify(identityMapper).insert(any(UserIdentity.class));
        verify(ticketMapper).insert(any(OAuthLoginTicket.class));
    }

    @Test
    void refusesSilentLinkToExistingLocalAccount() {
        when(oidcUser.getSubject()).thenReturn("google-subject");
        when(oidcUser.getEmail()).thenReturn("learner@example.com");
        when(oidcUser.getEmailVerified()).thenReturn(true);
        when(userMapper.selectOne(any())).thenReturn(new User());

        OAuthLoginException exception = assertThrows(OAuthLoginException.class,
                () -> service.completeLogin(oidcUser));

        assertEquals("account_exists", exception.getErrorCode());
        verify(userMapper, never()).insert(any(User.class));
        verify(identityMapper, never()).insert(any(UserIdentity.class));
    }

    @Test
    void consumesTicketBeforeIssuingJwt() {
        OAuthLoginTicket ticket = new OAuthLoginTicket();
        ticket.setId(7L);
        ticket.setUserId(42L);
        User user = new User();
        user.setId(42L);
        user.setStatus(1);
        LoginResponse expected = new LoginResponse();
        when(tokenHasher.hash("raw-ticket")).thenReturn("ticket-hash");
        when(ticketMapper.selectOne(any())).thenReturn(ticket);
        when(ticketMapper.consume(any(), any())).thenReturn(1);
        when(userMapper.selectById(42L)).thenReturn(user);
        when(authService.createLoginResponse(user)).thenReturn(expected);

        LoginResponse result = service.exchangeTicket("raw-ticket");

        assertSame(expected, result);
        verify(ticketMapper).consume(any(), any());
    }
}
