package me.ccrama.redditslide.Views;

import android.annotation.TargetApi;
import android.app.ActivityManager;
import android.content.Context;
import android.graphics.Matrix;
import android.graphics.PointF;
import android.media.MediaPlayer;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.support.annotation.IntRange;
import android.support.annotation.NonNull;
import android.support.v4.view.animation.FastOutLinearInInterpolator;
import android.support.v4.view.animation.LinearOutSlowInInterpolator;
import android.util.AttributeSet;
import android.util.Log;
import android.view.KeyEvent;
import android.view.MotionEvent;
import android.view.ScaleGestureDetector;
import android.view.View;
import android.view.animation.AlphaAnimation;
import android.view.animation.Animation;
import android.view.animation.AnimationSet;
import android.view.animation.TranslateAnimation;
import android.widget.LinearLayout;
import android.widget.SeekBar;

import com.devbrackets.android.exomedia.listener.OnBufferUpdateListener;
import com.devbrackets.android.exomedia.listener.OnCompletionListener;
import com.devbrackets.android.exomedia.listener.OnErrorListener;
import com.devbrackets.android.exomedia.listener.OnPreparedListener;
import com.devbrackets.android.exomedia.listener.OnVideoSizeChangedListener;
import com.devbrackets.android.exomedia.ui.animation.BottomViewHideShowAnimation;
import com.devbrackets.android.exomedia.ui.animation.TopViewHideShowAnimation;
import com.devbrackets.android.exomedia.ui.widget.VideoControls;
import com.devbrackets.android.exomedia.ui.widget.VideoView;
import com.devbrackets.android.exomedia.util.TimeFormatUtil;
import com.google.android.exoplayer2.source.dash.DashMediaSource;
import com.google.android.exoplayer2.source.dash.DefaultDashChunkSource;
import com.google.android.exoplayer2.upstream.DataSource;
import com.google.android.exoplayer2.upstream.DefaultBandwidthMeter;
import com.google.android.exoplayer2.upstream.DefaultDataSourceFactory;
import com.google.android.exoplayer2.upstream.DefaultHttpDataSourceFactory;
import com.google.android.exoplayer2.upstream.FileDataSource;
import com.google.android.exoplayer2.upstream.cache.CacheDataSink;
import com.google.android.exoplayer2.upstream.cache.CacheDataSource;
import com.google.android.exoplayer2.upstream.cache.LeastRecentlyUsedCacheEvictor;
import com.google.android.exoplayer2.upstream.cache.SimpleCache;
import com.google.android.exoplayer2.util.Util;

import java.io.File;
import java.net.URL;
import java.util.LinkedList;
import java.util.List;

import me.ccrama.redditslide.R;
import me.ccrama.redditslide.util.LogUtil;

/**
 * Created by vishna on 22/07/15.
 */
//
//VideoView
//
//
//Created by Alex Ross on 1/29/13
//Modified to accept a Matrix by Wiseman Designs
//

public class MediaVideoView extends VideoView {

    static final         int    NONE                     = 0;
    static final         int    DRAG                     = 1;
    static final         int    ZOOM                     = 2;
    static final         int    CLICK                    = 3;
    private static final String LOG_TAG                  = "VideoView";
    // all possible internal states
    private static final int    STATE_ERROR              = -1;
    private static final int    STATE_IDLE               = 0;
    private static final int    STATE_PREPARING          = 1;
    private static final int    STATE_PREPARED           = 2;
    private static final int    STATE_PLAYING            = 3;
    private static final int    STATE_PAUSED             = 4;
    private static final int    STATE_PLAYBACK_COMPLETED = 5;
    public int number;
    int    mode   = NONE;
    Matrix matrix = new Matrix();
    ScaleGestureDetector mScaleDetector;
    float minScale = 1f;
    float maxScale = 5f;
    float[] m;
    PointF last  = new PointF();
    PointF start = new PointF();
    float redundantXSpace, redundantYSpace;
    float width, height;
    float saveScale = 1f;
    float right, bottom, origWidth, origHeight, bmWidth, bmHeight;
    OnPreparedListener mOnPreparedListener;
    float              lastFocusX;
    float              lastFocusY;

