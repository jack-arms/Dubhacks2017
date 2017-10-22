package com.jackarms.dubhacks2017;

import android.Manifest;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.PackageManager;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.location.Location;
import android.media.Ringtone;
import android.net.Uri;
import android.os.Bundle;
import android.support.v4.app.ActivityCompat;
import android.support.v4.app.FragmentActivity;
import android.support.v4.content.ContextCompat;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.ArrayAdapter;
import android.widget.Button;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;
import com.google.android.gms.maps.CameraUpdateFactory;
import com.google.android.gms.maps.GoogleMap;
import com.google.android.gms.maps.GoogleMap.OnMapClickListener;
import com.google.android.gms.maps.GoogleMap.OnMyLocationChangeListener;
import com.google.android.gms.maps.OnMapReadyCallback;
import com.google.android.gms.maps.SupportMapFragment;
import com.google.android.gms.maps.model.BitmapDescriptorFactory;
import com.google.android.gms.maps.model.LatLng;
import com.google.android.gms.maps.model.Marker;
import com.google.android.gms.maps.model.MarkerOptions;
import com.google.android.gms.tasks.Task;

import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Calendar;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;

class Concern {
  public int cid;
  public double lat;
  public double lng;
  public String reason;
  public String time;
  public String concern_type;

    JSONObject toJSONObject() {
        JSONObject o = new JSONObject();
        try {
            o.put("lat", lat);
            o.put("lng", lng);
            o.put("reason", reason);
            o.put("time", time);
            o.put("concern_type", concern_type);
        } catch (JSONException e) { return null; }
        return o;
    }

    static Concern fromJSONObject(JSONObject o) {
        try {
            Concern c = new Concern();
            c.cid = o.getInt("cid");
            c.lat = o.getDouble("lat");
            c.lng = o.getDouble("lng");
            c.reason = o.getString("reason");
            c.time = o.getString("time");
            c.concern_type = o.getString("concern_type");

            return c;
        } catch (JSONException e) {
            Log.d("JSON", e.getStackTrace().toString());
            return null;
        }
    }

    public boolean equals(Object o) {
        if (!(o instanceof Concern)) return false;
        return ((Concern)o).cid == this.cid;
    }

    public int hashCode() {
        return cid;
    }
}

public class MapsActivity extends FragmentActivity implements OnMapReadyCallback {

    private GoogleMap mMap;
    private LatLng lastKnownLoc;
    private Marker pendingMarker;
    private EditText messageEditText;
    private Spinner incidentTypeSpinner;
    private List<Marker> markers;
    private Map<Marker, String> messages;

    private Map<Concern, Marker> dbMessages;

    String concernUrl = "http://173.250.252.65:1337/concerns";

    public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";
    public final String PHONE_NUMBER = "206-661-3732";

    class MarkerTask extends TimerTask {

        public void run(){
            getConcerns();
        }
    }

    UsbManager usbManager;
    UsbDevice device;
    UsbSerialDevice serialPort;
    UsbDeviceConnection connection;
    Context context;
    private TextView logText;
    Button connectArduino;

    RequestQueue queue;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        context = this;
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_maps);
        // Obtain the SupportMapFragment and get notified when the map is ready to be used.
        SupportMapFragment mapFragment = (SupportMapFragment) getSupportFragmentManager()
                .findFragmentById(R.id.map);
        mapFragment.getMapAsync(this);

        queue = Volley.newRequestQueue(this);

        connectArduino = (Button) findViewById(R.id.connect_arduino);
