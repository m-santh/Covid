/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.notifications

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import timber.log.Timber
import uk.nhs.nhsx.sonar.android.app.appComponent
import javax.inject.Inject

class ReminderBroadcastReceiver : BroadcastReceiver() {

    @Inject
    lateinit var reminders: Reminders

    override fun onReceive(context: Context, intent: Intent) {
        Timber.d("onReceive $intent")
        context.appComponent.inject(this)

        reminders.handleReminderBroadcast(intent)
    }
}