    // currentState is a VideoView object's current state.
    // targetState is the state that a method caller intends to reach.
    // For instance, regardless the VideoView object's current state,
    // calling pause() intends to bring the object to a target state
    // of STATE_PAUSED.
    private int currentState = STATE_IDLE;
    private int targetState  = STATE_IDLE;
    // Stuff we need for playing and showing a video
    private int                              surfaceWidth;
    private int                              surfaceHeight;
    private MediaPlayer.OnCompletionListener onCompletionListener;
    private MediaPlayer.OnPreparedListener   onPreparedListener;
    private int                              currentBufferPercentage;
    private MediaPlayer.OnErrorListener      onErrorListener;
    private MediaPlayer.OnInfoListener       onInfoListener;
    private int                              mSeekWhenPrepared;
    private int                              mSeekMode;
    // recording the seek position while preparing
    private boolean                          mCanPause;
    private boolean                          mCanSeekBack;
    private boolean                          mCanSeekForward;
    private Uri                              uri;
    //scale stuff
    private float widthScale  = 1.0f;
    private float heightScale = 1.0f;
    private Context mContext;
    private int     mAudioSession;
    // Listeners
    private OnBufferUpdateListener bufferingUpdateListener = new OnBufferUpdateListener() {
        @Override
        public void onBufferingUpdate(int percent) {
            currentBufferPercentage = percent;
        }
    };
    private OnPreparedListener     preparedListener        = new OnPreparedListener() {
        @Override
        public void onPrepared() {
            currentState = STATE_PREPARED;
            LogUtil.v("Video prepared for " + number);


            mCanPause = mCanSeekBack = mCanSeekForward = true;

            if (mOnPreparedListener != null) {
                mOnPreparedListener.onPrepared();
            }

            requestLayout();
            invalidate();
            if (targetState == STATE_PLAYING) {
                start();
            }
        }
    };

    private OnVideoSizeChangedListener videoSizeChangedListener = new OnVideoSizeChangedListener() {
        @Override
        public void onVideoSizeChanged(final int width, final int height) {
            LogUtil.v("Video size changed " + width + '/' + height + " number " + number);
        }
    };
    private OnErrorListener            errorListener            = new OnErrorListener() {
        @Override
        public boolean onError(Exception e) {
            currentState = STATE_ERROR;
            targetState = STATE_ERROR;
            Log.e(LOG_TAG, "There was an error during video playback.");
            return true;
        }
    };


    public MediaVideoView(final Context context) {
        super(context);
        mContext = context;
        initVideoView();
    }

    public MediaVideoView(final Context context, final AttributeSet attrs) {
        super(context, attrs);
        mContext = context;
        initVideoView();
    }

