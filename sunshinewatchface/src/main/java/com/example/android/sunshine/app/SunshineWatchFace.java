/*
 * Copyright (C) 2014 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.android.sunshine.app;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ColorMatrix;
import android.graphics.ColorMatrixColorFilter;
import android.graphics.Paint;
import android.graphics.Rect;
import android.graphics.Typeface;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.os.HandlerThread;
import android.os.Message;
import android.provider.ContactsContract;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v7.graphics.Palette;
import android.support.wearable.watchface.CanvasWatchFaceService;
import android.support.wearable.watchface.WatchFaceService;
import android.support.wearable.watchface.WatchFaceStyle;
import android.util.Log;
import android.view.SurfaceHolder;
import android.view.WindowInsets;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageApi;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;

import java.util.Calendar;
import java.util.Collection;
import java.util.HashSet;
import java.util.TimeZone;
import java.util.concurrent.TimeUnit;

/**
 * Sample analog watch face with a ticking second hand. In ambient mode, the second hand isn't
 * shown. On devices with low-bit ambient mode, the hands are drawn without anti-aliasing in ambient
 * mode. The watch face is drawn with less contrast in mute mode.
 *
 *
 */
public class SunshineWatchFace extends CanvasWatchFaceService {
    private static final String TAG = "SunshineWatchFace";

