package com.haofenshu.lnkscreen

import android.app.admin.DevicePolicyManager
import android.content.Context
import android.graphics.Color
import android.graphics.drawable.Drawable
import android.os.Bundle
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.WindowManager
import android.widget.Button
import android.widget.ImageView
import android.widget.RadioGroup
import android.widget.Switch
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView

class AppManagementActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var adapter: AppAdapter
    private lateinit var unfreezeAllButton: Button
    private lateinit var freezeOthersButton: Button
    private lateinit var backButton: View
    private lateinit var filterGroup: RadioGroup
    
    private var allApps: List<AppDisplayInfo> = emptyList()
    private var currentFilterId: Int = R.id.rbAll

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        
        window.addFlags(WindowManager.LayoutParams.FLAG_DRAWS_SYSTEM_BAR_BACKGROUNDS)
        window.statusBarColor = Color.parseColor("#FDEAC5")
        window.decorView.systemUiVisibility = View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR

        setContentView(R.layout.activity_app_management)

        initViews()
        loadApps()
    }

    private fun initViews() {
        recyclerView = findViewById(R.id.appRecyclerView)
        recyclerView.layoutManager = LinearLayoutManager(this)
        
        unfreezeAllButton = findViewById(R.id.unfreezeAllButton)
        freezeOthersButton = findViewById(R.id.freezeOthersButton)
        backButton = findViewById(R.id.backButton)
        filterGroup = findViewById(R.id.filterGroup)

        backButton.setOnClickListener { finish() }

        filterGroup.setOnCheckedChangeListener { _, checkedId ->
            currentFilterId = checkedId
            applyFilter()
        }

        unfreezeAllButton.setOnClickListener {
            val toUnfreeze = allApps.filter { it.isHidden }.map { it.packageName }
            if (KioskUtils.setAppsHidden(this, toUnfreeze, false)) {
                Toast.makeText(this, "已全部解冻", Toast.LENGTH_SHORT).show()
                loadApps()
            }
        }

        freezeOthersButton.setOnClickListener {
            val toFreeze = allApps.filter { !it.isWhitelisted && it.packageName != packageName && !it.isHidden }
                .map { it.packageName }
            
            if (KioskUtils.setAppsHidden(this, toFreeze, true)) {
                Toast.makeText(this, "已冻结非白名单应用", Toast.LENGTH_SHORT).show()
                loadApps()
            }
        }
    }

    private fun loadApps() {
        val pm = packageManager
        val installedApps = KioskUtils.getInstalledApps(this)
        val whitelist = KioskUtils.getAllWhitelistApps(this)
        
        // 获取所有设备管理器应用
        val dpm = getSystemService(Context.DEVICE_POLICY_SERVICE) as DevicePolicyManager
        val activeAdmins = dpm.activeAdmins?.map { it.packageName } ?: emptyList()
        
        allApps = installedApps.map { appInfo ->
            val isWhitelisted = whitelist.contains(appInfo.packageName)
            val isSystem = KioskUtils.isSystemApp(appInfo)
            val isHidden = KioskUtils.isAppHidden(this, appInfo.packageName)
            val isAdmin = activeAdmins.contains(appInfo.packageName)
            
            AppDisplayInfo(
                name = appInfo.loadLabel(pm).toString(),
                packageName = appInfo.packageName,
                icon = try { appInfo.loadIcon(pm) } catch (e: Exception) { null },
                isWhitelisted = isWhitelisted,
                isSystem = isSystem,
                isHidden = isHidden,
                isAdmin = isAdmin
            )
        }.sortedWith(compareBy({ !it.isWhitelisted }, { !it.isSystem }, { it.name }))

        applyFilter()
    }

    private fun applyFilter() {
        val filteredList = when (currentFilterId) {
            R.id.rbWhitelist -> allApps.filter { it.isWhitelisted }
            R.id.rbSystem -> allApps.filter { it.isSystem }
            R.id.rbUser -> allApps.filter { !it.isSystem }
            R.id.rbAdmin -> allApps.filter { it.isAdmin }
            else -> allApps
        }

        adapter = AppAdapter(filteredList) { pkg, hidden ->
            KioskUtils.setAppHidden(this, pkg, hidden)
            // 同步更新原始列表中的状态
            allApps = allApps.map { if (it.packageName == pkg) it.copy(isHidden = hidden) else it }
        }
        recyclerView.adapter = adapter
    }

    data class AppDisplayInfo(
        val name: String,
        val packageName: String,
        val icon: Drawable?,
        val isWhitelisted: Boolean,
        val isSystem: Boolean,
        val isHidden: Boolean,
        val isAdmin: Boolean
    )

    class AppAdapter(
        private var data: List<AppDisplayInfo>,
        private val onFreezeToggle: (String, Boolean) -> Unit
    ) : RecyclerView.Adapter<AppAdapter.ViewHolder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
            val view = LayoutInflater.from(parent.context).inflate(R.layout.item_app_management, parent, false)
            return ViewHolder(view)
        }

        override fun onBindViewHolder(holder: ViewHolder, position: Int) {
            val app = data[position]
            holder.appName.text = app.name
            holder.packageName.text = app.packageName
            if (app.icon != null) {
                holder.appIcon.setImageDrawable(app.icon)
            } else {
                holder.appIcon.setImageResource(android.R.drawable.sym_def_app_icon)
            }

            val categoryText = when {
                app.isAdmin -> "设备管理者"
                app.isWhitelisted -> "白名单应用"
                app.isSystem -> "系统应用"
                else -> "普通应用"
            }
            holder.appCategory.text = categoryText
            
            val color = when {
                app.isAdmin -> Color.parseColor("#E91E63") // 粉色
                app.isWhitelisted -> Color.parseColor("#4CAF50") // 绿色
                app.isSystem -> Color.parseColor("#FFB45604") // 棕色
                else -> Color.parseColor("#2196F3") // 蓝色
            }
            holder.appCategory.setTextColor(color)

            holder.freezeSwitch.setOnCheckedChangeListener(null)
            holder.freezeSwitch.isChecked = app.isHidden
            
            holder.freezeSwitch.setOnCheckedChangeListener { _, isChecked ->
                onFreezeToggle(app.packageName, isChecked)
                // 更新当前显示列表的状态
                val newData = data.toMutableList()
                newData[position] = app.copy(isHidden = isChecked)
                data = newData
            }
        }

        override fun getItemCount() = data.size

        class ViewHolder(view: View) : RecyclerView.ViewHolder(view) {
            val appIcon: ImageView = view.findViewById(R.id.appIcon)
            val appName: TextView = view.findViewById(R.id.appName)
            val packageName: TextView = view.findViewById(R.id.packageName)
            val appCategory: TextView = view.findViewById(R.id.appCategory)
            val freezeSwitch: Switch = view.findViewById(R.id.freezeSwitch)
        }
    }
}