    public MediaVideoView(Context context, AttributeSet attrs, int defStyle) {
        super(context, attrs, defStyle);
        mContext = context;
        initVideoView();
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        boolean isKeyCodeSupported = keyCode != KeyEvent.KEYCODE_BACK
                && keyCode != KeyEvent.KEYCODE_VOLUME_UP
                && keyCode != KeyEvent.KEYCODE_VOLUME_DOWN
                && keyCode != KeyEvent.KEYCODE_VOLUME_MUTE
                && keyCode != KeyEvent.KEYCODE_MENU
                && keyCode != KeyEvent.KEYCODE_CALL
                && keyCode != KeyEvent.KEYCODE_ENDCALL;
        if (isPlaying() && isKeyCodeSupported) {
            if (keyCode == KeyEvent.KEYCODE_HEADSETHOOK
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PLAY_PAUSE) {
                if (isPlaying()) {
                    pause();
                    getVideoControls().show();
                } else {
                    start();
                    getVideoControls().hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_PLAY) {
                if (!isPlaying()) {
                    start();
                    getVideoControls().hide();
                }
                return true;
            } else if (keyCode == KeyEvent.KEYCODE_MEDIA_STOP
                    || keyCode == KeyEvent.KEYCODE_MEDIA_PAUSE) {
                if (isPlaying()) {
                    pause();
                    getVideoControls().show();
                }
                return true;
            } else {
                toggleMediaControlsVisiblity();
            }
        }

        return super.onKeyDown(keyCode, event);
    }

    @Override
    public boolean onTrackballEvent(MotionEvent ev) {
        if (isPlaying()) {
            toggleMediaControlsVisiblity();
        }
        return false;
    }

    @Override
    public boolean onTouchEvent(MotionEvent ev) {
        if (isPlaying() && ev.getAction() == MotionEvent.ACTION_UP) {
            toggleMediaControlsVisiblity();
        }
        return true;
    }


    @Override
    public int getBufferPercentage() {
        return currentBufferPercentage;
    }


    public void initVideoView() {
        LogUtil.v("Initializing video view.");
        setAlpha(0);
        setFocusable(false);

        if (Build.VERSION.SDK_INT >= 26) {
            ActivityManager am =
                    (ActivityManager) mContext.getSystemService(Context.ACTIVITY_SERVICE);

            // Only seek to seek-points if low ram device, otherwise seek to frame
            if (am.isLowRamDevice()) {
                LogUtil.d("MediaVideoView: using SEEK_CLOSEST_SYNC (low ram device)");
                mSeekMode = MediaPlayer.SEEK_CLOSEST_SYNC;
            } else {
                LogUtil.d("MediaVideoView: using SEEK_CLOSEST");
                mSeekMode = MediaPlayer.SEEK_CLOSEST;
            }
        } else {
            LogUtil.d("MediaVideoView: using SEEK_PREVIOUS_SYNC (API<26)");
        }
    }

    public void openVideo() {
        if ((uri == null) && dashURL == null) {
            LogUtil.v("Cannot open video, uri or surface is null number " + number);
            return;
        }
        animate().alpha(1);

        // Tell the music playback service to pause

        try {
            setHandleAudioFocus(false);
            attachMediaControls();
            setOnBufferUpdateListener(bufferingUpdateListener);
            setOnPreparedListener(preparedListener);
            setOnErrorListener(errorListener);
            setOnCompletionListener(new OnCompletionListener() {
                @Override
                public void onCompletion() {
                    restart();
                }
            });
            setKeepScreenOn(true);
            setOnVideoSizedChangedListener(videoSizeChangedListener);
            if(dashURL == null) {
                setVideoURI(uri, null);
            } else {
                DataSource.Factory dataSourceFactory = new CacheDataSourceFactory(getContext(), 100 * 1024 * 1024, 5 * 1024 * 1024);
                DashMediaSource video = new DashMediaSource(Uri.parse(dashURL.toString()), dataSourceFactory, new DefaultDashChunkSource.Factory(dataSourceFactory), null, null);
                setVideoURI(uri, video);
            }
            LogUtil.v("Preparing media player.");
            currentState = STATE_PREPARING;
        } catch (IllegalStateException e) {
            currentState = STATE_ERROR;
            targetState = STATE_ERROR;
            e.printStackTrace();
        }
    }

    public void attachMediaControls() {
        setControls(new SlideVideoControls(mContext));
    }

    public int resolveAdjustedSize(int desiredSize, int measureSpec) {
        LogUtil.v("Resolve called.");
        int result = desiredSize;
        int specMode = MeasureSpec.getMode(measureSpec);
        int specSize = MeasureSpec.getSize(measureSpec);

        switch (specMode) {
            case MeasureSpec.UNSPECIFIED:
             /* Parent says we can be as big as we want. Just don't be larger
              * than max size imposed on ourselves.
              */
                result = desiredSize;
                break;

            case MeasureSpec.AT_MOST:
             /* Parent says we can be as big as we want, up to specSize.
              * Don't be larger than specSize, and don't be larger than
              * the max size imposed on ourselves.
              */
                result = Math.min(desiredSize, specSize);
                break;

            case MeasureSpec.EXACTLY:
                // No choice. Do what we are told.
                result = specSize;
                break;
        }
        return result;
    }

    public void resume() {
        openVideo();
    }

    public void setOnPreparedListener(OnPreparedListener onPreparedListener) {
        this.mOnPreparedListener = onPreparedListener;
    }

    URL dashURL;

    public void setVideoDASH(URL url) {
        dashURL = url;
        openVideo();
        requestLayout();
        invalidate();
    }

    public void setVideoPath(String path) {
        LogUtil.v("Setting video path to: " + path);
        setVideoURI(Uri.parse(path));
    }

    public void setVideoURI(Uri _videoURI) {
        uri = _videoURI;
        openVideo();
        requestLayout();
        invalidate();
    }

    private void toggleMediaControlsVisiblity() {
        if (getVideoControls().isVisible()) {
            getVideoControls().hide();
        } else {
            getVideoControls().show();
        }
    }
}

class SlideVideoControls extends VideoControls {
    protected SeekBar      seekBar;
    protected LinearLayout extraViewsContainer;

    protected boolean userInteracting = false;

    public SlideVideoControls(Context context) {
        super(context);
    }

