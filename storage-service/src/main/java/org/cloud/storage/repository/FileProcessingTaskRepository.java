package org.cloud.storage.repository;

import org.babyfish.jimmer.spring.repo.support.AbstractJavaRepository;
import org.babyfish.jimmer.sql.JSqlClient;
import org.babyfish.jimmer.sql.ast.mutation.SaveMode;
import org.cloud.storage.dto.FileProcessingTaskInput;
import org.cloud.storage.dto.enums.TaskType;
import org.cloud.storage.entity.FileProcessingTask;
import org.cloud.storage.entity.FileProcessingTaskTable;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public class FileProcessingTaskRepository extends AbstractJavaRepository<FileProcessingTask, Long> {
    private static final FileProcessingTaskTable table = FileProcessingTaskTable.$;

    public FileProcessingTaskRepository(JSqlClient sql) {
        super(sql);
    }

    public FileProcessingTask create(FileProcessingTaskInput input) {
        return sql.saveCommand(input)
                .setMode(SaveMode.INSERT_ONLY)
                .execute()
                .getModifiedEntity();
    }

    public List<FileProcessingTask> listUnprocessedTaskByTaskType(TaskType taskType) {
        return sql.createQuery(table)
                .where(table.processed().eq(false))
                .where(table.taskType().eq(taskType.getCode()))
                .select(table)
                .execute();
    }

    public void markAsProcessed(long id) {
         sql.createUpdate(table)
                .where(table.id().eq(id))
                .set(table.processed(), true)
                .execute();
    }
}
