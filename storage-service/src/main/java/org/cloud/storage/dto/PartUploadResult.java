package org.cloud.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件分片上传结果")
public class PartUploadResult {
    @Schema(description = "文件分片编号, 编号从 1 开始")
    private Integer partNumber;

    @Schema(description = "文件分片的MD5哈希值")
    private String etag;
}
