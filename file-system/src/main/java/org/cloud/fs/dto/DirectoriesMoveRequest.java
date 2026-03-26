package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;
import java.util.UUID;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
@Schema(description = "批量移动目录请求的参数")
public class DirectoriesMoveRequest {
    @NotEmpty(message = "Directory ID list can't be empty")
    @Schema(description = "目录 ID 列表")
    private List<UUID> directoryIds;

    @NotNull(message = "Target directory ID can't be null")
    @Schema(description = "移动至指定目录的ID")
    private UUID parentId;
}
