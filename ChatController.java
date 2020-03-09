

//ADD PACKAGE NAME HERE

import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothServerSocket;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.widget.Toast;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.Set;
import java.util.UUID;




/** PERMISSIONS REQUIRED IN MANIFEST
 <uses-permission android:name="android.permission.BLUETOOTH" />
 <uses-permission android:name="android.permission.BLUETOOTH_ADMIN" />
 */

/** CODE FOR MAIN JAVA
 --Declarations--
 private ChatController chatController;


 --onCreate--
 chatController = new ChatController(this, *DEVICE ADDRESS*, hMain);
--In Main Class--
     private final Handler handler = new Handler(new Handler.Callback() {
        @Override
        public boolean handleMessage(Message msg) {
            Bundle bundle = msg.getData();
            if (bundle!=null){
                String input = bundle.getString("newMessage");
                Toast.makeText(MainActivity.this, input, Toast.LENGTH_SHORT).show();
                //do something here with your messages
            }
            return true;
        }
    });

 **/
public class ChatController {
    private static final String APP_NAME = "BluetoothChatApp";
    //This is for phone to phone
   // private static final UUID MY_UUID = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66");
    //This is for HC-05
    private static final UUID MY_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    public static final String BT_MESSAGE = "bluetoothMessage";

    private final BluetoothAdapter BA;
    // private final Handler handler;
    private AcceptThread acceptThread;
    private ConnectThread connectThread;
    private ReadWriteThread connectedThread;
    private BluetoothDevice connectingDevice;
    private int state;
    public boolean BluetoothPaired;
	Handler main;

    public static final int MESSAGE_STATE_CHANGE = 1;
    public static final int MESSAGE_READ = 2;
    public static final int MESSAGE_WRITE = 3;
    public static final int MESSAGE_DEVICE_OBJECT = 4;
    public static final int MESSAGE_TOAST = 5;
    public static final String DEVICE_OBJECT = "DSD TECH HC-05";
    private static final int REQUEST_ENABLE_BLUETOOTH = 1;
    public static final int REQUEST_OVERLAY_PERMISSION = 2;
    public static final int REQ_CODE_WRITE = 200;
    public static final int REQ_CODE_READ = 201;

    static final int STATE_NONE = 0;
    static final int STATE_LISTEN = 1;
    static final int STATE_CONNECTING = 2;
    static final int STATE_CONNECTED = 3;
    private Context mContext;
    private String connectADDRESS;
    Set<BluetoothDevice> pairedDevices;

    public ChatController(Context context, String deviceAddress,Handler hMain) {
        BA = BluetoothAdapter.getDefaultAdapter();
        pairedDevices = BA.getBondedDevices();
        state = STATE_NONE;
        mContext = context;
		main = hMain;
        connectADDRESS = deviceAddress;
        //this.handler = handler;

        if (BA == null) {
            Toast.makeText(mContext, "Bluetooth is not available!", Toast.LENGTH_SHORT).show();

        }
        if (!BA.isEnabled()) {
            BA.enable();
            Toast.makeText(mContext, "Bluetooth has been turned on!", Toast.LENGTH_SHORT).show();
        }
        connectToDevice(deviceAddress);


    }

    public void reconnect(){
        BA.disable();
        BA.enable();
        connectToDevice(connectADDRESS);
    }

    public int serialPrint(String msg){

        if (ChatController.this.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(mContext, "Connection was lost!", Toast.LENGTH_SHORT).show();
            //BluetoothPaired = false;
            return 0;
        }

        if (msg.length() > 0) {
            byte[] send = msg.getBytes();
            write(send);
            return 1;
        }

        return 3;

    }

    public int serialWrite(byte data){
        if (ChatController.this.getState() != ChatController.STATE_CONNECTED) {
            Toast.makeText(mContext, "Connection was lost!", Toast.LENGTH_SHORT).show();
            //BluetoothPaired = false;
            return 0;
        }



            write(data);
            return 1;



    }
    // Set the current state of the chat connection
    private synchronized void setState(int state) {
        this.state = state;

        handler.obtainMessage(MESSAGE_STATE_CHANGE, state, -1).sendToTarget();
    }

    // get current connection state
    public synchronized int getState() {
        return state;
    }

    // start service
    public synchronized void start() {
        // Cancel any thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel any running thresd
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        setState(STATE_LISTEN);
        if (acceptThread == null) {
            acceptThread = new AcceptThread();
            acceptThread.start();
        }
    }