    public SlideVideoControls(Context context, AttributeSet attrs) {
        super(context, attrs);
    }

    public SlideVideoControls(Context context, AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
    }

    @TargetApi(Build.VERSION_CODES.LOLLIPOP)
    public SlideVideoControls(Context context, AttributeSet attrs, int defStyleAttr,
            int defStyleRes) {
        super(context, attrs, defStyleAttr, defStyleRes);
    }

    @Override
    protected int getLayoutResource() {
        return R.layout.media_controls;
    }

    @Override
    public void setPosition(@IntRange(from = 0) long position) {
        currentTimeTextView.setText(TimeFormatUtil.formatMs(position));
        seekBar.setProgress((int) position);
    }

    @Override
    public void setDuration(@IntRange(from = 0) long duration) {
        if (duration != seekBar.getMax()) {
            endTimeTextView.setText(TimeFormatUtil.formatMs(duration));
            seekBar.setMax((int) duration);
        }
    }

    @Override
    public void updateProgress(@IntRange(from = 0) long position, @IntRange(from = 0) long duration,
            @IntRange(from = 0, to = 100) int bufferPercent) {
        if (!userInteracting) {
            seekBar.setSecondaryProgress((int) (seekBar.getMax() * ((float) bufferPercent / 100)));
            seekBar.setProgress((int) position);
            currentTimeTextView.setText(TimeFormatUtil.formatMs(position));
        }
    }

    @Override
    protected void retrieveViews() {
        super.retrieveViews();
        seekBar = findViewById(com.devbrackets.android.exomedia.R.id.exomedia_controls_video_seek);
        extraViewsContainer = findViewById(
                com.devbrackets.android.exomedia.R.id.exomedia_controls_extra_container);
    }

    @Override
    protected void registerListeners() {
        super.registerListeners();
        seekBar.setOnSeekBarChangeListener(new SlideVideoControls.SeekBarChanged());
    }

    @Override
    public void addExtraView(@NonNull View view) {
        extraViewsContainer.addView(view);
    }

    @Override
    public void removeExtraView(@NonNull View view) {
        extraViewsContainer.removeView(view);
    }

    @NonNull
    @Override
    public List<View> getExtraViews() {
        int childCount = extraViewsContainer.getChildCount();
        if (childCount <= 0) {
            return super.getExtraViews();
        }

        //Retrieves the layouts children
        List<View> children = new LinkedList<>();
        for (int i = 0; i < childCount; i++) {
            children.add(extraViewsContainer.getChildAt(i));
        }

        return children;
    }

