package com.bbrister.tempodrone;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;

public abstract class QueryPurchasesRequest extends BillingServiceRequest {

    // Configuration
    boolean quiet;

    // Context
    AppCompatActivity activity;

    public QueryPurchasesRequest(AppCompatActivity activity, PurchasesUpdatedListener listener,
                                 boolean quiet) {
        super(activity, listener);
        this.activity = activity;
        this.quiet = quiet;
        attemptTask();
    }

    /**
     * The main return point for the asynchronous task. Returns null on failure.
     */
    abstract protected void finished(Purchase.PurchasesResult purchasesResult);

    /**
     * The main task to complete after establishing a connection.
     */
    protected void task() {
        finished(billingClient.queryPurchases(BillingClient.SkuType.INAPP));
    }

    /**
     * Implements the superclass's failure() calls.
     */
    public void failure(String reason) {
        if (!quiet) {
            AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                    String.format(
                            activity.getString(R.string.query_purchases_fail),
                            reason
                    )
            );
        }
        finished(null);
    }
}
