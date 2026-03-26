package org.cloud.fs.entity;

import com.fasterxml.jackson.annotation.JsonFormat;
import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;

import java.time.LocalDateTime;

@Entity
public interface Tag {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    String userId();

    String name();

    String color();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime createdAt();

    @JsonFormat(pattern = "yyyy-MM-dd HH:mm:ss")
    LocalDateTime updatedAt();
}
