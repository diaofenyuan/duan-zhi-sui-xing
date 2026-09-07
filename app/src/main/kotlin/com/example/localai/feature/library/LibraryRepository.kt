package com.example.localai.feature.library

import android.content.Context
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.provider.OpenableColumns
import com.example.localai.data.room.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.text.PDFTextStripper
import java.nio.ByteBuffer
import java.nio.charset.CodingErrorAction
import java.util.concurrent.Executors

class LibraryRepository(private val context: Context, private val database: AppDatabase) {
    private val dao = database.libraryDao()
    private val worker = Executors.newSingleThreadExecutor { Thread(it, "localai-library") }
    private val main = Handler(Looper.getMainLooper())

    fun <T> execute(block: () -> T, callback: (Result<T>) -> Unit) {
        worker.execute {
            val result = runCatching(block)
            main.post { callback(result) }
        }
    }

    fun workspaces(callback: (Result<List<WorkspaceEntity>>) -> Unit) = execute({
        database.runInTransaction {
            if (dao.workspaces().isEmpty()) dao.insertWorkspace(WorkspaceEntity().apply {
                title = "收件箱"; createdAt = System.currentTimeMillis()
            })
        }
        dao.workspaces()
    }, callback)

    fun addWorkspace(title: String, callback: (Result<Long>) -> Unit) = execute({
        require(title.isNotBlank()) { "请填写工作区名称" }
        dao.insertWorkspace(WorkspaceEntity().apply { this.title = title.trim().take(60); createdAt = System.currentTimeMillis() })
    }, callback)

    fun contents(id: Long, callback: (Result<Pair<List<SourceEntity>, List<TaskResultEntity>>>) -> Unit) =
        execute({ dao.sources(id) to dao.results(id) }, callback)
    fun result(id: Long, callback: (Result<TaskResultEntity?>) -> Unit) = execute({ dao.result(id) }, callback)
    fun source(id: Long, callback: (Result<SourceEntity?>) -> Unit) = execute({ dao.source(id) }, callback)
    fun selectedSources(ids: LongArray, callback: (Result<List<SourceEntity>>) -> Unit) = execute({ dao.selectedSources(ids) }, callback)
    fun deleteSource(id: Long, callback: (Result<Unit>) -> Unit) = execute({ dao.deleteSource(id) }, callback)
    fun deleteResult(id: Long, callback: (Result<Unit>) -> Unit) = execute({ dao.deleteResult(id) }, callback)
    fun deleteWorkspace(id: Long, callback: (Result<Unit>) -> Unit) = execute({ dao.deleteWorkspace(id) }, callback)

    fun save(result: TaskResultEntity, callback: (Result<Long>) -> Unit) {
        // 编辑器可继续变化，队列里只持有本次保存的快照。
        val copy = LibraryContent.gson.fromJson(LibraryContent.gson.toJson(result), TaskResultEntity::class.java)
        execute({
            copy.updatedAt = System.currentTimeMillis()
            if (copy.id == 0L) {
                copy.createdAt = copy.updatedAt
                dao.insertResult(copy)
            } else { dao.updateResult(copy); copy.id }
        }, callback)
    }

    fun importText(workspace: Long, name: String, text: String, callback: (Result<Long>) -> Unit) =
        execute({ insert(workspace, name.ifBlank { "文字资料" }, "text", listOf(SourcePage(1, text))) }, callback)

    fun importFile(workspace: Long, uri: Uri, callback: (Result<Long>) -> Unit) = execute({
        val resolver = context.contentResolver
        val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) cursor.getString(0) else null
        } ?: "导入资料"
        val bytes = resolver.openInputStream(uri)?.use { input ->
            val output = java.io.ByteArrayOutputStream()
            val buffer = ByteArray(8192)
            while (true) {
                val n = input.read(buffer)
                if (n < 0) break
                require(output.size() + n <= 16 * 1024 * 1024) { "文件超过 16 MB，请拆分后导入" }
                output.write(buffer, 0, n)
            }
            output.toByteArray()
        } ?: error("无法读取文件，请重新选择")
        val pdf = bytes.take(5).toByteArray().toString(Charsets.US_ASCII) == "%PDF-"
        val pages = if (pdf) {
            PDFBoxResourceLoader.init(context)
            PDDocument.load(bytes).use { document ->
                require(!document.isEncrypted) { "暂不支持加密 PDF，请导入未加密副本" }
                require(document.numberOfPages <= 120) { "PDF 超过 120 页，请拆分后导入" }
                val stripper = PDFTextStripper().apply { sortByPosition = true }
                var count = 0
                (1..document.numberOfPages).map { page ->
                    stripper.startPage = page; stripper.endPage = page
                    val text = stripper.getText(document)
                    count += text.length
                    require(count <= 400_000) { "资料文字过多，请拆分后导入" }
                    SourcePage(page, text)
                }
            }
        } else {
            require(name.endsWith(".txt", true) || resolver.getType(uri)?.startsWith("text/") == true) { "请选择 TXT 或可提取文字的 PDF" }
            listOf(SourcePage(1, decodeText(bytes)))
        }
        insert(workspace, name, if (pdf) "pdf" else "text", pages)
    }, callback)

    private fun insert(workspace: Long, name: String, type: String, pages: List<SourcePage>): Long {
        val count = pages.sumOf { it.text.length }
        require(pages.any { it.text.isNotBlank() }) { "没有提取到文字。扫描件请先识别图片文字" }
        require(count <= 400_000) { "资料超过 40 万字，请拆分后导入" }
        return dao.insertSource(SourceEntity().apply {
            workspaceId = workspace; this.name = name.take(160); this.type = type
            pagesJson = LibraryContent.gson.toJson(pages); charCount = count; createdAt = System.currentTimeMillis()
        })
    }

    companion object {
        fun decodeText(bytes: ByteArray): String {
            val charset = when {
                bytes.size >= 2 && bytes[0] == 0xff.toByte() && bytes[1] == 0xfe.toByte() -> Charsets.UTF_16LE
                bytes.size >= 2 && bytes[0] == 0xfe.toByte() && bytes[1] == 0xff.toByte() -> Charsets.UTF_16BE
                else -> Charsets.UTF_8
            }
            return try { charset.newDecoder().onMalformedInput(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString().removePrefix("\uFEFF") }
            catch (_: java.nio.charset.CharacterCodingException) {
                java.nio.charset.Charset.forName("GB18030").newDecoder().onMalformedInput(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes)).toString()
            }
        }
    }
}
