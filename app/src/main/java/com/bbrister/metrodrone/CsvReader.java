package com.bbrister.metrodrone;

import android.content.res.Resources;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;

public class CsvReader {

    Resources resources;

    // Initialize the CSV reader with a handle to the resources
    public CsvReader(Resources resources) {
        this.resources = resources;
    }

    // Method to handle reading the instrument CSV file. Returns the results in a list.
    protected List<String[]> read(int resourceId) {
        try {
            return readCsvHelper(resourceId);
        } catch (IOException ie) {
            ie.printStackTrace();
            throw BuildConfig.DEBUG_EXCEPTIONS ? new DebugException("Failed to read the CSV file!") :
                    new DefaultException();
        }
    }

    // Does the work of readCsv, wrapped to catch exceptions
    private List<String[]> readCsvHelper(int resourceId) throws IOException  {

        List<String[]> parsedLines = new ArrayList<>();

        InputStream inputStream = resources.openRawResource(resourceId);
        BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(inputStream));

        String line;
        int itemsPerLine = -1;
        while ((line = bufferedReader.readLine()) != null) {

            // Parse the line
            String[] items = line.split(",");

            // Get the header width
            if (itemsPerLine < 0) {
                itemsPerLine = items.length;
            };

            // Check formatting and add the data
            if (items.length != itemsPerLine) throw BuildConfig.DEBUG_EXCEPTIONS ?
                    new DebugException("Invalid CSV line: " + line) : new DefaultException();
            for (int i = 0; i < items.length; i++) {
                items[i] = items[i].trim();
            }
            parsedLines.add(items);
        }

        return parsedLines;
    }
}
