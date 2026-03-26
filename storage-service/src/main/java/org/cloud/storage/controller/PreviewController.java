package org.cloud.storage.controller;

import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.cloud.storage.dto.*;
import org.cloud.storage.service.PreviewService;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/storage/preview")
@RequiredArgsConstructor
@Tag(name = "媒体封面 API", description = "支持批量获取媒体封面")
public class PreviewController {
    private final PreviewService previewService;

    @PostMapping("/covers")
    @Operation(summary = "批量获取媒体封面")
    ApiResponse<List<MediaCoverDTO>> getMediaCovers(
            @RequestBody MediaCoverRequest request,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return ApiResponse.success(previewService.getMediaCovers(request.getFileHashes(), uid));
    }

    @PostMapping("/files")
    @Operation(summary = "批量获取文件访问预签名URL")
    ApiResponse<List<PreviewDTO>> getPreviews(
            @RequestBody PreviewRequest request,
            @Parameter(description = "用户 ID") @RequestHeader(value = "UID") UUID uid) {
        return ApiResponse.success(previewService.getPreviews(request.getFileIds(), uid));
    }
}
