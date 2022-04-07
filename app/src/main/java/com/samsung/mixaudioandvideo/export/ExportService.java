package com.samsung.mixaudioandvideo.export;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.media.MediaCodec;
import android.media.MediaCodecInfo;
import android.media.MediaExtractor;
import android.media.MediaFormat;
import android.media.MediaMuxer;
import android.media.MediaScannerConnection;
import android.util.Log;

import androidx.annotation.NonNull;

import com.techyourchance.threadposter.BackgroundThreadPoster;
import com.techyourchance.threadposter.UiThreadPoster;
import com.samsung.mixaudioandvideo.models.TrackType;
import com.samsung.mixaudioandvideo.utils.MediaUtils;

import java.io.File;
import java.io.IOException;
import java.nio.ByteBuffer;

public class ExportService {
    private static final String TAG = "ExportService";

    private static final String OUTPUT_AUDIO_KEY_MINE = "audio/mp4a-latm";
    private static final long TIMEOUT_US = 10000L;

    private final BackgroundThreadPoster mExportThread = new BackgroundThreadPoster();
    private final UiThreadPoster mUIThread = new UiThreadPoster();
    private ExportElement mExportElement;
    private ExportAdapter mExportAdapter;
    private File mOutputFile;
    private Activity mActivity;

    // export params
    private long mVideoDuration;
    private MediaExtractor mVideoExtractor;
    private MediaExtractor mAudioExtractor;
    private MediaFormat mInputVideoFormat;
    private MediaFormat mInputAudioFormat;
    private MediaCodec mAudioDecoder;
    private MediaCodec mAudioEncoder;
    private MediaMuxer muxer;
    private int mInputVideoTrack;
    private int mInputAudioTrack;
    private int mMuxerVideoTrack;
    private int mMuxerAudioTrack;
    private volatile boolean mStopExport;
    private volatile boolean mIsExportRunning;
    private volatile boolean mErrorWhenExporting;
    private boolean mMuxAudioDone;
    private boolean mMuxVideoDone;


    public void startExport(Activity activity, File outputFile, ExportElement exportElement, ExportAdapter exportAdapter) {
        mExportElement = exportElement;
        mExportAdapter = exportAdapter;
        mOutputFile = outputFile;
        mActivity = activity;
        mExportThread.post(this::exportVideo);
    }

    private void exportVideo() {
        Log.i(TAG, "exportVideo " + mExportElement.getAudioFilePath() + ", " + mExportElement.getVideoFilePath() + ", " + mOutputFile.getPath());

        try {
            initResources();
            mExportThread.post(this::startMuxVideo);
            mExportThread.post(this::startMuxAudio);
            waitMuxerFinished();
        } catch (Exception ex) {
            handleExportFailed(ex);
        } finally {
            cleanup();
        }
    }

    /***
     * init resources for exporting
     *
     * @throws IOException
     */
    private void initResources() throws IOException {
        // create audio and video extractor
        mVideoExtractor = createMediaExtractor(mExportElement.getVideoFilePath());
        mAudioExtractor = createMediaExtractor(mExportElement.getAudioFilePath());

        // get audio and video track
        mInputVideoTrack = MediaUtils.getTrackIndex(mVideoExtractor, TrackType.VIDEO);
        mInputAudioTrack = MediaUtils.getTrackIndex(mAudioExtractor, TrackType.AUDIO);

        // export video format
        mInputVideoFormat = mVideoExtractor.getTrackFormat(mInputVideoTrack);
        mInputAudioFormat = mAudioExtractor.getTrackFormat(mInputAudioTrack);

        // calculate export video duration
        mVideoDuration = mInputVideoFormat.getLong(MediaFormat.KEY_DURATION);

        // prepare audio decoder and encoder
        prepareAudioDecoderAndEncoder();

        // prepare media muxer
        prepareMediaMuxer();
    }

