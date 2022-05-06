package com.main.arwayfinding;

import android.app.Application;
import android.content.Context;

import com.google.maps.GeoApiContext;

public class ArWayfindingApp extends Application {
    @Override
    public void onCreate() {
        super.onCreate();

        // create GeoApiContext for later use
        geoApiContext = new GeoApiContext.Builder()
                .apiKey(getString(R.string.google_maps_key))
                .build();
        key = getString(R.string.google_maps_key);

        // save a global reference to the context object
        context = getApplicationContext();
    }

    public static GeoApiContext getGeoApiContext() {
        return geoApiContext;
    }

    public static float getDpWidth() {
        return dpWidth;
    }

    public static void setDpWidth(float dpWidth) {
        ArWayfindingApp.dpWidth = dpWidth;
    }


    public static float getDpHeight() {
        return dpHeight;
    }

    public static void setDpHeight(float dpHeight) {
        ArWayfindingApp.dpHeight = dpHeight;
    }

    public static String getKey() {
        return key;
    }

    public static void setKey(String key) {
        ArWayfindingApp.key = key;
    }

    private static GeoApiContext geoApiContext;

    public static Context getContext() {
        return context;
    }

    private static Context context;
    private static float dpHeight;
    private static float dpWidth;
    private static String key;
}
