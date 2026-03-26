package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "访问分享内容的请求参数")
public class ShareDetailRequest {
    @Schema(description = "分享id")
    @NotNull
    private UUID id;

    @Schema(description = "访问密码")
    private String password;
}
