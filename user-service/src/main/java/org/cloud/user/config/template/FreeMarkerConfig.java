package org.cloud.user.config.template;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.view.freemarker.FreeMarkerConfigurer;

import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.HashMap;
import java.util.Map;

@Configuration
public class FreeMarkerConfig {
    @Bean
    public FreeMarkerConfigurer freeMarkerConfigurer(){
        FreeMarkerConfigurer configurer = new FreeMarkerConfigurer();

        configurer.setTemplateLoaderPath("classpath:/templates/"); // 设置模板文件路径

        //添加全局变量
        Map<String, Object> variables = new HashMap<>();
        variables.put("contact", "passfort@163.com");
        variables.put("companyName", "cloud-storage");
        variables.put("currentYear", LocalDate.now().getYear());
        configurer.setFreemarkerVariables(variables);

        // 设置属性
        configurer.setDefaultCharset(StandardCharsets.UTF_8);
        configurer.setDefaultEncoding("UTF-8");

        return configurer;
    }
}