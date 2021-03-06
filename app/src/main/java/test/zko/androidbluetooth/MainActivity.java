package test.zko.androidbluetooth;

import android.app.AlertDialog;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothDevice;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.os.Bundle;
import android.support.v7.app.AppCompatActivity;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.AdapterView;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.ListView;
import android.widget.ProgressBar;
import android.widget.ScrollView;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ToggleButton;

import java.util.Set;

import butterknife.Bind;
import butterknife.BindString;
import butterknife.ButterKnife;
import de.greenrobot.event.EventBus;
import test.zko.androidbluetooth.events.ConnectEvent;
import test.zko.androidbluetooth.events.LogEvent;
import test.zko.androidbluetooth.events.SendDataEvent;
import test.zko.androidbluetooth.events.UpdateDeviceEvent;
import test.zko.androidbluetooth.jobs.ConnectJob;


public class MainActivity extends AppCompatActivity {

    @Bind(R.id.btn_turn_on) Button mBtnOn;
    @Bind(R.id.btn_turn_off) Button mBtnOff;
    @Bind(R.id.btn_scan) Button mBtnScan;
    @Bind(R.id.btn_disconnect) Button mBtnDisconnect;
    @Bind(R.id.btn_clear_log) Button mBtnClearLog;
    @Bind(R.id.toggle_btn) ToggleButton mToggleBtn;
    @Bind(R.id.seekbar) SeekBar mSeekBar;
    @Bind(R.id.seekbar_value_text) TextView mSeekbarValueText;
    @Bind(R.id.main_status_scan_progressbar) ProgressBar mScanProgressbar;
    @Bind(R.id.main_status_text) TextView mStatusText;
    @Bind(R.id.log_text) TextView mLogText;
    @Bind(R.id.list_view) ListView mDevicesFoundListView;
    @Bind(R.id.scrollView) ScrollView mScrollView;

    @BindString(R.string.status_enabled) String ENABLED;
    @BindString(R.string.status_disabled) String DISABLED;

    private DevicesListAdapter mListViewAdapter;
    private BluetoothAdapter mBluetoothAdapter;
    private BroadcastReceiver mBluetoothReceiver;

    public static final int REQUEST_ENABLE_BT = 0;

    public static final byte SEND_SIGNAL = 1;
    public static final byte TOGGLE_ID = 2;
    public static final byte SEEKBAR_ID = 3;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter();

        ButterKnife.bind(this);

        if(mBluetoothAdapter.isEnabled()) {
            mStatusText.setText(ENABLED);
            mStatusText.setTextColor(getResources().getColor(R.color.black));
        } else {
            mStatusText.setText(DISABLED);
            mStatusText.setTextColor(getResources().getColor(R.color.red));
        }

        mSeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                EventBus.getDefault().post(new SendDataEvent(new byte[]{SEEKBAR_ID, (byte)progress},false));
                mSeekbarValueText.setText(String.valueOf(progress));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        mListViewAdapter = new DevicesListAdapter(this,R.layout.device_list_item);
        mDevicesFoundListView.setAdapter(mListViewAdapter);


