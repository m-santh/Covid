package net.simplyadvanced.vitalsigns;

import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.Toast;

import net.simplyadvanced.vitalsigns.bloodpressure.BloodPressureActivity;
import net.simplyadvanced.vitalsigns.bodytemperature.BodyTemperatureActivity;
import net.simplyadvanced.vitalsigns.facialgestures.FacialGesturesActivity;
import net.simplyadvanced.vitalsigns.multiplefaces.MultipleFacesDetectionActivity;
import net.simplyadvanced.vitalsigns.pupil.PupilDilationActivity;

import net.simplyadvanced.vitalsigns.oxygensaturation.OxygenSaturationActivity;
import net.simplyadvanced.vitalsigns.respiratoryrate.RespiratoryRateActivity;


public class MainActivity extends Activity  {

    private static final int PERMISSIONS_REQUEST = 451;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        LinearLayout rootView = (LinearLayout) findViewById(R.id.root);

        addActivityNavigationView(rootView, CheckVitalSignsActivity.class, "HR/BP/Temp w/Hardcoded Forehead Area");
        addActivityNavigationView(rootView, BloodPressureActivity.class, "HR/BP/Temp w/Face Detection");
        addActivityNavigationView(rootView, BodyTemperatureActivity.class, "[N/A] Auto HR/BP at 5fps");
        addActivityNavigationView(rootView, RespiratoryRateActivity.class, "Respiratory Rate");
        addActivityNavigationView(rootView, OxygenSaturationActivity.class, "[SIM] Oxygen Saturation");
        addActivityNavigationView(rootView, FacialGesturesActivity.class, "Locate Facial Features");
        addActivityNavigationView(rootView, PupilDilationActivity.class, "[SIM] Pupils");
        addActivityNavigationView(rootView, MultipleFacesDetectionActivity.class, "Detect Multiple Faces");

        if (!hasPermission(this)) {
            requestPermission(this);
        }
    }

    public static final String PERMISSION_CAMERA = Manifest.permission.CAMERA;
    public static final String ACCESS_NETWORK_STATE = Manifest.permission.ACCESS_NETWORK_STATE;
    public static final String INTERNET = Manifest.permission.INTERNET;
    public static final String RECORD_AUDIO = Manifest.permission.RECORD_AUDIO;
    public static final String SEND_SMS = Manifest.permission.SEND_SMS;
    public static final String PERMISSION_STORAGE = Manifest.permission.WRITE_EXTERNAL_STORAGE;
    public static final String ACCESS_COARSE_LOCATION = Manifest.permission.ACCESS_COARSE_LOCATION;
    public static boolean hasPermission(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return context.checkSelfPermission(PERMISSION_CAMERA) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(PERMISSION_STORAGE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(INTERNET) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(ACCESS_NETWORK_STATE) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(SEND_SMS) == PackageManager.PERMISSION_GRANTED
                    && context.checkSelfPermission(ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED;
        } else {
            return true;
        }
    }

    
    public static void requestPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (activity.shouldShowRequestPermissionRationale(PERMISSION_CAMERA)
                    || activity.shouldShowRequestPermissionRationale(PERMISSION_STORAGE)
                    || activity.shouldShowRequestPermissionRationale(INTERNET)
                    || activity.shouldShowRequestPermissionRationale(ACCESS_NETWORK_STATE)
                    || activity.shouldShowRequestPermissionRationale(RECORD_AUDIO)
                    || activity.shouldShowRequestPermissionRationale(SEND_SMS)
                    || activity.shouldShowRequestPermissionRationale(ACCESS_COARSE_LOCATION)) {
                Toast.makeText(activity, "Need camera and storage permissions to continue",
                        Toast.LENGTH_LONG).show();
            }
            activity.requestPermissions(new String[]{PERMISSION_CAMERA,
                    PERMISSION_STORAGE, INTERNET, ACCESS_NETWORK_STATE, RECORD_AUDIO, SEND_SMS, ACCESS_COARSE_LOCATION
                    }, PERMISSIONS_REQUEST);
        }
    }


    private void addActivityNavigationView(ViewGroup root, final Class<?> activityClass,
            String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                startActivity(new Intent(MainActivity.this, activityClass));

            }
        });
        root.addView(button);
    }

}
