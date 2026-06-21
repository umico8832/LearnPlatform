package com.learnplatform.config;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class CaptchaServiceTest {

    @Test
    void fixedAnswer_isAcceptedOnlyOnce() {
        CaptchaService service = new CaptchaService("42");
        CaptchaService.CaptchaResult captcha = service.generateCaptcha();

        assertTrue(service.verifyCaptcha(captcha.getCaptchaId(), "42"));
        assertFalse(service.verifyCaptcha(captcha.getCaptchaId(), "42"));
    }

    @Test
    void fixedAnswer_rejectsIncorrectValue() {
        CaptchaService service = new CaptchaService("42");
        CaptchaService.CaptchaResult captcha = service.generateCaptcha();

        assertFalse(service.verifyCaptcha(captcha.getCaptchaId(), "41"));
    }
}
