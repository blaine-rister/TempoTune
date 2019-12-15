package com.bbrister.tempodrone;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.android.billingclient.api.BillingClient;
import com.android.billingclient.api.BillingResult;
import com.android.billingclient.api.Purchase;
import com.android.billingclient.api.PurchasesUpdatedListener;
import com.android.billingclient.api.SkuDetails;

import java.util.List;

public class PremiumManager implements PurchasesUpdatedListener {

    // Callback interface
    interface PurchaseListener {
        void onIsPurchased(boolean isPurchased);
    }

    // The name of the upgrade
    private static final String productName = "premium";

    // The calling activity
    AppCompatActivity activity;

    public PremiumManager(AppCompatActivity activity) {
        this.activity = activity;
    }


    /**
     * The main use case. Returns true the product is purchased, otherwise prompts the user to
     * purchase it.
     */
    public void promptPremium(final String message) {

        // Check if we have already purchased the product
        isPurchased(new PurchaseListener() {
            @Override
            public void onIsPurchased(boolean isPurchased) {
                if (!isPurchased) {
                    // Complete the purchase flow
                    completePromptPremium(message);
                }
            }
        });
    }

    private void completePromptPremium(final String message) {

        // Create the argument bundle
        Bundle arguments = new Bundle();
        arguments.putString(PremiumDialogFragment.messageKey, message);

        // Create the dialog and add its arguments
        PremiumDialogFragment dialog = new PremiumDialogFragment();
        dialog.setArguments(arguments);

        // Show the dialog
        dialog.show(activity.getSupportFragmentManager(), FragmentTags.premiumDialogTag);
    }

    /**
     * Required listener function.
     */
    @Override
    public void onPurchasesUpdated(BillingResult billingResult, List<Purchase> purchases) {
        if (billingResult.getResponseCode() == BillingClient.BillingResponseCode.OK &&
                purchases != null) {

            boolean purchasesChanged = false;

            // Handle any successful purchases
            for (Purchase purchase : purchases) {
                // Acknowledge the purchase
                if (purchase.getPurchaseState() == Purchase.PurchaseState.PURCHASED) {
                    acknowledgePurchase(purchase);
                    purchasesChanged = true;
                }
            }

            // Restart the activity if purchases were changed
            if (purchasesChanged) activity.recreate();

        } else {
            handlePurchaseErrors(billingResult);
        }
    }

