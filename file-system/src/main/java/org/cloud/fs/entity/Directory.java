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
    @OnDissociate(DissociateAction.LAX) // // 父目录删除时，不处理
    @Nullable
    Directory parent();

    @OneToMany(mappedBy = "parent", orderedProps = @OrderedProp("name"))
    @OnDissociate(DissociateAction.DELETE) // 删除目录时级联删除子目录
    List<Directory> directories();

    @OneToMany(mappedBy = "directory", orderedProps = @OrderedProp("name"))
    @OnDissociate(DissociateAction.DELETE) // 删除目录时级联删除文件
    List<File> files();
}

