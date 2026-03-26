package org.cloud.storage.service.impl;

import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;

@Slf4j
@Component
public class TempFileCache {

    /** 按过期时间排序的列表（从早到晚） */
    private final List<ZipTempFile> sortedFiles = new CopyOnWriteArrayList<>();
    private static final long DEFAULT_EXPIRY_MILLIS = 30 * 60 * 1000; // 30分钟
    private static final long CLEANUP_INTERVAL_MINUTE = 5;


    public record ZipTempFile(Path path, long expiredAt) {}

    public synchronized void register(Path path) {
        long expiredAt = System.currentTimeMillis() + DEFAULT_EXPIRY_MILLIS;
        sortedFiles.add(new ZipTempFile(path, expiredAt));
        log.debug("[ZipTempCache] Registered: path={}", path);
    }

    /**
     * 定时清理过期文件（每5分钟执行）
     */
    @Scheduled(fixedDelay = CLEANUP_INTERVAL_MINUTE, timeUnit = TimeUnit.MINUTES)
    public synchronized void cleanup() {
        long now = System.currentTimeMillis();
        List<ZipTempFile> expiredFiles = new ArrayList<>();

        // 从头扫描，收集过期文件
        Iterator<ZipTempFile> it = sortedFiles.iterator();
        while (it.hasNext()) {
            ZipTempFile file = it.next();
            if (file.expiredAt > now) {
                break; // 遇到未过期的，立即停止
            }
            expiredFiles.add(file);
            it.remove();
        }

        if (expiredFiles.isEmpty()) {
            return;
        }

        log.info("[ZipTempCleanup] Found {} expired files", expiredFiles.size());

        int success = 0;
        for (ZipTempFile file : expiredFiles) {
            try {
                Files.deleteIfExists(file.path);
                success++;
                log.debug("[ZipTempCleanup] Deleted: {}", file.path);
            } catch (IOException e) {
                log.error("[ZipTempCleanup] Failed to delete: {}", file.path, e);
            }
        }

        log.info("[ZipTempCleanup] Deleted {}/{} files", success, expiredFiles.size());
    }
}
