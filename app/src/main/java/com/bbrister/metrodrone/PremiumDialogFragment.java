package com.bbrister.metrodrone;

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
}
