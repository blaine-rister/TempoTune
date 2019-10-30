package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

import static com.android.billingclient.api.BillingClient.BillingResponseCode;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;
import com.android.billingclient.api.SkuDetailsParams;
import com.android.billingclient.api.SkuDetailsResponseListener;

import java.util.ArrayList;
import java.util.List;

public abstract class QuerySkuRequest extends BillingServiceRequest {

    // Context
    AppCompatActivity activity;

    // Product info
    private String productName;

    public QuerySkuRequest(AppCompatActivity activity, PurchasesUpdatedListener listener,
                           String productName) {
        super(activity, listener);
        this.productName = productName;
        this.activity = activity;
        attemptTask();
    }

    /**
     * The main return point for the asynchronous task. Returns null on failure.
     */
    abstract protected void finished(SkuDetails details);

    /**
     * The main task to complete after establishing a connection.
     */
    protected void task() {
        querySKU();
    }

    /**
     * Implements the superclass's failure() calls.
     */
    public void failure(String reason) {
        AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                String.format(
                        activity.getString(R.string.sku_fail),
                        productName,
                        reason
                )
        );
        finished(null);
    }

    private void querySKU() {
        // Query the SKU details
        final List<String> skuList = new ArrayList<>();
        skuList.add(productName);
        SkuDetailsParams.Builder params = SkuDetailsParams.newBuilder();
        params.setSkusList(skuList).setType(BillingClient.SkuType.INAPP);
        billingClient.querySkuDetailsAsync(params.build(),
                new SkuDetailsResponseListener() {
                    @Override
                    public void onSkuDetailsResponse(BillingResult billingResult,
                                                     List<SkuDetails> skuDetailsList) {
                        // Handle errors
                        if (billingResult.getResponseCode() != BillingResponseCode.OK) {
                            failure(billingResult.getDebugMessage());
                            return;
                        }
                        if (skuDetailsList == null) {
                            failure(resources.getString(R.string.unexpected_error));
                            return;
                        }

                        // Check the returned values
                        for (SkuDetails details : skuDetailsList) {
                            // Check for the desired product
                            if (!details.getSku().equalsIgnoreCase(productName))
                                continue;

                            // Return the payload
                            finished(details);
                            return;
                        }

                        // Handle failure to find the product
                        failure(String.format(
                                resources.getString(R.string.failed_to_find_product),
                                productName
                        ));
                    }
                });
    }
}
