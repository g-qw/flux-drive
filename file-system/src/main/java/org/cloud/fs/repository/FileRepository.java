package org.cloud.fs.repository;

import org.babyfish.jimmer.Page;
import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.babyfish.jimmer.sql.ast.query.MutableRootQuery;
import org.babyfish.jimmer.sql.runtime.LogicalDeletedBehavior;
import org.cloud.fs.dto.*;
import org.cloud.fs.entity.enums.FileType;
import org.cloud.fs.entity.File;
import org.cloud.fs.entity.FileDraft;
import org.cloud.fs.entity.FileFetcher;
import org.cloud.fs.entity.FileTable;
import org.cloud.fs.exception.AccessDeniedException;
import org.cloud.fs.exception.DuplicateFileNameException;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public class FileRepository extends AbstractJavaRepository<File, UUID> {
    private static final FileTable table = FileTable.$;

    private static final List<String> DOCUMENT_MIME_TYPES = List.of(
            "application/pdf",
            "application/msword",
            "application/vnd.openxmlformats-officedocument.wordprocessingml.document",
            "application/vnd.ms-excel",
            "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet",
            "application/vnd.ms-powerpoint",
            "application/vnd.openxmlformats-officedocument.presentationml.presentation",
            "text/plain",
            "text/csv",
            "application/rtf"
    );

    public FileRepository(JSqlClient sqlClient) {
        super(sqlClient);
    }

    public File createFile(FileInput input, UUID userId) {
        long now = System.currentTimeMillis();
        File file = FileDraft.$.produce(
                draft -> {
                    draft.setId(UUID.randomUUID());
                    draft.setUserId(userId);
                    draft.setDirectoryId(input.getDirectoryId());
                    draft.setBucket(input.getBucket());
                    draft.setStorageKey(input.getStorageKey());
                    draft.setName(input.getName());
                    draft.setMimeType(input.getMimeType());
                    draft.setSize(input.getSize());
                    draft.setMd5(input.getMd5());
                    draft.setDeletedAt(null);
                    draft.setCreatedAt(now);
                    draft.setUpdatedAt(now);
                }
        );

        return sql.saveCommand(file)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public int renameFile(UUID fileId, String name, UUID userId) {
        // 检查文件名称是否重复
        boolean exists = sql.createQuery(table)
                .where(
                        table.directoryId().eq(
                                sql.createSubQuery(table)
                                        .where(table.id().eq(fileId))
                                        .select(table.directoryId())
                        )
                )
                .where(table.name().eq(name))
                .exists();
        if (exists) {
            throw new DuplicateFileNameException(name);
        }

        return sql.createUpdate(table)
                .set(table.name(), name)
                .where(table.id().eq(fileId))
                .where(table.userId().eq(userId))
                .execute();
    }

    public int deleteFiles(List<UUID> fileIds, UUID userId) {
        return sql.createDelete(table)
                .where(table.id().in(fileIds))
                .where(table.userId().eq(userId))
                .execute();
    }

    public int deleteFilesByDirectoryIds(List<UUID> directoryIds) {
        return sql.createDelete(table)
                .where(table.directoryId().in(directoryIds))
                .execute();
    }

    public int recoverFiles(List<UUID> fileIds, UUID directoryId, UUID userId) {
        if(directoryId != null) {
            return sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                    .createUpdate(table)
                    .set(table.deletedAt(), (Long) null)
                    .set(table.directoryId(), directoryId)
                    .where(table.id().in(fileIds))
                    .where(table.userId().eq(userId))
                    .execute();
        } else {
            return sql.createUpdate(table)
                    .set(table.deletedAt(), (Long) null)
                    .where(table.id().in(fileIds))
                    .where(table.userId().eq(userId))
                    .execute();
        }
    }

    public void recoverFilesByDirectoryIds(List<UUID> directoryIds) {
        sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED)) // 忽略逻辑删除处理器
                .createUpdate(table)
                .set(table.deletedAt(), (Long) null)
                .where(table.directoryId().in(directoryIds))
                .execute();
    }

    public int copyFiles(List<UUID> fileIds, UUID directoryId, UUID useId) {
        List<File> metas = sql.createQuery(table)
                .where(table.id().in(fileIds))
                .where(table.userId().eq(useId))
                .select(table.fetch(FileFetcher.$.allScalarFields()))
                .execute();
        if(metas.size() != fileIds.size()) {
            throw new AccessDeniedException();
        }

        long now = System.currentTimeMillis();
        List<File> drafts = metas.stream()
                .map(meta -> FileDraft.$.produce(meta, draft -> {
                    draft.setId(UUID.randomUUID());
                    draft.setDirectoryId(directoryId);
                    draft.setCreatedAt(now);
                    draft.setUpdatedAt(now);
                })).toList();

        return sql.saveEntitiesCommand(drafts)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getAffectedRowCount(File.class);
    }

    public int moveFiles(List<UUID> fileIds, UUID directoryId, UUID userId) {
        return sql.createUpdate(table)
                    .set(table.directoryId(), directoryId)
                    .where(table.id().in(fileIds))
                    .where(table.userId().eq(userId))
                    .execute();
    }

    public boolean notOwns(UUID userId, List<UUID> fileIds) {
        return sql.createQuery(table)
                .where(table.id().in(fileIds))
                .where(table.userId().ne(userId))
                .exists();
    }

    public void validateFileNames(List<UUID> fileIds, UUID directoryId) {
        // 检查文件名称是否冲突
        List<String> duplicateNames = sql.createQuery(table)
                .where(table.directoryId().eq(directoryId))
                .where(
                        table.name().in(
                                sql.createSubQuery(table)
                                        .where(table.id().in(fileIds))
                                        .select(table.name())
                        )
                )
                .select(table.name())
                .execute();

        if (!duplicateNames.isEmpty()) {
            throw new DuplicateFileNameException(duplicateNames.getFirst());
        }
    }

    public FileView getFileViewById(UUID fileId, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().eq(fileId))
                .where(table.userId().eq(userId))
                .select(table.fetch(FileView.class))
                .fetchOneOrNull();
    }

    public FileRpcView getFileRpcViewById(UUID fileId, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().eq(fileId))
                .where(table.userId().eq(userId))
                .select(table.fetch(FileRpcView.class))
                .fetchOneOrNull();
    }

    public List<FileRpcView> listFileRpcView(List<UUID> fileIds, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().in(fileIds))
                .where(table.userId().eq(userId))
                .select(table.fetch(FileRpcView.class))
                .execute();
    }

    public List<FileView> listFileView(List<UUID> fileIds, UUID userId) {
        return sql.createQuery(table)
                .where(table.id().in(fileIds))
                .where(table.userId().eq(userId))
                .select(table.fetch(FileView.class))
                .execute();
    }

    public Long sumSizeByDirectoryId(UUID directoryId) {
        Long totalSize = sql.createQuery(table)
                .where(table.directoryId().eq(directoryId))
                .select(table.size().sum())
                .fetchOneOrNull();

        return totalSize != null ? totalSize : 0L;
    }

    public Long sumSizeByUserId(UUID userId) {
        Long totalSize = sql.createQuery(table)
                .where(table.userId().eq(userId))
                .select(table.size().sum())
                .fetchOneOrNull();

        return totalSize != null ? totalSize : 0L;
    }

    /**
     * 文件分页查询
     * @param pageRequest 分页请求参数
     * @param specification 查询条件
     * @param userId 用户 ID
     * @return 分页结果
     */
    public Page<FileView> queryFileViews(PageRequest pageRequest, FileSpecification specification, UUID userId) {
        // 构建查询
        MutableRootQuery<FileTable> query = sql.createQuery(table)
                .where(table.userId().eq(userId))
                .where(specification);

        // 添加排序
        pageRequest.getSort().forEach(order -> {
            if (order.isAscending()) {
                query.orderBy(table.get(order.getProperty()).asc());
            } else {
                query.orderBy(table.get(order.getProperty()).desc());
            }
        });

        return query.select(table.fetch(FileView.class))
                .fetchPage(
                        pageRequest.getPageNumber(),
                        pageRequest.getPageSize()
                );
    }

    /**
     * 分页查询软删除的文件
     * @param pageRequest 分页请求参数
     * @param userId 用户 ID
     * @return 分页结果
     */
    public Page<DeletedFileView> queryDeletedFiles(PageRequest pageRequest, UUID userId) {
        return sql.filters(cfg -> cfg.setBehavior(LogicalDeletedBehavior.IGNORED))
                .createQuery(table)
                .where(table.userId().eq(userId))
                .where(table.deletedAt().isNotNull())
                .orderBy(table.deletedAt().desc())
                .select(
                        table.fetch(DeletedFileView.class)
                ).fetchPage(
                        pageRequest.getPageNumber(),
                        pageRequest.getPageSize()
                );
    }

    public Page<FileView> queryFileViewsByType(FileType fileType, PageRequest pageRequest, UUID userId) {
        return switch (fileType) {
            case IMAGE -> queryFileViewsByTypePattern(pageRequest, "image/%", userId);
            case AUDIO -> queryFileViewsByTypePattern(pageRequest, "audio/%", userId);
            case VIDEO -> queryFileViewsByTypePattern(pageRequest, "video/%", userId);
            case DOCUMENT -> queryDocuments(pageRequest, userId);
        };
    }

    public Page<FileView> queryFileViewsByTypePattern(PageRequest pageRequest, String mimeTypePattern, UUID userId) {
        MutableRootQuery<FileTable> query = sql.createQuery(table)
                .where(table.userId().eq(userId))
                .where(table.mimeType().like(mimeTypePattern));

        // 添加排序
        pageRequest.getSort().forEach(order -> {
            if (order.isAscending()) {
                query.orderBy(table.get(order.getProperty()).asc());
            } else {
                query.orderBy(table.get(order.getProperty()).desc());
            }
        });

        return query.select(table.fetch(FileView.class))
                .fetchPage(
                        pageRequest.getPageNumber(),
                        pageRequest.getPageSize()
                );
    }

    public Page<FileView> queryDocuments(PageRequest pageRequest, UUID userId) {
        MutableRootQuery<FileTable> query = sql.createQuery(table)
                .where(table.userId().eq(userId))
                .where(table.mimeType().in(DOCUMENT_MIME_TYPES));

        // 添加排序
        pageRequest.getSort().forEach(order -> {
            if (order.isAscending()) {
                query.orderBy(table.get(order.getProperty()).asc());
            } else {
                query.orderBy(table.get(order.getProperty()).desc());
            }
        });

        return query.select(table.fetch(FileView.class))
                .fetchPage(
                        pageRequest.getPageNumber(),
                        pageRequest.getPageSize()
                );
    }
}
