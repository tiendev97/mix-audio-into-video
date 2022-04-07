package com.samsung.mixaudioandvideo.utils;

import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.util.Log;

import com.samsung.mixaudioandvideo.models.TrackType;

public class MediaUtils {
    private static String TAG = AppConstants.DEBUG_TAG + "MediaUtils";

    public static int getTrackIndex(MediaExtractor extractor, int type) {
        int trackCount = extractor.getTrackCount();
        for(int i = 0; i < trackCount; i++) {
            MediaFormat format = extractor.getTrackFormat(i);
            Log.i(TAG, "type = " + type + ", format=" + format);
            String mime = format.getString(MediaFormat.KEY_MIME);
            if (type == TrackType.AUDIO && mime.startsWith("audio/")) {
                return i;
            }
            if (type == TrackType.VIDEO && mime.startsWith("video/")) {
                return i;
            }
        }
        return TrackType.ERR_NO_TRACK_INDEX;
    }
}
