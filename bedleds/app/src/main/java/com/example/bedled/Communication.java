package com.example.bedled;

import android.bluetooth.BluetoothSocket;
import android.graphics.Color;
import android.widget.ImageButton;

import com.google.android.material.switchmaterial.SwitchMaterial;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.nio.charset.StandardCharsets;
import java.util.HashMap;
import java.util.List;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class Communication {

    private final InputStream inputStream;
    private final OutputStream outputStream;
    private String receivedData;

    public Communication(BluetoothSocket socket) throws IOException {
        this.inputStream = socket.getInputStream();
        this.outputStream = socket.getOutputStream();
    }

    public Communication(InputStream inputStream, OutputStream outputStream) {
        this.inputStream = inputStream;
        this.outputStream = outputStream;
    }

    public String getReceivedData() {
        return receivedData;
    }

    public void setReceivedData(String receivedData) {
        this.receivedData = receivedData;
    }

    public float[] rgb2hsv(int color) {
        float[] hsv = new float[3];
        Color.RGBToHSV(Color.red(color), Color.green(color), Color.blue(color), hsv);
        return hsv;
    }

    public int hsv2rgb(int h, int s, int v) {
        float[] hsv = {h, s, v};
        return Color.HSVToColor(hsv);
    }

    /*
     *  int color = Color.RED; // Example color
     *  String hexColor = String.format("#%06X", (0xFFFFFF & color));
     */


    private String gatherData(List<SwitchMaterial> switchList, HashMap<SwitchMaterial, ImageButton> switchMap, HashMap<ImageButton, Integer> buttonColorMap) {
        StringBuilder builder = new StringBuilder();
        switchList.forEach(sw -> {
            if (sw.isChecked()) {
                float[] hsv = rgb2hsv(buttonColorMap.get(switchMap.get(sw)));
                builder.append('@').append(sw.getHint()).append('#').append(hsv[0]).append('#').append(hsv[1]).append('#').append(hsv[2]);
            }
        });
        return builder.toString();
    }

    public Future<Boolean> retrieveData() {
        return Executors.newSingleThreadExecutor().submit(() -> {
            String code = "?" + protocol.ASK.ordinal() + "@" + zone.ALLZONES.ordinal();
            try {
                outputStream.write(code.getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return false;
            }
            return true;
        });
    }

    public Future<Boolean> sendData(List<SwitchMaterial> switchList, HashMap<SwitchMaterial, ImageButton> switchMap, HashMap<ImageButton, Integer> buttonColorMap) {
        // TODO: send data to the board
        StringBuilder builder = new StringBuilder();
        String data = gatherData(switchList, switchMap, buttonColorMap);
        builder.append("!!").append(protocol.APPLY.ordinal()).append(data);

        return Executors.newSingleThreadExecutor().submit(() -> {
            try {
                outputStream.write(builder.toString().getBytes(StandardCharsets.UTF_8));
            } catch (IOException e) {
                return false;
            }
            return true;
        });
    }

    private enum protocol {
        NOTHING, ASK, APPLY
    }

    public enum zone {
        ALLZONES, ZONE1, ZONE2, ZONE3, ZONE4
    }


}
