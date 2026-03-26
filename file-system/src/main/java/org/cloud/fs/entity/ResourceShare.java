package org.cloud.fs.entity;

import org.babyfish.jimmer.sql.*;
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator;
import org.jspecify.annotations.Nullable;

import java.util.List;
import java.util.UUID;

@Entity
public interface ResourceShare {
    @Id
    @GeneratedValue(generatorType = UUIDIdGenerator.class)
    UUID id();

    UUID ownerId();

    @Key
    String code();

    @Nullable
    String accessType();

    @Nullable
    Long expireAt();

    boolean requirePassword();

    @Nullable
    String passwordHash();

    @Nullable
    String status();

    String title();

    String description();

    Long createdAt();

    Long updatedAt();

    @OneToMany(mappedBy = "share")
    List<ShareItem> files();

    @OneToMany(mappedBy = "share")
    List<ShareAccess> whitelist();
}
