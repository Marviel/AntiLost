package name.lukebechtel.antilost;

import android.content.Context;
import android.os.AsyncTask;
import android.support.annotation.NonNull;
import android.support.v4.app.FragmentActivity;
import android.os.Bundle;

import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.MarkerOptions;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.Dialog;
import android.app.PendingIntent;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.net.sip.SipAudioCall;
import android.net.sip.SipErrorCode;
import android.net.sip.SipException;
import android.net.sip.SipManager;
import android.net.sip.SipProfile;
import android.net.sip.SipRegistrationListener;
import android.preference.PreferenceManager;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuItem;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.EditText;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;
import android.location.LocationManager;
import android.location.LocationListener;
import android.location.Location;
import android.location.LocationProvider;

import org.apache.http.HttpEntity;
import org.apache.http.HttpResponse;
import org.apache.http.NameValuePair;
import org.apache.http.client.ClientProtocolException;
import org.apache.http.client.HttpClient;
import org.apache.http.client.entity.UrlEncodedFormEntity;
import org.apache.http.client.methods.HttpGet;
import org.apache.http.client.methods.HttpPost;
import org.apache.http.client.methods.HttpPut;
import org.apache.http.impl.client.DefaultHttpClient;
import org.apache.http.message.BasicNameValuePair;
import org.apache.http.util.EntityUtils;
import org.json.JSONObject;

import java.io.IOException;
import java.io.UnsupportedEncodingException;
import java.net.URL;
import java.text.ParseException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Enumeration;
import java.util.Iterator;
import java.util.List;
import java.util.ListIterator;

public class AntilostActivity extends FragmentActivity implements View.OnTouchListener, OnMapReadyCallback {

    private GoogleMap mMap; // Might be null if Google Play services APK is not available.
    public String sipAddress = null;

    public SipManager manager = null;
    public SipProfile me = null;
    public SipAudioCall call = null;
    public IncomingCallReceiver callReceiver;

    private static final int CALL_ADDRESS = 1;
    private static final int SET_AUTH_INFO = 2;
    private static final int UPDATE_SETTINGS_DIALOG = 3;
    private static final int HANG_UP = 4;

    private static final String serveraddr = "http://ec2-52-89-85-85.us-west-2.compute.amazonaws.com:3000/locations/1";

    public boolean NewLocation = false;
    public float xLoc = 0;
    public float yLoc = 0;

    public boolean Parent = true; //Switch this for child build

    @Override
    protected void onCreate(Bundle savedInstanceState) {
//        super.onCreate(savedInstanceState);
//        setContentView(R.layout.activity_parent_side);
//        setUpMapIfNeeded();
//
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_parent_side);
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        Log.i("Antilost/onCreate", "about to getMapAsync");
        mapFragment.getMapAsync(this);
        Log.i("Antilost/onCreate", "after getMapAsync");

        if(SipManager.isVoipSupported(getBaseContext()) && SipManager.isApiSupported(getBaseContext()))
            Toast.makeText(getApplicationContext(), "SIP is supported!", Toast.LENGTH_LONG).show();
        else
            Toast.makeText(getApplicationContext(), "SIP NOT supported. App will not work! testing123", Toast.LENGTH_LONG).show();


        /* ------------LOCATION------- */
        // Acquire a reference to the system Location Manager
        LocationManager locationManager = (LocationManager) this.getSystemService(Context.LOCATION_SERVICE);

        // Define a listener that responds to location updates
        LocationListener locationListener = new LocationListener() {
            public void onLocationChanged(Location location) {
                // Called when a new location is found by the network location provider.
                Log.i("Antilost/initiateCall", "onLocationChanged!!!!");
                makeUseOfNewLocation(location);
            }

            public void onStatusChanged(String provider, int status, Bundle extras) {}

            public void onProviderEnabled(String provider) {}

            public void onProviderDisabled(String provider) {}
        };

        // Register the listener with the Location Manager to receive location updates
        locationManager.requestLocationUpdates(LocationManager.NETWORK_PROVIDER, 0, 0, locationListener);



        ToggleButton pushToTalkButton = (ToggleButton) findViewById(R.id.pushToTalk);
        pushToTalkButton.setOnTouchListener(this);

        // Set up the intent filter.  This will be used to fire an
        // IncomingCallReceiver when someone calls the SIP address used by this
        // application.
        IntentFilter filter = new IntentFilter();
        filter.addAction("android.SipDemo.INCOMING_CALL");
        callReceiver = new IncomingCallReceiver();
        this.registerReceiver(callReceiver, filter);

