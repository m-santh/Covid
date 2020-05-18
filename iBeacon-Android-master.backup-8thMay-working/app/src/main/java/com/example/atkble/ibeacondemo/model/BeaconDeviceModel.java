package com.example.atkble.ibeacondemo.model;

import java.util.List;

/**
 * Created by atkble on 10/9/2017 AD.
 */

public class BeaconDeviceModel {

    public List<String> deviceID;

    public BeaconDeviceModel() {

    }

    public BeaconDeviceModel(List<String> deviceID) {
        this.deviceID = deviceID;
    }

    public List<String> getDeviceID() {
        return deviceID;
    }

    @Override
    public String toString() {
        return "BeaconDeviceModel{" +
                "deviceID=" + deviceID +
                '}';
    }
}
