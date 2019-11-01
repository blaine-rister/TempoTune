package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

/**
 * Shortcut to instantiate a DyanamicModule using a Soundfont.
 */
public class SoundfontModule extends DynamicModule {
    public SoundfontModule(AppCompatActivity activity, Soundfont soundfont) {
        super(activity, soundfont.moduleName, soundfont.displayName);
    }
}
