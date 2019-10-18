package com.bbrister.metrodrone;

import java.io.Serializable;

public abstract class DownloadDialogListener implements Serializable {
    abstract void onStartDownload();
}
