package com.example.localai.data.room;

import androidx.room.Entity;
import androidx.room.Ignore;
import androidx.room.PrimaryKey;

/** 下载任务（S014 字段：taskId/modelId/version/fileName/bytesDownloaded/totalBytes/etag/sha256/state/retryCount/lastError）。 */
@Entity(tableName = "download_tasks")
public class DownloadEntity {

    @androidx.annotation.NonNull
    @PrimaryKey
    public String taskId;

    public String modelId;
    public String version;
    public String fileName;
    public String displayName;
    public String publisher;
    public String quantization;
    public String licenseSpdx;
    public long parameterCount;
    public long bytesDownloaded;
    public long totalBytes;
    public String etag;
    public String sha256;
    public String url;
    public String state = DownloadState.QUEUED;
    public int retryCount;
    public String lastError;
    public long createdAt;
    public long updatedAt;

    public DownloadEntity() {
    }

    @Ignore
    public DownloadEntity(String taskId, String modelId, String version, String fileName,
                          String displayName, String publisher, String quantization,
                          String licenseSpdx, long parameterCount,
                          long totalBytes, String sha256, String url) {
        this.taskId = taskId;
        this.modelId = modelId;
        this.version = version;
        this.fileName = fileName;
        this.displayName = displayName;
        this.publisher = publisher;
        this.quantization = quantization;
        this.licenseSpdx = licenseSpdx;
        this.parameterCount = parameterCount;
        this.totalBytes = totalBytes;
        this.sha256 = sha256;
        this.url = url;
        this.createdAt = System.currentTimeMillis();
        this.updatedAt = this.createdAt;
    }

    public int percent() {
        if (totalBytes <= 0) {
            return 0;
        }
        long p = bytesDownloaded * 100 / totalBytes;
        return (int) Math.min(100L, Math.max(0L, p));
    }

    /** 状态迁移（合法才生效）。 */
    public boolean transition(String to) {
        if (!DownloadState.canTransition(state, to)) {
            return false;
        }
        state = to;
        updatedAt = System.currentTimeMillis();
        return true;
    }
}
