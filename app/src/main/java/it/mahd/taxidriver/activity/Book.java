package it.mahd.taxidriver.activity;

import android.content.Context;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.support.design.widget.FloatingActionButton;
import android.support.v4.app.Fragment;
import android.support.v4.app.FragmentTransaction;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.app.AlertDialog;
import android.app.Service;
import android.content.DialogInterface;
import android.content.Intent;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.IBinder;
import android.provider.Settings;
import android.widget.Toast;

import com.github.nkzawa.emitter.Emitter;
import com.github.nkzawa.socketio.client.Socket;
import com.google.android.gms.maps.CameraUpdate;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.MapView;
import com.google.android.gms.maps.MapsInitializer;
import com.google.android.gms.maps.model.CameraPosition;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;

import org.json.JSONException;
import org.json.JSONObject;

import it.mahd.taxidriver.Main;
import it.mahd.taxidriver.R;
import it.mahd.taxidriver.util.Controllers;
import it.mahd.taxidriver.util.SocketIO;

/**
 * Created by salem on 2/13/16.
 */
public class Book extends Fragment implements LocationListener {
    SharedPreferences pref;
    Controllers conf = new Controllers();
    Socket socket = SocketIO.getInstance();

    MapView mMapView;
    Service service;
    private GoogleMap googleMap;
    protected LocationManager locationManager;// Declaring a Location Manager
    Location location; // location
    private CameraPosition cameraPosition;
    private CameraUpdate cameraUpdate;
    private double latitude, longitude;
    private Boolean isStart = false;
    boolean isGPSEnabled = false;// flag for GPS status
    boolean isNetworkEnabled = false;// flag for network status
    boolean canGetLocation = false;// flag for GPS status
    private static final long MIN_DISTANCE_CHANGE_FOR_UPDATES = 3;// The minimum distance to change Updates in meters // 3 meters
    private static final long MIN_TIME_BW_UPDATES = 1000 * 3 * 1;// The minimum time between updates in milliseconds // 3 seconds
    private FloatingActionButton Start_btn, Pause_btn;

    public Book() {}

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        View v = inflater.inflate(R.layout.book, container, false);
        pref = getActivity().getSharedPreferences(conf.app, Context.MODE_PRIVATE);
        socket.connect();

        mMapView = (MapView) v.findViewById(R.id.mapView);
        mMapView.onCreate(savedInstanceState);
        mMapView.onResume();

        try {
            MapsInitializer.initialize(getActivity().getApplicationContext());
        } catch (Exception e) {
            e.printStackTrace();
        }

        googleMap = mMapView.getMap();
        googleMap.setMyLocationEnabled(true);
        googleMap.getUiSettings().setZoomControlsEnabled(true);
        getLocation();
        if(canGetLocation()){
            latitude = getLatitude();
            longitude = getLongitude();
        }else{
            showSettingsAlert();
            latitude = 0;
            longitude = 0;
        }
        cameraPosition = new CameraPosition.Builder().target(new LatLng(latitude, longitude)).zoom(15).build();
        googleMap.animateCamera(CameraUpdateFactory.newCameraPosition(cameraPosition));

