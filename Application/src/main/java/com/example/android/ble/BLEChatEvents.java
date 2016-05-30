package com.example.android.ble;

/**
 * Created by jgomez on 4/05/16.
 */
public interface BLEChatEvents {
    int SENT_SUCCEED = 0;
    int SENT_FAILED = 1;

    void onMessage(String msg);
    void onData(byte[] data);
    void onDataStream(byte[] data);
    void onStreamSent(int status);
    void onInfo(String msg);
    void onConnectionError(String error);
}
