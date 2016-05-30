package com.example.android.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCallback;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothProfile;
import android.bluetooth.BluetoothSocket;
import android.bluetooth.le.ScanCallback;
import android.bluetooth.le.ScanFilter;
import android.bluetooth.le.ScanResult;
import android.bluetooth.le.ScanSettings;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Random;
import java.util.UUID;
import java.util.concurrent.RunnableFuture;


/**
 * This class will manage the Bluetooth LE Central stuff
 * A Central BLE device is a device which can connect to another Peripheral device that is
 * advertising services. So it's basically a client.
 *
 * It communicates with the outer space via events like:
 * * BLEDiscoverCallback
 * * BLECentralChatEvents
 *
 * Use init() no initialize the class
 *
 */
public class BLECentralHelper {

    private static final String TAG = "BLECentralHelper";

    // Name for the SDP record when creating server socket
    private static final String NAME_SECURE = "BluetoothLEChatSecure";
    private static final String NAME_INSECURE = "BluetoothLEChatInsecure";
    // Unique UUID for this application
    private static final UUID MY_UUID_SECURE =
            UUID.fromString("caa8f277-6b87-49fb-a11b-ab9c9dacbd44");
    private static final UUID MY_UUID_INSECURE =
            UUID.fromString("83769a57-e930-4496-8ece-fec16420c77c");

    private static final int MAX_RETRIES = 5;

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothGatt mConnectedGatt;
    /* Test RFCOMMSocket connection */
    private BluetoothSocket mSocket;
    private String mRfcommSocketAddress;
    private BLEDiscoverCallback mBleDiscoveryCallback;
    private BLECentralChatEvents mBleChatEvents;

    private Handler mHandler = new Handler();

    private Context mContext;

    private static BLECentralHelper instance = new BLECentralHelper();
    private BLECentralHelper(){}
    public static BLECentralHelper getInstance(){
        if(instance == null){
            synchronized (BLECentralHelper.class){
                if(instance == null){
                    instance = new BLECentralHelper();
                }
            }
        }
        return instance;
    }

    public void init(Context context, BLEDiscoverCallback bleCallback){
        mContext = context;
        mBleDiscoveryCallback = bleCallback;
        if( context == null){
            mBleDiscoveryCallback.onInitFailure("Invalid Context!");
            return;
        }
        mBluetoothManager = (BluetoothManager) context.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            mBleDiscoveryCallback.onInitFailure("Bluetooth not supported in this device!!");
            return;
        }

        if (!context.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            mBleDiscoveryCallback.onInitFailure("Bluetooth LE is not supported in this devices!!");
            return;
        }


