package com.samsung.mixaudioandvideo.picker;

import androidx.appcompat.app.AppCompatActivity;

import android.app.Activity;
import android.content.Intent;
import android.database.Cursor;
import android.os.Bundle;
import android.provider.MediaStore;

import com.samsung.mixaudioandvideo.databinding.ActivityPickerBinding;
import com.samsung.mixaudioandvideo.models.MediaItem;
import com.samsung.mixaudioandvideo.models.MediaType;
import com.samsung.mixaudioandvideo.utils.AppConstants;

import java.io.File;
import java.util.ArrayList;

public class PickerActivity extends AppCompatActivity {
    private static final String TAG = "PickerActivity";
    private ActivityPickerBinding mBinding;
    private ArrayList<MediaItem> mMediaItems;
    private PickerRecyclerViewAdapter mAdapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        mBinding = ActivityPickerBinding.inflate(getLayoutInflater());
        setContentView(mBinding.getRoot());
        initViews();
    }

    private void initViews() {
        mMediaItems = isVideoPicker() ? getVideoFilesFromExternalStorage() : getAudioFilesFromExternalStorage();
        mAdapter = new PickerRecyclerViewAdapter(mMediaItems, this::onMediaItemSelected);
        mBinding.recyclerView.setAdapter(mAdapter);
        mAdapter.notifyDataSetChanged();
    }

    private void onMediaItemSelected(MediaItem mediaItem){
        Intent intent = new Intent();
        intent.putExtra(AppConstants.SELECTED_MEDIA_ITEM, mediaItem);
        setResult(RESULT_OK);
        setResult(Activity.RESULT_OK, intent);
        finish();
    }

    private boolean isVideoPicker(){
        Intent intent = getIntent();
        String filterType = intent.getStringExtra(AppConstants.FILTER_TYPE);
        if(filterType != null && filterType.equalsIgnoreCase(AppConstants.FILTER_VIDEO_FILE)){
            return true;
        }

        return false;
    }

    public ArrayList<MediaItem> getVideoFilesFromExternalStorage() {
        ArrayList<MediaItem> videoItems = new ArrayList<>();
        String[] projection = {
                MediaStore.Video.VideoColumns.DATA, MediaStore.Video.Media.DISPLAY_NAME
        };
        Cursor cursor = getContentResolver().query(
                MediaStore.Video.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null);

        try {
            cursor.moveToFirst();
            do {
                String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));

                if(!isValidFilePath(filePath))
                    continue;

                MediaItem mediaItem = new MediaItem();
                mediaItem.setFilePath(filePath);
                mediaItem.setFileName(extractFileName(displayName));
                mediaItem.setMediaType(MediaType.VIDEO);
                videoItems.add(mediaItem);
            } while (cursor.moveToNext());

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return videoItems;
    }

    private boolean isValidFilePath(String filePath) {
        return new File(filePath).exists();
    }

    public ArrayList<MediaItem> getAudioFilesFromExternalStorage() {
        ArrayList<MediaItem> videoItems = new ArrayList<>();
        String[] projection = {
                MediaStore.Audio.AudioColumns.DATA, MediaStore.Audio.Media.DISPLAY_NAME
        };
        Cursor cursor = getContentResolver().query(
                MediaStore.Audio.Media.EXTERNAL_CONTENT_URI,
                projection, null, null, null);

        try {
            cursor.moveToFirst();
            do {
                String filePath = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));
                String displayName = cursor.getString(cursor.getColumnIndexOrThrow(MediaStore.Video.Media.DATA));

                if(!isValidFilePath(filePath) || !isAudioSupport(filePath)) {
                    continue;
                }

                MediaItem mediaItem = new MediaItem();
                mediaItem.setFilePath(filePath);
                mediaItem.setFileName(extractFileName(displayName));
                mediaItem.setMediaType(MediaType.AUDIO);
                videoItems.add(mediaItem);
            } while (cursor.moveToNext());

            cursor.close();
        } catch (Exception e) {
            e.printStackTrace();
        }

        return videoItems;
    }

    private String extractFileName(String filePath){
        return filePath.substring(filePath.lastIndexOf("/") + 1);
    }

    private boolean isAudioSupport(String filePath) {
        String fileExtension = filePath.substring(filePath.lastIndexOf("."));
        return fileExtension.equalsIgnoreCase(".mp3") || fileExtension.equalsIgnoreCase(".ogg");
    }
}