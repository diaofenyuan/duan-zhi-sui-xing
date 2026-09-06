package com.example.localai.data.storage

import java.io.File
import java.io.FileOutputStream
import java.io.IOException
import java.nio.charset.StandardCharsets

/**
 * 模型文件存储（S017 原子安装/回滚）：
 *   files/downloads/{taskId}/model.part            下载临时文件
 *   files/models/{modelId}/{version}/              manifest.json + manifest.sig + gguf + install.ok
 *   files/models/.staging/{taskId}/                安装暂存目录（跨进程可见）
 * 安装协议：staging 内全部写完后最后写 install.ok，随后同文件系统 rename 至目标目录；
 * 目标已存在时先改名退避到 .rollback，rename 失败则回滚旧版本。任何中断都不会留下
 * "已安装但未校验" 的模型（校验在 staging 进入前完成）。
 */
class ModelStorageManager(filesDir: File) {

    private val root: File = filesDir
    private val downloadsDir = File(filesDir, "downloads")
    private val modelsDir = File(filesDir, "models")
    private val stagingDir = File(modelsDir, ".staging")
    private val rollbackDir = File(modelsDir, ".rollback")

    fun partFile(taskId: String): File = File(downloadDir(taskId), "model.part")

    fun downloadDir(taskId: String): File {
        val dir = File(downloadsDir, taskId)
        dir.mkdirs()
        return dir
    }

    /** 入队时把已验证的 Manifest 原始字节落盘，供安装阶段随模型写入（断网也能安装）。 */
    @Throws(IOException::class)
    fun persistManifest(taskId: String, manifestBytes: ByteArray, sigBytes: ByteArray) {
        writeBytes(File(downloadDir(taskId), "manifest.json"), manifestBytes)
        writeBytes(File(downloadDir(taskId), "manifest.sig"), sigBytes)
    }

    @Throws(IOException::class)
    fun readManifest(taskId: String): ByteArray =
        java.nio.file.Files.readAllBytes(File(downloadDir(taskId), "manifest.json").toPath())

    @Throws(IOException::class)
    fun readManifestSig(taskId: String): ByteArray =
        java.nio.file.Files.readAllBytes(File(downloadDir(taskId), "manifest.sig").toPath())

    fun removeDownloadDir(taskId: String) {
        deleteRecursively(File(downloadsDir, taskId))
    }

    fun modelDir(modelId: String, version: String): File =
        File(modelsDir, modelId + File.separator + version)

    fun modelFile(modelId: String, version: String, fileName: String): File =
        File(modelDir(modelId, version), fileName)

    fun isInstalled(modelId: String, version: String): Boolean =
        File(modelDir(modelId, version), "install.ok").exists()

    fun isInstalled(modelId: String, version: String, fileName: String?, expectedBytes: Long): Boolean {
        if (fileName.isNullOrEmpty() || expectedBytes <= 0 || !isInstalled(modelId, version)) return false
        val file = modelFile(modelId, version, fileName)
        return file.isFile && file.length() == expectedBytes
    }

    /**
     * 原子安装：part 文件 -> staging -> 写入 manifest/sig/install.ok -> rename 到目标。
     * 目标已存在（同版本重装）时旧目录先退避，失败自动回滚。
     */
    @Throws(IOException::class)
    fun install(modelId: String, version: String, partFile: File,
                manifestBytes: ByteArray, sigBytes: ByteArray, fileName: String) {
        val staging = File(stagingDir, version + "-" + System.nanoTime())
        if (!staging.mkdirs()) {
            throw IOException("无法创建安装暂存目录")
        }
        val target = modelDir(modelId, version)
        var backup: File? = null
        try {
            val gguf = File(staging, fileName)
            // 提交前保留下载源；进程在 staging 阶段终止时仍可从源文件重新安装。
            linkOrCopy(partFile, gguf)
            writeBytes(File(staging, "manifest.json"), manifestBytes)
            writeBytes(File(staging, "manifest.sig"), sigBytes)
            writeBytes(File(staging, "install.ok"), "ok\n".toByteArray(StandardCharsets.US_ASCII))

            if (target.exists()) {
                val b = File(rollbackDir, version + "-" + System.nanoTime())
                if (!b.parentFile!!.mkdirs() && !b.parentFile!!.exists()) {
                    throw IOException("无法创建回滚目录")
                }
                if (!target.renameTo(b)) {
                    throw IOException("旧版本退避失败")
                }
                backup = b
            } else {
                val parent = target.parentFile
                if (parent != null && !parent.exists() && !parent.mkdirs()) {
                    throw IOException("无法创建模型目录")
                }
            }
            if (!staging.renameTo(target)) {
                throw IOException("安装 rename 失败")
            }
            partFile.delete()
            if (backup != null) {
                deleteRecursively(backup)
            }
        } catch (e: IOException) {
            if (backup != null && backup.exists() && !target.exists()) {
                // 回滚旧版本；若回滚 rename 也失败，保留 backup 目录（勿删除，旧版本仍可手工恢复）
                backup.renameTo(target)
            }
            throw e
        } finally {
            deleteRecursively(staging)
        }
    }

    /** 删除已安装模型（含全部版本文件）。 */
    fun delete(modelId: String, version: String) {
        deleteRecursively(modelDir(modelId, version))
        val parent = File(modelsDir, modelId)
        val leftovers = parent.list()
        if (leftovers == null || leftovers.isEmpty()) {
            deleteRecursively(parent)
        }
    }

    /** 启动时清理中断安装留下的孤儿目录；含 install.ok 的目录（可能是失败回滚遗留）保留。 */
    fun cleanup() {
        cleanOrphans(stagingDir, false)
        cleanOrphans(rollbackDir, true)
    }

    private fun cleanOrphans(dir: File, keepIfInstalled: Boolean) {
        val children = dir.listFiles() ?: return
        for (child in children) {
            if (keepIfInstalled && File(child, "install.ok").exists()) {
                continue
            }
            deleteRecursively(child)
        }
    }

    companion object {
        private fun linkOrCopy(source: File, dest: File) {
            try {
                // 应用私有目录通常位于同一文件系统，硬链接避免双倍模型空间。
                java.nio.file.Files.createLink(dest.toPath(), source.toPath())
            } catch (e: IOException) {
                java.nio.file.Files.copy(source.toPath(), dest.toPath())
            } catch (e: UnsupportedOperationException) {
                java.nio.file.Files.copy(source.toPath(), dest.toPath())
            }
        }

        @Throws(IOException::class)
        private fun writeBytes(file: File, bytes: ByteArray) {
            FileOutputStream(file).use { out -> out.write(bytes) }
        }

        private fun deleteRecursively(file: File?) {
            if (file == null || !file.exists()) {
                return
            }
            val children = file.listFiles()
            if (children != null) {
                for (child in children) {
                    deleteRecursively(child)
                }
            }
            file.delete()
        }
    }
}
