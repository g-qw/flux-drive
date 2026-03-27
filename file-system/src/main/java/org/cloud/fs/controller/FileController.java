package org.cloud.fs.controller;

import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.babyfish.jimmer.Page;
import org.cloud.fs.dto.ApiResponse;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.enums.FileType;
import org.springdoc.core.annotations.ParameterObject;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import io.swagger.v3.oas.annotations.Operation;
import lombok.RequiredArgsConstructor;
import org.cloud.fs.entity.File;
import org.cloud.fs.service.FileService;
import org.springframework.web.bind.annotation.*;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/fs/file")
@RequiredArgsConstructor
@Tag(name = "文件 API", description = "文件的元数据管理，支持创建、查询、重命名、删除、恢复、复制、移动等操作")
public class FileController {
    private final FileService fileService;

    @PostMapping("/create")
    @Operation(summary = "创建文件", description = "在指定目录创建文件，允许文件名称重复")
    public ApiResponse<File> createFile(
            @RequestBody FileInput fileInput,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.createFile(fileInput, uid));
    }

    @GetMapping("/{fileId}")
    @Operation(summary = "获取文件", description = "获取文件的完整信息")
    public ApiResponse<FileView> getFile(
            @PathVariable UUID fileId,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return fileService.getFileViewById(fileId, uid)
                .map(ApiResponse::success)
                .orElse(ApiResponse.failure(404, "File not found"));
    }

    @GetMapping("/page")
    @Operation(summary = "分页查询", description = "分页查询文件，默认返回第一页，每页20条，按名称升序排列")
    public ApiResponse<Page<FileView>> queryFiles(
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "name", direction = Sort.Direction.ASC) Pageable pageable,
            @ParameterObject @ModelAttribute FileSpecification specification,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        return ApiResponse.success(fileService.queryFiles(pageRequest, specification, uid));
    }

    @GetMapping("/page-deleted")
    @Operation(summary = "分页查询(软删除)", description = "分页查询被软删除的文件，默认返回第一页，每页20条，按删除时间降序排列")
    public ApiResponse<Page<DeletedFileView>> queryDeletedFiles(
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "deletedAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        return ApiResponse.success(fileService.queryDeletedFiles(pageRequest, uid));
    }

    @GetMapping("/type/{fileType}")
    @Operation(summary = "分页查询指定类别的文件", description = "按类别分页查询文件，默认返回第一页，每页20条，按创建时间降序排列")
    public ApiResponse<Page<FileView>> queryFilesByType(
            @Parameter(
                    description = "文件类别",
                    required = true,
                    schema = @Schema(
                            type = "string",
                            allowableValues = {"image", "audio", "video", "document"},
                            example = "image"
                    )
            ) @PathVariable("fileType") String fileType,
            @ParameterObject @PageableDefault(page = 0, size = 20, sort = "createdAt", direction = Sort.Direction.DESC) Pageable pageable,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid
    ) {
        PageRequest pageRequest = PageRequest.of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort()
        );

        return ApiResponse.success(fileService.queryFilesByType(FileType.from(fileType), pageRequest, uid));
    }

    @PostMapping("/rename")
    @Operation(summary = "重命名文件", description = "重命名文件，允许文件名称重复")
    public ApiResponse<Boolean> renameFile(
            @RequestBody FileRenameInput input,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.renameFile(input.getId(), input.getName(), uid));
    }

    @PostMapping("/delete")
    @Operation(summary = "批量删除文件(软删除)", description = "将多个文件标记为删除状态")
    public ApiResponse<Integer> deleteFiles(
            @RequestBody @Validated FilesDeleteRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.deleteFiles(request.getFileIds(), uid));
    }

    @PostMapping("/delete/physical")
    @Operation(summary = "批量删除文件(物理删除)", description = "永久删除文件记录，不可恢复")
    public ApiResponse<Integer> deleteFilesPhysically(
            @RequestBody @Validated FilesDeleteRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.deleteFilesPhysically(request.getFileIds(), uid));
    }

    @PostMapping("/recover")
    @Operation(summary = "批量恢复文件", description = "将多个被删除的文件恢复至原位置，恢复文件前请确保文件所在目录未被删除")
    public ApiResponse<Integer> recoverFiles(
            @RequestBody @Validated FilesRecoverRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.recoverFiles(request.getFileIds(), request.getTargetDirectoryId(), uid));
    }

    @PostMapping("/copy")
    @Operation(summary = "批量复制文件到指定目录", description = "将多个文件复制到目标目录，复制后的文件与原文件共享相同的存储数据，不额外占用存储空间")
    public ApiResponse<Integer> copyFiles(
            @RequestBody @Validated FilesCopyRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid) {
        return ApiResponse.success(fileService.copyFiles(request.getFileIds(), request.getTargetDirectoryId(), uid));
    }

    @PostMapping("/move")
    @Operation(summary = "批量移动文件至指定目录", description = "将多个文件移动至目标目录")
    public ApiResponse<Integer> moveFiles(
            @RequestBody @Validated FilesMoveRequest request,
            @Parameter(description = "用户 ID") @RequestHeader("UID") UUID uid
    ) {
        return ApiResponse.success(fileService.moveFiles(request.getFileIds(), request.getTargetDirectoryId(), uid));
    }
}
