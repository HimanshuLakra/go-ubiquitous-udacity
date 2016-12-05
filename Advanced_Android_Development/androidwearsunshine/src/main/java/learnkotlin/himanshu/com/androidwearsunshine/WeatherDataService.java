package learnkotlin.himanshu.com.androidwearsunshine;

import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.os.Bundle;
import android.support.annotation.NonNull;
import android.support.annotation.Nullable;
import android.support.v4.content.LocalBroadcastManager;
import android.util.Log;

import com.google.android.gms.common.ConnectionResult;
import com.google.android.gms.common.api.GoogleApiClient;
import com.google.android.gms.wearable.Asset;
import com.google.android.gms.wearable.DataEvent;
import com.google.android.gms.wearable.DataEventBuffer;
import com.google.android.gms.wearable.DataItem;
import com.google.android.gms.wearable.DataMap;
import com.google.android.gms.wearable.DataMapItem;
import com.google.android.gms.wearable.Wearable;
import com.google.android.gms.wearable.WearableListenerService;

import java.io.InputStream;
import java.util.concurrent.TimeUnit;

public class WeatherDataService extends WearableListenerService implements GoogleApiClient.ConnectionCallbacks,
        GoogleApiClient.OnConnectionFailedListener {

    private static final String TAG = "WeatherDataService";
    public static final String LOW_TEMP = "LowTemp";
    public static final String HIGHT_TEMP = "HighTemp";
    public static final String WEATHER_ASSET = "WeatherAsset";
    public static final String ACTION_WEATHER_DATA = "WeatherLowTemp";
    private static final long TIMEOUT_MS = TimeUnit.SECONDS.toMillis(5);

    GoogleApiClient mGoogleApiClient;

    @Override
    public void onCreate() {
        super.onCreate();
        mGoogleApiClient = new GoogleApiClient.Builder(this)
                .addApi(Wearable.API)
                .addConnectionCallbacks(WeatherDataService.this)
                .addOnConnectionFailedListener(WeatherDataService.this)
                .build();

        mGoogleApiClient.connect();
        Log.e(TAG, "onCreate: ");
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if ((mGoogleApiClient != null) && mGoogleApiClient.isConnected()) {
            mGoogleApiClient.disconnect();
        }
    }

    @Override
    public void onDataChanged(DataEventBuffer dataEvents) {
        super.onDataChanged(dataEvents);
        Log.e(TAG, "onDataChanged: ");
        for (DataEvent event : dataEvents) {
            if (event.getType() == DataEvent.TYPE_CHANGED) {
                // DataItem changed
                DataItem item = event.getDataItem();
                if (item.getUri().getPath().compareTo("/weather") == 0) {
                    DataMap dataMap = DataMapItem.fromDataItem(item).getDataMap();

                    Asset profileAsset = dataMap.getAsset(WEATHER_ASSET);
                    Bitmap bitmap = loadBitmapFromAsset(profileAsset);

                    Intent weatherIntent = new Intent(ACTION_WEATHER_DATA);
                    weatherIntent.putExtra(LOW_TEMP, dataMap.getDouble(LOW_TEMP));
                    weatherIntent.putExtra(HIGHT_TEMP, dataMap.getDouble(HIGHT_TEMP));
                    weatherIntent.putExtra(WEATHER_ASSET, bitmap);

                    LocalBroadcastManager.getInstance(getApplicationContext()).sendBroadcast(weatherIntent);

                }
            }
        }
    }

    public Bitmap loadBitmapFromAsset(Asset asset) {
        if (asset == null) {
            throw new IllegalArgumentException("Asset must be non-null");
        }
        ConnectionResult result =
                mGoogleApiClient.blockingConnect(TIMEOUT_MS, TimeUnit.MILLISECONDS);
        if (!result.isSuccess()) {
            return null;
        }
        // convert asset into a file descriptor and block until it's ready
        InputStream assetInputStream = Wearable.DataApi.getFdForAsset(
                mGoogleApiClient, asset).await().getInputStream();
        mGoogleApiClient.disconnect();

        if (assetInputStream == null) {
            Log.w(TAG, "Requested an unknown Asset.");
            return null;
        }
        // decode the stream into a bitmap
        return BitmapFactory.decodeStream(assetInputStream);
    }

    @Override
    public void onConnected(@Nullable Bundle bundle) {
        Log.e(TAG, "onConnected: ");
    }

    @Override
    public void onConnectionSuspended(int i) {
        Log.e(TAG, "onConnectionSuspended: ");
    }

    @Override
    public void onConnectionFailed(@NonNull ConnectionResult connectionResult) {

    }
}
