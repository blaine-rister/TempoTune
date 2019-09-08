package com.bbrister.metrodrone;

// Basic class for storing <instrument name, instrument code> pairs
public class NameValPair implements Comparable<NameValPair> {
    public String s;
    public int i;

    public NameValPair(String s, int i) {
        this.s = s;
        this.i = i;
    }

    // Sorts by the integer component
    @Override
    public int compareTo(NameValPair pair) {
        return this.i - pair.i;
    }

    // For printing
    @Override
    public String toString() {
        return s;
    }
}
