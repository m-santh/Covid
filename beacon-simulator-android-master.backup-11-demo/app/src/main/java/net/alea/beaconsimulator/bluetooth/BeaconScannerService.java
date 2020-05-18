/****************************************************************************************
 * Copyright (c) 2016, 2017, 2019 Vincent Hiribarren                                    *
 *                                                                                      *
 * This program is free software; you can redistribute it and/or modify it under        *
 * the terms of the GNU General Public License as published by the Free Software        *
 * Foundation; either version 3 of the License, or (at your option) any later           *
 * version.                                                                             *
 *                                                                                      *
 * Linking Beacon Simulator statically or dynamically with other modules is making      *
 * a combined work based on Beacon Simulator. Thus, the terms and conditions of         *
 * the GNU General Public License cover the whole combination.                          *
 *                                                                                      *
 * As a special exception, the copyright holders of Beacon Simulator give you           *
 * permission to combine Beacon Simulator program with free software programs           *
 * or libraries that are released under the GNU LGPL and with independent               *
 * modules that communicate with Beacon Simulator solely through the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataGenerator and the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataParser interfaces. You may           *
 * copy and distribute such a system following the terms of the GNU GPL for             *
 * Beacon Simulator and the licenses of the other code concerned, provided that         *
 * you include the source code of that other code when and as the GNU GPL               *
 * requires distribution of source code and provided that you do not modify the         *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataGenerator and the                    *
 * net.alea.beaconsimulator.bluetooth.AdvertiseDataParser interfaces.                   *
 *                                                                                      *
 * The intent of this license exception and interface is to allow Bluetooth low energy  *
 * closed or proprietary advertise data packet structures and contents to be sensibly   *
 * kept closed, while ensuring the GPL is applied. This is done by using an interface   *
 * which only purpose is to generate android.bluetooth.le.AdvertiseData objects.        *
 *                                                                                      *
 * This exception is an additional permission under section 7 of the GNU General        *
 * Public License, version 3 (“GPLv3”).                                                 *
 *                                                                                      *
 * This program is distributed in the hope that it will be useful, but WITHOUT ANY      *
 * WARRANTY; without even the implied warranty of MERCHANTABILITY or FITNESS FOR A      *
 * PARTICULAR PURPOSE. See the GNU General Public License for more details.             *
 *                                                                                      *
 * You should have received a copy of the GNU General Public License along with         *
 * this program.  If not, see <http://www.gnu.org/licenses/>.                           *
 ****************************************************************************************/

package net.alea.beaconsimulator.bluetooth;

import android.Manifest;
import android.app.AlarmManager;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.bluetooth.le.BluetoothLeScanner;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.ServiceConnection;
import android.content.pm.PackageManager;
import android.os.Binder;
import android.os.Build;
import android.os.IBinder;
import android.os.ParcelUuid;
import android.os.SystemClock;
import android.support.annotation.Nullable;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.NotificationCompat;
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
import java.sql.Timestamp;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TreeMap;
import java.util.TreeSet;
import java.util.UUID;


public class BeaconScannerService extends Service {

    private static final Logger sLogger = LoggerFactory.getLogger(BeaconScannerService.class);

    private static final int SCAN_TIME_MILLIS = 5000;
    private static final long SCAN_EXIT_TIME_MILLIS = 5 * 60 * 1000; // 5 mins

    private static final String PREFIX = "net.alea.beaconsimulator.service.";
    public static final String ACTION_START = PREFIX + "ACTION_START";
    public static final String ACTION_SCHEDULED = PREFIX + "ACTION_SCHEDULED";
    public static final String ACTION_STOP = PREFIX + "ACTION_STOP";
    public static final String ACTION_STOP_ALL = PREFIX + "ACTION_STOP_ALL";
    public static final String EXTRA_BEACON_STORE_ID = PREFIX + "EXTRA_BEACON_STORE_ID";
    public static final String EXTRA_USER_TRIGGERED = PREFIX + "EXTRA_USER_TRIGGERED";

    public static final int NOTIFICATION_ID = 1;
    public static final String CHANNEL_ID = "status";

    private static BeaconScannerService sInstance;

    private AlarmManager mAlarmManager;
    private BluetoothAdapter mBtAdapter;
    private BluetoothLeScanner mBtScanner;

    private Map<UUID, PendingIntent> mScheduledPendingIntents;
    private SocialBeaconStore mSocialBeaconStore;
    private boolean mIsScanning = false;

    // TODO Should I deprecate ServiceControl in favor of static methods?
    public class ServiceControl extends Binder {
        /* FIXME Warning, 3 ways to know number of running beacons: ServiceControl, SocialBeaconStore running list, BroadcastChangeEvent
         * ServiceControl is for current status, SocialBeaconStore to persist status, BroadcastChangEvent for changes in status. */
        public Set<String> getScannedList() {
            return mSocialBeaconStore.activeBeacons();
        }
        public boolean isScanning() {
            return mIsScanning;
        }
    }

    private final ServiceControl mBinder = new ServiceControl();

