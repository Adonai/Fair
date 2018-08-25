package com.kanedias.dybr.fair

import android.app.Application
import android.content.Context
import com.ftinc.scoop.Scoop
import com.kanedias.dybr.fair.database.DbProvider
import com.kanedias.dybr.fair.dto.Auth
import com.kanedias.dybr.fair.themes.ACCENT
import com.kanedias.dybr.fair.themes.PRIMARY
import com.kanedias.dybr.fair.themes.PRIMARY_DARK
import org.acra.ACRA
import org.acra.annotation.AcraCore
import org.acra.annotation.AcraDialog
import org.acra.annotation.AcraMailSender
import org.acra.data.StringFormat

/**
 * Place to initialize all data prior to launching activities
 *
 * @author Kanedias
 */
@AcraDialog(resIcon = R.mipmap.ic_launcher, resText = R.string.app_crashed, resCommentPrompt = R.string.leave_crash_comment, resTheme = R.style.AppTheme)
@AcraMailSender(mailTo = "kanedias@xaker.ru", resSubject = R.string.app_crash_report, reportFileName = "crash-report.json")
@AcraCore(buildConfigClass = BuildConfig::class, reportFormat = StringFormat.JSON, alsoReportToAndroidFramework = true)
class MainApplication : Application() {

    override fun attachBaseContext(base: Context?) {
        super.attachBaseContext(base)

        // init crash reporting
        ACRA.init(this)
    }

    override fun onCreate() {
        super.onCreate()
        DbProvider.setHelper(this)
        Network.init(this)
        Auth.init(this)
        Scoop.getInstance().initialize(mapOf(
                PRIMARY to resources.getColor(R.color.colorPrimary),
                PRIMARY_DARK to resources.getColor(R.color.colorPrimaryDark),
                ACCENT to resources.getColor(R.color.colorAccent))
        )



        // load last account if it exists
        val acc = DbProvider.helper.accDao.queryBuilder().where().eq("current", true).queryForFirst()
        acc?.let {
            Auth.updateCurrentUser(acc)
        }
    }

    override fun onTerminate() {
        super.onTerminate()
        DbProvider.releaseHelper()
    }
}
