package com.example.android.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.app.Activity;
import android.view.KeyEvent;
import android.view.View;
import android.webkit.WebView;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.ListView;
import android.widget.TextView;

import com.example.android.bluetoothchat.R;

import java.util.Comparator;

public class BLEDiscoveringActivity extends Activity {

    private static final int BLE_DEVICE_NOT_FOUND = -1;
    public static String EXTRA_DEVICE_ADDRESS = "device_address";
    public static String EXTRA_DEVICE_NAME = "device_name";

    private class BLEDevice {
        public String mName;
        public String mAddress;
        public int mRssi;

        BLEDevice(String name, String address, int rssi){
            mName = name; mAddress = address; mRssi = rssi;
        }
        @Override
        public String toString() {
            return mName + "@" + mAddress;
        }
    }

    ArrayAdapter<BLEDevice> mNewDevicesArrayAdapter;
    BLECentralHelper mBleChat = BLECentralHelper.getInstance();


    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_ble_discovering);

        WebView animation = (WebView)findViewById(R.id.webView);
        animation.loadUrl("file:///android_asset/discovering_animation.html");

        ListView newDevicesListView = (ListView) findViewById(R.id.bleDevicesFound);
        mNewDevicesArrayAdapter = new ArrayAdapter<BLEDevice>(this, R.layout.device_name);
        newDevicesListView.setAdapter(mNewDevicesArrayAdapter);
        newDevicesListView.setOnItemClickListener(mDeviceClickListener);

        mBleChat.init(this, mBleDiscoverCallback);
    }

    private BLEDiscoverCallback mBleDiscoverCallback = new BLEDiscoverCallback() {
        @Override
        public void onInitSuccess() {
            mBleChat.startScan();
        }

        @Override
        public void onInitFailure(String message) {
              /*TODO*/ finish();
        }

        @Override
        public void onScanResult(BluetoothDevice device, int rssi) {
            BLEDevice bleDevice = new BLEDevice(device.getName(), device.getAddress(), rssi);
            for(int i=0; i < mNewDevicesArrayAdapter.getCount(); ++i){
                if( mNewDevicesArrayAdapter.getItem(i).mAddress.compareTo(device.getAddress()) == 0 ){
                    return;
                }
            }

            mNewDevicesArrayAdapter.add(bleDevice);
            mNewDevicesArrayAdapter.sort(new Comparator<BLEDevice>() {
                @Override
                public int compare(BLEDevice first, BLEDevice second) {
                    return first.mRssi > second.mRssi ? first.mRssi : second.mRssi;
                }
            });
        }

        @Override
        public void onScanFailed(String message) {
            mBleChat.stopScan();
            finish();
        }
    };

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event)
    {
        if ((keyCode == KeyEvent.KEYCODE_BACK))
        {
            mBleChat.stopScan();
            finish();
        }
        return super.onKeyDown(keyCode, event);
    }

    private AdapterView.OnItemClickListener mDeviceClickListener
            = new AdapterView.OnItemClickListener() {
        public void onItemClick(AdapterView<?> av, View v, int arg2, long arg3) {
            mBleChat.stopScan();

            String name = ((TextView) v).getText().toString().split("@")[0];
            // Get the device MAC address, which is the last 17 chars in the View
            String info = ((TextView) v).getText().toString();
            String address = info.substring(info.length() - 17);

            // Create the result Intent and include the MAC address
            Intent intent = new Intent();
            intent.putExtra(EXTRA_DEVICE_ADDRESS, address);
            intent.putExtra(EXTRA_DEVICE_NAME, name);
            // Set result and finish this Activity
            setResult(Activity.RESULT_OK, intent);
            finish();
        }
    };
}
