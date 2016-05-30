/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.bluetoothchat;

import android.app.ActionBar;
import android.app.Activity;
import android.app.ProgressDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Environment;
import android.os.Handler;
import android.os.Message;
import android.support.annotation.Nullable;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentActivity;
import android.view.KeyEvent;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.inputmethod.EditorInfo;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ListView;
import android.widget.TextView;
import android.widget.Toast;

import com.example.android.ble.BLEAdvertisingActivity;
import com.example.android.ble.BLECentralHelper;
import com.example.android.ble.BLECentralChatEvents;
import com.example.android.ble.BLEChatEvents;
import com.example.android.ble.BLEDiscoveringActivity;
import com.example.android.ble.BLEMode;
import com.example.android.ble.BLEPeripheralChatEvents;
import com.example.android.ble.BLEPeripheralHelper;
import com.example.android.common.logger.Log;

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.util.concurrent.Semaphore;

/**
 * This fragment controls Bluetooth to communicate with other devices.
 */
public class BluetoothChatFragment extends Fragment {

    private static final String TAG = "BluetoothChatFragment";
    private BLEMode mBleMode = BLEMode.CENTRAL;

    // Intent request codes
    private static final int REQUEST_CONNECT_DEVICE_SECURE = 1;
    private static final int REQUEST_CONNECT_DEVICE_INSECURE = 2;
    private static final int REQUEST_ENABLE_BT = 3;
    private static final int BLE_REQUEST_CONNECT_DEVICE = 11;
    private static final int BLE_REQUEST_DEVICE_CONNECTING = 12;
    private static final int PICK_IMAGE = 21;

    // Layout Views
    private ListView mConversationView;
    private EditText mOutEditText;
    private Button mSendButton;

    /**
     * Name of the connected device
     */
    private String mConnectedDeviceName = null;

    /**
     * Array adapter for the conversation thread
     */
    private ArrayAdapter<String> mConversationArrayAdapter;

    /**
     * String buffer for outgoing messages
     */
    private StringBuffer mOutStringBuffer;

    /**
     * Local Bluetooth adapter
     */
    private BluetoothAdapter mBluetoothAdapter = null;

    /**
     * Member object for the chat services
     */
    private BluetoothChatService mChatService = null;

    /**
     * My Progress bar for transfer rates
     */
    private ProgressDialog mProgressBar = null;
    private int mProgressBarStatus = 0;

    private StreamThread mStreamThread = null;


    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setHasOptionsMenu(true);
        // Get local Bluetooth adapter
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        // If the adapter is null, then Bluetooth is not supported
        if (mBluetoothAdapter == null) {
            FragmentActivity activity = getActivity();
            Toast.makeText(activity, "Bluetooth is not available", Toast.LENGTH_LONG).show();
            activity.finish();
        }

