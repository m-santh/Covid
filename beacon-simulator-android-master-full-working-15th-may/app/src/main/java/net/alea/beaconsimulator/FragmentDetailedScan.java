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

import android.bluetooth.BluetoothDevice;
import android.bluetooth.le.ScanResult;
import android.content.Context;
import android.os.Bundle;
import android.support.annotation.Nullable;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v7.app.ActionBar;
import android.support.v7.app.AppCompatActivity;
import android.support.v7.widget.Toolbar;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.CheckBox;
import android.widget.TextView;

import com.neovisionaries.bluetooth.ble.advertising.ADManufacturerSpecific;
import com.neovisionaries.bluetooth.ble.advertising.ADPayloadParser;
import com.neovisionaries.bluetooth.ble.advertising.ADStructure;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneEID;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneTLM;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneUID;
import com.neovisionaries.bluetooth.ble.advertising.EddystoneURL;
import com.neovisionaries.bluetooth.ble.advertising.Flags;
import com.neovisionaries.bluetooth.ble.advertising.LocalName;
import com.neovisionaries.bluetooth.ble.advertising.ServiceData;
import com.neovisionaries.bluetooth.ble.advertising.TxPowerLevel;
import com.neovisionaries.bluetooth.ble.advertising.UUIDs;

import net.alea.beaconsimulator.bluetooth.BtNumbers;
import net.alea.beaconsimulator.bluetooth.ByteTools;
import net.alea.beaconsimulator.bluetooth.IBeaconParser;
import net.alea.beaconsimulator.bluetooth.model.AltBeacon;
import net.alea.beaconsimulator.bluetooth.model.BeaconModel;
import net.alea.beaconsimulator.bluetooth.model.BeaconType;
import net.alea.beaconsimulator.bluetooth.model.IBeacon;
import net.alea.beaconsimulator.bluetooth.model.SocialBeaconModel;
import net.alea.beaconsimulator.component.DialogCopyBeacon;
import net.alea.beaconsimulator.component.ViewEditAltBeacon;
import net.alea.beaconsimulator.component.ViewEditEddystoneTlm;
import net.alea.beaconsimulator.component.ViewEditEddystoneUid;
import net.alea.beaconsimulator.component.ViewEditEddystoneUrl;
import net.alea.beaconsimulator.component.ViewEditIBeacon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.net.URL;
import java.text.DecimalFormat;
import java.text.NumberFormat;
import java.text.SimpleDateFormat;
import java.util.Arrays;
import java.util.List;
import java.util.Locale;
import java.util.UUID;


public class FragmentDetailedScan extends Fragment {

    private static final Logger sLogger = LoggerFactory.getLogger(FragmentDetailedScan.class);

    public static final String SCAN_RESULT = "net.alea.beaconsimulator.SCAN_RESULT";

    private BeaconModel mBeaconModel = null;
    private SocialBeaconModel mScanResult = null;
    private LayoutInflater mLayoutInflater = null;

    private BtNumbers mBtNumbers;


