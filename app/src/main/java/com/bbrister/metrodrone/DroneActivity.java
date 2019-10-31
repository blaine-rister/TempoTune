package com.bbrister.metrodrone;

import android.content.ComponentName;
import android.content.ServiceConnection;

import android.os.IBinder;
import android.os.Bundle;

import android.view.Menu;
import android.view.MenuItem;
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

import java.util.Iterator;
import java.util.Set;
import java.util.HashSet;

/* Abstract class to automate the task of connecting to the drone, other basic app stuff. */
public abstract class DroneActivity extends AppCompatActivity {

    // Read-only configuration settings
    private boolean premiumMode;

    // Customization
    Set<Integer> hiddenActions;

    // State
    boolean isTapping;
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
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
                    "Lost connection to the server.") : new DefaultException();
        }

        @Override
        public void onNullBinding(ComponentName name) {
            throw BuildConfig.DEBUG_EXCEPTIONS  ? new DebugException(
                    "The server returned null on binding.") : new DefaultException();
        }

        @Override
        public void onBindingDied(ComponentName name) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("The server binding died.") :
                    new DefaultException();
        }
    };

    @Override
    protected void onCreate(final Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Initialize configuration/premium settings
        premiumMode = false;
        interstitialAd = null;

        // Initialize state
        isTapping = false;

        // Set customizations to default
        hiddenActions = new HashSet<>();

        // Set the main layout, but don't define the button functionality
        setContentView(R.layout.activity_main);

        // Query premium mode. Continue setting up the app afterwards
        new PremiumManager(this).isPurchased(new PremiumManager.PurchaseListener() {
            @Override
            public void onIsPurchased(boolean isPurchased) {
                final boolean firstCreation = savedInstanceState == null;
                onReceivePremiumMode(isPurchased, firstCreation);
            }
        });
    }

    /**
     * Finish setting up the app, after querying whether we are in premium mode.
     */
    protected void onReceivePremiumMode(final boolean isPurchased, final boolean firstTime) {

        // Record the premium mode setting, for later usage
        premiumMode = isPurchased;

        // Optionally initialize the ads
        if (!isPurchased) initAds();

        // Start the drone service and bind to it. When bound, we will set up the UI.
        Intent intent = new Intent(this, DroneService.class);
        if (startService(intent) == null) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
                    "Failed to start the service.") : new DefaultException();
        }
        if (!bindService(intent, droneConnection, Context.BIND_AUTO_CREATE)) {
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException(
                    "Binding to the server returned false.") : new DefaultException();
        }
    }

    /**
     * Initialize and display the ads.
     */
    private void initAds() {

        // Initialize ad API
        MobileAds.initialize(this);

        // Get the root layout
        CoordinatorLayout rootLayout = findViewById(R.id.activity_main_layout);

        // Inflate and initialize the banner
        AdView banner = (AdView) getLayoutInflater().inflate(R.layout.banner_ad, rootLayout,
                false);
        banner.loadAd(new AdRequest.Builder().build());

        // Attach the banner to the root layout
        rootLayout.addView(banner);

        // Initialize the interstitial ad
        interstitialAd = new InterstitialAd(this);
        interstitialAd.setAdUnitId(getString(R.string.interstitial_ad_unit_id));
    }

    /**
     * Basic maintenance tasks, including purchase acknowledgment.
     */
    @Override
    protected void onResume() {
        super.onResume();

        // Acknowledge any un-acknowledged purchases
        (new PremiumManager(this)).acknowledgeAll();
    }

    // On restart, load an ad
    @Override
    protected void onRestart()
    {
        super.onRestart();

        // Display an interstitial ad for the free version
        if (!havePremium() && interstitialAd != null) {
            //TODO: Keep track of a static variable, time since last ad. Don't show ads on every restart since this happens when we switch screens. Enforce the time limit
            interstitialAd.loadAd(new AdRequest.Builder().build());
            interstitialAd.show();
        }
    }

    // Clean up
    @Override
    protected void onDestroy() {
        super.onDestroy();
        unbindService(droneConnection);
    }

    // Create the menu
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);

        // Process the hidden actions
        for (Integer i : hiddenActions) {
            menu.findItem(i).setVisible(false);
        }

        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        switch (item.getItemId()) {
            case R.id.action_settings:
                startActivity(new Intent(this, SettingsActivity.class));
                return true;
            case R.id.action_about:
                // Start the credits activity to display the credits
                startActivity(new Intent(this, CreditsActivity.class));
                return true;
            default:
                // For default buttons such as home/up
                return super.onOptionsItemSelected(item);
        }
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

    // Hide a specific menu action
    protected void hideMenuAction(final int actionId) {
        hiddenActions.add(actionId);
        invalidateOptionsMenu();
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

    /**
     * Query whether this activity is in premium mode.
     */
    protected boolean havePremium() {
        return premiumMode;
    }
}
