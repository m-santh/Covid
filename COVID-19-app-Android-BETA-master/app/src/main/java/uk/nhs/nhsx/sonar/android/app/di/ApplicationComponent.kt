/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.di

import com.polidea.rxandroidble2.RxBleClient
import dagger.Component
import uk.nhs.nhsx.sonar.android.app.BaseActivity
import uk.nhs.nhsx.sonar.android.app.BootCompletedReceiver
import uk.nhs.nhsx.sonar.android.app.FlowTestStartActivity
import uk.nhs.nhsx.sonar.android.app.MainActivity
import uk.nhs.nhsx.sonar.android.app.PackageReplacedReceiver
import uk.nhs.nhsx.sonar.android.app.ble.BluetoothService
import uk.nhs.nhsx.sonar.android.app.contactevents.DeleteOutdatedEventsWorker
import uk.nhs.nhsx.sonar.android.app.debug.TesterActivity
import uk.nhs.nhsx.sonar.android.app.di.module.AppModule
import uk.nhs.nhsx.sonar.android.app.di.module.BluetoothModule
import uk.nhs.nhsx.sonar.android.app.di.module.CryptoModule
import uk.nhs.nhsx.sonar.android.app.di.module.NetworkModule
import uk.nhs.nhsx.sonar.android.app.di.module.NotificationsModule
import uk.nhs.nhsx.sonar.android.app.di.module.PersistenceModule
import uk.nhs.nhsx.sonar.android.app.diagnose.DiagnoseCloseActivity
import uk.nhs.nhsx.sonar.android.app.diagnose.DiagnoseCoughActivity
import uk.nhs.nhsx.sonar.android.app.diagnose.DiagnoseSubmitActivity
import uk.nhs.nhsx.sonar.android.app.diagnose.DiagnoseTemperatureActivity
import uk.nhs.nhsx.sonar.android.app.diagnose.SubmitContactEventsWorker
import uk.nhs.nhsx.sonar.android.app.diagnose.review.DiagnoseReviewActivity
import uk.nhs.nhsx.sonar.android.app.notifications.NotificationService
import uk.nhs.nhsx.sonar.android.app.notifications.ReminderBroadcastReceiver
import uk.nhs.nhsx.sonar.android.app.onboarding.EnableLocationActivity
import uk.nhs.nhsx.sonar.android.app.onboarding.GrantLocationPermissionActivity
import uk.nhs.nhsx.sonar.android.app.onboarding.PermissionActivity
import uk.nhs.nhsx.sonar.android.app.onboarding.PostCodeActivity
import uk.nhs.nhsx.sonar.android.app.referencecode.ReferenceCodeWorker
import uk.nhs.nhsx.sonar.android.app.registration.RegistrationWorker
import uk.nhs.nhsx.sonar.android.app.status.AtRiskActivity
import uk.nhs.nhsx.sonar.android.app.status.IsolateActivity
import uk.nhs.nhsx.sonar.android.app.status.OkActivity
import uk.nhs.nhsx.sonar.android.app.util.LocationHelper
import javax.inject.Singleton

@Singleton
@Component(
    modules = [
        AppModule::class,
        PersistenceModule::class,
        BluetoothModule::class,
        CryptoModule::class,
        NetworkModule::class,
        NotificationsModule::class
    ]
)
interface ApplicationComponent {
    fun inject(activity: BaseActivity)
    fun inject(activity: PermissionActivity)
    fun inject(activity: EnableLocationActivity)
    fun inject(activity: GrantLocationPermissionActivity)
    fun inject(activity: IsolateActivity)
    fun inject(activity: OkActivity)
    fun inject(activity: AtRiskActivity)
    fun inject(activity: DiagnoseReviewActivity)
    fun inject(activity: DiagnoseCloseActivity)
    fun inject(activity: MainActivity)
    fun inject(activity: FlowTestStartActivity)
    fun inject(activity: PostCodeActivity)
    fun inject(activity: TesterActivity)
    fun inject(activity: DiagnoseSubmitActivity)
    fun inject(activity: DiagnoseCoughActivity)
    fun inject(activity: DiagnoseTemperatureActivity)

    fun inject(service: BluetoothService)
    fun inject(service: NotificationService)

    fun inject(worker: DeleteOutdatedEventsWorker)
    fun inject(worker: RegistrationWorker)
    fun inject(worker: SubmitContactEventsWorker)
    fun inject(worker: ReferenceCodeWorker)

    fun inject(receiver: PackageReplacedReceiver)
    fun inject(receiver: BootCompletedReceiver)
    fun inject(receiver: ReminderBroadcastReceiver)

    fun provideRxBleClient(): RxBleClient
    fun provideLocationHelper(): LocationHelper
}
