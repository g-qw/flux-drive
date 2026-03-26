package org.cloud.fs.entity;

import org.babyfish.jimmer.sql.*;

import java.util.UUID;

@Entity
public interface ShareItem {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    @IdView
    UUID shareId();

    String resourceType();

    UUID resourceId();

    @ManyToOne
    @JoinColumn(name = "share_id")
    ResourceShare share();
}
