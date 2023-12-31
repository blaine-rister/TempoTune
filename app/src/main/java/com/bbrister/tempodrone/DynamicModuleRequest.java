package com.bbrister.tempodrone;

import android.content.Context;
import android.content.IntentSender;
import android.os.Handler;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

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

public abstract class DynamicModuleRequest {

    // Constants
    final private static int retryInterval = 15; // Number of seconds to wait before restarting the download

    // Static data
    private static int confirmationRequestCode = 0; // Unique to each confirmation request in the app

    // Configuration
    boolean quiet;
    protected String displayName;
    protected String moduleName;

    // Calling activity
    AppCompatActivity activity;

    // Manager
    SplitInstallManager splitInstallManager;

    // Session info
    int installSessionId;

    // Callbacks for status updates
    abstract void finished(boolean success);

    public DynamicModuleRequest(final AppCompatActivity activity, final String moduleName,
                                final String displayName, final boolean quiet) {

        // Initialize the configuration
        this.activity = activity;
        this.moduleName = moduleName;
        this.displayName = displayName;
        this.quiet = quiet;

        // Create the manager
        splitInstallManager = SplitInstallManagerFactory.create(activity);

        // Create a listener for request status updates
        SplitInstallStateUpdatedListener listener = new SplitInstallStateUpdatedListener() {
            @Override
            public void onStateUpdate(SplitInstallSessionState state) {
                if (state.sessionId() == installSessionId) {
                    handleInstallStatus(state, quiet);
                }
            }
        };
        splitInstallManager.registerListener(listener);

        startInstallation();
    }

    /**
     * Shortcut to query a string resource.
     */
    private String getString(final int resId) {
        return activity.getString(resId);
    }

    /**
     * Create the request and start installation. Refactored to be able to restart.
     */
    private void startInstallation() {

        // Create an install request
        SplitInstallRequest request = SplitInstallRequest.newBuilder()
                .addModule(moduleName).build();

        // Notify the user that we're starting the download
        if (!quiet) {
            installProgressMsg(String.format(getString(R.string.download_initiating), displayName));
        }

        // Start the installation
        splitInstallManager.startInstall(request)
                .addOnSuccessListener(
                        new OnSuccessListener<Integer>() {
                            @Override
                            public void onSuccess(Integer sessionId) {
                                // Record the session ID for progress monitoring
                                installSessionId = sessionId;
                            }
                        })
                .addOnFailureListener(
                        new OnFailureListener() {
                            @Override
                            public void onFailure(Exception e) {
                                handleInstallFailure(e);
                            }
                        });
    }

    // Handle installation status updates
    private void handleInstallStatus(SplitInstallSessionState state,
                                     final boolean quiet) {
        switch (state.status()) {
            case SplitInstallSessionStatus.PENDING:
                updateToast(String.format(
                        getString(R.string.install_pending),
                        displayName
                ));
                return;
            case SplitInstallSessionStatus.DOWNLOADING:
                // Report download progress
                if (!quiet) {
                    updateToast(String.format(
                                getString(R.string.download_progress),
                                displayName,
                                byte2mb(state.bytesDownloaded()),
                                    byte2mb(state.totalBytesToDownload())
                            )
                    );
                }
                return;
            case SplitInstallSessionStatus.DOWNLOADED:
                // Signal that the download is finished
                if (!quiet) {
                    updateToast(String.format(
                            getString(R.string.download_finished),
                            displayName
                    ));
                }
                return;
            case SplitInstallSessionStatus.INSTALLING:
                // Signal that the download is finished and installation has begun
                if (!quiet) {
                    updateToast(String.format(getString(R.string.install_started), displayName));
                }
                return;
            case SplitInstallSessionStatus.INSTALLED:
                // Signal that both the download and installation are finished
                finished(true);
                if (!quiet) {
                    updateToast(String.format(getString(R.string.install_finished), displayName));
                }
                return;
            case SplitInstallSessionStatus.REQUIRES_USER_CONFIRMATION:
                try {
                    splitInstallManager.startConfirmationDialogForResult(state, activity,
                            confirmationRequestCode++);
                } catch (IntentSender.SendIntentException e) {
                    if (BuildConfig.DEBUG_EXCEPTIONS) {
                        throw new RuntimeException(e);
                    }
                    installFailureMsg(String.format(
                            getString(R.string.install_confirmation_failed),
                            e.getLocalizedMessage()
                    ));
                }
                return;
            case SplitInstallSessionStatus.CANCELING:
                updateToast(String.format(
                        getString(R.string.install_canceling),
                        displayName
                ));
                return;
            case SplitInstallSessionStatus.CANCELED:
                updateToast(String.format(
                        getString(R.string.install_canceled),
                        displayName
                ));
                return;
            case SplitInstallSessionStatus.FAILED:
                updateToast(String.format(
                        getString(R.string.install_failed),
                        displayName
                ));
                return;
            case SplitInstallSessionStatus.UNKNOWN:
                fatalInstallErrorDialog(new Exception(String.format(
                        getString(R.string.install_unknown),
                        displayName
                )));
                return;
            default:
                fatalInstallErrorDialog(new Exception(String.format(
                        getString(R.string.install_status_unrecognized),
                        displayName,
                        state.status()
                )));
        }
    }

