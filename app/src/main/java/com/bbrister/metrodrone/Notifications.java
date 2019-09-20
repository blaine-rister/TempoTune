package com.bbrister.metrodrone;

/* Manage application-wide settings related to notifications */
public class Notifications {
    private static int nextId = 1; // The next notification ID to be dispensed

    // Returns an application-level unique notification ID
    public static int getUniqueId() {
        return nextId++;
    }
}
