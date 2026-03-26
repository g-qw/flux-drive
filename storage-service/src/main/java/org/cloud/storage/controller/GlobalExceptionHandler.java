package org.cloud.storage.controller;

import javassist.NotFoundException;
import lombok.extern.slf4j.Slf4j;
import org.cloud.storage.dto.ApiResponse;
import org.cloud.storage.exception.AvatarUploadException;
import org.cloud.storage.exception.BatchZipDownloadException;
import org.cloud.storage.exception.FileRangeDownloadException;
import org.cloud.storage.exception.MultipartUploadException;
import org.springframework.core.annotation.Order;
import org.springframework.http.server.reactive.ServerHttpRequest;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ControllerAdvice;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.server.ServerWebExchange;
import reactor.core.publisher.Mono;

import java.util.stream.Collectors;

@Slf4j
@ControllerAdvice
@Order(-2) // // 优先级高于 Spring 默认的 ErrorWebExceptionHandler
public class GlobalExceptionHandler {
    @ExceptionHandler(MultipartUploadException.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleMultipartUploadException(MultipartUploadException e) {
        return Mono.just(ApiResponse.failure(500, e.getMessage()));
    }

    @ExceptionHandler(FileRangeDownloadException.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleFileRangeDownloadException(FileRangeDownloadException e) {
        return Mono.just(ApiResponse.failure(500, e.getMessage()));
    }


    @ExceptionHandler(BatchZipDownloadException.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleBatchZipDownloadExceptions(BatchZipDownloadException e) {
        return Mono.just(ApiResponse.failure(500, e.getMessage()));
    }

    @ExceptionHandler(AvatarUploadException.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handle(AvatarUploadException e) {
        return Mono.just(ApiResponse.failure(500, e.getMessage()));
    }

    // 参数校验异常
    @ExceptionHandler({MethodArgumentNotValidException.class})
    @ResponseBody
    public Mono<ApiResponse<Void>> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return Mono.just(ApiResponse.failure(400, "参数错误: " + message));
    }

    // 处理路径不存在（404）
    @ExceptionHandler(NotFoundException.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleNotFoundException(NotFoundException ex, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        log.warn("请求路径不存在 - {} {}", request.getMethod(), request.getURI().getPath());
        return Mono.just(ApiResponse.failure(404, ex.getMessage()));
    }

    @ExceptionHandler(Exception.class)
    @ResponseBody
    public Mono<ApiResponse<Void>> handleGenericException(Exception e, ServerWebExchange exchange) {
        ServerHttpRequest request = exchange.getRequest();
        log.error("系统异常 - {} {}", request.getMethod(), request.getURI().getPath(), e);
        return Mono.just(ApiResponse.failure(500, "系统繁忙，请稍后重试"));
    }
}
