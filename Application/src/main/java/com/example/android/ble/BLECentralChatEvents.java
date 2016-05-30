package com.example.android.ble;

/**
 * Created by jgomez on 29/04/16.
 */
public interface BLECentralChatEvents extends BLEChatEvents {
    int MTU_CHANGE_SUCCEED = 0;
    int MTU_CHANGE_FAILED = 1;
    void onConnect();
    void onDisconnect();
    void onVersion(String version);
    void onDescription(String description);
    void onRfcommConnect();
    void onMtuChanged(int status, int newMtu);
}
