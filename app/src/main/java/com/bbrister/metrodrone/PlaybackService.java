package com.bbrister.metrodrone;

import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.os.Build;
import android.os.IBinder;
import android.support.v4.media.session.MediaSessionCompat;

import androidx.core.app.NotificationCompat;
import androidx.core.content.ContextCompat;

import com.bbrister.mididriver.PlaybackDriver;

/* A foreground service which plays sound on loop. */
public class PlaybackService extends Service {

    // Intent data tags
    public final static String soundTag = "sound";

    // Intent actions
    public final static String startAction = "startPlayback";
    public final static String stopAction = "stopPlayback";

    // Static check for media session API level
    public static final boolean hasMediaSession = Build.VERSION.SDK_INT >= 21;

    // An ID unique for the playback notification
    private static final int playbackNotificationId = Notifications.getUniqueId();

    // Data
    private PlaybackDriver driver;
    MediaSessionCompat mediaSession;

    // Create a bass intent for this service
    private static Intent getBaseIntent(Context context) {
        return new Intent(context, PlaybackService.class);
    }

    // Create a start action intent
    public static Intent getStartIntent(Context context) {
        return getBaseIntent(context).setAction(startAction);
    }

    // Create a stop action intent
    public static Intent getStopIntent(Context context) {
        return getBaseIntent(context).setAction(stopAction);
    }

    // Return null from binding attempts--this is strictly a started service
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();

        // Load the library
        driver = new PlaybackDriver();
    }

    @Override
    public int onStartCommand(Intent intent, int id, int flags) {
        super.onStartCommand(intent, id, flags);

        // Handle the intent action
        switch (intent.getAction()) {
            case startAction:
                // Continue as normal
                break;
            case stopAction:
                // Stop the service
                stopSelf();
                return START_NOT_STICKY;
            default:
                throw BuildConfig.DEBUG ? new DebugException("Unrecognized intent: " +
                        intent.getAction()) : new DefaultException();
        }

        // Make a MediaSession for sending media controls to other apps
        final String mediaTag = BuildConfig.DEBUG ? "playbackSession" : "";
        mediaSession = new MediaSessionCompat(this, mediaTag);

        // Retrieve the sound data
        final float[] sound = intent.getFloatArrayExtra(soundTag);

        // Play the sound
        driver.play(this.getApplicationContext(), sound);

        // Create a notification channel, for android O+ devices
        final String channelId = "playback";
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationManager mgr = (NotificationManager) getSystemService(NOTIFICATION_SERVICE);
            if (mgr.getNotificationChannel(channelId) == null) {
                mgr.createNotificationChannel(new NotificationChannel(channelId,
                        "Playback", NotificationManager.IMPORTANCE_DEFAULT));
            }
        }

        // Create a "playing" notification
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this,
                channelId);

        builder
                // Add the metadata for the currently playing track
                .setContentTitle("Playing sound")
                .setContentText("Tap to open")

                // Enable launching the player by clicking the notification
                .setContentIntent(
                        PendingIntent.getActivity(
                                this,
                                0,
                                new Intent(this, MainActivity.class),
                                PendingIntent.FLAG_UPDATE_CURRENT)
                )

                // Stop the service when the notification is swiped away
                .setDeleteIntent(
                        PendingIntent.getService(this,
                                0,
                                getStopIntent(this),
                                PendingIntent.FLAG_UPDATE_CURRENT)
                )

                // Make the transport controls visible on the lockscreen
                .setVisibility(NotificationCompat.VISIBILITY_PUBLIC)

                // Add an app icon and set its accent color
                // Be careful about the color
                .setSmallIcon(R.drawable.ic_notification_icon)
                .setColor(ContextCompat.getColor(this, R.color.colorPrimaryDark))
                .setPriority(NotificationCompat.PRIORITY_DEFAULT);

                /*
                //TODO
                // Add a pause button
                .addAction(new NotificationCompat.Action(
                        R.drawable.pause, getString(R.string.pause),
                        MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                PlaybackStateCompat.ACTION_PLAY_PAUSE)))

                */


                        /*
                         * TODO
                        // Add a cancel button
                        .setShowCancelButton(true)
                        .setCancelButtonIntent(MediaButtonReceiver.buildMediaButtonPendingIntent(context,
                                PlaybackStateCompat.ACTION_STOP)));
                         */

        // Take advantage of MediaStyle features
        builder.setStyle(new androidx.media.app.NotificationCompat.MediaStyle()
                .setMediaSession(mediaSession.getSessionToken()));
        //TODO: This was in the example code, but you need actions to use it
         //       .setShowActionsInCompactView(0)); // TODO refers to the pause button

        // Display the notification and place the service in the foreground
        startForeground(playbackNotificationId, builder.build());

        // Return a flag specifying that this should be restarted as-is if it's killed
        return START_REDELIVER_INTENT;
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        // Stop the sound
        driver.pause();
    }

}
