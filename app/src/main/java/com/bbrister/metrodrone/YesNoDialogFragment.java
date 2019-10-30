package com.bbrister.metrodrone;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

public abstract class YesNoDialogFragment extends AlertDialogFragment {

    // Argument keys
    final public static String yesNoListenerKey = "yesNoListener";

    // Abstract methods to get the strings for yes/no buttons
    abstract int getYesResourceId();
    abstract int getNoResourceId();

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final YesNoDialogListener listener =
                (YesNoDialogListener) arguments.getSerializable(yesNoListenerKey);

              // Call the superclass to get a builder
        AlertDialog.Builder builder = super.buildDialog();

        // Add the yes/no buttons
        builder
                .setPositiveButton(getYesResourceId(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Begin installation
                        listener.onYes();
                    }
                })
                .setNegativeButton(getNoResourceId(), new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing, since installation was cancelled
                    }
                });

        return builder;
    }
}
