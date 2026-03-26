package org.cloud.fs.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.cloud.fs.entity.ShareItem;
import org.cloud.fs.entity.ShareItemDraft;
import org.cloud.fs.entity.ShareItemTable;
import org.cloud.fs.entity.enums.ResourceType;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class ShareItemRepository extends AbstractJavaRepository<ShareItem, Long> {
    private static final ShareItemTable table = ShareItemTable.$;

    public ShareItemRepository(JSqlClient sql) {
        super(sql);
    }

    public int addBatch(List<UUID> fileIds, UUID shareId, ResourceType resourceType) {
        List<ShareItem> items = fileIds.stream().map(
                fileId -> ShareItemDraft.$.produce(
                        draft -> {
                            draft.setShareId(shareId);
                            draft.setResourceType(resourceType.getValue());
                            draft.setResourceId(fileId);
                        }
                )
        ).toList();

        return sql.saveEntitiesCommand(items)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getAffectedRowCount(ShareItem.class);
    }

    public List<UUID> listResourceIds(UUID shareId, ResourceType resourceType) {
        return sql.createQuery(table)
                .where(table.shareId().eq(shareId))
                .where(table.resourceType().eq(resourceType.getValue()))
                .select(table.resourceId())
                .execute();
    }
}
