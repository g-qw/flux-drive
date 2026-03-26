package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "批量恢复文件请求的参数")
public class FilesRecoverRequest implements Serializable {
    @NotEmpty(message = "File ID list can't be empty")
    @Schema(description = "文件 ID 列表")
    private List<UUID> fileIds;

    @NotNull(message = "Target directory ID can't be null")
    @Schema(description = "恢复至指定目录的ID")
    private UUID targetDirectoryId;
}
