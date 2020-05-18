/****************************************************************************************
 ****************************************************************************************/

package net.alea.beaconsimulator.bluetooth;

import android.Manifest;
import android.app.AlarmManager;
import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.PowerManager;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
import android.support.v4.app.NotificationManagerCompat;
import android.support.v4.content.ContextCompat;
import android.widget.Toast;

import net.alea.beaconsimulator.ActivityMain;
import net.alea.beaconsimulator.App;
import net.alea.beaconsimulator.R;
import net.alea.beaconsimulator.bluetooth.event.BeaconChangedEvent;
import net.alea.beaconsimulator.bluetooth.event.BeaconDeletedEvent;
import net.alea.beaconsimulator.bluetooth.event.BroadcastChangedEvent;
import net.alea.beaconsimulator.bluetooth.model.IBeacon;
import net.alea.beaconsimulator.bluetooth.model.SocialBeaconModel;
import net.alea.beaconsimulator.bluetooth.model.BeaconType;
import net.alea.beaconsimulator.event.UserRequestStartEvent;
import net.alea.beaconsimulator.event.UserRequestStopAllEvent;
import net.alea.beaconsimulator.event.UserRequestStopEvent;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.Time;
import java.sql.Timestamp;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;

import static android.bluetooth.le.ScanFilter.*;
import static net.alea.beaconsimulator.bluetooth.model.IBeacon.BEACON_CODE;
import static net.alea.beaconsimulator.bluetooth.model.IBeacon.MANUFACTURER_PACKET_SIZE;


public class BeaconScannerService extends Service {

    private static final Logger sLogger = LoggerFactory.getLogger(BeaconScannerService.class);

    private static final int SCAN_TIME_MILLIS = 500;
    public static final int DELTA_TIME_MILLIS = 100;
    private static final long SCAN_EXIT_TIME_MILLIS = 5 * 60 * 1000; // 5 mins

    private static final String PREFIX = "net.alea.beaconsimulator.service.";
    public static final String ACTION_START = PREFIX + "ACTION_START";
    public static final String ACTION_SCHEDULED = PREFIX + "ACTION_SCHEDULED";
    public static final String ACTION_STOP = PREFIX + "ACTION_STOP";
    public static final String ACTION_STOP_ALL = PREFIX + "ACTION_STOP_ALL";
    public static final String EXTRA_BEACON_STORE_ID = PREFIX + "EXTRA_BEACON_STORE_ID";
    public static final String EXTRA_USER_TRIGGERED = PREFIX + "EXTRA_USER_TRIGGERED";

    public static final int NO_WARNING = 0;
    public static final int EARLY_WARNING = 5;
    public static final int ACTUAL_WARNING = 6;
    public static final int SEVER_WARNING = 7;

    public static final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "status";

    public static final int NOTIFICATION_ID_2 = 5;
    public static final String CHANNEL_ID_2 = "scanning";

    private static BeaconScannerService sInstance;

    private AlarmManager mAlarmManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mBtScanner;

    //PowerManager.WakeLock wl ;
    //PowerManager pm;
    boolean wakeUpFlag = false;
    private Timer timer;
    private TimerTask timerTask;

    public void startTimer() {
        //set a new Timer
        timer = new Timer();

        //initialize the TimerTask's job
        timerTask = new TimerTask() {
            public void run() {
                sLogger.info("Scanner timer...");
                startBeaconScan();
            }
        };

        //schedule the timer, to wake up every 1 second
        timer.schedule(timerTask, SCAN_TIME_MILLIS); //
    }

    private static Timestamp mNotificationTime = SocialBeaconModel.getCurrentTimestamp();

    private boolean mIsScanning = false;
    private Map<UUID, PendingIntent> mScheduledPendingIntents;
    private SocialBeaconStore mSocialBeaconStore;

    // TODO Should I deprecate ServiceControl in favor of static methods?
    public class ServiceControl extends Binder {
        /* FIXME Warning, 3 ways to know number of running beacons: ServiceControl, SocialBeaconStore running list, BroadcastChangeEvent
         * ServiceControl is for current status, SocialBeaconStore to persist status, BroadcastChangEvent for changes in status. */
        public List<String> getScannedList() {
            return mSocialBeaconStore.allBeacons();
        }
        public boolean isScanning() {
            return mIsScanning;
        }
    }

