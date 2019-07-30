package com.example.metrodronemidi;

// Provides read-only access to the global settings, for the drone service
public class NoteSettingsReader {

    private NoteSettings settings;

    public NoteSettingsReader(NoteSettings settings) {
        this.settings = settings;
    }

    public double getVelocity() {
        return settings.velocity;
    }
}
