package com.example.bedled;

import android.Manifest;
import android.annotation.SuppressLint;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.bluetooth.BluetoothManager;
import android.bluetooth.BluetoothSocket;
import android.content.Context;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.Spinner;
import android.widget.TableLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.EdgeToEdge;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mrudultora.colorpicker.ColorPickerPopUp;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class MainActivity extends AppCompatActivity {

    private final CustomAlerts customAlerts = new CustomAlerts(this, this);
    private final HashMap<String, BluetoothDevice> btDevices = new HashMap<>();
    private final HashMap<SwitchMaterial, ImageButton> switchMap = new HashMap<>();
    private final HashMap<ImageButton, Integer> buttonColorMap = new HashMap<>();
    private Communication communication;
    private ProgressBar progressBar;
    private TableLayout tableLayout;
    private TextView connectMsg;
    private Button connectBtn;
    private Spinner spinner = null;
    private ImageButton btnZone0, btnZone1, btnZone2, btnZone3, btnZone4;
    private List<ImageButton> buttonList;
    private SwitchMaterial swZone0, swZone1, swZone2, swZone3, swZone4;
    private List<SwitchMaterial> switchList;
    private int color0, color1, color2, color3, color4;
    private BluetoothManager bluetoothManager;
    private BluetoothAdapter bluetoothAdapter;
    private BluetoothSocket socket;
    private BluetoothDevice btDevice;
    private Set<BluetoothDevice> pairedDevices;
    private FloatingActionButton sendActionButton;
    private ScheduledExecutorService bluetoothExecutor;
    private Executor mainExecutor;
    private InputStream inputStream;
    private OutputStream outputStream;
    private byte[] buffer;
    private int numBytes;
    private RadioGroup radioGroup;
    private RadioButton radioSingle, radioShared;

    private void buttonColorChange(ImageButton btn, int color) {
        btn.getDrawable().setTint(color);
    }

    @SuppressLint("MissingPermission")
    private boolean BTisOff() throws RuntimeException {
        bluetoothManager = bluetoothManager == null ? (BluetoothManager) getSystemService(Context.BLUETOOTH_SERVICE) : bluetoothManager;
        bluetoothAdapter = bluetoothAdapter == null ? bluetoothManager.getAdapter() : bluetoothAdapter;
        if (bluetoothAdapter == null) {
            // Device doesn't support Bluetooth
            Toast.makeText(MainActivity.this, "No Bluetooth", Toast.LENGTH_SHORT).show();
            throw new RuntimeException("No Bluetooth");
        }

        //Check if BT adapter Enable
        if (!bluetoothAdapter.isEnabled()) {
            customAlerts.displayAlert(CustomAlerts.alertType.BT);
        } else {
            pairedDevices = bluetoothAdapter.getBondedDevices();
            if (!pairedDevices.isEmpty() && spinner == null) {
                // There are paired devices. Get the name and address of each paired device.
                for (BluetoothDevice device : pairedDevices) {
                    btDevices.put(device.getName(), device);
                }
                ArrayAdapter<String> adapter = new ArrayAdapter<>(getApplicationContext(), R.layout.spinner_item, btDevices.keySet().toArray(new String[0]));

                adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);

                //Initialize spinner
                spinner = findViewById(R.id.spinner);
                spinner.setAdapter(adapter);
            }

        }
        return !bluetoothAdapter.isEnabled();
    }

    public void resetSocStream() {
        socket = null;
        btDevice = null;
        communication = null;
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (socket != null && socket.isConnected()) {
            try {
                socket.close();
            } catch (IOException e) {
                Log.e("MainActivity", "Error closing Bluetooth socket", e);
            }
        }
        resetSocStream();
        layoutChange(layoutState.INITIAL);
    }

    private void layoutChange(layoutState state) {
        switch (state) {
            case INITIAL:
                connectBtn.setEnabled(true);
                connectBtn.setText(getString(R.string.connect));
                tableLayout.setVisibility(View.GONE);
                radioGroup.setVisibility(View.GONE);
                connectMsg.setVisibility(View.VISIBLE);
                progressBar.setVisibility(View.GONE);
                sendActionButton.setEnabled(false);
                break;
            case CONNECTION:
                connectBtn.setEnabled(false); // Disable button while logging in
                connectBtn.setText(getString(R.string.connecting));
                tableLayout.setVisibility(View.GONE);
                radioGroup.setVisibility(View.GONE);
                progressBar.setVisibility(View.VISIBLE); // Show progress indicator
                break;
            case CONNECTED:
                connectBtn.setEnabled(true);
                connectBtn.setText(getString(R.string.connected));
                tableLayout.setVisibility(View.VISIBLE);
                radioGroup.setVisibility(View.VISIBLE);
                connectMsg.setVisibility(View.GONE);
                progressBar.setVisibility(View.GONE);
                sendActionButton.setEnabled(true);
                break;
        }
    }

    /**
     * Slicing data received back from the ardu
     *
     * @param receivedData String received from the Arduino.
     * @return HashMap of sliced data ordered as follows: Zone = [Hue, Saturation, Value]
     */
    @Nullable
    private HashMap<String, List<String>> sliceData(String receivedData) {
        if (receivedData == null || receivedData.isEmpty()) {
            return null;
        }
        String[] parts = receivedData.split("@");

        // Create the HashMap to store the result
        HashMap<String, List<String>> resultMap = new HashMap<>();

        for (String part : parts) {
            if (!part.isEmpty()) {
                // Split into key and values
                String[] keyValue = part.split("#");
                String key = keyValue[0]; // The first element is the key
                // Create an ArrayList for the values (the rest of the elements)
                List<String> values = new ArrayList<>(Arrays.asList(Arrays.copyOfRange(keyValue, 1, keyValue.length)));

                // Put the key and its associated values into the map
                resultMap.put(key, values);
            }
        }
        return resultMap;
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        _checkPerms();

        try {
//            while (BTisOff()) ;
        } catch (RuntimeException e) {
            Toast.makeText(MainActivity.this, e.toString(), Toast.LENGTH_SHORT).show();
        }

        init();

    }

    private void init() {
        mainExecutor = ContextCompat.getMainExecutor(this);
        bluetoothExecutor = Executors.newSingleThreadScheduledExecutor();

        //Initialize progress bar
        progressBar = findViewById(R.id.progressBar);

        //Initialize table layout
        tableLayout = findViewById(R.id.tableLayout);

        //Initialize variables
        color0 = color1 = color2 = color3 = color4 = getColor(R.color.white);
        connectMsg = findViewById(R.id.connectMsg);

        //Initialize buttons
        connectBtn = findViewById(R.id.connectBtn);
        btnZone0 = findViewById(R.id.btnZone0);
        btnZone1 = findViewById(R.id.btnZone1);
        btnZone2 = findViewById(R.id.btnZone2);
        btnZone3 = findViewById(R.id.btnZone3);
        btnZone4 = findViewById(R.id.btnZone4);
        buttonList = List.of(btnZone0, btnZone1, btnZone2, btnZone3, btnZone4);
        sendActionButton = findViewById(R.id.sendActionButton);

        //Initialize radio buttons
        radioGroup = findViewById(R.id.radioGroup);
        radioSingle = findViewById(R.id.radioSingle);
        radioShared = findViewById(R.id.radioShared);

        //Initialize switches
        swZone0 = findViewById(R.id.swZone0);
        swZone1 = findViewById(R.id.swZone1);
        swZone2 = findViewById(R.id.swZone2);
        swZone3 = findViewById(R.id.swZone3);
        swZone4 = findViewById(R.id.swZone4);
        switchList = List.of(swZone1, swZone2, swZone3, swZone4);

        //Initialize maps
        //Map<ImageButton, Integer> buttonColorMap = new HashMap<>();
        buttonColorMap.put(btnZone0, color0);
        buttonColorMap.put(btnZone1, color1);
        buttonColorMap.put(btnZone2, color2);
        buttonColorMap.put(btnZone3, color3);
        buttonColorMap.put(btnZone4, color4);

        //Map<SwitchMaterial, ImageButton> switchMap = new HashMap<>();
        switchMap.put(swZone0, btnZone0);
        switchMap.put(swZone1, btnZone1);
        switchMap.put(swZone2, btnZone2);
        switchMap.put(swZone3, btnZone3);
        switchMap.put(swZone4, btnZone4);

        if (BTisOff()) {
            return;
        }

        //Set listeners for switches
        switchList.forEach(sw -> {
            sw.setOnClickListener(v -> {
                if ((swZone1.isChecked() || swZone2.isChecked() || swZone3.isChecked() || swZone4.isChecked()) && swZone0.isChecked()) {
                    swZone0.setChecked(false);
                } else if ((!swZone1.isChecked() && !swZone2.isChecked() && !swZone3.isChecked() && !swZone4.isChecked()) && !swZone0.isChecked()) {
                    swZone0.setChecked(true);
                }
            });
            sw.setOnCheckedChangeListener((b, checked) -> {
                ImageButton im = switchMap.get(sw);
                if (checked) {
                    buttonColorChange(im, buttonColorMap.get(im));
                    im.setClickable(true);
                } else {
                    buttonColorChange(im, getColor(R.color.black));
                    im.setClickable(false);
                }
            });
        });

        //Set special listener for switch 0
        swZone0.setOnClickListener(v -> {
            if (swZone0.isChecked()) {
                swZone1.setChecked(false);
                swZone2.setChecked(false);
                swZone3.setChecked(false);
                swZone4.setChecked(false);
            } else {
                swZone1.setChecked(true);
                swZone2.setChecked(true);
                swZone3.setChecked(true);
                swZone4.setChecked(true);
            }
        });

        //Apply color depending on switch state
        switchList.forEach(sw -> {
            buttonColorChange(Objects.requireNonNull(switchMap.get(sw)), sw.isChecked() ? buttonColorMap.get(switchMap.get(sw)) : getColor(R.color.black));
            Objects.requireNonNull(switchMap.get(sw)).setClickable(sw.isChecked());
        });

        //Set listeners for buttons
        buttonList.forEach(btn -> btn.setOnClickListener(v -> {
            ColorPickerPopUp popUp = new ColorPickerPopUp(MainActivity.this);
            popUp.setShowAlpha(false).setDefaultColor(buttonColorMap.get(btn)).setDialogTitle("Pick a Color").setOnPickColorListener(new ColorPickerPopUp.OnPickColorListener() {
                @Override
                public void onColorPicked(int color) {
                    // handle the use of color
                    //TODO : ADD SINGLE AND SHARED
                    if (radioSingle.isChecked()) {
                        buttonColorMap.put(btn, color);
                        btn.getDrawable().setTint(color);
                    } else if (radioShared.isChecked()){
                        for (ImageButton imbt :
                                buttonColorMap.keySet()) {
                            buttonColorMap.put(imbt, color);
                            imbt.getDrawable().setTint(color);
                        }
                    }
                }

                @Override
                public void onCancel() {
                    popUp.dismissDialog();    // Dismiss the dialog.
                }
            }).show();
        }));

        sendActionButton.setOnClickListener(v -> {
            StringBuilder builder = new StringBuilder();
            String data = gatherData();
            builder.append("!!").append(2).append(data);
            Log.d("SENT", "msg : " + builder);

            try {
                outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
        });

        //Set listeners for connect button
        connectBtn.setOnClickListener(v -> {
            if (socket == null) {
                String key = spinner.getSelectedItem().toString();
                if (ActivityCompat.checkSelfPermission(getApplicationContext(), android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    return;
                }
                btDevice = btDevices.get(key);
                Toast.makeText(getApplicationContext(), btDevice.getName(), Toast.LENGTH_SHORT).show();

                layoutChange(layoutState.CONNECTION);
                bluetoothExecutor.execute(new ConnectThread(btDevice, getApplicationContext()));

                Log.e("CONNECT", "DEBUG " + bluetoothExecutor.toString());


            } else {
                Log.e("CONNECT", "DEBUG " + bluetoothExecutor.toString());
                try {
                    socket.close();
                } catch (IOException e) {
                    Log.e("CONNECT", "Could not close the client socket", e);
                    throw new RuntimeException(e);
                }
                resetSocStream();
                layoutChange(layoutState.INITIAL);
            }
        });
    }

    public int[] intToHSV255(int color) {
        float[] hsv = new float[3];
        Color.colorToHSV(color, hsv);

        int[] hsv255 = new int[3];
        hsv255[0] = (int) (hsv[0] / 360f * 255f); // Scale hue to 0-255
        hsv255[1] = (int) (hsv[1] * 255f);        // Scale saturation to 0-255
        hsv255[2] = (int) (hsv[2] * 255f);        // Scale value to 0-255

        return hsv255;
    }

    private String gatherData() {
        StringBuilder builder = new StringBuilder();
        if (swZone0.isChecked()) {
            int[] hsv = intToHSV255(buttonColorMap.get(switchMap.get(swZone0)));
            builder.append('@').append(swZone0.getHint()).append('#').append(hsv[0]).append('#').append(hsv[1]).append('#').append(hsv[2]);
        } else {
            switchList.forEach(sw -> {
                if (sw.isChecked()) {
                    int[] hsv = intToHSV255(buttonColorMap.get(switchMap.get(sw)));
                    builder.append('@').append(sw.getHint()).append('#').append(hsv[0]).append('#').append(hsv[1]).append('#').append(hsv[2]);
                } else if (!builder.toString().contains("@0")) {
                    builder.append("@").append(sw.getHint()).append("#").append(0).append("#").append(0).append("#").append(0);
                }
            });
        }
        return builder.toString();
    }

    private void _checkPerms() {
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 1);
        }
        if (ActivityCompat.checkSelfPermission(this, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(MainActivity.this, new String[]{Manifest.permission.BLUETOOTH_SCAN}, 1);
        }
    }

    private void updateTable() {
        Log.d("updateTable", "updateTable: ");
        try {
            outputStream.write("?1@0".getBytes(StandardCharsets.UTF_8));
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
    }

    private enum layoutState {
        INITIAL, CONNECTION, CONNECTED
    }

    public class ConnectThread extends Thread {
        private final Context context;
        private BluetoothSocket mmSocket;
        private volatile BluetoothSocket btSocket;


        public ConnectThread(BluetoothDevice device, Context context) {
            // Use a temporary object that is later assigned to mmSocket
            // because mmSocket is final.
            BluetoothSocket tmp = null;
            this.context = context;

            try {
                // Get a BluetoothSocket to connect with the given BluetoothDevice.
                // MY_UUID is the app's UUID string, also used in the server code.
                if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_CONNECT) != PackageManager.PERMISSION_GRANTED) {
                    ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_CONNECT}, 2);
                    return;
                }
                tmp = device.createRfcommSocketToServiceRecord(UUID.fromString("00001101-0000-1000-8000-00805F9B34FB"));
            } catch (IOException e) {
                Log.e("CNT_TH", "Socket's create() method failed", e);
            }
            mmSocket = tmp;
        }

        public void run() {
            // Cancel discovery because it otherwise slows down the connection.
            if (ActivityCompat.checkSelfPermission(context, android.Manifest.permission.BLUETOOTH_SCAN) != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(MainActivity.this, new String[]{android.Manifest.permission.BLUETOOTH_SCAN}, 2);
                return;
            }
            bluetoothAdapter.cancelDiscovery();

            try {
                // Connect to the remote device through the socket. This call blocks
                // until it succeeds or throws an exception.
                mmSocket.connect();
            } catch (IOException connectException) {
                // Unable to connect; close the socket and return.
                try {
                    mmSocket.close();
                } catch (IOException closeException) {
                    Log.e("TAG", "Could not close the client socket", closeException);
                }
                return;
            }

            // The connection attempt succeeded. Perform work associated with
            // the connection in a separate thread.

            try {
                inputStream = mmSocket.getInputStream();
                outputStream = mmSocket.getOutputStream();
            } catch (IOException e) {
                throw new RuntimeException(e);
            }
            btSocket = mmSocket;

            if (btSocket.isConnected()) {
                communication = new Communication(inputStream, outputStream);

                ScheduledExecutorService dialExecutor = Executors.newSingleThreadScheduledExecutor();
                dialExecutor.execute(new ConnectedThread());

                mainExecutor.execute(() -> {
                    layoutChange(layoutState.CONNECTED);
                });

            } else {
                mainExecutor.execute(() -> {
                    layoutChange(layoutState.INITIAL);
                });
            }
        }
    }

    private class ConnectedThread extends Thread {

        @Nullable
        private String readStream() {
            try {
                buffer = new byte[100];
                numBytes = inputStream.read(buffer);
                if (numBytes > 0) {
                    Log.i("TAG", "bytes received : " + numBytes);
                    return new String(buffer, StandardCharsets.UTF_8);
                }
            } catch (IOException e) {
                Log.d("TAG", "Input stream was disconnected", e);
                Toast.makeText(getApplicationContext(), "Input stream was disconnected", Toast.LENGTH_LONG).show();
                throw new RuntimeException(e);
            }
            return null;
        }

        public void run() {
            while (socket != null) {
                String s = readStream();
                if (s != null) {
                    mainExecutor.execute(() -> {
                        Log.d("TAG", s);
                        rootingAction(s);
                    });
                }
            }
        }

        private void rootingAction(String s) {
            Log.i("RECV", "rootingAction: " + s);
            if (s.startsWith("@")) {
                mainExecutor.execute(() -> {
                    HashMap<String, List<String>> sliced = sliceData(s);
                    sliced.forEach((k, v) -> {
                        Color color = Color.valueOf(communication.hsv2rgb(Integer.parseInt(v.get(0)), Integer.parseInt(v.get(1)), Integer.parseInt(v.get(2))));
                        buttonColorChange(buttonList.get(Integer.parseInt(k)), color.toArgb());
                        buttonColorMap.put(buttonList.get(Integer.parseInt(k)), color.toArgb());
                    });
                });
            } else {
                if (s.startsWith("!")) {
                    mainExecutor.execute(() -> Toast.makeText(getApplicationContext(), "Success !", Toast.LENGTH_SHORT).show());
                } else if (s.startsWith("?")) {
                    mainExecutor.execute(() -> Toast.makeText(getApplicationContext(), "Failed ?", Toast.LENGTH_SHORT).show());
                } else {
                    mainExecutor.execute(() -> Toast.makeText(getApplicationContext(), s, Toast.LENGTH_SHORT).show());
                }
            }
        }
    }
}


