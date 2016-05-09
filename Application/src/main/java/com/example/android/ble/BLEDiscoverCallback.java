package com.example.android.ble;

import android.bluetooth.BluetoothDevice;

/**
 * Created by jgomez on 27/04/16.
 */
public interface BLEDiscoverCallback {
    void onInitSuccess();
    void onInitFailure(String message);
    void onScanResult(BluetoothDevice device, int rssi);
    void onScanFailed(String message);
}
