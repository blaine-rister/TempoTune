package com.bbrister.tempodrone;

// Basic class for storing <instrument name, instrument code> pairs
public class NameValPair<T> {
    public String name;
    public T val;

    public NameValPair(String name, T val) {
        this.name = name;
        this.val = val;
    }

    // For printing
    @Override
    public String toString() {
        return name;
    }
}