        // "Push to talk" can be a serious pain when the screen keeps turning off.
        // Let's prevent that.
        getWindow().addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON);

        initializeManager();
    }

    @Override
    protected void onResume() {
        super.onResume();
        setUpMapIfNeeded();
    }

    /**
     * Sets up the map if it is possible to do so (i.e., the Google Play services APK is correctly
     * installed) and the map has not already been instantiated.. This will ensure that we only ever
     * call {@link #setUpMap()} once when {@link #mMap} is not null.
     * <p/>
     * If it isn't installed {@link SupportMapFragment} (and
     * {@link com.google.android.gms.maps.MapView MapView}) will show a prompt for the user to
     * install/update the Google Play services APK on their device.
     * <p/>
     * A user can return to this FragmentActivity after following the prompt and correctly
     * installing/updating/enabling the Google Play services. Since the FragmentActivity may not
     * have been completely destroyed during this process (it is likely that it would only be
     * stopped or paused), {@link #onCreate(Bundle)} may not be called again so we should call this
     * method in {@link #onResume()} to guarantee that it will be called.
     */
    private void setUpMapIfNeeded() {
        // Do a null check to confirm that we have not already instantiated the map.
        if (mMap == null) {
            // Try to obtain the map from the SupportMapFragment.
            mMap = ((SupportMapFragment) getSupportFragmentManager().findFragmentById(R.id.map))
                    .getMap();
            // Check if we were successful in obtaining the map.
            if (mMap != null) {
                setUpMap();
            }
        }
    }

    /**
     * This is where we can add markers or lines, add listeners or move the camera. In this case, we
     * just add a marker near Africa.
     * <p/>
     * This should only be called once and when we are sure that {@link #mMap} is not null.
     */
    private void setUpMap() {
        //mMap.addMarker(new MarkerOptions().position(new LatLng(0, 0)).title("Marker"));
    }

    @Override
    public void onMapReady(GoogleMap map) {
        // Add a marker in Sydney, Australia, and move the camera.
        Log.i("Antilost/onMapReady", "map ready...");
    }


    @Override
    public void onStart() {
        super.onStart();
        // When we get back from the preference setting Activity, assume
        // settings have changed, and re-login with new auth info.
        initializeManager();
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        if (call != null) {
            call.close();
        }

        closeLocalProfile();

        if (callReceiver != null) {
            this.unregisterReceiver(callReceiver);
        }
    }

    public void initializeManager() {
        if(manager == null) {
            manager = SipManager.newInstance(this);
        }

        initializeLocalProfile();
    }

    /**
     * Logs you into your SIP provider, registering this device as the location to
     * send SIP calls to for your SIP address.
     */
    public void initializeLocalProfile() {
        if (manager == null) {
            return;
        }

        if (me != null) {
            closeLocalProfile();
        }

        SharedPreferences prefs = PreferenceManager.getDefaultSharedPreferences(getBaseContext());
        String username = prefs.getString("namePref", "");
        String domain = prefs.getString("domainPref", "");
        String password = prefs.getString("passPref", "");
        String proxy = prefs.getString("proxyPref", "");

        if (username.length() == 0 || domain.length() == 0 || password.length() == 0) {
            showDialog(UPDATE_SETTINGS_DIALOG);
            return;
        }

        try {
            SipProfile.Builder builder = new SipProfile.Builder(username, domain);
            builder.setPassword(password);

            if(proxy.length() != 0){
                builder.setOutboundProxy(proxy);
            }

            me = builder.build();

            Intent i = new Intent();
            i.setAction("android.SipDemo.INCOMING_CALL");
            PendingIntent pi = PendingIntent.getBroadcast(this, 0, i, Intent.FILL_IN_DATA);
            manager.open(me, pi, null);


            // This listener must be added AFTER manager.open is called,
            // Otherwise the methods aren't guaranteed to fire.

            manager.setRegistrationListener(me.getUriString(), new SipRegistrationListener() {
                public void onRegistering(String localProfileUri) {
                    updateStatus("Registering with SIP Server (" + localProfileUri + "...");
                }

                public void onRegistrationDone(String localProfileUri, long expiryTime) {
                    updateStatus("Ready");
                }

                public void onRegistrationFailed(String localProfileUri, int errorCode,
                                                 String errorMessage) {
                    updateStatus("Registration failed, error " + SipErrorCode.toString(errorCode) + ". Please check settings.");
                }
            });
        } catch (ParseException pe) {
            updateStatus("ParseException Error.");
        } catch (SipException se) {
            updateStatus("SipException Error.");
        }
    }

    /**
     * Closes out your local profile, freeing associated objects into memory
     * and unregistering your device from the server.
     */
    public void closeLocalProfile() {
        if (manager == null) {
            return;
        }
        try {
            if (me != null) {
                manager.close(me.getUriString());
            }
        } catch (Exception ee) {
            Log.d("Antilost/onDestroy", "Failed to close local profile.", ee);
        }
    }

    /**
     * Make an outgoing call.
     */
    public void initiateCall() {
        Log.i("Antilost/initiateCall", "begin initiating call!");

                updateStatus(sipAddress);

        try {
            SipAudioCall.Listener listener = new SipAudioCall.Listener() {
                // Much of the client's interaction with the SIP Stack will
                // happen via listeners.  Even making an outgoing call, don't
                // forget to set up a listener to set things up once the call is established.
                @Override
                public void onCallEstablished(SipAudioCall call) {
                    Log.i("Antilost/initiateCall", "Call established!");
                    call.startAudio();
                    call.setSpeakerMode(true);
                    if(!call.isMuted())
                        call.toggleMute();

                    updateStatus(call);
                }

                @Override
                public void onCallEnded(SipAudioCall call) {
                    Log.i("Antilost/initiateCall", "Ready.");
                    updateStatus("Ready.");
                }

                @Override
                public void onCalling(SipAudioCall call) {
                    if(!call.isMuted())
                        call.toggleMute();
                    Log.i("Antilost/initiateCall", "calling...");
                    updateStatus("Calling...");
                }

                @Override
                public void onError(SipAudioCall call, int errorCode, String errorMessage) {
                    Log.i("Antilost/initiateCall", String.format("ERROR Calling; %d: %s",
                                                                    errorCode,errorMessage));
                }
            };

            Log.i("Antilost/initiateCall", String.format("makeAudioCall(%s,%s,..",me.getUriString(), sipAddress));
            call = manager.makeAudioCall(me.getUriString(), sipAddress, listener, 30);

        }
        catch (Exception e) {
            Log.i("Antilost/initiateCall", "Error when trying to close manager.", e);
            if (me != null) {
                try {
                    manager.close(me.getUriString());
                } catch (Exception ee) {
                    Log.i("Antilost/InitiateCall",
                            "Error when trying to close manager.", ee);
                    ee.printStackTrace();
                }
            }
            if (call != null) {
                call.close();
            }
        }
    }

    /**
     * Updates the status box at the top of the UI with a messege of your choice.
     * @param status The String to display in the status box.
     */
    public void updateStatus(final String status) {
        // Be a good citizen.  Make sure UI changes fire on the UI thread.
        this.runOnUiThread(new Runnable() {
            public void run() {
                TextView labelView = (TextView) findViewById(R.id.sipLabel);
                labelView.setText(status);
            }
        });
    }

    /**
     * Updates the status box with the SIP address of the current call.
     * @param call The current, active call.
     */
    public void updateStatus(SipAudioCall call) {
        String useName = call.getPeerProfile().getDisplayName();
        if(useName == null) {
            useName = call.getPeerProfile().getUserName();
        }
        updateStatus(useName + "@" + call.getPeerProfile().getSipDomain());
    }

    /**
     * Updates whether or not the user's voice is muted, depending on whether the button is pressed.
     * @param v The View where the touch event is being fired.
     * @param event The motion to act on.
     * @return boolean Returns false to indicate that the parent view should handle the touch event
     * as it normally would.
     */
    public boolean onTouch(View v, MotionEvent event) {
        if (call == null) {
            return false;
        } else if (event.getAction() == MotionEvent.ACTION_DOWN && call != null && call.isMuted()) {
            call.toggleMute();
        } else if (event.getAction() == MotionEvent.ACTION_UP && !call.isMuted()) {
            call.toggleMute();
        }
        return false;
    }

    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, CALL_ADDRESS, 0, "Make a SIP Call");
        menu.add(0, SET_AUTH_INFO, 0, "Settings");
        menu.add(0, HANG_UP, 0, "End Call");

        return true;
    }

    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case CALL_ADDRESS:
                showDialog(CALL_ADDRESS);
                break;
            case SET_AUTH_INFO:
                updatePreferences();
                break;
            case HANG_UP:
                if(call != null) {
                    try {
                        call.endCall();
                    } catch (SipException se) {
                        Log.d("Antilost/onOptSelected",
                                "Error ending call.", se);
                    }
                    call.close();
                    updateStatus("Ready.");
                }
                break;
        }
        return true;
    }

    @Override
    protected Dialog onCreateDialog(int id) {
        switch (id) {
            case CALL_ADDRESS:

                LayoutInflater factory = LayoutInflater.from(this);
                final View textBoxView = factory.inflate(R.layout.call_address_dialog, null);
                return new AlertDialog.Builder(this)
                        .setTitle("Make SIP Call")
                        .setView(textBoxView)
                        .setPositiveButton(
                                android.R.string.ok, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        EditText textField = (EditText)
                                                (textBoxView.findViewById(R.id.calladdress_edit));
                                        sipAddress = textField.getText().toString();
                                        initiateCall();

                                    }
                                })
                        .setNegativeButton(
                                android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Noop.
                                    }
                                })
                        .create();

            case UPDATE_SETTINGS_DIALOG:
                return new AlertDialog.Builder(this)
                        .setMessage("Please update your SIP Settings")
                        .setPositiveButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                            public void onClick(DialogInterface dialog, int whichButton) {
                                updatePreferences();
                            }
                        })
                        .setNegativeButton(
                                android.R.string.cancel, new DialogInterface.OnClickListener() {
                                    public void onClick(DialogInterface dialog, int whichButton) {
                                        // Noop.
                                    }
                                })
                        .create();
        }
        return null;
    }

    public void updatePreferences() {
        Intent settingsActivity = new Intent(getBaseContext(),
                SipSettings.class);
        startActivity(settingsActivity);
    }

    public void makeUseOfNewLocation(Location loc) {
        Log.i("Antilost/initiateCall", "MakeUseofNewLocation!");
        //CHILD / PARENT switch
        if(Parent) {
            mMap.addMarker(new MarkerOptions().position(new LatLng(loc.getLatitude(), loc.getLongitude())).title("YOU ARE HERE"));

            ExecuteGet executeGet = new ExecuteGet();
            executeGet.execute();
            ExecuteSend executeSend = new ExecuteSend();
            executeSend.execute(loc.getLatitude(), loc.getLongitude());
        }
        else{ //Child
            ExecuteSend executeSend = new ExecuteSend();
            executeSend.execute(loc.getLatitude(), loc.getLongitude());
        }
    }


    private class ExecuteGet extends AsyncTask<Void,Void,String>{
        private String responseString;
        @Override
        protected String doInBackground(Void... params) {
            HttpClient client = new DefaultHttpClient();
            HttpGet request = new HttpGet(serveraddr);
            HttpResponse response;
            try {
                response = client.execute(request);
                HttpEntity entity = response.getEntity();
                responseString = EntityUtils.toString(entity, "UTF-8");
                Log.d("Antilost/GetReqResponse", responseString);
                JSONObject obj = new JSONObject(responseString);
                final float x = Float.parseFloat(obj.getString("x"));
                final float y = Float.parseFloat(obj.getString("y"));
                runOnUiThread(new Runnable() {
                    @Override
                    public void run() {
                        mMap.clear();
                        mMap.addMarker(new MarkerOptions().position(new LatLng(x, y)).title("Vasu"));
                    }
                });

            } catch (ClientProtocolException e) {
                Log.e("Antilost/HTTPError", e.toString());
                // e.printStackTrace();
            } catch (Exception e) {
                Log.e("Antilost/HTTPError", e.toString());
            }
            return "";
        }

        @Override
        protected void onPostExecute(String aVoid) {
        }
    }

    private class ExecuteSend extends AsyncTask<Double,Void,String>{
        private double x;
        private double y;
        @Override
        protected String doInBackground(Double... params) {
            HttpClient httpClient = new DefaultHttpClient();

            HttpPut httpPut = new HttpPut(serveraddr);
            List<NameValuePair> nameValuePair = new ArrayList<NameValuePair>(2);
            try {
                Log.d("Antilost/gothere", "woooo!");
                Log.d("Antilost/params", Integer.toString(params.length));

                x = params[0];
                y = params[1];

                Log.d("Antilost/nameValSize", Integer.toString(nameValuePair.size()));
                nameValuePair.add(new BasicNameValuePair("x", Double.toString(x)));
                nameValuePair.add(new BasicNameValuePair("y", Double.toString(y)));
                Log.d("Antilost/nameValuePair", nameValuePair.toString());
            } catch(Exception e){
                Log.e("Antilost/nameValueError", e.toString());
            }

            //Encoding POST data
            try {
                UrlEncodedFormEntity uefe = new UrlEncodedFormEntity(nameValuePair);
                Log.d("Antilost/uefe", uefe.toString());
                httpPut.setEntity(uefe);

            } catch (UnsupportedEncodingException e)
            {
                e.printStackTrace();
            }

            try {
                HttpResponse response = httpClient.execute(httpPut);
                // write response to log
                Log.d("AntiLost/HTTPPut", response.toString());
            } catch (ClientProtocolException e) {
                // Log exception
                e.printStackTrace();
            } catch (IOException e) {
                // Log exception
                e.printStackTrace();
            }
            return "";
        }

        @Override
        protected void onPostExecute(String aVoid) {
        }
    }
}
