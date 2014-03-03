package com.tonydicola.bluetoothtest;

import android.app.Activity;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothSocket;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import java.io.BufferedReader;
import java.io.BufferedWriter;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.util.ArrayList;
import java.util.UUID;
import java.util.concurrent.ConcurrentLinkedQueue;

public class MainActivity extends Activity {

    private static final UUID SPP_UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB");
    private static final String OUTPUT_MESSAGE = "com.tonydicola.bluetoothtest.OUTPUT_MESSAGE";

    private Spinner deviceList;
    private TextView message;
    private TextView output;

    private BluetoothAdapter adapter;

    private ArrayList<String> outputStrings = new ArrayList<String>();

    private UpdateThread updateThread;

    private class DeviceListItem {
        public BluetoothDevice device;
        public DeviceListItem(BluetoothDevice device) {
            this.device = device;
        }
        @Override
        public String toString() {
            if (device != null) {
                return String.format("%s - %s", device.getName(), device.getAddress());
            }
            return "Null device!";
        }
    }
    private ArrayAdapter<DeviceListItem> devices;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Build layout.
        setContentView(R.layout.activity_main);

        // Get UI elements from layout.
        deviceList = (Spinner) findViewById(R.id.device_list);
        message = (TextView) findViewById(R.id.message);
        output = (TextView) findViewById(R.id.output);

        // Get the device's bluetooth adapter.
        adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter == null) {
            writeOutput("Bluetooth is NOT supported by this device!");
            return;
        }
        if (!adapter.isEnabled()) {
            writeOutput("Bluetooth is disabled!  Please enable bluetooth and run the program again.");
        }

        // Setup the list of devices.
        devices = new ArrayAdapter<DeviceListItem>(this, android.R.layout.simple_list_item_1);
        deviceList.setAdapter(devices);

        // Register for device discovery notifications.
        registerReceiver(receiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        // Clean up notification receiver.
        unregisterReceiver(receiver);
    }

    @Override
    public void onStop() {
        super.onStop();
        // Stop the update thread.
        if (updateThread != null) {
            updateThread.shouldRun = false;
            updateThread = null;
        }
    }

    public void searchForDevices(View view) {
        // Discover bluetooth devices.
        devices.clear();
        if (adapter.startDiscovery()) {
            writeOutput("Scanning for devices, they will be added to device list as found.");
        }
        else {
            writeOutput("Could not start bluetooth device scan!");
        }
    }

    public void connect(View view) {
        // Connect to the selected device.
        DeviceListItem item = (DeviceListItem) deviceList.getSelectedItem();
        if (item != null) {
            if (updateThread != null) {
                updateThread.shouldRun = false;
                updateThread = null;
            }
            updateThread = new UpdateThread(item.device, outputUpdate);
            updateThread.start();
        }
    }

    public void send(View view) {
        // Send a message to the update thread write message queue.
        if (updateThread != null) {
            String text = message.getText().toString();
            updateThread.writeMessage.add(text);
            message.setText("");
        }
    }

    public void writeOutput(String message) {
        // Concatenate all the output strings and write them to the text view.
        outputStrings.add(message);
        StringBuilder builder = new StringBuilder();
        for (String s : outputStrings) {
            builder.append(s);
            builder.append("\n");
        }
        output.setText(builder.toString());
    }

    private BroadcastReceiver receiver = new BroadcastReceiver() {
        public void onReceive(Context context, Intent intent) {
            // For each bluetooth discovery device found event, get the device and add it to the device list.
            if (BluetoothDevice.ACTION_FOUND.equals(intent.getAction())) {
                BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                DeviceListItem item = new DeviceListItem(device);
                writeOutput(String.format("Found device: %s", item));
                devices.add(item);
            }
        }
    };

    private Handler outputUpdate = new Handler() {
        @Override
        public void handleMessage(Message msg) {
            // Get the string from the message and write it to the output view.
            String message = msg.getData().getString(OUTPUT_MESSAGE);
            writeOutput(message);
        }
    };

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private class UpdateThread extends Thread {

        private BluetoothDevice device;

        private Handler outputUpdate;

        public ConcurrentLinkedQueue<String> writeMessage = new ConcurrentLinkedQueue<String>();

        public boolean shouldRun = true;

        public UpdateThread(BluetoothDevice device, Handler outputUpdate) {
            this.device = device;
            this.outputUpdate = outputUpdate;
        }

        @Override
        public void run() {
            // Cancel discovery.
            adapter.cancelDiscovery();
            // Connect to device.
            writeOutput(String.format("Connecting to device with address: %s", device.getAddress()));
            BluetoothSocket socket;
            try {
                // Create socket to SPP UUID on selected device.
                socket = device.createRfcommSocketToServiceRecord(SPP_UUID);
                // Connect to socket.
                socket.connect();
            }
            catch (IOException e) {
                writeOutput(String.format("Error connecting to device: %s", e));
                return;
            }
            writeOutput("Connected!");
            InputStream instream;
            OutputStream outstream;
            BufferedWriter writer;
            try {
                instream = socket.getInputStream();
                outstream = socket.getOutputStream();
                writer = new BufferedWriter(new OutputStreamWriter(outstream));
            }
            catch (IOException e) {
                writeOutput(String.format("Error initializing reader and writer: %s", e));
                return;
            }
            // Loop reading and writing data.
            byte[] buffer = new byte[1024];
            try {
                while (shouldRun) {
                    try {
                        // Check if there is data to read.
                        if (instream.available() > 0) {
                            // Read available data.
                            int len = instream.read(buffer);
                            if (len > 0) {
                                writeOutput(String.format("Received: %s", new String(buffer, 0, len)));
                            }
                        }
                        // Check if there is data to write.
                        String message = writeMessage.poll();
                        if (message != null && !message.isEmpty()) {
                            writeOutput(String.format("Writing: %s", message));
                            // Write data.
                            writer.write(message);
                            writer.flush();
                        }
                    }
                    catch (IOException e) {
                        writeOutput(String.format("Error reading/writing data: %s", e));
                    }
                    Thread.sleep(100);
                }
            }
            catch (InterruptedException e) {
                // Do nothing.
                writeOutput("Update thread was interrupted!");
            }
            // Disconnect
            try {
                writeOutput("Closing bluetooth socket.");
                socket.close();
            }
            catch (IOException e) {
                writeOutput(String.format("Error closing socket: %s", e));
            }
        }

        private void writeOutput(String text) {
            Message message = outputUpdate.obtainMessage();
            message.getData().putString(OUTPUT_MESSAGE, text);
            outputUpdate.sendMessage(message);
        }
    }
}
