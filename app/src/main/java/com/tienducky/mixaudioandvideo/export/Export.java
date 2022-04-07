package com.tienducky.mixaudioandvideo.export;

import com.tienducky.mixaudioandvideo.utils.ToastUtils;

import static com.tienducky.mixaudioandvideo.utils.AppConstants.DEBUG_TAG;

import android.app.Activity;
import android.os.Environment;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.TimeUnit;

public class Export {
    private static final String ROOT_FOLDER = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator;
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
            mExportService.startExport(activity, outputFile, exportElement, exportAdapter);
        }
    }

    private File createOutputFile(){
        Date date = new Date();
        SimpleDateFormat dateFormat = new SimpleDateFormat("yyyyMMdd");
        String fileName = dateFormat.format(date) + "_" + TimeUnit.MICROSECONDS.toSeconds(System.currentTimeMillis()) + ".mp4";
        String exportPath = ROOT_FOLDER + fileName;
        File outputFile = new File(exportPath);

        if(outputFile.exists())
            outputFile.delete();

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
