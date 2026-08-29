package com.example.localai.common.widget

import android.content.Context
import android.content.res.ColorStateList
import android.util.AttributeSet
import android.view.LayoutInflater
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.TextView
import androidx.core.content.ContextCompat
import com.example.localai.R
import com.google.android.material.button.MaterialButton

/** 可复用空态视图：图标 + 标题 + 副标题 +（可选）操作按钮。 */
class EmptyStateView(context: Context, attrs: AttributeSet?) : LinearLayout(context, attrs) {

    fun interface OnActionClickListener {
        fun onActionClick()
    }

    private val iconView: ImageView
    private val titleView: TextView
    private val subtitleView: TextView
    private val actionButton: MaterialButton

    init {
        orientation = VERTICAL
        gravity = android.view.Gravity.CENTER
        LayoutInflater.from(context).inflate(R.layout.view_empty_state, this, true)

        iconView = findViewById(R.id.es_icon)
        titleView = findViewById(R.id.es_title)
        subtitleView = findViewById(R.id.es_subtitle)
        actionButton = findViewById(R.id.es_action)

        val a = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView)
        val iconRes = a.getResourceId(R.styleable.EmptyStateView_esIcon, 0)
        val title = a.getString(R.styleable.EmptyStateView_esTitle)
        val subtitle = a.getString(R.styleable.EmptyStateView_esSubtitle)
        val action = a.getString(R.styleable.EmptyStateView_esAction)
        a.recycle()

        if (iconRes != 0) {
            iconView.setImageResource(iconRes)
            iconView.imageTintList = ColorStateList.valueOf(
                ContextCompat.getColor(context, R.color.text_tertiary))
        }
        titleView.text = title ?: ""
        subtitleView.text = subtitle ?: ""
        if (action.isNullOrEmpty()) {
            actionButton.visibility = GONE
        } else {
            actionButton.visibility = VISIBLE
            actionButton.text = action
        }
    }

    fun setOnActionClickListener(listener: OnActionClickListener?) {
        if (listener == null) {
            actionButton.setOnClickListener(null)
            return
        }
        actionButton.setOnClickListener { listener.onActionClick() }
    }

    /** 动态文案（用于加载失败/错误态等运行时状态）。 */
    fun setMessages(title: String?, subtitle: String?, actionLabel: String?) {
        titleView.text = title ?: ""
        subtitleView.text = subtitle ?: ""
        if (actionLabel.isNullOrEmpty()) {
            actionButton.visibility = GONE
        } else {
            actionButton.visibility = VISIBLE
            actionButton.text = actionLabel
        }
    }
}
