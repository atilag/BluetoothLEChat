package com.example.android.ble;

import android.app.Activity;
import android.bluetooth.BluetoothDevice;
import android.content.Intent;
import android.os.Bundle;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.webkit.WebView;
import android.widget.TextView;

import com.example.android.bluetoothchat.R;

public class BLEAdvertisingActivity extends FragmentActivity {

    public static String EXTRA_CLIENT_NAME = "ble_client_name";

    BLEPeripheralHelper mBleChat = BLEPeripheralHelper.getInstance();

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_advertising);
        WebView animation = (WebView)findViewById(R.id.webView2);
        animation.loadUrl("file:///android_asset/advertising_animation.html");
        mBleChat.register(mBLEAdvCallback);
        mBleChat.init(getApplicationContext());
    }

    public void setConsoleText(String text){
        TextView t = (TextView)findViewById(R.id.adv_text_console);
        t.setText(text);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            mBleChat.stopAdvertising();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private BLEAdvertiseCallback mBLEAdvCallback = new BLEAdvertiseCallback(){

        @Override
        public void onInitSuccess() {
            mBleChat.startAdvertising();
        }

        @Override
        public void onInitFailure(String message) {
            setConsoleText(message);
        }

        @Override
        public void onClientConnect(BluetoothDevice device){
            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            //intent.putExtra(EXTRA_CLIENT_NAME, device.getName());

            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            mBleChat.unregister(mBLEAdvCallback);
            //mBleChat.stopAdvertising();
            finish();
        }

        @Override
        public void onInfo(String info){
            setConsoleText(info);
        }

        @Override
        public void onError(String error){
            setResult(Activity.RESULT_CANCELED);
            finish();
        }
    };
}