        mDevicesFoundListView.setOnItemClickListener(new AdapterView.OnItemClickListener() {
            @Override
            public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
                BluetoothDevice device = (BluetoothDevice) view.getTag();
                createAlertDialog(device);
            }
        });

        setUpBroadcastReceiver();
        setUpButtons();
        getPairedDevices();
    }

    @Override
    protected void onStart() {
        super.onStart();
        EventBus.getDefault().register(this);
    }

    @Override
    protected void onStop() {
        EventBus.getDefault().unregister(this);
        super.onStop();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        unregisterReceiver(mBluetoothReceiver);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.menu_main, menu);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle action bar item clicks here. The action bar will
        // automatically handle clicks on the Home/Up button, so long
        // as you specify a parent activity in AndroidManifest.xml.
        int id = item.getItemId();

        //noinspection SimplifiableIfStatement
        if (id == R.id.action_settings) {
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    private void setUpButtons() {
        mBtnOn.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                if(mBluetoothAdapter.isEnabled()) {
                    Toast.makeText(getApplicationContext(),"Bluetooth is already on",Toast.LENGTH_SHORT).show();
                } else {
                    startActivityForResult(new Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE),REQUEST_ENABLE_BT);
                    Toast.makeText(getApplicationContext(),"Turning on...",Toast.LENGTH_SHORT).show();
                }
            }
        });

        mBtnOff.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mBluetoothAdapter.disable();
                mStatusText.setText(DISABLED);
                mStatusText.setTextColor(getResources().getColor(R.color.red));
            }
        });

        mBtnScan.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mScanProgressbar.setVisibility(View.VISIBLE);
                mListViewAdapter.clear();
                mBluetoothAdapter.startDiscovery();
            }
        });

        mBtnDisconnect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                EventBus.getDefault().post(new SendDataEvent(null, true));
            }
        });

        mBtnClearLog.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View v) {
                mLogText.setText("");
            }
        });

        mToggleBtn.setOnCheckedChangeListener(new CompoundButton.OnCheckedChangeListener() {
            @Override
            public void onCheckedChanged(CompoundButton buttonView, boolean isChecked) {
                if(isChecked) {
                    EventBus.getDefault().post(new SendDataEvent(new byte[] {TOGGLE_ID, (byte)1}, false));
                } else {
                    EventBus.getDefault().post(new SendDataEvent(new byte[] {TOGGLE_ID, (byte)0}, false));
                }
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == REQUEST_ENABLE_BT) {
            if (resultCode == RESULT_OK) {
                mStatusText.setText(ENABLED);
                mStatusText.setTextColor(getResources().getColor(R.color.black));
            }
        }
    }

    /**
     * Adds paired devices to the listview
     */
    private void getPairedDevices() {
        Set<BluetoothDevice> pairedDevices = mBluetoothAdapter.getBondedDevices();
        if (pairedDevices.size() > 0) {
            mListViewAdapter.addAll(pairedDevices);
            mListViewAdapter.notifyDataSetChanged();
        }
    }

    /**
     * Creates an alert dialog to check if the user wants to connect to the device or not
     * @param bluetoothDevice the device to connect to
     */
    private void createAlertDialog(final BluetoothDevice bluetoothDevice) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);

        builder.setTitle("Connect to device")
                .setMessage("Are you sure you want to connect to " + bluetoothDevice.getName() + " ?");

        builder.setPositiveButton("Yes", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                BluetoothApplication.getJobManager().addJobInBackground(new ConnectJob(bluetoothDevice));
            }
        });

        builder.setNegativeButton("No", new DialogInterface.OnClickListener() {
            @Override
            public void onClick(DialogInterface dialog, int which) {
                dialog.dismiss();
            }
        });

        builder.create().show();
    }

    private void setUpBroadcastReceiver() {
        mBluetoothReceiver = new BroadcastReceiver() {
            @Override
            public void onReceive(Context context, Intent intent) {
                if(BluetoothDevice.ACTION_FOUND.equals(intent.getAction())){
                    BluetoothDevice device = intent.getParcelableExtra(BluetoothDevice.EXTRA_DEVICE);
                    mListViewAdapter.add(device);
                    mListViewAdapter.notifyDataSetChanged();
                }
                mScanProgressbar.setVisibility(View.GONE);
            }
        };
        registerReceiver(mBluetoothReceiver, new IntentFilter(BluetoothDevice.ACTION_FOUND));
    }

    public void onEventMainThread(ConnectEvent event) {
        if(event.success) {
            mStatusText.setText("Connected to " + event.deviceName);
            mStatusText.setTextColor(getResources().getColor(R.color.green));
            mBtnDisconnect.setVisibility(View.VISIBLE);
            mSeekBar.setVisibility(View.VISIBLE);
            mSeekbarValueText.setVisibility(View.VISIBLE);
            mToggleBtn.setVisibility(View.VISIBLE);
        } else {
            mStatusText.setText("Disconnected");
            mStatusText.setTextColor(getResources().getColor(R.color.black));
            mBtnDisconnect.setVisibility(View.GONE);
            mSeekBar.setVisibility(View.GONE);
            mSeekbarValueText.setVisibility(View.GONE);
            mToggleBtn.setVisibility(View.GONE);
        }
    }

    public void onEventMainThread(LogEvent event) {
        mLogText.append(event.message+"\n");
        mScrollView.fullScroll(View.FOCUS_DOWN);
    }

    public void onEventMainThread(UpdateDeviceEvent event) {
        switch (event.deviceID) {
            case TOGGLE_ID:
                mLogText.append("Updating Toggle Button \n");
                if(event.deviceValue == 0) {
                    mToggleBtn.setChecked(false);
                } else {
                    mToggleBtn.setChecked(true);
                }
                break;
            case SEEKBAR_ID:
                mLogText.append("Updating Seekbar \n");
                mSeekBar.setProgress(Utility.convertByte(event.deviceValue));
                break;
        }
    }


}
