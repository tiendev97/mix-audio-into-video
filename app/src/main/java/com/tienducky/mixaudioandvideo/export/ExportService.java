package com.tienducky.mixaudioandvideo.export;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import com.techyourchance.threadposter.BackgroundThreadPoster;
import com.techyourchance.threadposter.UiThreadPoster;

import java.io.File;
import java.nio.ByteBuffer;

public class ExportService {
    private static final String TAG = "ExportService";
    private BackgroundThreadPoster mExportThread = new BackgroundThreadPoster();
    private UiThreadPoster mUIThread = new UiThreadPoster();
    private ExportElement mExportElement;
    private ExportAdapter mExportAdapter;
    private File mOutputFile;
    private volatile boolean stopExport;

    public void startExport(File outputFile, ExportElement exportElement, ExportAdapter exportAdapter) {
        mExportElement = exportElement;
        mExportAdapter = exportAdapter;
        mOutputFile = outputFile;
        mExportThread.post(this::exportVideo);
    }

    @SuppressLint("WrongConstant")
    private void exportVideo() {
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            audioExtractor.setDataSource(mExportElement.getAudioFilePath());
            videoExtractor.setDataSource(mExportElement.getVideoFilePath());

            muxer = new MediaMuxer(mOutputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);

            videoExtractor.selectTrack(0);
            MediaFormat videoFormat = videoExtractor.getTrackFormat(0);
            int videoTrack = muxer.addTrack(videoFormat);

//            audioExtractor.selectTrack(0);
//            MediaFormat audioFormat = audioExtractor.getTrackFormat(0);
//            int audioTrack = muxer.addTrack(audioFormat);

            Log.d(TAG, "Video Format " + videoFormat.toString());
//            Log.d(TAG, "Audio Format " + audioFormat.toString());

            boolean sawEOS = false;
            boolean sawEOS2 = false;
            int frameCount = 0;
            int offset = 0;
            int sampleSize = 1024 * 1024;
            ByteBuffer videoBuf = ByteBuffer.allocate(sampleSize);
            ByteBuffer audioBuf = ByteBuffer.allocate(sampleSize);
            MediaCodec.BufferInfo videoBufferInfo = new MediaCodec.BufferInfo();
            MediaCodec.BufferInfo audioBufferInfo = new MediaCodec.BufferInfo();
            videoExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
            audioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);

            muxer.start();

            while (!sawEOS && !stopExport) {
                videoBufferInfo.offset = offset;
                videoBufferInfo.size = videoExtractor.readSampleData(videoBuf, offset);


                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
                    Log.d(TAG, "saw input EOS.");
                    sawEOS = true;
                    videoBufferInfo.size = 0;

                } else {
                    videoBufferInfo.presentationTimeUs = videoExtractor.getSampleTime();
                    videoBufferInfo.flags = MediaCodec.BUFFER_FLAG_SYNC_FRAME;
                    muxer.writeSampleData(videoTrack, videoBuf, videoBufferInfo);
                    videoExtractor.advance();


                    frameCount++;
                    Log.d(TAG, "Frame (" + frameCount + ") Video PresentationTimeUs:" + videoBufferInfo.presentationTimeUs + " Flags:" + videoBufferInfo.flags + " Size(KB) " + videoBufferInfo.size / 1024);
                }
            }
            Log.d(TAG, "frame of video:" + frameCount);

//            frameCount = 0;
//            while (!sawEOS2 && !stopExport) {
//                frameCount++;
//                audioBufferInfo.offset = offset;
//                audioBufferInfo.size = audioExtractor.readSampleData(audioBuf, offset);
//
//                if (videoBufferInfo.size < 0 || audioBufferInfo.size < 0) {
//                    Log.d(TAG, "saw input EOS.");
//                    sawEOS2 = true;
//                    audioBufferInfo.size = 0;
//                } else {
//                    audioBufferInfo.presentationTimeUs = audioExtractor.getSampleTime();
//                    audioBufferInfo.flags = audioExtractor.getSampleFlags();
//                    muxer.writeSampleData(audioTrack, audioBuf, audioBufferInfo);
//                    audioExtractor.advance();
//
//                    Log.d(TAG, "Frame (" + frameCount + ") Audio PresentationTimeUs:" + audioBufferInfo.presentationTimeUs + " Flags:" + audioBufferInfo.flags + " Size(KB) " + audioBufferInfo.size / 1024);
//
//                }
//            }
//            Log.d(TAG, "frame Audio:" + frameCount);

            muxer.stop();
            mUIThread.post(() -> mExportAdapter.onExportComplete());
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
            mOutputFile.delete();
            mUIThread.post(() -> mExportAdapter.onExportError());
        } finally {
            if(muxer != null)
                muxer.release();
            videoExtractor.release();
            audioExtractor.release();
        }
    }

    public void stopExport() {
        stopExport = true;
    }
}
