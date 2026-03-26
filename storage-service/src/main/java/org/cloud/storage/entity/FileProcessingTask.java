package org.cloud.storage.entity;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;
import org.jspecify.annotations.Nullable;

import java.util.UUID;

@Entity
public interface FileProcessingTask {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    UUID fileId();

    UUID userId();

    String md5();

    String bucket();

    String storageKey();

    String taskType();

    @Nullable
    Boolean processed();

    @Nullable
    Long createdAt();

    @Nullable
    Long updatedAt();
}
