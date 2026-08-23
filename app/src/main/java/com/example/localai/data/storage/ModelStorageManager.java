package com.example.localai.data.storage;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * 模型文件存储（S017 原子安装/回滚）：
 *   files/downloads/{taskId}/model.part            下载临时文件
 *   files/models/{modelId}/{version}/              manifest.json + manifest.sig + gguf + install.ok
 *   files/models/.staging/{taskId}/                安装暂存目录（跨进程可见）
 * 安装协议：staging 内全部写完后最后写 install.ok，随后同文件系统 rename 至目标目录；
 * 目标已存在时先改名退避到 .rollback，rename 失败则回滚旧版本。任何中断都不会留下
 * "已安装但未校验" 的模型（校验在 staging 进入前完成）。
 */
public final class ModelStorageManager {

    private final File root;
    private final File downloadsDir;
    private final File modelsDir;
    private final File stagingDir;
    private final File rollbackDir;

    public ModelStorageManager(File filesDir) {
        this.root = filesDir;
        this.downloadsDir = new File(filesDir, "downloads");
        this.modelsDir = new File(filesDir, "models");
        this.stagingDir = new File(modelsDir, ".staging");
        this.rollbackDir = new File(modelsDir, ".rollback");
    }

    public File partFile(String taskId) {
        return new File(downloadDir(taskId), "model.part");
    }

    public File downloadDir(String taskId) {
        File dir = new File(downloadsDir, taskId);
        dir.mkdirs();
        return dir;
    }

    /** 入队时把已验证的 Manifest 原始字节落盘，供安装阶段随模型写入（断网也能安装）。 */
    public void persistManifest(String taskId, byte[] manifestBytes, byte[] sigBytes) throws IOException {
        writeBytes(new File(downloadDir(taskId), "manifest.json"), manifestBytes);
        writeBytes(new File(downloadDir(taskId), "manifest.sig"), sigBytes);
    }

    public byte[] readManifest(String taskId) throws IOException {
        return java.nio.file.Files.readAllBytes(new File(downloadDir(taskId), "manifest.json").toPath());
    }

    public byte[] readManifestSig(String taskId) throws IOException {
        return java.nio.file.Files.readAllBytes(new File(downloadDir(taskId), "manifest.sig").toPath());
    }

    public void removeDownloadDir(String taskId) {
        deleteRecursively(new File(downloadsDir, taskId));
    }

    public File modelDir(String modelId, String version) {
        return new File(modelsDir, modelId + File.separator + version);
    }

    public File modelFile(String modelId, String version, String fileName) {
        return new File(modelDir(modelId, version), fileName);
    }

    public boolean isInstalled(String modelId, String version) {
        return new File(modelDir(modelId, version), "install.ok").exists();
    }

    /**
     * 原子安装：part 文件 -> staging -> 写入 manifest/sig/install.ok -> rename 到目标。
     * 目标已存在（同版本重装）时旧目录先退避，失败自动回滚。
     */
    public void install(String modelId, String version, File partFile,
                        byte[] manifestBytes, byte[] sigBytes, String fileName) throws IOException {
        File staging = new File(stagingDir, version + "-" + System.nanoTime());
        if (!staging.mkdirs()) {
            throw new IOException("无法创建安装暂存目录");
        }
        File target = modelDir(modelId, version);
        File backup = null;
        try {
            File gguf = new File(staging, fileName);
            if (!moveInto(partFile, gguf)) {
                throw new IOException("模型文件移入暂存目录失败");
            }
            writeBytes(new File(staging, "manifest.json"), manifestBytes);
            writeBytes(new File(staging, "manifest.sig"), sigBytes);
            writeBytes(new File(staging, "install.ok"), "ok\n".getBytes(StandardCharsets.US_ASCII));

            if (target.exists()) {
                backup = new File(rollbackDir, version + "-" + System.nanoTime());
                if (!backup.getParentFile().mkdirs() && !backup.getParentFile().exists()) {
                    throw new IOException("无法创建回滚目录");
                }
                if (!target.renameTo(backup)) {
                    throw new IOException("旧版本退避失败");
                }
            } else {
                File parent = target.getParentFile();
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw new IOException("无法创建模型目录");
                }
            }
            if (!staging.renameTo(target)) {
                throw new IOException("安装 rename 失败");
            }
            if (backup != null) {
                deleteRecursively(backup);
            }
        } catch (IOException e) {
            if (backup != null && backup.exists() && !target.exists()) {
                // 回滚旧版本；若回滚 rename 也失败，保留 backup 目录（勿删除，旧版本仍可手工恢复）
                backup.renameTo(target);
            }
            throw e;
        } finally {
            deleteRecursively(staging);
        }
    }

    /** 删除已安装模型（含全部版本文件）。 */
    public void delete(String modelId, String version) {
        deleteRecursively(modelDir(modelId, version));
        File parent = new File(modelsDir, modelId);
        String[] leftovers = parent.list();
        if (leftovers == null || leftovers.length == 0) {
            deleteRecursively(parent);
        }
    }

    /** 启动时清理中断安装留下的孤儿目录；含 install.ok 的目录（可能是失败回滚遗留）保留。 */
    public void cleanup() {
        cleanOrphans(stagingDir, false);
        cleanOrphans(rollbackDir, true);
    }

    private void cleanOrphans(File dir, boolean keepIfInstalled) {
        File[] children = dir.listFiles();
        if (children == null) {
            return;
        }
        for (File child : children) {
            if (keepIfInstalled && new File(child, "install.ok").exists()) {
                continue;
            }
            deleteRecursively(child);
        }
    }

    private static boolean moveInto(File source, File dest) {
        if (source.renameTo(dest)) {
            return true;
        }
        // 跨文件系统场景（测试环境）：复制 + 删除
        try {
            java.nio.file.Files.copy(source.toPath(), dest.toPath());
            source.delete();
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private static void writeBytes(File file, byte[] bytes) throws IOException {
        try (FileOutputStream out = new FileOutputStream(file)) {
            out.write(bytes);
        }
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        File[] children = file.listFiles();
        if (children != null) {
            for (File child : children) {
                deleteRecursively(child);
            }
        }
        file.delete();
    }
}
