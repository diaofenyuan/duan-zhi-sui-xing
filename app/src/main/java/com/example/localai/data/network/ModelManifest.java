package com.example.localai.data.network;

import com.google.gson.annotations.SerializedName;

import java.util.List;
import java.util.Locale;
import java.util.regex.Pattern;

/**
 * 模型 Manifest（后端 model-manifest.schema.json 的客户端镜像）。
 * 字段名与 schema 严格一致；签名由 manifest.sig 侧车文件承载，不在本对象内。
 */
public final class ModelManifest {

    private static final Pattern SHA256_PATTERN = Pattern.compile("^[0-9a-f]{64}$");
    private static final Pattern VERSION_PATTERN = Pattern.compile("^[0-9A-Za-z._-]{1,32}$");
    private static final Pattern ID_PATTERN = Pattern.compile("^[a-z0-9][a-z0-9._-]{0,63}$");

    public String modelId;
    public String version;
    public String displayName;
    public Source source;
    public License license;
    public String format;
    public String architecture;
    public long parameterCount;
    public String quantization;
    public long contextLength;
    public String description;
    public List<FileEntry> files;
    public Runtime runtime;

    public static class Source {
        public String publisher;
        public String url;
    }

    public static class License {
        public String spdx;
        public String url;
    }

    public static class FileEntry {
        public String name;
        public long sizeBytes;
        public String sha256;
        public String etag;
        public List<String> urls;
    }

    public static class Runtime {
        public int minAndroidApi;
        public List<String> abis;
        public List<String> backends;
    }

    /**
     * 客户端侧结构校验（防缺失字段被误用，服务器 schema 之外的防御性检查）。
     * 返回 null 表示通过，否则返回可展示给 UI 的失败原因。
     */
    public String validate() {
        if (modelId == null || !ID_PATTERN.matcher(modelId).matches()) {
            return "manifest 缺少合法 modelId";
        }
        if (version == null || !VERSION_PATTERN.matcher(version).matches()) {
            return "manifest 缺少合法 version";
        }
        if (isBlank(displayName)) {
            return "manifest 缺少 displayName";
        }
        if (source == null || isBlank(source.publisher) || isBlank(source.url)) {
            return "manifest 缺少来源（publisher/url）";
        }
        if (license == null || isBlank(license.spdx) || isBlank(license.url)) {
            return "manifest 缺少许可证（spdx/url）";
        }
        if (!"gguf".equals(format)) {
            return "不支持的格式：" + format;
        }
        if (isBlank(architecture)) {
            return "manifest 缺少 architecture";
        }
        if (parameterCount < 1 || contextLength < 1) {
            return "manifest 参数数量或上下文长度非法";
        }
        if (files == null || files.isEmpty()) {
            return "manifest 缺少文件列表";
        }
        for (FileEntry f : files) {
            if (isBlank(f.name) || f.sizeBytes < 1) {
                return "文件条目缺少 name/sizeBytes";
            }
            if (f.sha256 == null || !SHA256_PATTERN.matcher(f.sha256.toLowerCase(Locale.ROOT)).matches()) {
                return "文件 " + f.name + " 缺少合法 sha256";
            }
            if (f.urls == null || f.urls.isEmpty()) {
                return "文件 " + f.name + " 缺少下载地址";
            }
        }
        if (runtime == null || runtime.minAndroidApi < 1
                || runtime.abis == null || runtime.abis.isEmpty()
                || runtime.backends == null || runtime.backends.isEmpty()) {
            return "manifest 缺少运行约束（minAndroidApi/abis/backends）";
        }
        return null;
    }

    public FileEntry primaryFile() {
        return files == null || files.isEmpty() ? null : files.get(0);
    }

    private static boolean isBlank(String s) {
        return s == null || s.trim().isEmpty();
    }
}
