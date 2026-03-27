package org.cloud.fs.entity;

import org.babyfish.jimmer.sql.*;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator;

import java.util.UUID;

@Entity
public interface File {
    @Id
    @GeneratedValue(generatorType = UUIDIdGenerator.class)
    UUID id();

    @IdView
    UUID directoryId();

    UUID userId();

    String bucket();

    String storageKey();

    String name();

    String mimeType();

    long size();

    String md5();

    @LogicalDeleted(generatorType = LogicalDeletedLongGenerator.class)
    Long deletedAt();

    Long createdAt();

    Long updatedAt();

    @ManyToOne
    @JoinColumn(name = "directory_id")
    Directory directory();
}