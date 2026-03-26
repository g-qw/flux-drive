package org.cloud.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "文件上传结果")
public class FileUploadResult implements Serializable {
    @Schema(description = "文件名称")
    private String filename;

    @Schema(description = "上传是否成功")
    private Boolean success;

    @Schema(description = "错误消息")
    private String errorMsg;

    public static FileUploadResult success(String filename) {
        return FileUploadResult.builder()
                .filename(filename)
                .success(true)
                .build();
    }

    public static FileUploadResult failure(String filename, String errorMsg) {
        return FileUploadResult.builder()
                .filename(filename)
                .success(false)
                .errorMsg(errorMsg)
                .build();
    }
}
