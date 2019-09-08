package com.bbrister.metrodrone;

import com.google.android.material.snackbar.Snackbar;

import android.os.Handler;
import android.view.View;

import java.util.List;
import java.util.ArrayList;

public abstract class TempoTapper  {

    Snackbar snackbar;

    private Handler terminationHandler = new Handler();
    List<Long> taps = new ArrayList<Long>();
    int terminationCounter = 0;
    int bpm = 0;
    public boolean hasReturned = false;

    public TempoTapper(View view) {
        // Set up the instruction snackbar, cancel everything if it's removed
        snackbar = Snackbar.make(view, "Tap the desired tempo.",
                Snackbar.LENGTH_INDEFINITE);
        snackbar.setAction("Action", null).show();
        snackbar.addCallback(new Snackbar.Callback() {
            @Override
            public void onDismissed(Snackbar snackber, int event) {
                if (!hasReturned) terminate();
            }
        });

        scheduleTermination();
    }

    // Returns a successful result in BPM. Either this or onCancel() is called only once.
    public abstract void onComplete(int bpm);

    // Called in case the action was cancelled. Either this or onComplete() is called only once.
    public abstract void onCancel();

    // Use this to count a tap
    public void tap() {
        taps.add(System.currentTimeMillis());
        scheduleTermination();
    }

    // Schedule termination of this snackbar in 2s, unless this is called again
    public void scheduleTermination() {
        // Update the counter
        terminationCounter++;

        // Schedule termination
        final long delayMs = 2000;
        terminationHandler.postDelayed(new Runnable() {
            @Override
            public void run() {
                // Only proceed when all scheduled terminations have completed
                terminationCounter--;
                if (terminationCounter > 0) return;
                terminate();
            }
        }, delayMs);
    }

    // Cancel everything and call either onComplete() or onCancel()
    public void terminate() {

        if (hasReturned) return;

        // Cancel if we don't have enough taps
        final int numTaps = taps.size();
        if (numTaps < 2) {
            snackbar.dismiss();
            cancel();
            return;
        }

        // Compute the elapsed time and average taps per minute
        final long elapsedMs = taps.get(numTaps - 1) - taps.get(0);
        final double tapsPerMs = (double) (numTaps - 1) / (double) elapsedMs;
        final double msPerMinute = 1000. * 60.;
        final double tapsPerMinute = tapsPerMs * msPerMinute;
        final int bpm = (int) Math.round(tapsPerMinute);

        // Terminate and return the BPM
        complete(bpm);
        snackbar.dismiss();
    }


    // Calls onComplete
    public void complete(int bpm) {
        if (!hasReturned) onComplete(bpm);
        hasReturned = true;
    }

    // Calls onCancel
    public void cancel() {
        if (!hasReturned) onCancel();
        hasReturned = true;
    }
}
