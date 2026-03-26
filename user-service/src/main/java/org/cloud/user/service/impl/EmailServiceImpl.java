package org.cloud.user.service.impl;

import freemarker.template.TemplateException;
import io.netty.util.internal.ThreadLocalRandom;
import jakarta.mail.internet.MimeMessage;
import lombok.extern.slf4j.Slf4j;
import org.cloud.user.exception.*;
import org.cloud.user.service.EmailService;
import org.redisson.api.RBucket;
import org.redisson.api.RedissonClient;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.mail.javamail.JavaMailSender;
import org.springframework.mail.javamail.MimeMessageHelper;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;
import org.springframework.stereotype.Service;
import org.springframework.ui.freemarker.FreeMarkerTemplateUtils;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.io.IOException;
import java.time.Duration;
import java.util.Map;

@Slf4j
@Service
public class EmailServiceImpl implements EmailService {
    private final String fromEmail;
    private final ThreadPoolTaskExecutor executor;
    private final JavaMailSender mailSender;
    private final FreeMarkerConfigurer freeMarkerConfigurer;
    private final RedissonClient redisson;

    private static final String CODE_CHARSET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789";
    private static final String CACHE_KEY_CODE_PREFIX = "mail:code:";
    private static final String CACHE_KEY_FREQ_PREFIX = "mail:freq:";
    private static final Duration VERIFICATION_CODE_TTL = Duration.ofMinutes(1);

    public EmailServiceImpl(
            @Value("${spring.mail.username}") String fromEmail,
            @Qualifier("sharedTaskExecutor") ThreadPoolTaskExecutor executor,
            JavaMailSender mailSender,
            FreeMarkerConfigurer freeMarkerConfigurer,
            RedissonClient redisson) {
        this.fromEmail = fromEmail;
        this.executor = executor;
        this.mailSender = mailSender;
        this.freeMarkerConfigurer = freeMarkerConfigurer;
        this.redisson = redisson;
    }

    /**
     * 发送验证码邮件到指定邮箱
     *
     * @param email 邮箱地址
     */
    @Override
    public void sendVerificationCode(String email) {
        String requestKey = CACHE_KEY_FREQ_PREFIX + email;
        String codeKey = CACHE_KEY_CODE_PREFIX + email;

        // 防刷检查
        RBucket<Integer> requestBucket = redisson.getBucket(requestKey);
        if (requestBucket.isExists()) {
            Integer count = requestBucket.get();
            if (count != null && count > 3) {
                throw new BusinessException(429, "发送过于频繁，请稍后再试");
            }
            requestBucket.set(count + 1, VERIFICATION_CODE_TTL);
        } else {
            requestBucket.set(1, VERIFICATION_CODE_TTL);
        }

        // 生成验证码
        String code = generateCode(6);

        // 加载模板并发送邮件
        try {
            String content = loadTemplate(code);
            executor.submit(() -> send(email, content, code));
        } catch (IOException e) {
            throw new BusinessException(500, "邮件模板加载失败，请稍后重试");
        } catch (TemplateException e) {
            throw new BusinessException(500, "邮件内容生成失败，请稍后重试");
        }

        // 缓存验证码
        RBucket<String> codeBucket = redisson.getBucket(codeKey);
        codeBucket.set(code, VERIFICATION_CODE_TTL);
    }

    public void verifyCode(String email, String code) {
        String cacheKey = CACHE_KEY_CODE_PREFIX + email;
        RBucket<String> codeBucket = redisson.getBucket(cacheKey);
        String cachedCode = codeBucket.get();

        // 验证码已过期或不存在
        if (cachedCode == null) {
            log.warn("[verifyCode] email: {}, code: {} - expired or not exists", email, code);
            throw new BusinessException(400, "验证码已过期或不存在");
        }

        // 验证码匹配
        boolean isValid = cachedCode.equals(code);
        if (isValid) {
            codeBucket.delete();
        } else {
            log.warn("[verifyCode] email: {}, input: {}, cached: {} - mismatch", email, code, cachedCode);
            throw new BusinessException(400, "验证码错误");
        }
    }

    private void send(String email, String content, String code) {
        long startTime = System.currentTimeMillis();
        try {
            // 发送邮件
            MimeMessage msg = mailSender.createMimeMessage();
            MimeMessageHelper helper = new MimeMessageHelper(msg, true);
            helper.setFrom(fromEmail);
            helper.setTo(email);
            helper.setSubject("Cloud Storage 邮箱验证");
            helper.setText(content, true);
            mailSender.send(msg);

            // 设置 redis 缓存
            String codeKey = CACHE_KEY_CODE_PREFIX + email;
            redisson.getBucket(codeKey).set(code, VERIFICATION_CODE_TTL);
            redisson.getBucket(CACHE_KEY_FREQ_PREFIX + email)
                    .set(String.valueOf(System.currentTimeMillis()), VERIFICATION_CODE_TTL);

            long elapsedTime = System.currentTimeMillis() - startTime;
            log.info("[send] email={}, code={}, elapsed={}ms", email, code, elapsedTime);
        } catch (Exception e) {
            log.error("Send verification code to {} failed", email, e);
        }
    }

    private String generateCode(int length) {
        char[] buf = new char[length];
        for (int i = 0; i < length; i++) {
            buf[i] = CODE_CHARSET.charAt(ThreadLocalRandom.current().nextInt(CODE_CHARSET.length()));
        }
        return new String(buf);
    }

    private String loadTemplate(String code) throws IOException, TemplateException {
        Map<String, String> model = Map.of("code", code);
        return FreeMarkerTemplateUtils.processTemplateIntoString(
                freeMarkerConfigurer.getConfiguration().getTemplate("auth.html"),
                model
        );
    }
}
