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

package net.alea.beaconsimulator;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.le.BluetoothLeScanner;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.support.annotation.Nullable;
import android.support.design.widget.Snackbar;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.Fragment;
import android.support.v4.content.ContextCompat;
import android.support.v7.util.SortedList;
import android.support.v7.widget.LinearLayoutManager;
import android.support.v7.widget.RecyclerView;
import android.support.v7.widget.SimpleItemAnimator;
import android.text.Html;
import android.text.Spanned;
import android.util.SparseArray;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.Eddystone;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneEID;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneTLM;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneUID;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneURL;

import net.alea.beaconsimulator.bluetooth.BeaconScannerService;
import net.alea.beaconsimulator.bluetooth.BtNumbers;
import net.alea.beaconsimulator.bluetooth.ByteTools;
import net.alea.beaconsimulator.bluetooth.ComparableScanResult;
import net.alea.beaconsimulator.bluetooth.IBeaconParser;
import net.alea.beaconsimulator.bluetooth.SocialBeaconStore;
import net.alea.beaconsimulator.bluetooth.event.BroadcastChangedEvent;
import net.alea.beaconsimulator.bluetooth.model.AltBeacon;
import net.alea.beaconsimulator.bluetooth.model.BeaconType;
import net.alea.beaconsimulator.bluetooth.model.IBeacon;
import net.alea.beaconsimulator.bluetooth.model.SocialBeaconModel;
import net.alea.beaconsimulator.component.DialogAskScanPermission;
import net.alea.beaconsimulator.component.DividerItemDecoration;