        setupProgressBar(getContext());
    }

    @Override
    public void onStart() {
        super.onStart();
        // If BT is not on, request that it be enabled.
        // setupChat() will then be called during onActivityResult
        if (!mBluetoothAdapter.isEnabled()) {
            Intent enableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
            startActivityForResult(enableIntent, REQUEST_ENABLE_BT);
            // Otherwise, setup the chat session
        } else if (mChatService == null) {
            setupChat();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (mChatService != null) {
            mChatService.stop();
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        // Performing this check in onResume() covers the case in which BT wasbuffer
        // not enabled during onStart(), so we were paused to enable it...
        // onResume() will be called when ACTION_REQUEST_ENABLE activity returns.
        if (mChatService != null) {
            // Only if the state is STATE_NONE, do we know that we haven't started already
            if (mChatService.getState() == BluetoothChatService.STATE_NONE) {
                // Start the Bluetooth chat services
                mChatService.start();
            }
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_bluetooth_chat, container, false);
    }

    @Override
    public void onViewCreated(View view, @Nullable Bundle savedInstanceState) {
        mConversationView = (ListView) view.findViewById(R.id.in);
        mOutEditText = (EditText) view.findViewById(R.id.edit_text_out);
        mSendButton = (Button) view.findViewById(R.id.button_send);
    }

    /**
     * Set up the UI and background operations for chat.
     */
    private void setupChat() {
        Log.d(TAG, "setupChat()");

        // Initialize the array adapter for the conversation thread
        mConversationArrayAdapter = new ArrayAdapter<String>(getActivity(), R.layout.message);

        mConversationView.setAdapter(mConversationArrayAdapter);

        // Initialize the compose field with a listener for the return key
        mOutEditText.setOnEditorActionListener(mWriteListener);

        // Initialize the send button with a listener that for click eventsbuffer
        mSendButton.setOnClickListener(new View.OnClickListener() {
            public void onClick(View v) {
                // Send a message using content of the edit text widget
                View view = getView();
                if (null != view) {
                    TextView textView = (TextView) view.findViewById(R.id.edit_text_out);
                    String message = textView.getText().toString();
                    processOutgoingMsg(message);
                }
            }
        });

        // Initialize the BluetoothChatService to perform bluetooth connections
        mChatService = new BluetoothChatService(getActivity(), mHandler);

        // Initialize the buffer for outgoing messages
        mOutStringBuffer = new StringBuffer("");
    }

    /**
     * Makes this device discoverable.
     */
    private void ensureDiscoverable() {
        if (mBluetoothAdapter.getScanMode() !=
                BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE) {
            Intent discoverableIntent = new Intent(BluetoothAdapter.ACTION_REQUEST_DISCOVERABLE);
            discoverableIntent.putExtra(BluetoothAdapter.EXTRA_DISCOVERABLE_DURATION, 300);
            startActivity(discoverableIntent);
        }
    }

    private synchronized void processOutgoingMsg(String message){
        if(message.startsWith("/")){
            String[] tokens = message.split(" ", 2);
            if(tokens[0].compareTo("/transfertest") == 0){
                // We change the MTU to 512 first
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        BLECentralHelper.getInstance().changeMtu(512);
                    }
                }, 2100);
                return;
            }else if(tokens[0].compareTo("/transfer") == 0){
                sendStream();
                return;
            }
        }
        sendMessage(message);
    }

    private void sendStream(){
        if(mBleMode == BLEMode.PERIPHERAL ){
            BLEPeripheralHelper.getInstance().sendStream();
        }else if(mBleMode == BLEMode.CENTRAL){
            BLECentralHelper.getInstance().sendData();
        }
    }

    private synchronized void sendMessage(String message) {
        if(mBleMode != BLEMode.NONE)
            sendMessageViaBLE(message);
        else
            sendMessageViaClassicBT(message);
    }

    private synchronized void sendMessageViaClassicBT(String message) {
        // Check that we're actually connected before trying anything
        if (mChatService.getState() != BluetoothChatService.STATE_CONNECTED) {
            Toast.makeText(getActivity(), R.string.not_connected, Toast.LENGTH_SHORT).show();
            return;
        }

        // Check that there's actually something to send
        if (message.length() > 0) {
            // Get the message bytes and tell the BluetoothChatService to write
            byte[] send = message.getBytes();
            mChatService.write(send);

            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * The action listener for the EditText widget, to listen for the return key
     */
    private TextView.OnEditorActionListener mWriteListener
            = new TextView.OnEditorActionListener() {
        public boolean onEditorAction(TextView view, int actionId, KeyEvent event) {
            // If the action is a key-up event on the return key, send the message
            if (actionId == EditorInfo.IME_NULL && event.getAction() == KeyEvent.ACTION_UP) {
                String message = view.getText().toString();
                sendMessage(message);
            }
            return true;
        }
    };

    /**
     * Updates the status on the action bar.
     *
     * @param resId a string resource ID
     */
    private void setStatus(int resId) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(resId);
    }

    /**
     * Updates the status on the action bar.
     *
     * @param subTitle status
     */
    private void setStatus(CharSequence subTitle) {
        FragmentActivity activity = getActivity();
        if (null == activity) {
            return;
        }
        final ActionBar actionBar = activity.getActionBar();
        if (null == actionBar) {
            return;
        }
        actionBar.setSubtitle(subTitle);
    }

    /**
     * This method is part of an automatic ping-ping conversation like.
     * @param msg
     */
    private void answerBack(String msg){
        final String mMsg = msg;
        mHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                Log.i(TAG, "ConversationThread::run()");
                String response = (mMsg.equalsIgnoreCase("PING") ? "PONG" : "PING");
                try {
                    Thread.sleep(2000);
                } catch (InterruptedException ex) {
                    ex.printStackTrace();
                }
                sendMessage(response);
            }
        }, 2000);
    }


    /**
     *
     *  Some helper methods for the mHandler messaging mechanism
     *
     **/

    private void showIncomingMessage(String msg){
        mHandler.obtainMessage(Constants.MESSAGE_READ, msg.length(), -1, msg.getBytes())
                .sendToTarget();
    }

    private void showOutgoingMessage(String msg){
        mHandler.obtainMessage(Constants.MESSAGE_WRITE, -1, -1, msg.getBytes())
                .sendToTarget();
    }

    private void showInfo(String info){
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.TOAST, info);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        Log.i(TAG, info);
    }

    private void showStatus(int status){
        mHandler.obtainMessage(Constants.MESSAGE_STATE_CHANGE, status, -1)
                .sendToTarget();
    }

    private void showConnectedName(String name){
        Message msg = mHandler.obtainMessage(Constants.MESSAGE_DEVICE_NAME);
        Bundle bundle = new Bundle();
        bundle.putString(Constants.DEVICE_NAME, name);
        msg.setData(bundle);
        mHandler.sendMessage(msg);
        setState(BluetoothChatService.STATE_CONNECTED);
    }

    private void setState(int newState){
        switch (newState) {
            case BluetoothChatService.STATE_CONNECTED:
                setStatus(getString(R.string.title_connected_to, mConnectedDeviceName));
                //mConversationArrayAdapter.clear();
                break;
            case BluetoothChatService.STATE_CONNECTING:
                setStatus(R.string.title_connecting);
                break;
            case BluetoothChatService.STATE_LISTEN:
            case BluetoothChatService.STATE_NONE:
                setStatus(R.string.title_not_connected);
                break;
        }
    }

    /**
     * The Handler that gets information back from the BluetoothChatService
     */
    private final Handler mHandler = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            FragmentActivity activity = getActivity();
            switch (msg.what) {
                case Constants.MESSAGE_STATE_CHANGE:
                    setState(msg.arg1);
                    break;
                case Constants.MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
                    // construct a string from the buffer
                    String writeMessage = new String(writeBuf);
                    mConversationArrayAdapter.add("Me:  " + writeMessage);
                    break;
                case Constants.MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;
                    // construct a string from the valid bytes in the buffer
                    String readMessage = new String(readBuf, 0, msg.arg1);
                    mConversationArrayAdapter.add(mConnectedDeviceName + ":  " + readMessage);
                    //answerBack(readMessage);
                    break;
                case Constants.MESSAGE_DEVICE_NAME:
                    // save the connected device's name
                    mConnectedDeviceName = msg.getData().getString(Constants.DEVICE_NAME);
                    if (null != activity) {
                        Toast.makeText(activity, "Connected to "
                                + mConnectedDeviceName, Toast.LENGTH_SHORT).show();
                    }
                    break;
                case Constants.MESSAGE_TOAST:
                    if (null != activity) {
                        Toast.makeText(activity, msg.getData().getString(Constants.TOAST),
                                Toast.LENGTH_SHORT).show();
                    }
                    break;
            }
        }
    };



    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        switch (requestCode) {
            case REQUEST_CONNECT_DEVICE_SECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, true);
                }
                break;
            case REQUEST_CONNECT_DEVICE_INSECURE:
                // When DeviceListActivity returns with a device to connect
                if (resultCode == Activity.RESULT_OK) {
                    connectDevice(data, false);
                }
                break;
            case REQUEST_ENABLE_BT:
                // When the request to enable Bluetooth returns
                if (resultCode == Activity.RESULT_OK) {
                    // Bluetooth is now enabled, so set up a chat session
                    setupChat();
                } else {
                    // User did not enable Bluetooth or an error occurred
                    Log.d(TAG, "BT not enabled");
                    Toast.makeText(getActivity(), R.string.bt_not_enabled_leaving,
                            Toast.LENGTH_SHORT).show();
                    getActivity().finish();
                }
                break;
            case BLE_REQUEST_CONNECT_DEVICE:
                if( resultCode == Activity.RESULT_OK) {
                    connectBleDevice(data);
                }
                break;
            case BLE_REQUEST_DEVICE_CONNECTING:
                if( resultCode == Activity.RESULT_OK){
                    bleDeviceConnecting(data);
                }
                break;
            case PICK_IMAGE:
                if( resultCode == Activity.RESULT_OK){
                    sendFile(data);
                }
        }
    }

    /**
     * Establish connection with other device
     *
     * @param data   An {@link Intent} with {@link DeviceListActivity#EXTRA_DEVICE_ADDRESS} extra.
     * @param secure Socket Security type - Secure (true) , Insecure (false)
     */
    private void connectDevice(Intent data, boolean secure) {
        // Get the device MAC address
        String address = data.getExtras()
                .getString(DeviceListActivity.EXTRA_DEVICE_ADDRESS);
        // Get the BluetoothDevice object
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        mBleMode = BLEMode.NONE;
        // Attempt to connect to the device
        mChatService.connect(device, secure);
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        inflater.inflate(R.menu.bluetooth_chat, menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.secure_connect_scan: {
                if(mChatService != null)
                    mChatService.start();
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_SECURE);
                return true;
            }
            case R.id.insecure_connect_scan: {
                if(mChatService != null)
                    mChatService.start();
                // Launch the DeviceListActivity to see devices and do scan
                Intent serverIntent = new Intent(getActivity(), DeviceListActivity.class);
                startActivityForResult(serverIntent, REQUEST_CONNECT_DEVICE_INSECURE);
                return true;
            }
            case R.id.discoverable: {
                // Ensure this device is discoverable by others
                ensureDiscoverable();
                return true;
            }
            case R.id.ble_advertise: {
                startAdvertising();
                return true;
            }
            case R.id.ble_discover: {
                startScanning();
                return true;
            }

        }
        return false;
    }

    public BluetoothChatService getChatService(){
        return mChatService;
    }


    /**
     *
     *****************************
     * Bluetooth LE Specific code.
     *****************************
     *
     */

    /**
     * Starts BLE Advertisement
     */
    private void startAdvertising() {
        Intent advertisementIntent = new Intent(getContext(), BLEAdvertisingActivity.class);
        startActivityForResult(advertisementIntent, BLE_REQUEST_DEVICE_CONNECTING);
    }

    private void startScanning(){
        Intent scanningIntent = new Intent(getActivity(), BLEDiscoveringActivity.class);
        startActivityForResult(scanningIntent, BLE_REQUEST_CONNECT_DEVICE);
    }

    private synchronized void sendMessageViaBLE(String message) {
        // Check that there's actually something to send
        if (message.length() > 0) {
            if(mBleMode == BLEMode.PERIPHERAL){
                BLEPeripheralHelper.getInstance().send(message);
            }else if(mBleMode == BLEMode.CENTRAL){
                BLECentralHelper.getInstance().send(message);
            }
            showOutgoingMessage(message);
            // Reset out string buffer to zero and clear the edit text field
            mOutStringBuffer.setLength(0);
            mOutEditText.setText(mOutStringBuffer);
        }
    }

    /**
     * Sends the selected file to the Peripheral device
     * @param data
     */
    private void sendFile(Intent data){
        Uri uri = data.getData();
        BLECentralHelper.getInstance().sendFile(uri);
    }

    /**
     * [Central device role]
     *
     * Connect to a Peripheral device
     * @param data
     */
    private void connectBleDevice(Intent data){
        mBleMode = BLEMode.CENTRAL;
        if(mChatService != null)
            mChatService.stop();
        mConnectedDeviceName = data.getExtras().getString(BLEDiscoveringActivity.EXTRA_DEVICE_NAME);
        String address = data.getExtras().getString(BLEDiscoveringActivity.EXTRA_DEVICE_ADDRESS);
        BluetoothDevice device = mBluetoothAdapter.getRemoteDevice(address);
        showStatus(BluetoothChatService.STATE_CONNECTING);
        BLECentralHelper.getInstance().connect(this.getContext(), device, mBLEChatEvents);
    }


    private BLECentralChatEvents mBLEChatEvents = new BLECentralChatEvents() {
        private Object mLock = new Object();
        @Override
        public void onVersion(String version) {
            synchronized (mLock){
                showInfo("Version: " + version);
            }
        }

        @Override
        public void onDescription(String description) {
            synchronized (mLock){
                showIncomingMessage("Description: " + description);
            }
        }

        @Override
        public void onMessage(String msg) {
            synchronized (mLock){
                processIncomingMsg(msg);
            }
        }

        @Override
        public void onInfo(String info){
            synchronized (mLock) {
                showInfo(info);
            }
        }

        @Override
        public void onConnect(){
            synchronized (mLock){
                mHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        sendMessage("/name Z3C");
                        showConnectedName(mConnectedDeviceName);
                        showStatus(BluetoothChatService.STATE_CONNECTED);
                    }
                }, 2000);
            }
        }

        @Override
        public void onDisconnect(){
            synchronized (mLock) {
                Message msg = mHandler.obtainMessage(Constants.MESSAGE_TOAST);
                Bundle bundle = new Bundle();
                bundle.putString(Constants.TOAST, new String("[!] Disconnected"));
                msg.setData(bundle);
                mHandler.sendMessage(msg);

                showStatus(BluetoothChatService.STATE_NONE);
                if(mProgressBar!=null){
                    mProgressBar.dismiss();
                    mProgressBar.hide();
                }

            }
        }

        @Override
        public void onConnectionError(String error){
            synchronized (mLock){
                if(mStreamThread!=null)
                    mStreamThread.end();

                mProgressBar.dismiss();
                mProgressBar.cancel();

                showStatus(BluetoothChatService.STATE_NONE);
                showInfo("[!] Error : " + error);

            }
        }

        @Override
        public void onRfcommConnect(){
            synchronized (mLock) {
                Intent intent = new Intent();
                intent.setType("image/*");
                intent.setAction(Intent.ACTION_GET_CONTENT);
                startActivityForResult(Intent.createChooser(intent, "Select Picture"), PICK_IMAGE);
            }
        }

        @Override
        public void onData(byte [] data){
            synchronized (mLock){
                showInfo("Not implemented yet");
            }
        }

        private int mLastLength = 0;
        @Override
        public void onDataStream(byte[] data){
            synchronized (mLock){
                if( mLastLength != data.length ) {
                    showInfo("Received " + data.length + " bytes via BLE!");
                    mLastLength = data.length;
                }
                //showIncomingMessage(new String(data));
            }
        }

        @Override
        public void onStreamSent(int status){
            synchronized (mLock) {
                if (status == BLEChatEvents.SENT_SUCCEED) {
                    mStreamThread.nextMessage();
                } else {
                    mStreamThread.end();
                }
            }
        }


        @Override
        public void onMtuChanged(int status, int newMtu){
            synchronized (mLock) {
                if (status == BLECentralChatEvents.MTU_CHANGE_SUCCEED) {
                    showInfo("MTU changed to " + newMtu);
                } else {
                    showInfo("Error changing MTU. Falling back to " +  newMtu + " ...");
                }
                // Once the MTU has been changed, we start the thread for the transfer rate test
                mStreamThread = new StreamThread();
                mStreamThread.start();
            }
        }
    };


    /**
     * [Peripheral device role]
     *
     * A Central device is connecting to us
     * @param data
     */
    private void bleDeviceConnecting(Intent data){
        mBleMode = BLEMode.PERIPHERAL;
        if(mChatService != null)
            mChatService.stop();
        //showConnectedName(data.getExtras().getString(BLEAdvertisingActivity.EXTRA_CLIENT_NAME));
        showStatus(BluetoothChatService.STATE_CONNECTED);
        BLEPeripheralHelper.getInstance().register(mBlePeripheralChatEvents);
    }
    private BLEPeripheralChatEvents mBlePeripheralChatEvents = new BLEPeripheralChatEvents() {
        private Object mLock = new Object();

        @Override
        public void onClientDisconnect(BluetoothDevice device) {
            synchronized (mLock){
                showInfo(device.getName() + " disconnected");
                setStatus(R.string.title_not_connected);
            }
        }

        @Override
        public void onMessage(String msg) {
            synchronized (mLock){
                processIncomingMsg(msg);
            }
        }

        @Override
        public void onInfo(String info) {
            synchronized (mLock){
                showInfo(info);
            }
        }

        @Override
        public void onConnectionError(String error) {
            synchronized (mLock){
                if(mProgressBar != null){
                    mProgressBar.dismiss();
                    mProgressBar.hide();
                }

                showInfo("[!] Error : " + error);
            }
        }

        @Override
        public void onInitRfcommSocket(){
            synchronized (mLock) {
                ensureDiscoverable();
                showInfo("RFCOMM: Socket listening...");
            }
        }

        @Override
        public void onConnectRfcommSocket(){
            synchronized (mLock){
                showInfo("RFCOMM: Client connected");
            }
        }

        @Override
        public void onData(byte [] data){
            synchronized (mLock) {
                save2File(data);
            }
        }

        private int mLastLength = 0;
        private long mStartingTime = 0;
        private long mBytesPerSec = 0;
        @Override
        public void onDataStream(byte[] data){
            synchronized (mLock){
                if( mLastLength != data.length ) {
                    showInfo("Received " + data.length + " bytes via BLE!");
                    mLastLength = data.length;
                    mHandler.post(new Runnable() {
                      @Override
                      public void run() {
                          mProgressBar.show();
                      }
                    });
                }

                long now = System.currentTimeMillis();
                if( mStartingTime == 0 ){
                    mStartingTime = now;
                }
                if( mStartingTime + 1000 <= now ){
                    showInfo( mBytesPerSec + " B/s");
                    mBytesPerSec = mStartingTime = 0;
                }

                //showIncomingMessage("Msg length: " + data.length);
                mBytesPerSec += data.length;
                mProgressBarStatus += data.length;
                mHandler.post(new Runnable() {
                    @Override
                    public void run() {
                        mProgressBar.setProgress(mProgressBarStatus);
                    }
                });

            }
        }

        @Override
        public void onStreamSent(int status){
            if(status == BLEChatEvents.SENT_SUCCEED){
                mStreamThread.nextMessage();
            }else{
                mStreamThread.end();
                showInfo("Error sending data!!");
            }
        }

    };

    private void processIncomingMsg(String msg){
        if(msg.startsWith("/")){
            String[] tokens = msg.split(" ", 2);
            if(tokens[0].compareTo("/name") == 0){
                showConnectedName(tokens[1]);
            }else if(tokens[0].compareTo("/send") == 0){
                transferData();
            }
        }else{
            showIncomingMessage(msg);
        }
    }

    /**
     * Tries to connect to a RFCOMMSocket to achieve high-throughput transfer rates
     *
     */
    private void transferData(){
        if(mBleMode == BLEMode.PERIPHERAL ) {
            // 1st - unleash RFCOMM Socket machinery...
            BLEPeripheralHelper.getInstance().initRfcommService();
            showInfo("Initializing RFCOMM socket...");
        }
    }

    /**
     * Saves the data byte array into a file
     */
    private void save2File(byte [] data){
        File filepath = Environment.getExternalStorageDirectory();
        String filePathName = filepath.getAbsolutePath() + "/BluetoothBLEChat/";
        // Create a new folder in SD Card
        File dir = new File(filePathName);
        dir.mkdirs();
        try {
            BufferedOutputStream bos = new BufferedOutputStream(new FileOutputStream(filePathName + "data.jpg"));
            bos.write(data);
            bos.flush();
            bos.close();
        }catch (FileNotFoundException ex){
            showInfo(ex.toString());
        }catch (IOException ex){
            showInfo(ex.toString());
        }finally {
            BLEPeripheralHelper.getInstance().stopRfcommService();
        }
    }


    private void setupProgressBar(Context cxt) {
        if(mProgressBar == null) {
            mProgressBar = new ProgressDialog(cxt);
            mProgressBar.setCancelable(true);
            mProgressBar.setMessage("Transferring data ...");
            mProgressBar.setProgressStyle(ProgressDialog.STYLE_HORIZONTAL);
            mProgressBar.setMax(1024 * 1024);
        }
    }

    /**
     * This class will help run the Transfer Rate Test
     * It can send the data in two ways:
     * 1 - Within a loop where we will write to characteristcs as fast as possible
     * 2 - In an event-based way, where we will write to characteristics once we receive
     *     the onCharacteristicWrite event from the BT stack.
     */
    private class StreamThread extends Thread {
        private boolean mEnd = false;
        private Semaphore mSemaphore = new Semaphore(0,true);
        StreamThread(){ }

        private void updateProgressBar(final int increment){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.setProgress(increment);
                }
            });
        }

        private void hideProgressBar(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.dismiss();
                    mProgressBar.hide();
                }
            });
        }

        private void showProgressBar(){
            mHandler.post(new Runnable() {
                @Override
                public void run() {
                    mProgressBar.show();
                }
            });
        }


        private void sendViaLoop(){
            int iMtu = BLECentralHelper.getInstance().getMtu();
            long startTime = System.currentTimeMillis();
            for(int iBytesSent = 0;!mEnd && iBytesSent < 1024 * 1024 ; iBytesSent += iMtu ) {
                processOutgoingMsg("/transfer");
                updateProgressBar(iBytesSent);
            }
            long difference = (System.currentTimeMillis() - startTime) / 1000;
            showInfo("1 MB took " + difference + " secs to complete");
        }

        private void sendViaEvent(){
            int iBytesSent = 0;
            int iMtu = BLECentralHelper.getInstance().getMtu();
            while(!mEnd && iBytesSent < 1024 * 1024) {
                processOutgoingMsg("/transfer");
                iBytesSent += iMtu;
                updateProgressBar(iBytesSent);
                try {
                    mSemaphore.acquire();
                }catch (InterruptedException ex){
                    mBLEChatEvents.onConnectionError("Interrupted while in a semaphore!!");
                }
            }
        }

        /**
         * Releases the semaphore, so we can send the next message. It's only used when sending
         * messages via events.
         */
        public void nextMessage(){
            mSemaphore.release();
        }

        public void run(){
            showProgressBar();
            try {
                Thread.sleep(2000);
                sendViaEvent();
                //sendViaLoop();
            }catch (InterruptedException ex){
                mBLEChatEvents.onConnectionError("Interrupted while sleeping!!!");
            }finally {
                hideProgressBar();
            }
        }

        public void end(){
            mEnd = true;
        }
    }
}