    /**
     * Send the user an alert depending on the billing error code.
     */
    protected void handlePurchaseErrors(BillingResult billingResult) {
        switch (billingResult.getResponseCode()) {
            case BillingClient.BillingResponseCode.OK:
                return;
            case BillingClient.BillingResponseCode.USER_CANCELED:
                // Handle an error caused by a user cancelling the purchase flow.
                // N.B. nothing to do here...
                return;
            default:
                // Handle any other error codes
                AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                        String.format(
                                activity.getString(R.string.purchase_fail),
                                productName,
                                billingResult.getDebugMessage()
                        )
                );
        }
    }

    private Purchase completeQueryPurchases(final Purchase.PurchasesResult purchasesResult,
                                            final boolean quiet) {

        // Initialize the security library
        Security security = new Security(activity.getApplicationContext());

        /**
         * TODO: Is this needed?
        // Check the error code
        BillingResult billingResult = purchasesResult.getBillingResult();
        switch (billingResult.getResponseCode()) {
         */

        // Check if there are any purchases
        List<Purchase> purchasesList = purchasesResult.getPurchasesList();
        if (purchasesList == null)
            return null;

        // Iterate through all purchases
        boolean invalidSignature = false;
        for (Purchase purchase : purchasesList) {
            // Check if this is the same product
            if (!purchase.getSku().equalsIgnoreCase(productName))
                continue;

            // Check the signature
            if (!security.verifyPurchase(purchase)) {
                invalidSignature = true;
                continue;
            }

            return purchase;
        }

        // Alert the user if failure is due to an invalid signature
        if (invalidSignature && !quiet) {
            AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                    activity.getString(R.string.purchase_invalid_signature)
            );
        }

        // Return null if nothing was found
        return null;
    }

    /**
     * Acknowledge a purchase.
     */
    private void acknowledgePurchase(Purchase purchase) {
        // Do nothing if it's already acknowledged or not yet purchased
        if (purchase.isAcknowledged() ||
                purchase.getPurchaseState() != Purchase.PurchaseState.PURCHASED) {
            return;
        }

        // Else start the acknowledgment request
        new AcknowledgePurchaseRequest(activity, this, purchase);
    }

    /**
     * Query all purchases and acknowlege any which have yet to be. Sends no alerts if purchases
     * cannot be queried. Only sends an alert if acknowledgment fails.
     */
    public void acknowledgeAll() {

        final boolean quiet = true;

        new QueryPurchasesRequest(activity, this, quiet) {
            @Override
            public void finished(Purchase.PurchasesResult purchasesResult) {
                // Do nothing if querying fails
                if (purchasesResult == null)
                    return;

                // Else check for the desired purchase
                Purchase purchase = completeQueryPurchases(purchasesResult, quiet);
                if (purchase == null)
                    return;

                // Finally proceed to acknowledgment
                acknowledgePurchase(purchase);
            }
        };
    }

    /**
     * Queries whether the product has previously been purchased. If so, acknowledges the purchase.
     * Informs the user of purchase status through alerts.
     */
    public void isPurchased(final PurchaseListener listener) {

        // Query for a purchase history
        new QueryPurchasesRequest(activity, this, false) {
            @Override
            public void finished(Purchase.PurchasesResult purchasesResult) {
                // Handle errors
                if (purchasesResult == null) {
                    listener.onIsPurchased(false);
                    return;
                }

                // Proceed to return the purchases
                completeIsPurchased(purchasesResult, listener);
            }
        };
    }

    private void completeIsPurchased(Purchase.PurchasesResult purchasesResult,
                                    final PurchaseListener listener) {

        // Finish querying for the purchase
        Purchase purchase = completeQueryPurchases(purchasesResult, false);

        // Return false if no matching purchase was found
        if (purchase == null) {
            listener.onIsPurchased(false);
            return;
        }

        // Check the purchase state
        switch (purchase.getPurchaseState()) {
            case Purchase.PurchaseState.PURCHASED:
                // Check if the purchase is acknowledged.
                if (purchase.isAcknowledged()) {
                    listener.onIsPurchased(true);
                    return;
                } else {
                    // Acknowledge the purchase. Return false for now, since this is an asynchronous
                    // operation.
                    acknowledgePurchase(purchase);
                    listener.onIsPurchased(false);
                    return;
                }
            case Purchase.PurchaseState.PENDING:
                // Here you can confirm to the user that they've started the pending
                // purchase, and to complete it, they should follow instructions that
                // are given to them. You can also choose to remind the user in the
                // future to complete the purchase if you detect that it is still
                // pending.
                AlertDialogFragment.showDialog(activity.getSupportFragmentManager(),
                        String.format(
                                activity.getString(R.string.purchase_pending),
                                purchase.getSku()
                        ));
                listener.onIsPurchased(false);
                return;
            case Purchase.PurchaseState.UNSPECIFIED_STATE:
                listener.onIsPurchased(false);
                return;
            default:
                if (BuildConfig.DEBUG_EXCEPTIONS) {
                    throw new DebugException("Unrecognized purchase state");
                }
                listener.onIsPurchased(false);
                return;
        }
    }

    public void purchase() {
        // Get the SKU details, then finish the purchase
        new QuerySkuRequest(activity, this, productName) {
            @Override
            public void finished(SkuDetails details) {
                finishPurchase(details);
            }
        };
    }

    private void finishPurchase(SkuDetails details) {
        // Start the purchase flow
        new LaunchBillingFlowRequest(activity, this, details);
    }
}
