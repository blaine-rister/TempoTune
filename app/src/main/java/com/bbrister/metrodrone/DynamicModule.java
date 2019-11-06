package com.bbrister.metrodrone;

import android.os.Bundle;

import androidx.appcompat.app.AppCompatActivity;

import com.google.android.play.core.splitinstall.SplitInstallManager;
import com.google.android.play.core.splitinstall.SplitInstallManagerFactory;

import java.util.Set;

public class DynamicModule {

    // Interfaces
    interface InstallListener {
        void onInstallFinished(boolean success);
    };
    private InstallListener installListener;

    // Calling activity
    AppCompatActivity activity;

    // Data
    protected String displayName;
    protected String moduleName;

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

    public DynamicModule(AppCompatActivity activity, String moduleName, String displayName) {
        // Basic initialization
        installListener = null;
        this.moduleName = moduleName;
        this.displayName = displayName;
        this.activity = activity;
        downloadStatus = DownloadStatus.NOT_REQUESTED;

        // Query the installed modules to see if this is already installed
        SplitInstallManager splitInstallManager = SplitInstallManagerFactory.create(activity);
        Set<String> installedModules = splitInstallManager.getInstalledModules();
        installStatus = installedModules.contains(moduleName) ?
                InstallStatus.INSTALLED : InstallStatus.NOT_REQUESTED;
    }

    /**
     *  Download and install the module.
     */
    public void install(final boolean quiet) {
        new DynamicModuleRequest(activity, moduleName, displayName, quiet) {
            @Override
            void updateInstallStatus(InstallStatus status) {
                installStatus = status;
            }
            @Override
            void updateDownloadStatus(DownloadStatus status) {
                downloadStatus = status;
            }
            @Override
            void finished(boolean success) {
                installFinished(success);
            }
        };
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
    public void installQuiet() {
        install(true);
    }

    /**
     * Ask the user whether they wish to install this module. If yes, begins installation.
     */
    public void promptInstallation() {

        // Create the argument bundle
        Bundle arguments = new Bundle();
        arguments.putString(DownloadDialogFragment.moduleNameKey, moduleName);
        arguments.putString(DownloadDialogFragment.displayNameKey, displayName);

        // Create the dialog and add its arguments
        DownloadDialogFragment dialog = new DownloadDialogFragment();
        dialog.setArguments(arguments);

        // Show the dialog
        dialog.show(activity.getSupportFragmentManager(), FragmentTags.downloadDialogTag);
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
