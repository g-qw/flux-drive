package org.cloud.storage.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import io.swagger.v3.oas.annotations.media.Schema;
import lombok.Getter;
import lombok.Setter;

@Setter
@Getter
@Schema(description = "统一响应包装")
public class ApiResponse<T> {
    @JsonProperty("code")
    @Schema(description = "状态码", example = "200")
    private int code;

    @JsonProperty("msg")
    @Schema(description = "消息", example = "操作成功")
    private String msg;

    @JsonProperty("data")
    @Schema(description = "返回的数据载体")
    private T data;

    public ApiResponse(int code) {
        this.code = code;
    }

    public ApiResponse(int code, String msg) {
        this.code = code;
        this.msg = msg;
    }

    public ApiResponse(int code, String msg, T data) {
        this.code = code;
        this.msg = msg;
        this.data = data;
    }

    public static <T> ApiResponse<T> success() {
        return new ApiResponse<>(200);
    }

    public static <T> ApiResponse<T> success(T data) {
        return new ApiResponse<>(200, "操作成功", data);
    }

    public static <T> ApiResponse<T> failure(int code, String msg) {
        return new ApiResponse<>(code, msg);
    }
}
