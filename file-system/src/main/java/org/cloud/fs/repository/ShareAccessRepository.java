package org.cloud.fs.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.cloud.fs.entity.ShareAccess;
import org.cloud.fs.entity.ShareAccessDraft;
import org.cloud.fs.entity.ShareAccessTable;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ShareAccessRepository extends AbstractJavaRepository<ShareAccess, Long> {
    public static final ShareAccessTable table = ShareAccessTable.$;

    public ShareAccessRepository(JSqlClient sql) {
        super(sql);
    }

    public int addBatch(List<UUID> userIds, UUID shareId) {
        List<ShareAccess> whitelist = userIds.stream().map(
                userId -> ShareAccessDraft.$.produce(
                            draft -> {
                                draft.setShareId(shareId);
                                draft.setUserId(userId);
                            }
                    )

        ).toList();

        return sql.saveEntitiesCommand(whitelist)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getAffectedRowCount(ShareAccess.class);
    }

    public List<UUID> getWhiteList(UUID shareId) {
        return sql.createQuery(table)
                .where(table.shareId().eq(shareId))
                .select(table.userId())
                .execute();
    }

    public boolean hasAccess(UUID shareId, UUID userId) {
        return sql.createQuery(table)
                .where(table.shareId().eq(shareId))
                .where(table.userId().eq(userId))
                .exists();
    }
}
