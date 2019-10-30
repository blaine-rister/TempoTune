package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.BillingFlowParams;
import com.android.billingclient.api.SkuDetails;

public class LaunchBillingFlowRequest extends BillingServiceRequest {

    private AppCompatActivity activity;
    private PremiumManager premiumManager;
    private SkuDetails details;

    /**
     * The main action performed once a connection is established.
     */
    protected void task() {
        launchBillingFlow();
    }

    /**
     * For the superclass to report failure.
     */
    protected void failure(String reason) {
        AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                String.format(
                        activity.getString(R.string.billing_flow_fail),
                        reason
                )
        );
    }

    public LaunchBillingFlowRequest(AppCompatActivity activity, PremiumManager premiumManager,
                                    SkuDetails details) {
        super(activity, premiumManager);
        this.activity = activity;
        this.premiumManager = premiumManager;
        this.details = details;
        attemptTask();
    }

    private void launchBillingFlow() {
        BillingFlowParams flowParams = BillingFlowParams.newBuilder()
                .setSkuDetails(details)
                .build();
        premiumManager.handlePurchaseErrors(billingClient.launchBillingFlow(activity, flowParams));
    }
}
