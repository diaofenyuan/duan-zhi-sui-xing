package com.example.localai.feature.download;

import android.os.Bundle;
import android.os.StatFs;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.Fragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.localai.MainActivity;
import com.example.localai.R;
import com.example.localai.common.Fmt;
import com.example.localai.common.widget.EmptyStateView;
import com.example.localai.data.ServiceLocator;
import com.example.localai.data.room.DownloadEntity;
import com.example.localai.data.room.DownloadState;
import com.example.localai.data.room.ModelEntity;
import com.google.android.material.progressindicator.LinearProgressIndicator;
import com.google.android.material.snackbar.Snackbar;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * 下载管理页（P2 真实化）：
 * 模型目录（签名验证通过的目录数据 + 同步状态）-> 进行中任务（真实进度/速度/状态）->
 * 已安装（真实安装记录与删除）。存储概览为 StatFs 实测值，不再使用写死的演示数据。
 */
public class DownloadsFragment extends Fragment implements DownloadRepository.Listener {

    private DownloadAdapter adapter;
    private CatalogAdapter catalogAdapter;
    private InstalledAdapter installedAdapter;

    private TextView summaryView;
    private TextView storageText;
    private LinearProgressIndicator storageBar;
    private TextView catalogStatus;
    private View catalogHeader;
    private View activeLabel;
    private View installedLabel;
    private EmptyStateView emptyView;

    private final List<ModelEntity> installed = new ArrayList<>();

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_downloads, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        summaryView = view.findViewById(R.id.text_summary);
        storageText = view.findViewById(R.id.storage_text);
        storageBar = view.findViewById(R.id.storage_bar);
        catalogStatus = view.findViewById(R.id.catalog_status);
        catalogHeader = view.findViewById(R.id.catalog_header);
        activeLabel = view.findViewById(R.id.label_active);
        installedLabel = view.findViewById(R.id.label_installed);
        emptyView = view.findViewById(R.id.empty);

        adapter = new DownloadAdapter(new DownloadAdapter.Actions() {
            @Override
            public void onPauseResume(DownloadEntity task) {
                if (DownloadState.PAUSED.equals(task.state)) {
                    ServiceLocator.downloads().resume(task.taskId);
                } else {
                    ServiceLocator.downloads().pause(task.taskId);
                }
            }

            @Override
            public void onCancel(DownloadEntity task) {
                ServiceLocator.downloads().cancel(task.taskId);
            }

            @Override
            public void onRetry(DownloadEntity task) {
                ServiceLocator.downloads().retry(task.taskId);
            }
        });

        RecyclerView listActive = view.findViewById(R.id.list_active);
        listActive.setLayoutManager(new LinearLayoutManager(requireContext()));
        listActive.setAdapter(adapter);

        catalogAdapter = new CatalogAdapter(item -> {
            ServiceLocator.downloads().enqueue(item.modelId, (ok, message) -> {
                if (getView() == null) {
                    return;
                }
                Snackbar.make(getView(),
                                ok ? getString(R.string.snackbar_queued_real) : message,
                                Snackbar.LENGTH_SHORT)
                        .show();
            });
        });
        RecyclerView listCatalog = view.findViewById(R.id.list_catalog);
        listCatalog.setLayoutManager(new LinearLayoutManager(requireContext()));
        listCatalog.setAdapter(catalogAdapter);
        view.findViewById(R.id.btn_sync_catalog).setOnClickListener(v -> {
            if (ServiceLocator.downloads() != null) {
                ServiceLocator.downloads().refreshCatalog();
            }
        });

        installedAdapter = new InstalledAdapter();
        RecyclerView listInstalled = view.findViewById(R.id.list_installed);
        listInstalled.setLayoutManager(new LinearLayoutManager(requireContext()));
        listInstalled.setAdapter(installedAdapter);

        emptyView.setOnActionClickListener(() -> {
            if (getActivity() instanceof MainActivity) {
                ((MainActivity) getActivity()).openTab(R.id.nav_market);
            }
        });

