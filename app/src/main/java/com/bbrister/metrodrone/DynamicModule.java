package com.bbrister.metrodrone;

import android.content.Context;
import android.os.Handler;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;

import com.google.android.play.core.splitinstall.SplitInstallException;
import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;
import com.google.android.play.core.splitinstall.SplitInstallRequest;
import com.google.android.play.core.splitinstall.SplitInstallSessionState;
import com.google.android.play.core.splitinstall.SplitInstallStateUpdatedListener;
import com.google.android.play.core.splitinstall.model.SplitInstallErrorCode;
import com.google.android.play.core.splitinstall.model.SplitInstallSessionStatus;
import com.google.android.play.core.tasks.OnFailureListener;
import com.google.android.play.core.tasks.OnSuccessListener;

import java.util.Set;

public class DynamicModule {

    // Interfaces
    interface InstallListener {
        boolean onInstallFinished(boolean success);
    };
    private InstallListener installListener;

    // Constants
    final int retryInterval = 15; // Number of seconds to wait before restarting the download

    // Data
    protected String displayName;
    protected String moduleName;
    protected boolean isFree;

    // Install state
    public enum InstallStatus {
        INSTALLED,
        DOWNLOADED,
        PENDING,
        FAILED,
        NOT_REQUESTED
    }
    InstallStatus installStatus;

    // Download state
    public enum DownloadStatus {
        ACTIVE,
        PENDING,
        COMPLETED,
        FAILED,
        NOT_REQUESTED
    };
    DownloadStatus downloadStatus;
    long bytesDownloaded;
    long downloadSize;
    int installSessionId;

    public DynamicModule(Context context, String moduleName, String displayName, boolean isFree) {
        // Basic initialization
        installListener = null;
        moduleName = moduleName;
        displayName = displayName;
        this.isFree = isFree;
        downloadStatus = DownloadStatus.NOT_REQUESTED;
        bytesDownloaded = 0;
        downloadSize = 0;

        // Query the installed modules to see if this is already installed
        SplitInstallManager splitInstallManager = SplitInstallManagerFactory.create(context);
        Set<String> installedModules = splitInstallManager.getInstalledModules();
        installStatus = installedModules.contains(moduleName) ?
                InstallStatus.INSTALLED : InstallStatus.NOT_REQUESTED;
    }

    // Check if this module requires further payment for activation
    public boolean requiresPayment() {
        if (isFree)
            return true;

        // TODO: try to query the billing API.
        return false;
    }

    // Download and install the module corresponding to this soundfont
    public void install(final Context context) throws ApiLevelException {

        // Check if it's already installed or installing
        switch (installStatus) {
            case INSTALLED:
            case PENDING:
                return;
        }

        // Check the API level, raise an exception if it's not met
        final int thisApiLevel = android.os.Build.VERSION.SDK_INT;
        final int minApiLevel = 21;
        if (thisApiLevel <= minApiLevel) {
            throw new ApiLevelException();
        }

        // Get the install manager
        SplitInstallManager splitInstallManager = SplitInstallManagerFactory.create(context);

        // Create a listener for request status updates
        SplitInstallStateUpdatedListener listener = new SplitInstallStateUpdatedListener() {
            @Override
            public void onStateUpdate(SplitInstallSessionState state) {
                if (state.sessionId() == installSessionId) {
                    handleInstallStatus(context, state);
                }
            }
        };
        splitInstallManager.registerListener(listener);

        // Create an install request
        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(moduleName).build();

        // Notify the user that we're starting the download
        installProgressMsg(String.format("Initiating download of module %s...", displayName),
                context);

        // Start the installation
        splitInstallManager.startInstall(request)
                .addOnSuccessListener(
                        new OnSuccessListener<Integer>() {
                            @Override
                            public void onSuccess(Integer sessionId) {
                                // Record the session ID for progress monitoring
                                installSessionId = sessionId;
                                downloadStatus = DownloadStatus.ACTIVE;
                                //TODO launch the "Downloads" activity
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                               handleInstallFailure(context, e);
                        }
                });
    }

    // Handle installation status updates
    private void handleInstallStatus(Context context, SplitInstallSessionState state) {
        switch (state.status()) {
            case SplitInstallSessionStatus.DOWNLOADING:
                // Signal that the download has started
                updateToast(context, "Started downloading module " + displayName);
                downloadSize = state.totalBytesToDownload();
                bytesDownloaded = state.bytesDownloaded();
                return;
            case SplitInstallSessionStatus.DOWNLOADED:
            case SplitInstallSessionStatus.INSTALLING:
            case SplitInstallSessionStatus.INSTALLED:

                // Signal that the download is finished
                downloadStatus = DownloadStatus.COMPLETED;
                bytesDownloaded = downloadSize;
                updateToast(context, "Finished downloading module " + displayName);

                // Display an alert dialog telling the user to restart the app
                AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
                alertDialogBuilder.setTitle("Installation failed");
                alertDialogBuilder.setMessage(String.format("Finished installing module %s. " +
                        "Please restart the app to use these new sounds."));
                alertDialogBuilder.create().show();
                return;
        }
    }

