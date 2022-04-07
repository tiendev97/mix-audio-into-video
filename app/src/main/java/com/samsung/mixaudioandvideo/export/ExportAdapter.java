package com.samsung.mixaudioandvideo.export;

public interface ExportAdapter {
    void onExportComplete();

    void onExportFail();

    void onExportProgressUpdate(int progress);
}
