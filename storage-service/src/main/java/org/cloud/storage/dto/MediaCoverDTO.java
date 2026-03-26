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
@Schema(description = "多媒体文件的封面")
public class MediaCoverDTO implements Serializable {
    @Schema(description = "文件的MD5哈希值")
    private String md5;

    @Schema(description = "封面的访问地址")
    private String url;

    @Schema(description = "媒体封面的宽度")
    private Integer width;

    @Schema(description = "媒体封面的高度")
    private Integer height;
}
