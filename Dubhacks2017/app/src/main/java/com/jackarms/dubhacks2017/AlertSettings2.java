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
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.Settings;
import android.support.v4.app.ActivityCompat;
import android.support.v7.app.AppCompatActivity;
import android.text.method.ScrollingMovementMethod;
import android.util.Log;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;
import java.util.Map;

public class AlertSettings2 extends AppCompatActivity {

  private Ringtone alarm;
  private boolean alarmEnabled;

  public final String ACTION_USB_PERMISSION = "com.hariharan.arduinousb.USB_PERMISSION";

  UsbManager usbManager;
  UsbDevice device;
  UsbSerialDevice serialPort;
  UsbDeviceConnection connection;
  Context context;
  private TextView logText;
  Button connectArduino;

  @Override
  protected void onCreate(Bundle savedInstanceState) {
    context = this;

    super.onCreate(savedInstanceState);
    setContentView(R.layout.alert_settings);
    Uri path = Settings.System.DEFAULT_RINGTONE_URI;
    alarm = RingtoneManager.getRingtone(getApplicationContext(), path);
    alarmEnabled = false;

//    ToggleButton alarmSet =(ToggleButton) findViewById(R.id.toggle_alarm);
    connectArduino = (Button) findViewById(R.id.connect_arduino);
    logText = (TextView) findViewById(R.id.logText);
    logText.setMovementMethod(new ScrollingMovementMethod());
    Log.d("BLAH", "Registered logText?" + Boolean.toString(logText != null));
//    alarmSet.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
//      @Override
//      public void onCheckedChanged(CompoundButton toggleButton, boolean isChecked) {
//        alarmEnabled = isChecked;
//      }
//    });

    IntentFilter filter = new IntentFilter();
    filter.addAction(ACTION_USB_PERMISSION);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_ATTACHED);
    filter.addAction(UsbManager.ACTION_USB_DEVICE_DETACHED);
    registerReceiver(broadcastReceiver, filter);
  }

  public void soundAlarm(View view) {
//    MediaPlayer mediaPlayer = MediaPlayer.create(context, R.raw.sound_file_1);
//    mediaPlayer.start(); // no need to call prepare(); create() does that for you
    if (alarmEnabled) {
      alarm.play();
    }
  }

  public void stopAlarm(View view) {
    alarm.stop();
  }

  public void setMaxRingtoneVolumne(View view) {
    AudioManager mobilemode = (AudioManager) this.getSystemService(Context.AUDIO_SERVICE);
    mobilemode.setStreamVolume(AudioManager.STREAM_RING, mobilemode.getStreamMaxVolume(AudioManager.STREAM_RING), 0);
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
          Intent intent = new Intent(Intent.ACTION_CALL, Uri.parse("tel:" + "206-920-9368"));
          str.add("MADE INTENT FOR PHONE");
          if (ActivityCompat.checkSelfPermission(AlertSettings2.this, Manifest.permission.CALL_PHONE) != PackageManager.PERMISSION_GRANTED) {
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
            AlertSettings2.this.startActivity(intent);
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
    this.logText.setText(this.logText.getText() + "\n" + str);
  }

}
