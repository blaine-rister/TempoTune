package com.bbrister.metrodrone;

import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.SeekBar;

public class SettingsActivity extends DroneActivity {

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Customize the menu to remove the option for this class
        hideMenuAction(R.id.action_settings);

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

        // Reverb
        final int numReverbChoices = droneBinder.getNumReverbPresets();
        SeekBar reverbSeekBar = findViewById(R.id.reverbSeekBar);
        reverbSeekBar.setMax(numReverbChoices - 1);
        reverbSeekBar.setProgress(droneBinder.getReverbPreset());
        reverbSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                // Do nothing
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
                // Do nothing
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                // Update when a selection is made
                droneBinder.setReverbPreset(seekBar.getProgress());
            }
        });
    }
}
