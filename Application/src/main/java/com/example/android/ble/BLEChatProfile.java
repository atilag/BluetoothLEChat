package com.example.android.ble;

import android.bluetooth.BluetoothGatt;
import android.bluetooth.BluetoothProfile;

import java.util.UUID;

/**
 * Created by jgomez on 26/04/16.
 */
public class BLEChatProfile {
    public static UUID SERVICE_UUID = UUID.fromString("1706BBC0-88AB-4B8D-877E-2237916EE929");
    public static UUID CHARACTERISTIC_MESSAGE_UUID = UUID.fromString("275348FB-C14D-4FD5-B434-7C3F351DEA5F");
    public static UUID DESCRIPTOR_MESSAGE_UUID = UUID.fromString("45bda094-ff40-4cb8-835d-0da8742bb1eb");
    public static UUID CHARACTERISTIC_VERSION_UUID = UUID.fromString("BD28E457-4026-4270-A99F-F9BC20182E15");
    public static UUID CHARACTERISTIC_DESC_UUID = UUID.fromString("FDD00F2A-DAA3-47F5-8715-9DE659E5EB7B");
    public static UUID CHARACTERISTIC_RFCOMM_TRANSFER_UUID = UUID.fromString("34df5318-94de-4c1d-af31-31616c7fd9dd");
    public static UUID DESCRIPTOR_RFCOMM_TRANSFER_UUID = UUID.fromString("42a210d6-b6c5-4f82-a9cc-67d0e1d76a1e");
    public static UUID CHARACTERISTIC_BLE_TRANSFER_UUID = UUID.fromString("482f1096-137b-46cc-8ca8-3457c15cc433");
    public static UUID DESCRIPTOR_BLE_TRANSFER_UUID = UUID.fromString("421ecb34-bb49-4b70-a5ea-042c1f38ec32");

    public static final int SEND_INTERVAL = 100;


    private static String mVersion = "1";
    private static String mDescription = "BLEChat - Juan Gomez :_AtilA_";

    /**
     *  For Logging purposes only
     */
    public static String getStateDescription(int state) {
        switch (state) {
            case BluetoothProfile.STATE_CONNECTED:
                return "Connected";
            case BluetoothProfile.STATE_CONNECTING:
                return "Connecting";
            case BluetoothProfile.STATE_DISCONNECTED:
                return "Disconnected";
            case BluetoothProfile.STATE_DISCONNECTING:
                return "Disconnecting";
            default:
                return "Unknown State "+state;
        }
    }

    public static String getStatusDescription(int status) {
        switch (status) {
            case BluetoothGatt.GATT_SUCCESS:
                return "SUCCESS";
            default:
                return "Unknown Status "+status;
        }
    }

    public static String getVersion(){
        return mVersion;
    }

    public static String getDescription(){
        return  mDescription;
    }

}
