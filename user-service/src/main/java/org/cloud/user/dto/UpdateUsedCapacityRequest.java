package org.cloud.user.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.math.BigDecimal;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class UpdateUsedCapacityRequest implements Serializable {
    /**
     * 用户ID
     */
    @NotBlank(message = "用户ID不能为空")
    private String userId;

    /**
     * 已使用的存储空间，单位字节 (byte)
     */

    @NotNull(message = "已使用存储池空间不能为空")
    @DecimalMin(value = "0", message = "已使用存储空间不能为负数")
    private BigDecimal usedCapacity;
}
