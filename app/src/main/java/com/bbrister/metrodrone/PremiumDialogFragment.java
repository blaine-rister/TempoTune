package com.bbrister.metrodrone;

import androidx.appcompat.app.AlertDialog;

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

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

        // Add a message to the arguments
        getArguments().putString(messageKey, getString(R.string.premium_prompt));

        // Get the base builder from the superclass
        return super.buildDialog();
    }
}
