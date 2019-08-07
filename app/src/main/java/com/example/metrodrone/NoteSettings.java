package com.example.metrodrone;

// Class to hold the settings which vary during the operation of the note player
public class NoteSettings {
    double velocity = 1.0; // [0,1]
    double duration = 0.95; // [0,1]

    // Get read-only access to this class
    public NoteSettingsReader getReader() {
        return new NoteSettingsReader(this);
    }
}
