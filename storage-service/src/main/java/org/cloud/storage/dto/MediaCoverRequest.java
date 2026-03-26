package org.cloud.storage.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.util.List;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "媒体文件的封面请求")
public class MediaCoverRequest implements Serializable {
    @NotEmpty
    @Schema(description = "媒体文件的MD5哈希值列表")
    List<String> fileHashes;
}
