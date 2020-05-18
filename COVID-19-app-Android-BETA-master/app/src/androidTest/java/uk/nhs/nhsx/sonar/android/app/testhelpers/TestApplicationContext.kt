/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.testhelpers

import android.content.ContextWrapper
import android.content.Intent
import android.util.Base64
import androidx.annotation.StringRes
import androidx.core.os.bundleOf
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.rule.ActivityTestRule
import androidx.test.uiautomator.By
import androidx.test.uiautomator.Direction
import androidx.test.uiautomator.UiDevice
import androidx.test.uiautomator.Until
import androidx.work.WorkManager
import com.google.firebase.messaging.RemoteMessage
import kotlinx.coroutines.runBlocking
import net.danlew.android.joda.JodaTimeAndroid
import okhttp3.mockwebserver.MockWebServer
import org.assertj.core.api.Assertions.assertThat
import org.awaitility.kotlin.await
import org.awaitility.kotlin.until
import org.awaitility.kotlin.untilNotNull
import org.joda.time.DateTime
import timber.log.Timber
import uk.nhs.nhsx.sonar.android.app.FlowTestStartActivity
import uk.nhs.nhsx.sonar.android.app.SonarApplication
import uk.nhs.nhsx.sonar.android.app.crypto.BluetoothIdentifier
import uk.nhs.nhsx.sonar.android.app.crypto.Cryptogram
import uk.nhs.nhsx.sonar.android.app.crypto.encodeAsSecondsSinceEpoch
import uk.nhs.nhsx.sonar.android.app.di.module.AppModule
import uk.nhs.nhsx.sonar.android.app.di.module.CryptoModule
import uk.nhs.nhsx.sonar.android.app.di.module.NetworkModule
import uk.nhs.nhsx.sonar.android.app.di.module.PersistenceModule
import uk.nhs.nhsx.sonar.android.app.http.jsonOf
import uk.nhs.nhsx.sonar.android.app.notifications.NotificationService
import uk.nhs.nhsx.sonar.android.app.referencecode.ReferenceCode
import uk.nhs.nhsx.sonar.android.app.registration.TokenRetriever
import uk.nhs.nhsx.sonar.android.app.testhelpers.TestSonarServiceDispatcher.Companion.PUBLIC_KEY
import uk.nhs.nhsx.sonar.android.app.testhelpers.TestSonarServiceDispatcher.Companion.REFERENCE_CODE
import uk.nhs.nhsx.sonar.android.app.testhelpers.TestSonarServiceDispatcher.Companion.RESIDENT_ID
import uk.nhs.nhsx.sonar.android.app.testhelpers.TestSonarServiceDispatcher.Companion.SECRET_KEY
import uk.nhs.nhsx.sonar.android.app.util.AndroidLocationHelper
import java.nio.ByteBuffer
import java.security.KeyStore
import java.util.concurrent.TimeUnit
import javax.crypto.Mac
import kotlin.random.Random

class TestApplicationContext(rule: ActivityTestRule<FlowTestStartActivity>) {

