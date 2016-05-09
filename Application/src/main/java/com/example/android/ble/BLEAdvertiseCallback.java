package com.example.android.ble;

import android.bluetooth.BluetoothDevice;

/**
 * Created by jgomez on 2/05/16.
 */
public interface BLEAdvertiseCallback {
    void onInitSuccess();
    void onInitFailure(String message);
    void onClientConnect(BluetoothDevice device);
    void onInfo(String info);
    void onError(String error);
}
