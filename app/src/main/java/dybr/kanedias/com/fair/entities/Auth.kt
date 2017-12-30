package dybr.kanedias.com.fair.entities

import android.content.Context
import dybr.kanedias.com.fair.R
import dybr.kanedias.com.fair.database.DbProvider

/**
 * Auth static entity providing info about user login status
 *
 * @author Kanedias
 *
 * Created on 05.11.17
 */
object Auth {
    lateinit var guest: Account
    lateinit var user: Account

    fun init(ctx: Context) {
        guest = Account().apply { email = ctx.getString(R.string.guest) }
        user = guest
    }

    fun updateCurrentProfile(prof: OwnProfile) {
        user.currentProfile = prof
        user.lastProfileId = prof.id

        DbProvider.helper.accDao.update(Auth.user)
    }
}