    private void waitMuxerFinished() {
        try {
            synchronized (this) {
                while (!mMuxVideoDone || !mMuxAudioDone) {
                    wait();
                }

                if (mErrorWhenExporting) {
                    handleExportFailed(new Exception("Error when mux audio and video"));
                } else {
                    handleExportComplete();
                }
            }
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    /***
     * handle export complete
     */
    private void handleExportComplete() {
        MediaScannerConnection.scanFile(mActivity, new String[]{mOutputFile.getPath()}, null, (path, uri) -> {
            Log.i(TAG, "runMediaScanner " + mOutputFile.getPath());
            mUIThread.post(() -> mExportAdapter.onExportComplete());
        });
    }

    /***
     * cleanup resources
     */
    private void cleanup() {
        Log.i(TAG, "cleanup resources stopByUser: " + mStopExport);

        if (muxer != null) {
            muxer.stop();
            muxer.release();
            muxer = null;
        }

        if (mAudioDecoder != null) {
            mAudioDecoder.stop();
            mAudioDecoder.release();
            mAudioDecoder = null;
        }

        if (mAudioEncoder != null) {
            mAudioEncoder.stop();
            mAudioEncoder.release();
            mAudioEncoder = null;
        }

        if (mVideoExtractor != null) {
            mVideoExtractor.release();
            mVideoExtractor = null;
        }

        if (mAudioExtractor != null) {
            mAudioExtractor.release();
            mVideoExtractor = null;
        }

        if (mStopExport) {
            clearOutputFile();
        }
    }

    public boolean isExportRunning() {
        return mIsExportRunning;
    }

    /**
     * Stop export video
     */
    public void stopExport() {
        Log.i(TAG, "stop export video");
        mStopExport = true;
        clearOutputFile();
        cleanup();
    }

    /***
     * Handle export complete
     *
     * @param ex
     */
    private void handleExportFailed(Exception ex) {
        ex.printStackTrace();
        clearOutputFile();
        mUIThread.post(() -> mExportAdapter.onExportFail());
    }

    private void clearOutputFile() {
        boolean isDelete = mOutputFile.delete();
        Log.i(TAG, "clearOutputFile: " + isDelete);
    }

    /***
     * create audio decoder and encoder
     *
     * @throws IOException
     */
    private void prepareAudioDecoderAndEncoder() throws IOException {
        Log.i(TAG, "1. prepareAudioDecoderAndEncoder ");
        mAudioExtractor.selectTrack(mInputAudioTrack);
        mAudioDecoder = MediaCodec.createDecoderByType(mInputAudioFormat.getString(MediaFormat.KEY_MIME));
        mAudioDecoder.configure(mInputAudioFormat, null, null, 0);
        mAudioDecoder.start();

        MediaFormat audioOutputFormat = prepareAudioOutputFormat(mInputAudioFormat);
        mAudioEncoder = MediaCodec.createEncoderByType(audioOutputFormat.getString(MediaFormat.KEY_MIME));
        mAudioEncoder.configure(audioOutputFormat, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE);
        mAudioEncoder.start();
    }

    /***
     * prepare media muxer
     *
     * @throws IOException
     */
    private void prepareMediaMuxer() throws IOException {
        Log.i(TAG, "2. prepareMediaMuxer ");
        muxer = new MediaMuxer(mOutputFile.getPath(), MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
        mMuxerVideoTrack = muxer.addTrack(mInputVideoFormat);
        mMuxerAudioTrack = muxer.addTrack(mAudioEncoder.getOutputFormat());
        muxer.start();
    }

    /***
     * create new MediaExtractor from file path
     *
     * @param filePath
     * @return
     * @throws IOException
     */
    private MediaExtractor createMediaExtractor(String filePath) throws IOException {
        MediaExtractor extractor = new MediaExtractor();
        extractor.setDataSource(filePath);
        return extractor;
    }

    /***
     * start export video
     */
    @SuppressLint("WrongConstant")
    private void startMuxVideo() {
        Log.i(TAG, "3. startMuxVideo ");
        long startTime = System.currentTimeMillis();
        int maxBufferSize = mInputVideoFormat.getInteger(MediaFormat.KEY_MAX_INPUT_SIZE);
        MediaCodec.BufferInfo bufferInfo = new MediaCodec.BufferInfo();
        ByteBuffer videoBuffer = ByteBuffer.allocateDirect(maxBufferSize);
        mVideoExtractor.selectTrack(mInputVideoTrack);
        mIsExportRunning = true;

        try {
            while (!mStopExport) {
                int size = mVideoExtractor.readSampleData(videoBuffer, 0);
                if (size == -1) { // last file
                    break;
                }
                long sampleTimeUs = mVideoExtractor.getSampleTime();
                int flags = mVideoExtractor.getSampleFlags();
                bufferInfo.presentationTimeUs = sampleTimeUs;
                bufferInfo.flags = flags;
                bufferInfo.size = size;
                muxer.writeSampleData(mMuxerVideoTrack, videoBuffer, bufferInfo);
//                Log.i(TAG, "mux video: { timeStamp " + sampleTimeUs + " , progress = " + getCurrentProgress(sampleTimeUs) + "}");
                mVideoExtractor.advance();
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            mErrorWhenExporting = true;
        }

        Log.i(TAG, "total time export video: " + (System.currentTimeMillis() - startTime));
        synchronized (this) {
            mMuxVideoDone = true;
            notifyAll();
        }
    }

    private int getCurrentProgress(long timeStamp) {
        return (int) ((float) timeStamp / mVideoDuration * 100);
    }

    /***
     * start export audio
     */
    private void startMuxAudio() {
        Log.i(TAG, "4. startMuxAudio ");

        long startTime = System.currentTimeMillis();
        boolean allInputExtracted = false;
        boolean allInputDecoded = false;
        boolean allOutputEncoded = false;
        MediaCodec.BufferInfo mAudioBufferInfo = new MediaCodec.BufferInfo();
        long lastPresentationTimeUs = 0;

        try {
            while (!allOutputEncoded && !mStopExport) {
                // feed input to decoder
                if (!allInputExtracted) {
                    int inBufferId = mAudioDecoder.dequeueInputBuffer(TIMEOUT_US);
                    if (inBufferId >= 0) {
                        ByteBuffer buffer = mAudioDecoder.getInputBuffer(inBufferId);
                        int sampleSize = mAudioExtractor.readSampleData(buffer, 0);

                        if (sampleSize >= 0) {
                            if (mAudioBufferInfo.presentationTimeUs > mVideoDuration) {
                                mAudioDecoder.queueInputBuffer(
                                        inBufferId, 0, 0,
                                        0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                );
                                allInputExtracted = true;
                            } else {
                                mAudioDecoder.queueInputBuffer(
                                        inBufferId, 0,
                                        sampleSize,
                                        lastPresentationTimeUs + mAudioExtractor.getSampleTime(),
                                        mAudioExtractor.getSampleFlags()
                                );
                                mAudioExtractor.advance();
                            }
                        } else {
                            if (mAudioBufferInfo.presentationTimeUs < mVideoDuration) {
                                lastPresentationTimeUs = mAudioBufferInfo.presentationTimeUs;
                                mAudioExtractor.seekTo(0, MediaExtractor.SEEK_TO_CLOSEST_SYNC);
                                mAudioExtractor.advance();
                            } else {
                                mAudioDecoder.queueInputBuffer(
                                        inBufferId, 0, 0,
                                        0, MediaCodec.BUFFER_FLAG_END_OF_STREAM
                                );
                                allInputExtracted = true;
                            }
                        }
                    }
                }

                boolean encoderOutputAvailable = true;
                boolean decoderOutputAvailable = true;

                while ((encoderOutputAvailable || decoderOutputAvailable)) {
                    // drain Encoder & mux first
                    int outBufferId = mAudioEncoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_US);
                    if (outBufferId >= 0) {
                        ByteBuffer encodedBuffer = mAudioEncoder.getOutputBuffer(outBufferId);
                        muxer.writeSampleData(mMuxerAudioTrack, encodedBuffer, mAudioBufferInfo);
                        mAudioEncoder.releaseOutputBuffer(outBufferId, false);
//                        Log.i(TAG, "mux audio: { timeStamp = "
//                                + mAudioBufferInfo.presentationTimeUs + "}"
//                                + " , progress = " + getCurrentProgress(mAudioBufferInfo.presentationTimeUs) + "}"
//                        );
                        mUIThread.post(() -> mExportAdapter.onExportProgressUpdate(getCurrentProgress(mAudioBufferInfo.presentationTimeUs)));

                        // check finished
                        if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0) {
                            allOutputEncoded = true;
                            break;
                        }
                    } else if (outBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                        encoderOutputAvailable = false;
                    }

                    if (outBufferId != MediaCodec.INFO_TRY_AGAIN_LATER)
                        continue;

                    // Get output from decoder and feed it to encoder
                    if (!allInputDecoded) {
                        outBufferId = mAudioDecoder.dequeueOutputBuffer(mAudioBufferInfo, TIMEOUT_US);
                        if (outBufferId >= 0) {
                            ByteBuffer outBuffer = mAudioDecoder.getOutputBuffer(outBufferId);

                            // If needed, process decoded data here
                            // ...
                            // We drained the encoder, so there should be input buffer
                            // available. If this is not the case, we get a NullPointerException
                            // when touching inBuffer
                            int inBufferId = mAudioEncoder.dequeueInputBuffer(TIMEOUT_US);
                            ByteBuffer inBuffer = mAudioEncoder.getInputBuffer(inBufferId);

                            // Copy buffers - decoder output goes to encoder input
                            inBuffer.put(outBuffer);

                            // Feed encoder
                            mAudioEncoder.queueInputBuffer(
                                    inBufferId, mAudioBufferInfo.offset,
                                    mAudioBufferInfo.size,
                                    mAudioBufferInfo.presentationTimeUs,
                                    mAudioBufferInfo.flags);

                            mAudioDecoder.releaseOutputBuffer(outBufferId, false);
                            // Did we get all output from decoder?
                            if ((mAudioBufferInfo.flags & MediaCodec.BUFFER_FLAG_END_OF_STREAM) != 0)
                                allInputDecoded = true;

                        } else if (outBufferId == MediaCodec.INFO_TRY_AGAIN_LATER) {
                            decoderOutputAvailable = false;
                        }
                    }
                }
            }
        } catch (Exception ex) {
            ex.printStackTrace();
            mErrorWhenExporting = true;
        }
        Log.i(TAG, "total time export audio: " + (System.currentTimeMillis() - startTime));
        synchronized (this) {
            mMuxAudioDone = true;
            notifyAll();
        }
    }

    /***
     * create new audio output format
     * Prepare output format aac/m4a
     *
     * @param inputAudioFormat
     * @return
     */
    @NonNull
    private MediaFormat prepareAudioOutputFormat(MediaFormat inputAudioFormat) {
        MediaFormat outputFormat = new MediaFormat();
        outputFormat.setString(MediaFormat.KEY_MIME, OUTPUT_AUDIO_KEY_MINE);
        outputFormat.setInteger(MediaFormat.KEY_AAC_PROFILE, MediaCodecInfo.CodecProfileLevel.AACObjectLC);
        outputFormat.setInteger(MediaFormat.KEY_SAMPLE_RATE, inputAudioFormat.getInteger(MediaFormat.KEY_SAMPLE_RATE));
        outputFormat.setInteger(MediaFormat.KEY_BIT_RATE, inputAudioFormat.getInteger(MediaFormat.KEY_BIT_RATE));
        outputFormat.setInteger(MediaFormat.KEY_CHANNEL_COUNT, inputAudioFormat.getInteger(MediaFormat.KEY_CHANNEL_COUNT));
        outputFormat.setInteger(MediaFormat.KEY_MAX_INPUT_SIZE, 1048576); // // avoid BufferOverflowException
        return outputFormat;
    }
}

