package com.bbrister.tempodrone;

import android.content.DialogInterface;

import androidx.appcompat.app.AlertDialog;

public abstract class YesNoDialogFragment extends AlertDialogFragment {

    // Abstract methods to get the strings for yes/no buttons
    abstract int getYesResourceId();
    abstract int getNoResourceId();

    // Implementation of 'yes'
    abstract void onYes();

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

              // Call the superclass to get a builder
        AlertDialog.Builder builder = super.buildDialog();

        // Add the yes/no buttons
        builder
                .setPositiveButton(getYesResourceId(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        onYes();
                    }
                })
                .setNegativeButton(getNoResourceId(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing
                    }
                });

        return builder;
    }
}
