package com.bbrister.metrodrone;

import androidx.appcompat.app.AppCompatActivity;

public class Soundfont extends DynamicModule {

    // Constants
    final static String fileExt = ".sf2";

    // Data about this soundfont
    protected String path;
    protected boolean isFree;

    // Provide the path and package name
    public Soundfont(AppCompatActivity activity, String path, String packageName, boolean isFree) {
        super(activity, packageName, getDisplayName(path));
        this.path = path;
        this.isFree = isFree;
    }

    /**
     * Requests the dynamic module corresponding to a given soundfont.
     */
    public void request(final DynamicModule.InstallListener listener) {
        setInstallListener(listener);
        installQuiet();
    }

    // Format the path as a display name
    private static String getDisplayName(final String path) {
        // Strip the file extension
        String baseName = path.substring(0, path.length() - fileExt.length());

        // Format the name for display
        char[] displayNameChars = baseName.toCharArray();
        displayNameChars[0] = Character.toTitleCase(displayNameChars[0]);
        for (int i = 1; i < displayNameChars.length; i++) {
            switch (displayNameChars[i - 1]) {
                case '_':
                    displayNameChars[i - 1] = ' '; // Convert to spaces
                case ' ':
                    displayNameChars[i] = Character.toTitleCase(displayNameChars[i]);
            }
        }

        return new String(displayNameChars).trim();
    }

    // For printing
    @Override
    public String toString() {
        return displayName;
    }
}
