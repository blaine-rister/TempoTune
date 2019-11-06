package com.bbrister.metrodrone;

import android.os.Bundle;

import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;

public class DownloadDialogFragment extends YesNoDialogFragment {

    // Argument keys
    final public static String moduleNameKey = "moduleName";
    final public static String displayNameKey = "displayName";

    /**
     * Abstract methods from the parent class.
     */
    protected int getYesResourceId() {
        return R.string.yes;
    }
    protected int getNoResourceId() {
        return R.string.no;
    }
    protected void onYes() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final String moduleName = arguments.getString(moduleNameKey);
        final String displayName = arguments.getString(displayNameKey);

        // Create a new DynamicModule and install it
        new DynamicModule((AppCompatActivity) getActivity(), moduleName, displayName)
                .install(false);
    }

    /**
     * Override buildDialog to customize the displayed dialog.
     */
    @Override
    protected AlertDialog.Builder buildDialog() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final String displayName = arguments.getString(displayNameKey);

        // Format the message and put it in the arguments
        final String message = String.format(getString(R.string.download_prompt), displayName);
        arguments.putString(AlertDialogFragment.messageKey, message);

        return super.buildDialog();
    }
}
