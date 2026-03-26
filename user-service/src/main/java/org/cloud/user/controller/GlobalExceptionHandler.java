package org.cloud.user.controller;

import jakarta.servlet.http.HttpServletRequest;
import lombok.extern.slf4j.Slf4j;
import org.cloud.user.dto.ApiResponse;
import org.cloud.user.exception.BusinessException;
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
    // 业务异常
    @ExceptionHandler(BusinessException.class)
    public ApiResponse<Void> handleBusinessException(BusinessException e) {
        return ApiResponse.failure(e.getCode(), e.getMessage());
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
