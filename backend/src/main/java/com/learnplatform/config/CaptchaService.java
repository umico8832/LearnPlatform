package com.learnplatform.config;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.*;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.Base64;
import java.util.Random;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ThreadLocalRandom;

/**
 * 图形验证码服务
 * 使用简单数学运算生成验证码图片，不依赖外部库。
 * 验证码答案存储在内存中，5 分钟过期自动清除。
 */
@Service
public class CaptchaService {

    private static final Logger log = LoggerFactory.getLogger(CaptchaService.class);

    /** 验证码有效期（毫秒） */
    private static final long CAPTCHA_TTL_MS = 5 * 60 * 1000L;

    /** 图片宽度 */
    private static final int WIDTH = 120;
    /** 图片高度 */
    private static final int HEIGHT = 40;

    /** captchaId -> CaptchaEntry */
    private final ConcurrentHashMap<String, CaptchaEntry> captchaStore = new ConcurrentHashMap<>();

    /**
     * 仅由 e2e Profile 配置的固定验证码。生产和开发环境保持为空，继续使用随机数学验证码。
     */
    private final String fixedAnswer;

    public CaptchaService(@Value("${captcha.fixed-answer:}") String fixedAnswer) {
        this.fixedAnswer = fixedAnswer == null ? "" : fixedAnswer.trim();
    }

    /**
     * 生成验证码，返回 {captchaId, base64Image}
     */
    public CaptchaResult generateCaptcha() {
        if (!fixedAnswer.isEmpty()) {
            String captchaId = java.util.UUID.randomUUID().toString().replace("-", "");
            captchaStore.put(captchaId, new CaptchaEntry(fixedAnswer, System.currentTimeMillis()));
            cleanExpired();
            return new CaptchaResult(captchaId, generateImage("E2E = ?"));
        }

        // 生成数学表达式和答案
        int a = ThreadLocalRandom.current().nextInt(1, 20);
        int b = ThreadLocalRandom.current().nextInt(1, 20);
        String[] operators = {"+", "-", "×"};
        String op = operators[ThreadLocalRandom.current().nextInt(operators.length)];
        int answer;
        switch (op) {
            case "+":
                answer = a + b;
                break;
            case "-":
                // 确保结果非负
                if (a < b) {
                    int tmp = a;
                    a = b;
                    b = tmp;
                }
                answer = a - b;
                break;
            default: // "×"
                answer = a * b;
                break;
        }

        String text = a + " " + op + " " + b + " = ?";
        String captchaId = java.util.UUID.randomUUID().toString().replace("-", "");

        // 生成图片
        String base64Image = generateImage(text);

        // 存储答案
        captchaStore.put(captchaId, new CaptchaEntry(String.valueOf(answer), System.currentTimeMillis()));

        // 清理过期条目（惰性清理）
        cleanExpired();

        return new CaptchaResult(captchaId, base64Image);
    }

    /**
     * 校验验证码，验证成功后立即失效（一次性）
     */
    public boolean verifyCaptcha(String captchaId, String userAnswer) {
        if (captchaId == null || userAnswer == null) {
            return false;
        }
        CaptchaEntry entry = captchaStore.remove(captchaId);
        if (entry == null) {
            return false;
        }
        // 检查过期
        if (System.currentTimeMillis() - entry.createTime > CAPTCHA_TTL_MS) {
            return false;
        }
        return entry.answer.trim().equalsIgnoreCase(userAnswer.trim());
    }

    private String generateImage(String text) {
        BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        Random random = ThreadLocalRandom.current();

        // 背景
        g.setColor(new Color(240, 242, 245));
        g.fillRect(0, 0, WIDTH, HEIGHT);

        // 干扰线
        for (int i = 0; i < 6; i++) {
            g.setColor(new Color(180 + random.nextInt(60), 180 + random.nextInt(60), 180 + random.nextInt(60)));
            int x1 = random.nextInt(WIDTH);
            int y1 = random.nextInt(HEIGHT);
            int x2 = random.nextInt(WIDTH);
            int y2 = random.nextInt(HEIGHT);
            g.drawLine(x1, y1, x2, y2);
        }

        // 文字
        g.setFont(new Font("SansSerif", Font.BOLD, 22));
        FontMetrics fm = g.getFontMetrics();
        int textWidth = fm.stringWidth(text);
        int x = (WIDTH - textWidth) / 2;
        int y = (HEIGHT + fm.getAscent() - fm.getDescent()) / 2;

        // 每个字符单独绘制，略微偏移
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            g.setColor(new Color(random.nextInt(80), random.nextInt(80), random.nextInt(80)));
            int charX = x + fm.stringWidth(text.substring(0, i));
            int charY = y + random.nextInt(-3, 4);
            g.drawString(String.valueOf(c), charX, charY);
        }

        g.dispose();

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            ImageIO.write(image, "png", baos);
        } catch (IOException e) {
            log.error("生成验证码图片失败", e);
        }
        return "data:image/png;base64," + Base64.getEncoder().encodeToString(baos.toByteArray());
    }

    private void cleanExpired() {
        long now = System.currentTimeMillis();
        captchaStore.entrySet().removeIf(e -> now - e.getValue().createTime > CAPTCHA_TTL_MS);
    }

    /** 验证码条目 */
    private static class CaptchaEntry {
        final String answer;
        final long createTime;

        CaptchaEntry(String answer, long createTime) {
            this.answer = answer;
            this.createTime = createTime;
        }
    }

    /** 验证码生成结果 */
    public static class CaptchaResult {
        private final String captchaId;
        private final String image; // base64 data URI

        public CaptchaResult(String captchaId, String image) {
            this.captchaId = captchaId;
            this.image = image;
        }

        public String getCaptchaId() {
            return captchaId;
        }

        public String getImage() {
            return image;
        }
    }
}
