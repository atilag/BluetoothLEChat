package com.example.android.ble;

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothGattCharacteristic;
import android.bluetooth.BluetoothGattDescriptor;
import android.bluetooth.BluetoothGattServer;
import android.bluetooth.BluetoothGattServerCallback;
import android.bluetooth.BluetoothGattService;
import android.bluetooth.BluetoothManager;
import android.bluetooth.le.AdvertiseCallback;
import android.bluetooth.le.AdvertiseData;
import android.bluetooth.le.AdvertiseSettings;
import android.bluetooth.le.BluetoothLeAdvertiser;
import android.content.Context;
import android.content.pm.PackageManager;
import android.os.Handler;
import android.os.ParcelUuid;
import android.util.Log;

import java.io.UnsupportedEncodingException;
import java.util.ArrayList;

/**
 * This singleton helper class will manage all the Bluetooth LE Peripheral stuff
 * A Peripheral BLE device is basically a device which can advertise services so other
 * Central devices can connect to it, so yes, is like a server.
 *
 * It communicates with other classes via Interfaces/Events like
 * * BLEPeripheralChatEvents
 * * BLEAdvertiseCallback
 *
 * Use un/register( objectImplementingInterface ) to start receiving events
 *
 */
public class BLEPeripheralHelper {

    private static final String TAG = "BLEPeripheralHelper";

    private BluetoothManager mBluetoothManager;
    private BluetoothAdapter mBluetoothAdapter;
    private BluetoothLeAdvertiser mBluetoothLeAdvertiser;
    private BluetoothGattServer mGattServer;
    private Context mContext;
    //private BLEAdvertiseCallback mBleAdvCallback;
    private ArrayList<BLEAdvertiseCallback> mAdvListeners = new ArrayList<>();
    private ArrayList<BLEPeripheralChatEvents> mChatListeners = new ArrayList<>();

    private ArrayList<BluetoothDevice> mConnectedDevices;
    private Object mLock = new Object();
    private Handler mHandler = new Handler();


    private static BLEPeripheralHelper instance = new BLEPeripheralHelper();
    private BLEPeripheralHelper(){}
    public static BLEPeripheralHelper getInstance(){
        if(instance == null){
            synchronized (BLEPeripheralHelper.class){
                if(instance == null){
                    instance = new BLEPeripheralHelper();
                }
            }
        }
        return instance;
    }

    public void register(BLEAdvertiseCallback advListener){
        mAdvListeners.add(advListener);
    }
    public void register(BLEPeripheralChatEvents chatEventListener){
        mChatListeners.add(chatEventListener);
    }
    public void unregister(BLEAdvertiseCallback advListener){
        mAdvListeners.remove(advListener);
    }
    public void unregister(BLEPeripheralChatEvents chatEventListener){
        mChatListeners.remove(chatEventListener);
    }

    /**
     * Only these events could happen while advertising
     */
    private enum NotifyAdvAction {
        NOTIFY_ADV_ACTION_INIT_FAILURE,
        NOTIFY_ADV_ACTION_INIT_SUCCESS,
        NOTIFY_ADV_ACTION_CLIENT_CONNECT,
        NOTIFY_ADV_ACTION_INFO,
        NOTIFY_ADV_ACTION_CONNECTION_ERROR,
    };

    /**
     * These events could only happen while devices are connected
     */
    private enum NotifyChatAction {
        NOTIFY_CHAT_ACTION_MESSAGE,
        NOTIFY_CHAT_ACTION_INFO,
        NOTIFY_CHAT_ACTION_CLIENT_DISCONNECT,
        NOTIFY_CHAT_ACTION_CONNECTION_ERROR,
    };

    private void notifyAdvListeners(NotifyAdvAction action, Object data){
        for (BLEAdvertiseCallback listener: mAdvListeners) {
            switch (action){
                case NOTIFY_ADV_ACTION_INIT_SUCCESS:
                    listener.onInitSuccess();
                    break;
                case NOTIFY_ADV_ACTION_INIT_FAILURE:
                    listener.onInitFailure((String)data);
                    break;
                case NOTIFY_ADV_ACTION_CLIENT_CONNECT:
                    listener.onClientConnect((BluetoothDevice)data);
                    break;
                case NOTIFY_ADV_ACTION_INFO:
                    listener.onInfo((String)data);
                    break;
                case NOTIFY_ADV_ACTION_CONNECTION_ERROR:
                    listener.onError((String)data);
                    break;
            }
        }
    }

