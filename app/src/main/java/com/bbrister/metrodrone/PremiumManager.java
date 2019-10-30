package com.bbrister.metrodrone;

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

        // Create the callback interface
        YesNoDialogListener listener = new YesNoDialogListener() {
            @Override
            public void onYes() {
                purchase();
            }
        };

        // Create the argument bundle
        Bundle arguments = new Bundle();
        arguments.putSerializable(DownloadDialogFragment.yesNoListenerKey, listener);
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

    private Purchase completeQueryPurchases(Purchase.PurchasesResult purchasesResult) {

        // Check the error code
        BillingResult billingResult = purchasesResult.getBillingResult();
        //switch (purchasesResult.getBillingResult()) {
        //TODO

        // Check if there are any purchases
        List<Purchase> purchasesList = purchasesResult.getPurchasesList();
        if (purchasesList == null)
            return null;

        // Iterate through all purchases
        for (Purchase purchase : purchasesList) {
            // Check if this is the same product
            if (!purchase.getSku().equalsIgnoreCase(productName))
                continue;

            return purchase;
        }

        // Return null if nothing was found
        return null;
    }

    /**
     * Acknowledge a purchase.
     */
    private void acknowledgePurchase(Purchase purchase) {
        // Do nothing if it's already acknowledged
        if (purchase.isAcknowledged())
            return;

        // Else start the acknowledgment request
        new AcknowledgePurchaseRequest(activity, this, purchase);
    }

    /**
     * Query all purchases and acknowlege any which have yet to be.
     */
    public void acknowledgeAll() {
        isPurchased(new PurchaseListener() {
            @Override
            public void onIsPurchased(boolean isPurchased) {
                // Do nothing
            }
        });
    }

    /**
     * Queries whether the product has previously been purchased. If so, acknowledges the purchase.
     */
    public void isPurchased(final PurchaseListener listener) {

        // Query for a purchase history
        new QueryPurchasesRequest(activity, this) {
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

    public void completeIsPurchased(Purchase.PurchasesResult purchasesResult,
                                    final PurchaseListener listener) {

        // Finish querying for the purchase
        Purchase purchase = completeQueryPurchases(purchasesResult);

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
