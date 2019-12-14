package com.bbrister.tempodrone;

import android.app.Activity;
import android.app.Dialog;
import android.os.Bundle;
import android.text.SpannableString;
import android.text.method.LinkMovementMethod;
import android.text.util.Linkify;
import android.widget.TextView;

import androidx.core.text.util.LinkifyCompat;
import androidx.fragment.app.DialogFragment;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

public class AlertDialogFragment extends DialogFragment {

    // Argument keys
    final public static String messageKey = "message";

    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        // Build and return the dialog
        return buildDialog().create();
    }

    /**
     * Override this to change the dialog properties in subclasses.
     */
    protected AlertDialog.Builder buildDialog() {

        // Retrieve the arguments
        final Bundle arguments = getArguments();
        final String message = arguments.getString(messageKey);

        // Format the text in a TextView, for hyperlinks and other features
        TextView textView = new TextView(getContext());
        SpannableString linkMessage = new SpannableString(message);
        LinkifyCompat.addLinks(linkMessage, Linkify.ALL);
        textView.setText(linkMessage);
        textView.setMovementMethod(LinkMovementMethod.getInstance());

        // Build the dialog with the message
        AlertDialog.Builder builder = new AlertDialog.Builder(getActivity());
        builder.setView(textView);

        // Add an 'OK' button for exiting the dialog
        builder.setPositiveButton(R.string.ok, null);

        return builder;
    }

    /**
     * Use this to create and show the dialog.
     */
    public static void showDialog(FragmentManager fragmentManager, String message) {
        // Create the argument bundle
        Bundle arguments = new Bundle();
        arguments.putString(AlertDialogFragment.messageKey, message);

        // Create the dialog and add its arguments
        AlertDialogFragment dialog = new AlertDialogFragment();
        dialog.setArguments(arguments);

        // Show the dialog
        dialog.show(fragmentManager, FragmentTags.alertDialogTag);
    }
}
