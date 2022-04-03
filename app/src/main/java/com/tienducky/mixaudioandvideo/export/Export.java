package com.tienducky.mixaudioandvideo.export;

import com.tienducky.mixaudioandvideo.utils.ToastUtils;

import static com.tienducky.mixaudioandvideo.utils.AppConstants.DEBUG_TAG;

import android.app.Activity;
import android.os.Environment;

import java.io.File;

public class Export {
    private static Export _instance = new Export();
    private ExportService mExportService;

    public static Export getInstance() {
        return _instance;
    }

    /***
     * start export video
     *
     * @param exportElement
     * @param exportAdapter
     */
    public void startExport(Activity activity, ExportElement exportElement, ExportAdapter exportAdapter){
        File outputFile = createOutputFile();
        if(outputFile == null){
            ToastUtils.showShortToast(activity, "Can't export video.");
        } else {
            mExportService = new ExportService();
            mExportService.startExport(outputFile, exportElement, exportAdapter);
        }
    }

    private File createOutputFile(){
        String exportPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "export_output.mp4";
        File outputFile = new File(exportPath);

        return outputFile;
    }

    public void stopExport() {
        if(mExportService == null)
            return;

        mExportService.stopExport();
    }

    public boolean isExportRunning() {
        if(mExportService == null)
            return false;

        return mExportService.isExportRunning();
    }
}
