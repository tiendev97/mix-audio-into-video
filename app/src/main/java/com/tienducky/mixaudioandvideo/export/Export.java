package com.tienducky.mixaudioandvideo.export;

import com.techyourchance.threadposter.BackgroundThreadPoster;
import com.tienducky.mixaudioandvideo.utils.ToastUtils;

import static com.tienducky.mixaudioandvideo.utils.AppConstants.DEBUG_TAG;

import android.app.Activity;
import android.os.Environment;
import android.util.Log;

import java.io.File;
import java.io.IOException;

public class Export {
    private static final String TAG = DEBUG_TAG + "Export";
    private static Export _instance = new Export();
    private Activity mActivity;
    private ExportElement mExportParams;
    private File mOutputFile;

    public static Export getInstance() {
        return _instance;
    }

    /***
     * This method is used to mix audio into video
     * @param exportElement
     * @param exportAdapter
     */
    public void startExport(Activity activity, ExportElement exportElement, ExportAdapter exportAdapter){
        File outputFile = createOutputFile();
        if(outputFile == null){
            ToastUtils.showShortToast(activity, "Can't export video.");
        } else {
            mExportParams = exportElement;
            mOutputFile = outputFile;
            ExportService exportService = new ExportService();
            exportService.startExport(outputFile, exportElement, exportAdapter);
        }
    }

    private File createOutputFile(){
        String exportPath = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DCIM) + File.separator + "export_output.mp4";
        File outputFile = new File(exportPath);

        return outputFile;
    }
}
