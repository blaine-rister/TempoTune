package com.example.metrodrone;

import android.content.ComponentName;
import android.content.ServiceConnection;

import android.os.IBinder;
import android.os.Bundle;

import android.view.View;

import android.widget.ImageButton;

import android.content.Context;
import android.content.Intent;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.widget.NestedScrollView;

import com.google.android.gms.ads.MobileAds;
import com.google.android.gms.ads.AdRequest;
import com.google.android.gms.ads.AdView;
import com.google.android.gms.ads.InterstitialAd;

/* Abstract class to automate the task of connecting to the drone, other basic app stuff. */
public abstract class DroneActivity extends AppCompatActivity {

    // Constants
    final static boolean testAds = BuildConfig.DEBUG;

    // State
    boolean isTapping = false;
    TempoTapper tempoTapper;

    // Ads
    static InterstitialAd interstitialAd;

    // This is called when the drone service connects. Subclasses should override it to perform
    // actions such as UI initialization, which depend on the service being bound.
    protected void onDroneConnected() {}

    // This is called when the service is somehow updated. Subclasses should override it to update
    // their UI.
    protected void onDroneChanged() {}

    // Service interface
    protected DroneService.DroneBinder droneBinder;
    protected ServiceConnection droneConnection = new ServiceConnection()
    {
        @Override
        public void onServiceConnected(ComponentName className,
                                       IBinder service) {
            droneBinder = (DroneService.DroneBinder) service;
            setupUI();
            onDroneConnected();
        }

        @Override
        public void onServiceDisconnected(ComponentName name) {
            throw BuildConfig.DEBUG ? new DebugException("Lost connection to the server.") :
                    new DefaultException();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            throw BuildConfig.DEBUG  ? new DebugException("The server returned null on binding.") :
                    new DefaultException();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            throw BuildConfig.DEBUG ? new DebugException("The server binding died.") :
                    new DefaultException();
        }
    };

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the main layout, but don't define the button functionality
        setContentView(R.layout.activity_main);

        // Start the drone service and bind to it. When bound, we will set up the UI.
        Intent intent = new Intent(this, DroneService.class);
        if (startService(intent) == null) {
            throw BuildConfig.DEBUG ? new DebugException("Failed to start the service.") :
                    new DefaultException();
        }
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            throw BuildConfig.DEBUG ? new DebugException("Binding to the server returned false.") :
                    new DefaultException();
        }

        // Get the ad unit IDs
        final int bannerAdUnitIdRes = testAds ? R.string.test_banner_ad_unit_id :
                R.string.real_banner_ad_unit_id;
        final int interstitialAdUnitRes = testAds ? R.string.test_interstitial_ad_unit_id :
                R.string.real_interstitial_ad_unit_id;

        // Initialize ads
        MobileAds.initialize(this);

        // Initialize the banner ad, if present
        AdView banner = findViewById(R.id.adView);
        if (banner != null)
            banner.loadAd(new AdRequest.Builder().build());

        // Initialize the interstitial ad
        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getResources().getString(R.string.interstitial_ad_unit_id));
    }

    // On restart, load an ad
    @Override
    protected void onRestart()
    {
        super.onRestart();
        //TODO: Keep track of a static variable, time since last ad. Don't show ads on every restart since this happens when we switch screens. Enforce the time limit
        interstitialAd.loadAd(new AdRequest.Builder().build());
        interstitialAd.show();
    }

    // Set the main content, which is shown in a scrolling window in the middle of the screen
    protected void setContentLayout(final int contentLayoutId) {

        // Inflate the main content layout
        CoordinatorLayout mainLayout = findViewById(R.id.activity_main_layout);
        View contentView = getLayoutInflater().inflate(contentLayoutId, mainLayout, false);

        // Add this to the parent scrollView
        NestedScrollView scrollView = findViewById(R.id.mainContentScroll);
        scrollView.addView(contentView);
    }

    // Tell the service to start playing sound
    protected void play() {
        droneBinder.play();
    }

    // Tell the service to stop playing sound
    protected void pause() {
        droneBinder.pause();
    }

    // Set up the UI items shared across all drone activities, using the bound service
    private void setupUI() {

        // Set up the toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        // Play button
        ImageButton playButton = findViewById(R.id.playButton);
        playButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                play();
            }
        });

        // Pause button
        ImageButton pauseButton = findViewById(R.id.pauseButton);
        pauseButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                pause();
            }
        });

        // Tempo button
        ImageButton tempoButton = findViewById(R.id.tempoButton);
        tempoButton.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if (isTapping) {
                    tempoTapper.tap();
                } else {
                    //TODO could animate the button to indicate tapping mode
                    tempoTapper = new TempoTapper(view) {
                        @Override
                        public void onComplete(int bpm) {
                            isTapping = false;
                            droneBinder.setBpm(bpm);
                            onDroneChanged();
                        }

                        @Override
                        public void onCancel() {
                            // TODO animate button
                            isTapping = false;
                        }
                    };
                    isTapping = true;
                }
            }
        });
    }
}
