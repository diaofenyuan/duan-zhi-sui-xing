package com.example.localai;

import android.os.Bundle;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.fragment.app.FragmentTransaction;

import com.example.localai.feature.chat.ChatFragment;
import com.example.localai.feature.diagnostics.DiagnosticsFragment;
import com.example.localai.feature.download.DownloadsFragment;
import com.example.localai.feature.market.MarketFragment;
import com.example.localai.feature.settings.SettingsFragment;
import com.google.android.material.bottomnavigation.BottomNavigationView;

/** 单 Activity 宿主：底部 Tab 使用 show/hide 保留状态；二级页使用返回栈。 */
public class MainActivity extends AppCompatActivity {

    private BottomNavigationView bottomNav;

    private MarketFragment marketFragment;
    private ChatFragment chatFragment;
    private DownloadsFragment downloadsFragment;
    private DiagnosticsFragment diagnosticsFragment;
    private SettingsFragment settingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        bottomNav = findViewById(R.id.bottom_nav);
        bottomNav.setOnItemSelectedListener(item -> {
            openTab(item.getItemId());
            return true;
        });

        getSupportFragmentManager().addOnBackStackChangedListener(this::syncNavSelection);

        if (savedInstanceState == null) {
            openTab(R.id.nav_market);
        }

        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager fm = getSupportFragmentManager();
                if (fm.getBackStackEntryCount() > 0) {
                    fm.popBackStack();
                } else {
                    finish();
                }
            }
        });
    }

    public void openTab(int tabId) {
        android.view.MenuItem item = bottomNav.getMenu().findItem(tabId);
        if (item != null && !item.isChecked()) {
            item.setChecked(true);
        }
        showTab(tabId);
        syncNavSelection();
    }

    private void syncNavSelection() {
        Fragment top = topStackFragment();
        int target = currentTabId(top);
        if (target != 0 && bottomNav.getSelectedItemId() != target) {
            bottomNav.getMenu().findItem(target).setChecked(true);
        }
    }

    private Fragment topStackFragment() {
        FragmentManager fm = getSupportFragmentManager();
        if (fm.getBackStackEntryCount() == 0) {
            return activeTabFragment();
        }
        return fm.findFragmentById(R.id.container);
    }

    private int currentTabId(Fragment fragment) {
        if (fragment instanceof MarketFragment) {
            return R.id.nav_market;
        }
        if (fragment instanceof ChatFragment) {
            return R.id.nav_chat;
        }
        if (fragment instanceof DownloadsFragment) {
            return R.id.nav_download;
        }
        if (fragment instanceof DiagnosticsFragment) {
            return R.id.nav_diag;
        }
        if (fragment instanceof SettingsFragment) {
            return R.id.nav_settings;
        }
        return 0;
    }

    private Fragment activeTabFragment() {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (!f.isHidden() && (f instanceof MarketFragment || f instanceof ChatFragment
                    || f instanceof DownloadsFragment || f instanceof DiagnosticsFragment
                    || f instanceof SettingsFragment)) {
                return f;
            }
        }
        return null;
    }

    private void showTab(int tabId) {
        FragmentManager fm = getSupportFragmentManager();
        // 从二级页返回时直接清空返回栈
        while (fm.getBackStackEntryCount() > 0) {
            fm.popBackStackImmediate();
        }
        FragmentTransaction tx = fm.beginTransaction();
        hideAll(tx);

        String tag;
        if (tabId == R.id.nav_market) {
            tag = "market";
        } else if (tabId == R.id.nav_chat) {
            tag = "chat";
        } else if (tabId == R.id.nav_download) {
            tag = "downloads";
        } else if (tabId == R.id.nav_diag) {
            tag = "diag";
        } else if (tabId == R.id.nav_settings) {
            tag = "settings";
        } else {
            tag = null;
        }

        Fragment existing = tag == null ? null : fm.findFragmentByTag(tag);
        Fragment target;
        if (existing == null) {
            switch (tag) {
                case "market":
                    marketFragment = new MarketFragment();
                    target = marketFragment;
                    break;
                case "chat":
                    chatFragment = new ChatFragment();
                    target = chatFragment;
                    break;
                case "downloads":
                    downloadsFragment = new DownloadsFragment();
                    target = downloadsFragment;
                    break;
                case "diag":
                    diagnosticsFragment = new DiagnosticsFragment();
                    target = diagnosticsFragment;
                    break;
                default:
                    settingsFragment = new SettingsFragment();
                    target = settingsFragment;
                    break;
            }
            tx.add(R.id.container, target, tag);
        } else {
            target = existing;
            tx.show(target);
        }
        tx.setPrimaryNavigationFragment(target);
        tx.commitNowAllowingStateLoss();
    }

    /** 推入二级页面（详情、历史等）。 */
    public void push(Fragment fragment) {
        FragmentManager fm = getSupportFragmentManager();
        FragmentTransaction tx = fm.beginTransaction()
                .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                        R.anim.pop_in_left, R.anim.pop_out_right)
                .replace(R.id.container, fragment)
                .addToBackStack(null)
                .setPrimaryNavigationFragment(fragment);
        tx.commit();
    }

    private void hideAll(FragmentTransaction tx) {
        for (Fragment f : getSupportFragmentManager().getFragments()) {
            if (!f.isAdded() || f.isHidden()) {
                continue;
            }
            if (f instanceof MarketFragment || f instanceof ChatFragment
                    || f instanceof DownloadsFragment || f instanceof DiagnosticsFragment
                    || f instanceof SettingsFragment) {
                tx.hide(f);
            }
        }
    }
}