//        logText = (TextView) findViewById(R.id.logText);
//        logText.setMovementMethod(new ScrollingMovementMethod());
//        Log.d("BLAH", "Registered logText?" + Boolean.toString(logText != null));

        IntentFilter filter = new IntentFilter();
        filter.addAction(ACTION_USB_PERMISSION);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
        filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
        registerReceiver(broadcastReceiver, filter);


        markers = new ArrayList<Marker>();
        messages = new HashMap<Marker, String>();
        dbMessages = new HashMap<>();
        Timer timer = new Timer();


        // Schedule to run after every 3 second(3000 millisecond)
        timer.scheduleAtFixedRate( new MarkerTask(), 3000, 3000);


        messageEditText = (EditText) findViewById(R.id.messageEditText);

        incidentTypeSpinner = (Spinner) findViewById(R.id.incidentTypeSpinner);
        ArrayAdapter<CharSequence> adapter = ArrayAdapter.createFromResource(this, R.array.user_incident_types, android.R.layout.simple_spinner_item);
        adapter.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        incidentTypeSpinner.setAdapter(adapter);

        Button reportButton = (Button) findViewById(R.id.report);
        reportButton.setOnClickListener(new Button.OnClickListener(){
            public void onClick(View v) {
                if (pendingMarker != null) {
                    String concernMessage = messageEditText.getText().toString();
                    String concernType = incidentTypeSpinner.getSelectedItem().toString();
                    pendingMarker.setTitle(concernType);
                    //Marker newIncidentMarker = mMap.addMarker(new MarkerOptions().position(pendingMarker.getPosition()).title(concernType).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                    //messages.put(newIncidentMarker, concernMessage);

                    // post to db
                    Concern c = new Concern();
                    c.lat =         (float) pendingMarker.getPosition().latitude;
                    c.lng =         (float) pendingMarker.getPosition().longitude;
                    c.reason = concernMessage;
                    c.concern_type = concernType;
                    c.time = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(Calendar.getInstance().getTime());
                    postConcern(c);


                    //markers.add(newIncidentMarker);
                    pendingMarker.remove();
                    pendingMarker = null;
                }
            }
        });

    }

    public void sendWarningLights(View view) {
        if (serialPort != null) {
            serialPort.write(new byte[]{2});
            logText("starting...");
        } else {
            logText("SERIAL - error: send lights without serial port.");
        }
    }

    public void stopWarningLights(View view) {
        if (serialPort != null) {
            serialPort.write(new byte[]{1});
            logText("stopping...");
        } else {
            logText("SERIAL - error: stop lights without serial port.");
        }
    }

    public void arduinoSetup(View view) {
        try {
            this.usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
            Map<String, UsbDevice> usbDevices = usbManager.getDeviceList();
            logText("Found " + usbDevices.size() + " devices.");
            if (!usbDevices.isEmpty()) {
                boolean keep = true;
                for (UsbDevice device : usbDevices.values()) {
                    this.device = device;
                    int deviceVID = device.getVendorId();
                    logText("" + deviceVID);
                    if (deviceVID == 10755) {
                        logText("Found arduino OMG!");
                        PendingIntent pi = PendingIntent.getBroadcast(this, 0,
                                new Intent(ACTION_USB_PERMISSION), 0);
                        try {
                            usbManager.requestPermission(device, pi);
                        } catch (Exception e) {
                            logText("D: " + e.getMessage());
                        }
                        keep = false;
                    } else {
                        logText("Setting stuff to NULL!!!!");
                        connection = null;
                        this.device = null;
                    }

                    if (!keep)
                        break;
                }
            }
        } catch (Exception e) {
            logText("C: " + e.getMessage());
        }
    }

    UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() { //Defining a Callback which triggers whenever data is read.
        @Override
        public void onReceivedData(byte[] arg0) {
            final List<String> str = new ArrayList<>();
            try {
                str.add("RECEIVE - " + Arrays.toString(arg0));
                if (arg0.length == 1 && arg0[0] == 3) {
                    // 3 am booty call
                    str.add("RECEIVED A 3");
                    Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + PHONE_NUMBER));
                    str.add("MADE INTENT FOR PHONE");
                    if (ActivityCompat.checkSelfPermission(MapsActivity.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
                        // TODO: Consider calling
                        //    ActivityCompat#requestPermissions
                        // here to request the missing permissions, and then overriding
                        //   public void onRequestPermissionsResult(int requestCode, String[] permissions,
                        //                                          int[] grantResults)
                        // to handle the case where the user grants the permission. See the documentation
                        // for ActivityCompat#requestPermissions for more details.
                        str.add("No permission to call phone!!!!");
                    } else {
                        str.add("making a call1!!!");
                        MapsActivity.this.startActivity(intent);
                    }
                }
            } catch (Exception e) {
                str.add("A: " + e.getMessage());
            }
            Runnable r = new Runnable() {
                @Override
                public void run() {
                    for (String s : str) {
                        logText(s);
                    }
                }
            };
            runOnUiThread(r);
        }
    };

    private final BroadcastReceiver broadcastReceiver = new BroadcastReceiver() { //Broadcast Receiver to automatically start and stop the Serial connection.
        @Override
        public void onReceive(Context context, Intent intent) {
            logText("onReceive");
            try {
                if (intent.getAction().equals(ACTION_USB_PERMISSION)) {
                    logText("usb permission");
                    boolean granted = intent.getExtras().getBoolean(UsbManager.EXTRA_PERMISSION_GRANTED);
                    if (granted) {
                        logText("granted!");
                        connection = usbManager.openDevice(device);
                        serialPort = UsbSerialDevice.createUsbSerialDevice(device, connection);
                        if (serialPort != null) {
                            if (serialPort.open()) { //Set Serial Connection Parameters.
                                serialPort.setBaudRate(9600);
                                serialPort.setDataBits(UsbSerialInterface.DATA_BITS_8);
                                serialPort.setStopBits(UsbSerialInterface.STOP_BITS_1);
                                serialPort.setParity(UsbSerialInterface.PARITY_NONE);
                                serialPort.setFlowControl(UsbSerialInterface.FLOW_CONTROL_OFF);
                                serialPort.read(mCallback);
                                logText("Serial Connection Opened!");

                            } else {
                                logText("SERIAL - PORT NOT OPEN");
                            }
                        } else {
                            logText("SERIAL - PORT IS NULL");
                        }
                    } else {
                        logText("SERIAL - PERM NOT GRANTED");
                    }
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_ATTACHED)) {
                    arduinoSetup(connectArduino);
                } else if (intent.getAction().equals(UsbManager.ACTION_USB_DEVICE_DETACHED)) {
                    serialPort.close();
                }
            } catch (Exception e) {
                logText("B: " + e.getMessage());
            }
        }

    };

    public void logText(String str) {
//        this.logText.setText(this.logText.getText() + "\n" + str);
    }

    /**
     * Manipulates the map once available.
     * This callback is triggered when the map is ready to be used.
     * This is where we can add markers or lines, add listeners or move the camera. In this case,
     * we just add a marker near Sydney, Australia.
     * If Google Play services is not installed on the device, the user will be prompted to install
     * it inside the SupportMapFragment. This method will only be triggered once the user has
     * installed Google Play services and returned to the app.
     */
    @Override
    public void onMapReady(GoogleMap googleMap) {
        mMap = googleMap;
        lastKnownLoc = new LatLng(0, 0);

        if (ContextCompat.checkSelfPermission(this, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED) {
            mMap.setMyLocationEnabled(true);
        }

        mMap.setOnMapClickListener(new OnMapClickListener() {

            public void onMapClick(LatLng point) {
                if (pendingMarker != null) {
                    pendingMarker.remove();
                }
                MarkerOptions incedentMarker = new MarkerOptions().position(point).title("Tell us why you felt unsafe.").icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_BLUE));
                pendingMarker = mMap.addMarker(incedentMarker);

            }
        });

        messageEditText.setOnFocusChangeListener(new View.OnFocusChangeListener() {
            @Override
            public void onFocusChange(View v, boolean hasFocus) {
                if (hasFocus && messageEditText.getText().toString().equals("Tell us why you felt unsafe.")) {
                    messageEditText.setText("");
                }
            }
        });

        mMap.setOnMyLocationChangeListener(new OnMyLocationChangeListener(){
            public void onMyLocationChange(Location location) {
                LatLng loc = new LatLng(location.getLatitude(), location.getLongitude());
                if (lastKnownLoc.equals(new LatLng(0, 0))) {
                    mMap.moveCamera(CameraUpdateFactory.newLatLng(loc));
                    mMap.moveCamera(CameraUpdateFactory.zoomTo(10));
                }
                lastKnownLoc = loc;

                checkNearbyMarkers();
            }
        });

        mMap.setOnMarkerClickListener(new GoogleMap.OnMarkerClickListener() {
            @Override
            public boolean onMarkerClick(Marker marker) {
                marker.showInfoWindow();
                if (messages.containsKey(marker)) {
                    messageEditText.setText(messages.get(marker));
                }
                return true;
            }
        });
    }

