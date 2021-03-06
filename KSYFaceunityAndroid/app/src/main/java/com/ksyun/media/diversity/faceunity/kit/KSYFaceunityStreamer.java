package com.ksyun.media.diversity.faceunity.kit;

import android.content.Context;
import android.opengl.GLSurfaceView;
import android.os.Handler;
import android.os.Looper;
import android.text.TextUtils;
import android.util.Log;
import android.view.TextureView;

import com.ksyun.media.streamer.capture.AudioCapture;
import com.ksyun.media.streamer.capture.AudioDummyCapture;
import com.ksyun.media.streamer.capture.AudioPlayerCapture;
import com.ksyun.media.streamer.capture.CameraCapture;
import com.ksyun.media.streamer.capture.WaterMarkCapture;
import com.ksyun.media.streamer.encoder.AVCodecAudioEncoder;
import com.ksyun.media.streamer.encoder.AudioEncodeFormat;
import com.ksyun.media.streamer.encoder.AudioEncoderMgt;
import com.ksyun.media.streamer.encoder.Encoder;
import com.ksyun.media.streamer.encoder.ImgTexToBuf;
import com.ksyun.media.streamer.encoder.MediaCodecAudioEncoder;
import com.ksyun.media.streamer.encoder.VideoEncodeFormat;
import com.ksyun.media.streamer.encoder.VideoEncoderMgt;
import com.ksyun.media.streamer.filter.audio.AudioFilterMgt;
import com.ksyun.media.streamer.filter.audio.AudioMixer;
import com.ksyun.media.streamer.filter.audio.AudioPreview;
import com.ksyun.media.streamer.filter.audio.AudioResampleFilter;
import com.ksyun.media.streamer.filter.imgbuf.ImgBufScaleFilter;
import com.ksyun.media.streamer.filter.imgbuf.ImgPreProcessWrap;
import com.ksyun.media.streamer.filter.imgtex.ImgTexFilterMgt;
import com.ksyun.media.streamer.filter.imgtex.ImgTexMixer;
import com.ksyun.media.streamer.filter.imgtex.ImgTexScaleFilter;
import com.ksyun.media.streamer.framework.AVConst;
import com.ksyun.media.streamer.framework.AudioBufFormat;
import com.ksyun.media.streamer.kit.KSYStreamer;
import com.ksyun.media.streamer.kit.RecorderConstants;
import com.ksyun.media.streamer.kit.StreamerConstants;
import com.ksyun.media.streamer.logstats.StatsConstant;
import com.ksyun.media.streamer.logstats.StatsLogReport;
import com.ksyun.media.streamer.publisher.FilePublisher;
import com.ksyun.media.streamer.publisher.Publisher;
import com.ksyun.media.streamer.publisher.PublisherMgt;
import com.ksyun.media.streamer.publisher.RtmpPublisher;
import com.ksyun.media.streamer.util.gles.GLRender;


/**
 * All in one streamer class.
 */
public class KSYFaceunityStreamer {

    private static final String TAG = "KSYFaceunityStreamer";
    private static final boolean DEBUG = false;
    public static final String VERSION = "1.0.1.1";

    private Context mContext;

    private String mUri;
    private int mScreenRenderWidth = 0;
    private int mScreenRenderHeight = 0;
    private int mPreviewResolution = StreamerConstants.VIDEO_RESOLUTION_360P;
    private int mPreviewWidth = 0;
    private int mPreviewHeight = 0;
    private float mPreviewFps = 0;
    private int mTargetResolution = StreamerConstants.VIDEO_RESOLUTION_360P;
    private int mTargetWidth = 0;
    private int mTargetHeight = 0;
    private float mTargetFps = 0;
    private float mIFrameInterval = 3.0f;
    private int mVideoCodecId = AVConst.CODEC_ID_AVC;
    private int mEncodeScene = VideoEncodeFormat.ENCODE_SCENE_SHOWSELF;
    private int mEncodeProfile = VideoEncodeFormat.ENCODE_PROFILE_LOW_POWER;
    private int mRotateDegrees = 0;
    private int mMaxVideoBitrate = 800 * 1000;
    private int mInitVideoBitrate = 600 * 1000;
    private int mMinVideoBitrate = 200 * 1000;
    private boolean mAutoAdjustVideoBitrate = true;
    private int mAudioBitrate = 48 * 1000;
    private int mAudioSampleRate = 44100;
    private int mAudioChannels = 1;

    private boolean mFrontCameraMirror = false;
    private boolean mEnableStreamStatModule = true;
    private int mCameraFacing = CameraCapture.FACING_FRONT;

    private boolean mIsRecording = false;
    private boolean mIsFileRecording = false;
    private boolean mIsCaptureStarted = false;
    private boolean mIsAudioOnly = false;
    private boolean mIsAudioPreviewing = false;
    private boolean mDelayedStartCameraPreview = false;
    private boolean mEnableDebugLog = false;
    private boolean mEnableAudioMix = false;
    private boolean mUseDummyAudioCapture = false;
    private boolean mAutoRestart = false;
    private int mAutoRestartInterval = 3000;

    private KSYStreamer.OnInfoListener mOnInfoListener;
    private KSYStreamer.OnErrorListener mOnErrorListener;

    private GLRender mGLRender;
    private CameraCapture mCameraCapture;
    private WaterMarkCapture mWaterMarkCapture;
    private ImgTexScaleFilter mImgTexScaleFilter;
    private ImgTexMixer mImgTexMixer;
    private ImgTexFilterMgt mImgTexFilterMgt;
    private AudioCapture mAudioCapture;
    private AudioDummyCapture mAudioDummyCapture;
    private VideoEncoderMgt mVideoEncoderMgt;
    private AudioEncoderMgt mAudioEncoderMgt;
    private RtmpPublisher mRtmpPublisher;

    private AudioResampleFilter mAudioResampleFilter;
    private AudioFilterMgt mAudioFilterMgt;
    private AudioPlayerCapture mAudioPlayerCapture;
    private AudioMixer mAudioMixer;
    private AudioPreview mAudioPreview;
    private FilePublisher mFilePublisher;
    private PublisherMgt mPublisherMgt;

    private Handler mMainHandler;

    //faceunity
    private boolean mEnableFaceunityProp;
    private boolean mEnableFaceunityGesture;

    private ImgFaceunityFilter mFaceunityFilter;
    private ImgYFlipFilter mImgYFlipFilter;
    private ImgBufScaleFilter mFaceunityScale;

    public KSYFaceunityStreamer(Context context) {
        if (context == null) {
            throw new IllegalArgumentException("Context cannot be null!");
        }
        mContext = context.getApplicationContext();
        mMainHandler = new Handler(Looper.getMainLooper());
        initModules();
    }

