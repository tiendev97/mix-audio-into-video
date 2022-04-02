package com.tienducky.mixaudioandvideo;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.tienducky.mixaudioandvideo.export.Export;
import com.tienducky.mixaudioandvideo.export.ExportAdapter;
import com.tienducky.mixaudioandvideo.export.ExportElement;
import com.tienducky.mixaudioandvideo.models.MediaItem;
import com.tienducky.mixaudioandvideo.picker.PickerActivity;
import com.tienducky.mixaudioandvideo.utils.AppConstants;
import com.tienducky.mixaudioandvideo.utils.FileUtils;
import com.tienducky.mixaudioandvideo.utils.ToastUtils;

import static com.tienducky.mixaudioandvideo.utils.AppConstants.DEBUG_TAG;

public class MainActivity extends AppCompatActivity implements View.OnClickListener {

    private static final String TAG = DEBUG_TAG + "MainActivity";

    private Button mSelectVideoFileBtn;
    private Button mSelectAudioFileBtn;
    private Button mExportBtn;

    private TextView mVideoInfoTV;
    private TextView mAudioInfoTv;
    private MediaItem mVideoItem;
    private MediaItem mAudioItem;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        initViews();
    }

    private void initViews() {
        // find views
        mSelectVideoFileBtn = findViewById(R.id.btn_select_video_file);
        mSelectAudioFileBtn = findViewById(R.id.btn_select_audio_file);
        mExportBtn = findViewById(R.id.btn_export_video);
        mVideoInfoTV = findViewById(R.id.tv_video_info);
        mAudioInfoTv = findViewById(R.id.tv_audio_info);

        // add listeners
        mSelectVideoFileBtn.setOnClickListener(this);
        mSelectAudioFileBtn.setOnClickListener(this);
        mExportBtn.setOnClickListener(this);
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
                mVideoInfoTV.setText(videoItem.getFileName());
            }

        }
    }

    private void processAudioSelected(ActivityResult result) {
        if (result.getResultCode() == Activity.RESULT_OK) {
            Intent intentData = result.getData();
            if (intentData != null) {
                if (intentData != null) {
                    MediaItem audioItem = (MediaItem) intentData.getSerializableExtra(AppConstants.SELECTED_MEDIA_ITEM);
                    mAudioInfoTv.setText(audioItem.getFileName());
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

        enableExportButton(false);

        ExportElement exportParams = new ExportElement();
        exportParams.setVideoFilePath(mVideoItem.getFilePath());
        exportParams.setAudioFilePath(mAudioItem.getFilePath());

        Export.getInstance().startExport(this, exportParams, new ExportAdapter() {
            @Override
            public void onExportComplete() {
                Log.d(TAG, "Export complete.");
                enableExportButton(true);
                ToastUtils.showShortToast(getApplicationContext(), "Export complete.");
            }

            @Override
            public void onExportError() {
                Log.d(TAG, "Export error.");
                enableExportButton(true);
            }
        });
    }

    private void enableExportButton(boolean enable) {
        mExportBtn.setEnabled(enable);
        mExportBtn.setClickable(enable);
    }
}