import org.greenrobot.eventbus.EventBus;
import org.greenrobot.eventbus.Subscribe;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.ByteBuffer;
import java.sql.Time;
import java.sql.Timestamp;
import java.text.DecimalFormat;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.TimerTask;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class FragmentScanner extends Fragment {

    public interface OnScannerActionDelegate {
        void onScanStatusUpdate(boolean isScanning);
    }

    private static final int SWICH_REFRESH_DELAY = 700; // in ms

    private static final Logger sLogger = LoggerFactory.getLogger(FragmentScanner.class);
    private static final long REFRESH_PERIOD_NANOS = TimeUnit.MILLISECONDS.toNanos(250);

    private ScannedBeaconAdapter mBeaconAdapter;
    private boolean mIsScanning = false;
    private BluetoothAdapter mBluetoothAdapter;
    private SocialBeaconStore mSocialBeaconStore;
    private final Handler mHandler = new Handler();
    //private BluetoothLeScanner mBleScanner;
    private BeaconScannerService.ServiceControl mServiceControl;
    private TextView mDescriptionView;
    private int nbActive = 0;

    private OnScannerActionDelegate mScannerActionDelegate;

/*
    private final ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            super.onScanResult(callbackType, result);
            sLogger.info("New BLE device found: {}", result.getDevice().getAddress());
            mBeaconAdapter.addScanResult(result);
        }
        public void onBatchScanResults(List<ScanResult> results) {
        }
        public void onScanFailed(int errorCode) {
        }
    };

*/
    @Override
    public void onAttach(Context context){
        super.onAttach(context);
        if(context instanceof OnScannerActionDelegate){
            mScannerActionDelegate = (OnScannerActionDelegate) context;
        }
    }

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        sLogger.debug("onCreate()");
        setHasOptionsMenu(true);
        setRetainInstance(true);
        mBeaconAdapter = new ScannedBeaconAdapter();
        mBeaconAdapter.registerAdapterDataObserver(new RecyclerView.AdapterDataObserver() {
            @Override
            public void onChanged() {
                //Do some task.
                //Snackbar.make(getView(), getResources().getQuantityString(R.plurals.simulator_scanning_start, nbActive, nbActive), Snackbar.LENGTH_SHORT).show();
            }
        });

        mSocialBeaconStore = ((App)getActivity().getApplicationContext()).getSocialBeaconStore();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        super.onCreateView(inflater, container, savedInstanceState);
        sLogger.debug("onCreateView()");

        View view = inflater.inflate(R.layout.fragment_scanner, container, false);

        mDescriptionView = (TextView)view.findViewById(R.id.scanner_textview_description);
        if (mBeaconAdapter.getItemCount() > 0) {
            mDescriptionView.setVisibility(View.GONE);
        }

        RecyclerView recyclerView = (RecyclerView) view.findViewById(R.id.scanned_recylclerview_beaconlist);
        recyclerView.setHasFixedSize(true);
        recyclerView.setLayoutManager(new LinearLayoutManager(getContext()));
        recyclerView.addItemDecoration(new DividerItemDecoration(getActivity()));
        recyclerView.setAdapter(mBeaconAdapter);
        // http://stackoverflow.com/questions/29331075/recyclerview-blinking-after-notifydatasetchanged
        RecyclerView.ItemAnimator animator = recyclerView.getItemAnimator();
        if (animator instanceof SimpleItemAnimator) {
            ((SimpleItemAnimator) animator).setSupportsChangeAnimations(false);
        }
        recyclerView.setAdapter(mBeaconAdapter);
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();
        // TODO Check if bluetooth adapter is null, bluetooth not supporte

        startBeaconScan();
        //BeaconScannerService.startScanning(getContext());
        //actionAddEvent();

        return view;
    }

    @Override
    public void onStart() {
        super.onStart();
        sLogger.debug("onStart() called");
        startBeaconScan();
        EventBus.getDefault().register(this);
        BeaconScannerService.startScanning(getContext());
    }

    @Override
    public void onResume() {
        super.onResume();
        sLogger.debug("onResume() called");
        if (mIsScanning) {
            updateStateScreenOn(true);
        }
        else {
            startBeaconScan();
        }
        BeaconScannerService.startScanning(getContext());
    }

    @Override
    public void onPause() {
        super.onPause();
        sLogger.debug("onPause() called");
    }

    @Override
    public void onStop() {
        super.onStop();
        sLogger.debug("onStop() called");
        if (mIsScanning && ! getActivity().isChangingConfigurations()) {
            //Toast.makeText(getContext(), getString(R.string.scanner_scan_stop), Toast.LENGTH_SHORT).show();
            stopBeaconScan();
        }
        EventBus.getDefault().unregister(this);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.fragment_scan, menu);
        App app = (App)getActivity().getApplication();
        menu.findItem(R.id.action_screen_on).setChecked(
                app.getConfig().getKeepScreenOnForScan()
        );
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.action_clear: {
                mBeaconAdapter.clearAll();
                return true;
            }
            case R.id.action_screen_on: {
                App app = (App)getActivity().getApplication();
                if (item.isChecked()) {
                    item.setChecked(false);
                    app.getConfig().setKeepScreenOnForScan(false);
                    updateStateScreenOn(false);
                }
                else {
                    item.setChecked(true);
                    updateStateScreenOn(true);
                    app.getConfig().setKeepScreenOnForScan(true);
                }
            }
        }
        return super.onOptionsItemSelected(item);
    }

    public void actionScanToggle() {
        /*
        if (mIsScanning) {
            stopBeaconScan();
        }
        else */{
            startBeaconScan();
        }
    }

    public void actionAddEvent() {
        mBeaconAdapter.addScanResult();
    }

    // http://stackoverflow.com/questions/10024739/how-to-determine-when-fragment-becomes-visible-in-viewpager
    @Override
    public void setUserVisibleHint(boolean isVisibleToUser) {
        super.setUserVisibleHint(isVisibleToUser);
        sLogger.debug("setUserVisibleHint() called, isVisibleToUser: {}", isVisibleToUser);
        if (isVisibleToUser) {
            updateScanStatusView();
        }
        else if (mIsScanning) {
            //Toast.makeText(getContext(), getString(R.string.scanner_scan_stop), Toast.LENGTH_SHORT).show();
            //stopBeaconScan();
        }
    }

    private void updateScanStatusView() {
        if (mScannerActionDelegate != null) {
            mScannerActionDelegate.onScanStatusUpdate(mIsScanning);
        }
    }

    private void startBeaconScan() {
        if (mIsScanning) {
            return;
        }
        boolean ok = checkPermission();
        if (! ok) {
/*
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //stopBeaconScan();
                    startBeaconScan();
                    BeaconScannerService.startScanning(getContext());
                }
            }, SWICH_REFRESH_DELAY);
*/

            return;
        }
        sLogger.debug("Starting scan of beacons");
        mIsScanning = true;
        updateStateScreenOn(true);
        ScanSettings.Builder builder = new ScanSettings.Builder();
        builder.setScanMode(ScanSettings.SCAN_MODE_LOW_LATENCY);
        //mBleScanner = mBluetoothAdapter.getBluetoothLeScanner();
        //mBleScanner.startScan(null, builder.build(), mScanCallback);
        updateScanStatusView();
        mBeaconAdapter.addScanResult();
        /*
        Snackbar.make(getView(), R.string.scanner_scan_start, Snackbar.LENGTH_LONG)
                .show();
                */
        //Snackbar.make(getView(), getResources().getQuantityString(R.plurals.simulator_scanning_start, nbActive, nbActive), Snackbar.LENGTH_SHORT).show();
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //stopBeaconScan();
                mBeaconAdapter.addScanResultTimed();
            }
        }, SWICH_REFRESH_DELAY);

    }

    public void stopBeaconScan() {
        if (! mIsScanning) {
            return;
        }
        sLogger.debug("Stopping scan of beacons");
        updateStateScreenOn(false);
        mIsScanning = false;
/*
        if (mBluetoothAdapter.getState() == BluetoothAdapter.STATE_ON) {
            mBleScanner.stopScan(mScanCallback);
        }
*/
        updateScanStatusView();
/*
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                //startBeaconScan();
                mBeaconAdapter.addScanResult();
            }
        }, SWICH_REFRESH_DELAY);
*/
    }

    public void updateStateScreenOn(boolean shouldKeepScreenOn) {
        final Activity activity = getActivity();
        final App app = (App)activity.getApplication();
        if (shouldKeepScreenOn && app.getConfig().getKeepScreenOnForScan() && mIsScanning) {
            activity.getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
            return;
        }
        if (!shouldKeepScreenOn) {
            activity.getWindow().clearFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);
        }
    }

    private boolean checkPermission() {
        boolean needLocation = false;
        boolean needBluetooth = false;
        // Check Android permission
        if (ContextCompat.checkSelfPermission(getActivity(),
                Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            if (ActivityCompat.shouldShowRequestPermissionRationale(getActivity(),
                    Manifest.permission.ACCESS_COARSE_LOCATION)) {
                DialogAskScanPermission dialog = DialogAskScanPermission.newInstance(true, true);
                dialog.show(getFragmentManager(), dialog.getTag());
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        0);
            }
            else {
                ActivityCompat.requestPermissions(getActivity(),
                        new String[]{Manifest.permission.ACCESS_COARSE_LOCATION},
                        0);
            }
            return false;
        }

        // Check if BL adapter on
        if (!mBluetoothAdapter.isEnabled()) {
            needBluetooth = true;
        }

        // Check if location enable
        int locationMode = Settings.Secure.getInt(getContext().getContentResolver(), Settings.Secure.LOCATION_MODE,  Settings.Secure.LOCATION_MODE_OFF);
        if (locationMode == Settings.Secure.LOCATION_MODE_OFF) {
            needLocation = true;
        }

        if (needBluetooth || needLocation) {
            DialogAskScanPermission dialog = DialogAskScanPermission.newInstance(needBluetooth, needLocation);
            dialog.show(getFragmentManager(), dialog.getTag());
            return false;
        }
        else {
            return true;
        }

    }


    public class ScannedBeaconAdapter extends RecyclerView.Adapter<ScannedBeaconAdapter.ViewHolder> {
        private final List<SocialBeaconModel> _dataset = new ArrayList<>();
        private final Map<String, Integer> _beaconPositions = new HashMap<>();

        public class ViewHolder extends RecyclerView.ViewHolder {
            public final View view;
            public final TextView rssi;
            public final TextView specificContent;
            public final TextView specificContentSub;
            public final TextView mac;
            public final ImageView beaconType;
            public ViewHolder(View v) {
                super(v);
                v.setTag(this);
                view = v;
                rssi =(TextView) v.findViewById(R.id.scannedbeacon_textview_rssi);
                mac =(TextView) v.findViewById(R.id.scannedbeacon_textview_mac);
                specificContent =(TextView) v.findViewById(R.id.scannedbeacon_textview_specific);
                specificContentSub =(TextView) v.findViewById(R.id.scannedbeacon_textview_specificsub);
                beaconType = (ImageView) v.findViewById(R.id.scannedbeacon_imageview_type);

            }
        }

        // Create new views (invoked by the layout manager)
        @Override
        public ScannedBeaconAdapter.ViewHolder onCreateViewHolder(ViewGroup parent, int viewType) {
            View v = LayoutInflater.from(parent.getContext())
                    .inflate(R.layout.item_scanned_beacon, parent, false);
            final ViewHolder viewHolder = new ViewHolder(v);
            viewHolder.view.setOnClickListener(new View.OnClickListener() {
                @Override
                public void onClick(View view) {
                    final ScannedBeaconAdapter.ViewHolder vh = (ScannedBeaconAdapter.ViewHolder)view.getTag();
                    final int adapterPosition = vh.getAdapterPosition();
                    if (adapterPosition == RecyclerView.NO_POSITION) {
                        sLogger.warn( "NO_POSITION returned when beacon is clicked, skipping");
                        return;
                    }
                    final Context context = view.getContext();
                    final Intent intent = new Intent(context, ActivityDetailedScan.class);
                    intent.putExtra(FragmentDetailedScan.SCAN_RESULT, _dataset.get(adapterPosition));
                    context.startActivity(intent);
                    getActivity().overridePendingTransition(R.anim.slide_left_in, R.anim.slide_left_out);
                }
            });
            return viewHolder;
        }

        @SuppressLint("SetTextI18n")
        @Override
        public void onBindViewHolder(ViewHolder holder, int position) {
            SocialBeaconModel scanResult = _dataset.get(position);
            //ByteBuffer emp =  ByteBuffer.allocate(Long.BYTES);
            //emp.putLong(scanResult.getSocialEmp());
            holder.mac.setText(Html.fromHtml(String.format("Neighbour ID <b>%8s</b>",Long.toString(scanResult.getSocialEmp()))));

            DecimalFormat df = new DecimalFormat("00.00");
            //String[] specificStrings = getBeaconTypeContent(scanResult);
            long duration = scanResult.getElapsedTime();
            long hours = TimeUnit.MILLISECONDS.toHours(duration);
            long minutes = TimeUnit.MILLISECONDS.toMinutes(duration);
            double distance = scanResult.getContactDistance();


            holder.rssi.setText(Html.fromHtml(String.format("<b>%4s</b>",df.format(distance))));
            int alertLevel = BeaconScannerService.getBeaconState(scanResult);
            if(alertLevel == BeaconScannerService.NO_WARNING) {
                holder.rssi.setTextColor(Color.parseColor("#41BF50"));
                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l3);
            }
            else if (alertLevel == BeaconScannerService.ACTUAL_WARNING) {
                holder.rssi.setTextColor(Color.parseColor("#00A2DB"));
                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l1);

            }
            else {
                holder.rssi.setTextColor(Color.parseColor("#B70808"));
                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l2);
            }
            /*

            if(TimeUnit.MILLISECONDS.toMinutes(visibleTime) > 5) {

                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l3);
            }
            else if((minutes > 5) || ( distance < 1.0)) {
                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l1);

            }
            else {

                holder.beaconType.setImageResource(R.drawable.ic_social_distance_l2);
            }

             */

            SimpleDateFormat format = new SimpleDateFormat("MMM dd,yyyy hh:mm a");
            String date = format.format(scanResult.getExitTime());

            format = new SimpleDateFormat("hh:mm");
            String time = format.format(new Timestamp(duration));
            long visibleTime = SocialBeaconModel.getCurrentTimestamp().getTime() - scanResult.getExitTime().getTime();


            //df = new DecimalFormat("00");
            Spanned typeMessage = Html.fromHtml(String.format("Duration Time (HH:MM) <b>%s</b>", time)); //Long.toString(hours), Long.toString(minutes) ));
            Spanned subTypeMessage; //= Html.fromHtml(String.format("Last Update <b>%s</b>", "<font color='red'>" + date + "</font>"));
            if(TimeUnit.MILLISECONDS.toMinutes(visibleTime) < 1 ) { //(SWICH_REFRESH_DELAY + BeaconScannerService.DELTA_TIME_MILLIS)) {
                //String newDate = "<font color='red'>" + date + "</font>";
                subTypeMessage = Html.fromHtml(String.format("Last Update <b>%s</b>", "<font color='red'>" + date + "</font>"));
            }
            else {
                subTypeMessage = Html.fromHtml(String.format("Last Update <b>%s</b>", date ));
            }

            holder.specificContent.setText(typeMessage);
            holder.specificContent.setVisibility(typeMessage.toString().isEmpty() ? View.GONE : View.VISIBLE);
            holder.specificContentSub.setText(subTypeMessage);
            holder.specificContentSub.setVisibility(subTypeMessage.toString().isEmpty() ? View.GONE : View.VISIBLE);

            //Snackbar.make(getView(), getResources().getQuantityString(R.plurals.simulator_scanning_start, nbActive, nbActive), Snackbar.LENGTH_INDEFINITE).show();
        }


        @Override
        public int getItemCount() {
            return _dataset.size();
        }

        public void onChange() {
            //super.notifyDataSetChanged();
            //Snackbar.make(getView(), getResources().getQuantityString(R.plurals.simulator_scanning_start, nbActive, nbActive), Snackbar.LENGTH_SHORT).show();
        }

        public void addScanResultTimed() {
            addScanResult();
            mHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    //stopBeaconScan();
                    mBeaconAdapter.addScanResultTimed();
                }
            }, SWICH_REFRESH_DELAY);
        }
        public void addScanResult() {
            List<String> beaconId = mSocialBeaconStore.allBeacons();
            int lnbActive = 0;

            Comparator<String> compareById = new Comparator<String>() {
                @Override
                public int compare(String o1, String o2) {
                    SocialBeaconModel first = mSocialBeaconStore.getBeacon(UUID.fromString(o1));
                    SocialBeaconModel two = mSocialBeaconStore.getBeacon(UUID.fromString(o2));
                    Timestamp t1 = first.getExitTime();
                    Timestamp t2 = two.getExitTime();
                    return (int)(t2.getTime() - t1.getTime());
                }
            };

            Collections.sort(beaconId, compareById);

            Timestamp now = SocialBeaconModel.getCurrentTimestamp();

            _dataset.clear();
            _beaconPositions.clear();

            for (String id: beaconId) {
                SocialBeaconModel sm = mSocialBeaconStore.getBeacon(UUID.fromString(id));
                if(sm.getEventState() == SocialBeaconModel.EXIT_EVENT) {
                    // dont add
                    continue;
                }
                _dataset.add(sm);
                _beaconPositions.put(id, _dataset.lastIndexOf(sm));
                if((now.getTime()  - sm.getExitTime().getTime()) < (SWICH_REFRESH_DELAY + BeaconScannerService.DELTA_TIME_MILLIS)) {
                    lnbActive++;
                }
            }

            nbActive = lnbActive;
            notifyDataSetChanged();

            mDescriptionView.setVisibility(View.GONE);
            /*
            ActivityMain app = (ActivityMain) App.getInstance().getActivityMain();
            FragmentScanner fg = app.getFragmentScanner();
            int count = fg.getScanActive();
            Snackbar.make(fg.getView(), getResources().getQuantityString(R.plurals.simulator_scanning_start, count, count), Snackbar.LENGTH_SHORT).show();


             */

            //EventBus.getDefault().post(new BroadcastChangedEvent(new UUID(0,0), false, -1));
        }

        public void clearAll() {
            int oldSize = _dataset.size();
            _dataset.clear();
            _beaconPositions.clear();
            notifyItemRangeRemoved(0, oldSize);
            mDescriptionView.setVisibility(View.VISIBLE);
            addScanResult();
        }
    }

    public int getScanActive() {
        return nbActive;


        //updateStopBroadcastMenu();
    }

    @Subscribe
    public void onBroadcastChangedEvent(BroadcastChangedEvent event) {
        final int id =  event.getActiveBroadcast();

        sLogger.info("Starting scan of beacons onbroadcast");
        if(id != -1) {
            return;
        }
        Snackbar.make(getView(), getResources().getQuantityString(R.plurals.simulator_broadcast_start, nbActive, nbActive), Snackbar.LENGTH_INDEFINITE).show();

    }

}
