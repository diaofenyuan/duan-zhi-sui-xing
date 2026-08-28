package com.example.localai.feature.settings;

import android.content.Context;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;
import androidx.fragment.app.Fragment;

import com.example.localai.BuildConfig;
import com.example.localai.R;
import com.example.localai.common.Fmt;
import com.example.localai.data.ServiceLocator;
import com.google.android.material.card.MaterialCardView;
import com.google.android.material.materialswitch.MaterialSwitch;

/** 设置页：运行模式、推理行为、存储与数据、隐私、关于；配置持久化到 SharedPreferences。 */
public class SettingsFragment extends Fragment {

    private static final String PREFS = "localai_settings";
    private static final String KEY_MODE = "mode";
    private static final String KEY_KEEP_SCREEN = "keep_screen";
    private static final String KEY_BG_GEN = "bg_gen";
    private static final String KEY_METRICS = "metrics";

    private MaterialCardView cardAuto;
    private MaterialCardView cardBalanced;
    private MaterialCardView cardSaver;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_settings, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        ((TextView) view.findViewById(R.id.text_version)).setText(
                getString(R.string.settings_ver_fmt, BuildConfig.VERSION_NAME));

        cardAuto = view.findViewById(R.id.mode_auto);
        cardBalanced = view.findViewById(R.id.mode_balanced);
        cardSaver = view.findViewById(R.id.mode_saver);

        final android.widget.RadioButton radioAuto = view.findViewById(R.id.radio_auto);
        final android.widget.RadioButton radioBalanced = view.findViewById(R.id.radio_balanced);
        final android.widget.RadioButton radioSaver = view.findViewById(R.id.radio_saver);

        View.OnClickListener selectAuto = v -> setMode("auto", radioAuto, radioBalanced, radioSaver);
        View.OnClickListener selectBalanced =
                v -> setMode("balanced", radioAuto, radioBalanced, radioSaver);
        View.OnClickListener selectSaver =
                v -> setMode("saver", radioAuto, radioBalanced, radioSaver);
        cardAuto.setOnClickListener(selectAuto);
        cardBalanced.setOnClickListener(selectBalanced);
        cardSaver.setOnClickListener(selectSaver);

        String mode = prefs().getString(KEY_MODE, "auto");
        if ("balanced".equals(mode)) {
            setMode("balanced", radioAuto, radioBalanced, radioSaver);
        } else if ("saver".equals(mode)) {
            setMode("saver", radioAuto, radioBalanced, radioSaver);
        } else {
            setMode("auto", radioAuto, radioBalanced, radioSaver);
        }

        MaterialSwitch swKeepScreen = view.findViewById(R.id.sw_keep_screen);
        MaterialSwitch swBgGen = view.findViewById(R.id.sw_bg_gen);
        MaterialSwitch swMetrics = view.findViewById(R.id.sw_metrics);

        swKeepScreen.setChecked(prefs().getBoolean(KEY_KEEP_SCREEN, false));
        swBgGen.setChecked(prefs().getBoolean(KEY_BG_GEN, false));
        swMetrics.setChecked(prefs().getBoolean(KEY_METRICS, false));

        swKeepScreen.setOnCheckedChangeListener((b, checked) ->
                prefs().edit().putBoolean(KEY_KEEP_SCREEN, checked).apply());
        swBgGen.setOnCheckedChangeListener((b, checked) ->
                prefs().edit().putBoolean(KEY_BG_GEN, checked).apply());
        swMetrics.setOnCheckedChangeListener((b, checked) ->
                prefs().edit().putBoolean(KEY_METRICS, checked).apply());

        // 存储与数据：真实已安装模型体积
        TextView cacheSize = view.findViewById(R.id.text_cache_size);
        cacheSize.setText(getString(R.string.row_clear_cache_val_fmt, cacheLabel()));
        view.findViewById(R.id.row_clear_cache).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.row_clear_cache)
                        .setMessage(getString(R.string.row_clear_cache_val_fmt, cacheLabel()))
                        .setPositiveButton(R.string.action_ok, (d, w) ->
                                android.widget.Toast.makeText(requireContext(),
                                        getString(R.string.cache_cleaned_fmt, cacheLabel()),
                                        android.widget.Toast.LENGTH_SHORT).show())
                        .setNegativeButton(R.string.action_cancel, null)
                        .show());

        view.findViewById(R.id.row_clear_sessions).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.dialog_clear_sessions_title)
                        .setMessage(R.string.dialog_clear_sessions_msg)
                        .setPositiveButton(R.string.action_delete, (d, w) -> {
                            if (ServiceLocator.chat() != null) {
                                ServiceLocator.chat().clearAll();
                            }
                            android.widget.Toast.makeText(requireContext(),
                                    R.string.toast_sessions_cleared,
                                    android.widget.Toast.LENGTH_SHORT).show();
                        })
                        .setNegativeButton(R.string.action_cancel, null)
                        .show());

        // 关于：开源许可证
        view.findViewById(R.id.row_license).setOnClickListener(v ->
                new AlertDialog.Builder(requireContext())
                        .setTitle(R.string.license_dialog_title)
                        .setMessage(licenseSummary())
                        .setPositiveButton(R.string.action_close, null)
                        .show());
    }

    private void setMode(String mode, android.widget.RadioButton auto,
                         android.widget.RadioButton balanced, android.widget.RadioButton saver) {
        prefs().edit().putString(KEY_MODE, mode).apply();
        boolean isAuto = "auto".equals(mode);
        boolean isBalanced = "balanced".equals(mode);
        auto.setChecked(isAuto);
        balanced.setChecked(isBalanced);
        saver.setChecked(!isAuto && !isBalanced);
        highlight(cardAuto, isAuto);
        highlight(cardBalanced, isBalanced);
        highlight(cardSaver, !isAuto && !isBalanced);
    }

    private void highlight(MaterialCardView card, boolean selected) {
        float density = getResources().getDisplayMetrics().density;
        card.setStrokeWidth(selected ? (int) (2 * density) : (int) (1 * density));
        card.setStrokeColor(ContextCompat.getColor(requireContext(),
                selected ? R.color.md_primary : R.color.outline));
    }

    /** 已安装模型真实体积（Room installed_models 汇总）。 */
    private String cacheLabel() {
        if (ServiceLocator.downloads() == null) {
            return "0 MB";
        }
        return Fmt.humanBytes(ServiceLocator.downloads().installedBytes());
    }

    private String licenseSummary() {
        return "端智随行 P4 版\n\n"
                + "· Material Components — Apache-2.0\n"
                + "· AndroidX（AppCompat / RecyclerView / Core / Room / Work）— Apache-2.0\n"
                + "· llama.cpp / ggml — MIT\n"
                + "· OkHttp — Apache-2.0\n"
                + "· Gson — Apache-2.0\n"
                + "· JUnit 4 — EPL-1.0（仅测试）\n\n"
                + "完整清单见 docs/license-policy.md 与 NOTICE。";
    }

    private android.content.SharedPreferences prefs() {
        return requireContext().getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }
}
