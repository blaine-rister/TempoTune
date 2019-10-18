package com.bbrister.metrodrone;

import android.content.Context;
import android.os.Bundle;
import android.os.Handler;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.FragmentManager;

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
        void onInstallFinished(boolean success);
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
        this.moduleName = moduleName;
        this.displayName = displayName;
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

    /**
     *  Download and install the module corresponding to this soundfont. Pass null fragmentManager
     *  for quiet mode.
     */
    private void install(final Context context, final FragmentManager fragmentManager) {

        //TODO refactor this into an InstallSession class, whence quiet is a member variable
        final boolean quiet = fragmentManager == null;

        // Get the install manager
        SplitInstallManager splitInstallManager = SplitInstallManagerFactory.create(context);

        // Create a listener for request status updates
        SplitInstallStateUpdatedListener listener = new SplitInstallStateUpdatedListener() {
            @Override
            public void onStateUpdate(SplitInstallSessionState state) {
                if (state.sessionId() == installSessionId) {
                    handleInstallStatus(context, state, quiet);
                }
            }
        };
        splitInstallManager.registerListener(listener);

        // Create an install request
        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(moduleName).build();

        // Notify the user that we're starting the download
        if (!quiet) {
            installProgressMsg(String.format("Initiating download of module %s...", displayName),
                    context);
        }

        // Start the installation
        splitInstallManager.startInstall(request)
                .addOnSuccessListener(
                        new OnSuccessListener<Integer>() {
                            @Override
                            public void onSuccess(Integer sessionId) {
                                // Record the session ID for progress monitoring
                                installSessionId = sessionId;
                                downloadStatus = DownloadStatus.ACTIVE;
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                               handleInstallFailure(context, fragmentManager, e);
                        }
                });
    }

    // Convert bytes to MB
    private final static long bytesPerMb = (long) Math.pow(2, 20);
    private int byte2mb(final long bytes) {
        return (int) (bytes / bytesPerMb);
    }

    // Handle installation status updates
    private void handleInstallStatus(Context context, SplitInstallSessionState state,
                                     final boolean quiet) {
        switch (state.status()) {
            case SplitInstallSessionStatus.DOWNLOADING:
                // Report download progress
                downloadSize = state.totalBytesToDownload();
                bytesDownloaded = state.bytesDownloaded();
                if (!quiet) {
                    updateToast(context, String.format("Downloading module %s (%d / %d MB)",
                            displayName, byte2mb(bytesDownloaded), byte2mb(downloadSize)));
                }
                return;
            case SplitInstallSessionStatus.DOWNLOADED:
                // Signal that the download is finished
                downloadStatus = DownloadStatus.COMPLETED;
                if (!quiet) {
                    updateToast(context, "Finished downloading module " + displayName);
                }
                return;
            case SplitInstallSessionStatus.INSTALLING:
                // Signal that the download is finished and installation has begun
                downloadStatus = DownloadStatus.COMPLETED;
                if (!quiet) {
                    updateToast(context, "Installing module " + displayName);
                }
                return;
            case SplitInstallSessionStatus.INSTALLED:
                // Signal that both the download and installation are finished
                downloadStatus = DownloadStatus.COMPLETED;
                installStatus = InstallStatus.INSTALLED;
                bytesDownloaded = downloadSize;
                installFinished(true);
                if (!quiet) {
                    updateToast(context, "Finished installing module " + displayName);
                }
                return;
        }
    }

    // Show a toast updating the user on installation status
    public static void updateToast(Context context, String msg) {
        Toast.makeText(context, msg, Toast.LENGTH_SHORT).show();
    }

    // Handle installation failure
    private void handleInstallFailure(final Context context, FragmentManager fragmentManager,
                                      Exception e) {

        // Check for quiet mode
        final boolean quiet = fragmentManager == null;

        // If this is not a SplitInstallException, just print the message
        if (!(e instanceof SplitInstallException)) {
            if (BuildConfig.DEBUG_EXCEPTIONS) {
                throw new RuntimeException(e);
            }
            installFailureMsg(e.getLocalizedMessage(), context, fragmentManager);
        }

        // Handle the SplitInstallException
        switch (((SplitInstallException) e).getErrorCode()) {
            case SplitInstallErrorCode.API_NOT_AVAILABLE:

                //TODO notfiy the user that the play API is not available on
                // this device
                //TODO merge this with R.string.download_api_level_fail
                installFailureMsg("We're sorry, but downloading new " +
                        "features is not supported on this " +
                        "device. Please upgrade to at least " +
                        "Android version 5.0", context, fragmentManager);
                //TODO: provide this help link here: https://support.google.com/googleplay/answer/7513003
                break;
            case SplitInstallErrorCode.NETWORK_ERROR:
                // Display a message that requests the user to establish a
                // network connection.
                installFailureMsg("Failed to connect to the Google Play " +
                        "server. Please establish a network " +
                        "connection.", context, fragmentManager);
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
                if (!quiet) {
                    installProgressMsg(String.format("Download rejected " +
                            "by the server. Trying again in %d " +
                            "seconds...", retryInterval), context);
                }
                downloadStatus = DownloadStatus.PENDING;
                delayedRestartInstall(context, fragmentManager);
                return; // Do not break--we are not finished yet
            case SplitInstallErrorCode.INSUFFICIENT_STORAGE:
                installFailureMsg("Insufficient storage. Please "+
                        "free up space on your device.", context, fragmentManager);
                break;
            case SplitInstallErrorCode.MODULE_UNAVAILABLE:
            case SplitInstallErrorCode.SESSION_NOT_FOUND:
            case SplitInstallErrorCode.INVALID_REQUEST:
                if (BuildConfig.DEBUG_EXCEPTIONS) {
                    /* Only throw an exception in debug mode. This is not
                     * fatal for the app overall. */
                    throw new DebugException("Fatal programming error " +
                            "downloading soundfont module");
                }
            default:
                if (BuildConfig.DEBUG_EXCEPTIONS) {
                    /* Only throw an exception in debug mode. This is not
                     * fatal for the app overall. */
                    throw new DebugException("Unrecognized error " +
                            "downloading soundfont module");
                }
                installFailureMsg("We're sorry, but there " +
                                "was an unanticipated error. Please contact the " +
                                "app developers to troubleshoot this issue.",
                        context, fragmentManager);
                break;
        }

        installFinished(false);
    }

    // Retry the installation after a set waiting period
    private void delayedRestartInstall(final Context context,
                                       final FragmentManager fragmentManager) {
        updateToast(context, String.format("Restarting download of module %s...", displayName));
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                install(context, fragmentManager);
            }
        }, retryInterval * 1000);
    }

    // Display a message showing the progress of the ongoing installation
    private void installProgressMsg(String msg, Context context) {
        Toast.makeText(context, msg, Toast.LENGTH_LONG).show();
    }

    /**
     * Display a message showing that installation was not able to succeed. Pass fragmentManager as
     * null to disable.
     */
    private void installFailureMsg(String msg, Context context, FragmentManager fragmentManager) {
        // Check for quiet mode
        if (fragmentManager == null)
            return;

        // Add some decoration text.
        msg = String.format("Installation failed for module %s.\n\nReason:\n%s\n\nPlease " +
                "see https://support.google.com/googleplay/answer/7513003 for troubleshooting " +
                "advice.", displayName, msg);

        // Display an alert dialog
        AlertDialogFragment.showDialog(fragmentManager, msg);
    }

    /**
     * Called to finish installation. Makes a callback if one is registered.
     */
    private void installFinished(final boolean success) {
        installStatus = success ? InstallStatus.DOWNLOADED : InstallStatus.FAILED;
        downloadStatus = success ? DownloadStatus.COMPLETED : DownloadStatus.FAILED;
        if (haveInstallListener()) {
            installListener.onInstallFinished(success);
            removeInstallListener();
        }
    }

    /**
     * Install in "quiet" mode. Does not report any text. Use this for already-installed modules.
     */
    public void installQuiet(final Context context) {
        install(context, null);
    }

    /**
     * Ask the user whether they wish to install this module. If yes, begins installation.
     */
    public void promptInstallation(final Context context, final FragmentManager fragmentManager) {

        // Create the callback interface
        DownloadDialogListener listener = new DownloadDialogListener() {
            @Override
            public void onStartDownload() {
                install(context, fragmentManager);
            }
        };

        // Create the argument bundle
        Bundle arguments = new Bundle();
        arguments.putString(DownloadDialogFragment.moduleNameKey, displayName);
        arguments.putSerializable(DownloadDialogFragment.downloadListenerKey, listener);

        // Create the dialog and add its arguments
        DownloadDialogFragment dialog = new DownloadDialogFragment();
        dialog.setArguments(arguments);

        // Show the dialog
        dialog.show(fragmentManager, FragmentTags.downloadDialogTag);
    }

    // Set callbacks for installation--for internal use
    public void setInstallListener(InstallListener listener) {
        this.installListener = listener;
    }

    // Check if a callback is registered
    public boolean haveInstallListener() {
        return installListener != null;
    }

    // Remove the callback, in case it oddly gets called multiple times
    public void removeInstallListener() {
        this.installListener = null;
    }
}
