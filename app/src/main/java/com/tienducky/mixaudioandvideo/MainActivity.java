package com.tienducky.mixaudioandvideo;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.util.Log;
import android.view.View;

import com.tienducky.mixaudioandvideo.databinding.ActivityMainBinding;
import com.tienducky.mixaudioandvideo.export.Export;
import com.tienducky.mixaudioandvideo.export.ExportAdapter;
import com.tienducky.mixaudioandvideo.export.ExportElement;
import com.tienducky.mixaudioandvideo.models.MediaItem;
import com.tienducky.mixaudioandvideo.picker.PickerActivity;
import com.tienducky.mixaudioandvideo.utils.AppConstants;
import com.tienducky.mixaudioandvideo.utils.ToastUtils;

import static com.tienducky.mixaudioandvideo.utils.AppConstants.DEBUG_TAG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = DEBUG_TAG + "MainActivity";

    private ActivityMainBinding mViewBinding;
    private MediaItem mVideoItem;
    private MediaItem mAudioItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mViewBinding = ActivityMainBinding.inflate(getLayoutInflater());
        setContentView(mViewBinding.getRoot());
        setListeners();
    }

    private void setListeners() {
        mViewBinding.btnSelectVideoFile.setOnClickListener(this);
        mViewBinding.btnSelectAudioFile.setOnClickListener(this);
        mViewBinding.btnExportVideo.setOnClickListener(this);
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onClick(View view) {
        switch (view.getId()) {
            case R.id.btn_select_video_file:
                doSelectVideoFile();
                break;
            case R.id.btn_select_audio_file:
                doSelectedAudioFile();
                break;
            case R.id.btn_export_video:
                doExportVideo();
                break;
        }
    }

    ActivityResultLauncher<Intent> launchActivityForVideoResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::processVideoSelected);

    ActivityResultLauncher<Intent> launchActivityForAudioResult = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), this::processAudioSelected);

    private void processVideoSelected(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent intentData = result.getData();
            if (intentData != null) {
                MediaItem videoItem = (MediaItem) intentData.getSerializableExtra(AppConstants.SELECTED_MEDIA_ITEM);
                mVideoItem = videoItem;
                mViewBinding.tvVideoInfo.setText(videoItem.getFileName());
            }

        }
    }

    private void processAudioSelected(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent intentData = result.getData();
            if (intentData != null) {
                if (intentData != null) {
                    MediaItem audioItem = (MediaItem) intentData.getSerializableExtra(AppConstants.SELECTED_MEDIA_ITEM);
                    mViewBinding.tvAudioInfo.setText(audioItem.getFileName());
                    mAudioItem = audioItem;
                }
            }

        }
    }

    private void doSelectVideoFile() {
        Intent intent = new Intent(getApplicationContext(), PickerActivity.class);
        intent.putExtra(AppConstants.FILTER_TYPE, AppConstants.FILTER_VIDEO_FILE);
        launchActivityForVideoResult.launch(intent);
    }

    private void doSelectedAudioFile() {
        Intent intent = new Intent(getApplicationContext(), PickerActivity.class);
        intent.putExtra(AppConstants.FILTER_TYPE, AppConstants.FILTER_AUDIO_FILE);
        launchActivityForAudioResult.launch(intent);
    }

    private void doExportVideo() {
        if (mVideoItem == null || mAudioItem == null) {
            ToastUtils.showShortToast(getApplicationContext(), "Can't export video.");
            return;
        }

        prepareUIForExport(false, true, 0);

        ExportElement exportParams = new ExportElement();
        exportParams.setVideoFilePath(mVideoItem.getFilePath());
        exportParams.setAudioFilePath(mAudioItem.getFilePath());

        Export.getInstance().startExport(this, exportParams, new ExportAdapter() {
            @Override
            public void onExportComplete() {
                Log.d(TAG, "Export complete.");
                prepareUIForExport(true, false, 0);
                ToastUtils.showShortToast(getApplicationContext(), "Export complete.");
            }

            @Override
            public void onExportFail() {
                Log.d(TAG, "Export error.");
                prepareUIForExport(true, false, 0);
            }

            @Override
            public void onExportProgressUpdate(int progress) {
                updateExportProgress(progress);
            }
        });
    }

    private void prepareUIForExport(boolean enableExportButton, boolean enableExportProgress, int progress) {
        enableExportButton(enableExportButton);
        mViewBinding.progressExport.setVisibility(enableExportProgress ? View.VISIBLE : View.GONE);
        mViewBinding.progressExport.setProgress(progress);
        mViewBinding.tvExportProgress.setVisibility(enableExportProgress ? View.VISIBLE : View.GONE);
        mViewBinding.tvExportProgress.setText(progress + "%");
    }

    private void updateExportProgress(int progress) {
        mViewBinding.progressExport.setProgress(progress);
        mViewBinding.tvExportProgress.setText(progress + "%");
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        if (Export.getInstance().isExportRunning()) {
            Export.getInstance().stopExport();
        }
    }

    private void enableExportButton(boolean enable) {
        mViewBinding.btnExportVideo.setEnabled(enable);
        mViewBinding.btnExportVideo.setClickable(enable);
    }
}