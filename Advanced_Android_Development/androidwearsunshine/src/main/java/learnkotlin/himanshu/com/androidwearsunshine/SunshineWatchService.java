package learnkotlin.himanshu.com.androidwearsunshine;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.res.Resources;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.os.Bundle;
import android.os.Handler;
import android.os.Message;
import android.support.v4.content.LocalBroadcastManager;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.lang.ref.WeakReference;
import java.text.DateFormat;
import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

import static learnkotlin.himanshu.com.androidwearsunshine.WeatherDataService.ACTION_WEATHER_DATA;
import static learnkotlin.himanshu.com.androidwearsunshine.WeatherDataService.HIGHT_TEMP;
import static learnkotlin.himanshu.com.androidwearsunshine.WeatherDataService.LOW_TEMP;
import static learnkotlin.himanshu.com.androidwearsunshine.WeatherDataService.WEATHER_ASSET;

public class SunshineWatchService extends CanvasWatchFaceService {

    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);

    /**
     * Update rate in milliseconds for interactive mode. We update once a second since seconds are
     * displayed in interactive mode.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);

    /**
     * Handler message id for updating the time periodically in interactive mode.
     */
    private static final int MSG_UPDATE_TIME = 0;
    private static final String SHARED_PREF_NAME = "Sunshine";
    private static final int SHARED_PREF_MODE = Context.MODE_PRIVATE;
    private static final String WEATHER_CACHE_FILE_NAME = "weather";
    private static SharedPreferences mSharedPreferences;

    @Override
    public Engine onCreateEngine() {
        mSharedPreferences =
                getSharedPreferences(SHARED_PREF_NAME, SHARED_PREF_MODE);
        return new Engine();
    }

    private static class EngineHandler extends Handler {
        private final WeakReference<SunshineWatchService.Engine> mWeakReference;

        public EngineHandler(SunshineWatchService.Engine reference) {
            mWeakReference = new WeakReference<>(reference);
        }

        @Override
        public void handleMessage(Message msg) {
            SunshineWatchService.Engine engine = mWeakReference.get();
            if (engine != null) {
                switch (msg.what) {
                    case MSG_UPDATE_TIME:
                        engine.handleUpdateTimeMessage();
                        break;
                }
            }
        }
    }

    private class Engine extends CanvasWatchFaceService.Engine {
        final Handler mUpdateTimeHandler = new EngineHandler(this);
        boolean mRegisteredTimeZoneReceiver = false;
        Paint mBackgroundPaint;
        Paint mTimePaint, mDatePaint, mTempPaint;
        boolean mAmbient;
        Calendar mCalendar;
        private double lowTemp, highTemp;

        final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                if (intent.getAction().equals(ACTION_WEATHER_DATA)) {
                    lowTemp = intent.getLongExtra(LOW_TEMP, 0l) ;
                    highTemp = intent.getLongExtra(HIGHT_TEMP, 0l);
                    weather = (Bitmap) intent.getParcelableExtra(WEATHER_ASSET);

                    mSharedPreferences.edit()
                            .putLong(LOW_TEMP, Double.doubleToRawLongBits(lowTemp))
                            .putLong(HIGHT_TEMP, Double.doubleToRawLongBits(maxTemp))
                            .apply();

                    if (weather != null) {
                        try {
                            FileOutputStream fileOutputStream = openFileOutput(
                                    WEATHER_CACHE_FILE_NAME, Context.MODE_PRIVATE);
                            weather.compress(Bitmap.CompressFormat.PNG, 100, fileOutputStream);
                        } catch (FileNotFoundException e) {
                            e.printStackTrace();
                        }
                    }
                    Log.e("onReceive: ", lowTemp + " " + highTemp);
                    invalidate();
                }
                invalidate();
            }
        };
        float mXOffset;
        float mYOffset;

        private double maxTemp, minTemp;
        private Bitmap weather;

        /**
         * Whether the display supports fewer bits for each color in ambient mode. When true, we
         * disable anti-aliasing in ambient mode.
         */
        boolean mLowBitAmbient;

        @Override
        public void onCreate(SurfaceHolder holder) {
            super.onCreate(holder);

            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchService.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_VARIABLE)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .setAcceptsTapEvents(true)
                    .build());

            highTemp = Double.longBitsToDouble(mSharedPreferences.
                    getLong(HIGHT_TEMP, Double.doubleToRawLongBits(0)));
            lowTemp = Double.longBitsToDouble(mSharedPreferences
                    .getLong(LOW_TEMP, Double.doubleToRawLongBits(0)));
            try {
                weather = BitmapFactory.decodeStream(
                        openFileInput(WEATHER_CACHE_FILE_NAME));
            } catch (FileNotFoundException e) {
                e.printStackTrace();
            }

            Resources resources = SunshineWatchService.this.getResources();
            mYOffset = resources.getDimension(R.dimen.digital_y_offset);

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(resources.getColor(R.color.primary_dark));

            mTimePaint = new Paint();
            mTimePaint = createTextPaint(resources.getColor(R.color.digital_text));

            mDatePaint = new Paint();
            mDatePaint = createTextPaint(resources.getColor(R.color.white));

            mTempPaint = new Paint();
            mTempPaint = createTextPaint(resources.getColor(R.color.white_light));

            mCalendar = Calendar.getInstance();
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        private Paint createTextPaint(int textColor) {
            Paint paint = new Paint();
            paint.setColor(textColor);
            paint.setTypeface(NORMAL_TYPEFACE);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                registerReceiver();

                // Update time zone in case it changed while we weren't visible.
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            } else {
                unregisterReceiver();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            LocalBroadcastManager.getInstance(SunshineWatchService.this).registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            LocalBroadcastManager.getInstance(SunshineWatchService.this).unregisterReceiver(mTimeZoneReceiver);
        }

        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);

            // Load resources that have alternate values for round watches.
            Resources resources = SunshineWatchService.this.getResources();
            boolean isRound = insets.isRound();
            mXOffset = resources.getDimension(isRound
                    ? R.dimen.digital_x_offset_round : R.dimen.digital_x_offset);
            float timeTextSize = resources.getDimension(isRound
                    ?  R.dimen.digital_time_size_round : R.dimen.digital_time_size);
            float dateTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_date_size_round : R.dimen.digital_date_size);
            float tempTextSize = resources.getDimension(isRound
                    ? R.dimen.digital_temp_size_round : R.dimen.digital_temp_size);

            mTimePaint.setTextSize(timeTextSize);
            mDatePaint.setTextSize(dateTextSize);
            mTempPaint.setTextSize(tempTextSize);
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (mAmbient != inAmbientMode) {
                mAmbient = inAmbientMode;
                if (mLowBitAmbient) {
                    mTimePaint.setAntiAlias(!inAmbientMode);
                    mDatePaint.setAntiAlias(!inAmbientMode);
                    mTempPaint.setAntiAlias(!inAmbientMode);
                }
                invalidate();
            }

            // Whether the timer should be running depends on whether we're visible (as well as
            // whether we're in ambient mode), so we may need to start or stop the timer.
            updateTimer();
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {
            // Draw the background.
            if (isInAmbientMode()) {
                canvas.drawColor(Color.BLACK);
            } else {
                canvas.drawRect(0, 0, bounds.width(), bounds.height(), mBackgroundPaint);
                if (weather != null) {
                    canvas.drawBitmap(weather,
                            ((canvas.getWidth() - weather.getWidth()) / 2),
                            mYOffset +
                                    mTimePaint.descent() - mDatePaint.ascent() +
                                    mDatePaint.descent() - mTempPaint.ascent() +
                                    mTempPaint.descent() - mTempPaint.ascent(),
                            null);
                }
            }

            // Draw H:MM in ambient mode or H:MM:SS in interactive mode.
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            String time = mAmbient
                    ? String.format("%d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE))
                    : String.format("%d:%02d:%02d", mCalendar.get(Calendar.HOUR),
                    mCalendar.get(Calendar.MINUTE), mCalendar.get(Calendar.SECOND));
            Rect r = new Rect();
            mTimePaint.getTextBounds(time, 0, time.length(), r);
            canvas.drawText(time,
                    ((canvas.getWidth() - r.width()) / 2),
                    mYOffset,
                    mTimePaint);

            DateFormat dateFormat = new SimpleDateFormat("EEE, MMM d yyyy");
            String date = dateFormat.format(mCalendar.getTime()).toUpperCase();
            mDatePaint.getTextBounds(date, 0, date.length(), r);
            canvas.drawText(date,
                    ((canvas.getWidth() - r.width()) / 2),
                    mYOffset + mTimePaint.descent() - mDatePaint.ascent(),
                    mDatePaint);

            String temp = String.format("%s\u00B0 %s\u00B0", highTemp, lowTemp);
            mTempPaint.getTextBounds(temp, 0, temp.length(), r);
            canvas.drawText(temp,
                    ((canvas.getWidth() - r.width()) / 2),
                    mYOffset + mTimePaint.descent() - mDatePaint.ascent() +
                            mDatePaint.descent() - mTempPaint.ascent(),
                    mTempPaint);
        }

        /**
         * Starts the {@link #mUpdateTimeHandler} timer if it should be running and isn't currently
         * or stops it if it shouldn't be running but currently is.
         */
        private void updateTimer() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer should
         * only run when we're visible and in interactive mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !isInAmbientMode();
        }

        /**
         * Handle updating the time periodically in interactive mode.
         */
        private void handleUpdateTimeMessage() {
            invalidate();
            if (shouldTimerBeRunning()) {
                long timeMs = System.currentTimeMillis();
                long delayMs = INTERACTIVE_UPDATE_RATE_MS
                        - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
            }
        }
    }
}

