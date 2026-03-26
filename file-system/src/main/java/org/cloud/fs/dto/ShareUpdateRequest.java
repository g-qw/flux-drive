package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "更新分享的请求参数")
public class ShareUpdateRequest implements Serializable {
    @Schema(description = "分享 id")
    @NotNull
    private UUID id;

    @Schema(description = "分享过期时间戳（毫秒），设为空表示永不过期")
    @NotNull
    private Long expireAt;

    @Schema(description = "访问密码")
    private String password;

    @Schema(description = "分享标题")
    @NotBlank
    private String title;

    @Schema(description = "分享描述信息")
    @NotBlank
    private String description;
}
