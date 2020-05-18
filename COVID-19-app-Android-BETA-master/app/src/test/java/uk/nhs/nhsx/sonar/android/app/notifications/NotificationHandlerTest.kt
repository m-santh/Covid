/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.notifications

import io.mockk.Called
import io.mockk.every
import io.mockk.mockk
import io.mockk.verify
import io.mockk.verifyAll
import org.joda.time.DateTime
import org.junit.Test
import uk.nhs.nhsx.sonar.android.app.R
import uk.nhs.nhsx.sonar.android.app.registration.ActivationCodeProvider
import uk.nhs.nhsx.sonar.android.app.registration.RegistrationManager
import uk.nhs.nhsx.sonar.android.app.status.DefaultState
import uk.nhs.nhsx.sonar.android.app.status.AmberState
import uk.nhs.nhsx.sonar.android.app.status.RedState
import uk.nhs.nhsx.sonar.android.app.status.Symptom
import uk.nhs.nhsx.sonar.android.app.status.UserStateStorage
import uk.nhs.nhsx.sonar.android.app.util.nonEmptySetOf

class NotificationHandlerTest {

    private val sender = mockk<NotificationSender>(relaxUnitFun = true)
    private val statusStorage = mockk<UserStateStorage>(relaxUnitFun = true)
    private val activationCodeProvider = mockk<ActivationCodeProvider>(relaxUnitFun = true)
    private val registrationManager = mockk<RegistrationManager>(relaxUnitFun = true)
    private val ackDao = mockk<AcknowledgmentsDao>(relaxUnitFun = true)
    private val ackApi = mockk<AcknowledgmentsApi>(relaxUnitFun = true)
    private val handler = NotificationHandler(
        sender,
        statusStorage,
        activationCodeProvider,
        registrationManager,
        ackDao,
        ackApi
    )

    @Test
    fun testOnMessageReceived_UnrecognizedNotification() {
        val messageData = mapOf("foo" to "bar")

        handler.handle(messageData)

        verifyAll {
            sender wasNot Called
            statusStorage wasNot Called
            activationCodeProvider wasNot Called
            registrationManager wasNot Called
        }
    }

    @Test
    fun testOnMessageReceived_Activation() {
        val messageData = mapOf("activationCode" to "code-023")

        handler.handle(messageData)

        verify {
            activationCodeProvider.setActivationCode("code-023")
            registrationManager.register()
        }
    }

    @Test
    fun testOnMessageReceived_StatusUpdate() {
        val messageData = mapOf("status" to "POTENTIAL")
        every { statusStorage.get() } returns DefaultState

        handler.handle(messageData)

        verifyAll {
            statusStorage.get()
            statusStorage.update(any<AmberState>())
            sender.send(10001, R.string.notification_title, R.string.notification_text, any())
        }
    }

    @Test
    fun testOnMessageReceived_InAmberState() {
        val messageData = mapOf("status" to "POTENTIAL")
        every { statusStorage.get() } returns AmberState(DateTime.now())

        handler.handle(messageData)

        verifyAll {
            statusStorage.get()
            sender wasNot Called
        }
        verify(exactly = 0) { statusStorage.update(any<AmberState>()) }
    }

    @Test
    fun testOnMessageReceived_InRedState() {
        val messageData = mapOf("status" to "POTENTIAL")
        every { statusStorage.get() } returns RedState(DateTime.now(), nonEmptySetOf(Symptom.TEMPERATURE))

        handler.handle(messageData)

        verifyAll {
            statusStorage.get()
            sender wasNot Called
        }
        verify(exactly = 0) { statusStorage.update(any<AmberState>()) }
    }

    @Test
    fun testOnMessageReceived_WithAcknowledgmentUrl() {
        every { ackDao.tryFind(any()) } returns null
        every { statusStorage.get() } returns DefaultState

        val messageData =
            mapOf("status" to "POTENTIAL", "acknowledgmentUrl" to "https://api.example.com/ack/100")

        handler.handle(messageData)

        verifyAll {
            statusStorage.get()
            statusStorage.update(any<AmberState>())
            sender.send(10001, R.string.notification_title, R.string.notification_text, any())
            ackApi.send("https://api.example.com/ack/100")
            ackDao.tryFind("https://api.example.com/ack/100")
            ackDao.insert(Acknowledgment("https://api.example.com/ack/100"))
        }
    }

    @Test
    fun testOnMessageReceived_WhenItHasAlreadyBeenReceived() {
        every { ackDao.tryFind(any()) } returns Acknowledgment("https://api.example.com/ack/101")

        val messageData =
            mapOf("status" to "POTENTIAL", "acknowledgmentUrl" to "https://api.example.com/ack/101")

        handler.handle(messageData)

        verifyAll {
            statusStorage wasNot Called
            sender wasNot Called
            ackApi.send("https://api.example.com/ack/101")
            ackDao.tryFind("https://api.example.com/ack/101")
            ackDao.insert(Acknowledgment("https://api.example.com/ack/101"))
        }
    }
}