    @Override
    public void onAttach(Context context) {
        super.onAttach(context);
        mBtNumbers = ((App)getActivity().getApplication()).getBtNumbers();
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
                             Bundle savedInstanceState) {
        // Inflate the layout for this fragment
        return inflater.inflate(R.layout.fragment_detailed_scan, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        Bundle bundle = getArguments();
        mLayoutInflater =  (LayoutInflater)getContext().getSystemService(Context.LAYOUT_INFLATER_SERVICE);

        mScanResult = (SocialBeaconModel) bundle.get(SCAN_RESULT);
        if (mScanResult == null) {
            return;
        }

        ViewGroup cardContainer = (ViewGroup)view.findViewById(R.id.detailedscan_linearlayout_cardlist);
        fillDeviceCard(cardContainer);
        generateCards(cardContainer); // It also fills mBeaconModel

        FloatingActionButton fab = (FloatingActionButton)view.findViewById(R.id.detailedscan_fab_copy);
	fab.hide();

        Toolbar toolbar = (Toolbar) view.findViewById(R.id.detailedscan_toolbar);
        ((AppCompatActivity)getActivity()).setSupportActionBar(toolbar);
        ActionBar actionBar =  ((AppCompatActivity)getActivity()).getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        // Problem of focus in some card showing some text higlighted even if set as not editable
        // http://stackoverflow.com/questions/6117967/how-to-remove-focus-without-setting-focus-to-another-control/
        view.findViewById(R.id.detailedscan_linearlayout_dummyfocusable).requestFocus();

    }


    private void generateCards(ViewGroup cardContainer) {

        IBeacon iBeacon = mScanResult.getIBeacon();
        if (true || iBeacon != null) {
            mBeaconModel = new BeaconModel(BeaconType.ibeacon);
            mBeaconModel.setIBeacon(iBeacon);
            fillIBeaconCard(mBeaconModel, cardContainer);
        }
        /*else {
            fillUnknownTypeCard(structure, cardContainer);
            mBeaconModel = new BeaconModel(BeaconType.raw);
        } */

    }

    private void appendCardSpace(ViewGroup container) {
        mLayoutInflater.inflate(R.layout.view_space, container);
    }


    private void fillDeviceCard(ViewGroup container) {
        View view = mLayoutInflater.inflate(R.layout.card_beacon_device, container, false);
        container.addView(view);


        TextView macView = (TextView)view.findViewById(R.id.carddevice_textview_mac);
        macView.setText(getString(R.string.card_device_mac, Long.toString(mScanResult.getSocialEmp())));

        SimpleDateFormat format = new SimpleDateFormat("MMM dd,yyyy hh:mm a");
        String date = format.format(mScanResult.getEntryTime());

        TextView entryView = (TextView)view.findViewById(R.id.carddevice_textview_entrydate);
        entryView.setText(getString(R.string.card_device_entrydate, date));

        date = format.format(mScanResult.getExitTime());
        TextView exitView = (TextView)view.findViewById(R.id.carddevice_textview_exitdate);
        exitView.setText(getString(R.string.card_device_exitdate, date));

        TextView txpowView = (TextView)view.findViewById(R.id.carddevice_textview_power);
        txpowView.setText(getString(R.string.card_device_power, mScanResult.getIBeacon().getPower()));

        TextView rssiView = (TextView)view.findViewById(R.id.carddevice_textview_rssi);
        rssiView.setText(getString(R.string.card_device_rssi, mScanResult.getRssi()));

        DecimalFormat df = new DecimalFormat("00.00");
        TextView distView = (TextView)view.findViewById(R.id.carddevice_textview_distnace);
        distView.setText(getString(R.string.card_device_distance, df.format(mScanResult.getContactDistance())));

        TextView typeView = (TextView)view.findViewById(R.id.carddevice_textview_type);
        typeView.setText(getString(R.string.card_device_bluetoothtype, mScanResult.getDeviceType()));

        appendCardSpace(container);
    }


    private void fillIBeaconCard(BeaconModel model, ViewGroup cardContainer) {
        ViewEditIBeacon iBeaconView = new ViewEditIBeacon(getContext());
        iBeaconView.loadModelFrom(model);
        iBeaconView.setEditMode(false);
        cardContainer.addView(iBeaconView);
    }

    private void fillUnknownTypeCard(ADStructure structure, ViewGroup cardContainer) {
        View view = mLayoutInflater.inflate(R.layout.card_beacon_unknown, cardContainer, false);
        cardContainer.addView(view);
        final TextView dataSizeValue = (TextView)view.findViewById(R.id.cardunknown_textview_datasize);
        dataSizeValue.setText(NumberFormat.getInstance().format(structure.getLength()));
        final TextView structureTypeValue = (TextView)view.findViewById(R.id.cardunknown_textview_type);
        final String gapType = mBtNumbers.convertGapType(structure.getType());
        if (gapType != null) {
            structureTypeValue.setText(String.format("0x%02X - %s", structure.getType(), gapType));
        }
        else {
            structureTypeValue.setText(String.format("0x%02X", structure.getType()));
        }
        final TextView unknownContent = (TextView)view.findViewById(R.id.cardunknown_textview_content);
        unknownContent.setText(ByteTools.bytesToHexWithSpaces(structure.getData()).toUpperCase());
    }

}
