package com.vm.sds011;

import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.usb.UsbDevice;
import android.hardware.usb.UsbDeviceConnection;
import android.hardware.usb.UsbManager;
import android.os.Handler;
import android.support.v7.app.AppCompatActivity;
import android.os.Bundle;
import android.util.Log;
import android.widget.TextView;

import com.felhr.usbserial.UsbSerialDevice;
import com.felhr.usbserial.UsbSerialInterface;

import java.util.HashMap;
import java.util.Iterator;

public class MainActivity extends AppCompatActivity {

    // >adb tcp 55555
    // >adb connect 192.168.0.17
    //

    private static final String ACTION_USB_PERMISSION =
            "com.android.example.USB_PERMISSION";
    private static final String TAG = MainActivity.class.getSimpleName();

    // SDS011 identifiers
    private static final int PRODUCT_ID = 29987;
    private static final int VENDOR_ID = 6790;

    UsbManager usbManager;
    Handler mainHandler;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        usbManager = (UsbManager) getSystemService(Context.USB_SERVICE);
        mainHandler = new Handler(getMainLooper());

        UsbDevice device = getIntent().getParcelableExtra(UsbManager.EXTRA_DEVICE);
        if (device != null) {
            // Device was attached, no need to ask for permissions
            connect(device);
        }
        else {
            // List all devices, find DSS011 by product id and ask for permission
            PendingIntent permissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(ACTION_USB_PERMISSION), 0);
            IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
            registerReceiver(usbReceiver, filter);

            for (UsbDevice usbDevice : usbManager.getDeviceList().values()) {
                if (usbDevice.getProductId() == PRODUCT_ID && usbDevice.getVendorId() == VENDOR_ID) {
                    usbManager.requestPermission(usbDevice, permissionIntent);
                    break;
                }
            }
        }
    }

    private final BroadcastReceiver usbReceiver = new BroadcastReceiver() {

        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (ACTION_USB_PERMISSION.equals(action)) {
                synchronized (this) {
                    UsbDevice device = intent.getParcelableExtra(UsbManager.EXTRA_DEVICE);
                    if (intent.getBooleanExtra(UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
                        if(device != null){
                            connect(device);
                        }
                    }
                    else {
                        // Bummer
                        Log.d(TAG, "permission denied for device " + device);
                    }
                }
            }
        }
    };

    private void appendText(String text) {
        TextView textView = findViewById(R.id.text_view);
        String t = textView.getText() + text;
        textView.setText(t);
    }

    private void setText(String text) {
        TextView textView = findViewById(R.id.text_view);
        textView.setText(text);
    }

    // https://github.com/felHR85/UsbSerial
    // https://dev.to/minkovsky/working-on-my-iot-air-quality-monitoring-setup-40a5
    // https://www.banggood.com/Geekcreit-Nova-PM-Sensor-SDS011-High-Precision-Laser-PM2_5-Air-Quality-Detection-Sensor-Module-Tester-p-1144246.html?utm_source=google&utm_medium=cpc_ods&utm_campaign=nancy-197s-sdsrm-bag-all-m-content&utm_content=nancy&gclid=CjwKCAjw0vTtBRBREiwA3URt7qq2SrHrKZjl5-T8WsEyMzuyt6P0df34Mdc5w4K-pcUH1BDTgAPctBoC2MIQAvD_BwE&cur_warehouse=CN

/*
    Communication protocol:

    Serial communication protocol: 9600 8N1. (Rate of 9600, data bits 8, parity none, stop bits 1)
    Serial report communication cycle: 1+0.5 seconds
    Data frame (10 bytes): message header + order+ data(6 bytes) + checksum + message trailer

    Bytes |	Name	             |  Content
    ------|----------------------|----------------
      0	  | message header	     |  AA
      1	  | order	             |  C0
      2	  | data 1	             |  PM2.5 low byte
      3	  | data 2	             |  PM2.5 high byte
      4	  | data 3	             |  PM10 low byte
      5	  | data 4	             |  PM10 high byte
      6	  | data 5	             |  0(reserved)
      7	  | data 6	             |  0(reserved)
      8	  | checksum	         |  checksum
      9	  | message trailer	     |  AB

    Checksum: data 1 + data 2 + ...+ data 6
    PM2.5 data content: PM2.5 (ug/m3) = ((PM2.5 high byte*256 ) + PM2.5 low byte)/10
    PM10 data content: PM10 (ug/m3) = ((PM10 high byte*256 ) + PM10 low byte)/10
*/

    public void connect(UsbDevice device) {

        UsbDeviceConnection usbConnection = usbManager.openDevice(device);
        UsbSerialDevice serial = UsbSerialDevice.createUsbSerialDevice(device, usbConnection);

        // Serial communication protocol: 9600 8N1. (Rate of 9600, data bits 8, parity none, stop bits 1)
        serial.open();
        serial.setBaudRate(9600);
        serial.setDataBits(UsbSerialInterface.DATA_BITS_8);
        serial.setParity(UsbSerialInterface.PARITY_NONE);
        serial.setStopBits(UsbSerialInterface.STOP_BITS_1);
        serial.setFlowControl(UsbSerialInterface. FLOW_CONTROL_OFF);

        serial.read(mCallback);
    }

    private UsbSerialInterface.UsbReadCallback mCallback = new UsbSerialInterface.UsbReadCallback() {

        @Override
        public void onReceivedData(final byte[] b)
        {
            if (b.length == 10) {
                float pm25 = (b[2] + b[3] << 8)/10.0f;
                float pm10 = (b[4] + b[5] << 8)/10.0f;

                postAppend("pm25 " + pm25 + " pm10 " + pm10 + "\n");
            }
        }
    };

    private void postAppend(final String s) {
        mainHandler.post(new Runnable() {
            @Override
            public void run() {
                appendText(s);
            }
        });
    }


}