    private final BroadcastReceiver mBroadcastReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            switch (intent.getAction()) {
                case BluetoothAdapter.ACTION_STATE_CHANGED: {
                    final int btState = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, -1);
                    switch (btState) {
                        case BluetoothAdapter.STATE_TURNING_OFF:
                            //context.stop(); TODO
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
        mAlarmManager = (AlarmManager)getSystemService(ALARM_SERVICE);
        mBtAdapter = ((BluetoothManager)getSystemService(BLUETOOTH_SERVICE)).getAdapter();

        mScheduledPendingIntents = new HashMap<>();
        mSocialBeaconStore = ((App)getApplication()).getSocialBeaconStore();
        registerReceiver(mBroadcastReceiver, new IntentFilter(BluetoothAdapter.ACTION_STATE_CHANGED));
        EventBus.getDefault().register(this);
        sInstance = this;
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        //updateNotification(); // Called as soon as possible to avoid ANR: startForegroundService/startForeground sequence
        String action = intent.getAction();
        switch (action) {
            case ACTION_START: {
                sLogger.info("Action: starting new broadcast");
                startBeaconScan();
                break;
            }
            case ACTION_SCHEDULED: {
                sLogger.info("Action: processing scheduled update");
                handleExitEvent();
                if (isScanning()) {
                    //stopBeaconScan();
                    //handleExitEvent();
                }
                else {
                    //startBeaconScan();
                }
                break;
            }
            case ACTION_STOP_ALL:
            case ACTION_STOP: {
                sLogger.debug("Action: stopping a broadcast");
                stopBeaconScan();
                break;
            }

            default: {
                sLogger.warn("Unknown action asked");
            }

        }
        return START_NOT_STICKY;
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return mBinder;
    }

    @Override
    public void onDestroy() {
        sInstance = null;
        super.onDestroy();
        sLogger.debug("onDestroy() called");
        stopBeaconScan();
        unregisterReceiver(mBroadcastReceiver);
        EventBus.getDefault().unregister(this);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onBeaconDeleted(BeaconDeletedEvent event) {
            //BeaconScannerService.startScanning(this);
    }

    @SuppressWarnings("unused")
    @Subscribe
    public void onBeaconChanged(BeaconChangedEvent event) {
        /*if (mAdvertiseCallbacks.containsKey(event.getBeaconId())) {
            BeaconScannerService.stopBeaconScan(this, event.getBeaconId(), false);
            BeaconScannerService.startBeaconScan(this, event.getBeaconId(), false);
        }

         */
    }

    private void updateBroadcast(int serviceStartId, UUID id) {
        stopBeaconScan();
        startBeaconScan();
    }


    public void addScanResult(ScanResult scanResult) {

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
        }


        socialModel.updateRssi(rssi);

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

    private void handleExitEvent() {
        List<String> mIds = mSocialBeaconStore.allBeacons();
        for(String id : mIds) {
            SocialBeaconModel sm = mSocialBeaconStore.getBeacon(UUID.fromString(id));
            if((sm != null) && (sm.getEventState() != SocialBeaconModel.EXIT_EVENT)) {
                Timestamp ts = sm.getExitTime();
                Timestamp now = SocialBeaconModel.getCurrentTimestamp();
                if((now.getTime() - ts.getTime()) > SCAN_EXIT_TIME_MILLIS) {
                    sm.setExitEvent();
                    mSocialBeaconStore.saveBeacon(sm);
                }
            }

        }

    }

    private void startBeaconScan() {
        if (mIsScanning) {
            return;
        }
        sLogger.debug("Starting scan of beacons");
        mIsScanning = true;

        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        mBtScanner = mBtAdapter.getBluetoothLeScanner();

        mBtScanner.startScan(null, builder.build(), mScanCallback);

        mBtScanner = mBtAdapter.getBluetoothLeScanner();
        if (mBtScanner == null || !mBtAdapter.isEnabled()) {
            sLogger.warn("Bluetooth is off, doing nothing");
            return;
        }
        mBtScanner.startScan(null, builder.build(), mScanCallback);
        setScheduledUpdate(SCAN_TIME_MILLIS);
    }

    private void stopBeaconScan() {
        if (! mIsScanning) {
            return;
        }
        sLogger.debug("Stopping scan of beacons");
        mIsScanning = false;
        if (mBtAdapter.getState() == BluetoothAdapter.STATE_ON) {
            mBtScanner.stopScan(mScanCallback);
        }
        setScheduledUpdate(SCAN_TIME_MILLIS);
    }

    private void setScheduledUpdate(long timestamp) {
        Intent intent = new Intent(getApplicationContext(), BeaconScannerService.class);
        intent.setAction(ACTION_SCHEDULED);
        PendingIntent pendingIntent = PendingIntent.getService(getApplicationContext(), (int)System.currentTimeMillis(), intent, PendingIntent.FLAG_UPDATE_CURRENT);
        mScheduledPendingIntents.put(null, pendingIntent);
        mAlarmManager.setExact(AlarmManager.RTC_WAKEUP, timestamp, pendingIntent);
    }


    public static void startScanning(Context context ) {
        final Intent intent = new Intent(context, BeaconScannerService.class);
        intent.setAction(ACTION_START);
        ContextCompat.startForegroundService(context, intent);
    }

    public static void bindService(Context context, ServiceConnection serviceConnection) {
        Intent intent = new Intent(context, BeaconScannerService.class);
        context.bindService(intent, serviceConnection, Context.BIND_AUTO_CREATE);
    }

    public static void unbindService(Context context, ServiceConnection serviceConnection) {
        context.unbindService(serviceConnection);
    }

    public static boolean isBluetoothOn(Context context) {
        final BluetoothAdapter bluetoothAdapter = ((BluetoothManager)context.getSystemService(BLUETOOTH_SERVICE)).getAdapter();
        return (bluetoothAdapter != null && bluetoothAdapter.isEnabled());
    }

    @SuppressWarnings("SimplifiableIfStatement")
    public boolean isScanning() {
        return mIsScanning;
    }


}
