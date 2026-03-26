package org.cloud.user.config.mail;

import jakarta.annotation.PostConstruct;
import org.springframework.context.annotation.Configuration;

@Configuration
public class MailConfig {
    @PostConstruct
    public void setMailProperties() {
        // 避免在生成邮件地址时进行DNS查找, 可以显著降低发送邮件的时长，减少大概 15s 左右
        System.setProperty("mail.mime.address.usecanonicalhostname", "false");
    }
}
