package org.cloud.fs.entity;

import jakarta.annotation.Nullable;
import org.babyfish.jimmer.sql.*;
import org.babyfish.jimmer.sql.meta.LogicalDeletedLongGenerator;
import org.babyfish.jimmer.sql.meta.UUIDIdGenerator;

import java.util.List;
import java.util.UUID;

@Entity
public interface Directory {
    @Id
    @GeneratedValue(generatorType = UUIDIdGenerator.class)
    UUID id();

    @IdView
    @Nullable
    UUID parentId();

    UUID userId();

    String name();

    @LogicalDeleted(generatorType = LogicalDeletedLongGenerator.class)
    Long deletedAt();

    Long createdAt();

    Long updatedAt();

    Long size();

    @ManyToOne
    @JoinColumn(name = "parent_id")
    @OnDissociate(DissociateAction.LAX)
    @Nullable
    Directory parent();

    @OneToMany(mappedBy = "parent", orderedProps = @OrderedProp("name"))
    List<Directory> directories();

    @OneToMany(mappedBy = "directory", orderedProps = @OrderedProp("name"))
    List<File> files();
}