    private final ServiceControl mBinder = new ServiceControl();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String methodName = new Object() {}
                    .getClass()
                    .getEnclosingMethod()
                    .getName();

            //sLogger.info("NBeaconScanner: {}", methodName);

            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    final int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    switch (btState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            BeaconScannerService.stopScanning(context,  true);
                            break;
                        case BluetoothAdapter.STATE_ON:
                            if (ContextCompat.checkSelfPermission(context,
                                    Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
                                BeaconScannerService.startScanning(context);
                            }
                            break;
                    }
                    break;
                }
            }
        }
    };


    @Override
    public void onCreate() {
        super.onCreate();
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        mAlarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
        mBtAdapter = ((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();
	    mIsScanning = false;
        mScheduledPendingIntents = new HashMap<>();
        mSocialBeaconStore = ((App)getApplication()).getSocialBeaconStore();
        registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        EventBus.getDefault().register(this);
        sInstance = this;
        //startScanning(App.getInstance().getActivityMain());
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        updateNotification(); // Called as soon as possible to avoid ANR: startForegroundService/startForeground sequence
        String action = intent.getAction();
        switch (action) {
            case ACTION_START: {
                sLogger.info("Action: starting new scanning");
                startBeaconScan();
                break;
            }
            case ACTION_SCHEDULED: {
                sLogger.info("Action: processing scheduled scan update");
                //handleExitEvent();
                startBeaconScan();
                if (isScanning()) {
		            //updateScan();
                }
                else {
                    //startBeaconScan();
                }
                break;
            }
            case ACTION_STOP_ALL:
            case ACTION_STOP: {
                sLogger.debug("Action: stopping a scanning");
                stopBeaconScan();
                break;
            }

            default: {
                sLogger.warn("Unknown action scan asked");
            }

        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);
        return mBinder;
    }

    @Override
    public void onDestroy() {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        sInstance = null;
        super.onDestroy();
        sLogger.debug("onDestroy() called");
        /*
        final String EXTRA_FEATURE = "EXTRA_FEATURE";

        Context context = this; //App.getInstance().getActivityMain();
        final Intent activityIntent = new Intent(context, ActivityMain.class);
        activityIntent.putExtra(EXTRA_FEATURE, ActivityMain.Feature.scanRestart);
        context.startActivity(activityIntent);

         */

        stopBeaconScan();
        unregisterReceiver(mBroadcastReceiver);
        EventBus.getDefault().unregister(this);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onBeaconDeleted(BeaconDeletedEvent event) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);
            BeaconScannerService.stopScanning(this, true);
        //startBeaconScan();
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onBeaconChanged(BeaconChangedEvent event) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        if (true) {
            stopBeaconScan();//this, event.getBeaconId(), false);
            startBeaconScan();//this, event.getBeaconId(), false);
        }
    }

    private void updateScan() {
        stopBeaconScan();
        startBeaconScan();
    }

    public static byte[] hexStringToByteArray(String s) {
        int len = s.length();
        byte[] data = new byte[len / 2];
        for (int i = 0; i < len; i += 2) {
            data[i / 2] = (byte) ((Character.digit(s.charAt(i), 16) << 4)
                    + Character.digit(s.charAt(i+1), 16));
        }
        return data;
    }

    private void startBeaconScan() {
        if (mIsScanning ) { //&& mWorking) {
            sLogger.debug("Already started to scan of beacons");
            return;
        }
        sLogger.debug("Starting scan of beacons");
        //mIsScanning = true;

        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mBtScanner = mBtAdapter.getBluetoothLeScanner();
        if (mBtScanner == null || !mBtAdapter.isEnabled()) {
            sLogger.warn("Bluetooth is off, doing nothing");
            mBtScanner = null;
            EventBus.getDefault().post(new BroadcastChangedEvent(new UUID(0,0), false, mSocialBeaconStore.size()));
            return;
        }

	    ScanFilter.Builder scanbuilder = new Builder();

        //ParcelUuid parcelUuid = ParcelUuid.fromString(App.BEACON_UUID);
	    //ParcelUuid parcelUuidMask = ParcelUuid.fromString(App.BEACON_UUID_MASK);

        // Empty data
        byte[] manData = new byte[]{0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0,0};

// Data Mask
        byte[] mask = new byte[]{0,0,1,1,1,1,1,1,1,1,0,0,0,0,0,0,0,0,0,0,0,0,0};

// Copy UUID into data array and remove all "-"
        System.arraycopy(hexStringToByteArray(App.BEACON_UUID.replace("-","")), 0, manData, 2, 16);

	    scanbuilder.setManufacturerData(IBeacon.MANUFACTURER_ID, manData, mask);
        final List<ScanFilter> filters = Collections.singletonList(scanbuilder.build());

        mBtScanner.startScan(filters, builder.build(), mScanCallback);
        //setScheduledUpdate(SCAN_TIME_MILLIS);
    }


    private void stopBeaconScan() {
        if (!mIsScanning) {
            return;
        }
        removeScheduledUpdate();
        sLogger.debug("Stopping scan of beacons");
        mIsScanning = false;
        stopForeground(true);
        if (mBtScanner != null) {
            mBtScanner.stopScan(mScanCallback);
	    mBtScanner = null;
        }
        updateNotification();
        //setScheduledUpdate(SCAN_TIME_MILLIS);
    }

    private void setScheduledUpdate(long timestamp) {
	    removeScheduledUpdate();
        Intent intent = new Intent(getApplicationContext(), BeaconScannerService.class);
        intent.setAction(ACTION_SCHEDULED);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), (int)System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mScheduledPendingIntents.put(new UUID(0,1), pendingIntent);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            mAlarmManager.setExactAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent);
        }
        else {
            mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent);
        }
    }

    private void removeScheduledUpdate() {
        PendingIntent pendingIntent = mScheduledPendingIntents.remove(new UUID(0,1));
        if (pendingIntent == null) {
            return;
        }
        mAlarmManager.cancel(pendingIntent);
    }

    public static void startScanning(Context context ) {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        final Intent intent = new Intent(context, BeaconScannerService.class);
        intent.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
        //context.startService(intent);
    }

    public static int getBeaconState(SocialBeaconModel socialModel) {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        int alertLevel = NO_WARNING;
        Double dist = socialModel.getContactDistance();

        if(dist > 2 ) {
            alertLevel = EARLY_WARNING;
        }
        else if (dist > 1) {
            alertLevel = ACTUAL_WARNING;
        }
        else {
            alertLevel = SEVER_WARNING;
        }
        return alertLevel;
    }

    public static void stopScanning(Context context, boolean updateActiveList) {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        final Intent intent = new Intent(context, BeaconScannerService.class);
        intent.setAction(ACTION_STOP_ALL);
        intent.putExtra(EXTRA_USER_TRIGGERED, updateActiveList);
        ContextCompat.startForegroundService(context, intent);
        //context.startService(intent);
    }

    public static void bindService(Context context, ServiceConnection serviceConnection) {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        Intent intent = new Intent(context, BeaconScannerService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void unbindService(Context context, ServiceConnection serviceConnection) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        context.unbindService(serviceConnection);
    }

    public static boolean isBluetoothOn(Context context) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        final BluetoothAdapter bluetoothAdapter = ((BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        return (bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

    public static boolean isScanningAvailable(Context context) {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        final BluetoothAdapter bluetoothAdapter = ((BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        return bluetoothAdapter != null ;
    }

    public static List<String> getSacnnedList() {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        if (sInstance != null) {
            return sInstance.mSocialBeaconStore.allBeacons();
        }
        else {
            return null;
        }
    }

    public boolean isScanning() {
        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        if (sInstance != null) {
             return this.mIsScanning;
	    }
	    return false;
    }

    public void addScanResult(ScanResult scanResult) {

        String methodName = new Object() {}
                .getClass()
                .getEnclosingMethod()
                .getName();

        //sLogger.info("NBeaconScanner: {}", methodName);

        mIsScanning = true;
        IBeacon ibeacon = IBeacon.parseScanRecord(scanResult.getScanRecord());
        if(ibeacon == null) {
            // Not iBeacon
            return;
        }
        UUID id = ibeacon.getProximityUUID();
        long idMSB = id.getMostSignificantBits();
        final long filerId = UUID.fromString(App.BEACON_UUID).getMostSignificantBits();

        if(filerId != idMSB) {
            sLogger.warn("Dropping this beacond id = {}", id.toString());
            return;
        }

        String macAddress = scanResult.getDevice().getAddress();
        int rssi = scanResult.getRssi();
        int txPower = ibeacon.getPower();
        String empno = App.getInstance().getConfig().getEmpno();
        long socialEmpno = id.getLeastSignificantBits();

        SocialBeaconModel socialModel = mSocialBeaconStore.getBeacon(id);
        if(socialModel == null) {
            socialModel = new SocialBeaconModel(macAddress,  rssi, id, ibeacon, ByteBuffer.wrap(empno.getBytes()).getLong());
            // Send intent to fragment...
            final String EXTRA_FEATURE = "EXTRA_FEATURE";
            Context context = App.getInstance().getActivityMain();
            final Intent activityIntent = new Intent(context, ActivityMain.class);
            activityIntent.putExtra(EXTRA_FEATURE, ActivityMain.Feature.scanAdd);
            context.startActivity(activityIntent);

        }

        socialModel.updateRssi(rssi);
        updateNotificationVibrate(getBeaconState(socialModel));

        mSocialBeaconStore.saveBeacon(socialModel);


    }

    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            sLogger.info("New BLE device found: {}", result.getDevice().getAddress());
            sInstance.addScanResult(result);
        }
        public void onBatchScanResults(List<ScanResult> results) {
        }
        public void onScanFailed(int errorCode) {
        }
    };

    private int handleExitEvent() {
        List<String> mIds = mSocialBeaconStore.allBeacons();
        int count = 0;
        for(String id : mIds) {
            SocialBeaconModel sm = mSocialBeaconStore.getBeacon(UUID.fromString(id));
            if((sm != null) && (sm.getEventState() != SocialBeaconModel.EXIT_EVENT)) {
                Timestamp ts = sm.getExitTime();
                Timestamp now = SocialBeaconModel.getCurrentTimestamp();
                if((now.getTime() - ts.getTime()) > SCAN_EXIT_TIME_MILLIS) {
                    sm.setExitEvent();
                    mSocialBeaconStore.saveBeacon(sm);
                }
                if(getBeaconState(sm) != NO_WARNING && ((now.getTime() - ts.getTime())< DELTA_TIME_MILLIS)) {
                    count = count + 1;
                }
            }

        }

        return count;
    }


    private void updateNotification() {
        if (true) {
            //startForeground(NOTIFICATION_ID_2, null);
            //return;
        }
        final Intent activityIntent = new Intent(BeaconScannerService.this, ActivityMain.class);
        final PendingIntent activityPendingIntent = PendingIntent.getActivity(BeaconScannerService.this, 0, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        final Intent stopBroadcastIntent = new Intent(BeaconScannerService.this, BeaconScannerService.class);
        stopBroadcastIntent.setAction(ACTION_STOP_ALL);
        stopBroadcastIntent.putExtra(EXTRA_USER_TRIGGERED, true);
        final PendingIntent stopBroadcastPendingIntent = PendingIntent.getService(BeaconScannerService.this, 0, stopBroadcastIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        final NotificationManager notificationManager = (NotificationManager)getSystemService(Context.NOTIFICATION_SERVICE);

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel creation
            final CharSequence name = getString(R.string.notif_channel_name);
            final String description = getString(R.string.notif_channel_description);
            final int importance = NotificationManager.IMPORTANCE_LOW;
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            notificationManager.createNotificationChannel(channel);
        }
        final NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(BeaconScannerService.this, CHANNEL_ID);
        final int count = handleExitEvent();
        notifBuilder
                .setSmallIcon(R.drawable.ic_app_plain1)
                //.setPriority(NotificationCompat.PRIORITY_LOW)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getResources().getQuantityString(R.plurals.notif_scan_message, count, count));
                //.addAction(R.drawable.ic_menu_pause, getString(R.string.notif_scan_stop), stopBroadcastPendingIntent);
                //.setContentIntent(activityPendingIntent);
        startForeground(NOTIFICATION_ID, notifBuilder.build());
        stopForeground(true);
        //notificationManager.notify(NOTIFICATION_ID_2, notifBuilder.build());
    }

    private void updateNotificationVibrate(int alertLevel) {

         Timestamp now = SocialBeaconModel.getCurrentTimestamp();
         if((now.getTime() - mNotificationTime.getTime()) < DELTA_TIME_MILLIS) {
             return;
         }

        if(alertLevel == NO_WARNING) {
            return;
        }
        final Intent activityIntent = new Intent(BeaconScannerService.this, ActivityMain.class);
        final PendingIntent activityPendingIntent = PendingIntent.getActivity(BeaconScannerService.this, 0, activityIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        final Intent stopBroadcastIntent = new Intent(BeaconScannerService.this, BeaconScannerService.class);
        stopBroadcastIntent.setAction(ACTION_STOP_ALL);
        stopBroadcastIntent.putExtra(EXTRA_USER_TRIGGERED, true);
        final PendingIntent stopBroadcastPendingIntent = PendingIntent.getService(BeaconScannerService.this, 0, stopBroadcastIntent, PendingIntent.FLAG_CANCEL_CURRENT);
        final NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // Channel creation
            final CharSequence name = getString(R.string.notif_channel_name);
            final String description = getString(R.string.notif_channel_description);
            final NotificationChannel channel = new NotificationChannel(CHANNEL_ID_2, name, NotificationManager.IMPORTANCE_HIGH);
            channel.setDescription(description);
            channel.enableLights(true);
            channel.enableVibration(true);
            channel.setImportance(NotificationManager.IMPORTANCE_HIGH);
            notificationManager.createNotificationChannel(channel);
        }
        final NotificationCompat.Builder notifBuilder = new NotificationCompat.Builder(BeaconScannerService.this, CHANNEL_ID_2);
        final int count = handleExitEvent();
        notifBuilder
                .setSmallIcon(R.drawable.ic_app_plain1)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                //.setVisibility(Notification.VISIBILITY_PUBLIC)
                .setDefaults(Notification.DEFAULT_VIBRATE | Notification.DEFAULT_SOUND | Notification.DEFAULT_LIGHTS)
                .setContentTitle(getString(R.string.app_name))
                .setContentText(getResources().getQuantityString(R.plurals.notif_scan_message, count, count));
                //.addAction(R.drawable.ic_menu_pause, getString(R.string.notif_scan_stop), stopBroadcastPendingIntent)
                //.setContentIntent(activityPendingIntent);
                //.setAutoCancel(true);

        if(count > 0) {
            if (alertLevel == EARLY_WARNING) {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_NOTIFICATION);
                notifBuilder.setSound(alarmSound);
                notifBuilder.setVibrate(new long[]{1000, 1000, 1000});
                notifBuilder.setLights(Color.YELLOW, 3000, 3000);
            } else if (alertLevel == ACTUAL_WARNING) {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_ALARM);
                notifBuilder.setSound(alarmSound);
                notifBuilder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000});
                notifBuilder.setLights(Color.BLUE, 3000, 3000);
            } else if (alertLevel == SEVER_WARNING) {
                Uri alarmSound = RingtoneManager.getDefaultUri(RingtoneManager.TYPE_RINGTONE);
                notifBuilder.setSound(alarmSound);
                notifBuilder.setVibrate(new long[]{1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000, 1000});
                notifBuilder.setLights(Color.RED, 3000, 3000);
            }
        }
        else {
            notifBuilder.setSound(null);
            notifBuilder.setVibrate(new long[] {});
            notifBuilder.setLights(Color.GREEN, 0,0);
        }
        Notification note = notifBuilder.build();
        note.defaults |= Notification.DEFAULT_VIBRATE;
        note.defaults |= Notification.DEFAULT_SOUND;

        //notificationManager.notify(NOTIFICATION_ID_2, note);

        startForeground(NOTIFICATION_ID_2, note); //notifBuilder.build());
        mNotificationTime = now;
    }

}
