package com.example.localai.data.room;

import androidx.room.Entity;
import androidx.room.Ignore;

/** 已安装模型（modelId+version 联合主键）。 */
@Entity(tableName = "installed_models", primaryKeys = {"modelId", "version"})
public class ModelEntity {

    @androidx.annotation.NonNull
    public String modelId;
    @androidx.annotation.NonNull
    public String version;
    public String displayName;
    public String publisher;
    public String quantization;
    public String licenseSpdx;
    public String fileName;
    public long sizeBytes;
    public long parameterCount;
    public long installedAt;

    public ModelEntity() {
    }

    @Ignore
    public ModelEntity(String modelId, String version, String displayName, String publisher,
                       String quantization, String licenseSpdx, String fileName,
                       long sizeBytes, long parameterCount) {
        this.modelId = modelId;
        this.version = version;
        this.displayName = displayName;
        this.publisher = publisher;
        this.quantization = quantization;
        this.licenseSpdx = licenseSpdx;
        this.fileName = fileName;
        this.sizeBytes = sizeBytes;
        this.parameterCount = parameterCount;
        this.installedAt = System.currentTimeMillis();
    }
}