        refreshAll();
    }

    @Override
    public void onStart() {
        super.onStart();
        if (ServiceLocator.downloads() != null) {
            ServiceLocator.downloads().register(this);
        }
    }

    @Override
    public void onStop() {
        super.onStop();
        if (ServiceLocator.downloads() != null) {
            ServiceLocator.downloads().unregister(this);
        }
    }

    @Override
    public void onDownloadsChanged() {
        if (getView() == null) {
            return;
        }
        refreshActive();
        refreshInstalled();
        refreshStorage();
    }

    @Override
    public void onCatalogChanged() {
        if (getView() == null) {
            return;
        }
        refreshCatalog();
    }

    private void refreshAll() {
        refreshStorage();
        refreshCatalog();
        refreshActive();
        refreshInstalled();
    }

    private void refreshStorage() {
        StatFs statFs = new StatFs(requireContext().getFilesDir().getAbsolutePath());
        long total = statFs.getTotalBytes();
        long available = statFs.getAvailableBytes();
        long used = Math.max(0, total - available);
        int usedPercent = total <= 0 ? 0 : (int) (used * 100 / total);
        storageBar.setProgress(usedPercent);
        storageText.setText(getString(R.string.storage_text_fmt,
                Fmt.humanBytes(used), Fmt.humanBytes(total), Fmt.humanBytes(available)));
    }

    private void refreshCatalog() {
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository == null) {
            return;
        }
        DownloadRepository.CatalogView view = repository.catalogView();
        if (view.isLoading()) {
            catalogStatus.setText(R.string.catalog_status_loading);
            catalogStatus.setTextColor(requireContext().getColor(R.color.text_tertiary));
        } else if (view.isError()) {
            catalogStatus.setText(R.string.catalog_status_error);
            catalogStatus.setTextColor(requireContext().getColor(R.color.status_danger));
        } else {
            catalogStatus.setText(getString(R.string.catalog_status_fmt, view.models.size()));
            catalogStatus.setTextColor(requireContext().getColor(R.color.text_tertiary));
        }
        catalogAdapter.submit(view.models);
        catalogHeader.setVisibility(View.VISIBLE);
        updateEmptyState();
    }

    private void refreshActive() {
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository == null) {
            return;
        }
        adapter.submit(repository.tasks());
        int activeCount = 0;
        for (DownloadRepository.TaskView t : repository.tasks()) {
            if (DownloadState.isActive(t.entity.state)) {
                activeCount++;
            }
        }
        long free = 0;
        StatFs statFs = new StatFs(requireContext().getFilesDir().getAbsolutePath());
        free = statFs.getAvailableBytes();
        summaryView.setText(getString(R.string.dl_summary_fmt, activeCount, Fmt.humanBytes(free)));
        updateEmptyState();
    }

    private void refreshInstalled() {
        DownloadRepository repository = ServiceLocator.downloads();
        if (repository == null) {
            return;
        }
        installed.clear();
        installed.addAll(repository.installed());
        installedAdapter.notifyDataSetChanged();
        updateEmptyState();
    }

    private void updateEmptyState() {
        DownloadRepository repository = ServiceLocator.downloads();
        boolean noTasks = repository == null
                || (adapter.getItemCount() == 0 && installed.isEmpty() && catalogAdapter.getItemCount() == 0);
        emptyView.setVisibility(noTasks ? View.VISIBLE : View.GONE);
        activeLabel.setVisibility(adapter.getItemCount() == 0 ? View.GONE : View.VISIBLE);
        installedLabel.setVisibility(installed.isEmpty() ? View.GONE : View.VISIBLE);
    }

    /** 已安装模型行（真实 ModelEntity + 真实删除）。 */
    private class InstalledAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {

        @NonNull
        @Override
        public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View v = getLayoutInflater().inflate(R.layout.item_installed, parent, false);
            return new RecyclerView.ViewHolder(v) {
            };
        }

        @Override
        public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
            View itemView = holder.itemView;
            final ModelEntity model = installed.get(position);
            String displayName = model.displayName == null ? model.modelId : model.displayName;

            itemView.findViewById(R.id.icon_bg)
                    .setBackgroundResource(ModelAdapterGrad.grad(displayName.hashCode()));
            ((TextView) itemView.findViewById(R.id.text_icon))
                    .setText(String.valueOf(letterOf(displayName)));
            ((TextView) itemView.findViewById(R.id.text_name)).setText(displayName);
            ((TextView) itemView.findViewById(R.id.text_meta)).setText(
                    getString(R.string.installed_meta_fmt,
                            Fmt.humanBytes(model.sizeBytes) + " · " + model.quantization,
                            new SimpleDateFormat("yyyy-MM-dd", Locale.US)
                                    .format(new Date(model.installedAt))));

            itemView.findViewById(R.id.btn_chat).setOnClickListener(v -> openTab(R.id.nav_chat));
            itemView.findViewById(R.id.btn_delete).setOnClickListener(v ->
                    new AlertDialog.Builder(requireContext())
                            .setTitle(R.string.dialog_delete_model_title)
                            .setMessage(R.string.dialog_delete_model_msg)
                            .setPositiveButton(R.string.dialog_delete_model_ok, (d, w) ->
                                    ServiceLocator.downloads().deleteModel(model.modelId, model.version,
                                            (ok, message) -> {
                                                if (getView() == null) {
                                                    return;
                                                }
                                                Snackbar.make(getView(), ok
                                                                ? getString(R.string.toast_deleted_real) : message,
                                                        Snackbar.LENGTH_SHORT).show();
                                            }))
                            .setNegativeButton(R.string.action_cancel, null)
                            .show());
        }

        @Override
        public int getItemCount() {
            return installed.size();
        }
    }

    private static char letterOf(String name) {
        if (name == null || name.isEmpty()) {
            return '?';
        }
        char c = name.charAt(0);
        return Character.toUpperCase(c);
    }

    private void openTab(int tabId) {
        if (getActivity() instanceof MainActivity) {
            ((MainActivity) getActivity()).openTab(tabId);
        }
    }

    /** 渐变资源选择（避免循环依赖 market 包）。 */
    private static final class ModelAdapterGrad {
        static int grad(int index) {
            switch (Math.floorMod(index, 4)) {
                case 1:
                    return R.drawable.grad_b;
                case 2:
                    return R.drawable.grad_c;
                case 3:
                    return R.drawable.grad_d;
                default:
                    return R.drawable.grad_a;
            }
        }
    }
}
