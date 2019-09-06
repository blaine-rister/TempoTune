package org.billthefarmer.mididriver;

/* Class to hold the settings passed to the render() function. */
public class RenderSettings {
    public byte pitchArray[];
    public byte velocity;
    public long noteDurationMs;
    public long recordDurationMs;
    public boolean volumeBoost;
}
