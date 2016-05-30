# Bluetooth Low Energy Chat example

I took the original Bluetoot Chat example from Google and modified it to run
as a Bluetooth Low Energy (BLE) GATT service.

This is just a proof of concept to see how the Android BLE API works in a real world, and test some use-cases/scenarios like:
* Connect devices and see how much can we done without user interactions
* Playoing with different pairing/security mechanisms
* Advertising ranges
* Test transfer rate speeds
* Mixing Bluetooth Classic with Bluetooth LE
* ...

Do not expect a production-like code, don't even expect a fully working program.
The point of this experiment is just for researching Bluetooth capabillities on Android devices, not to deliver a messenger app.

## What can I do and how?
This what you can do with the application so far:
* Connect via Classic Bluetooth (CBT) methods
  1. You need at least two devices running the app, one will be the server and the other the client. In the server side, tap in the menu (upper right) and then tap on **CBT - Make discoverable** option.
  2. In the client side, tap in one of the CBT prefixed options to connect using a secure method (pairing/encrypted) or  insecure method (unpairing/unencrypted).
  3 If there's no previously paired phones yet, you need to tap on the "Scan for devices" to find the server and tap on it to initiate a connection.

* Connect via BLE methods
  1. You need at least two devices running the app, one will play the role of Peripheral (server), and the othe will be
    the Central device (client). In the Peripheral side, tap in the menu (upper right) and then tap in the BLE - Advertise
    option. 
  2. In the Central device, tap in the BLE - Discover option, a new screen will show up with a list of devices found. Select
    the Peripheral device in the list and tap on it to initiate a connection.

* Send messages (CBT and BLE)
  1. Just write the message you want to send in the box below
  2. Tap on SEND button.

* Send an image
  1. Write "/send" and you will see a new screen with your Gallery photos.
  2. Choose one picture and it will be send to the other device.
    The image is going to be send via RFCOMM Socket, so the transfer rate should be the highest possible.
    
    **WARNING:** You need to be previously connected via BLE.

* Test trasnfer rate
  1. Write "/transfertest" and it will show you a progress bar in both the Peripheral and Central devices. This test will try
    send 1MB of data. The transfer rate speed in Megabytes/Second can be shown in the screen of Peripheral device in real time.
    
    **WARNING:** You need to be previously connected via BLE.

## About the code
As I took the Google BluetoothChat exmaple to add all BLE capabilities on top of it, I tried to separate both BLE and CBT code in
differente Java packages, so I created a new package called `com.example.android.ble`, where you can see some classes and interfaces.
However there are only 2 importante classes to take a look: `BLECentralHelper` and `BLEPeripheralHelper`, represting all Central and
Peripheral implementations respectively.
Another important class is `BluetoothChatFragment` (`com.android.example.bluetoothchat`), which is the original one but with all the
logic to communicate with `BLECentralHelper` and `BLEPeripheralHelper`.


Enjoy!

Juan Gómez [:\_AtilA\_]
