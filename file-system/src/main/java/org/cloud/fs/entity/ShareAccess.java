package org.cloud.fs.entity;

import org.babyfish.jimmer.sql.*;

import java.util.UUID;

@Entity
public interface ShareAccess {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    long id();

    @IdView
    UUID shareId();

    UUID userId();

    String username();

    @ManyToOne
    @JoinColumn(name = "share_id")
    ResourceShare share();
}
