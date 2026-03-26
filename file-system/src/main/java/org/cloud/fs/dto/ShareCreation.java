package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "资源分享请求参数")
public class ShareCreation implements Serializable {
    @Schema(description = "访问类型，public(公开)/private(白名单)")
    @NotBlank
    private String accessType;

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

    @Schema(description = "分享中包含的文件资源")
    List<UUID> fileIds;

    @Schema(description = "分享中包含的目录资源")
    List<UUID> directoryIds;

    @Schema(description = "用户ID")
    List<UUID> userIds;
}
