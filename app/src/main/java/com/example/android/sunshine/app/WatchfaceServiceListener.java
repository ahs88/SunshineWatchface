package com.example.android.sunshine.app;

import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Looper;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.CursorLoader;
import android.util.Log;

import com.example.android.sunshine.app.data.WeatherContract;
import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.common.api.ResultCallback;
import com.google.android.gms.wearable.CapabilityInfo;
import com.google.android.gms.wearable.DataApi;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.PutDataMapRequest;
import com.google.android.gms.wearable.PutDataRequest;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.Date;
import java.util.List;
import java.util.logging.Handler;

/**
 * Created by shetty on 12/10/16.
 */

public class WatchfaceServiceListener extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {
    private static final String RESPONSE_WEATHER_PATH = "/response_weather_path";
    private static final String RESPONSE_WEATHER_MAX_TEMP = "RESPONSE_WEATHER_MAX";
    private static final String RESPONSE_WEATHER_MIN_TEMP = "RESPONSE_WEATHER_MIN";
    private static final String RESPONSE_WEATHER_CONDITION_ID = "RESPONSE_WEATHER_CONDITION_ID";
    public static String TAG = WatchfaceServiceListener.class.getName();
    public static String REQUEST_WEATHER_PATH = "/request_weather";
    private static final String[] FORECAST_COLUMNS = {
            WeatherContract.WeatherEntry.COLUMN_MAX_TEMP,
            WeatherContract.WeatherEntry.COLUMN_MIN_TEMP,
            WeatherContract.WeatherEntry.COLUMN_WEATHER_ID,
    };
    private GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(this)
                .addOnConnectionFailedListener(this)
                .build();
        mGoogleApiClient.connect();
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        super.onMessageReceived(messageEvent);
        Log.d(TAG,"onMessageReceived:"+messageEvent);
        if (messageEvent.getPath().equals(REQUEST_WEATHER_PATH)) {
            Log.d(TAG,"onMessageReceived with path:"+messageEvent);
            String locationSetting = Utility.getPreferredLocation(getApplicationContext());
            Uri weatherForLocationUri = WeatherContract.WeatherEntry.buildWeatherLocationWithDate(
                    locationSetting, System.currentTimeMillis());

            Cursor cursor = getContentResolver().query(weatherForLocationUri, FORECAST_COLUMNS, null, null, null);
            cursor.moveToFirst();
            int weather_id = cursor.getInt(2);

            double max = cursor.getDouble(0);
            String highString = Utility.formatTemperature(getApplicationContext(), max);

            double min = cursor.getDouble(1);
            String lowString = Utility.formatTemperature(getApplicationContext(), min);


            sendWeatherDetailsThroughDataApi(highString,lowString,weather_id);




        }

    }

    private void sendWeatherDetailsThroughDataApi(final String highString,final String lowString,final int condition_id) {


        android.os.Handler handler = new android.os.Handler(Looper.getMainLooper());
        handler.post(new Runnable() {
            @Override
            public void run() {
                Log.d(TAG,"sendWeatherDetailsThroughDataApi highString:"+highString+" lowString:"+lowString+" condition_id:"+condition_id);
                PutDataMapRequest dataMap = PutDataMapRequest.create(RESPONSE_WEATHER_PATH);
                dataMap.getDataMap().putString(RESPONSE_WEATHER_MAX_TEMP, String.valueOf(highString));
                dataMap.getDataMap().putString(RESPONSE_WEATHER_MIN_TEMP, String.valueOf(lowString));
                dataMap.getDataMap().putInt(RESPONSE_WEATHER_CONDITION_ID, condition_id);
                dataMap.getDataMap().putLong("time", System.currentTimeMillis());
                PutDataRequest request = dataMap.asPutDataRequest();
                request.setUrgent();

                if(mGoogleApiClient.isConnected())
                    Log.d(TAG,"is Connected");
                else
                    Log.d(TAG,"client not Connected");

                Wearable.DataApi.putDataItem(mGoogleApiClient, request)
                        .setResultCallback(new ResultCallback<DataApi.DataItemResult>() {
                            @Override
                            public void onResult(DataApi.DataItemResult dataItemResult) {
                                Log.d(TAG, "Sending weather data status: " + dataItemResult.getStatus()
                                        .isSuccess());
                            }
                        });
            }
        });

    }

    @Override
    public void onDataChanged(DataEventBuffer dataEventBuffer) {
        super.onDataChanged(dataEventBuffer);
        Log.d(TAG,"onDataChanged");
    }

    @Override
    public void onConnectedNodes(List<Node> list) {
        super.onConnectedNodes(list);
        Log.d(TAG,"onConnectedNodes");
    }

    @Override
    public void onCapabilityChanged(CapabilityInfo capabilityInfo) {
        super.onCapabilityChanged(capabilityInfo);
    }


    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.d(TAG,"onConnected");
        Wearable.DataApi.addListener(mGoogleApiClient, this);
    }


    @Override
    public void onConnectionSuspended(int i) {

    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();

    }
}