        mBleDiscoveryCallback.onInitSuccess();
    }

    /**
     * This is a passive action, it will listen advertisements from other peripheral devices
     */
    public void startScan() {

        ScanFilter scanFilter = new ScanFilter.Builder()
                .setServiceUuid(new ParcelUuid(BLEChatProfile.SERVICE_UUID))
                .build();
        ArrayList<ScanFilter> filters = new ArrayList<ScanFilter>();
        filters.add(scanFilter);

        ScanSettings settings = new ScanSettings.Builder()
                .setScanMode(ScanSettings.SCAN_MODE_BALANCED)
                .build();
        mBluetoothAdapter.getBluetoothLeScanner().startScan(filters, settings, mScanCallback);
    }

    public void stopScan() {
        mBluetoothAdapter.getBluetoothLeScanner().stopScan(mScanCallback);
    }

    private ScanCallback mScanCallback = new ScanCallback() {
        @Override
        public void onScanResult(int callbackType, ScanResult result) {
            Log.d(TAG, "onScanResult");
            processResult(result);
        }

        @Override
        public void onBatchScanResults(List<ScanResult> results) {
            Log.d(TAG, "onBatchScanResults: "+results.size()+" results");
            for (ScanResult result : results) {
                processResult(result);
            }
        }

        @Override
        public void onScanFailed(int errorCode) {
            Log.w(TAG, "LE Scan Failed: "+errorCode);
        }

        private void processResult(ScanResult result) {
            BluetoothDevice device = result.getDevice();
            Log.i(TAG, "New LE Device: " + device.getName() + " @ " + result.getRssi());
            mBleDiscoveryCallback.onScanResult(device, result.getRssi());
        }
    };

    /*
     * Connect to a Bluetooth device
     *
     * @param context
     * @param device
     * @param events
     */
    public void connect(Context context, BluetoothDevice device, BLECentralChatEvents events){
        mBleChatEvents = events;
        mConnectedGatt = device.connectGatt(context, false, mGattCallback);
    }

    /**
     * Will try to connect to a RFCOMMSocket previously announced by the Peripheral device
     * WARNING: This is just a test. It may fail...
     *
     *
     */
    private void connect2RfcommSocket(){
        IntentFilter filter = new IntentFilter(BluetoothDevice.ACTION_FOUND);
        mContext.registerReceiver(mReceiver, filter);
        // Register for broadcasts when discovery has finished
        filter = new IntentFilter(BluetoothAdapter.ACTION_DISCOVERY_FINISHED);
        mContext.registerReceiver(mReceiver, filter);
        mBluetoothAdapter.startDiscovery();

    }

    private BroadcastReceiver mReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            // When discovery finds a device
            if (BluetoothDevice.ACTION_FOUND.equals(action)) {
                // Get the BluetoothDevice object from the Intent
                BluetoothDevice classicBtDevice = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                if (mRfcommSocketAddress.compareTo(classicBtDevice.getAddress()) == 0) {
                    try {
                        mSocket = classicBtDevice.createInsecureRfcommSocketToServiceRecord(MY_UUID_INSECURE);
                        mSocket.connect();
                        mBleChatEvents.onRfcommConnect();
                    } catch (IOException e) {
                        try {
                            mSocket.close();
                        } catch (IOException e2) {
                            mBleChatEvents.onConnectionError(e2.toString());
                        }
                        mBleChatEvents.onConnectionError(e.toString());
                    }
                }
                // When discovery is finished, change the Activity title
            } else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED.equals(action)) {

            }
        }
    };

    public BluetoothGattCallback mGattCallback = new BluetoothGattCallback(){
        @Override
        public void onConnectionStateChange(BluetoothGatt gatt, int status, int newState) {
            super.onConnectionStateChange(gatt, status, newState);
            Log.d(TAG, "onConnectionStateChange "
                    +BLEChatProfile.getStatusDescription(status)+" "
                    +BLEChatProfile.getStateDescription(newState));

            if(status == BluetoothGatt.GATT_SUCCESS) {
                if (newState == BluetoothProfile.STATE_CONNECTED) {
                    gatt.discoverServices();
                } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                    mHandler.post(new Runnable() {
                        @Override
                        public void run() {
                            mBleChatEvents.onDisconnect();
                        }
                    });

                }
            }else{
                final int finalStatus = status;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onConnectionError("Connection state error! : Error = " + finalStatus);
                    }
                });

            }
        }

        @Override
        public void onServicesDiscovered(BluetoothGatt gatt, int status) {
            super.onServicesDiscovered(gatt, status);
            Log.d(TAG, "onServicesDiscovered:");

            for (BluetoothGattService service : gatt.getServices()) {
                Log.d(TAG, "Service: "+service.getUuid());
                if (BLEChatProfile.SERVICE_UUID.equals(service.getUuid())) {
                    gatt.readCharacteristic(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_VERSION_UUID));
                    gatt.readCharacteristic(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_DESC_UUID));
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID), true);
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_RFCOMM_TRANSFER_UUID), true);
                    gatt.setCharacteristicNotification(service.getCharacteristic(BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID), true);
                }
            }
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBleChatEvents.onConnect();
                }
            });
        }

        @Override
        public void onCharacteristicRead(BluetoothGatt gatt,
                                         final BluetoothGattCharacteristic characteristic,
                                         int status) {
            super.onCharacteristicRead(gatt, characteristic, status);
            if (BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
                final String msg = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onMessage(msg);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
            if (BLEChatProfile.CHARACTERISTIC_VERSION_UUID.equals(characteristic.getUuid())) {
                final String version = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onVersion(version);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
            if (BLEChatProfile.CHARACTERISTIC_DESC_UUID.equals(characteristic.getUuid())) {
                final String description = characteristic.getStringValue(0);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onDescription(description);
                    }
                });

                //Register for further updates as notifications
                gatt.setCharacteristicNotification(characteristic, true);
            }
        }

        @Override
        public void onCharacteristicWrite (BluetoothGatt gatt,
                                    BluetoothGattCharacteristic characteristic,
                                    int status){
            if (BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID.equals(characteristic.getUuid())){
                final int chatStatus = (status == BluetoothGatt.GATT_SUCCESS ? BLEChatEvents.SENT_SUCCEED : BLEChatEvents.SENT_FAILED);
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onStreamSent(chatStatus);
                    }
                });
            }

        }

        @Override
        public void onMtuChanged (BluetoothGatt gatt,
                           int mtu,
                           int status){
            final int chatStatus = (status == BluetoothGatt.GATT_SUCCESS ? BLECentralChatEvents.MTU_CHANGE_SUCCEED : BLECentralChatEvents.MTU_CHANGE_FAILED);
            mMtu = mtu;
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBleChatEvents.onMtuChanged(chatStatus, mMtu);
                }
            });
        }


        @Override
        public void onCharacteristicChanged(BluetoothGatt gatt,
                                            final BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicChanged(gatt, characteristic);
            Log.i(TAG, "Notification of message characteristic changed on server.");
            if (BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onMessage(characteristic.getStringValue(0));
                    }
                });
            } else if (BLEChatProfile.CHARACTERISTIC_RFCOMM_TRANSFER_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mRfcommSocketAddress = characteristic.getStringValue(0);
                        connect2RfcommSocket();
                        //mBleChatEvents.onTransfer(characteristic.getStringValue(0));
                    }
                });
            } else if (BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID.equals(characteristic.getUuid())) {
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mBleChatEvents.onInfo("BLE_TRANS Charac changed!!");
                    }
                });
            }
        }
    }; //End BluetoothGattCallback

    public void send(byte[] data) {
        final BluetoothGattCharacteristic characteristic = mConnectedGatt
                .getService(BLEChatProfile.SERVICE_UUID)
                .getCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID);

        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT);
        characteristic.setValue(data);
        if(!mConnectedGatt.writeCharacteristic(characteristic)){
            mBleChatEvents.onConnectionError("Couldn't send data!!");
        }
    }

    public void send(String msg){
        send(msg.getBytes());
    }

    /**
     *
     */
    public void sendFile(final Uri uri){
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                try{
                    InputStream is = mContext.getContentResolver().openInputStream(uri);
                    ByteArrayOutputStream bos = new ByteArrayOutputStream();
                    byte[] b = new byte[1024];
                    int bytesRead = 0;
                    while ((bytesRead = is.read(b)) != -1) {
                        bos.write(b, 0, bytesRead);
                    }
                    send2Rfcomm(bos.toByteArray());
                }catch(FileNotFoundException ex){

                }catch(IOException ex){

                }
            }
        });
    }

    /**
     *
     * @param data
     */
    private synchronized void send2Rfcomm(byte[] data){
        try {
            OutputStream stream = mSocket.getOutputStream();
            stream.write(data);
        }catch (IOException e){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBleChatEvents.onConnectionError("Error sending message to RFCOMM Socket");
                }
            });

        }
    }


    /**
     * Sends a MTU size block of data
     */
    public synchronized void sendData() {

        //byte[] data = getAlphabetDataBlock(mMtu);
        byte[] data = new byte[mMtu];

        final BluetoothGattCharacteristic characteristic = mConnectedGatt
                .getService(BLEChatProfile.SERVICE_UUID)
                .getCharacteristic(BLEChatProfile.CHARACTERISTIC_BLE_TRANSFER_UUID);

        characteristic.setValue(data);
        characteristic.setWriteType(BluetoothGattCharacteristic.WRITE_TYPE_NO_RESPONSE);
        int iRetries = 0;
        while (!mConnectedGatt.writeCharacteristic(characteristic)) {
            try {
                if (iRetries > MAX_RETRIES) {
                    mBleChatEvents.onConnectionError("Couldn't send more data!!");
                    return;
                }
                iRetries++;
                Log.d(TAG, "Error sending data. Retrying... " + iRetries);
                // We are in StreamThread thread.... so we can sleep
                Thread.sleep(BLEChatProfile.SEND_INTERVAL);
            } catch (InterruptedException ex) {
                mBleChatEvents.onConnectionError("Interrupted while sleeping!!");
                return;
            }
        }
    }


    /**
     * Gets a block of numElems size of the alphabet. Subsequent calls to this method
     * will start the new block with the next letter of the alphabet.
     */
    private int mLetterCounter = 97;
    private byte[] getAlphabetDataBlock(int numElems){
        String string = "";

        for(int e = 0; e < numElems - 3; e++){
            string += String.valueOf((char)mLetterCounter);
            mLetterCounter = (mLetterCounter > 121 ? 97 : mLetterCounter + 1);
        }
        byte [] data = string.getBytes();
        byte [] finalData = new byte[data.length + 3];
        byte [] header = {0,0,0};


        System.arraycopy(data, 0, finalData, 0, data.length);
        System.arraycopy(header,0, finalData, finalData.length - 3, header.length );
        return finalData;
    }


    /**
     * Default BLE MTU is 20
     */
    private int mMtu = 20;

    /**
     * Changes MTU.
     * This will trigger onMtuChanged() callback
     * @param size
     */
    public void changeMtu(int size){
        if (!mConnectedGatt.requestMtu(size)) {
            Log.d(TAG,"Couldn't set MTU!!");
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mBleChatEvents.onConnectionError("Couldn't set MTU!!");
                }
            });
            return;
        }
        Log.d(TAG, "MTU set to " + size);
    }


    /**
     * Sets the final MTU
     */
    public void setMtu(int size){
        mMtu = size;
    };

    /**
     * Gets the final MTU
     */
    public int getMtu(){
        return mMtu;
    }


}