    // Show a toast updating the user on installation status
    public static void updateToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    // Handle installation failure
    private void handleInstallFailure(final Context context, Exception e) {
        switch (((SplitInstallException) e).getErrorCode()) {
            case SplitInstallErrorCode.API_NOT_AVAILABLE:

                //TODO notfiy the user that the play API is not available on
                // this device
                installFailureMsg("We're sorry, but downloading new " +
                        "features is not supported on this " +
                        "device. Please upgrade to at least " +
                        "Android version 5.0", context);
                //TODO: provide this help link here: https://support.google.com/googleplay/answer/7513003
                break;
            case SplitInstallErrorCode.NETWORK_ERROR:
                // Display a message that requests the user to establish a
                // network connection.
                installFailureMsg("Failed to connect to the Google Play " +
                        "server. Please establish a network " +
                        "connection.", context);
                //TODO notify the user to establish an internet connection
                //TODO: provide this help link here: https://support.google.com/googleplay/answer/7513003
                break;
            case SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED:
            case SplitInstallErrorCode.SERVICE_DIED:
            case SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION:
            case SplitInstallErrorCode.ACCESS_DENIED:
                //TODO notify the user that we will try again. Retry after a set
                // number of seconds. Something like "Download rejected. Trying again in 5 seconds..."
                // probably in a snackbar.
                installProgressMsg(String.format("Download rejected " +
                        "by the server. Trying again in %d " +
                        "seconds...", retryInterval), context);
                downloadStatus = DownloadStatus.PENDING;
                delayedRestartInstall(context);
                return; // Do not break--we are not finished yet
            case SplitInstallErrorCode.INSUFFICIENT_STORAGE:
                installFailureMsg("Insufficient storage. Please "+
                        "free up space on your device.", context);
                break;
            case SplitInstallErrorCode.MODULE_UNAVAILABLE:
            case SplitInstallErrorCode.SESSION_NOT_FOUND:
            case SplitInstallErrorCode.INVALID_REQUEST:
                if (BuildConfig.DEBUG) {
                    /* Only throw an exception in debug mode. This is not
                     * fatal for the app overall. */
                    throw new DebugException("Fatal programming error " +
                            "downloading soundfont module");
                }
            default:
                if (BuildConfig.DEBUG) {
                    /* Only throw an exception in debug mode. This is not
                     * fatal for the app overall. */
                    throw new DebugException("Unrecognized error " +
                            "downloading soundfont module");
                }
                installFailureMsg("We're sorry, but there " +
                                "was an unanticipated error. Please contact the " +
                                "app developers to troubleshoot this issue.",
                        context);
                break;
        }

        installFinished(false);
    }

    // Retry the installation after a set waiting period
    private void delayedRestartInstall(final Context context) {
        updateToast(context, String.format("Restarting download of module %s...", displayName));
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                try {
                    install(context);
                } catch (ApiLevelException e) {
                    // Do nothing...this should be handled on the first try
                }
            }
        }, retryInterval * 1000);
    }

    // Display a message showing the progress of the ongoing installation
    private void installProgressMsg(String msg, Context context) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    // Display a message showing that installation was not able to succeed
    private void installFailureMsg(String msg, Context context) {
        // Add some decoration text.
        msg = String.format("Installation failed for sound package %s.Reason:\n%s\n\nPlease " +
                "see https://support.google.com/googleplay/answer/7513003 for troubleshooting " +
                "advice.", displayName, msg);

        // Display an alert dialog
        AlertDialog.Builder alertDialogBuilder = new AlertDialog.Builder(context);
        alertDialogBuilder.setTitle("Installation failed");
        alertDialogBuilder.setMessage(msg);
        alertDialogBuilder.create().show();
    }

    private void installFinished(final boolean success) {
        installStatus = success ? InstallStatus.DOWNLOADED : InstallStatus.FAILED;
        downloadStatus = success ? DownloadStatus.COMPLETED : DownloadStatus.FAILED;
        if (haveInstallListener())
            installListener.onInstallFinished(success);
    }

    public boolean haveInstallListener() {
        return installListener == null;
    }

    // Set callbacks for installation
    //TODO probably don't actually need this--just refuse to switch to the soundset until it's installed
    public void setInstallListener(InstallListener listener) {
        this.installListener = listener;
    }
}
