package com.volkersfreunde.phoneDrone;

import java.io.FileDescriptor;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;

import android.app.Activity;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.hardware.Sensor;
import android.hardware.SensorEvent;
import android.hardware.SensorEventListener;
import android.hardware.SensorManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.ParcelFileDescriptor;
import android.os.PowerManager;
import android.os.PowerManager.WakeLock;
import android.util.Log;
import android.view.Display;
import android.view.Surface;
import android.view.View;
import android.view.WindowManager;
import android.widget.CheckBox;
import android.widget.CompoundButton;
import android.widget.CompoundButton.OnCheckedChangeListener;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.SeekBar.OnSeekBarChangeListener;

import com.android.future.usb.UsbAccessory;
import com.android.future.usb.UsbManager;

public class HelloADKActivity extends Activity implements Runnable,
		OnSeekBarChangeListener, SensorEventListener, OnCheckedChangeListener {

	private static final String TAG = "PhoneDrone";

	private static final String ACTION_USB_PERMISSION = "com.google.android.DemoKit.action.USB_PERMISSION";

	private UsbManager mUsbManager;
	private PendingIntent mPermissionIntent;
	private boolean mPermissionRequestPending;

	private SeekBar servo1SeekBar;
	private SeekBar incoming1SeekBar;
	private EditText incoming1EditText;
	private EditText servo1EditText;

	private SeekBar servo2SeekBar;
	private SeekBar incoming2SeekBar;
	private EditText incoming2EditText;
	private EditText servo2EditText;

	private SensorManager mSensorManager;
	private Sensor mAccelerometer;
	private PowerManager mPowerManager;
	private WindowManager mWindowManager;
	private Display mDisplay;
	private WakeLock mWakeLock;

	private CheckBox updateValuesCheckbox;

	Handler handler = new Handler();

	private CheckBox mAutoPilotCheckBox;
	private boolean mAutoPilotTurnedOn;

	private float mSensorX;
	private float mSensorY;
	private float mSensorZ;

	UsbAccessory mAccessory;
	ParcelFileDescriptor mFileDescriptor;
	FileInputStream mInputStream;
	FileOutputStream mOutputStream;

	public static final byte SERVO1_COMMAND = 2;
	public static final byte SERVO2_COMMAND = 3;
	public static final byte SEND_UPDATES_COMMAND = 4;

	private final BroadcastReceiver mUsbReceiver = new BroadcastReceiver() {
		@Override
		public void onReceive(Context context, Intent intent) {
			String action = intent.getAction();
			if (ACTION_USB_PERMISSION.equals(action)) {
				synchronized (this) {
					UsbAccessory accessory = UsbManager.getAccessory(intent);
					if (intent.getBooleanExtra(
							UsbManager.EXTRA_PERMISSION_GRANTED, false)) {
						openAccessory(accessory);
					} else {
						Log.d(TAG, "permission denied for accessory "
								+ accessory);
					}
					mPermissionRequestPending = false;
				}
			} else if (UsbManager.ACTION_USB_ACCESSORY_DETACHED.equals(action)) {
				UsbAccessory accessory = UsbManager.getAccessory(intent);
				if (accessory != null && accessory.equals(mAccessory)) {
					closeAccessory();
				}
			}
		}
	};

	public void updateIncomingSeekBar(final int value, final int servoIndex) {
		handler.post(new Runnable() {

			@Override
			public void run() {
				switch (servoIndex) {
				case 0:
					HelloADKActivity.this.incoming1EditText.setText("" + value);
					if (value != -1) {
						HelloADKActivity.this.incoming1SeekBar
								.setProgress(value);
					}
					break;
				case 1:
					HelloADKActivity.this.incoming2EditText.setText("" + value);
					if (value != -1) {
						HelloADKActivity.this.incoming2SeekBar
								.setProgress(value);
					}
					break;

				}

			}
		});
	}

	@Override
	public void onCreate(Bundle savedInstanceState) {
		super.onCreate(savedInstanceState);

		mUsbManager = UsbManager.getInstance(this);
		mPermissionIntent = PendingIntent.getBroadcast(this, 0, new Intent(
				ACTION_USB_PERMISSION), 0);
		IntentFilter filter = new IntentFilter(ACTION_USB_PERMISSION);
		filter.addAction(UsbManager.ACTION_USB_ACCESSORY_DETACHED);
		registerReceiver(mUsbReceiver, filter);

		if (getLastNonConfigurationInstance() != null) {
			mAccessory = (UsbAccessory) getLastNonConfigurationInstance();
			openAccessory(mAccessory);
		}

		setContentView(R.layout.main);

		mPowerManager = (PowerManager) getSystemService(POWER_SERVICE);

		mWindowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
		mDisplay = mWindowManager.getDefaultDisplay();
		mWakeLock = mPowerManager.newWakeLock(
				PowerManager.SCREEN_BRIGHT_WAKE_LOCK, getClass().getName());

		mAutoPilotCheckBox = (CheckBox) findViewById(R.id.autopilot_check_box);
		mAutoPilotCheckBox.setChecked(false);
		mAutoPilotCheckBox.setOnCheckedChangeListener(this);

		mAutoPilotTurnedOn = false;

		incoming1SeekBar = (SeekBar) findViewById(R.id.incoming1_seek_bar);
		incoming1SeekBar.setMax(2000);
		incoming1SeekBar.setEnabled(false);

		servo1EditText = (EditText) findViewById(R.id.servo1_editText);

		incoming1EditText = (EditText) findViewById(R.id.incoming1_editText);

		servo1SeekBar = (SeekBar) findViewById(R.id.servo1_seek_bar);
		servo1SeekBar.setMax(2000);
		servo1SeekBar.setProgress(1000);
		servo1SeekBar.setOnSeekBarChangeListener(this);
		servo1SeekBar.setEnabled(false);

		incoming2SeekBar = (SeekBar) findViewById(R.id.incoming2_seek_bar);
		incoming2SeekBar.setMax(2000);
		incoming2SeekBar.setEnabled(false);

		servo2EditText = (EditText) findViewById(R.id.servo2_editText);

		incoming2EditText = (EditText) findViewById(R.id.incoming2_editText);

		servo2SeekBar = (SeekBar) findViewById(R.id.servo2_seek_bar);
		servo2SeekBar.setMax(2000);
		servo2SeekBar.setProgress(1000);
		servo2SeekBar.setOnSeekBarChangeListener(this);
		servo2SeekBar.setEnabled(false);

		mSensorManager = (SensorManager) getSystemService(SENSOR_SERVICE);
		mAccelerometer = mSensorManager
				.getDefaultSensor(Sensor.TYPE_ACCELEROMETER);

		updateValuesCheckbox = (CheckBox) findViewById(R.id.updateValues_Checkbox);
		updateValuesCheckbox.setChecked(false);
		updateValuesCheckbox.setOnCheckedChangeListener(this);

	}

	@Override
	public Object onRetainNonConfigurationInstance() {
		if (mAccessory != null) {
			return mAccessory;
		} else {
			return super.onRetainNonConfigurationInstance();
		}
	}

	private void startSensorListener() {
		mAutoPilotTurnedOn = true;
		mSensorManager.registerListener(this, mAccelerometer,
				SensorManager.SENSOR_DELAY_UI);
	}

	private void stopSensorListener() {
		mSensorManager.unregisterListener(this);
		mAutoPilotTurnedOn = false;
	}

	@Override
	public void onResume() {
		super.onResume();
		mWakeLock.acquire();
		if (mInputStream != null && mOutputStream != null) {
			return;
		}

		UsbAccessory[] accessories = mUsbManager.getAccessoryList();
		UsbAccessory accessory = (accessories == null ? null : accessories[0]);
		if (accessory != null) {
			if (mUsbManager.hasPermission(accessory)) {
				openAccessory(accessory);
			} else {
				synchronized (mUsbReceiver) {
					if (!mPermissionRequestPending) {
						mUsbManager.requestPermission(accessory,
								mPermissionIntent);
						mPermissionRequestPending = true;
					}
				}
			}
		} else {
			Log.d(TAG, "mAccessory is null");
		}
	}

	@Override
	public void onPause() {
		super.onPause();

		stopSensorListener();

		// and release our wake-lock
		mWakeLock.release();
		closeAccessory();
	}

	@Override
	public void onDestroy() {
		unregisterReceiver(mUsbReceiver);
		super.onDestroy();
	}

	private void openAccessory(UsbAccessory accessory) {
		mFileDescriptor = mUsbManager.openAccessory(accessory);
		if (mFileDescriptor != null) {
			mAccessory = accessory;
			FileDescriptor fd = mFileDescriptor.getFileDescriptor();
			mInputStream = new FileInputStream(fd);
			mOutputStream = new FileOutputStream(fd);
			Thread thread = new Thread(null, this, "DemoKit");
			thread.start();
			Log.d(TAG, "accessory opened");
			enableControls(true);
			sendUpdatesCommand(updateValuesCheckbox.isChecked());
		} else {
			Log.d(TAG, "accessory open fail");
		}
	}

	private void enableControls(boolean enable) {
		servo1SeekBar.setEnabled(enable);
		servo2SeekBar.setEnabled(enable);
		updateValuesCheckbox.setEnabled(enable);
	}

	private void closeAccessory() {
		enableControls(false);

		try {
			if (mFileDescriptor != null) {
				mFileDescriptor.close();
			}
		} catch (IOException e) {
		} finally {
			mFileDescriptor = null;
			mAccessory = null;
		}
	}

	public void sendCommand(byte command, byte value1, byte value2) {
		byte[] buffer = new byte[3];

		buffer[0] = command;
		buffer[1] = value1;
		buffer[2] = value2;
		if (mOutputStream != null && buffer[1] != -1) {
			try {
				mOutputStream.write(buffer);
			} catch (IOException e) {
				Log.e(TAG, "write failed", e);
			}
		}
	}

	@Override
	public void run() {
		int ret = 0;
		byte[] buffer = new byte[5];
		int incoming = 0;
		while (ret >= 0) {
			try {
				ret = mInputStream.read(buffer);

			} catch (IOException e) {
				break;
			}
			Log.d(TAG, "[0]:" + buffer[0] + " [1]:" + buffer[1] + " [2]:"
					+ buffer[2] + " [3]:" + buffer[3] + " [4]:" + buffer[4]);
			switch (buffer[0]) {
			case 1:
				incoming = buffer[1] * 256;
				incoming += buffer[2];
				Log.d(TAG, "current pwm1 is " + incoming);
				HelloADKActivity.this.updateIncomingSeekBar(incoming, 0);
				incoming = buffer[3] * 256;
				incoming += buffer[4];
				HelloADKActivity.this.updateIncomingSeekBar(incoming, 1);
				Log.d(TAG, "current pwm2 is " + incoming);
				break;
			default:
				Log.d(TAG, "unknown msg: " + buffer[0]);
				break;
			}

		}
	}

	@Override
	public void onProgressChanged(SeekBar arg0, int progress, boolean arg2) {
		byte highProgress = (byte) (progress / 256);
		byte lowProgress = (byte) (progress & 0x0f);
		if (arg0 == servo1SeekBar) {
			if (!mAutoPilotTurnedOn) {
				sendCommand(SERVO1_COMMAND, lowProgress, highProgress);
			}
			servo1EditText.setText("" + (progress - 1000));

		} else if (arg0 == servo2SeekBar) {
			if (!mAutoPilotTurnedOn) {
				sendCommand(SERVO2_COMMAND, lowProgress, highProgress);
			}
			servo2EditText.setText("" + (progress - 1000));
		}
	}

	@Override
	public void onStartTrackingTouch(SeekBar arg0) {
		// We do nothing
	}

	@Override
	public void onStopTrackingTouch(SeekBar arg0) {
		// we do nothing
	}

	@Override
	public void onSensorChanged(SensorEvent event) {
		if (event.sensor.getType() == Sensor.TYPE_ACCELEROMETER) {
			switch (mDisplay.getRotation()) {
			case Surface.ROTATION_0:
				mSensorX = event.values[0];
				mSensorY = event.values[1];
				mSensorZ = event.values[2];
				break;
			case Surface.ROTATION_90:
				mSensorX = -event.values[1];
				mSensorY = event.values[0];
				mSensorZ = event.values[2];
				break;
			case Surface.ROTATION_180:
				mSensorX = -event.values[0];
				mSensorY = -event.values[1];
				mSensorZ = event.values[2];
				break;
			case Surface.ROTATION_270:
				mSensorX = event.values[1];
				mSensorY = -event.values[0];
				mSensorZ = event.values[2];
				break;
			}

			int progress = 1000 + (int) (mSensorX * 200);
			byte highProgress = (byte) (progress / 256);
			byte lowProgress = (byte) (progress & 0x0f);
			sendCommand(SERVO2_COMMAND, lowProgress, highProgress);
			servo2SeekBar.setProgress(progress);
		}
	}

	@Override
	public void onAccuracyChanged(Sensor sensor, int accuracy) {
		// TODO Auto-generated method stub

	}

	@Override
	public void onCheckedChanged(CompoundButton arg0, boolean checked) {
		if (arg0 == updateValuesCheckbox) {
			sendUpdatesCommand(checked);
			Log.d(TAG, "onCheckedChanged");
			incoming1SeekBar.setVisibility(checked ? View.VISIBLE
					: View.INVISIBLE);
			incoming2SeekBar.setVisibility(checked ? View.VISIBLE
					: View.INVISIBLE);
			incoming1EditText.setVisibility(checked ? View.VISIBLE
					: View.INVISIBLE);
			incoming2EditText.setVisibility(checked ? View.VISIBLE
					: View.INVISIBLE);
		} else if (arg0 == mAutoPilotCheckBox) {
			if (checked) {
				startSensorListener();
			} else {
				stopSensorListener();
			}
		}

	}

	private void sendUpdatesCommand(boolean checked) {
		sendCommand(SEND_UPDATES_COMMAND, checked ? (byte) 1 : 0,
				checked ? (byte) 1 : 0);

	}
}
