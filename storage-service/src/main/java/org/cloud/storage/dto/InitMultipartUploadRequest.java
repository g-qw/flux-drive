package org.cloud.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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
@Schema(description = "初始化分片上传的请求参数")
public class InitMultipartUploadRequest implements Serializable {
    @NotBlank
    @Schema(description = "目录ID")
    private UUID directoryId;

    @NotBlank(message = "文件名称不能为空")
    @Schema(description = "文件名称")
    private String filename;

    @Size(min = 1, message = "文件大小不能小于1字节")
    @Schema(description = "文件大小，单位：字节")
    private Long size;

    @NotBlank(message = "内容类型不能为空")
    @Size(max = 64, message = "内容类型长度不能超过64个字符")
    @Schema(description = "文件的MIME类型")
    private String contentType;
}