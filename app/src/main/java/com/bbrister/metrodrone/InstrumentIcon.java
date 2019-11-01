package com.bbrister.metrodrone;

import android.content.Context;
import android.content.res.Resources;

import java.util.List;

/**
 * A class to look up the icon of an instrument, given its name.
 */
public class InstrumentIcon {

    // Settings
    final private static int defaultDrawableId = R.drawable.ic_treble_clef_small;

    // Persistent data
    private String packageName;
    private Resources resources;
    private List<String[]> iconTable;

    public InstrumentIcon(Context context) {

        // Save the context metadata for future use
        packageName = context.getPackageName();
        resources = context.getResources();

        // Read the icon info from a file
        CsvReader csvReader = new CsvReader(resources);
        iconTable = csvReader.read(R.raw.instrument_icons);
    }

    /**
     * Given an instrument name, return the integer ID of its drawable.
     */
    public int lookupDrawable(String instrumentName) {

        // Check the correctness of the CSV header
        final int iconInstrumentNameIdx = 0;
        final int iconDrawableNameIdx = 1;
        CsvReader.verifyHeader(iconTable, new String[] {"INSTRUMENT", "ICON"});

        // First, pass through the rows looking for an exact match
        String drawableName = null;
        for (int i = 1; i < iconTable.size(); i++) {
            final String[] row = iconTable.get(i);
            final String iconInstrumentName = row[iconInstrumentNameIdx];
            if (instrumentName.equalsIgnoreCase(iconInstrumentName)) {
                drawableName = row[iconDrawableNameIdx];
                break;
            }
        }

        // Return the match if one was found
        if (drawableName != null) {
            return getDrawable(drawableName);
        }

        // If not, pass through again looking for a substring match
        final String instrumentNameLower = instrumentName.toLowerCase();
        for (int i = 1; i < iconTable.size(); i++) {
            final String[] row = iconTable.get(i);
            final String iconName = row[iconInstrumentNameIdx];
            if (instrumentNameLower.contains(iconName.toLowerCase())) {
                drawableName = row[iconDrawableNameIdx];
                break;
            }
        }

        // Return the match if one was found, else some default value
        return drawableName == null ? defaultDrawableId : getDrawable(drawableName);
    }

    /**
     * Return the integer ID of a given drawable.
     */
    private int getDrawable(String name) {
        return resources.getIdentifier(name, "drawable", packageName);
    }
}