    private val testActivity = rule.activity
    val app = rule.activity.application as SonarApplication
    val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())

    private val notificationService = NotificationService()
    private val testRxBleClient = TestRxBleClient(app)
    private var eventNumber = 0
    private val startTime = DateTime.parse("2020-04-01T14:33:13Z")
    private val currentTimestampProvider = {
        eventNumber++
        Timber.d("Sending event nr $eventNumber")
        when (eventNumber) {
            1 -> { startTime }
            2, 3 -> DateTime.parse("2020-04-01T14:34:43Z") // +90 seconds
            4 -> DateTime.parse("2020-04-01T14:44:53Z") // +610 seconds
            else -> throw IllegalStateException()
        }
    }
    private val countryCode = "GB".toByteArray()
    private val transmissionTime = ByteBuffer.wrap(startTime.encodeAsSecondsSinceEpoch()).int
    private val firstDeviceSignature = Random.nextBytes(16)
    private val secondDeviceSignature = Random.nextBytes(16)
    private val firstDeviceId = BluetoothIdentifier(
        countryCode,
        Cryptogram.fromBytes(Random.nextBytes(Cryptogram.SIZE)),
        -6,
        transmissionTime,
        firstDeviceSignature
    )
    private val secondDeviceId = BluetoothIdentifier(
        countryCode,
        Cryptogram.fromBytes(Random.nextBytes(Cryptogram.SIZE)),
        -8,
        transmissionTime + 90,
        secondDeviceSignature
    )

    private val testBluetoothModule = TestBluetoothModule(
        app,
        testRxBleClient,
        currentTimestampProvider,
        scanIntervalLength = 2
    )

    private val testLocationHelper = TestLocationHelper(AndroidLocationHelper(app))
    private var testDispatcher = TestSonarServiceDispatcher()
    private var mockServer = MockWebServer()

    val component: TestAppComponent

    init {
        JodaTimeAndroid.init(app)

        resetTestMockServer()
        val mockServerUrl = mockServer.url("").toString().removeSuffix("/")

        val keyStore = KeyStore.getInstance("AndroidKeyStore").apply { load(null) }
        keyStore.aliases().asSequence().forEach { keyStore.deleteEntry(it) }

        component = DaggerTestAppComponent.builder()
            .appModule(AppModule(app, testLocationHelper, TestAnalytics()))
            .persistenceModule(PersistenceModule(app))
            .bluetoothModule(testBluetoothModule)
            .cryptoModule(
                CryptoModule(
                    app,
                    keyStore
                )
            )
            .networkModule(NetworkModule(mockServerUrl, "someValue"))
            .testNotificationsModule(TestNotificationsModule())
            .build()

        app.appComponent = component

        notificationService.let {
            val contextField = ContextWrapper::class.java.getDeclaredField("mBase")
            contextField.isAccessible = true
            contextField.set(it, app)

            app.appComponent.inject(it)
        }
    }

    fun shutdownMockServer() {
        mockServer.shutdown()
    }

    private fun simulateActivationCodeReceived() {
        val msg = RemoteMessage(bundleOf("activationCode" to "test activation code #001"))
        notificationService.onMessageReceived(msg)
    }

    fun simulateStatusUpdateReceived() {
        val msg = RemoteMessage(bundleOf("status" to "POTENTIAL"))
        notificationService.onMessageReceived(msg)
    }

    fun clickOnNotification(
        @StringRes notificationTitleRes: Int,
        @StringRes notificationTextRes: Int,
        notificationDisplayTimeout: Long = 500
    ) {
        val notificationTitle = testActivity.getString(notificationTitleRes)
        val notificationText = testActivity.getString(notificationTextRes)

        device.openNotification()

        device.wait(Until.hasObject(By.text(notificationTitle)), notificationDisplayTimeout)

        // Only title is shown, click on it to toggle notification,
        // on some devices/android version it might trigger the notification action instead
        if (!device.hasObject(By.text(notificationText))) {
            device.findObject(By.text(notificationTitle)).click()
        }

        // If notification text is visible, click it.
        // It might have shown up because we toggled by clicking on the title
        // It might have always been visible if there was enough room on the screen
        if (device.hasObject(By.text(notificationText))) {
            device.findObject(By.text(notificationText)).click()
        }

        // Ensure notifications are hidden before moving on.
        device.wait(Until.gone(By.text(notificationText)), 500)
        device.wait(Until.gone(By.text(notificationTitle)), 500)
    }

    fun clickOnNotificationAction(
        @StringRes notificationTitleRes: Int,
        @StringRes notificationTextRes: Int,
        @StringRes notificationActionRes: Int,
        notificationDisplayTimeout: Long = 500
    ) {
        val notificationTitle = testActivity.getString(notificationTitleRes)
        val notificationText = testActivity.getString(notificationTextRes)
        val notificationAction = testActivity.getString(notificationActionRes)

        device.openNotification()

        device.wait(Until.hasObject(By.text(notificationTitle)), notificationDisplayTimeout)

        // Only title is shown, click on it to toggle notification,
        // on some devices/android version it might trigger the notification action instead
        if (!device.hasObject(By.text(notificationAction)) &&
            !device.hasObject(By.text(notificationAction.toUpperCase()))
        ) {
            device.findObject(By.text(notificationTitle)).swipe(Direction.DOWN, 1F)
        }

        assertThat(device.hasObject(By.text(notificationText))).isTrue()

        val action = device.findObject(By.text(notificationAction.toUpperCase()))
            ?: device.findObject(By.text(notificationAction))

        action.click()
        device.pressBack()
    }

    fun simulateBackendResponse(error: Boolean) {
        testDispatcher.simulateResponse(error)
    }

    fun verifyRegistrationFlow() {
        verifyReceivedRegistrationRequest()
        simulateActivationCodeReceived()
        verifyReceivedActivationRequest()
        verifySonarIdAndSecretKeyAndPublicKey()
        verifyReferenceCode()
    }

    fun verifyRegistrationRetry() {
        verifyReceivedRegistrationRequest()
        simulateActivationCodeReceived()
    }

    private fun verifyReceivedRegistrationRequest() {
        // WorkManager is responsible for starting registration process and unfortunately it is not exact
        // Have to wait for longer time (usually less than 10 seconds). Putting 20 secs just to be sure
        var lastRequest = mockServer.takeRequest(20_000, TimeUnit.MILLISECONDS)

        if (lastRequest?.path?.contains("linking-id") == true) {
            lastRequest = mockServer.takeRequest()
        }

        assertThat(lastRequest).isNotNull()
        assertThat(lastRequest?.method).isEqualTo("POST")
        assertThat(lastRequest?.path).isEqualTo("/api/devices/registrations")
        assertThat(lastRequest?.body?.readUtf8()).isEqualTo("""{"pushToken":"test firebase token #010"}""")
    }

    private fun verifyReceivedActivationRequest() {
        // WorkManager is responsible for starting registration process and unfortunately it is not exact
        // Have to wait for longer time (usually less than 10 seconds). Putting 20 secs just to be sure
        val lastRequest = mockServer.takeRequest(20_000, TimeUnit.MILLISECONDS)

        assertThat(lastRequest).isNotNull()
        assertThat(lastRequest?.method).isEqualTo("POST")
        assertThat(lastRequest?.path).isEqualTo("/api/devices")
        assertThat(lastRequest?.body?.readUtf8())
            .contains("""{"activationCode":"test activation code #001","pushToken":"test firebase token #010",""")
    }

    private fun verifySonarIdAndSecretKeyAndPublicKey() {
        val idProvider = component.getSonarIdProvider()
        val keyStorage = component.getKeyStorage()

        await until {
            idProvider.getSonarId().isNotEmpty()
        }
        assertThat(idProvider.getSonarId()).isEqualTo(RESIDENT_ID)

        await untilNotNull {
            keyStorage.provideSecretKey()
        }
        val messageToSign = "some message".toByteArray()
        val actualSignature = Mac.getInstance("HMACSHA256").apply {
            init(keyStorage.provideSecretKey())
        }.doFinal(messageToSign)
        val expectedSignature = Mac.getInstance("HMACSHA256").apply {
            init(SECRET_KEY)
        }.doFinal(messageToSign)

        assertThat(actualSignature).isEqualTo(expectedSignature)

        await untilNotNull {
            keyStorage.providePublicKey()
        }
        val publicKey = keyStorage.providePublicKey()?.encoded
        val decodedPublicKey = Base64.decode(PUBLIC_KEY, Base64.DEFAULT)
        assertThat(publicKey).isEqualTo(decodedPublicKey)
    }

    private fun verifyReferenceCode() {
        val provider = component.getReferenceCodeProvider()
        await until {
            provider.get() == ReferenceCode(REFERENCE_CODE)
        }
    }

    fun simulateDeviceInProximity() {
        val dao = component.getAppDatabase().contactEventDao()

        testRxBleClient.emitScanResults(
            ScanResultArgs(
                encryptedId = firstDeviceId.asBytes(),
                macAddress = "06-00-00-00-00-00",
                rssiList = listOf(10),
                txPower = -5
            ),
            ScanResultArgs(
                encryptedId = secondDeviceId.asBytes(),
                macAddress = "07-00-00-00-00-00",
                rssiList = listOf(40),
                txPower = -1
            )
        )

        await until {
            runBlocking { dao.getAll().size } == 2
        }

        testRxBleClient.emitScanResults(
            ScanResultArgs(
                encryptedId = firstDeviceId.asBytes(),
                macAddress = "06-00-00-00-00-00",
                rssiList = listOf(20),
                txPower = -5
            )
        )

        await until {
            runBlocking { dao.get(firstDeviceId.cryptogram.asBytes())!!.rssiValues.size } == 2
        }

        testRxBleClient.emitScanResults(
            ScanResultArgs(
                encryptedId = firstDeviceId.asBytes(),
                macAddress = "06-00-00-00-00-00",
                rssiList = listOf(15),
                txPower = -5
            )
        )

        await until {
            runBlocking { dao.get(firstDeviceId.cryptogram.asBytes())!!.rssiValues.size } == 3
        }
    }

    fun verifyReceivedProximityRequest() {
        val lastRequest = mockServer.takeRequest(500, TimeUnit.MILLISECONDS)

        assertThat(lastRequest).isNotNull()
        assertThat(lastRequest?.path).isEqualTo("/api/residents/$RESIDENT_ID")
        assertThat(lastRequest?.method).isEqualTo("PATCH")

        val body = lastRequest?.body?.readUtf8() ?: ""
        assertThat(body).contains(""""symptomsTimestamp":""")
        assertThat(body).contains(""""contactEvents":[""")
        val rssiValues = listOf(10, 20, 15).map { it.toByte() }.toByteArray()
        assertThat(body).contains(
            jsonOf(
                "encryptedRemoteContactId" to Base64.encodeToString(
                    firstDeviceId.cryptogram.asBytes(),
                    Base64.DEFAULT
                ),
                "rssiValues" to Base64.encodeToString(
                    rssiValues,
                    Base64.DEFAULT
                ),
                "rssiIntervals" to listOf(0, 90, 610),
                "timestamp" to "2020-04-01T14:33:13Z",
                "duration" to 700,
                "txPowerInProtocol" to -6,
                "txPowerAdvertised" to -5,
                "hmacSignature" to Base64.encodeToString(firstDeviceSignature, Base64.DEFAULT),
                "transmissionTime" to transmissionTime,
                "countryCode" to ByteBuffer.wrap(countryCode).short
            )
        )
        assertThat(body).contains(
            jsonOf(
                "encryptedRemoteContactId" to Base64.encodeToString(
                    secondDeviceId.cryptogram.asBytes(),
                    Base64.DEFAULT
                ),
                "rssiValues" to Base64.encodeToString(
                    byteArrayOf(40.toByte()),
                    Base64.DEFAULT
                ),
                "rssiIntervals" to listOf(0),
                "timestamp" to "2020-04-01T14:34:43Z",
                "duration" to 60,
                "txPowerInProtocol" to -8,
                "txPowerAdvertised" to -1,
                "hmacSignature" to Base64.encodeToString(secondDeviceSignature, Base64.DEFAULT),
                "transmissionTime" to transmissionTime + 90,
                "countryCode" to ByteBuffer.wrap(countryCode).short

            )
        )
        assertThat(body.countOccurrences("""{"encryptedRemoteContactId":""")).isEqualTo(2)
    }

    fun simulateBackendDelay(delayInMillis: Long) {
        testDispatcher.simulateDelay(delayInMillis)
    }

    fun closeNotificationPanel() {
        val it = Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS)
        app.baseContext.sendBroadcast(it)
    }

    fun simulateUnsupportedDevice() {
        testBluetoothModule.simulateUnsupportedDevice = true
    }

    fun simulateTablet() {
        testBluetoothModule.simulateTablet = true
    }

    fun disableLocationAccess() {
        testLocationHelper.locationEnabled = false
        app.sendBroadcast(Intent(testLocationHelper.providerChangedIntentAction))
    }

    fun enableLocationAccess() {
        testLocationHelper.locationEnabled = true
        app.sendBroadcast(Intent(testLocationHelper.providerChangedIntentAction))
    }

    fun revokeLocationPermission() {
        testLocationHelper.locationPermissionsGranted = false
    }

    fun grantLocationPermission() {
        testLocationHelper.locationPermissionsGranted = true
    }

    private fun resetTestMockServer() {
        testDispatcher = TestSonarServiceDispatcher()
        mockServer.shutdown()
        mockServer = MockWebServer()
        mockServer.dispatcher = testDispatcher
        mockServer.start(43239)
    }

    fun reset() {
        component.apply {
            getAppDatabase().clearAllTables()
            getOnboardingStatusProvider().setOnboardingFinished(false)
            getUserStateStorage().clear()
            getSonarIdProvider().clear()
            getActivationCodeProvider().clear()
        }
        testBluetoothModule.reset()
        testLocationHelper.reset()

        WorkManager.getInstance(app).cancelAllWork()
        resetTestMockServer()
    }
}

class TestTokenRetriever : TokenRetriever {
    override suspend fun retrieveToken() = "test firebase token #010"
}

private fun String.countOccurrences(substring: String): Int =
    if (!contains(substring)) {
        0
    } else {
        1 + replaceFirst(substring, "").countOccurrences(substring)
    }