    private void initModules() {
        // Init GLRender for gpu render
        mGLRender = new GLRender();

        // Watermark capture
        mWaterMarkCapture = new WaterMarkCapture(mGLRender);

        // Camera preview
        mCameraCapture = new CameraCapture(mContext, mGLRender);
        mImgTexScaleFilter = new ImgTexScaleFilter(mGLRender);
        mImgTexFilterMgt = new ImgTexFilterMgt(mContext);
        mImgTexMixer = new ImgTexMixer(mGLRender);
        mImgTexMixer.setIsPreviewer(true);
        mCameraCapture.mImgTexSrcPin.connect(mImgTexScaleFilter.getSinkPin());
        mImgTexScaleFilter.getSrcPin().connect(mImgTexFilterMgt.getSinkPin());
        //mImgTexFilterMgt.getSrcPin().connect(mImgTexMixer.getSinkPin(0));
        mWaterMarkCapture.mLogoTexSrcPin.connect(mImgTexMixer.getSinkPin(1));
        mWaterMarkCapture.mTimeTexSrcPin.connect(mImgTexMixer.getSinkPin(2));

        //init faceunity
        mFaceunityFilter = new ImgFaceunityFilter(mContext, mGLRender);
        mFaceunityScale = new ImgBufScaleFilter();
        mImgYFlipFilter = new ImgYFlipFilter(mGLRender);
        mImgTexFilterMgt.getSrcPin().connect(mImgYFlipFilter.getSinkPin());
        mImgYFlipFilter.getSrcPin().connect(mFaceunityFilter.getTexSinkPin());
        mCameraCapture.mImgBufSrcPin.connect(mFaceunityScale.getSinkPin());
        mFaceunityScale.getSrcPin().connect(mFaceunityFilter.getBufSinkPin());
        mFaceunityFilter.getSrcPin().connect(mImgTexMixer.getSinkPin(0));

        // Audio preview
        mAudioPlayerCapture = new AudioPlayerCapture(mContext);
        mAudioCapture = new AudioCapture();
        mAudioDummyCapture = new AudioDummyCapture();
        mAudioResampleFilter = new AudioResampleFilter();
        mAudioFilterMgt = new AudioFilterMgt();
        mAudioMixer = new AudioMixer();
        mAudioPreview = new AudioPreview();
        mAudioCapture.mAudioBufSrcPin.connect(mAudioResampleFilter.getSinkPin());
        mAudioDummyCapture.getSrcPin().connect(mAudioResampleFilter.getSinkPin());
        mAudioResampleFilter.getSrcPin().connect(mAudioFilterMgt.getSinkPin());
        mAudioFilterMgt.getSrcPin().connect(mAudioMixer.getSinkPin(0));
        if (mEnableAudioMix) {
            mAudioPlayerCapture.mSrcPin.connect(mAudioMixer.getSinkPin(1));
        }
        mAudioMixer.getSrcPin().connect(mAudioPreview.mSinkPin);

        // encoder
        mVideoEncoderMgt = new VideoEncoderMgt(mGLRender);
        mAudioEncoderMgt = new AudioEncoderMgt();
        mWaterMarkCapture.mLogoBufSrcPin.connect(mVideoEncoderMgt.getImgBufMixer().getSinkPin(1));
        mWaterMarkCapture.mTimeBufSrcPin.connect(mVideoEncoderMgt.getImgBufMixer().getSinkPin(2));
        mImgTexMixer.getSrcPin().connect(mVideoEncoderMgt.getImgTexSinkPin());
        mCameraCapture.mImgBufSrcPin.connect(mVideoEncoderMgt.getImgBufSinkPin());
        mAudioMixer.getSrcPin().connect(mAudioEncoderMgt.getSinkPin());

        // publisher
        mRtmpPublisher = new RtmpPublisher();
        mFilePublisher = new FilePublisher();

        mPublisherMgt = new PublisherMgt();
        mAudioEncoderMgt.getSrcPin().connect(mPublisherMgt.getAudioSink());
        mVideoEncoderMgt.getSrcPin().connect(mPublisherMgt.getVideoSink());
        mPublisherMgt.addPublisher(mFilePublisher);
        mPublisherMgt.addPublisher(mRtmpPublisher);

        // stats
        StatsLogReport.getInstance().initLogReport(mContext);

        // set listeners
        mAudioCapture.setAudioCaptureListener(new AudioCapture.OnAudioCaptureListener() {
            @Override
            public void onStatusChanged(int status) {
            }

            @Override
            public void onError(int errorCode) {
                Log.e(TAG, "AudioCapture error: " + errorCode);
                int what;
                switch (errorCode) {
                    case AudioCapture.AUDIO_START_FAILED:
                        what = StreamerConstants.KSY_STREAMER_AUDIO_RECORDER_ERROR_START_FAILED;
                        break;
                    case AudioCapture.AUDIO_ERROR_UNKNOWN:
                    default:
                        what = StreamerConstants.KSY_STREAMER_AUDIO_RECORDER_ERROR_UNKNOWN;
                        break;
                }
                if (mOnErrorListener != null) {
                    mOnErrorListener.onError(what, 0, 0);
                }
                //do not need to auto restart
            }
        });

        mCameraCapture.setOnCameraCaptureListener(new CameraCapture.OnCameraCaptureListener() {
            @Override
            public void onStarted() {
                Log.d(TAG, "CameraCapture ready");
                if (mOnInfoListener != null) {
                    mOnInfoListener.onInfo(StreamerConstants.KSY_STREAMER_CAMERA_INIT_DONE, 0, 0);
                }
            }

            @Override
            public void onFacingChanged(int facing) {
                mCameraFacing = facing;
                updateFrontMirror();
            }

            @Override
            public void onError(int err) {
                Log.e(TAG, "CameraCapture error: " + err);
                int what;
                switch (err) {
                    case CameraCapture.CAMERA_ERROR_START_FAILED:
                        what = StreamerConstants.KSY_STREAMER_CAMERA_ERROR_START_FAILED;
                        break;
                    case CameraCapture.CAMERA_ERROR_SERVER_DIED:
                        what = StreamerConstants.KSY_STREAMER_CAMERA_ERROR_SERVER_DIED;
                        break;
                    case CameraCapture.CAMERA_ERROR_EVICTED:
                        what = StreamerConstants.KSY_STREAMER_CAMERA_ERROR_EVICTED;
                        break;
                    case CameraCapture.CAMERA_ERROR_UNKNOWN:
                    default:
                        what = StreamerConstants.KSY_STREAMER_CAMERA_ERROR_UNKNOWN;
                        break;
                }
                if (mOnErrorListener != null) {
                    mOnErrorListener.onError(what, 0, 0);
                }
                //do not need to auto restart
            }
        });

        Encoder.EncoderListener encoderListener = new Encoder.EncoderListener() {
            @Override
            public void onError(Encoder encoder, int err) {
                if (err != 0) {
                    stopStream();
                }

                boolean isVideo = true;
                if (encoder instanceof MediaCodecAudioEncoder ||
                        encoder instanceof AVCodecAudioEncoder) {
                    isVideo = false;
                }

                int what;
                switch (err) {
                    case Encoder.ENCODER_ERROR_UNSUPPORTED:
                        what = isVideo ?
                                StreamerConstants.KSY_STREAMER_VIDEO_ENCODER_ERROR_UNSUPPORTED :
                                StreamerConstants.KSY_STREAMER_AUDIO_ENCODER_ERROR_UNSUPPORTED;
                        break;
                    case Encoder.ENCODER_ERROR_UNKNOWN:
                    default:
                        what = isVideo ?
                                StreamerConstants.KSY_STREAMER_VIDEO_ENCODER_ERROR_UNKNOWN :
                                StreamerConstants.KSY_STREAMER_AUDIO_ENCODER_ERROR_UNKNOWN;
                        break;
                }
                //do not need to auto restart
                if (mOnErrorListener != null) {
                    mOnErrorListener.onError(what, 0, 0);
                }
            }
        };
        mVideoEncoderMgt.setEncoderListener(encoderListener);
        mAudioEncoderMgt.setEncoderListener(encoderListener);

        mRtmpPublisher.setPubListener(new Publisher.PubListener() {
            @Override
            public void onInfo(int type, long msg) {
                switch (type) {
                    case RtmpPublisher.INFO_CONNECTED:
                        if (!mAudioEncoderMgt.getEncoder().isEncoding()) {
                            mAudioEncoderMgt.getEncoder().start();
                        }
                        mAudioEncoderMgt.getEncoder().sendExtraData();
                        if (mOnInfoListener != null) {
                            mOnInfoListener.onInfo(
                                    StreamerConstants.KSY_STREAMER_OPEN_STREAM_SUCCESS, 0, 0);
                        }
                        break;
                    case RtmpPublisher.INFO_AUDIO_HEADER_GOT:
                        if (!mIsAudioOnly) {
                            // start video encoder after audio header got
                            if (!mVideoEncoderMgt.getEncoder().isEncoding()) {
                                mVideoEncoderMgt.start();
                            }
                            mVideoEncoderMgt.getEncoder().sendExtraData();
                            mVideoEncoderMgt.getEncoder().forceKeyFrame();
                        }
                        break;
                    case RtmpPublisher.INFO_PACKET_SEND_SLOW:
                        Log.i(TAG, "packet send slow, delayed " + msg + "ms");
                        if (mOnInfoListener != null) {
                            mOnInfoListener.onInfo(
                                    StreamerConstants.KSY_STREAMER_FRAME_SEND_SLOW,
                                    (int) msg, 0);
                        }
                        break;
                    case RtmpPublisher.INFO_EST_BW_RAISE:
                        if (mIsAudioOnly) {
                            break;
                        }
                        if (mAutoAdjustVideoBitrate) {
                            Log.d(TAG, "Raise video bitrate to " + msg);
                            mVideoEncoderMgt.getEncoder().adjustBitrate((int) msg);
                        }
                        if (mOnInfoListener != null) {
                            mOnInfoListener.onInfo(
                                    StreamerConstants.KSY_STREAMER_EST_BW_RAISE, (int) msg, 0);
                        }
                        break;
                    case RtmpPublisher.INFO_EST_BW_DROP:
                        if (mIsAudioOnly) {
                            break;
                        }
                        if (mAutoAdjustVideoBitrate) {
                            Log.d(TAG, "Drop video bitrate to " + msg);
                            mVideoEncoderMgt.getEncoder().adjustBitrate((int) msg);
                        }
                        if (mOnInfoListener != null) {
                            mOnInfoListener.onInfo(
                                    StreamerConstants.KSY_STREAMER_EST_BW_DROP, (int) msg, 0);
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onError(int err, long msg) {
                Log.e(TAG, "RtmpPub err=" + err);
                if (err != 0) {
                    stopStream();
                }

                if (mOnErrorListener != null) {
                    int status;
                    switch (err) {
                        case RtmpPublisher.ERROR_CONNECT_BREAKED:
                            status = StreamerConstants.KSY_STREAMER_ERROR_CONNECT_BREAKED;
                            break;
                        case RtmpPublisher.ERROR_DNS_PARSE_FAILED:
                            status = StreamerConstants.KSY_STREAMER_ERROR_DNS_PARSE_FAILED;
                            break;
                        case RtmpPublisher.ERROR_CONNECT_FAILED:
                            status = StreamerConstants.KSY_STREAMER_ERROR_CONNECT_FAILED;
                            break;
                        case RtmpPublisher.ERROR_PUBLISH_FAILED:
                            status = StreamerConstants.KSY_STREAMER_ERROR_PUBLISH_FAILED;
                            break;
                        case RtmpPublisher.ERROR_AV_ASYNC_ERROR:
                            status = StreamerConstants.KSY_STREAMER_ERROR_AV_ASYNC;
                            break;
                        default:
                            status = StreamerConstants.KSY_STREAMER_ERROR_PUBLISH_FAILED;
                            break;
                    }
                    mOnErrorListener.onError(status, (int) msg, 0);
                    //do need to auto restart
                    autoRestart();
                }
            }
        });

        mFilePublisher.setPubListener(new Publisher.PubListener() {

            @Override
            public void onInfo(int type, long msg) {
                switch (type) {
                    case FilePublisher.INFO_OPENED:
                        //start audio encoder first
                        mAudioEncoderMgt.getEncoder().start();
                        mAudioEncoderMgt.getEncoder().sendExtraData();
                        if (mOnInfoListener != null) {
                            mOnInfoListener.onInfo(
                                    StreamerConstants.KSY_STREAMER_OPEN_STREAM_SUCCESS, 0, 0);
                        }
                        break;
                    case FilePublisher.INFO_AUDIO_HEADER_GOT:
                        if (!mIsAudioOnly) {
                            // start video encoder after audio header got
                            mVideoEncoderMgt.start();
                            mVideoEncoderMgt.getEncoder().sendExtraData();
                            mVideoEncoderMgt.getEncoder().forceKeyFrame();
                        }
                        break;
                    default:
                        break;
                }
            }

            @Override
            public void onError(int err, long msg) {
                Log.e(TAG, "FilePublisher err=" + err);
                if (err != 0) {
                    stopRecord();
                }

                if (mOnErrorListener != null) {
                    int status;
                    switch (err) {
                        case FilePublisher.FILE_PUBLISHER_ERROR_OPEN_FAILED:
                            status = StreamerConstants.KSY_STREAMER_FILE_PUBLISHER_OPEN_FAILED;
                            break;
                        case FilePublisher.FILE_PUBLISHER_ERROR_WRITE_FAILED:
                            status = StreamerConstants.KSY_STREAMER_FILE_PUBLISHER_WRITE_FAILED;
                            break;
                        case FilePublisher.FILE_PUBLISHER_ERROR_CLOSE_FAILED:
                            status = StreamerConstants.KSY_STREAMER_FILE_PUBLISHER_CLOSE_FAILED;
                            break;
                        default:
                            status = StreamerConstants.KSY_STREAMER_FILE_PUBLISHER_ERROR_UNKNOWN;
                            break;
                    }
                    mOnErrorListener.onError(status, (int) msg, 0);
                }
                //do not need to restart
            }
        });
    }

    /**
     * Get {@link GLRender} instance.
     *
     * @return GLRender instance.
     */
    public GLRender getGLRender() {
        return mGLRender;
    }

    /**
     * Get {@link CameraCapture} module instance.
     *
     * @return CameraCapture instance.
     */
    public CameraCapture getCameraCapture() {
        return mCameraCapture;
    }

    /**
     * Get {@link AudioCapture} module instance.
     *
     * @return AudioCapture instance.
     */
    public AudioCapture getAudioCapture() {
        return mAudioCapture;
    }

    /**
     * Get {@link ImgTexFilterMgt} instance to manage GPU filters.
     *
     * @return ImgTexFilterMgt instance.
     */
    public ImgTexFilterMgt getImgTexFilterMgt() {
        return mImgTexFilterMgt;
    }

    /**
     * Get {@link AudioFilterMgt} instance to manage audio filters.
     *
     * @return AudioFilterMgt instance
     */
    public AudioFilterMgt getAudioFilterMgt() {
        return mAudioFilterMgt;
    }

    /**
     * Get {@link ImgTexMixer} instance which could handle PIP related operations.
     *
     * @return ImgTexMixer instance.
     */
    public ImgTexMixer getImgTexMixer() {
        return mImgTexMixer;
    }

    /**
     * Get {@link VideoEncoderMgt} instance which control video encoders.
     *
     * @return VideoEncoderMgt instance.
     */
    public VideoEncoderMgt getVideoEncoderMgt() {
        return mVideoEncoderMgt;
    }

    /**
     * Get {@link AudioEncoderMgt} instance which control audio encoders.
     *
     * @return AudioEncoderMgt instance.
     */
    public AudioEncoderMgt getAudioEncoderMgt() {
        return mAudioEncoderMgt;
    }

    /**
     * Get {@link AudioPlayerCapture} instance which could handle BGM related operations.
     *
     * @return AudioPlayerCapture instance
     */
    public AudioPlayerCapture getAudioPlayerCapture() {
        return mAudioPlayerCapture;
    }

    /**
     * Get {@link RtmpPublisher} instance which publish encoded a/v frames throw rtmp protocol.
     *
     * @return RtmpPublisher instance.
     */
    public RtmpPublisher getRtmpPublisher() {
        return mRtmpPublisher;
    }

    /**
     * Set GLSurfaceView as camera previewer.<br/>
     * Must set once before the GLSurfaceView created.
     *
     * @param surfaceView GLSurfaceView to be set.
     */
    public void setDisplayPreview(GLSurfaceView surfaceView) {
        mGLRender.init(surfaceView);
        mGLRender.addListener(mGLRenderListener);
    }

    /**
     * Set TextureView as camera previewer.<br/>
     * Must set once before the TextureView ready.
     *
     * @param textureView TextureView to be set.
     */
    public void setDisplayPreview(TextureView textureView) {
        mGLRender.init(textureView);
        mGLRender.addListener(mGLRenderListener);
    }

    /**
     * Set offscreen preview.
     *
     * @param width  offscreen width
     * @param height offscreen height
     * @throws IllegalArgumentException
     */
    public void setOffscreenPreview(int width, int height) throws IllegalArgumentException {
        if (width <= 0 || height <= 0) {
            throw new IllegalArgumentException("Invalid offscreen resolution");
        }
        mGLRender.init(width, height);
        mGLRender.addListener(mGLRenderListener);
    }

    /**
     * Set streaming url.<br/>
     * must set before startStream, must not be null
     * The set url would take effect on the next {@link #startStream()} call.
     *
     * @param url Streaming url to set.
     * @throws IllegalArgumentException
     */
    public void setUrl(String url) throws IllegalArgumentException {
        if (TextUtils.isEmpty(url)) {
            throw new IllegalArgumentException("url can not be null");
        }
        mUri = url;
    }

    /**
     * get streaming url
     *
     * @return streaming url
     */
    public String getUrl() {
        return mUri;
    }

    /**
     * Set rotate degrees in anti-clockwise of current Activity.
     *
     * @param degrees Degrees in anti-clockwise, only 0, 90, 180, 270 accepted.
     * @throws IllegalArgumentException
     */
    public void setRotateDegrees(int degrees) throws IllegalArgumentException {
        degrees %= 360;
        if (degrees % 90 != 0) {
            throw new IllegalArgumentException("Invalid rotate degrees");
        }
        mRotateDegrees = degrees;
    }

    /**
     * get rotate degrees
     *
     * @return degrees Degrees in anti-clockwise, only 0, 90, 180, 270 accepted.
     */
    public int getRotateDegrees() {
        return mRotateDegrees;
    }

    /**
     * Set preview resolution.<br/>
     * <p>
     * The set resolution would take effect on next {@link #startCameraPreview()}
     * {@link #startCameraPreview(int)} call.<br/>
     * <p>
     * The set width and height must not be 0 at same time.
     * If one of the params is 0, the other would calculated by the actual preview view size
     * to keep the ratio of the preview view.
     *
     * @param width  preview width.
     * @param height preview height.
     * @throws IllegalArgumentException
     */
    public void setPreviewResolution(int width, int height) throws IllegalArgumentException {
        if (width < 0 || height < 0 || (width == 0 && height == 0)) {
            throw new IllegalArgumentException("Invalid resolution");
        }
        mPreviewWidth = width;
        mPreviewHeight = height;
    }

    /**
     * Set preview resolution index.<br/>
     * <p>
     * The set resolution would take effect on next {@link #startCameraPreview()}
     * {@link #startCameraPreview(int)} call.
     *
     * @param idx Resolution index.<br/>
     * @throws IllegalArgumentException
     * @see StreamerConstants#VIDEO_RESOLUTION_360P
     * @see StreamerConstants#VIDEO_RESOLUTION_480P
     * @see StreamerConstants#VIDEO_RESOLUTION_540P
     * @see StreamerConstants#VIDEO_RESOLUTION_720P
     */
    public void setPreviewResolution(int idx) throws IllegalArgumentException {
        if (idx < StreamerConstants.VIDEO_RESOLUTION_360P ||
                idx > StreamerConstants.VIDEO_RESOLUTION_720P) {
            throw new IllegalArgumentException("Invalid resolution index");
        }
        mPreviewResolution = idx;
        mPreviewWidth = 0;
        mPreviewHeight = 0;
    }

    /**
     * get preview width
     *
     * @return preview width
     */
    public int getPreviewWidth() {
        return mPreviewWidth;
    }

    /**
     * get preview height
     *
     * @return preview height
     */
    public int getPreviewHeight() {
        return mPreviewHeight;
    }

    /**
     * Set preview fps.<br/>
     * <p>
     * The set fps would take effect on next {@link #startCameraPreview()}
     * {@link #startCameraPreview(int)} call.<br/>
     * <p>
     * The actual preview fps is depend on device, may be different with the set value.
     *
     * @param fps frame rate to be set.
     * @throws IllegalArgumentException
     */
    public void setPreviewFps(float fps) throws IllegalArgumentException {
        if (fps <= 0) {
            throw new IllegalArgumentException("the fps must > 0");
        }
        mPreviewFps = fps;
        if (mTargetFps == 0) {
            mTargetFps = mPreviewFps;
        }
    }

    /**
     * get preview frame rate
     *
     * @return preview frame rate
     */
    public float getPreviewFps() {
        return mPreviewFps;
    }

    /**
     * Set encode method for both video and audio.<br/>
     * Must not be set while encoding.
     * default value:ENCODE_METHOD_SOFTWARE
     *
     * @param encodeMethod Encode method.<br/>
     * @throws IllegalArgumentException must not be ENCODE_METHOD_SOFTWARE_COMPAT
     * @throws IllegalStateException
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE
     * @see StreamerConstants#ENCODE_METHOD_HARDWARE
     */
    public void setEncodeMethod(int encodeMethod) throws IllegalStateException {
        if (encodeMethod == StreamerConstants.ENCODE_METHOD_SOFTWARE_COMPAT) {
            throw new IllegalArgumentException("not support ENCODE_METHOD_SOFTWARE_COMPAT");
        }
        setVideoEncodeMethod(encodeMethod);
        setAudioEncodeMethod(encodeMethod);
    }

    /**
     * Set encode method for video.<br/>
     * Must not be set while encoding.
     *
     * @param encodeMethod Encode method.<br/>
     * @throws IllegalStateException
     * @throws IllegalArgumentException must not be ENCODE_METHOD_SOFTWARE_COMPAT
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE
     * @see StreamerConstants#ENCODE_METHOD_HARDWARE
     */
    public void setVideoEncodeMethod(int encodeMethod) {
        if (mIsRecording) {
            throw new IllegalStateException("Cannot set encode method while recording");
        }

        if (encodeMethod == StreamerConstants.ENCODE_METHOD_SOFTWARE_COMPAT) {
            throw new IllegalArgumentException("not support ENCODE_METHOD_SOFTWARE_COMPAT");
        }

        mVideoEncoderMgt.setEncodeMethod(encodeMethod);
    }

    /**
     * Get video encode method.
     *
     * @return video encode method.
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE_COMPAT
     * @see StreamerConstants#ENCODE_METHOD_HARDWARE
     */
    public int getVideoEncodeMethod() {
        return mVideoEncoderMgt.getEncodeMethod();
    }

    /**
     * Set encode method for audio.<br/>
     * Must not be set while encoding.
     *
     * @param encodeMethod Encode method.<br/>
     * @throws IllegalStateException
     * @throws IllegalArgumentException must not be ENCODE_METHOD_SOFTWARE_COMPAT
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE
     * @see StreamerConstants#ENCODE_METHOD_HARDWARE
     */
    public void setAudioEncodeMethod(int encodeMethod) throws IllegalStateException {
        if (mIsRecording) {
            throw new IllegalStateException("Cannot set encode method while recording");
        }

        if (encodeMethod == StreamerConstants.ENCODE_METHOD_SOFTWARE_COMPAT) {
            throw new IllegalArgumentException("not support ENCODE_METHOD_SOFTWARE_COMPAT");
        }

        mAudioEncoderMgt.setEncodeMethod(encodeMethod);
    }

    /**
     * Get audio encode method.
     *
     * @return video encode method.
     * @see StreamerConstants#ENCODE_METHOD_SOFTWARE
     * @see StreamerConstants#ENCODE_METHOD_HARDWARE
     */
    public int getAudioEncodeMethod() {
        return mAudioEncoderMgt.getEncodeMethod();
    }

    /**
     * Set streaming resolution.<br/>
     * <p>
     * The set resolution would take effect on next
     * {@link #startStream()} call.<br/>
     * <p>
     * The set width and height must not be 0 at same time.
     * If one of the params is 0, the other would calculated by the actual preview view size
     * to keep the ratio of the preview view.
     *
     * @param width  streaming width.
     * @param height streaming height.
     * @throws IllegalArgumentException
     */
    public void setTargetResolution(int width, int height) throws IllegalArgumentException {
        if (width < 0 || height < 0 || (width == 0 && height == 0)) {
            throw new IllegalArgumentException("Invalid resolution");
        }
        mTargetWidth = width;
        mTargetHeight = height;
    }

    /**
     * Set streaming resolution index.<br/>
     * <p>
     * The set resolution would take effect on next
     * {@link #startStream()} call.
     *
     * @param idx Resolution index.<br/>
     * @throws IllegalArgumentException
     * @see StreamerConstants#VIDEO_RESOLUTION_360P
     * @see StreamerConstants#VIDEO_RESOLUTION_480P
     * @see StreamerConstants#VIDEO_RESOLUTION_540P
     * @see StreamerConstants#VIDEO_RESOLUTION_720P
     */
    public void setTargetResolution(int idx) throws IllegalArgumentException {
        if (idx < StreamerConstants.VIDEO_RESOLUTION_360P ||
                idx > StreamerConstants.VIDEO_RESOLUTION_720P) {
            throw new IllegalArgumentException("Invalid resolution index");
        }
        mTargetResolution = idx;
        mTargetWidth = 0;
        mTargetHeight = 0;
    }

    /**
     * get streaming width
     *
     * @return streaming width
     */
    public int getTargetWidth() {
        return mTargetWidth;
    }

    /**
     * get streaming height
     *
     * @return streaming height
     */
    public int getTargetHeight() {
        return mTargetHeight;
    }

    /**
     * Set streaming fps.<br/>
     * <p>
     * The set fps would take effect on next
     * {@link #startStream()} call.<br/>
     * <p>
     * If actual preview fps is larger than set value,
     * the extra frames will be dropped before encoding,
     * and if is smaller than set value, nothing will be done.
     * default value : 15
     *
     * @param fps frame rate.
     * @throws IllegalArgumentException
     */
    public void setTargetFps(float fps) throws IllegalArgumentException {
        if (fps <= 0) {
            throw new IllegalArgumentException("the fps must > 0");
        }
        mTargetFps = fps;
        if (mPreviewFps == 0) {
            mPreviewFps = mTargetFps;
        }
    }

    /**
     * get streaming fps
     *
     * @return streaming fps
     */
    public float getTargetFps() {
        return mTargetFps;
    }

    /**
     * Set key frames interval in seconds.<br/>
     * Would take effect on next {@link #startStream()} call.
     * default value 3.0f
     *
     * @param iFrameInterval key frame interval in seconds.
     * @throws IllegalArgumentException
     */
    public void setIFrameInterval(float iFrameInterval) throws IllegalArgumentException {
        if (iFrameInterval <= 0) {
            throw new IllegalArgumentException("the IFrameInterval must > 0");
        }

        mIFrameInterval = iFrameInterval;
    }

    /**
     * get key frames interval in seconds
     *
     * @return key frame interval in seconds.
     */
    public float getIFrameInterval() {
        return mIFrameInterval;
    }

    /**
     * Set video bitrate in bps, and disable video bitrate auto adjustment.<br/>
     * Would take effect on next {@link #startStream()} call.
     * default value : 600 * 1000
     *
     * @param bitrate video bitrate in bps
     * @throws IllegalArgumentException
     */
    public void setVideoBitrate(int bitrate) throws IllegalArgumentException {
        if (bitrate <= 0) {
            throw new IllegalArgumentException("the VideoBitrate must > 0");
        }
        mInitVideoBitrate = bitrate;
        mAutoAdjustVideoBitrate = false;
    }

    /**
     * Set video bitrate in kbps, and disable video bitrate auto adjustment.<br/>
     * Would take effect on next {@link #startStream()} call.
     *
     * @param kBitrate video bitrate in kbps
     * @throws IllegalArgumentException
     */
    public void setVideoKBitrate(int kBitrate) throws IllegalArgumentException {
        setVideoBitrate(kBitrate * 1024);
    }

    /**
     * Set video init/min/max bitrate in bps, and enable video bitrate auto adjustment.<br/>
     * Would take effect on next {@link #startStream()} call.
     *
     * @param initVideoBitrate init video bitrate in bps. default value 600 * 1000
     * @param maxVideoBitrate  max video bitrate in bps. default value 800 * 1000
     * @param minVideoBitrate  min video bitrate in bps. default value 200 * 1000
     * @throws IllegalArgumentException
     */
    public void setVideoBitrate(int initVideoBitrate, int maxVideoBitrate, int minVideoBitrate)
            throws IllegalArgumentException {
        if (initVideoBitrate <= 0 || maxVideoBitrate <= 0 || minVideoBitrate <= 0) {
            throw new IllegalArgumentException("the VideoBitrate must > 0");
        }

        mInitVideoBitrate = initVideoBitrate;
        mMaxVideoBitrate = maxVideoBitrate;
        mMinVideoBitrate = minVideoBitrate;
        mAutoAdjustVideoBitrate = true;
    }

    /**
     * Set video init/min/max bitrate in kbps, and enable video bitrate auto adjustment.<br/>
     * Would take effect on next {@link #startStream()} call.
     *
     * @param initVideoKBitrate init video bitrate in kbps.
     * @param maxVideoKBitrate  max video bitrate in kbps.
     * @param minVideoKBitrate  min video bitrate in kbps.
     * @throws IllegalArgumentException
     */
    public void setVideoKBitrate(int initVideoKBitrate,
                                 int maxVideoKBitrate,
                                 int minVideoKBitrate)
            throws IllegalArgumentException {
        setVideoBitrate(initVideoKBitrate * 1024,
                maxVideoKBitrate * 1024,
                minVideoKBitrate * 1024);
    }

    /**
     * get init video bit rate
     *
     * @return init video bit rate
     */
    public int getInitVideoBitrate() {
        return mInitVideoBitrate;
    }

    /**
     * get min video bit rate
     *
     * @return min video bit rate
     */
    public int getMinVideoBitrate() {
        return mMinVideoBitrate;
    }

    /**
     * get max video bit rate
     *
     * @return max video bit rate
     */
    public int getMaxVideoBitrate() {
        return mMaxVideoBitrate;
    }

    /**
     * check if is auto adjust video bit rate
     *
     * @return true if enabled false if disabled
     */
    public boolean isAutoAdjustVideoBitrate() {
        return mAutoAdjustVideoBitrate;
    }

    /**
     * Set codec id to video encoder.
     *
     * @param codecId video codec id to set.
     * @see AVConst#CODEC_ID_AVC
     * @see AVConst#CODEC_ID_HEVC
     */
    public void setVideoCodecId(int codecId) {
        mVideoCodecId = codecId;
    }

    /**
     * Get video encoder codec id.
     *
     * @return video codec id
     */
    public int getVideoCodecId() {
        return mVideoCodecId;
    }

    /**
     * Set scene mode to video encoder.
     * <p>
     * Only valid in ENCODE_METHOD_SOFTWARE and ENCODE_METHOD_SOFTWARE_COMPAT mode.
     *
     * @param scene scene mode to be set,
     *              default value {@link VideoEncodeFormat#ENCODE_SCENE_SHOWSELF}
     * @see VideoEncodeFormat#ENCODE_SCENE_DEFAULT
     * @see VideoEncodeFormat#ENCODE_SCENE_SHOWSELF
     * @see VideoEncodeFormat#ENCODE_SCENE_GAME
     */
    public void setVideoEncodeScene(int scene) {
        mEncodeScene = scene;
    }

    /**
     * Get scene mode for video encoder.
     *
     * @return scene mode
     */
    public int getVideoEncodeScene() {
        return mEncodeScene;
    }

    /**
     * Set encode profile to video encoder.
     * <p>
     * Only valid in ENCODE_METHOD_SOFTWARE and ENCODE_METHOD_SOFTWARE_COMPAT mode.
     *
     * @param profile encode profile mode to be set,
     *                default value {@link VideoEncodeFormat#ENCODE_PROFILE_LOW_POWER}
     * @see VideoEncodeFormat#ENCODE_PROFILE_LOW_POWER
     * @see VideoEncodeFormat#ENCODE_PROFILE_BALANCE
     * @see VideoEncodeFormat#ENCODE_PROFILE_HIGH_PERFORMANCE
     */
    public void setVideoEncodeProfile(int profile) {
        mEncodeProfile = profile;
    }

    /**
     * Get encode profile for video encoder.
     *
     * @return encode profile mode
     */
    public int getVideoEncodeProfile() {
        return mEncodeProfile;
    }

    /**
     * Set audio sample rate while streaming.<br/>
     * Would take effect on next {@link #startStream()} call.
     * default value 44100
     *
     * @param sampleRate sample rate in Hz.
     * @throws IllegalArgumentException
     */
    public void setAudioSampleRate(int sampleRate) throws IllegalArgumentException {
        if (sampleRate <= 0) {
            throw new IllegalArgumentException("the AudioSampleRate must > 0");
        }

        mAudioSampleRate = sampleRate;
    }

    /**
     * Set audio channel number.<br/>
     * Would take effect on next {@link #startStream()} call.
     * default value : 1
     *
     * @param channels audio channel number, 1 for mono, 2 for stereo.
     * @throws IllegalArgumentException
     */
    public void setAudioChannels(int channels) throws IllegalArgumentException {
        if (channels != 1 && channels != 2) {
            throw new IllegalArgumentException("the AudioChannels must be mono or stereo");
        }

        mAudioChannels = channels;
    }

    /**
     * Set audio bitrate in bps.<br/>
     * Would take effect on next {@link #startStream()} call.
     * default value : 48 * 1000
     *
     * @param bitrate audio bitrate in bps.
     * @throws IllegalArgumentException
     */
    public void setAudioBitrate(int bitrate) throws IllegalArgumentException {
        if (bitrate <= 0) {
            throw new IllegalArgumentException("the AudioBitrate must >0");
        }

        mAudioBitrate = bitrate;
    }

    /**
     * Set audio bitrate in kbps.<br/>
     * Would take effect on next {@link #startStream()} call.
     *
     * @param kBitrate audio bitrate in kbps.
     * @throws IllegalArgumentException
     */
    public void setAudioKBitrate(int kBitrate) throws IllegalArgumentException {
        setAudioBitrate(kBitrate * 1024);
    }

    /**
     * get audio bitrate in bps.
     *
     * @return audio bitrate in bps
     */
    public int getAudioBitrate() {
        return mAudioBitrate;
    }

    /**
     * get audio sample rate.
     *
     * @return audio sample rate in hz
     */
    public int getAudioSampleRate() {
        return mAudioSampleRate;
    }

    /**
     * get audio channel number
     *
     * @return audio channel number
     */
    public int getAudioChannels() {
        return mAudioChannels;
    }

    /**
     * Set enable front camera mirror or not while streaming.<br/>
     * Would take effect immediately while streaming.
     *
     * @param mirror true to enable, false to disable.
     */
    public void setFrontCameraMirror(boolean mirror) {
        mFrontCameraMirror = mirror;
        updateFrontMirror();
        StatsLogReport.getInstance().setIsFrontCameraMirror(mirror);
    }

    /**
     * check if front camera mirror enabled or not.
     *
     * @return true if mirror enabled, false if mirror disabled.
     */
    public boolean isFrontCameraMirrorEnabled() {
        return mFrontCameraMirror;
    }

    /**
     * Set initial camera facing.<br/>
     * Set before {@link #startCameraPreview()}, give a chance to set initial camera facing,
     * equals {@link #startCameraPreview(int)}.<br/>
     *
     * @param facing camera facing.
     * @see CameraCapture#FACING_FRONT
     * @see CameraCapture#FACING_BACK
     */
    public void setCameraFacing(int facing) {
        mCameraFacing = facing;
    }

    /**
     * get camera facing.
     *
     * @return camera facing
     */
    public int getCameraFacing() {
        return mCameraFacing;
    }

    /**
     * Start camera preview with default facing, or facing set by
     * {@link #setCameraFacing(int)} before.
     */
    public void startCameraPreview() {
        startCameraPreview(mCameraFacing);
    }

    /**
     * Start camera preview with given facing.
     *
     * @param facing camera facing.
     * @see CameraCapture#FACING_FRONT
     * @see CameraCapture#FACING_BACK
     */
    public void startCameraPreview(int facing) {
        mCameraFacing = facing;
        if (mScreenRenderWidth == 0 || mScreenRenderHeight == 0) {
            mDelayedStartCameraPreview = true;
        } else {
            setPreviewParams();
            mCameraCapture.start(mCameraFacing);
        }
    }

    /**
     * Stop camera preview.
     */
    public void stopCameraPreview() {
        mCameraCapture.stop();
    }

    private int getShortEdgeLength(int resolution) {
        switch (resolution) {
            case StreamerConstants.VIDEO_RESOLUTION_360P:
                return 360;
            case StreamerConstants.VIDEO_RESOLUTION_480P:
                return 480;
            case StreamerConstants.VIDEO_RESOLUTION_540P:
                return 540;
            case StreamerConstants.VIDEO_RESOLUTION_720P:
                return 720;
            default:
                return 720;
        }
    }

    private int align(int val, int align) {
        return (val + align - 1) / align * align;
    }

    private void calResolution() {
        if (mPreviewWidth == 0 && mPreviewHeight == 0) {
            int val = getShortEdgeLength(mPreviewResolution);
            if (mScreenRenderWidth > mScreenRenderHeight) {
                mPreviewHeight = val;
            } else {
                mPreviewWidth = val;
            }
        }
        if (mTargetWidth == 0 && mTargetHeight == 0) {
            int val = getShortEdgeLength(mTargetResolution);
            if (mScreenRenderWidth > mScreenRenderHeight) {
                mTargetHeight = val;
            } else {
                mTargetWidth = val;
            }
        }

        if (mPreviewWidth == 0) {
            mPreviewWidth = mPreviewHeight * mScreenRenderWidth / mScreenRenderHeight;
        } else if (mPreviewHeight == 0) {
            mPreviewHeight = mPreviewWidth * mScreenRenderHeight / mScreenRenderWidth;
        }
        mPreviewWidth = align(mPreviewWidth, 8);
        mPreviewHeight = align(mPreviewHeight, 8);
        if (mTargetWidth == 0) {
            mTargetWidth = mTargetHeight * mScreenRenderWidth / mScreenRenderHeight;
        } else if (mTargetHeight == 0) {
            mTargetHeight = mTargetWidth * mScreenRenderHeight / mScreenRenderWidth;
        }
        mTargetWidth = align(mTargetWidth, 8);
        mTargetHeight = align(mTargetHeight, 8);
    }

    private void updateFrontMirror() {
        if (mCameraFacing == CameraCapture.FACING_FRONT) {
            mImgTexMixer.setMirror(0, !mFrontCameraMirror);
            mVideoEncoderMgt.setImgBufMirror(mFrontCameraMirror);
            mFaceunityScale.setMirror(true);
        } else {
            mImgTexMixer.setMirror(0, false);
            mVideoEncoderMgt.setImgBufMirror(false);
            mFaceunityScale.setMirror(false);
        }
    }

    private void setAudioParams() {
        mAudioResampleFilter.setOutFormat(new AudioBufFormat(AVConst.AV_SAMPLE_FMT_S16,
                mAudioSampleRate, mAudioChannels));
    }

    private void setPreviewParams() {
        calResolution();
        mWaterMarkCapture.setPreviewSize(mScreenRenderWidth, mScreenRenderHeight);
        mWaterMarkCapture.setTargetSize(mTargetWidth, mTargetHeight);
        mCameraCapture.setOrientation(mRotateDegrees);
        mCameraCapture.setPreviewSize(mPreviewWidth, mPreviewHeight);
        mCameraCapture.setPreviewFps(mPreviewFps);

        mImgTexScaleFilter.setTargetSize(mPreviewWidth, mPreviewHeight);
        mImgTexMixer.setTargetSize(mTargetWidth, mTargetHeight);

        mFaceunityScale.setTargetSize(mTargetWidth, mTargetHeight);

        setAudioParams();
    }

    private void setRecordingParams() {
        calResolution();
        mImgTexMixer.setTargetSize(mTargetWidth, mTargetHeight);
        VideoEncodeFormat videoEncodeFormat = new VideoEncodeFormat(mVideoCodecId,
                mTargetWidth, mTargetHeight, mInitVideoBitrate);
        videoEncodeFormat.setFramerate(mTargetFps);
        videoEncodeFormat.setIframeinterval(mIFrameInterval);
        videoEncodeFormat.setScene(mEncodeScene);
        videoEncodeFormat.setProfile(mEncodeProfile);
        mVideoEncoderMgt.setEncodeFormat(videoEncodeFormat);

        AudioEncodeFormat audioEncodeFormat = new AudioEncodeFormat(AudioEncodeFormat.MIME_AAC,
                AVConst.AV_SAMPLE_FMT_S16, mAudioSampleRate, mAudioChannels, mAudioBitrate);
        mAudioEncoderMgt.setEncodeFormat(audioEncodeFormat);

        RtmpPublisher.BwEstConfig bwEstConfig = new RtmpPublisher.BwEstConfig();
        bwEstConfig.initAudioBitrate = mAudioBitrate;
        bwEstConfig.initVideoBitrate = mInitVideoBitrate;
        bwEstConfig.minVideoBitrate = mMinVideoBitrate;
        bwEstConfig.maxVideoBitrate = mMaxVideoBitrate;
        bwEstConfig.isAdjustBitrate = mAutoAdjustVideoBitrate;
        mRtmpPublisher.setBwEstConfig(bwEstConfig);
        mRtmpPublisher.setFramerate(mTargetFps);
        mRtmpPublisher.setVideoBitrate(mMaxVideoBitrate);
        mRtmpPublisher.setAudioBitrate(mAudioBitrate);
    }

    /**
     * Start streaming.<br/>
     * Must be called after {@link #setUrl(String)} and got
     * {@link StreamerConstants#KSY_STREAMER_CAMERA_INIT_DONE} event on
     * {@link OnInfoListener#onInfo(int, int, int)}.
     *
     * @return false if it's already streaming, true otherwise.
     */
    public boolean startStream() {
        if (mIsRecording) {
            return false;
        }
        mIsRecording = true;
        startCapture();
        mRtmpPublisher.connect(mUri);
        return true;
    }

    public boolean startRecord(String recordUrl) {
        if (mIsFileRecording) {
            return false;
        }
        mIsFileRecording = true;
        startCapture();
        mFilePublisher.startRecording(recordUrl);
        return true;
    }

    public void stopRecord() {
        if (!mIsFileRecording) {
            return;
        }
        mIsFileRecording = false;
        mFilePublisher.stop();
        stopCapture();
    }

    private void startCapture() {
        if (mIsCaptureStarted) {
            return;
        }
        mIsCaptureStarted = true;
        setAudioParams();
        setRecordingParams();
        if (!mUseDummyAudioCapture) {
            mAudioCapture.start();
        } else {
            mAudioDummyCapture.start();
        }
        mCameraCapture.startRecord();
    }

    private void stopCapture() {
        if (!mIsCaptureStarted) {
            return;
        }
        if (mIsRecording || mIsFileRecording) {
            return;
        }
        mIsCaptureStarted = false;
        if (!mIsAudioPreviewing) {
            mAudioCapture.stop();
            mAudioDummyCapture.stop();
        }
        if (mCameraCapture.isRecording()) {
            mCameraCapture.stopRecord();
        }

        mVideoEncoderMgt.stop();
        mAudioEncoderMgt.getEncoder().stop();
    }

    /**
     * Stop streaming.
     *
     * @return false if it's not streaming, true otherwise.
     */
    public boolean stopStream() {
        if (!mIsRecording) {
            return false;
        }
        mIsRecording = false;
        stopCapture();
        mRtmpPublisher.disconnect();
        return true;
    }

    /**
     * Get is recording started.
     *
     * @return true after start, false otherwise.
     */
    public boolean isRecording() {
        return mIsRecording;
    }

    public boolean isFileRecording() {
        return mIsFileRecording;
    }

    /**
     * Set if in audio only streaming mode.<br/>
     * If enable audio only before start stream, then disable it while streaming will
     * cause streaming error. Otherwise, start stream with audio only disabled,
     * you can enable or disable it dynamically.
     *
     * @param audioOnly true to enable, false to disable.
     */
    public void setAudioOnly(boolean audioOnly) {
        if (mIsAudioOnly == audioOnly) {
            return;
        }
        if (audioOnly) {
            mVideoEncoderMgt.getSrcPin().disconnect(false);
            if (mIsRecording) {
                mVideoEncoderMgt.getEncoder().stop();
            }
            mPublisherMgt.setAudioOnly(true);
        } else {
            mVideoEncoderMgt.getSrcPin().connect(mPublisherMgt.getVideoSink());
            mPublisherMgt.setAudioOnly(false);
            if (mIsRecording) {
                mVideoEncoderMgt.getEncoder().start();
            }
        }
        mIsAudioOnly = audioOnly;
    }

    /**
     * Enable to use AudioDummyCapture to output silence audio data
     * instead of mic data captured by AudioCapture or not.
     *
     * @param enable true to use AudioDummyCapture false to use AudioCapture
     */
    public void setUseDummyAudioCapture(boolean enable) {
        mUseDummyAudioCapture = enable;
        if (enable) {
            if (mAudioCapture.isRecordingState()) {
                mAudioCapture.stop();
                mAudioDummyCapture.start();
            }
        } else {
            if (mAudioDummyCapture.isRecordingState()) {
                mAudioDummyCapture.stop();
                mAudioCapture.start();
            }
        }
    }

    /**
     * Should be called on Activity.onResume or Fragment.onResume.
     */
    public void onResume() {
        Log.d(TAG, "onResume");
        mGLRender.onResume();
        if (mIsRecording && !mIsAudioOnly) {
            getVideoEncoderMgt().getEncoder().stopRepeatLastFrame();
        }
    }

    /**
     * Should be called on Activity.onPause or Fragment.onPause.
     */
    public void onPause() {
        Log.d(TAG, "onPause");
        mFaceunityFilter.onPause();
        mGLRender.onPause();
        if (mIsRecording && !mIsAudioOnly) {
            getVideoEncoderMgt().getEncoder().startRepeatLastFrame();
        }
    }

    /**
     * Set enable debug log or not.
     *
     * @param enableDebugLog true to enable, false to disable.
     */
    public void enableDebugLog(boolean enableDebugLog) {
        mEnableDebugLog = enableDebugLog;
        StatsLogReport.getInstance().setEnableDebugLog(mEnableDebugLog);
    }

    /**
     * Get encoded frame number.
     *
     * @return Encoded frame number on current streaming session.
     * @see #getVideoEncoderMgt()
     * @see VideoEncoderMgt#getEncoder()
     * @see Encoder#getFrameEncoded()
     */
    public long getEncodedFrames() {
        return mVideoEncoderMgt.getEncoder().getFrameEncoded();
    }

    /**
     * Get dropped frame number.
     *
     * @return Frame dropped number on current streaming session.
     * @see #getVideoEncoderMgt()
     * @see VideoEncoderMgt#getEncoder()
     * @see Encoder#getFrameDropped()
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getDroppedVideoFrames()
     */
    public int getDroppedFrameCount() {
        return mVideoEncoderMgt.getEncoder().getFrameDropped() +
                mRtmpPublisher.getDroppedVideoFrames();
    }

    /**
     * Get dns parse time of current or previous streaming session.
     *
     * @return dns parse time in ms.
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getDnsParseTime()
     */
    public int getDnsParseTime() {
        return mRtmpPublisher.getDnsParseTime();
    }

    /**
     * Get connect time of current or previous streaming session.
     *
     * @return connect time in ms.
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getConnectTime()
     */
    public int getConnectTime() {
        return mRtmpPublisher.getConnectTime();
    }

    /**
     * Get current upload speed.
     *
     * @return upload speed in kbps.
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getCurrentUploadKBitrate()
     */
    public int getCurrentUploadKBitrate() {
        return mRtmpPublisher.getCurrentUploadKBitrate();
    }

    /**
     * Get total uploaded data of current streaming session.
     *
     * @return uploaded data size in kbytes.
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getUploadedKBytes()
     */
    public int getUploadedKBytes() {
        return mRtmpPublisher.getUploadedKBytes();
    }

    /**
     * Get host ip of current or previous streaming session.
     *
     * @return host ip in format as 120.4.32.122
     * @see #getRtmpPublisher()
     * @see RtmpPublisher#getHostIp()
     */
    public String getRtmpHostIP() {
        return mRtmpPublisher.getHostIp();
    }

    /**
     * Set info listener.
     *
     * @param listener info listener
     */
    public void setOnInfoListener(KSYStreamer.OnInfoListener listener) {
        mOnInfoListener = listener;
    }

    /**
     * Set error listener.
     *
     * @param listener error listener
     */
    public void setOnErrorListener(KSYStreamer.OnErrorListener listener) {
        mOnErrorListener = listener;
    }

    /**
     * Switch camera facing between front and back.
     */
    public void switchCamera() {
        mCameraCapture.switchCamera();
    }

    /**
     * Get if current camera in use is front camera.<br/>
     *
     * @return true if front camera in use false otherwise.
     */
    public boolean isFrontCamera() {
        return mCameraFacing == CameraCapture.FACING_FRONT;
    }

    /**
     * Get if torch supported on current camera facing.
     *
     * @return true if supported, false if not.
     * @see #getCameraCapture()
     * @see CameraCapture#isTorchSupported()
     */
    public boolean isTorchSupported() {
        return mCameraCapture.isTorchSupported();
    }

    /**
     * Toggle torch of current camera.
     *
     * @param open true to turn on, false to turn off.
     * @return true if success, false if failed or on invalid mState.
     * @see #getCameraCapture()
     * @see CameraCapture#toggleTorch(boolean)
     */
    public boolean toggleTorch(boolean open) {
        return mCameraCapture.toggleTorch(open);
    }

    /**
     * Start bgm play.
     *
     * @param path bgm path.
     * @param loop true if loop this music, false if not.
     */
    public void startBgm(String path, boolean loop) {
        if (mIsAudioPreviewing) {
            mAudioPlayerCapture.setMute(true);
        }
        mAudioPlayerCapture.start(path, loop);
    }

    /**
     * Stop bgm play.
     */
    public void stopBgm() {
        mAudioPlayerCapture.stop();
    }

    /**
     * Set if enable audio mix, usually set true when headset plugged.
     *
     * @param enable true to enable, false to disable.
     */
    public void setEnableAudioMix(boolean enable) {
        mEnableAudioMix = enable;
        if (mEnableAudioMix) {
            mAudioPlayerCapture.mSrcPin.connect(mAudioMixer.getSinkPin(1));
        } else {
            mAudioPlayerCapture.mSrcPin.disconnect(mAudioMixer.getSinkPin(1), false);
        }
    }

    /**
     * check if audio mix is enabled.
     *
     * @return true if enable, false if not.
     */
    public boolean isAudioMixEnabled() {
        return mEnableAudioMix;
    }

    /**
     * Set mic volume.
     *
     * @param volume volume in 0~1.0f.
     */
    public void setVoiceVolume(float volume) {
        mAudioMixer.setInputVolume(0, volume);
    }

    /**
     * get mic volume
     *
     * @return volume in 0~1.0f.
     */
    public float getVoiceVolume() {
        return mAudioMixer.getInputVolume(0);
    }

    /**
     * Set enable cpu beauty filter.<br/>
     * Only need to set when video encode method is
     * {@link StreamerConstants#ENCODE_METHOD_SOFTWARE_COMPAT}.<br/>
     *
     * @param enable true to enable, false to disable.
     * @see #getVideoEncoderMgt()
     * @see VideoEncoderMgt#getEncodeMethod()
     */
    public void setEnableImgBufBeauty(boolean enable) {
        mVideoEncoderMgt.setEnableImgBufBeauty(enable);
    }

    /**
     * Set if mute audio while streaming.
     *
     * @param isMute true to mute, false to unmute.
     */
    public void setMuteAudio(boolean isMute) {
        if (!mIsAudioPreviewing) {
            mAudioPlayerCapture.setMute(isMute);
        }
        mAudioMixer.setMute(isMute);
    }

    /**
     * check if audio is muted or not.
     *
     * @return
     */
    public boolean isAudioMuted() {
        return mAudioMixer.getMute();
    }

    /**
     * Set if start audio preview.<br/>
     * Should start only when headset plugged.
     *
     * @param enable true to start, false to stop.
     */
    public void setEnableAudioPreview(boolean enable) {
        mIsAudioPreviewing = enable;
        if (enable) {
            setAudioParams();
            if (!mUseDummyAudioCapture) {
                mAudioCapture.start();
            } else {
                mAudioDummyCapture.start();
            }
            mAudioPreview.start();
            mAudioPlayerCapture.setMute(true);
        } else {
            if (!mIsRecording) {
                mAudioCapture.stop();
                mAudioDummyCapture.stop();
            }
            mAudioPreview.stop();
            mAudioPlayerCapture.setMute(false);
        }
    }

    /**
     * check if audio preview is enabled or not.
     *
     * @return true if audio preview is enabled
     */
    public boolean isAudioPreviewing() {
        return mIsAudioPreviewing;
    }

    /**
     * auto restart streamer when the following error occurred
     *
     * @param enable   default false
     * @param interval the restart interval(ms) default 3000
     * @see StreamerConstants#KSY_STREAMER_ERROR_CONNECT_BREAKED
     * @see StreamerConstants#KSY_STREAMER_ERROR_DNS_PARSE_FAILED
     * @see StreamerConstants#KSY_STREAMER_ERROR_CONNECT_FAILED
     * @see StreamerConstants#KSY_STREAMER_ERROR_PUBLISH_FAILED
     * @see StreamerConstants#KSY_STREAMER_ERROR_AV_ASYNC
     */
    public void setEnableAutoRestart(boolean enable, int interval) {
        mAutoRestart = enable;
        mAutoRestartInterval = interval;
    }

    public boolean getEnableAutoRestart() {
        return mAutoRestart;
    }

    /**
     * Set stat info upstreaming log.
     *
     * @param listener listener
     */
    public void setOnLogEventListener(StatsLogReport.OnLogEventListener listener) {
        StatsLogReport.getInstance().setOnLogEventListener(listener);
    }

    /**
     * Set if enable stat info upstreaming.
     *
     * @param enableStreamStatModule true to enable, false to disable.
     */
    public void setEnableStreamStatModule(boolean enableStreamStatModule) {
        mEnableStreamStatModule = enableStreamStatModule;
        StatsLogReport.getInstance().setIsPermitLogReport(mEnableStreamStatModule);
    }

    /**
     * Set and show watermark logo both on preview and stream. Support jpeg, png.
     *
     * @param path  logo file path.
     *              prefix "file://" for absolute path,
     *              and prefix "assets://" for image resource in assets folder.
     * @param x     x position for left top of logo relative to the video, between 0~1.0.
     * @param y     y position for left top of logo relative to the video, between 0~1.0.
     * @param w     width of logo relative to the video, between 0~1.0, if set to 0,
     *              width would be calculated by h and logo image radio.
     * @param h     height of logo relative to the video, between 0~1.0, if set to 0,
     *              height would be calculated by w and logo image radio.
     * @param alpha alpha value，between 0~1.0
     */
    public void showWaterMarkLogo(String path, float x, float y, float w, float h, float alpha) {
        alpha = Math.max(0.0f, alpha);
        alpha = Math.min(alpha, 1.0f);
        mImgTexMixer.setRenderRect(1, x, y, w, h, alpha);
        mVideoEncoderMgt.getImgBufMixer().setRenderRect(1, x, y, w, h, alpha);
        mWaterMarkCapture.showLogo(mContext, path, w, h);
    }

    /**
     * Hide watermark logo.
     */
    public void hideWaterMarkLogo() {
        mWaterMarkCapture.hideLogo();
    }

    /**
     * Set and show timestamp both on preview and stream.
     *
     * @param x     x position for left top of timestamp relative to the video, between 0~1.0.
     * @param y     y position for left top of timestamp relative to the video, between 0~1.0.
     * @param w     width of timestamp relative to the video, between 0-1.0,
     *              the height would be calculated automatically.
     * @param color color of timestamp, in ARGB.
     * @param alpha alpha of timestamp，between 0~1.0.
     */
    public void showWaterMarkTime(float x, float y, float w, int color, float alpha) {
        alpha = Math.max(0.0f, alpha);
        alpha = Math.min(alpha, 1.0f);
        mImgTexMixer.setRenderRect(2, x, y, w, 0, alpha);
        mVideoEncoderMgt.getImgBufMixer().setRenderRect(2, x, y, w, 0, alpha);
        mWaterMarkCapture.showTime(color, "yyyy-MM-dd HH:mm:ss", w, 0);
    }

    /**
     * Hide timestamp watermark.
     */
    public void hideWaterMarkTime() {
        mWaterMarkCapture.hideTime();
    }

    /**
     * Get current sdk version.
     *
     * @return version number as 1.0.0.0
     */
    public static String getVersion() {
        return StatsConstant.SDK_VERSION_SUB_VALUE;
    }

    /**
     * Release all resources used by KSYStreamer.
     */
    public void release() {
        if (mMainHandler != null) {
            mMainHandler.removeCallbacksAndMessages(null);
            mMainHandler = null;
        }

        mCameraCapture.release();
        mAudioCapture.release();
        mAudioDummyCapture.release();
        mWaterMarkCapture.release();
        mAudioPlayerCapture.release();
        mGLRender.release();
        setOnLogEventListener(null);
    }

    /**
     * request screen shot with resolution of the screen
     *
     * @param screenShotListener the listener to be called when bitmap of the screen shot available
     */
    public void requestScreenShot(GLRender.ScreenShotListener screenShotListener) {
        mImgTexMixer.requestScreenShot(screenShotListener);
    }

    /**
     * request screen shot with scale factor
     *
     * @param scaleFactor        the scale factor of the bitmap, between 0~1.0.
     * @param screenShotListener the listener to be called when bitmap of the screen shot available
     */
    public void requestScreenShot(float scaleFactor, GLRender.ScreenShotListener screenShotListener) {
        mImgTexMixer.requestScreenShot(scaleFactor, screenShotListener);
    }

    private GLRender.GLRenderListener mGLRenderListener = new GLRender.GLRenderListener() {
        @Override
        public void onReady() {
        }

        @Override
        public void onSizeChanged(int width, int height) {
            mScreenRenderWidth = width;
            mScreenRenderHeight = height;
            if (mDelayedStartCameraPreview) {
                setPreviewParams();
                mCameraCapture.start(mCameraFacing);
                mDelayedStartCameraPreview = false;
            }
        }

        @Override
        public void onDrawFrame() {
        }

        @Override
        public void onReleased() {
        }
    };

    private void autoRestart() {
        if (mAutoRestart) {
            if (mMainHandler != null) {
                stopStream();
                mMainHandler.postDelayed(new Runnable() {
                    @Override
                    public void run() {
                        startStream();
                    }
                }, mAutoRestartInterval);
            }
        }
    }

    //faceunity
    public ImgFaceunityFilter getFaceuintyFilter() {
        return mFaceunityFilter;
    }

    /**
     * 连接buffer数据，用于人脸识别
     */
    public void showFaceunityProp() {
        mEnableFaceunityProp = true;
        mCameraCapture.mImgBufSrcPin.connect(mFaceunityScale.getSinkPin());
    }

    /**
     * 没有开启贴纸功能时disconnect，否则浪费cpu
     */
    public void hideFaceunityProp() {
        mEnableFaceunityProp = false;
        disconnectFaceunityData();
    }

    public void showFaceunityGesture() {
        mEnableFaceunityGesture = true;
        mCameraCapture.mImgBufSrcPin.connect(mFaceunityScale.getSinkPin());
    }

    public void hideFaceunityGesture() {
        mEnableFaceunityGesture = false;
        disconnectFaceunityData();
    }

    private void disconnectFaceunityData() {
        if (mEnableFaceunityProp || mEnableFaceunityGesture) {
            return;
        }

        mCameraCapture.mImgBufSrcPin.disconnect(mFaceunityScale.getSinkPin(), false);
    }
}