    private void connectToDevice(String deviceAddress) {
        BA.cancelDiscovery();
        BluetoothDevice device = BA.getRemoteDevice(deviceAddress);
        connect(device);
    }
    // initiate connection to remote device
    public synchronized void connect(BluetoothDevice device) {
        // Cancel any thread
        if (state == STATE_CONNECTING) {
            if (connectThread != null) {
                connectThread.cancel();
                connectThread = null;
            }
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        // Start the thread to connect with the given device
        connectThread = new ConnectThread(device);
        connectThread.start();
        setState(STATE_CONNECTING);
    }

    // manage Bluetooth connection
    public synchronized void connected(BluetoothSocket socket, BluetoothDevice device) {
        // Cancel the thread
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        // Cancel running thread
        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }

        // Start the thread to manage the connection and perform transmissions
        connectedThread = new ReadWriteThread(socket);
        connectedThread.start();

        // Send the name of the connected device back to the UI Activity
        Message msg = handler.obtainMessage(MESSAGE_DEVICE_OBJECT);
        Bundle bundle = new Bundle();
        bundle.putParcelable(DEVICE_OBJECT, device);
        msg.setData(bundle);
        handler.sendMessage(msg);

        setState(STATE_CONNECTED);
    }

    // stop all threads
    public synchronized void stop() {
        if (connectThread != null) {
            connectThread.cancel();
            connectThread = null;
        }

        if (connectedThread != null) {
            connectedThread.cancel();
            connectedThread = null;
        }

        if (acceptThread != null) {
            acceptThread.cancel();
            acceptThread = null;
        }
        setState(STATE_NONE);
    }

