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

package net.alea.beaconsimulator.bluetooth.model;

import android.os.Parcel;
import android.os.Parcelable;

import com.google.gson.FieldNamingPolicy;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.google.gson.JsonSyntaxException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.sql.Timestamp;
import java.text.DateFormat;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.UUID;
import java.util.concurrent.TimeUnit;


public class SocialBeaconModel extends  BeaconModel implements Parcelable {

    private static final Logger sLogger = LoggerFactory.getLogger(SocialBeaconModel.class);

    public static final int ENTRY_EVENT = 10;
    public static final int EXIT_EVENT = 11;
    public static final int NO_EVENT = 0;
    private static final long ANDROID_N_MIN_SCAN_CYCLE_MILLIS = 6000; // 6 seconds

    public static final long DEFAULT_SAMPLE_EXPIRATION_MILLISECONDS = 20000; /* 20 seconds */

    private Timestamp entryTime;
    private Timestamp exitTime;
    private String macAddress;
    private String devType;
    private int rssi;
    private UUID uuid;
    private int eventState;
    private long empno;
    private long socialEmpno;
    private double distance;

    private class Measurement implements Comparable<Measurement> {
        Integer rssi;
        long timestamp;
        @Override
        public int compareTo(Measurement arg0) {
            return rssi.compareTo(arg0.rssi);
        }
    }

    /**
     * The number of rssi samples available, if known
     */
    private ArrayList<Measurement>  mRssiMeasurements = new ArrayList<Measurement>();

    private static class CurveFittedDistanceCalculator {
        //public static final String TAG = "CurveFittedDistanceCalculator";
        private double mCoefficient1 = 0.42093;
        private double mCoefficient2 = 6.9476;
        private double mCoefficient3 = 0.54992;

        //private static final Logger sLogger = LoggerFactory.getLogger(SocialBeaconModel.class);

        CurveFittedDistanceCalculator() {
            this.mCoefficient1 = 0.42093;
            this.mCoefficient2 = 6.9476;
            this.mCoefficient3 = 0.54992;
        }

        CurveFittedDistanceCalculator(double coefficient1, double coefficient2, double coefficient3) {
            this.mCoefficient1 = coefficient1;
            this.mCoefficient2 = coefficient2;
            this.mCoefficient3 = coefficient3;
        }

        double calculateDistance(int txPower, double rssi) {
            if (rssi == 0.0D || txPower == 0) {
                return -1.0D;
            } else {
                //sLogger.info("CurveFittedDistanceCalculator calculating distance based on mRssi of {} and txPower of {}", new Object[]{rssi, txPower});
                double ratio = rssi * 1.0D / (double)txPower;
                double distance;
                if (ratio < 1.0D) {
                    distance = Math.pow(ratio, 10.0D);
                } else {
                    distance = this.mCoefficient1 * Math.pow(ratio, this.mCoefficient2) + this.mCoefficient3;
                }

                //sLogger.info("CurveFittedDistanceCalculator avg mRssi: {} distance: {}", new Object[]{rssi, distance});
                return distance * 0.3048; // convert feet into meters
            }
        }
    }

    private Double calculateDistance(int txPower, double bestRssiAvailable) {
        CurveFittedDistanceCalculator dist = new CurveFittedDistanceCalculator();
        return dist.calculateDistance(txPower, bestRssiAvailable);
    }

    public static Timestamp getCurrentTimestamp() {
        long time = (System.currentTimeMillis());
        return new Timestamp(time);
    }

    public SocialBeaconModel() {
        super(null, null, null, null);
        this.entryTime = getCurrentTimestamp();
        this.macAddress = null;
        this.devType = "unknown";
        this.rssi = 0;
        this.uuid = null;
        this.eventState = NO_EVENT;
        this.exitTime = entryTime;
        this.empno = 0;
        this.socialEmpno = 0;
        this.distance = 0;
    }

    public SocialBeaconModel(Timestamp entryTime, String mac, int rssi, UUID id) {
        super(null, id, null, null);
        this.entryTime = entryTime;
        this.macAddress = mac;
        this.devType = "unknown";
        this.rssi = rssi;
        this.uuid = id;
        this.eventState = NO_EVENT;
        this.exitTime = entryTime;
        this.empno = 0;
        this.socialEmpno = id.getLeastSignificantBits();
        this.distance = 0;

    }

    public SocialBeaconModel(String mac, int rssi, UUID id, IBeacon ibeacon, long empno) {
        //long time = (System.currentTimeMillis());
        //Timestamp entryTime = new Timestamp(time);

        this(getCurrentTimestamp(), mac, rssi, id);
        this.setIBeacon(ibeacon);
        if(eventState != NO_EVENT) {
            sLogger.warn("eentState not NO_EVENT, {}", eventState);
        }
        eventState = ENTRY_EVENT;
        this.empno = empno;
        this.socialEmpno = ibeacon.getProximityUUID().getLeastSignificantBits();
        this.distance = calculateDistance(ibeacon.getPower(), rssi);
    }

    public void setSocialIBeacon(IBeacon iBeacon) {

        if((eventState != NO_EVENT)|| (getIBeacon() != null)) {
            sLogger.warn("eentState not NO_EVENT or ibeacon not null, {}", eventState);
        }

        Timestamp entryTime = getCurrentTimestamp();

        this.setIBeacon(iBeacon);
        this.uuid = iBeacon.getProximityUUID();

        eventState = ENTRY_EVENT;
        this.entryTime = entryTime;
        this.exitTime = entryTime;

        this.socialEmpno = iBeacon.getProximityUUID().getLeastSignificantBits();
        this.distance = calculateDistance(iBeacon.getPower(), rssi);
    }

