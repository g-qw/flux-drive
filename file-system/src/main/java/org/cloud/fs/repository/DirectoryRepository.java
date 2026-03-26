package org.cloud.fs.repository;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.DeleteMode;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.ast.query.MutableRootQuery;
import org.babyfish.jimmer.sql.runtime.LogicalDeletedBehavior;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.*;
import org.cloud.fs.exception.DuplicateDirectoryNameException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.*;
import java.util.stream.Collectors;

@Repository
public class DirectoryRepository extends AbstractJavaRepository<Directory, UUID> {
    private static final DirectoryTable table = DirectoryTable.$;

    public DirectoryRepository(JSqlClient sqlClient) {
        super(sqlClient);
    }

    public Directory createRootDirectory(UUID id, UUID userId) {
        long now = System.currentTimeMillis();
        Directory root = DirectoryDraft.$.produce(
                draft -> {
                    draft.setId(id);
                    draft.setUserId(userId);
                    draft.setParentId(null);
                    draft.setName("/");
                    draft.setDeletedAt(null);
                    draft.setCreatedAt(now);
                    draft.setUpdatedAt(now);
                    draft.setSize(0L);
                }
        );

        return sql.saveCommand(root)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public Directory createDirectory(UUID userId, UUID parentId, String name) {
        long now = System.currentTimeMillis();
        Directory newDirectory = DirectoryDraft.$.produce(
                draft -> {
                    draft.setUserId(userId);
                    draft.setParentId(parentId);
                    draft.setName(name);
                    draft.setDeletedAt(null);
                    draft.setCreatedAt(now);
                    draft.setUpdatedAt(now);
                    draft.setSize(0L);
                }
        );

        return sql.saveCommand(newDirectory)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public int deleteDirectories(List<UUID> ids) {
        // 逻辑删除
        return sql.createDelete(table)
                .where(table.id().in(ids))
                .execute();
    }

    public int deleteDirectoriesPhysically(List<UUID> ids) {
        return sql.deleteByIds(Directory.class, ids, DeleteMode.PHYSICAL)
                .getAffectedRowCount(Directory.class);
    }

    /**
     * 批量恢复目录
     */
    public int recoverDirectories(List<UUID> ids) {
        return sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                .createUpdate(table)
                .set(table.deletedAt(), (Long) null)
                .where(table.id().in(ids))
                .execute();
    }

    public int renameDirectory(UUID id, String name, UUID userId) {
        UUID parentId = sql.createQuery(table)
                .where(table.id().eq(id))
                .select(table.parentId())
                .fetchOne();

        // 检查名称冲突
        boolean isExists = sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .where(table.name().eq(name))
                .exists();
        if(isExists) {
            throw new DuplicateDirectoryNameException(name);
        }

        return sql.createUpdate(table)
                .set(table.name(), name)
                .where(table.id().eq(id))
                .where(table.userId().eq(userId))
                .execute();
    }

    public void updateDirectorySize(UUID id, Long size) {
        sql.createUpdate(table)
                .set(table.size(), size)
                .where(table.id().eq(id))
                .execute();
    }

    public int moveDirectories(List<UUID> ids, UUID parentId, UUID userId) {
        return sql.createUpdate(table)
                .set(table.parentId(), parentId)
                .where(table.id().in(ids))
                .where(table.userId().eq(userId))
                .execute();
    }

    public boolean notOwns(UUID userId, UUID directoryId) {
        return !sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                .createQuery(table)
                .where(table.id().eq(directoryId))
                .where(table.userId().eq(userId))
                .exists();
    }

    public boolean notOwns(UUID userId, List<UUID> directoryIds) {
        return sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                .createQuery(table)
                .where(table.id().in(directoryIds))
                .where(table.userId().ne(userId))
                .exists();
    }

    public void validateDirectoryNames(List<UUID> ids, UUID parentId) {
        List<String> duplicateDirectoryNames = sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .where(
                        table.name().in(
                                sql.createSubQuery(table)
                                        .where(table.id().in(ids))
                                        .select(table.name())
                        )
                )
                .select(table.name())
                .execute();

        if(!duplicateDirectoryNames.isEmpty()) {
            throw new DuplicateDirectoryNameException(duplicateDirectoryNames.getFirst());
        }
    }

    public Long getUpdatedAt(UUID id) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .select(table.updatedAt())
                .fetchOneOrNull();
    }

    public boolean isDirectoryExist(UUID parentId, String name) {
        return sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .where(table.name().eq(name))
                .exists();
    }

    public Long sumSizeByDirectoryId(UUID parentId) {
        Long totalSize = sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .select(table.size().sum())
                .fetchOneOrNull();

        return totalSize != null ? totalSize : 0L;
    }

    public Directory getRootDirectory(UUID userId) {
        return sql.createQuery(table)
                .where(table.userId().eq(userId))
                .where(table.parentId().isNull())
                .select(
                        table.fetch(
                                DirectoryFetcher.$.allScalarFields()
                                        .parent(false)
                                        .directories(false)
                                        .files(false)
                        )
                ).fetchOneOrNull();
    }

    public List<DirectoryInfoView> getDirectoryPathChain(UUID directoryId, UUID userId) {
        List<DirectoryInfoView> pathNodes = new LinkedList<>();

        // 查询起始目录（当前目录）
        DirectoryInfoView currentDirectory = sql.createQuery(table)
                .where(table.id().eq(directoryId))
                .where(table.userId().eq(userId))
                .select(
                        table.fetch(DirectoryInfoView.class)
                )
                .fetchOneOrNull();

        if (currentDirectory == null) {
            return List.of();
        }

        // 自关联向上遍历父目录链
        pathNodes.addFirst(currentDirectory);
        for (int depth = 0; depth < 64; depth++) {
            DirectoryInfoView currentNode = pathNodes.getFirst();

            // 到达根目录（parentId 为 null），停止遍历
            if (currentNode.getParentId() == null) {
                break;
            }

            // 查询父目录
            DirectoryInfoView parentDirectory = sql.createQuery(table)
                    .where(table.id().eq(currentNode.getParentId()))
                    .select(
                            table.fetch(DirectoryInfoView.class)
                    )
                    .fetchOne();

            // 父目录不存在时中断（数据一致性异常）
            if (parentDirectory == null) {
                break;
            }

            pathNodes.addFirst(parentDirectory);
        }

        return pathNodes;
    }

    public List<DirectoryInfoView> getDirectoryPathChain(List<UUID> ids) {
        List<DirectoryInfoView> nodes = sql.createQuery(table)
                .where(table.id().in(ids))
                .select(
                        table.fetch(DirectoryInfoView.class)
                ).execute();

        Map<UUID, DirectoryInfoView> nodeMap = nodes.stream()
                .collect(Collectors.toMap(DirectoryInfoView::getId, directory -> directory));

        return ids.stream().map(nodeMap::get).toList();
    }

    public String getDirectoryPath(UUID directoryId, UUID userId) {
        List<Directory> pathNodes = new LinkedList<>();

        // 查询起始目录（当前目录）
        Directory currentDirectory = sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED))
                .createQuery(table)
                .where(table.id().eq(directoryId))
                .where(table.userId().eq(userId))
                .select(
                        table.fetch(
                                DirectoryFetcher.$.name()
                                        .parentId()
                                        .parent(false)
                        )
                )
                .fetchOneOrNull();

