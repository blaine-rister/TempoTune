package com.bbrister.tempodrone;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.core.text.HtmlCompat;

public class CreditsActivity extends DroneActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Customize the menu to remove the option for this class
        hideMenuAction(R.id.action_about);

        // Set the content
        setContentLayout(R.layout.content_credits);

        // Construct the text and parse the HTML links
        TextView textView = findViewById(R.id.creditsTextView);
        textView.setText(HtmlCompat.fromHtml(
            String.format(
                getString(R.string.credits),
                BuildConfig.VERSION_NAME
            ),
            HtmlCompat.FROM_HTML_MODE_LEGACY
        ));

        // Enable links in the textView
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
