package com.example.metrodrone;

import android.os.Bundle;
import android.text.method.LinkMovementMethod;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

public class CreditsActivity extends AppCompatActivity {
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Set the content
        setContentView(R.layout.credits);
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));

        // Enable links in the textView
        TextView textView = findViewById(R.id.creditsTextView);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
    }
}
