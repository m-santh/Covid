/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.notifications

import android.app.AlarmManager
import android.app.AlarmManager.RTC_WAKEUP
import android.app.PendingIntent
import android.content.Intent
import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.joda.time.DateTime
import org.junit.Test
import uk.nhs.nhsx.sonar.android.app.notifications.Reminders.Companion.REMINDER_TYPE
import uk.nhs.nhsx.sonar.android.app.notifications.Reminders.Companion.REQUEST_CODE_CHECK_IN_REMINDER
import uk.nhs.nhsx.sonar.android.app.util.CheckInReminderNotification

class RemindersTest {

    private val alarmManager = mockk<AlarmManager>()
    private val checkInReminderNotification = mockk<CheckInReminderNotification>()
    private val reminderBroadcastFactory = mockk<ReminderBroadcastFactory>()
    private val reminders = Reminders(alarmManager, checkInReminderNotification, reminderBroadcastFactory)

    @Test
    fun scheduleCheckInReminder() {
        val broadcast = mockk<PendingIntent>()

        every { reminderBroadcastFactory.create(any()) } returns broadcast
        every { alarmManager.setExactAndAllowWhileIdle(any(), any(), any()) } returns Unit

        val time = DateTime.parse("2020-04-28T15:20:00Z")

        reminders.scheduleCheckInReminder(time)

        verifyAll {
            reminderBroadcastFactory.create(REQUEST_CODE_CHECK_IN_REMINDER)
            alarmManager.setExactAndAllowWhileIdle(RTC_WAKEUP, time.millis, broadcast)
        }
    }

    @Test
    fun `handleReminderBroadcast - with check in reminder intent`() {
        every { checkInReminderNotification.show() } returns Unit

        reminders.handleReminderBroadcast(TestIntent(REQUEST_CODE_CHECK_IN_REMINDER))

        verify {
            checkInReminderNotification.show()
        }
    }

    @Test
    fun `handleReminderBroadcast - with any other intent`() {
        reminders.handleReminderBroadcast(TestIntent(null))

        verify {
            checkInReminderNotification wasNot Called
        }
    }

    class TestIntent(private val reminderType: Int?) : Intent() {

        override fun getIntExtra(name: String, defaultValue: Int): Int =
            when {
                reminderType == null -> defaultValue
                name == REMINDER_TYPE -> reminderType
                else -> super.getIntExtra(name, defaultValue)
            }
    }
}
