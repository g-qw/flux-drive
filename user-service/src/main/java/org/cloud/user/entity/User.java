package org.cloud.user.entity;

import org.babyfish.jimmer.sql.*;
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator;
import org.jspecify.annotations.Nullable;

import java.math.BigDecimal;
import java.util.UUID;

@Entity
@Table(name = "users")
public interface User {
    @Id
    @GeneratedValue(generatorType = UUIDIdGenerator.class)
    UUID id();

    @Key
    String email();

    @Key
    String username();

    String pwd();

    @Nullable
    UUID rootDir();

    @Column(name = "created_at")
    Long createdAt();

    @Nullable
    Long lastLoginAt();

    String userStatus();

    String userRole();

    Long storageCapacity();
    Long usedCapacity();

    @Nullable
    String bio();

    @Nullable
    String avatar();
}