        Start_btn = (FloatingActionButton) v.findViewById(R.id.start_btn);
        Start_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isStart = true;
                sendToServer(latitude, longitude, isStart);
            }
        });

        Pause_btn = (FloatingActionButton) v.findViewById(R.id.pause_btn);
        Pause_btn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                isStart = false;
                sendToServer(0, 0, isStart);
            }
        });
        return v;
    }

    private void sendToServer(double lat, double lon, boolean work) {
        JSONObject json = new JSONObject();
        try{
            json.put(conf.tag_latitude,lat);
            json.put(conf.tag_longitude, lon);
            json.put(conf.tag_token, pref.getString(conf.tag_token, ""));
            json.put(conf.tag_working, work);
            socket.emit(conf.tag_gps, json);
        }catch(JSONException e){ }
    }

    public Location getLocation() {
        try {
            locationManager = (LocationManager) getActivity().getSystemService(service.LOCATION_SERVICE);
            isGPSEnabled = locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER);// getting GPS status
            isNetworkEnabled = locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER);// getting network status

            if (!isGPSEnabled) {// no GPS provider is enabled
                showSettingsAlert();
            } else {
                this.canGetLocation = true;
                if (isNetworkEnabled) {// First get location from Network Provider
                    locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER,
                            MIN_TIME_BW_UPDATES,
                            MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                    if (locationManager != null) {
                        location = locationManager.getLastKnownLocation(LocationManager.NETWORK_PROVIDER);
                        if (location != null) {
                            latitude = location.getLatitude();
                            longitude = location.getLongitude();
                        }
                    }
                }
                if (isGPSEnabled) {// if GPS Enabled get lat/long using GPS Services
                    if (location == null) {
                        locationManager.requestLocationUpdates(
                                LocationManager.GPS_PROVIDER,
                                MIN_TIME_BW_UPDATES,
                                MIN_DISTANCE_CHANGE_FOR_UPDATES, this);
                        if (locationManager != null) {
                            location = locationManager
                                    .getLastKnownLocation(LocationManager.GPS_PROVIDER);
                            if (location != null) {
                                latitude = location.getLatitude();
                                longitude = location.getLongitude();
                            }
                        }
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }
        return location;
    }

    public void stopUsingGPS(){//Stop using GPS listener & Calling this function will stop using GPS in your app
        if(locationManager != null){
            locationManager.removeUpdates(this);
        }
    }

    public double getLatitude(){//Function to get latitude
        if(location != null){
            latitude = location.getLatitude();
        }
        return latitude;
    }

    public double getLongitude(){//Function to get longitude
        if(location != null){
            longitude = location.getLongitude();
        }
        return longitude;
    }

    //Function to check GPS/wifi enabled
    public boolean canGetLocation() {
        return this.canGetLocation;
    }

    public void showSettingsAlert(){//Function to show settings alert dialog & On pressing Settings button will lauch Settings Options
        final AlertDialog.Builder builder =  new AlertDialog.Builder(getActivity());
        final String action = Settings.ACTION_LOCATION_SOURCE_SETTINGS;
        final String title = "GPS is settings";// Setting Dialog Title
        final String message = "GPS is not enabled. Do you want open GPS setting?";// Setting Dialog Message
        builder.setTitle(title).setMessage(message)
                .setPositiveButton("OK",
                        new DialogInterface.OnClickListener() {// On pressing Settings button
                            public void onClick(DialogInterface d, int id) {
                                getActivity().startActivity(new Intent(action));
                                d.dismiss();
                            }
                        })
                .setNegativeButton("Cancel",
                        new DialogInterface.OnClickListener() {// on pressing cancel button
                            public void onClick(DialogInterface d, int id) {
                                d.cancel();
                            }
                        });
        builder.create().show();// Showing Alert Message
    }

    @Override
    public void onLocationChanged(Location location) {
        latitude = location.getLatitude();
        longitude = location.getLongitude();
        cameraUpdate = CameraUpdateFactory.newLatLng(new LatLng(latitude, longitude));
        googleMap.moveCamera(cameraUpdate);
        changeLocation();
    }
    private void changeLocation() {
        if (isStart) {
            sendToServer(latitude, longitude, isStart);
        }
    }

    @Override
    public void onProviderDisabled(String provider) {}

    @Override
    public void onProviderEnabled(String provider) {}

    @Override
    public void onStatusChanged(String provider, int status, Bundle extras) {}


    public IBinder onBind(Intent arg0) {
        return null;
    }

    @Override
    public void onResume() {
        super.onResume();
        mMapView.onResume();
    }

    @Override
    public void onPause() {
        super.onPause();
        mMapView.onPause();
    }

    @Override
    public void onDestroy() {
        sendToServer(latitude, longitude, false);
        stopUsingGPS();
        socket.disconnect();
        mMapView.onDestroy();
        FragmentTransaction ft = getFragmentManager().beginTransaction();
        ft.replace(R.id.container_body, new Home());
        ft.addToBackStack(null);
        ft.commit();
        ((Main) getActivity()).getSupportActionBar().setTitle(getString(R.string.home));
        super.onDestroy();
    }

    @Override
    public void onLowMemory() {
        super.onLowMemory();
        mMapView.onLowMemory();
    }
}
