package org.cloud.storage.entity;

import org.babyfish.jimmer.sql.Entity;
import org.babyfish.jimmer.sql.GeneratedValue;
import org.babyfish.jimmer.sql.GenerationType;
import org.babyfish.jimmer.sql.Id;

import java.util.UUID;

@Entity
public interface MediaCover {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    UUID fileId();

    UUID userId();

    String md5();

    String bucket();

    String storageKey();

    Long size();

    Integer width();

    Integer height();

    Long createdAt();

    Long updatedAt();
}
