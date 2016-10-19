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

import android.content.Intent;
import android.net.Uri;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Parcelable;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.MessageEvent;
import com.google.android.gms.wearable.Node;
import com.google.android.gms.wearable.NodeApi;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.concurrent.TimeUnit;

/**
 * Listens to DataItems and Messages from the local node.
 */
public class DataLayerListenerService{
        //extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks, GoogleApiClient.OnConnectionFailedListener {

    /*private static final String TAG = DataLayerListenerService.class.getName();

    private static final String RESPONSE_WEATHER_MAX_TEMP = "RESPONSE_WEATHER_MAX";
    private static final String RESPONSE_WEATHER_MIN_TEMP = "RESPONSE_WEATHER_MIN";
    private static final String RESPONSE_WEATHER_CONDITION_ID = "RESPONSE_WEATHER_CONDITION_ID";
    private static final String RESPONSE_WEATHER_PATH = "/response_weather_path";
    GoogleApiClient mGoogleApiClient;
    private List<Node> connectDeviceList;

    @Override
    public void onCreate() {
        super.onCreate();
        Log.d(TAG,"onCreate");
        GoogleClient googleClient = new GoogleClient();
        mGoogleApiClient = googleClient.getInstance(this,this,this);
        mGoogleApiClient.connect();
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        Log.d(TAG, "onDataChanged: " + dataEvents);
        if (!mGoogleApiClient.isConnected() || !mGoogleApiClient.isConnecting()) {
            ConnectionResult connectionResult = mGoogleApiClient
                    .blockingConnect(30, TimeUnit.SECONDS);
            if (!connectionResult.isSuccess()) {
                Log.e(TAG, "DataLayerListenerService failed to connect to GoogleApiClient, "
                        + "error code: " + connectionResult.getErrorCode());
                return;
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
                long weather_id = dataMap.get(RESPONSE_WEATHER_CONDITION_ID);

                Log.d(TAG,"received data from phone  maxTemp:"+maxTemp+" minTemp:"+minTemp+" weatherId:"+weather_id);
            }
        }
    }

    @Override
    public void onMessageReceived(MessageEvent messageEvent) {
        Log.d(TAG, "onMessageReceived: " + messageEvent);
        // Check to see if the message is to start an activity
    }

    public static void LOGD(final String tag, String message) {
        if (Log.isLoggable(tag, Log.DEBUG)) {
            Log.d(tag, message);
        }
    }


    @Override
    public void onConnectedNodes(List<Node> list) {
        super.onConnectedNodes(list);
        connectDeviceList = list;
        if(list.size()>0) {
            Log.d(TAG, "connected nodes:" + list.get(0));
        }

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
        Log.d(TAG,"onConnectionFailed");
    }


    public interface WearInterface{
        public void connectedDevices(List<Node> connectedDevice);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        Wearable.DataApi.removeListener(mGoogleApiClient, this);
        mGoogleApiClient.disconnect();
    }*/
}