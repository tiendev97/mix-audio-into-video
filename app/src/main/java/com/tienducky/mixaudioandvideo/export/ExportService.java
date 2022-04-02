package com.tienducky.mixaudioandvideo.export;

import android.annotation.SuppressLint;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.util.Log;

import androidx.annotation.NonNull;

import com.techyourchance.threadposter.BackgroundThreadPoster;
import com.techyourchance.threadposter.UiThreadPoster;
import com.tienducky.mixaudioandvideo.models.TrackType;
import com.tienducky.mixaudioandvideo.utils.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExportService {
    private static final String TAG = "ExportService";
    private BackgroundThreadPoster mExportThread = new BackgroundThreadPoster();
    private UiThreadPoster mUIThread = new UiThreadPoster();
    private ExportElement mExportElement;
    private ExportAdapter mExportAdapter;
    private File mOutputFile;

    // export params
    private long mTotalDuration;

    public void startExport(File outputFile, ExportElement exportElement, ExportAdapter exportAdapter) {
        mExportElement = exportElement;
        mExportAdapter = exportAdapter;
        mOutputFile = outputFile;
        mExportThread.post(this::exportVideo);
    }

    private void exportVideo() {
        Log.i(TAG, "exportVideo " + mExportElement.getAudioFilePath() + ", " + mExportElement.getVideoFilePath() + ", " + mOutputFile.getPath());
        MediaExtractor videoExtractor = new MediaExtractor();
        MediaExtractor audioExtractor = new MediaExtractor();
        MediaMuxer muxer = null;
        try {
            videoExtractor = new MediaExtractor();
            videoExtractor.setDataSource(mExportElement.getVideoFilePath());
            audioExtractor = new MediaExtractor();
            audioExtractor.setDataSource(mExportElement.getAudioFilePath());

            int inputVideoTrack = MediaUtils.getTrackIndex(videoExtractor, TrackType.VIDEO);
            int inputAudioTrack = MediaUtils.getTrackIndex(audioExtractor, TrackType.AUDIO);

            // export video format
            MediaFormat inputVideoFormat = videoExtractor.getTrackFormat(inputVideoTrack);
            MediaFormat inputAudioFormat = audioExtractor.getTrackFormat(inputAudioTrack);

            long videoDuration = inputVideoFormat.getLong(MediaFormat.KEY_DURATION);
            mTotalDuration  = videoDuration * 2;

            Log.i(TAG, "inputVideoFormat = " + inputVideoFormat);
            Log.i(TAG, "inputAudioFormat = " + inputAudioFormat);

            muxer = new MediaMuxer(mOutputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            int muxerVideoTrack = muxer.addTrack(inputVideoFormat);

            long previousTime = System.currentTimeMillis();
            mergeAudioIntoOutputFile(muxer, audioExtractor, inputAudioFormat, inputAudioTrack, videoDuration);
            Log.i("tien.ngc", "time export audio " + (System.currentTimeMillis() - previousTime) / 60);
            previousTime = System.currentTimeMillis();
            mergeVideoIntoOutputFile(muxer, videoExtractor, inputVideoFormat, inputVideoTrack, muxerVideoTrack);
            Log.i("tien.ngc", "time export video " + (System.currentTimeMillis() - previousTime));

            muxer.stop();
            mUIThread.post(() -> mExportAdapter.onExportComplete());
        } catch (Exception ex) {
            Log.d(TAG, ex.toString());
            ex.printStackTrace();
            mOutputFile.delete();
            mUIThread.post(() -> mExportAdapter.onExportError());
        } finally {
            if (muxer != null)
                muxer.release();
            videoExtractor.release();
            audioExtractor.release();
        }
    }

    /***
     * This method is used to merge to output video file
     * @param muxer
     * @param videoExtractor
     * @param inputVideoFormat
     * @param inputVideoTrack
     */
    @SuppressLint("WrongConstant")
    private void mergeVideoIntoOutputFile(MediaMuxer muxer, MediaExtractor videoExtractor, MediaFormat inputVideoFormat, int inputVideoTrack, int muxerVideoTrack) {
        videoExtractor.selectTrack(inputVideoTrack);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int maxBufferSize = inputVideoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        ByteBuffer videoBuffer = ByteBuffer.allocateDirect(maxBufferSize);
//        long lastVideoTimeUs = 0;
        while (true) {
            int size = videoExtractor.readSampleData(videoBuffer, 0);
            if (size == -1) {
                break;
            }
            long sampleTimeUs = videoExtractor.getSampleTime();
            int flags = videoExtractor.getSampleFlags();
            bufferInfo.presentationTimeUs = sampleTimeUs;
            bufferInfo.flags = flags;
            bufferInfo.size = size;
            muxer.writeSampleData(muxerVideoTrack, videoBuffer, bufferInfo);
//            lastVideoTimeUs = sampleTimeUs;
            videoExtractor.advance();
        }
    }

    /***
     * This method is used to merge audio into output video file
     * @param muxer
     * @param audioExtractor
     * @param inputAudioFormat
     * @param inputAudioTrack
     * @throws IOException
     */
    private void mergeAudioIntoOutputFile(MediaMuxer muxer, MediaExtractor audioExtractor, MediaFormat inputAudioFormat, int inputAudioTrack, long totalDuration) throws IOException {
        audioExtractor.selectTrack(inputAudioTrack);
        MediaCodec decoder = MediaCodec.createDecoderByType(inputAudioFormat.getString(MediaFormat.KEY_MIME));
        decoder.configure(inputAudioFormat, null, null, 0);
        decoder.start();

        MediaFormat audioOutputFormat = prepareAudioOutputFormat(inputAudioFormat);
        MediaCodec encoder = MediaCodec.createEncoderByType(audioOutputFormat.getString(MediaFormat.KEY_MIME));
        encoder.configure(audioOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        encoder.start();

        Log.i(TAG,"outputAudioFormat: " + audioOutputFormat);

        boolean allInputExtracted = false;
        boolean allInputDecoded = false;
        boolean allOutputEncoded = false;

        long timeoutUs = 10000L;
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        int muxerAudioTrack = -1;

        while (!allOutputEncoded) {
            // feed input to decoder
            if (!allInputExtracted) {
                int inBufferId = decoder.dequeueInputBuffer(timeoutUs);
                if (inBufferId >= 0) {
                    ByteBuffer buffer = decoder.getInputBuffer(inBufferId);
                    int sampleSize = audioExtractor.readSampleData(buffer, 0);

                    if (sampleSize >= 0 && audioExtractor.getSampleTime() <= totalDuration) {
                        decoder.queueInputBuffer(
                                inBufferId, 0, sampleSize,
                                audioExtractor.getSampleTime(),
                                audioExtractor.getSampleFlags()
                        );
                        audioExtractor.advance();
                    } else {
                        decoder.queueInputBuffer(
                                inBufferId, 0, 0,
                                0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                        );
                        allInputExtracted = true;
                    }
                }
            }

            boolean encoderOutputAvailable = true;
            boolean decoderOutputAvailable = !allInputDecoded;

            while (encoderOutputAvailable || decoderOutputAvailable) {
                // drain Encoder & mux first
                int outBufferId = encoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                if (outBufferId >= 0) {
                    ByteBuffer encodedBuffer = encoder.getOutputBuffer(outBufferId);
                    muxer.writeSampleData(muxerAudioTrack, encodedBuffer, bufferInfo);
                    encoder.releaseOutputBuffer(outBufferId, false);

                    // check finished
                    if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                        allOutputEncoded = true;
                        break;
                    }
                } else if (outBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                    encoderOutputAvailable = false;
                } else if (outBufferId == MediaCodec.INFO_OUTPUT_FORMAT_CHANGED) {
                    muxerAudioTrack = muxer.addTrack(encoder.getOutputFormat());
                    muxer.start();
                }

                if (outBufferId != MediaCodec.INFO_TRY_AGAIN_LATER)
                    continue;

                // Get output from decoder and feed it to encoder
                if (!allInputDecoded) {
                    outBufferId = decoder.dequeueOutputBuffer(bufferInfo, timeoutUs);
                    if (outBufferId >= 0) {
                        ByteBuffer outBuffer = decoder.getOutputBuffer(outBufferId);

                        // If needed, process decoded data here
                        // ...

                        // We drained the encoder, so there should be input buffer
                        // available. If this is not the case, we get a NullPointerException
                        // when touching inBuffer
                        int inBufferId = encoder.dequeueInputBuffer(timeoutUs);
                        ByteBuffer inBuffer = encoder.getInputBuffer(inBufferId);

                        // Copy buffers - decoder output goes to encoder input
                        inBuffer.put(outBuffer);

                        // Feed encoder
                        encoder.queueInputBuffer(
                                inBufferId, bufferInfo.offset,
                                bufferInfo.size,
                                bufferInfo.presentationTimeUs,
                                bufferInfo.flags);

                        Log.i(TAG, "audio presentationTimeUs " + bufferInfo.presentationTimeUs);

                        decoder.releaseOutputBuffer(outBufferId, false);

                        // Did we get all output from decoder?
                        if ((bufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                            allInputDecoded = true;

                    } else if (outBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        decoderOutputAvailable = false;
                    }
                }
            }
        }
    }

    /***
     * This method is used to create new audio output format for export video
     * @param inputAudioFormat
     * @return
     */
    @NonNull
    private MediaFormat prepareAudioOutputFormat(MediaFormat inputAudioFormat) {
        // Prepare output format for aac/m4a
        MediaFormat outputFormat = new MediaFormat();
        outputFormat.setString(MediaFormat.KEY_MIME, "audio/mp4a-latm");
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, inputAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE));
        outputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1048576); // // avoid BufferOverflowException
        return outputFormat;
    }
}

