package org.cloud.fs.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.cloud.fs.dto.ApiResponse;
import org.cloud.fs.exception.*;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.servlet.NoHandlerFoundException;
import org.springframework.web.servlet.resource.NoResourceFoundException;

import java.util.stream.Collectors;

@Slf4j
@RestControllerAdvice
public class GlobalExceptionHandler {
    @ExceptionHandler(AccessDeniedException.class)
    public ApiResponse<Void> handleAccessDeniedException(AccessDeniedException e) {
        return ApiResponse.failure(403, "拒绝访问");
    }

    @ExceptionHandler(DuplicateFileNameException.class)
    public ApiResponse<Void> handleDuplicateFileNameException(DuplicateFileNameException e) {
        if(e.getName() != null) {
            return ApiResponse.failure(409, String.format("文件 '%s' 已存在", e.getName()));
        }


        return ApiResponse.failure(409, "操作失败，文件名称存在冲突");
    }

    @ExceptionHandler(DuplicateDirectoryNameException.class)
    public ApiResponse<Void> handleDuplicateDirectoryNameException(DuplicateDirectoryNameException e) {
        if(e.getName() != null) {
            return ApiResponse.failure(409, String.format("目录 '%s' 已存在", e.getName()));
        }

        return ApiResponse.failure(409, "操作失败，目录名称存在冲突");
    }

    @ExceptionHandler(InvalidDirectoryNameException.class)
    public ApiResponse<Void> handleInvalidDirectoryNameException(InvalidDirectoryNameException e) {
        if(e.isLengthExceeded()) {
            return ApiResponse.failure(400, "目录名称长度超过 1024 字符");
        }

        if (e.getIllegalChars() != null) {
            return ApiResponse.failure(400, String.format("目录名称不能包含字符 '%s'", e.getIllegalChars()));
        }

        return ApiResponse.failure(400, "无效的目录名称");
    }

    @ExceptionHandler(InvalidFileNameException.class)
    public ApiResponse<Void> handleInvalidFileNameException(InvalidFileNameException e) {
        return ApiResponse.failure(400, "无效的文件名");
    }

    @ExceptionHandler(ResourceNotFoundException.class)
    public ApiResponse<Void> handleResourceNotFoundException(ResourceNotFoundException e) {
        return ApiResponse.failure(404, String.format("路径 '%s' 不存在", e.getPath()));
    }

    @ExceptionHandler(DirectoryCircularDependencyException.class)
    public ApiResponse<Void> handleDirectoryCircularDependencyException(DirectoryCircularDependencyException e) {
        return ApiResponse.failure(400, "移动失败，目标位置位于被移动目录的内部");
    }

    // 参数校验异常
    @ExceptionHandler({MethodArgumentNotValidException.class})
    public ApiResponse<Void> handleValidationException(MethodArgumentNotValidException e) {
        String message = e.getBindingResult().getFieldErrors().stream()
                .map(FieldError::getDefaultMessage)
                .collect(Collectors.joining(", "));
        return ApiResponse.failure(400, "参数错误: " + message);
    }

    // 处理路径不存在（404）
    @ExceptionHandler({NoHandlerFoundException.class, NoResourceFoundException.class})
    public ApiResponse<Void> handleNotFoundException(Exception e, HttpServletRequest request) {
        String path = "Unknown";
        if (e instanceof NoResourceFoundException) {
            NoResourceFoundException ex = (NoResourceFoundException) e;
            path = ex.getResourcePath();
        } else if (e instanceof NoHandlerFoundException) {
            path = ((NoHandlerFoundException) e).getRequestURL();
        }

        String msg = String.format("请求路径不存在: %s %s", request.getMethod(), path);
        log.warn(msg);
        return ApiResponse.failure(404, msg);
    }

    // 兜底处理
    @ExceptionHandler(Exception.class)
    public ApiResponse<Void> handleException(Exception e, HttpServletRequest request) {
        log.error("系统异常 - {} {}", request.getMethod(), request.getRequestURI(), e);
        return ApiResponse.failure(500, "系统繁忙，请稍后重试");
    }
}