    /**
     * Show a dialog apologizing to the user for a fatal exception. Throw the exception in debug
     * mode.
     */
    private void fatalInstallErrorDialog(Exception e) {
        installFailureMsg(String.format(
                getString(R.string.install_fatal_error),
                displayName,
                e.getLocalizedMessage()
        ));
    }

    // Show a toast updating the user on installation status
    private void updateToast(final String msg) {
        Toast.makeText(activity, msg, Toast.LENGTH_SHORT).show();
    }

    // Handle installation failure
    private void handleInstallFailure(Exception e) {

        // If this is not a SplitInstallException, just print the message
        if (!(e instanceof SplitInstallException)) {
            if (BuildConfig.DEBUG_EXCEPTIONS) {
                throw new RuntimeException(e);
            }
            installFailureMsg(e.getLocalizedMessage());
            finished(false);
            return;
        }

        // Handle the SplitInstallException
        switch (((SplitInstallException) e).getErrorCode()) {
            case SplitInstallErrorCode.API_NOT_AVAILABLE:
                // First check if this is due to android version
                final int dynamicModuleVersion = 21;
                if (BuildConfig.VERSION_CODE < dynamicModuleVersion) {
                    installFailureMsg(getString(R.string.download_api_level_fail));
                } else {
                    // Else could be caused by a development build, emulator, etc.
                    installFailureMsg(getString(R.string.download_api_unavailable));
                }
                break;
            case SplitInstallErrorCode.NETWORK_ERROR:
                // Display a message that requests the user to establish a
                // network connection.
                installFailureMsg(getString(R.string.download_network_fail));
                break;
            case SplitInstallErrorCode.ACTIVE_SESSIONS_LIMIT_EXCEEDED:
            case SplitInstallErrorCode.INCOMPATIBLE_WITH_EXISTING_SESSION:
            case SplitInstallErrorCode.ACCESS_DENIED:
                installProgressMsg(
                        String.format(
                            getString(R.string.download_rejected_retrying),
                            retryInterval
                        )
                );
                delayedRestartInstall();
                return; // Do not break--we are not finished yet
            case SplitInstallErrorCode.INSUFFICIENT_STORAGE:
                installFailureMsg(getString(R.string.download_insufficient_storage));
                break;
            case SplitInstallErrorCode.MODULE_UNAVAILABLE:
            case SplitInstallErrorCode.SESSION_NOT_FOUND:
            case SplitInstallErrorCode.INVALID_REQUEST:
            default:
                fatalInstallErrorDialog(e);
                break;
        }

        finished(false);
    }

    // Retry the installation after a set waiting period
    private void delayedRestartInstall() {
        updateToast(String.format(getString(R.string.download_restart), displayName));
        Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                startInstallation();
            }
        }, retryInterval * 1000);
    }

    // Display a message showing the progress of the ongoing installation
    private void installProgressMsg(String msg) {
        Toast.makeText(activity, msg, Toast.LENGTH_LONG).show();
    }

    // Convert bytes to MB
    private final static long bytesPerMb = (long) Math.pow(2, 20);
    private int byte2mb(final long bytes) {
        return (int) (bytes / bytesPerMb);
    }

    /**
     * Display a message showing that installation was not able to succeed. Pass fragmentManager as
     * null to disable.
     */
    private void installFailureMsg(String msg) {

        // Add some decoration text.
        msg = String.format(getString(R.string.install_trouleshooting), displayName, msg);

        // Display an alert dialog
        AlertDialogFragment.showDialog(activity.getSupportFragmentManager(), msg);
    }

}
