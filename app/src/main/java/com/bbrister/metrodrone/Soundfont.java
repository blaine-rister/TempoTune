package com.bbrister.metrodrone;

public class Soundfont {

    // Constants
    final static String fileExt = ".sf2";

    // Data about this soundfont
    protected String path;
    protected String displayName;
    protected String moduleName;
    protected boolean isFree;

    // Provide the path and package name
    public Soundfont(String path, String packageName, boolean isFree) {
        this.path = path;
        this.moduleName = packageName;
        this.isFree = isFree;
        displayName = getDisplayName(path);
    }

    // Format the path as a display name
    private String getDisplayName(final String path) {
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
