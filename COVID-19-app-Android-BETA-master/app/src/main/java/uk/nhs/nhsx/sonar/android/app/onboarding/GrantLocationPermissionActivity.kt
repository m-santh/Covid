/*
 * Copyright © 2020 NHSX. All rights reserved.
 */

package uk.nhs.nhsx.sonar.android.app.onboarding

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings.ACTION_APPLICATION_DETAILS_SETTINGS
import kotlinx.android.synthetic.main.activity_edge_case.edgeCaseText
import kotlinx.android.synthetic.main.activity_edge_case.edgeCaseTitle
import kotlinx.android.synthetic.main.activity_edge_case.takeActionButton
import kotlinx.android.synthetic.main.banner.toolbar_info
import uk.nhs.nhsx.sonar.android.app.ColorInversionAwareActivity
import uk.nhs.nhsx.sonar.android.app.R
import uk.nhs.nhsx.sonar.android.app.appComponent
import uk.nhs.nhsx.sonar.android.app.util.LocationHelper
import uk.nhs.nhsx.sonar.android.app.util.URL_INFO
import uk.nhs.nhsx.sonar.android.app.util.openUrl
import javax.inject.Inject

open class GrantLocationPermissionActivity :
    ColorInversionAwareActivity(R.layout.activity_edge_case) {

    @Inject
    lateinit var locationHelper: LocationHelper

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        appComponent.inject(this)

        if (Build.VERSION.SDK_INT >= 29) {
            edgeCaseTitle.setText(R.string.grant_location_permission_title)
            edgeCaseText.setText(R.string.grant_location_permission_rationale)
        } else {
            edgeCaseTitle.setText(R.string.grant_location_permission_title_pre_10)
            edgeCaseText.setText(R.string.grant_location_permission_rationale_pre_10)
        }
        takeActionButton.setText(R.string.go_to_app_settings)

        takeActionButton.setOnClickListener {
            val intent = Intent(ACTION_APPLICATION_DETAILS_SETTINGS)
            val uri = Uri.fromParts("package", packageName, null)
            intent.data = uri

            if (intent.resolveActivity(packageManager) != null) {
                startActivity(intent)
            }
        }

        toolbar_info.setOnClickListener {
            openUrl(URL_INFO)
        }
    }

    override fun onResume() {
        super.onResume()
        if (locationHelper.locationPermissionsGranted()) {
            finish()
        }
    }

    override fun handleInversion(inversionModeEnabled: Boolean) {
        if (inversionModeEnabled) {
            takeActionButton.setBackgroundResource(R.drawable.button_round_background_inversed)
        } else {
            takeActionButton.setBackgroundResource(R.drawable.button_round_background)
        }
    }

    companion object {
        fun start(context: Context) =
            context.startActivity(getIntent(context))

        private fun getIntent(context: Context) =
            Intent(context, GrantLocationPermissionActivity::class.java)
    }
}
