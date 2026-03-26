package org.cloud.user.config.doc;

import io.swagger.v3.oas.models.Components;
import io.swagger.v3.oas.models.OpenAPI;
import io.swagger.v3.oas.models.info.Contact;
import io.swagger.v3.oas.models.info.Info;
import io.swagger.v3.oas.models.info.License;
import io.swagger.v3.oas.models.security.SecurityRequirement;
import io.swagger.v3.oas.models.security.SecurityScheme;
import io.swagger.v3.oas.models.servers.Server;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;

@Configuration
public class OpenApiConfig {
    @Value("${server.port}")
    private String serverPort;

    @Value("${gateway.port:75}")
    private String gatewayPort;

    /**
     * 全局 OpenAPI 文档配置（不分组时的默认配置）
     */
    @Bean
    public OpenAPI customOpenAPI() {
        return new OpenAPI()
                .info(apiInfo()) // API 基础信息
                .servers(servers()) // 服务器列表
                .security(securityRequirements()) // 安全认证配置
                .components(components()); // 全局安全组件定义
    }

    private Info apiInfo() {
        return new Info()
                .title("用户管理 API 文档")
                .description("提供对用户的管理，支持注册、登录、修改用户信息等等")
                .version("v1.0.0")
                .contact(new Contact()
                        .name("技术支持")
                        .email("laiqw6537@eqq.com")
                        .url("https://www.zhihu.com/people/13-47-79-44"))
                .license(new License()
                        .name("Apache 2.0")
                        .url("https://www.apache.org/licenses/LICENSE-2.0")
                );
    }

    private List<Server> servers() {
        return List.of(
                new Server()
                        .url("http://localhost:" + serverPort)
                        .description("本地开发环境"),
                new Server()
                        .url("http://localhost:" + gatewayPort)
                        .description("测试环境"),
                new Server()
                        .url("http://gateway:" + gatewayPort)
                        .description("生产环境")
        );
    }

    private List<SecurityRequirement> securityRequirements() {
        return List.of(new SecurityRequirement().addList("bearerAuth"));
    }

    private Components components() {
        return new Components()
                // JWT Token 认证
                .addSecuritySchemes("bearerAuth", new SecurityScheme()
                        .name("bearerAuth")
                        .type(SecurityScheme.Type.HTTP)
                        .scheme("bearer")
                        .bearerFormat("JWT")
                )

                // API Key 认证
                .addSecuritySchemes("apiKey", new SecurityScheme()
                        .type(SecurityScheme.Type.APIKEY)
                        .in(SecurityScheme.In.HEADER)
                        .name("X-API-Key"));
    }
}
