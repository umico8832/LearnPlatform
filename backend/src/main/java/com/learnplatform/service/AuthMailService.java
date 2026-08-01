package com.learnplatform.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.SimpleMailMessage;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.stereotype.Service;

@Service
public class AuthMailService {
    private final JavaMailSender mailSender;
    private final String from;

    public AuthMailService(JavaMailSender mailSender,
                           @Value("${spring.mail.from:no-reply@learnplatform.local}") String from) {
        this.mailSender = mailSender;
        this.from = from;
    }

    public void sendVerificationCode(String email, String code) {
        SimpleMailMessage message = baseMessage(email, "LearnPlatform 邮箱验证码");
        message.setText("你的注册验证码是：" + code + "\n\n验证码 10 分钟内有效，请勿转发给他人。");
        mailSender.send(message);
    }

    public void sendPasswordResetLink(String email, String resetLink) {
        SimpleMailMessage message = baseMessage(email, "LearnPlatform 密码重置");
        message.setText("我们收到了密码重置请求。请在 30 分钟内打开以下链接：\n\n"
                + resetLink + "\n\n如果不是你本人操作，请忽略此邮件。");
        mailSender.send(message);
    }

    private SimpleMailMessage baseMessage(String email, String subject) {
        SimpleMailMessage message = new SimpleMailMessage();
        message.setFrom(from);
        message.setTo(email);
        message.setSubject(subject);
        return message;
    }
}
