package com.example.localai.data.network;

import java.util.List;

/** 模型目录（backend catalog.schema.json 的客户端镜像）；签名由 catalog.sig 承载。 */
public final class Catalog {

    public long schemaVersion;
    public String generatedAt;
    public List<Entry> models;

    public static class Entry {
        public String modelId;
        public String version;
        public String displayName;
        public String description;
    }
}