    @Override
    public void hideDelayed(long delay) {
        hideDelay = delay;

        LogUtil.v("Hiding delayed");

        if (delay < 0 || !canViewHide || isLoading ) {
            return;
        }

        //If the user is interacting with controls we don't want to start the delayed hide yet
        if (!userInteracting) {
            visibilityHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    animateVisibility(false);
                }
            }, delay);
        }
    }

    @Override
    protected void animateVisibility(boolean toVisible) {
        if (isVisible == toVisible) {
            return;
        }

        if (!hideEmptyTextContainer || !isTextContainerEmpty()) {
            textContainer.startAnimation(new FadeInAnimation(textContainer, toVisible,
                    CONTROL_VISIBILITY_ANIMATION_LENGTH));
        }

        if (!isLoading) {
            controlsContainer.startAnimation(
                    new FadeInAnimation(controlsContainer, toVisible,
                            CONTROL_VISIBILITY_ANIMATION_LENGTH));
        }

        isVisible = toVisible;
        onVisibilityChanged();
    }

    @Override
    protected void updateTextContainerVisibility() {
        if (!isVisible) {
            return;
        }

        boolean emptyText = isTextContainerEmpty();
        if (hideEmptyTextContainer && emptyText && textContainer.getVisibility() == VISIBLE) {
            textContainer.clearAnimation();
            textContainer.startAnimation(new FadeInAnimation(textContainer, false,
                    CONTROL_VISIBILITY_ANIMATION_LENGTH));
        } else if ((!hideEmptyTextContainer || !emptyText)
                && textContainer.getVisibility() != VISIBLE) {
            textContainer.clearAnimation();
            textContainer.startAnimation(new FadeInAnimation(textContainer, true,
                    CONTROL_VISIBILITY_ANIMATION_LENGTH));
        }
    }

    @Override
    public void showLoading(boolean initialLoad) {
        if (isLoading) {
            return;
        }

        isLoading = true;
        loadingProgressBar.setVisibility(View.GONE);

        if (initialLoad) {
            controlsContainer.setVisibility(View.GONE);
        } else {
            playPauseButton.setEnabled(false);
            previousButton.setEnabled(false);
            nextButton.setEnabled(false);
        }
    }

    @Override
    public void finishLoading() {
        if (!isLoading) {
            return;
        }

        isLoading = false;
        loadingProgressBar.setVisibility(View.GONE);


        playPauseButton.setEnabled(true);
        previousButton.setEnabled(true);
        nextButton.setEnabled(true);

        updatePlaybackState(true);
        hide();
        final Handler handler = new Handler();
        handler.postDelayed(new Runnable() {
            @Override
            public void run() {
                controlsContainer.setVisibility(View.VISIBLE);
            }
        }, 600);
    }

    @Override
    public void updatePlaybackState(boolean isPlaying) {
        updatePlayPauseImage(isPlaying);
        progressPollRepeater.start();

        if (isPlaying) {
        } else {
            show();
        }
    }

    /**
     * Listens to the seek bar change events and correctly handles the changes
     */
    protected class SeekBarChanged implements SeekBar.OnSeekBarChangeListener {
        private long seekToTime;

        @Override
        public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
            if (!fromUser) {
                return;
            }

            seekToTime = progress;
            if (currentTimeTextView != null) {
                currentTimeTextView.setText(TimeFormatUtil.formatMs(seekToTime));
            }
        }

        @Override
        public void onStartTrackingTouch(SeekBar seekBar) {
            userInteracting = true;
            if (seekListener == null || !seekListener.onSeekStarted()) {
                internalListener.onSeekStarted();
            }
        }

        @Override
        public void onStopTrackingTouch(SeekBar seekBar) {
            userInteracting = false;
            if (seekListener == null || !seekListener.onSeekEnded(seekToTime)) {
                internalListener.onSeekEnded(seekToTime);
            }
        }
    }
}

class CacheDataSourceFactory implements DataSource.Factory {
    private final Context context;
    private final DefaultDataSourceFactory defaultDatasourceFactory;
    private final long maxFileSize, maxCacheSize;

    CacheDataSourceFactory(Context context, long maxCacheSize, long maxFileSize) {
        super();
        this.context = context;
        this.maxCacheSize = maxCacheSize;
        this.maxFileSize = maxFileSize;
        String userAgent = Util.getUserAgent(context, context.getString(R.string.app_name));
        DefaultBandwidthMeter bandwidthMeter = new DefaultBandwidthMeter();
        defaultDatasourceFactory = new DefaultDataSourceFactory(this.context,
                bandwidthMeter,
                new DefaultHttpDataSourceFactory(userAgent, bandwidthMeter));
    }

    @Override
    public DataSource createDataSource() {
        LeastRecentlyUsedCacheEvictor evictor = new LeastRecentlyUsedCacheEvictor(maxCacheSize);
        SimpleCache simpleCache = new SimpleCache(new File(context.getCacheDir(), "media"), evictor);
        return new CacheDataSource(simpleCache, defaultDatasourceFactory.createDataSource(),
                new FileDataSource(), new CacheDataSink(simpleCache, maxFileSize),
                CacheDataSource.FLAG_BLOCK_ON_CACHE | CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR, null);
    }
}

class FadeInAnimation extends AnimationSet {
    private View animationView;
    private boolean toVisible;

    public FadeInAnimation(View view, boolean toVisible, long duration) {
        super(false);
        this.toVisible = toVisible;
        this.animationView = view;

        //Creates the Alpha animation for the transition
        float startAlpha = toVisible ? 0 : 1;
        float endAlpha = toVisible ? 1 : 0;

        AlphaAnimation alphaAnimation = new AlphaAnimation(startAlpha, endAlpha) ;
        alphaAnimation.setDuration(duration);


        addAnimation(alphaAnimation);

        setAnimationListener(new FadeInAnimation.Listener());
    }

    private class Listener implements AnimationListener {

        @Override
        public void onAnimationStart(Animation animation) {
            animationView.setVisibility(View.VISIBLE);
        }

        @Override
        public void onAnimationEnd(Animation animation) {
            animationView.setVisibility(toVisible ? View.VISIBLE : View.GONE);
        }

        @Override
        public void onAnimationRepeat(Animation animation) {
            //Purposefully left blank
        }
    }
}