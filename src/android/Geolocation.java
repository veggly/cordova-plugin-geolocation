/*
       Licensed to the Apache Software Foundation (ASF) under one
       or more contributor license agreements.  See the NOTICE file
       distributed with this work for additional information
       regarding copyright ownership.  The ASF licenses this file
       to you under the Apache License, Version 2.0 (the
       "License"); you may not use this file except in compliance
       with the License.  You may obtain a copy of the License at
         http://www.apache.org/licenses/LICENSE-2.0
       Unless required by applicable law or agreed to in writing,
       software distributed under the License is distributed on an
       "AS IS" BASIS, WITHOUT WARRANTIES OR CONDITIONS OF ANY
       KIND, either express or implied.  See the License for the
       specific language governing permissions and limitations
       under the License.
 */


package org.apache.cordova.geolocation;

import android.content.pm.PackageManager;
import android.location.Location;
import android.Manifest;
import android.os.Build;

import com.google.android.gms.location.LocationServices;
import com.google.android.gms.location.LocationRequest;
import com.google.android.gms.location.LocationResult;
import com.google.android.gms.location.LocationCallback;
import com.google.android.gms.location.LocationAvailability;
import com.google.android.gms.tasks.OnCompleteListener;
import com.google.android.gms.tasks.Task;

import org.apache.cordova.CallbackContext;
import org.apache.cordova.CordovaArgs;
import org.apache.cordova.CordovaPlugin;
import org.apache.cordova.PermissionHelper;
import org.apache.cordova.PluginResult;
import org.apache.cordova.LOG;
import org.json.JSONArray;
import org.json.JSONException;

import java.util.HashMap;
import java.util.Map;
import javax.security.auth.callback.Callback;

public class Geolocation extends CordovaPlugin {
    public final static String TAG = "GeolocationPlugin";

    String [] permissions = { Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION };

    private FusedLocationProviderClient locationsClient;
    private Map<String, LocationCallback> watchers;

    @Override
    protected void pluginInitialize() {
        if (!hasPermisssion()) {
            PermissionHelper.requestPermissions(this, 0, permissions);
        } else {
            initLocationClient();
        }
    }

    @Override
    public boolean execute(String action, JSONArray args, CallbackContext callbackContext) throws JSONException {
        if (!hasPermisssion()) {
            callbackContext.error(PluginResult.Status.ILLEGAL_ACCESS_EXCEPTION);
        } else if ("getLocation".equals(action)) {
            getLocation(args.getBoolean(0), args.getInt(1), callbackContext);
        } else if ("addWatch".equals(action)) {
            addWatch(args.getString(0), args.getBoolean(1), callbackContext);
        } else if ("clearWatch".equals(action)) {
            clearWatch(args.getString(0), callbackContext);
        } else {
            return false;
        }

        return true;
    }

    private void initLocationClient() {
        locationsClient = LocationServices.getFusedLocationProviderClient(this.cordova.getActivity());
        watchers = new HashMap<String, LocationCallback>();
    }

    private void getLocation(boolean enableHighAccuracy, int maximumAge, final CallbackContext callbackContext) {
        this.locationsClient.getLastLocation()
            .addOnCompleteListener(this.cordova.getActivity()), new OnCompleteListener<Location>() {
                @Override
                public void onComplete(Task<Location> task) {
                    if (task.isSuccessful()) {
                        Log.d(TAG, "Got last location");

                        callbackContext.success(createResult(task.getResult()));
                    } else {
                        Log.e(TAG, "Fail to get last location");

                        callbackContext.error(task.getException().getMessage());
                    }
                }
            });
    }

    private void addWatch(String id, boolean enableHighAccuracy, final CallbackContext callbackContext) {
        LocationRequest request = new LocationRequest();

        if (enableHighAccuracy) {
            request.setInterval(5000);
            request.setSmallestDisplacement(5);
            request.setPriority(LocationRequest.PRIORITY_HIGH_ACCURACY);
        } else {
            request.setInterval(5000);
            request.setSmallestDisplacement(10);
            request.setPriority(LocationRequest.PRIORITY_BALANCED_POWER_ACCURACY);
        }

        LocationCallback locationCallback = new LocationCallback() {
            @Override
            public void onLocationAvailability(LocationAvailability locationAvailability) {
                Log.d(TAG, "onLocationAvailability");
            }

            @Override
            public void onLocationResult(LocationResult result) {
                Log.d(TAG, "onLocationResult");

                JSONObject result = createResult(result.getLastLocation());
                PluginResult pluginResult = new PluginResult(PluginResult.Status.OK, result);
                pluginResult.setKeepCallback(true);
                callbackContext.sendPluginResult(pluginResult);
            }
        };

        locationsClient.requestLocationUpdates(locationRequest, locationCallback);

        watchers.put(id, locationCallback);
    }

    private void clearWatch(String id, CallbackContext callbackContext) throws JSONException {
        LocationCallback locationCallback = this.watchers.get(uid);
        if (locationCallback != null) {
            locationsClient.removeLocationUpdates(locationCallback);
        }

        callbackContext.success();
    }

    private JSONObject createResult(Location loc) {
        JSONObject result = new JSONObject();

        try {
            result.put("timestamp", loc.getTime());
            result.put("velocity", loc.getSpeed());
            result.put("accuracy", loc.getAccuracy());
            result.put("heading", loc.getHeading());
            result.put("altitude", loc.getAltitude());
            result.put("latitude", loc.getLatitude());
            result.put("longitude", loc.getLongitude());

            return result;
        } catch (JSONException e) {
            Log.e(TAG, "Fail to convert location");

            return null;
        }
    }


    public void onRequestPermissionResult(int requestCode, String[] permissions,
                                          int[] grantResults) throws JSONException
    {
        for (int r : grantResults) {
            if (r == PackageManager.PERMISSION_DENIED) {
                LOG.d(TAG, "Permission Denied!");
                return;
            }
        }

        initLocationClient();
    }

    public boolean hasPermisssion() {
        for (String p : permissions) {
            if(!PermissionHelper.hasPermission(this, p)) {
                return false;
            }
        }
        return true;
    }

    /*
     * We override this so that we can access the permissions variable, which no longer exists in
     * the parent class, since we can't initialize it reliably in the constructor!
     */

    public void requestPermissions(int requestCode) {
        PermissionHelper.requestPermissions(this, requestCode, permissions);
    }
}