    private void notifyChatListeners(NotifyChatAction action, Object data){
        for (BLEPeripheralChatEvents listener: mChatListeners) {
            switch (action){
                case NOTIFY_CHAT_ACTION_MESSAGE:
                    listener.onMessage((String)data);
                    break;
                case NOTIFY_CHAT_ACTION_INFO:
                    listener.onInfo((String)data);
                    break;
                case NOTIFY_CHAT_ACTION_CLIENT_DISCONNECT:
                    listener.onClientDisconnect((BluetoothDevice)data);
                    break;
                case NOTIFY_CHAT_ACTION_CONNECTION_ERROR:
                    listener.onConnectionError((String)data);
                    break;
            }
        }
    }


    public void init(Context context){
        if( context == null ){
            notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_INIT_FAILURE, "Context cannot be null!!");
            return;
        }
        mContext = context;
        mConnectedDevices = new ArrayList<BluetoothDevice>();
        mBluetoothManager = (BluetoothManager) mContext.getSystemService(Context.BLUETOOTH_SERVICE);
        mBluetoothAdapter = mBluetoothManager.getAdapter();
        if (mBluetoothAdapter == null || !mBluetoothAdapter.isEnabled()){
            //mBleAdvCallback.onInitFailure("Bluetooth not supported in this device!!");
            notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_INIT_FAILURE, "Bluetooth not supported in this device!!");
            return;
        }

        if (!mContext.getPackageManager().hasSystemFeature(PackageManager.FEATURE_BLUETOOTH_LE)) {
            //mBleAdvCallback.onInitFailure("Bluetooth LE is not supported in this devices!!");
            notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_INIT_FAILURE, "Bluetooth LE is not supported in this devices!!");
            return;
        }
        //mBleAdvCallback.onInitSuccess();
        notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_INIT_SUCCESS, null);
    }

    private BluetoothGattServerCallback mGattServerCallback = new BluetoothGattServerCallback() {
        @Override
        public void onConnectionStateChange(BluetoothDevice device, int status, int newState) {
            super.onConnectionStateChange(device, status, newState);
            Log.i(TAG, "onConnectionStateChange "
                    + BLEChatProfile.getStatusDescription(status)+" "
                    + BLEChatProfile.getStateDescription(newState));
            if(status == BluetoothGatt.GATT_SUCCESS ) {
                if (newState == BluetoothGatt.STATE_CONNECTED) {
                    mConnectedDevices.add(device);
                    notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_CLIENT_CONNECT, device);
                } else if (newState == BluetoothGatt.STATE_DISCONNECTED) {
                    mConnectedDevices.remove(device);
                    notifyChatListeners(NotifyChatAction.NOTIFY_CHAT_ACTION_CLIENT_DISCONNECT, device);
                }
            }else{
                String error = "Error:" + status;
                notifyChatListeners(NotifyChatAction.NOTIFY_CHAT_ACTION_CONNECTION_ERROR,error);
                notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_CONNECTION_ERROR,error);
            }
        }

        @Override
        public void onCharacteristicReadRequest(BluetoothDevice device,
                                                int requestId,
                                                int offset,
                                                BluetoothGattCharacteristic characteristic) {
            super.onCharacteristicReadRequest(device, requestId, offset, characteristic);
            Log.i(TAG, "onCharacteristicReadRequest " + characteristic.getUuid().toString());
            if (BLEChatProfile.CHARACTERISTIC_VERSION_UUID.equals(characteristic.getUuid())) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        getCharacteristicVersionValue());
            }else if (BLEChatProfile.CHARACTERISTIC_DESC_UUID.equals(characteristic.getUuid())) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        0,
                        getCharacteristicDescValue());
            }

        }

        @Override
        public void onCharacteristicWriteRequest(BluetoothDevice device,
                                                 int requestId,
                                                 BluetoothGattCharacteristic characteristic,
                                                 boolean preparedWrite,
                                                 boolean responseNeeded,
                                                 int offset,
                                                 byte[] value) {
            super.onCharacteristicWriteRequest(device, requestId, characteristic, preparedWrite, responseNeeded, offset, value);
            Log.i(TAG, "onCharacteristicWriteRequest " + characteristic.getUuid().toString());
            if (BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID.equals(characteristic.getUuid())) {
                int gatResult = BluetoothGatt.GATT_SUCCESS;
                try {
                    String msg = new String(value, "UTF-8");
                    notifyChatListeners(NotifyChatAction.NOTIFY_CHAT_ACTION_MESSAGE, msg);

                    for (BluetoothDevice connectedDevice : mConnectedDevices) {
                        BluetoothGattCharacteristic msgCharacteristic = mGattServer.getService(BLEChatProfile.SERVICE_UUID)
                                .getCharacteristic(BLEChatProfile.CHARACTERISTIC_DESC_UUID);
                        msgCharacteristic.setValue(msg.getBytes());
                        mGattServer.notifyCharacteristicChanged(connectedDevice, msgCharacteristic, false);
                    }

                } catch (UnsupportedEncodingException ex) {
                    notifyChatListeners(NotifyChatAction.NOTIFY_CHAT_ACTION_CONNECTION_ERROR, ex.toString());
                    gatResult = BluetoothGatt.GATT_FAILURE;
                } finally {
                    if (responseNeeded) {
                        mGattServer.sendResponse(device,
                                requestId,
                                gatResult,
                                0,
                                value);
                    }
                }

            }
        }

        @Override
        public void onDescriptorWriteRequest (BluetoothDevice device,
                                              int requestId, BluetoothGattDescriptor descriptor,
                                              boolean preparedWrite, boolean responseNeeded,
                                              int offset, byte[] value) {
            if (responseNeeded) {
                mGattServer.sendResponse(device,
                        requestId,
                        BluetoothGatt.GATT_SUCCESS,
                        offset,
                        value);
            }
        }
    };

    private void initService(){
        mGattServer = mBluetoothManager.openGattServer(mContext, mGattServerCallback);

        BluetoothGattService service =new BluetoothGattService(BLEChatProfile.SERVICE_UUID,
                BluetoothGattService.SERVICE_TYPE_PRIMARY);

        BluetoothGattCharacteristic messageCharacteristic =
                new BluetoothGattCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID,
                        //Read-write characteristic, supports notifications
                        BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor messageDesc = new BluetoothGattDescriptor(BLEChatProfile.DESCRIPTOR_MESSAGE_UUID,
                        BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        messageCharacteristic.addDescriptor(messageDesc);

        BluetoothGattCharacteristic versionCharacteristic =
                new BluetoothGattCharacteristic(BLEChatProfile.CHARACTERISTIC_VERSION_UUID,
                        //Read-only characteristic
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic descriptionCharacteristic =
                new BluetoothGattCharacteristic(BLEChatProfile.CHARACTERISTIC_DESC_UUID,
                        //Read-write characteristic, supports notifications
                        BluetoothGattCharacteristic.PROPERTY_READ,
                        BluetoothGattCharacteristic.PERMISSION_READ);

        BluetoothGattCharacteristic transferRateCharacteristic =
                new BluetoothGattCharacteristic(BLEChatProfile.CHARACTERISTIC_TRANSFER_RATE_UUID,
                        //Read-write characteristic, supports notifications
                        BluetoothGattCharacteristic.PROPERTY_READ | BluetoothGattCharacteristic.PROPERTY_WRITE | BluetoothGattCharacteristic.PROPERTY_NOTIFY,
                        BluetoothGattCharacteristic.PERMISSION_READ | BluetoothGattCharacteristic.PERMISSION_WRITE);
        BluetoothGattDescriptor transferRateDesc = new BluetoothGattDescriptor(BLEChatProfile.DESCRIPTOR_TRANSFER_RATE_UUID,
                BluetoothGattDescriptor.PERMISSION_WRITE | BluetoothGattDescriptor.PERMISSION_READ);
        messageCharacteristic.addDescriptor(transferRateDesc);


        service.addCharacteristic(descriptionCharacteristic);
        service.addCharacteristic(versionCharacteristic);
        service.addCharacteristic(messageCharacteristic);
        service.addCharacteristic(transferRateCharacteristic);

        mGattServer.addService(service);
    }

    private void advertiseService(){
        AdvertiseSettings settings = new AdvertiseSettings.Builder()
                .setAdvertiseMode(AdvertiseSettings.ADVERTISE_MODE_BALANCED)
                .setConnectable(true)
                .setTimeout(0)
                .setTxPowerLevel(AdvertiseSettings.ADVERTISE_TX_POWER_MEDIUM)
                .build();

        AdvertiseData data = new AdvertiseData.Builder()
                .setIncludeDeviceName(true)
                .addServiceUuid(new ParcelUuid(BLEChatProfile.SERVICE_UUID))
                .build();

        /*int MANUFACTURER_ID = 0x45;
        byte [] manufacturerData = {
                0x30, 0x31, 0x32, 0x33, 0x34, 0x35, 0x36, 0x37, 0x38, 0x39,
                0x40, 0x41, 0x42, 0x43, 0x44, 0x45, 0x46, 0x47, 0x48, 0x49,
                //0x50, 0x51, 0x52, 0x53, 0x54, 0x55, 0x56, 0x57, 0x58, 0x59,
                //0x60, 0x61, 0x62, 0x63, 0x64, 0x65, 0x66, 0x67, 0x68, 0x69,
                //0x70, 0x71, 0x72, 0x73, 0x74, 0x75, 0x76, 0x77, 0x78, 0x79
        };

        AdvertiseData data = new AdvertiseData.Builder()
                .addManufacturerData(MANUFACTURER_ID, manufacturerData)
                .build();*/

        mBluetoothLeAdvertiser.startAdvertising(settings, data, mAdvertiseCallback);
    }

    public void startAdvertising() {

        if (!mBluetoothAdapter.isMultipleAdvertisementSupported()) {
            postStatusMessage("Bluetooth LE Peripheral mode is not supported in this device!!");
            return;
        }

        if (mBluetoothLeAdvertiser == null){
            mBluetoothLeAdvertiser = mBluetoothAdapter.getBluetoothLeAdvertiser();
            if(mBluetoothLeAdvertiser == null){
                postStatusMessage("Error initializing BLE Advertiser");
                return;
            }
        }

        initService();
        advertiseService();
    }

    /*
     * Terminate the advertiser
     */
    public void stopAdvertising() {
        if (mBluetoothLeAdvertiser == null)
            return;

        mBluetoothLeAdvertiser.stopAdvertising(mAdvertiseCallback);
    }


    private AdvertiseCallback mAdvertiseCallback = new AdvertiseCallback() {
        @Override
        public void onStartSuccess(AdvertiseSettings settingsInEffect) {
            Log.i(TAG, "Peripheral Advertise Started.");
            postStatusMessage("GATT Server Ready");
        }

        @Override
        public void onStartFailure(int errorCode) {
            Log.w(TAG, "Peripheral Advertise Failed: "+errorCode);
            postStatusMessage("GATT Server Error "+errorCode);
        }
    };

    private void postStatusMessage(final String message) {
        mHandler.post(new Runnable() {
            @Override
            public void run() {
                notifyAdvListeners(NotifyAdvAction.NOTIFY_ADV_ACTION_INFO, message);
            }
        });
    }

    private byte[] getCharacteristicVersionValue(){
        synchronized (mLock){
            return BLEChatProfile.getVersion().getBytes();
        }
    }

    private byte[] getCharacteristicDescValue(){
        synchronized (mLock){
            return BLEChatProfile.getDescription().getBytes();
        }
    }

    public void send(String msg) {
        for (BluetoothDevice device : mConnectedDevices) {
            BluetoothGattCharacteristic msgCharacteristic = mGattServer.getService(BLEChatProfile.SERVICE_UUID)
                    .getCharacteristic(BLEChatProfile.CHARACTERISTIC_MESSAGE_UUID);
            msgCharacteristic.setValue(msg);
            mGattServer.notifyCharacteristicChanged(device, msgCharacteristic, false);
        }
    }
}
