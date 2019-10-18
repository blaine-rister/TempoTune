package com.bbrister.metrodrone;

import android.content.Context;

/**
 * Shortcut to instantiate a DyanamicModule using a Soundfont.
 */
public class SoundfontModule extends DynamicModule {
    public SoundfontModule(Context context, Soundfont soundfont) {
        super(context, soundfont.moduleName, soundfont.displayName, soundfont.isFree);
    }
}
