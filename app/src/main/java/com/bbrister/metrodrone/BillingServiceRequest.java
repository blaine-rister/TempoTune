package com.bbrister.metrodrone;

import android.content.res.Resources;

import androidx.appcompat.app.AppCompatActivity;

import static com.android.billingclient.api.BillingClient.BillingResponseCode;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingClientStateListener;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PurchasesUpdatedListener;

/**
 * Abstract class to start a billing connection then execute some kind of task. Retries the task a
 * certain number of times if the connection fails.
 */
public abstract class BillingServiceRequest {

    protected Resources resources;
    protected BillingClient billingClient;

    final private int maxTries = 3;
    private int numAttempts;

    abstract protected void task();
    abstract protected void failure(String reason);


    public BillingServiceRequest(AppCompatActivity activity, PurchasesUpdatedListener listener) {
        this.resources = activity.getResources();
        billingClient = BillingClient.newBuilder(activity)
                .setListener(listener)
                .enablePendingPurchases()
                .build();
        numAttempts = 0;
    }

    /**
     * Try to establish a connection and start the task.
     */
    protected void attemptTask() {
        numAttempts++;
        billingClient.startConnection(new BillingClientStateListener() {
            @Override
            public void onBillingSetupFinished(BillingResult billingResult) {
                if (billingResult.getResponseCode() != BillingResponseCode.OK) {
                    failure(billingResult.getDebugMessage());
                    return;
                }

                // The BillingClient is ready. You can query purchases here.
                numAttempts = 0;
                task();
            }

            @Override
            public void onBillingServiceDisconnected() {
                // Try to restart the connection on the next request to
                // Google Play by calling the startConnection() method.

                // Give up if we've exceeded the max number of retries
                if (numAttempts >= maxTries) {
                    failure(resources.getString(R.string.google_play_failure));
                    return;
                }

                // Try to establish a connection again
                attemptTask();
            }
        });
    }
}