//    // Instantiate the RequestQueue.

//    String url ="http://www.google.com";
//
//    // Request a string response from the provided URL.
//    StringRequest stringRequest = new StringRequest(Request.Method.GET, url,
//    new Response.Listener<String>() {
//    @Override
//    public void onResponse(String response) {
//    // Display the first 500 characters of the response string.
//    mTextView.setText("Response is: "+ response.substring(0,500));
//    }
//    }, new Response.ErrorListener() {
//    @Override
//    public void onErrorResponse(VolleyError error) {
//    mTextView.setText("That didn't work!");
//    }
//    });
//    // Add the request to the RequestQueue.
//    queue.add(stringRequest);


  public void getConcerns() {
    JsonObjectRequest jsObjRequest = new JsonObjectRequest
      (Request.Method.GET, concernUrl, null, new Response.Listener<JSONObject>() {

        @Override
        public void onResponse(JSONObject response) {
          Log.d("REQUEST", "Response: " + response.toString());
            try {
                JSONArray concerns = response.getJSONArray("concerns");
                for (int i = 0; i < concerns.length(); i++) {
                    Concern c = Concern.fromJSONObject(concerns.getJSONObject(i));
                    if (!dbMessages.containsKey(c)) {
                        Marker newMarker = mMap.addMarker(new MarkerOptions().position(new LatLng(c.lat, c.lng)).title(c.concern_type).icon(BitmapDescriptorFactory.defaultMarker(BitmapDescriptorFactory.HUE_YELLOW)));
                        messages.put(newMarker, c.reason);
                        dbMessages.put(c, newMarker);
                    }
                }
            } catch (JSONException e) {
                Log.d("JSON", e.getStackTrace().toString());
            }
        }
      }, new Response.ErrorListener() {

        @Override
        public void onErrorResponse(VolleyError error) {
          Log.d("REQUEST", "Error: " + error.toString());
        }
      });

      queue.add(jsObjRequest);
    }

    public void postConcern(Concern c) {
      JsonObjectRequest jsObjRequest = new JsonObjectRequest
        (Request.Method.POST, concernUrl, c.toJSONObject(), new Response.Listener<JSONObject>() {

          @Override
          public void onResponse(JSONObject response) {
            Log.d("REQUEST", "Response: " + response.toString());
              Toast.makeText(context, "Thank you for your report.", Toast.LENGTH_LONG).show();
          }
        }, new Response.ErrorListener() {

          @Override
          public void onErrorResponse(VolleyError error) {
            Log.d("REQUEST", "Error: " + error.toString());
          }
        });
        queue.add(jsObjRequest);
    }
    public void checkNearbyMarkers() {
        boolean withinRange = false;
        for (Marker m : markers) {
            double threshold = .001;
            double dist = Math.sqrt(Math.pow(lastKnownLoc.latitude - m.getPosition().latitude, 2) + Math.pow(lastKnownLoc.longitude - m.getPosition().longitude, 2));
            if (dist < threshold) {
                withinRange = true;
            }
        }
        if (withinRange){
            sendWarningLights(null);
        }
        else {
            stopWarningLights(null);
        }

    }

}
