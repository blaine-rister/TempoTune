package com.bbrister.metrodrone;

import android.content.DialogInterface;
import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

public class DownloadDialogFragment extends AlertDialogFragment {

    // Argument keys
    final public static String moduleNameKey = "moduleName";
    final public static String downloadListenerKey = "downloadListener";

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final String moduleName = arguments.getString(moduleNameKey);
        final DownloadDialogListener listener =
                (DownloadDialogListener) arguments.getSerializable(downloadListenerKey);

        // Format the message and put it in the arguments
        final String message = String.format(getString(R.string.download_prompt), moduleName);
        arguments.putString(AlertDialogFragment.messageKey, message);

        // Call the superclass to get a builder
        AlertDialog.Builder builder = super.buildDialog();

        // Add the yes/no buttons
            builder
                .setPositiveButton(R.string.yes, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Begin installation
                        listener.onStartDownload();
                    }
                })
                .setNegativeButton(R.string.no, new DialogInterface.OnClickListener() {
                    public void onClick(DialogInterface dialog, int id) {
                        // Do nothing, since installation was cancelled
                    }
                });

        return builder;
    }
}
