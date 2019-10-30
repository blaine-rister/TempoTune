package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.AcknowledgePurchaseParams;
import com.android.billingclient.api.AcknowledgePurchaseResponseListener;
import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

public class AcknowledgePurchaseRequest extends BillingServiceRequest {

    // Private data passed to task()
    private Purchase purchase;
    private AppCompatActivity activity;

    /**
     * The main action performed once a connection is established.
     */
    protected void task() {
        requestAcknowledgment(purchase);
    }

    /**
     * For the superclass to report failure.
     */
    protected void failure(String reason) {
        AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                String.format(
                        activity.getString(R.string.acknowledge_fail),
                        purchase.getSku(),
                        reason
                )
        );
    }

    /**
     * Constructor begins the request.
     */
    public AcknowledgePurchaseRequest(AppCompatActivity activity, PurchasesUpdatedListener listener,
                                      Purchase purchase) {
        super(activity, listener);
        this.purchase = purchase;
        this.activity = activity;
        attemptTask();
    }

    /**
     * Initiate the process of acknowledging a purchase, if not already done.
     */
    private void requestAcknowledgment(final Purchase purchase) {
        // Return success if it's already acknowledged
        if (purchase.isAcknowledged()) {
            return;
        }

        // Create the response listener
        AcknowledgePurchaseResponseListener listener =
                new AcknowledgePurchaseResponseListener() {
                    @Override
                    public void onAcknowledgePurchaseResponse(BillingResult billingResult) {
                        if (billingResult.getResponseCode() !=
                                BillingClient.BillingResponseCode.OK) {
                            failure(billingResult.getDebugMessage());
                        }
                    }
                };

        // Send the request
        AcknowledgePurchaseParams acknowledgePurchaseParams =
                AcknowledgePurchaseParams.newBuilder()
                        .setPurchaseToken(purchase.getPurchaseToken())
                        .build();
        billingClient.acknowledgePurchase(acknowledgePurchaseParams, listener);
    }
}