    /*
     * Update rate in milliseconds for interactive mode. We update once a second to advance the
     * second hand.
     */
    private static final long INTERACTIVE_UPDATE_RATE_MS = TimeUnit.SECONDS.toMillis(1);
    private static final Typeface NORMAL_TYPEFACE =
            Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL);
    private static final String REQUEST_WEATHER_DATA = "/request_weather";
    private static final String MAX_TEMP = "MAX_TEMP";
    private static final String MIN_TEMP = "MIN_TEMP";
    private static final String WEATHER_ID = "WEATHER_ID";
    private Paint mWeatherLowTempPaint;
    private GoogleApiClient mGoogleApiClient;

    private static final String RESPONSE_WEATHER_MAX_TEMP = "RESPONSE_WEATHER_MAX";
    private static final String RESPONSE_WEATHER_MIN_TEMP = "RESPONSE_WEATHER_MIN";
    private static final String RESPONSE_WEATHER_CONDITION_ID = "RESPONSE_WEATHER_CONDITION_ID";
    private static final String RESPONSE_WEATHER_PATH = "/response_weather_path";
    private SunshineWatchFace mContext;

    @Override
    public Engine onCreateEngine() {
        Log.d(TAG,"onCreateEngine");
        mContext = this;
        return new Engine();
    }

    private class Engine extends CanvasWatchFaceService.Engine implements DataApi.DataListener,
            GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
        private static final int MSG_UPDATE_TIME = 0;

        private static final float HOUR_STROKE_WIDTH = 5f;
        private static final float MINUTE_STROKE_WIDTH = 3f;
        private static final float SECOND_TICK_STROKE_WIDTH = 2f;

        private static final float CENTER_GAP_AND_CIRCLE_RADIUS = 4f;

        private static final int SHADOW_RADIUS = 6;

        private Calendar mCalendar;
        private boolean mRegisteredTimeZoneReceiver = false;
        private boolean mMuteMode;

        private float mCenterX;
        private float mCenterY;

        private float mSecondHandLength;
        private float sMinuteHandLength;
        private float sHourHandLength;

        /* Colors for all hands (hour, minute, seconds, ticks) based on photo loaded. */
        private int mWatchHandColor;
        private int mWatchHandHighlightColor;
        private int mWatchHandShadowColor;

        private Paint mHourPaint;
        private Paint mMinutePaint;
        private Paint mSecondPaint;
        private Paint mTickAndCirclePaint;

        private Paint mBackgroundPaint;
        private Paint weatherBackgroundPaint;
        private Bitmap mBackgroundBitmap;
        private Bitmap mGrayBackgroundBitmap;
        private Bitmap weatherBackgroundBitmap;
        private Paint mWeatherHighTempPaint;

        private boolean mAmbient;
        private boolean mLowBitAmbient;
        private boolean mBurnInProtection;

        private String maxTemp="20°C";
        private String minTemp="10°C";


        private Rect mPeekCardBounds = new Rect();


        private final BroadcastReceiver mTimeZoneReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
            }
        };

        /* Handler to update the time once a second in interactive mode. */
        private final Handler mUpdateTimeHandler = new Handler() {
            @Override
            public void handleMessage(Message message) {

                if (Log.isLoggable(TAG, Log.DEBUG)) {
                    Log.d(TAG, "updating time");
                }
                invalidate();
                if (shouldTimerBeRunning()) {
                    long timeMs = System.currentTimeMillis();
                    long delayMs = INTERACTIVE_UPDATE_RATE_MS
                            - (timeMs % INTERACTIVE_UPDATE_RATE_MS);
                    mUpdateTimeHandler.sendEmptyMessageDelayed(MSG_UPDATE_TIME, delayMs);
                }

            }
        };



        @Override
        public void onCreate(SurfaceHolder holder) {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onCreate");
            }
            super.onCreate(holder);

            mGoogleApiClient = new GoogleApiClient.Builder(mContext)
                    .addApi(Wearable.API)
                    .addConnectionCallbacks(this)
                    .addOnConnectionFailedListener(this)
                    .build();
            mGoogleApiClient.connect();


            setWatchFaceStyle(new WatchFaceStyle.Builder(SunshineWatchFace.this)
                    .setCardPeekMode(WatchFaceStyle.PEEK_MODE_SHORT)
                    .setBackgroundVisibility(WatchFaceStyle.BACKGROUND_VISIBILITY_INTERRUPTIVE)
                    .setShowSystemUiTime(false)
                    .build());

            mBackgroundPaint = new Paint();
            mBackgroundPaint.setColor(Color.BLACK);
            mBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.bg);

            weatherBackgroundPaint = new Paint();
            weatherBackgroundPaint.setColor(Color.BLACK);
            weatherBackgroundBitmap = BitmapFactory.decodeResource(getResources(), R.drawable.art_clouds);

            mWeatherHighTempPaint = createTextPaint(getResources().getColor(R.color.config_activity_background));
            mWeatherLowTempPaint = createTextPaint(getResources().getColor(R.color.config_activity_white_dim));

            /* Set defaults for colors */
            mWatchHandColor = Color.WHITE;
            mWatchHandHighlightColor = Color.RED;
            mWatchHandShadowColor = Color.BLACK;

            mHourPaint = new Paint();
            mHourPaint.setColor(mWatchHandColor);
            mHourPaint.setStrokeWidth(HOUR_STROKE_WIDTH);
            mHourPaint.setAntiAlias(true);
            mHourPaint.setStrokeCap(Paint.Cap.ROUND);
            mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mMinutePaint = new Paint();
            mMinutePaint.setColor(mWatchHandColor);
            mMinutePaint.setStrokeWidth(MINUTE_STROKE_WIDTH);
            mMinutePaint.setAntiAlias(true);
            mMinutePaint.setStrokeCap(Paint.Cap.ROUND);
            mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mSecondPaint = new Paint();
            mSecondPaint.setColor(mWatchHandHighlightColor);
            mSecondPaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mSecondPaint.setAntiAlias(true);
            mSecondPaint.setStrokeCap(Paint.Cap.ROUND);
            mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            mTickAndCirclePaint = new Paint();
            mTickAndCirclePaint.setColor(mWatchHandColor);
            mTickAndCirclePaint.setStrokeWidth(SECOND_TICK_STROKE_WIDTH);
            mTickAndCirclePaint.setAntiAlias(true);
            mTickAndCirclePaint.setStyle(Paint.Style.STROKE);
            mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);

            /* Extract colors from background image to improve watchface style. */
            Palette.generateAsync(
                    mBackgroundBitmap,
                    new Palette.PaletteAsyncListener() {
                        @Override
                        public void onGenerated(Palette palette) {
                            if (palette != null) {
                                if (Log.isLoggable(TAG, Log.DEBUG)) {
                                    Log.d(TAG, "Palette: " + palette);
                                }

                                mWatchHandHighlightColor = palette.getVibrantColor(Color.RED);
                                mWatchHandColor = palette.getLightVibrantColor(Color.WHITE);
                                mWatchHandShadowColor = palette.getDarkMutedColor(Color.BLACK);
                                updateWatchHandStyle();
                            }
                        }
                    });

            mCalendar = Calendar.getInstance();
            //if todays data is set donot send request again
            //TO SAVE BATTERY
            if(Utils.getValue(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis())) ==null) {
                new SendDataRequestTask().execute();
            }
        }

        @Override
        public void onConnected(@Nullable Bundle bundle) {
            Wearable.DataApi.addListener(mGoogleApiClient, this);
            if(Utils.getValue(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis())) ==null) {
                new SendDataRequestTask().execute();
            }
        }

        @Override
        public void onConnectionSuspended(int i) {

        }

        @Override
        public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

        }



        @Override
        public void onDataChanged(final DataEventBuffer dataEvents) {
            Log.d(TAG, "onDataChanged: " + dataEvents);
            if(mGoogleApiClient.isConnected() && Utils.getValue(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis())) ==null ){
                for (DataEvent event : dataEvents) {
                    Uri uri = event.getDataItem().getUri();
                    String path = uri.getPath();
                    DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                    if (RESPONSE_WEATHER_PATH.equals(path)) {
                        maxTemp = dataMap.get(RESPONSE_WEATHER_MAX_TEMP);
                        minTemp = dataMap.get(RESPONSE_WEATHER_MIN_TEMP);
                        int weather_id = (int)dataMap.get(RESPONSE_WEATHER_CONDITION_ID);

                        weatherBackgroundBitmap = BitmapFactory.decodeResource(getResources(), Utils.getIconResourceForWeatherCondition(weather_id));
                        invalidate();
                        Utils.clearSharedPreference(mContext);
                        Utils.save(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis()),maxTemp);
                        Utils.save(mContext,MAX_TEMP,maxTemp);
                        Utils.save(mContext,MIN_TEMP,minTemp);
                        Utils.save(mContext,WEATHER_ID,String.valueOf(weather_id));

                        Log.d(TAG,"received data from phone  maxTemp:"+maxTemp+" minTemp:"+minTemp+" weatherId:"+weather_id);
                    }
                }
            }
            else
            {
                //if removed as watchface and reselected then this condition
                checkIWeatherDataAlreadyRetrieved();
                Log.d(TAG,"googleapiclient not connected");
            }
            //new GoogleConnectTask(dataEvents).execute();
        }

        private class GoogleConnectTask extends AsyncTask<Void,Void,Void>{
            public DataEventBuffer mDataEvents;
            public GoogleConnectTask(DataEventBuffer dataEvents){
                mDataEvents = dataEvents;
            }

            @Override
            protected Void doInBackground(Void... voids) {

                connectAndProcessData(mDataEvents);
                return null;
            }

        }

        private void connectAndProcessData(DataEventBuffer dataEvents) {
            if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
                ConnectionResult connectionResult = mGoogleApiClient
                        .blockingConnect(30, TimeUnit.SECONDS);
                if (!connectionResult.isSuccess()) {
                    Log.e(TAG, "DataLayerListenerService failed to connect to GoogleApiClient, "
                            + "error code: " + connectionResult.getErrorCode());
                    return;
                }
                else
                {
                    Log.d(TAG,"connection success");
                }
            }

            // Loop through the events and send a message back to the node that created the data item.
            for (DataEvent event : dataEvents) {
                Uri uri = event.getDataItem().getUri();
                String path = uri.getPath();
                DataMap dataMap = DataMapItem.fromDataItem(event.getDataItem()).getDataMap();
                if (RESPONSE_WEATHER_PATH.equals(path)) {
                    String maxTemp = dataMap.get(RESPONSE_WEATHER_MAX_TEMP);
                    String minTemp = dataMap.get(RESPONSE_WEATHER_MIN_TEMP);
                    int weather_id = dataMap.get(RESPONSE_WEATHER_CONDITION_ID);

                    Log.d(TAG,"received data from phone  maxTemp:"+maxTemp+" minTemp:"+minTemp+" weatherId:"+weather_id);
                }
            }
        }


        private class SendDataRequestTask extends AsyncTask<Void, Void, Void> {

            @Override
            protected Void doInBackground(Void... args) {
                Collection<String> nodes = getNodes();
                for (String node : nodes) {
                    Log.d(TAG,"sending weather data request");
                    //sendStartActivityMessage(node);
                    sendWeatherDataRequest(node);
                }
                return null;
            }
        }

        private void sendWeatherDataRequest(String node) {

                Wearable.MessageApi.sendMessage(
                        mGoogleApiClient, node, REQUEST_WEATHER_DATA, new byte[0]).setResultCallback(
                        new ResultCallback<MessageApi.SendMessageResult>() {
                            @Override
                            public void onResult(MessageApi.SendMessageResult sendMessageResult) {
                                if (!sendMessageResult.getStatus().isSuccess()) {
                                    Log.e(TAG, "Failed to send message with status code: "
                                            + sendMessageResult.getStatus().getStatusCode());
                                } else {
                                    Log.d(TAG, "Successfully sent message");
                                }
                            }
                        }
                );

        }

        private Collection<String> getNodes() {
            HashSet<String> results = new HashSet<>();
            NodeApi.GetConnectedNodesResult nodes =
                    Wearable.NodeApi.getConnectedNodes(mGoogleApiClient).await();
            Log.d(TAG,"node size:"+nodes.getNodes().size());

            for (Node node : nodes.getNodes()) {
                results.add(node.getId());
            }

            return results;
        }


        private Paint createTextPaint(int defaultInteractiveColor) {
            return createTextPaint(defaultInteractiveColor, NORMAL_TYPEFACE);
        }

        private Paint createTextPaint(int defaultInteractiveColor, Typeface typeface) {
            Paint paint = new Paint();
            paint.setColor(defaultInteractiveColor);
            paint.setTypeface(typeface);
            paint.setAntiAlias(true);
            return paint;
        }

        @Override
        public void onDestroy() {
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            super.onDestroy();
        }

        @Override
        public void onPropertiesChanged(Bundle properties) {
            super.onPropertiesChanged(properties);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onPropertiesChanged: low-bit ambient = " + mLowBitAmbient);
            }

            mLowBitAmbient = properties.getBoolean(PROPERTY_LOW_BIT_AMBIENT, false);
            mBurnInProtection = properties.getBoolean(PROPERTY_BURN_IN_PROTECTION, false);
        }

        @Override
        public void onTimeTick() {
            super.onTimeTick();
            invalidate();
        }


        @Override
        public void onApplyWindowInsets(WindowInsets insets) {
            super.onApplyWindowInsets(insets);
            mWeatherLowTempPaint.setTextSize(getResources().getDimension(R.dimen.digital_date_text_size));
            mWeatherHighTempPaint.setTextSize(getResources().getDimension(R.dimen.digital_date_text_size));

        }

        @Override
        public void onAmbientModeChanged(boolean inAmbientMode) {
            super.onAmbientModeChanged(inAmbientMode);
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "onAmbientModeChanged: " + inAmbientMode);
            }
            mAmbient = inAmbientMode;

            updateWatchHandStyle();

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        private void updateWatchHandStyle(){
            if (mAmbient){
                mHourPaint.setColor(Color.WHITE);
                mMinutePaint.setColor(Color.WHITE);
                mSecondPaint.setColor(Color.WHITE);
                mTickAndCirclePaint.setColor(Color.WHITE);

                mHourPaint.setAntiAlias(false);
                mMinutePaint.setAntiAlias(false);
                mSecondPaint.setAntiAlias(false);
                mTickAndCirclePaint.setAntiAlias(false);

                mHourPaint.clearShadowLayer();
                mMinutePaint.clearShadowLayer();
                mSecondPaint.clearShadowLayer();
                mTickAndCirclePaint.clearShadowLayer();

            } else {
                mHourPaint.setColor(mWatchHandColor);
                mMinutePaint.setColor(mWatchHandColor);
                mSecondPaint.setColor(mWatchHandHighlightColor);
                mTickAndCirclePaint.setColor(mWatchHandColor);

                mHourPaint.setAntiAlias(true);
                mMinutePaint.setAntiAlias(true);
                mSecondPaint.setAntiAlias(true);
                mTickAndCirclePaint.setAntiAlias(true);

                mHourPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mMinutePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mSecondPaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
                mTickAndCirclePaint.setShadowLayer(SHADOW_RADIUS, 0, 0, mWatchHandShadowColor);
            }
        }

        @Override
        public void onInterruptionFilterChanged(int interruptionFilter) {
            super.onInterruptionFilterChanged(interruptionFilter);
            boolean inMuteMode = (interruptionFilter == WatchFaceService.INTERRUPTION_FILTER_NONE);

            /* Dim display in mute mode. */
            if (mMuteMode != inMuteMode) {
                mMuteMode = inMuteMode;
                mHourPaint.setAlpha(inMuteMode ? 100 : 255);
                mMinutePaint.setAlpha(inMuteMode ? 100 : 255);
                mSecondPaint.setAlpha(inMuteMode ? 80 : 255);
                invalidate();
            }
        }

        @Override
        public void onSurfaceChanged(SurfaceHolder holder, int format, int width, int height) {
            super.onSurfaceChanged(holder, format, width, height);

            /*
             * Find the coordinates of the center point on the screen, and ignore the window
             * insets, so that, on round watches with a "chin", the watch face is centered on the
             * entire screen, not just the usable portion.
             */
            mCenterX = width / 2f;
            mCenterY = height / 2f;

            /*
             * Calculate lengths of different hands based on watch screen size.
             */
            mSecondHandLength = (float) (mCenterX * 0.875);
            sMinuteHandLength = (float) (mCenterX * 0.75);
            sHourHandLength = (float) (mCenterX * 0.5);


            /* Scale loaded background image (more efficient) if surface dimensions change. */
            float scale = ((float) width) / (float) mBackgroundBitmap.getWidth();

            mBackgroundBitmap = Bitmap.createScaledBitmap(mBackgroundBitmap,
                    (int) (mBackgroundBitmap.getWidth() * scale),
                    (int) (mBackgroundBitmap.getHeight() * scale), true);


            /* scale loaded weather image to desired size , assuming 1/4th of the watch width/ */
            float wScale = ( (((float) width) /4) / (float) weatherBackgroundBitmap.getWidth() );

            weatherBackgroundBitmap = Bitmap.createScaledBitmap(weatherBackgroundBitmap,
                    (int) (weatherBackgroundBitmap.getWidth() * wScale),
                    (int) (weatherBackgroundBitmap.getHeight() * wScale), true);



            /*
             * Create a gray version of the image only if it will look nice on the device in
             * ambient mode. That means we don't want devices that support burn-in
             * protection (slight movements in pixels, not great for images going all the way to
             * edges) and low ambient mode (degrades image quality).
             *
             * Also, if your watch face will know about all images ahead of time (users aren't
             * selecting their own photos for the watch face), it will be more
             * efficient to create a black/white version (png, etc.) and load that when you need it.
             */
            if (!mBurnInProtection && !mLowBitAmbient) {
                initGrayBackgroundBitmap();
            }
        }

        private void initGrayBackgroundBitmap() {
            mGrayBackgroundBitmap = Bitmap.createBitmap(
                    mBackgroundBitmap.getWidth(),
                    mBackgroundBitmap.getHeight(),
                    Bitmap.Config.ARGB_8888);
            Canvas canvas = new Canvas(mGrayBackgroundBitmap);
            Paint grayPaint = new Paint();
            ColorMatrix colorMatrix = new ColorMatrix();
            colorMatrix.setSaturation(0);
            ColorMatrixColorFilter filter = new ColorMatrixColorFilter(colorMatrix);
            grayPaint.setColorFilter(filter);
            canvas.drawBitmap(mBackgroundBitmap, 0, 0, grayPaint);
        }

        @Override
        public void onDraw(Canvas canvas, Rect bounds) {

            checkIWeatherDataAlreadyRetrieved();
            if (Log.isLoggable(TAG, Log.VERBOSE)) {
                Log.v(TAG, "onDraw");
            }
            long now = System.currentTimeMillis();
            mCalendar.setTimeInMillis(now);

            if (mAmbient && (mLowBitAmbient || mBurnInProtection)) {
                canvas.drawColor(Color.BLACK);
            } else if (mAmbient) {
                canvas.drawBitmap(mGrayBackgroundBitmap, 0, 0, mBackgroundPaint);
            } else {
                //canvas.drawBitmap(mBackgroundBitmap, 0, 0, mBackgroundPaint);
                canvas.drawColor(getResources().getColor(android.R.color.black));
                canvas.drawBitmap(weatherBackgroundBitmap,mCenterX + 20,mCenterY+20,weatherBackgroundPaint);

                canvas.drawText(
                        maxTemp,
                        mCenterX - mWeatherHighTempPaint.measureText(maxTemp)-20, mCenterY+mWeatherHighTempPaint.measureText(maxTemp)-15, mWeatherHighTempPaint);
                canvas.drawText(
                        minTemp,
                        mCenterX - mWeatherLowTempPaint.measureText(minTemp)-20, mCenterY+mWeatherHighTempPaint.measureText(maxTemp)+20, mWeatherLowTempPaint);

            }



            /*
             * Draw ticks. Usually you will want to bake this directly into the photo, but in
             * cases where you want to allow users to select their own photos, this dynamically
             * creates them on top of the photo.
             */
            float innerTickRadius = mCenterX - 10;
            float outerTickRadius = mCenterX;
            for (int tickIndex = 0; tickIndex < 12; tickIndex++) {
                float tickRot = (float) (tickIndex * Math.PI * 2 / 12);
                float innerX = (float) Math.sin(tickRot) * innerTickRadius;
                float innerY = (float) -Math.cos(tickRot) * innerTickRadius;
                float outerX = (float) Math.sin(tickRot) * outerTickRadius;
                float outerY = (float) -Math.cos(tickRot) * outerTickRadius;
                canvas.drawLine(mCenterX + innerX, mCenterY + innerY,
                        mCenterX + outerX, mCenterY + outerY, mTickAndCirclePaint);
            }

            /*
             * These calculations reflect the rotation in degrees per unit of time, e.g.,
             * 360 / 60 = 6 and 360 / 12 = 30.
             */
            final float seconds =
                    (mCalendar.get(Calendar.SECOND) + mCalendar.get(Calendar.MILLISECOND) / 1000f);
            final float secondsRotation = seconds * 6f;

            final float minutesRotation = mCalendar.get(Calendar.MINUTE) * 6f;

            final float hourHandOffset = mCalendar.get(Calendar.MINUTE) / 2f;
            final float hoursRotation = (mCalendar.get(Calendar.HOUR) * 30) + hourHandOffset;

            /*
             * Save the canvas state before we can begin to rotate it.
             */
            canvas.save();

            canvas.rotate(hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sHourHandLength,
                    mHourPaint);

            canvas.rotate(minutesRotation - hoursRotation, mCenterX, mCenterY);
            canvas.drawLine(
                    mCenterX,
                    mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                    mCenterX,
                    mCenterY - sMinuteHandLength,
                    mMinutePaint);

            /*
             * Ensure the "seconds" hand is drawn only when we are in interactive mode.
             * Otherwise, we only update the watch face once a minute.
             */
            if (!mAmbient) {
                canvas.rotate(secondsRotation - minutesRotation, mCenterX, mCenterY);
                canvas.drawLine(
                        mCenterX,
                        mCenterY - CENTER_GAP_AND_CIRCLE_RADIUS,
                        mCenterX,
                        mCenterY - mSecondHandLength,
                        mSecondPaint);

            }
            canvas.drawCircle(
                    mCenterX,
                    mCenterY,
                    CENTER_GAP_AND_CIRCLE_RADIUS,
                    mTickAndCirclePaint);

            /* Restore the canvas' original orientation. */
            canvas.restore();

            /* Draw rectangle behind peek card in ambient mode to improve readability. */
            if (mAmbient) {
                canvas.drawRect(mPeekCardBounds, mBackgroundPaint);
            }
        }

        private void checkIWeatherDataAlreadyRetrieved() {
            //if removed as watchface and reselected then this condition
            if(Utils.getValue(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis()))  !=null)
            {
                maxTemp = Utils.getValue(mContext,MAX_TEMP);
                minTemp = Utils.getValue(mContext,MIN_TEMP);
                String weatherId = Utils.getValue(mContext,WEATHER_ID);
                int weather_id = Integer.parseInt(weatherId);
                weatherBackgroundBitmap = BitmapFactory.decodeResource(getResources(), Utils.getIconResourceForWeatherCondition(weather_id));
                invalidate();
            }
        }

        @Override
        public void onVisibilityChanged(boolean visible) {
            super.onVisibilityChanged(visible);

            if (visible) {
                mGoogleApiClient.connect();
                registerReceiver();
                /* Update time zone in case it changed while we weren't visible. */
                mCalendar.setTimeZone(TimeZone.getDefault());
                invalidate();
                //if todays data is set donot send request again
                //TO SAVE BATTERY
                if(Utils.getValue(mContext,Utils.getFullFriendlyDayString(mContext,System.currentTimeMillis())) ==null) {
                    new SendDataRequestTask().execute();
                }
            } else {
                unregisterReceiver();
                if (mGoogleApiClient != null && mGoogleApiClient.isConnected()) {
                    Wearable.DataApi.removeListener(mGoogleApiClient, this);
                    mGoogleApiClient.disconnect();
                }
            }

            /* Check and trigger whether or not timer should be running (only in active mode). */
            updateTimer();
        }

        @Override
        public void onPeekCardPositionUpdate(Rect rect) {
            super.onPeekCardPositionUpdate(rect);
            mPeekCardBounds.set(rect);
        }

        private void registerReceiver() {
            if (mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = true;
            IntentFilter filter = new IntentFilter(Intent.ACTION_TIMEZONE_CHANGED);
            SunshineWatchFace.this.registerReceiver(mTimeZoneReceiver, filter);
        }

        private void unregisterReceiver() {
            if (!mRegisteredTimeZoneReceiver) {
                return;
            }
            mRegisteredTimeZoneReceiver = false;
            SunshineWatchFace.this.unregisterReceiver(mTimeZoneReceiver);
        }

        /**
         * Starts/stops the {@link #mUpdateTimeHandler} timer based on the state of the watch face.
         */
        private void updateTimer() {
            if (Log.isLoggable(TAG, Log.DEBUG)) {
                Log.d(TAG, "updateTimer");
            }
            mUpdateTimeHandler.removeMessages(MSG_UPDATE_TIME);
            if (shouldTimerBeRunning()) {
                mUpdateTimeHandler.sendEmptyMessage(MSG_UPDATE_TIME);
            }
        }

        /**
         * Returns whether the {@link #mUpdateTimeHandler} timer should be running. The timer
         * should only run in active mode.
         */
        private boolean shouldTimerBeRunning() {
            return isVisible() && !mAmbient;
        }
    }



}
