package com.example.localai

import android.os.Bundle
import android.view.MenuItem
import androidx.activity.OnBackPressedCallback
import androidx.appcompat.app.AppCompatActivity
import androidx.fragment.app.Fragment
import androidx.fragment.app.FragmentManager
import androidx.fragment.app.FragmentTransaction
import com.example.localai.feature.chat.ChatFragment
import com.example.localai.feature.diagnostics.DiagnosticsFragment
import com.example.localai.feature.download.DownloadsFragment
import com.example.localai.feature.market.MarketFragment
import com.example.localai.feature.settings.SettingsFragment
import com.google.android.material.bottomnavigation.BottomNavigationView

/** 单 Activity 宿主：底部 Tab 使用 show/hide 保留状态；二级页使用返回栈。 */
class MainActivity : AppCompatActivity() {

    private lateinit var bottomNav: BottomNavigationView

    private var marketFragment: MarketFragment? = null
    private var chatFragment: ChatFragment? = null
    private var downloadsFragment: DownloadsFragment? = null
    private var diagnosticsFragment: DiagnosticsFragment? = null
    private var settingsFragment: SettingsFragment? = null

    /** 跨页导航暂存（详情→聊天、历史→聊天）；对话页 onResume 消费一次。 */
    private var pendingConversationId = 0L
    private var pendingChatModel: String? = null
    private var pendingChatPrefill: String? = null

    /** 打开聊天 Tab 并切换指定模型。 */
    fun openChatWithModel(modelId: String) {
        pendingChatModel = modelId
        openTab(R.id.nav_chat)
    }

    fun openConversation(conversationId: Long) {
        pendingConversationId = conversationId
        openTab(R.id.nav_chat)
    }

    fun setChatPrefill(prefill: String) {
        pendingChatPrefill = prefill
    }

    fun consumeChatPrefill(): String? {
        val value = pendingChatPrefill
        pendingChatPrefill = null
        return value
    }

    fun consumePendingConversation(): Long {
        val value = pendingConversationId
        pendingConversationId = 0
        return value
    }

    fun consumePendingChatModel(): String? {
        val value = pendingChatModel
        pendingChatModel = null
        return value
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bottomNav = findViewById(R.id.bottom_nav)
        bottomNav.setOnItemSelectedListener { item ->
            openTab(item.itemId)
            true
        }

        supportFragmentManager.addOnBackStackChangedListener { syncNavSelection() }

        if (savedInstanceState == null) {
            openTab(R.id.nav_market)
        }

        onBackPressedDispatcher.addCallback(this, object : OnBackPressedCallback(true) {
            override fun handleOnBackPressed() {
                val fm = supportFragmentManager
                if (fm.backStackEntryCount > 0) {
                    fm.popBackStack()
                } else {
                    finish()
                }
            }
        })
    }

    fun openTab(tabId: Int) {
        val item = bottomNav.menu.findItem(tabId)
        if (item != null && !item.isChecked) {
            item.isChecked = true
        }
        showTab(tabId)
        syncNavSelection()
    }

    private fun syncNavSelection() {
        val top = topStackFragment()
        val target = currentTabId(top)
        if (target != 0 && bottomNav.selectedItemId != target) {
            bottomNav.menu.findItem(target).isChecked = true
        }
    }

    private fun topStackFragment(): Fragment? {
        val fm = supportFragmentManager
        if (fm.backStackEntryCount == 0) {
            return activeTabFragment()
        }
        return fm.findFragmentById(R.id.container)
    }

    private fun currentTabId(fragment: Fragment?): Int {
        return when (fragment) {
            is MarketFragment -> R.id.nav_market
            is ChatFragment -> R.id.nav_chat
            is DownloadsFragment -> R.id.nav_download
            is DiagnosticsFragment -> R.id.nav_diag
            is SettingsFragment -> R.id.nav_settings
            else -> 0
        }
    }

    private fun activeTabFragment(): Fragment? {
        for (f in supportFragmentManager.fragments) {
            if (!f.isHidden && (f is MarketFragment || f is ChatFragment
                        || f is DownloadsFragment || f is DiagnosticsFragment
                        || f is SettingsFragment)) {
                return f
            }
        }
        return null
    }

    private fun showTab(tabId: Int) {
        val fm = supportFragmentManager
        // 从二级页返回时直接清空返回栈
        while (fm.backStackEntryCount > 0) {
            fm.popBackStackImmediate()
        }
        val tx = fm.beginTransaction()
        hideAll(tx)

        val tag: String? = when (tabId) {
            R.id.nav_market -> "market"
            R.id.nav_chat -> "chat"
            R.id.nav_download -> "downloads"
            R.id.nav_diag -> "diag"
            R.id.nav_settings -> "settings"
            else -> null
        }

        val existing = if (tag == null) null else fm.findFragmentByTag(tag)
        val target: Fragment
        if (existing == null) {
            when (tag) {
                "market" -> {
                    marketFragment = MarketFragment()
                    target = marketFragment!!
                }
                "chat" -> {
                    chatFragment = ChatFragment()
                    target = chatFragment!!
                }
                "downloads" -> {
                    downloadsFragment = DownloadsFragment()
                    target = downloadsFragment!!
                }
                "diag" -> {
                    diagnosticsFragment = DiagnosticsFragment()
                    target = diagnosticsFragment!!
                }
                else -> {
                    settingsFragment = SettingsFragment()
                    target = settingsFragment!!
                }
            }
            tx.add(R.id.container, target, tag)
        } else {
            target = existing
            tx.show(target)
        }
        tx.setPrimaryNavigationFragment(target)
        tx.commitNowAllowingStateLoss()
    }

    /** 推入二级页面（详情、历史等）。 */
    fun push(fragment: Fragment) {
        val fm = supportFragmentManager
        val tx = fm.beginTransaction()
            .setCustomAnimations(R.anim.slide_in_right, R.anim.slide_out_left,
                R.anim.pop_in_left, R.anim.pop_out_right)
            .replace(R.id.container, fragment)
            .addToBackStack(null)
            .setPrimaryNavigationFragment(fragment)
        tx.commit()
    }

    private fun hideAll(tx: FragmentTransaction) {
        for (f in supportFragmentManager.fragments) {
            if (!f.isAdded || f.isHidden) {
                continue
            }
            if (f is MarketFragment || f is ChatFragment
                || f is DownloadsFragment || f is DiagnosticsFragment
                || f is SettingsFragment) {
                tx.hide(f)
            }
        }
    }
}
