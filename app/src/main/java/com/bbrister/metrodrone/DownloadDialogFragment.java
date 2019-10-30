package com.bbrister.metrodrone;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;

public class DownloadDialogFragment extends YesNoDialogFragment {

    // Argument keys
    final public static String moduleNameKey = "moduleName";

    /**
     * Abstract methods from the parent class.
     */
    protected int getYesResourceId() {
        return R.string.yes;
    }
    protected int getNoResourceId() {
        return R.string.no;
    }

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final String moduleName = arguments.getString(moduleNameKey);

        // Format the message and put it in the arguments
        final String message = String.format(getString(R.string.download_prompt), moduleName);
        arguments.putString(AlertDialogFragment.messageKey, message);

        return super.buildDialog();
    }
}
