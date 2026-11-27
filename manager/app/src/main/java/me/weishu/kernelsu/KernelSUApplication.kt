package me.weishu.kernelsu

import android.app.Application
import android.content.ComponentName
import android.content.Context
import android.content.pm.ApplicationInfo
import android.content.pm.PackageManager
import android.os.Build
import android.os.UserManager
import android.system.Os
import androidx.core.content.edit
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import me.weishu.kernelsu.data.repository.SettingsRepositoryImpl
import me.weishu.kernelsu.ui.MainActivity
import me.weishu.kernelsu.ui.viewmodel.SuperUserViewModel
import okhttp3.Cache
import okhttp3.OkHttpClient
import org.lsposed.hiddenapibypass.HiddenApiBypass
import java.io.File
import java.util.Locale

lateinit var ksuApp: KernelSUApplication

class KernelSUApplication : Application(), ViewModelStoreOwner {

    companion object {
        fun setEnableOnBackInvokedCallback(appInfo: ApplicationInfo, enable: Boolean) {
            runCatching {
                val applicationInfoClass = ApplicationInfo::class.java
                val method = applicationInfoClass.getDeclaredMethod("setEnableOnBackInvokedCallback", Boolean::class.javaPrimitiveType)
                method.isAccessible = true
                method.invoke(appInfo, enable)
            }
        }

        private fun isComponentEffectivelyEnabled(
            pm: PackageManager,
            component: ComponentName,
            defaultEnabled: Boolean,
        ): Boolean = when (pm.getComponentEnabledSetting(component)) {
            PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> true
            PackageManager.COMPONENT_ENABLED_STATE_DEFAULT -> defaultEnabled
            else -> false
        }

        fun applyLauncherIconMode(context: Context, mode: Int) {
            val pm = context.packageManager
            val mainComponent = ComponentName(context, MainActivity::class.java)
            pm.setComponentEnabledSetting(
                mainComponent,
                PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                PackageManager.DONT_KILL_APP
            )
            val midorisuComponent = ComponentName(context, MainActivityMidorisu::class.java)
            val kowsuComponent = ComponentName(context, MainActivityKowsu::class.java)
            val officialComponent = ComponentName(context, MainActivityOfficial::class.java)
            val target = when (mode) {
                1 -> kowsuComponent
                2 -> officialComponent
                else -> midorisuComponent
            }
            pm.setComponentEnabledSetting(
                target,
                PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                PackageManager.DONT_KILL_APP
            )
            listOf(midorisuComponent, kowsuComponent, officialComponent).forEach { comp ->
                if (comp != target) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }

        fun syncLauncherIconMode(context: Context) {
            val pm = context.packageManager
            val prefs = context.getSharedPreferences("settings", MODE_PRIVATE)
            val mainComponent = ComponentName(context, MainActivity::class.java)
            val midorisuComponent = ComponentName(context, MainActivityMidorisu::class.java)
            val kowsuComponent = ComponentName(context, MainActivityKowsu::class.java)
            val officialComponent = ComponentName(context, MainActivityOfficial::class.java)

            val storedMode = if (prefs.contains("app_icon_mode")) {
                prefs.getInt("app_icon_mode", 0)
            } else {
                val derived = when {
                    pm.getComponentEnabledSetting(kowsuComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> 1
                    pm.getComponentEnabledSetting(officialComponent) == PackageManager.COMPONENT_ENABLED_STATE_ENABLED -> 2
                    else -> 0
                }
                prefs.edit { putInt("app_icon_mode", derived) }
                derived
            }

            val mainState = pm.getComponentEnabledSetting(mainComponent)
            if (mainState != PackageManager.COMPONENT_ENABLED_STATE_DEFAULT &&
                mainState != PackageManager.COMPONENT_ENABLED_STATE_ENABLED
            ) {
                pm.setComponentEnabledSetting(
                    mainComponent,
                    PackageManager.COMPONENT_ENABLED_STATE_DEFAULT,
                    PackageManager.DONT_KILL_APP
                )
            }

            val targetMode = if (storedMode in 0..2) storedMode else 0
            val entries = listOf(
                Triple(0, midorisuComponent, true),
                Triple(1, kowsuComponent, false),
                Triple(2, officialComponent, false),
            )
            val (_, targetComp, targetDefault) = entries[targetMode]
            if (!isComponentEffectivelyEnabled(pm, targetComp, targetDefault)) {
                pm.setComponentEnabledSetting(
                    targetComp,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                )
            }
            entries.forEach { (mode, comp, defaultEnabled) ->
                if (mode != targetMode && isComponentEffectivelyEnabled(pm, comp, defaultEnabled)) {
                    pm.setComponentEnabledSetting(
                        comp,
                        PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                        PackageManager.DONT_KILL_APP
                    )
                }
            }
        }
    }

    lateinit var okhttpClient: OkHttpClient
    private val appViewModelStore by lazy { ViewModelStore() }

    private fun isUserUnlocked(): Boolean =
        getSystemService(UserManager::class.java)?.isUserUnlocked == true

    override fun onCreate() {
        super.onCreate()
        ksuApp = this

        if (!isUserUnlocked()) {
            return
        }

        syncLauncherIconMode(this)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            val prefs = this.getSharedPreferences("settings", MODE_PRIVATE)
            val enable = prefs.getBoolean("enable_predictive_back", true)
            HiddenApiBypass.addHiddenApiExemptions("Landroid/content/pm/ApplicationInfo;->setEnableOnBackInvokedCallback")
            setEnableOnBackInvokedCallback(applicationInfo, enable)
        }

        val superUserViewModel = ViewModelProvider(this)[SuperUserViewModel::class.java]
        superUserViewModel.loadAppList()

        val webroot = File(dataDir, "webroot")
        if (!webroot.exists()) {
            webroot.mkdir()
        }

        // Provide working env for rust's temp_dir()
        Os.setenv("TMPDIR", cacheDir.absolutePath, true)

        okhttpClient =
            OkHttpClient.Builder().cache(Cache(File(cacheDir, "okhttp"), 10 * 1024 * 1024))
                .addInterceptor { block ->
                    block.proceed(
                        block.request().newBuilder()
                            .header("User-Agent", "KernelSU/${BuildConfig.VERSION_CODE}")
                            .header("Accept-Language", Locale.getDefault().toLanguageTag()).build()
                    )
                }.build()
    }

    override val viewModelStore: ViewModelStore
        get() = appViewModelStore
}
