package com.lagradost.shiro

import ANILIST_SHOULD_UPDATE_LIST
import DataStore.setKey
import MAL_SHOULD_UPDATE_LIST
import android.app.Application
import android.content.Context
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import com.google.auto.service.AutoService
import com.jaredrummler.cyanea.Cyanea
import com.lagradost.shiro.utils.ErrorLogger
import com.lagradost.shiro.utils.cs3.CsPluginManager
import com.lagradost.shiro.utils.mvvm.logError
import com.lagradost.shiro.utils.mvvm.normalSafeApiCall
import org.acra.ReportField
import org.acra.config.CoreConfiguration
import org.acra.config.toast
import org.acra.data.CrashReportData
import org.acra.data.StringFormat
import org.acra.ktx.initAcra
import org.acra.sender.ReportSender
import org.acra.sender.ReportSenderFactory
import java.lang.ref.WeakReference
import kotlin.concurrent.thread

class CustomReportSender : ReportSender {
    // Sends all your crashes to google forms
    override fun send(context: Context, errorContent: CrashReportData) {
        try {
            println("Report sent")
            val url =
                "https://docs.google.com/forms/u/0/d/e/1FAIpQLSf8U6zVn4YPGhbCQXUBNH4k5wlYC2KmmGuUZz4O6TL2o62cAw/formResponse"
            val data = mapOf(
                "entry.1083318133" to errorContent.toJSON()
            )
            thread {
                khttp.post(url, data = data)
            }
        } catch (e: Exception) {
            logError(e)
        }
    }
}

@AutoService(ReportSenderFactory::class)
class CustomSenderFactory : ReportSenderFactory {
    override fun create(context: Context, config: CoreConfiguration): ReportSender {
        return CustomReportSender()
    }

    override fun enabled(config: CoreConfiguration): Boolean {
        // Disabled: the endpoint posts crash reports to the original Shiro
        // developer's Google Form. Nekomo has no replacement sink yet.
        return false
    }
}

class AcraApplication : Application() {
    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)
        context = base

        // Persist crashes/errors to app storage so they're visible from Settings even on a
        // release build with no debugger attached (see utils/ErrorLogger.kt).
        base?.let { ErrorLogger.init(it) }

        // CloudStream extension subsystem: injects our Context into the vendored
        // cloudstream3 classes and loads installed .cs3 plugins on a bg thread
        base?.let { CsPluginManager.init(it) }

        normalSafeApiCall {
            base?.setKey(MAL_SHOULD_UPDATE_LIST, true)
            base?.setKey(ANILIST_SHOULD_UPDATE_LIST, true)
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            initAcra {
                //core configuration:
                buildConfigClass = BuildConfig::class.java
                reportFormat = StringFormat.JSON
                reportContent = listOf(
                    ReportField.BUILD_CONFIG, ReportField.USER_CRASH_DATE,
                    ReportField.ANDROID_VERSION, ReportField.PHONE_MODEL,
                    ReportField.STACK_TRACE, ReportField.LOGCAT
                )

                //each plugin you chose above can be configured in a block like this:
                toast {
                    text = getString(R.string.acra_report_toast)
                    //opening this block automatically enables the plugin.
                }

            }
        }
    }

    override fun onCreate() {
        super.onCreate()
        context = context ?: baseContext
        Cyanea.init(this, resources)
        AppCompatDelegate.setCompatVectorFromResourcesEnabled(true)
//        normalSafeApiCall {
//            // Cleaning up update folder
//            File("$filesDir/Download/apk/").deleteOnExit()
//        }
    }

    companion object {
        private var _context: WeakReference<Context>? = null
        var context
            get() = _context?.get()
            private set(value) {
                _context = WeakReference(value)
            }

        fun getAppContext(): Context? {
            return context
        }

    }

}