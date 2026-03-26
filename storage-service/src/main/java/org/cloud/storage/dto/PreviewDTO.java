package org.cloud.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件预览的信息")
public class PreviewDTO {
    @Schema(description = "文件ID")
    private UUID fileId;

    @Schema(description = "预签名 URL, 如果为空则表示生成预签名 URL 失败")
    private String url;
}