    public int getEventState () { return this.eventState; }

    public String getDeviceType() { return this.devType; }

    public void setDeviceType(String devType) {
        if(!devType.equalsIgnoreCase("unknown")) {
            this.devType = devType;
        }
    }

    public int getRssi() {
        return rssi;
    }

    public void updateRssi(int rssi) {
        if((eventState != ENTRY_EVENT)) {
            sLogger.warn("eentState not ENTRY_EVENT {}", eventState);
        }
        long duration = getCurrentTimestamp().getTime() - exitTime.getTime();

        if(duration > ANDROID_N_MIN_SCAN_CYCLE_MILLIS) {
            this.entryTime = getCurrentTimestamp();
            mRssiMeasurements.clear();
        }

        this.exitTime = getCurrentTimestamp();

        Measurement mm = new Measurement();
        long now = getCurrentTimestamp().getTime();
        mm.rssi = (Integer) rssi;
        mm.timestamp = now;

        mRssiMeasurements.add(mm);

        // Compute the average rssi
        int sum = 0;

        for(int i =0; i < mRssiMeasurements.size(); i++){
            Measurement idx = mRssiMeasurements.get(i);
            if((now - idx.timestamp) > DEFAULT_SAMPLE_EXPIRATION_MILLISECONDS) {
                mRssiMeasurements.remove(idx);
            }
            else {
                sum = sum + idx.rssi;
            }
        }

        this.rssi = sum/mRssiMeasurements.size();

        this.distance = calculateDistance(getIBeacon().getPower(), this.rssi);
    }

    public Timestamp getEntryTime() {
        return entryTime;
    }

    public long getSocialEmp () {
        return socialEmpno;
    }

    public Timestamp getExitTime() {
        return exitTime;
    }

    public long getElapsedTime() {
        return exitTime.getTime() - entryTime.getTime();
    }

    public double getContactDistance() {
        /*
        if((eventState != EXIT_EVENT)) {
            sLogger.warn("eentState not in EXIT_STATE, {}", eventState);
            return calculateDistance(getIBeacon().getPower(), rssi);
        }
         */

        return distance;
    }

    public void setExitEvent() {
        if((eventState != ENTRY_EVENT)) {
            sLogger.warn("eentState not in EXIT_STATE, {}", eventState);
        }

        this.exitTime = getCurrentTimestamp();
        this.distance = calculateDistance(getIBeacon().getPower(), this.rssi);
    }

    public boolean hasSocialBeaconExpired(long inDaysExpiry) {
        //Timestamp currentTime = getCurrentTimestamp();
        long duration = getCurrentTimestamp().getTime() - this.exitTime.getTime();
        //long diffInSeconds = TimeUnit.MILLISECONDS.toSeconds(duration);
        //long diffInMinutes = TimeUnit.MILLISECONDS.toMinutes(duration);
        //long diffInHours = TimeUnit.MILLISECONDS.toHours(duration);
        long diffInDays = TimeUnit.MILLISECONDS.toDays(duration);
        return (diffInDays > inDaysExpiry);
    }

    public boolean isSocialBeaconAvailable (UUID id) {
        return (id.equals(this.uuid));
    }

    public String serializeToJson() {
        GsonBuilder gsonBuilder = new GsonBuilder();
        Gson gson = new GsonBuilder()
                .enableComplexMapKeySerialization()
                .serializeNulls()
                .setDateFormat(DateFormat.LONG)
                .serializeSpecialFloatingPointValues()
                .create();
        return gson.toJson(this) ; //, SocialBeaconModel.class);
    }

    public static SocialBeaconModel parseFromJson(String json) {
        Gson gson = new Gson();
        SocialBeaconModel beacon = null;
        try {
            beacon = gson.fromJson(json, SocialBeaconModel.class);
        }
        catch (JsonSyntaxException e) {
            sLogger.warn("Error while parsing JSON", e);
        }
        return beacon;
    }
/*
    @Override
    public String toString() {
        return serializeToJson();
    }
    */
    @Override
    public int describeContents() {
        return 0;
    }

    @Override
    public void writeToParcel(Parcel dest, int flags) {
        //super(dest);
        dest.writeSerializable(this.uuid);
        dest.writeString(this.entryTime.toString());
        dest.writeString(this.exitTime.toString());
        dest.writeString(this.macAddress);
        dest.writeInt(this.rssi);
        dest.writeLong(this.empno);
        dest.writeLong(this.socialEmpno);
        dest.writeDouble(this.distance);
        dest.writeInt(this.eventState);
        dest.writeParcelable(this.getIBeacon(), flags);
    }

    private SocialBeaconModel(Parcel in) {
        //super(in);
        this.uuid = (UUID) in.readSerializable();
        this.entryTime = Timestamp.valueOf(in.readString());
        this.exitTime = Timestamp.valueOf(in.readString());
        this.macAddress = in.readString();
        this.rssi = in.readInt();
        this.empno = in.readLong();
        this.socialEmpno = in.readLong();
        this.distance = in.readDouble();
        this.eventState = in.readInt();

        IBeacon ibeacon = in.readParcelable(IBeacon.class.getClassLoader());
        this.setIBeacon(ibeacon);
    }

    public static final Creator<SocialBeaconModel> CREATOR = new Creator<SocialBeaconModel>() {
        @Override
        public SocialBeaconModel createFromParcel(Parcel source) {
            return new SocialBeaconModel(source);
        }

        @Override
        public SocialBeaconModel[] newArray(int size) {
            return new SocialBeaconModel[size];
        }
    };
}
