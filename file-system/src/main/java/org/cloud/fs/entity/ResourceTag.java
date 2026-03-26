package org.cloud.fs.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;

import java.time.LocalDateTime;

@Entity
public interface ResourceTag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    long tagId();

    String resourceType();

    String resourceId();

    String userId();

    Boolean visibility();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime createdAt();
}