        if (currentDirectory == null) {
            return "";
        }

        // 自关联向上遍历父目录链
        pathNodes.addFirst(currentDirectory);
        for (int depth = 0; depth < 64; depth++) {
            Directory currentNode = pathNodes.getFirst();

            // 到达根目录（parentId 为 null），停止遍历
            if (currentNode.parentId() == null) {
                break;
            }

            // 查询父目录
            Directory parentDirectory = sql.createQuery(table)
                    .where(table.id().eq(currentNode.parentId()))
                    .select(
                            table.fetch(
                                    DirectoryFetcher.$.name()
                                            .parentId()
                                            .parent(false)
                            )
                    )
                    .fetchOne();

            // 父目录不存在时中断（数据一致性异常）
            if (parentDirectory == null) {
                break;
            }

            pathNodes.addFirst(parentDirectory);
        }

        StringBuilder sb = new StringBuilder("/");
        for(int i = 1; i < pathNodes.size(); i++) {
            sb.append(pathNodes.get(i).name());
            sb.append("/");
        }

        return sb.toString();
    }

    /**
     * 获取指定目录的所有后代目录 id
     */
    public List<UUID> listDescendantIds(List<UUID> ancestorIds) {
        List<UUID> result = new ArrayList<>();

        // 层序遍历
        Queue<UUID> queue = new LinkedList<>(ancestorIds);
        while (!queue.isEmpty()) {
            UUID currentId = queue.poll();

            List<UUID> childIds = sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                    .createQuery(table)
                    .where(table.parentId().eq(currentId))
                    .select(table.id())
                    .execute();

            if (!childIds.isEmpty()) {
                queue.addAll(childIds);
                result.addAll(childIds);
            }
        }

        return result;
    }

    public Directory getDirectoryById(UUID id, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .where(table.userId().eq(userId))
                .select(
                        table.fetch(
                                DirectoryFetcher.$.allScalarFields()
                                        .parentId()
                                        .parent(false)
                                        .directories(false)
                                        .files(false)
                        )
                ).fetchOneOrNull();
    }

    public Directory getDirectoryByName(UUID parentId, String name) {
        return sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .where(table.name().eq(name))
                .select(
                        table.fetch(
                                DirectoryFetcher.$.allScalarFields()
                                    .parentId()
                                    .parent(false)
                                    .directories(false)
                                    .files(false)
                        )
                ).fetchOneOrNull();
    }

    public DirectoryView getDirectoryView(UUID id, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().eq(id))
                .where(table.userId().eq(userId))
                .select(table.fetch(DirectoryView.class))
                .fetchOneOrNull();
    }

    public List<DirectoryNodeView> listDirectoryNodeView(UUID parentId, UUID userId) {
        return sql.createQuery(table)
                .where(table.parentId().eq(parentId))
                .where(table.userId().eq(userId))
                .select(table.fetch(DirectoryNodeView.class))
                .execute();
    }

    public Page<Directory> queryDirectories(PageRequest pageRequest, DirectorySpecification specification, UUID userId) {
        MutableRootQuery<DirectoryTable> query = sql.createQuery(table)
                .where(table.userId().eq(userId))
                .where(specification);

        pageRequest.getSort().forEach(order -> {
            if (order.isAscending()) {
                query.orderBy(table.get(order.getProperty()).asc());
            } else {
                query.orderBy(table.get(order.getProperty()).desc());
            }
        });

        return query.select(
                table.fetch(
                        DirectoryFetcher.$.allScalarFields()
                                .parentId()
                                .parent(false)
                                .directories(false)
                                .files(false)
                )
        ).fetchPage(
                pageRequest.getPageNumber(),
                pageRequest.getPageSize()
        );
    }

    public Page<DeletedDirectoryView> queryDeletedDirectories(PageRequest pageRequest, UUID userId) {
        MutableRootQuery<DirectoryTable> query = sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED))
                .createQuery(table)
                .where(table.userId().eq(userId))
                .where(table.deletedAt().isNotNull());

        pageRequest.getSort().forEach(order -> {
            if (order.isAscending()) {
                query.orderBy(table.get(order.getProperty()).asc());
            } else {
                query.orderBy(table.get(order.getProperty()).desc());
            }
        });

        return query.select(
                table.fetch(DeletedDirectoryView.class)
        ).fetchPage(
                pageRequest.getPageNumber(),
                pageRequest.getPageSize()
        );
    }
}
