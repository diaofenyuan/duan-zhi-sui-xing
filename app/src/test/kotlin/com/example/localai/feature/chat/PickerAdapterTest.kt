package com.example.localai.feature.chat

import android.content.Context
import android.view.ContextThemeWrapper
import android.widget.FrameLayout
import androidx.core.view.ViewCompat
import com.example.localai.R
import com.example.localai.model.ModelInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment

/**
 * 模型切换列表的无障碍语义：勾选图标本身是装饰（`contentDescription="@null"`，不单独朗读），
 * 选中状态改由整行通过 stateDescription 播报，避免只靠图标颜色/形状传达状态。
 */
@RunWith(RobolectricTestRunner::class)
class PickerAdapterTest {

    private fun model(id: String, name: String) = ModelInfo(
        id, name, "Qwen", "0.5B", 0.5, "Q4_K_M", "400 MB", 400L * 1024 * 1024,
        "2K", ModelInfo.TASK_TEXT, ModelInfo.langs("zh"), "Apache-2.0", "说明",
        ModelInfo.COMPAT_RECOMMENDED, "可用", 100, 10.0, 600, "2026-09-01", 1, true)

    @Test
    fun selectedRowExposesStateToAccessibility() {
        val themed: Context = ContextThemeWrapper(RuntimeEnvironment.getApplication(), R.style.Theme_LocalAI)
        val adapter = PickerAdapter(listOf(model("a", "甲模型"), model("b", "乙模型")), "b") { }
        val holder = adapter.onCreateViewHolder(FrameLayout(themed), 0)

        // 已选中的条目：整行播报「已选择」。
        adapter.onBindViewHolder(holder, 1)
        assertEquals("已选择", ViewCompat.getStateDescription(holder.itemView))

        // 未选中的条目：不残留上一次绑定播报的状态。
        adapter.onBindViewHolder(holder, 0)
        assertNull(ViewCompat.getStateDescription(holder.itemView))
    }
}
