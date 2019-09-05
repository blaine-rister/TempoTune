package com.example.metrodrone;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

public class CreditsActivity extends DroneActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content
        setContentLayout(R.layout.content_credits);

        // Enable links in the textView
        TextView textView = findViewById(R.id.creditsTextView);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
