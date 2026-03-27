package org.cloud.fs.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.babyfish.jimmer.Page;
import org.cloud.fs.dto.ApiResponse;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.Directory;
import org.cloud.fs.service.DirectoryService;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fs/dir")
@RequiredArgsConstructor
@Tag(name = "目录 API", description = "目录元数据管理，支持创建目录、重命名目录、获取目录内容、路径解析、对目录进行删除、恢复、移动的批量操作等等")
public class DirectoryController {
    private final DirectoryService directoryService;

    @PostMapping("/create")
    @Operation(summary = "创建目录", description = "在指定目录下创建目录，目录名称不能包含 /, 在当前目录发生名称冲突将导致操作失败")
    ApiResponse<Directory> createDirectory(
            @RequestBody DirectoryInput input,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.createDirectory(input, uid));
    }

    @PostMapping("/rename")
    @Operation(summary = "重命名目录", description = "重命名目录，目录名称不能包含 /, 在当前目录发生名称冲突将导致操作失败")
    ApiResponse<Boolean> renameDirectory(
            @RequestBody DirectoryRenameInput input,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.renameDirectory(input.getId(), input.getName(), uid));
    }

    @PostMapping("/{directoryId}/update-size")
    @Operation(summary = "更新目录大小", description = "更新目录大小，单位字节，目录大小为子文件、子目录大小之和")
    ApiResponse<Long> updateDirectorySize(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.updateDirectorySize(directoryId, uid));
    }

    @GetMapping
    @Operation(summary = "获取根目录", description = "获取根目录，根目录的父目录 id 为 null, 根目录名称为 /")
    ApiResponse<Directory> getRootDirectory(@Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return directoryService.getRootDirectory(uid)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure(404, "Root directory not exists"));
    }

    @GetMapping("/{directoryId}")
    @Operation(summary = "获取目录信息", description = "获取完整的目录信息")
    ApiResponse<Directory> getDirectory(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return directoryService.getDirectoryById(directoryId, uid)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure(404, String.format("Directory %s not exists", directoryId)));
    }

    @GetMapping("/{directoryId}/content")
    @Operation(summary = "获取目录内容", description = "获取目录的子文件、子目录，文件和目录均按名称升序排列")
    ApiResponse<DirectoryView> listDirectoryContent(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return directoryService.listDirectoryContent(directoryId, uid)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure(404, String.format("Directory %s not exists", directoryId)));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询目录，默认返回第一页，每页20条，按名称升序排列")
    ApiResponse<Page<Directory>> queryDirectories(
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @ParameterObject @ModelAttribute DirectorySpecification specification,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        return ApiResponse.success(directoryService.queryDirectories(pageRequest, specification, uid));
    }

    @GetMapping("/page-deleted")
    @Operation(summary = "分页查询(软删除)", description = "分页查询被软删除的目录，默认返回第一页，每页20条，按删除时间降序排列")
    ApiResponse<Page<DeletedDirectoryView>> queryDeletedDirectories(
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "deletedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        return ApiResponse.success(directoryService.queryDeletedDirectories(pageRequest, uid));
    }

    @PostMapping("/parse-path")
    @Operation(summary = "获取目录层级路径", description = "根据路径获取从根目录到当前目录的完整层级结构，返回各级目录信息列表")
    ApiResponse<List<Directory>> parsePath(
            @RequestBody @Validated PathParseRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.parsePath(request.getPath(), uid));
    }

    @GetMapping("/{directoryId}/breadcrumb")
    @Operation(summary = "获取指定目录的路径链", description = "获取从根目录到当前目录的完整层级结构，返回各级目录信息列表")
    ApiResponse<List<DirectoryInfoView>> getDirectoryPathChain(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.getDirectoryPathChain(directoryId, uid));
    }

    @GetMapping("/{directoryId}/path")
    @Operation(summary = "获取指定目录的绝对路径", description = "获取从根目录到当前目录的完整层级结构，返回各级目录名称拼接而成的路径字符串")
    ApiResponse<String> getDirectoryPath(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.getDirectoryPath(directoryId, uid));
    }

    @GetMapping("/{directoryId}/node")
    @Operation(summary = "加载目录树数据", description = "返回目录节点数据，包含当前目录信息和直接子目录列表，用于逐级展开目录树")
    ApiResponse<DirectoryNode> getDirectoryNode(
            @Parameter(description = "目录 ID") @PathVariable UUID directoryId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.getDirectoryNode(directoryId, uid));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除目录(软删除)", description = "将多个目录标记为删除状态，被删除目录下的子文件、子目录也将被递归地标记为删除状态")
    ApiResponse<Integer> deleteDirectories(
            @RequestBody @Validated DirectoriesDeleteRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.deleteDirectories(request.getDirectoryIds(), uid));
    }

    @PostMapping("/delete/physical")
    @Operation(summary = "批量删除目录(物理删除)", description = "永久删除目录记录，不可恢复")
    ApiResponse<Integer> deleteDirectoriesPhysically(
            @RequestBody @Validated DirectoriesDeleteRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.deleteDirectoriesPhysically(request.getDirectoryIds(), uid));
    }

    @PostMapping("/recover")
    @Operation(summary = "批量恢复目录", description = "将多个被删除的目录恢复至原位置，被删除目录下的子文件、子目录也将被递归地恢复")
    ApiResponse<Integer> recoverDirectories(
            @RequestBody @Validated DirectoriesRecoverRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.recoverDirectories(request.getDirectoryIds(), uid));
    }

    @PostMapping("/move")
    @Operation(summary = "批量移动目录", description = "将多个目录移动到新的父目录下")
    ApiResponse<Integer> moveDirectories(
            @RequestBody @Validated DirectoriesMoveRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(directoryService.moveDirectories(request.getDirectoryIds(), request.getParentId(), uid));
    }
}
