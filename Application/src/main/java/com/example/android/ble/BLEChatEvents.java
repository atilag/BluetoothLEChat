package com.example.android.ble;

/**
 * Created by jgomez on 4/05/16.
 */
public interface BLEChatEvents {
    void onMessage(String msg);
    void onInfo(String msg);
    void onConnectionError(String error);
}
