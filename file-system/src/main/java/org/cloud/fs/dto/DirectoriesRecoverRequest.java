package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotEmpty;
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
@Schema(description = "批量恢复目录请求的参数")
public class DirectoriesRecoverRequest {
    @NotEmpty(message = "Directory ID list can't be empty")
    @Schema(description = "目录 ID 列表")
    private List<UUID> directoryIds;
}
