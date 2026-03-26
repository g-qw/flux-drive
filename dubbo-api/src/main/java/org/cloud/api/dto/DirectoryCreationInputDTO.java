package org.cloud.api.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.io.Serial;
import java.io.Serializable;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class DirectoryCreationInputDTO implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    private String parentId;

    private String name;
}
