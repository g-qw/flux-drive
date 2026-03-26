package org.cloud.fs.dto;

import io.swagger.v3.oas.annotations.media.Schema;
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
@Schema(description = "目录树节点")
public class DirectoryNode implements Serializable {
    @Schema(description = "目录ID")
    private UUID id;

    @Schema(description = "目录名称")
    private String name;

    @Schema(description = "子目录列表")
    private List<DirectoryNodeView> children;
}