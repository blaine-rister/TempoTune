package com.example.metrodrone;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;

public class SettingsActivity extends DroneActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content
        setContentLayout(R.layout.content_settings);
    }

    @Override
    protected void onDroneConnected() {
        setupUI();
    }

    // Set up the UI elements
    private void setupUI() {
        // Volume boost (DNR compression)
        CheckBox volumeBoostBox = findViewById(R.id.volumeBoostBox);
        volumeBoostBox.setChecked(droneBinder.getVolumeBoost());
        volumeBoostBox.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton compoundButton, boolean isChecked) {
                droneBinder.setVolumeBoost(isChecked);
            }
        });

        // Reverb (room size)
    }
}