    public void write(byte[] out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = connectedThread;
        }
        r.write(out);
    }

    public void write(byte out) {
        ReadWriteThread r;
        synchronized (this) {
            if (state != STATE_CONNECTED)
                return;
            r = connectedThread;
        }
        r.write(out);
    }

    private void connectionFailed() {
        Message msg = handler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Unable to connect device");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        ChatController.this.start();
    }

    private void connectionLost() {
        Message msg = handler.obtainMessage(MESSAGE_TOAST);
        Bundle bundle = new Bundle();
        bundle.putString("toast", "Device connection was lost");
        msg.setData(bundle);
        handler.sendMessage(msg);

        // Start the service over to restart listening mode
        ChatController.this.start();
    }

    // runs while listening for incoming connections
    private class AcceptThread extends Thread {
        private final BluetoothServerSocket serverSocket;

        public AcceptThread() {
            BluetoothServerSocket tmp = null;
            try {
                tmp = BA.listenUsingInsecureRfcommWithServiceRecord(APP_NAME, MY_UUID);
            } catch (IOException ex) {
                ex.printStackTrace();
            }
            serverSocket = tmp;
        }

        public void run() {
            setName("AcceptThread");
            BluetoothSocket socket;
            while (state != STATE_CONNECTED) {
                try {
                    socket = serverSocket.accept();
                } catch (IOException e) {
                    break;
                }

                // If a connection was accepted
                if (socket != null) {
                    synchronized (ChatController.this) {
                        switch (state) {
                            case STATE_LISTEN:
                            case STATE_CONNECTING:
                                // start the connected thread.
                                connected(socket, socket.getRemoteDevice());
                                break;
                            case STATE_NONE:
                            case STATE_CONNECTED:
                                // Either not ready or already connected. Terminate
                                // new socket.
                                try {
                                    socket.close();
                                } catch (IOException e) {
                                }
                                break;
                        }
                    }
                }
            }
        }

        public void cancel() {
            try {
                serverSocket.close();
            } catch (IOException e) {
            }
        }
    }

    // runs while attempting to make an outgoing connection
    private class ConnectThread extends Thread {
        private final BluetoothSocket socket;
        private final BluetoothDevice device;

        public ConnectThread(BluetoothDevice device) {
            this.device = device;
            BluetoothSocket tmp = null;
            try {
                tmp = device.createInsecureRfcommSocketToServiceRecord(MY_UUID);
            } catch (IOException e) {
                e.printStackTrace();
            }
            socket = tmp;
        }

        public void run() {
            setName("ConnectThread");

            // Always cancel discovery because it will slow down a connection
            BA.cancelDiscovery();

            // Make a connection to the BluetoothSocket
            try {
                socket.connect();
            } catch (IOException e) {
                try {
                    socket.close();
                } catch (IOException e2) {
                }
                connectionFailed();
                return;
            }

            // Reset the ConnectThread because we're done
            synchronized (ChatController.this) {
                connectThread = null;
            }

            // Start the connected thread
            connected(socket, device);
        }

        public void cancel() {
            try {
                socket.close();
            } catch (IOException e) {
            }
        }
    }

    // runs during a connection with a remote device
    private class ReadWriteThread extends Thread {
        private final BluetoothSocket bluetoothSocket;
        private final InputStream inputStream;
        private final OutputStream outputStream;

        public ReadWriteThread(BluetoothSocket socket) {
            this.bluetoothSocket = socket;
            InputStream tmpIn = null;
            OutputStream tmpOut = null;

            try {
                tmpIn = socket.getInputStream();
                tmpOut = socket.getOutputStream();
            } catch (IOException e) {
            }

            inputStream = tmpIn;
            outputStream = tmpOut;
        }

        public void run() {
            byte[] buffer = new byte[1024];
            int bytes = 0;

            // Keep listening to the InputStream
            while (true) {
                try {

                    // Read from the InputStream
                    byte in1 = (byte) inputStream.read();
                    if (in1 != '\r') {
                        buffer[bytes] = in1;
                        // Send the obtained bytes to the UI Activity
                        if ((buffer[bytes] == '\n')) {
                            handler.obtainMessage(MESSAGE_READ, bytes, -1, buffer).sendToTarget();
                            bytes = 0;
                        } else {
                            bytes++;
                        }
                    }
                   
                } catch (IOException e) {
                    connectionLost();
                    // Start the service over to restart listening mode
                    ChatController.this.start();
                    break;
                }
            }
        }

        // write to OutputStream
        public void write(byte[] buffer) {
            try {
                outputStream.write(buffer);
                handler.obtainMessage(MESSAGE_WRITE, -1, -1,
                        buffer).sendToTarget();
            } catch (IOException e) {
            }
        }
        public void write(byte buffer) {
            try {
                outputStream.write(buffer);

            } catch (IOException e) {
            }
        }

        public void cancel() {
            try {
                bluetoothSocket.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }



    }

    private final Handler handler = new Handler(new Handler.Callback() {

        @Override
        public boolean handleMessage(Message msg) {
            switch (msg.what) {
                case MESSAGE_STATE_CHANGE:
                    switch (msg.arg1) {
                        case ChatController.STATE_CONNECTED:
                            showMessage("Connected to: " + connectingDevice.getName());
                            //btnConnect.setEnabled(false);
                            BluetoothPaired = true;     

                            break;
                        case ChatController.STATE_CONNECTING:
                            showMessage("Connecting...");
                            
                            break;
                        case ChatController.STATE_LISTEN:
                        case ChatController.STATE_NONE:
                            showMessage("Not connected to BT");
                            BluetoothPaired = false;
                            break;
                    }
                    break;
                case MESSAGE_WRITE:
                    byte[] writeBuf = (byte[]) msg.obj;
					
					//If you want to display the written message
                    //String writeMessage = new String(writeBuf);
                    //chatMessages.add("Me: " + writeMessage);
                    //chatAdapter.notifyDataSetChanged();
                    break;
                case MESSAGE_READ:
                    byte[] readBuf = (byte[]) msg.obj;

                    String readMessage = new String(readBuf, 0, msg.arg1);
					//This needs to be defined, should probably send handler message back to main
                    //SerialRead(readMessage);
					
					Message msg = hMain.obtainMessage();
					Bundle bundle = new Bundle();
					bundle.putString("newMessage", readMessage);
					msg.setData(bundle);
					hMain.sendMessage(msg);
                    

                    break;
                case MESSAGE_DEVICE_OBJECT:
                    connectingDevice = msg.getData().getParcelable(DEVICE_OBJECT);
                    break;
                case MESSAGE_TOAST:
                    Toast.makeText(mContext, msg.getData().getString("toast"),
                            Toast.LENGTH_SHORT).show();
                    break;
            }
            return false;
        }
    });


    private void showMessage(String msg){
        Toast.makeText(mContext, msg, Toast.LENGTH_SHORT).show();
    }



}

