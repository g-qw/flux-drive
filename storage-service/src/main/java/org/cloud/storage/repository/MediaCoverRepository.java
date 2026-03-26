package org.cloud.storage.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.cloud.storage.dto.MediaCoverInput;
import org.cloud.storage.entity.MediaCover;
import org.cloud.storage.entity.MediaCoverTable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class MediaCoverRepository extends AbstractJavaRepository<MediaCover, Long> {
    private static final MediaCoverTable table = MediaCoverTable.$;

    public MediaCoverRepository(JSqlClient sql) {
        super(sql);
    }

    public MediaCover create(MediaCoverInput input) {
        return sql.saveCommand(input)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public List<MediaCover> listByFileIds(List<UUID> fileIds, UUID userId) {
        return sql.createQuery(table)
                .where(table.fileId().in(fileIds))
                .where(table.userId().eq(userId))
                .orderBy(table.updatedAt())
                .select(table)
                .execute();
    }

    public List<MediaCover> listByFileHashes(List<String> fileHashes, UUID userId) {
        return sql.createQuery(table)
                .where(table.md5().in(fileHashes))
                .where(table.userId().eq(userId))
                .orderBy(table.updatedAt())
                .select(table)
                .execute();
    }
}
