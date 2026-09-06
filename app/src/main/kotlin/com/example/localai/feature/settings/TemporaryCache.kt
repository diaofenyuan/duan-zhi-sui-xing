package com.example.localai.feature.settings

import java.io.File
import java.io.IOException
import java.nio.file.FileVisitResult
import java.nio.file.Files
import java.nio.file.Path
import java.nio.file.SimpleFileVisitor
import java.nio.file.attribute.BasicFileAttributes

/** 只操作 Android 分配的 cacheDir，不扫描模型、下载断点或聊天数据库。 */
class TemporaryCache(private val directory: File) {
    data class Result(val bytes: Long, val failures: Int)

    fun measure(): Result = visit(false)
    fun clear(): Result = visit(true)

    private fun visit(delete: Boolean): Result {
        val root = directory.toPath()
        if (!Files.exists(root)) return Result(0, 0)
        var bytes = 0L
        var failures = 0
        try {
            // 不跟随符号链接，链接目标即使指向模型目录也不会被遍历或删除。
            Files.walkFileTree(root, object : SimpleFileVisitor<Path>() {
                override fun visitFile(file: Path, attrs: BasicFileAttributes): FileVisitResult {
                    try {
                        if (!delete || Files.deleteIfExists(file)) {
                            if (attrs.isRegularFile) bytes += attrs.size()
                        }
                    } catch (e: IOException) { failures++ }
                    return FileVisitResult.CONTINUE
                }

                override fun visitFileFailed(file: Path, exc: IOException): FileVisitResult {
                    failures++
                    return FileVisitResult.CONTINUE
                }

                override fun postVisitDirectory(dir: Path, exc: IOException?): FileVisitResult {
                    if (exc != null) failures++
                    if (delete && dir != root) {
                        try { Files.deleteIfExists(dir) } catch (e: IOException) { failures++ }
                    }
                    return FileVisitResult.CONTINUE
                }
            })
        } catch (e: IOException) { failures++ }
        return Result(bytes, failures)
    }
}
