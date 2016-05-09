package com.example.android.ble;

/**
 * Created by jgomez on 29/04/16.
 */
public interface BLECentralChatEvents extends BLEChatEvents {
    void onConnect();
    void onDisconnect();
    void onVersion(String version);
    void onDescription(String description);
}
