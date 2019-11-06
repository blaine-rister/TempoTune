package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

public class PremiumDialogFragment extends YesNoDialogFragment {

    /**
     * Abstract methods from the parent class.
     */
    protected int getYesResourceId() {
        return R.string.upgrade;
    }
    protected int getNoResourceId() {
        return R.string.not_now;
    }
    protected void onYes() {
        // Create a new PremiumManager and start the billing flow
        new PremiumManager((AppCompatActivity) getActivity()).purchase();
    }
}
