package com.example.bedled;

import android.app.Activity;
import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;

public class CustomAlerts {

    private final Context context;
    private final ActivityResultLauncher<Intent> activityResultLauncher;


    public CustomAlerts(MainActivity activity, Context context) {
        this.context = context;
        activityResultLauncher = activity.registerForActivityResult(new ActivityResultContracts.StartActivityForResult(), result -> {
            if (result.getResultCode() == Activity.RESULT_OK) {
                Log.e("Activity result", "OK");
                // There are no request codes
                Intent data = result.getData();
            }
        });
    }

    public void displayAlert(alertType type) {
        switch (type) {
            case BT:
                displayAlert("Turn On Bluetooth", "Bluetooth needs to be turn on", alertType.BT);
                break;
            case DEFAULT:
                displayAlert("ERR0R..:.!", "You are not supposed to see this", alertType.DEFAULT);
                break;
        }
    }

    private void displayAlert(String title, String msg, alertType type) {
        AlertDialog.Builder alertBox = new AlertDialog.Builder(this.context);
        alertBox.setTitle(title);
        alertBox.setMessage(msg);
        if (type == alertType.BT) {
            alertBox.setPositiveButton("Turn On", (dialog, which) -> {
                Intent intent = new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE);
                activityResultLauncher.launch(intent);
            });
        } else {
            alertBox.setPositiveButton("OK", (dialog, which) -> {
            });
        }
        alertBox.setNegativeButton("Close", (dialog, which) -> {
        });
        alertBox.show();
    }

    public enum alertType {
        DEFAULT, BT
    }

}
