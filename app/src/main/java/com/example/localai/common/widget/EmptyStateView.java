package com.example.localai.common.widget;

import android.content.Context;
import android.content.res.ColorStateList;
import android.content.res.TypedArray;
import android.util.AttributeSet;
import android.view.LayoutInflater;
import android.view.View;
import android.widget.ImageView;
import android.widget.LinearLayout;

import androidx.core.content.ContextCompat;

import com.example.localai.R;

/** 可复用空态视图：图标 + 标题 + 副标题 +（可选）操作按钮。 */
public class EmptyStateView extends LinearLayout {

    public interface OnActionClickListener {
        void onActionClick();
    }

    private final ImageView iconView;
    private final android.widget.TextView titleView;
    private final android.widget.TextView subtitleView;
    private final com.google.android.material.button.MaterialButton actionButton;

    public EmptyStateView(Context context, AttributeSet attrs) {
        super(context, attrs);
        setOrientation(VERTICAL);
        setGravity(android.view.Gravity.CENTER);
        LayoutInflater.from(context).inflate(R.layout.view_empty_state, this, true);

        iconView = findViewById(R.id.es_icon);
        titleView = findViewById(R.id.es_title);
        subtitleView = findViewById(R.id.es_subtitle);
        actionButton = findViewById(R.id.es_action);

        TypedArray a = context.obtainStyledAttributes(attrs, R.styleable.EmptyStateView);
        int iconRes = a.getResourceId(R.styleable.EmptyStateView_esIcon, 0);
        String title = a.getString(R.styleable.EmptyStateView_esTitle);
        String subtitle = a.getString(R.styleable.EmptyStateView_esSubtitle);
        String action = a.getString(R.styleable.EmptyStateView_esAction);
        a.recycle();

        if (iconRes != 0) {
            iconView.setImageResource(iconRes);
            iconView.setImageTintList(ColorStateList.valueOf(
                    ContextCompat.getColor(context, R.color.text_tertiary)));
        }
        titleView.setText(title == null ? "" : title);
        subtitleView.setText(subtitle == null ? "" : subtitle);
        if (action == null || action.isEmpty()) {
            actionButton.setVisibility(GONE);
        } else {
            actionButton.setVisibility(VISIBLE);
            actionButton.setText(action);
        }
    }

    public void setOnActionClickListener(final OnActionClickListener listener) {
        if (listener == null) {
            actionButton.setOnClickListener(null);
            return;
        }
        actionButton.setOnClickListener(new OnClickListener() {
            @Override
            public void onClick(View v) {
                listener.onActionClick();
            }
        });
    }

    /** 动态文案（用于加载失败/错误态等运行时状态）。 */
    public void setMessages(String title, String subtitle, String actionLabel) {
        titleView.setText(title == null ? "" : title);
        subtitleView.setText(subtitle == null ? "" : subtitle);
        if (actionLabel == null || actionLabel.isEmpty()) {
            actionButton.setVisibility(GONE);
        } else {
            actionButton.setVisibility(VISIBLE);
            actionButton.setText(actionLabel);
        }
    }
}
