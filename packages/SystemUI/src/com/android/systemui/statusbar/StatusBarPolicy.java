/*
 * Copyright (C) 2008 The Android Open Source Project
 * Copyright (c) 2010-2011, Code Aurora Forum. All rights reserved.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.android.systemui.statusbar.policy;

import android.app.StatusBarManager;
import android.app.AlertDialog;
import android.bluetooth.BluetoothA2dp;
import android.bluetooth.BluetoothAdapter;
import android.bluetooth.BluetoothHeadset;
import android.bluetooth.BluetoothPbap;
import android.content.BroadcastReceiver;
import android.content.ContentResolver;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.res.TypedArray;
import android.graphics.PixelFormat;
import android.graphics.Typeface;
import android.graphics.drawable.Drawable;
import android.location.LocationManager;
import android.media.AudioManager;
import android.media.Ringtone;
import android.media.RingtoneManager;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.net.Uri;
import android.net.wifi.WifiManager;
import android.os.Binder;
import android.os.Handler;
import android.os.Message;
import android.os.RemoteException;
import android.os.storage.StorageManager;
import android.provider.Settings;
import android.telephony.PhoneStateListener;
import android.telephony.ServiceState;
import android.telephony.SignalStrength;
import android.telephony.TelephonyManager;
import android.text.format.DateFormat;
import android.text.style.CharacterStyle;
import android.text.style.RelativeSizeSpan;
import android.text.style.ForegroundColorSpan;
import android.text.style.StyleSpan;
import android.text.Spannable;
import android.text.SpannableStringBuilder;
import android.util.Slog;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.view.WindowManagerImpl;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.android.internal.app.IBatteryStats;
import com.android.internal.telephony.IccCard;
import com.android.internal.telephony.TelephonyIntents;
import com.android.internal.telephony.cdma.EriInfo;
import com.android.internal.telephony.cdma.TtyIntent;
import com.android.server.am.BatteryStatsService;

import com.android.systemui.R;

/**
 * This class contains all of the policy about which icons are installed in the status
 * bar at boot time.  It goes through the normal API for icons, even though it probably
 * strictly doesn't need to.
 */
public class StatusBarPolicy {
    private static final String TAG = "StatusBarPolicy";

    // message codes for the handler
    private static final int EVENT_BATTERY_CLOSE = 4;

    private static final int AM_PM_STYLE_NORMAL  = 0;
    private static final int AM_PM_STYLE_SMALL   = 1;
    private static final int AM_PM_STYLE_GONE    = 2;

    private static final int AM_PM_STYLE = AM_PM_STYLE_GONE;

    private static final int INET_CONDITION_THRESHOLD = 50;

    private final Context mContext;
    private final StatusBarManager mService;
    private final Handler mHandler = new StatusBarHandler();
    private final IBatteryStats mBatteryStats;

    // storage
    private StorageManager mStorageManager;

    // battery
    private boolean mBatteryFirst = true;
    private boolean mBatteryPlugged;
    private int mBatteryLevel;
    private AlertDialog mLowBatteryDialog;
    private TextView mBatteryLevelTextView;
    private View mBatteryView;
    private int mBatteryViewSequence;
    private boolean mBatteryShowLowOnEndCall = false;
    private static final boolean SHOW_LOW_BATTERY_WARNING = true;
    private static final boolean SHOW_BATTERY_WARNINGS_IN_CALL = true;

    private static final int LTE = 1;
    private static final int GSM = 2;
    private static final int CDMA = 3;
    private static final int EVDO = 4;
    private static final int INVALID_DATA_RADIO = 5;

    // phone
    private TelephonyManager mPhone;
    private int[] mPhoneSignalIconId;

    private boolean isInAirplaneMode = false;
    //***** Signal strength icons
    //GSM/UMTS
    private static final int[][] sSignalImages = {
        { R.drawable.stat_sys_signal_0,
          R.drawable.stat_sys_signal_1,
          R.drawable.stat_sys_signal_2,
          R.drawable.stat_sys_signal_3,
          R.drawable.stat_sys_signal_4 },
        { R.drawable.stat_sys_signal_0_fully,
          R.drawable.stat_sys_signal_1_fully,
          R.drawable.stat_sys_signal_2_fully,
          R.drawable.stat_sys_signal_3_fully,
          R.drawable.stat_sys_signal_4_fully }
    };
    private static final int[][] sSignalImages_r = {
        { R.drawable.stat_sys_r_signal_0,
          R.drawable.stat_sys_r_signal_1,
          R.drawable.stat_sys_r_signal_2,
          R.drawable.stat_sys_r_signal_3,
          R.drawable.stat_sys_r_signal_4 },
        { R.drawable.stat_sys_r_signal_0_fully,
          R.drawable.stat_sys_r_signal_1_fully,
          R.drawable.stat_sys_r_signal_2_fully,
          R.drawable.stat_sys_r_signal_3_fully,
          R.drawable.stat_sys_r_signal_4_fully }
    };
    private static final int[] sRoamingIndicatorImages_cdma = new int[] {
        R.drawable.stat_sys_roaming_cdma_0, //Standard Roaming Indicator
        // 1 is Standard Roaming Indicator OFF
        // TODO T: image never used, remove and put 0 instead?
        R.drawable.stat_sys_roaming_cdma_0,

        // 2 is Standard Roaming Indicator FLASHING
        // TODO T: image never used, remove and put 0 instead?
        R.drawable.stat_sys_roaming_cdma_0,

        // 3-12 Standard ERI
        R.drawable.stat_sys_roaming_cdma_0, //3
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,

        // 13-63 Reserved for Standard ERI
        R.drawable.stat_sys_roaming_cdma_0, //13
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,

        // 64-127 Reserved for Non Standard (Operator Specific) ERI
        R.drawable.stat_sys_roaming_cdma_0, //64
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0,
        R.drawable.stat_sys_roaming_cdma_0 //83

        // 128-255 Reserved
    };

    //***** Data connection icons
    private int[] mDataIconList = sDataNetType_g[0];
    //GSM/UMTS
    private static final int[][] sDataNetType_g = {
            { R.drawable.stat_sys_data_connected_g,
              R.drawable.stat_sys_data_in_g,
              R.drawable.stat_sys_data_out_g,
              R.drawable.stat_sys_data_inandout_g },
            { R.drawable.stat_sys_data_fully_connected_g,
              R.drawable.stat_sys_data_fully_in_g,
              R.drawable.stat_sys_data_fully_out_g,
              R.drawable.stat_sys_data_fully_inandout_g }
        };
    private static final int[][] sDataNetType_3g = {
            { R.drawable.stat_sys_data_connected_3g,
              R.drawable.stat_sys_data_in_3g,
              R.drawable.stat_sys_data_out_3g,
              R.drawable.stat_sys_data_inandout_3g },
            { R.drawable.stat_sys_data_fully_connected_3g,
              R.drawable.stat_sys_data_fully_in_3g,
              R.drawable.stat_sys_data_fully_out_3g,
              R.drawable.stat_sys_data_fully_inandout_3g }
        };
    private static final int[][] sDataNetType_e = {
            { R.drawable.stat_sys_data_connected_e,
              R.drawable.stat_sys_data_in_e,
              R.drawable.stat_sys_data_out_e,
              R.drawable.stat_sys_data_inandout_e },
            { R.drawable.stat_sys_data_fully_connected_e,
              R.drawable.stat_sys_data_fully_in_e,
              R.drawable.stat_sys_data_fully_out_e,
              R.drawable.stat_sys_data_fully_inandout_e }
        };
    //3.5G
    private static final int[][] sDataNetType_h = {
            { R.drawable.stat_sys_data_connected_h,
              R.drawable.stat_sys_data_in_h,
              R.drawable.stat_sys_data_out_h,
              R.drawable.stat_sys_data_inandout_h },
            { R.drawable.stat_sys_data_fully_connected_h,
              R.drawable.stat_sys_data_fully_in_h,
              R.drawable.stat_sys_data_fully_out_h,
              R.drawable.stat_sys_data_fully_inandout_h }
    };

    //CDMA
    // Use 3G icons for EVDO data and 1x icons for 1XRTT data
    private static final int[][] sDataNetType_1x = {
            { R.drawable.stat_sys_data_connected_1x,
              R.drawable.stat_sys_data_in_1x,
              R.drawable.stat_sys_data_out_1x,
              R.drawable.stat_sys_data_inandout_1x },
            { R.drawable.stat_sys_data_fully_connected_1x,
              R.drawable.stat_sys_data_fully_in_1x,
              R.drawable.stat_sys_data_fully_out_1x,
              R.drawable.stat_sys_data_fully_inandout_1x }
            };

    //4G icon for LTE
    private static final int[][] sDataNetType_lte = {
         { R.drawable.stat_sys_data_connected_4g,
           R.drawable.stat_sys_data_in_4g,
           R.drawable.stat_sys_data_out_4g,
           R.drawable.stat_sys_data_inandout_4g },
         { R.drawable.stat_sys_data_fully_connected_4g,
           R.drawable.stat_sys_data_fully_in_4g,
           R.drawable.stat_sys_data_fully_out_4g,
           R.drawable.stat_sys_data_fully_inandout_4g },
         };

    // Assume it's all good unless we hear otherwise.  We don't always seem
    // to get broadcasts that it *is* there.
    IccCard.State[] mSimState;
    int mPhoneState = TelephonyManager.CALL_STATE_IDLE;
    int mDataState = TelephonyManager.DATA_DISCONNECTED;
    int mDataActivity = TelephonyManager.DATA_ACTIVITY_NONE;
    ServiceState[] mServiceState;
    SignalStrength[] mSignalStrength;
    String[] mSignalIcon = {"phone_signal", "phone_signal_second_sub"};
    private PhoneStateListener[] mPhoneStateListener;

    // data connection
    private boolean mDataIconVisible;
    private boolean mHspaDataDistinguishable;

    // ringer volume
    private boolean mVolumeVisible;

    // bluetooth device status
    private int mBluetoothHeadsetState;
    private boolean mBluetoothA2dpConnected;
    private int mBluetoothPbapState;
    private boolean mBluetoothEnabled;

    // wifi
    private static final int[][] sWifiSignalImages = {
            { R.drawable.stat_sys_wifi_signal_1,
              R.drawable.stat_sys_wifi_signal_2,
              R.drawable.stat_sys_wifi_signal_3,
              R.drawable.stat_sys_wifi_signal_4 },
            { R.drawable.stat_sys_wifi_signal_1_fully,
              R.drawable.stat_sys_wifi_signal_2_fully,
              R.drawable.stat_sys_wifi_signal_3_fully,
              R.drawable.stat_sys_wifi_signal_4_fully }
        };
    private static final int sWifiTemporarilyNotConnectedImage =
            R.drawable.stat_sys_wifi_signal_0;

    private int mLastWifiSignalLevel = -1;
    private boolean mIsWifiConnected = false;

    // state of inet connection - 0 not connected, 100 connected
    private int mInetCondition = 0;

    // sync state
    // If sync is active the SyncActive icon is displayed. If sync is not active but
    // sync is failing the SyncFailing icon is displayed. Otherwise neither are displayed.

    private BroadcastReceiver mIntentReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            String action = intent.getAction();
            if (action.equals(Intent.ACTION_BATTERY_CHANGED)) {
                updateBattery(intent);
            }
            else if (action.equals(Intent.ACTION_ALARM_CHANGED)) {
                updateAlarm(intent);
            }
            else if (action.equals(Intent.ACTION_SYNC_STATE_CHANGED)) {
                updateSyncState(intent);
            }
            else if (action.equals(Intent.ACTION_BATTERY_LOW)) {
                onBatteryLow(intent);
            }
            else if (action.equals(Intent.ACTION_BATTERY_OKAY)
                    || action.equals(Intent.ACTION_POWER_CONNECTED)) {
                onBatteryOkay(intent);
            }
            else if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED) ||
                    action.equals(BluetoothHeadset.ACTION_STATE_CHANGED) ||
                    action.equals(BluetoothA2dp.ACTION_SINK_STATE_CHANGED) ||
                    action.equals(BluetoothPbap.PBAP_STATE_CHANGED_ACTION)) {
                updateBluetooth(intent);
            }
            else if (action.equals(WifiManager.NETWORK_STATE_CHANGED_ACTION) ||
                    action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION) ||
                    action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
                updateWifi(intent);
            }
            else if (action.equals(LocationManager.GPS_ENABLED_CHANGE_ACTION) ||
                    action.equals(LocationManager.GPS_FIX_CHANGE_ACTION)) {
                updateGps(intent);
            }
            else if (action.equals(AudioManager.RINGER_MODE_CHANGED_ACTION) ||
                    action.equals(AudioManager.VIBRATE_SETTING_CHANGED_ACTION)) {
                updateVolume();
            }
            else if (action.equals(TelephonyIntents.ACTION_SIM_STATE_CHANGED)) {
                updateSimState(intent);
            }
            else if (action.equals(TtyIntent.TTY_ENABLED_CHANGE_ACTION)) {
                updateTTY(intent);
            }
            else if (action.equals(ConnectivityManager.CONNECTIVITY_ACTION) ||
                     action.equals(ConnectivityManager.INET_CONDITION_ACTION)) {
                // TODO - stop using other means to get wifi/mobile info
                updateConnectivity(intent);
            }
        }
    };

    public StatusBarPolicy(Context context) {
        mContext = context;
        mService = (StatusBarManager)context.getSystemService(Context.STATUS_BAR_SERVICE);
        mBatteryStats = BatteryStatsService.getService();

        // storage
        mStorageManager = (StorageManager) context.getSystemService(Context.STORAGE_SERVICE);
        mStorageManager.registerListener(
                new com.android.systemui.usb.StorageNotification(context));

        // battery
        mService.setIcon("battery", com.android.internal.R.drawable.stat_sys_battery_unknown, 0);

        int numPhones = TelephonyManager.getPhoneCount();
        mSignalStrength = new SignalStrength[numPhones];
        mServiceState = new ServiceState[numPhones];
        mSimState = new IccCard.State[numPhones];
        mPhoneSignalIconId = new int[numPhones];
        mPhoneStateListener = new PhoneStateListener[numPhones];

        mPhone = (TelephonyManager)context.getSystemService(Context.TELEPHONY_SERVICE);

        for (int i=0; i < numPhones; i++) {
            mSignalStrength[i] = new SignalStrength();
            mServiceState[i] = new ServiceState();
            mSimState[i] = IccCard.State.READY;
            // phone_signal
            mPhoneSignalIconId[i] = R.drawable.stat_sys_signal_null;
            mService.setIcon(mSignalIcon[i], mPhoneSignalIconId[i], 0);
            mPhoneStateListener[i] = getPhoneStateListener(i);

            // register for phone state notifications.
            ((TelephonyManager)mContext.getSystemService(Context.TELEPHONY_SERVICE))
                    .listen(mPhoneStateListener[i],
                              PhoneStateListener.LISTEN_SERVICE_STATE
                            | PhoneStateListener.LISTEN_SIGNAL_STRENGTHS
                            | PhoneStateListener.LISTEN_CALL_STATE
                            | PhoneStateListener.LISTEN_DATA_CONNECTION_STATE
                            | PhoneStateListener.LISTEN_DATA_ACTIVITY);
        }

        // data_connection
        mService.setIcon("data_connection", R.drawable.stat_sys_data_connected_g, 0);
        mService.setIconVisibility("data_connection", false);

        // wifi
        mService.setIcon("wifi", sWifiSignalImages[0][0], 0);
        mService.setIconVisibility("wifi", false);
        // wifi will get updated by the sticky intents

        // TTY status
        mService.setIcon("tty",  R.drawable.stat_sys_tty_mode, 0);
        mService.setIconVisibility("tty", false);

        // Cdma Roaming Indicator, ERI
        mService.setIcon("cdma_eri", R.drawable.stat_sys_roaming_cdma_0, 0);
        mService.setIconVisibility("cdma_eri", false);

        // bluetooth status
        mService.setIcon("bluetooth", R.drawable.stat_sys_data_bluetooth, 0);
        BluetoothAdapter adapter = BluetoothAdapter.getDefaultAdapter();
        if (adapter != null) {
            mBluetoothEnabled = adapter.isEnabled();
        } else {
            mBluetoothEnabled = false;
        }
        mBluetoothA2dpConnected = false;
        mBluetoothHeadsetState = BluetoothHeadset.STATE_DISCONNECTED;
        mBluetoothPbapState = BluetoothPbap.STATE_DISCONNECTED;
        mService.setIconVisibility("bluetooth", mBluetoothEnabled);

        // Gps status
        mService.setIcon("gps", R.drawable.stat_sys_gps_acquiring_anim, 0);
        mService.setIconVisibility("gps", false);

        // Alarm clock
        mService.setIcon("alarm_clock", R.drawable.stat_notify_alarm, 0);
        mService.setIconVisibility("alarm_clock", false);

        // Sync state
        mService.setIcon("sync_active", com.android.internal.R.drawable.stat_notify_sync_anim0, 0);
        mService.setIcon("sync_failing", com.android.internal.R.drawable.stat_notify_sync_error, 0);
        mService.setIconVisibility("sync_active", false);
        mService.setIconVisibility("sync_failing", false);

        // volume
        mService.setIcon("volume", R.drawable.stat_sys_ringer_silent, 0);
        mService.setIconVisibility("volume", false);
        updateVolume();

        IntentFilter filter = new IntentFilter();

        // Register for Intent broadcasts for...
        filter.addAction(Intent.ACTION_BATTERY_CHANGED);
        filter.addAction(Intent.ACTION_BATTERY_LOW);
        filter.addAction(Intent.ACTION_BATTERY_OKAY);
        filter.addAction(Intent.ACTION_POWER_CONNECTED);
        filter.addAction(Intent.ACTION_ALARM_CHANGED);
        filter.addAction(Intent.ACTION_SYNC_STATE_CHANGED);
        filter.addAction(AudioManager.RINGER_MODE_CHANGED_ACTION);
        filter.addAction(AudioManager.VIBRATE_SETTING_CHANGED_ACTION);
        filter.addAction(BluetoothAdapter.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothHeadset.ACTION_STATE_CHANGED);
        filter.addAction(BluetoothA2dp.ACTION_SINK_STATE_CHANGED);
        filter.addAction(BluetoothPbap.PBAP_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.WIFI_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION);
        filter.addAction(WifiManager.NETWORK_STATE_CHANGED_ACTION);
        filter.addAction(WifiManager.RSSI_CHANGED_ACTION);
        filter.addAction(LocationManager.GPS_ENABLED_CHANGE_ACTION);
        filter.addAction(LocationManager.GPS_FIX_CHANGE_ACTION);
        filter.addAction(TelephonyIntents.ACTION_SIM_STATE_CHANGED);
        filter.addAction(TtyIntent.TTY_ENABLED_CHANGE_ACTION);
        filter.addAction(ConnectivityManager.CONNECTIVITY_ACTION);
        filter.addAction(ConnectivityManager.INET_CONDITION_ACTION);
        mContext.registerReceiver(mIntentReceiver, filter, null, mHandler);

        // load config to determine if to distinguish Hspa data icon
        try {
            mHspaDataDistinguishable = mContext.getResources().getBoolean(
                    R.bool.config_hspa_data_distinguishable);
        } catch (Exception e) {
            mHspaDataDistinguishable = false;
        }
    }

    private final void updateAlarm(Intent intent) {
        boolean alarmSet = intent.getBooleanExtra("alarmSet", false);
        mService.setIconVisibility("alarm_clock", alarmSet);
    }

    private final void updateSyncState(Intent intent) {
        boolean isActive = intent.getBooleanExtra("active", false);
        boolean isFailing = intent.getBooleanExtra("failing", false);
        mService.setIconVisibility("sync_active", isActive);
        // Don't display sync failing icon: BUG 1297963 Set sync error timeout to "never"
        //mService.setIconVisibility("sync_failing", isFailing && !isActive);
    }

    private final void updateBattery(Intent intent) {
        final int id = intent.getIntExtra("icon-small", 0);
        int level = intent.getIntExtra("level", 0);
        mService.setIcon("battery", id, level);

        boolean plugged = intent.getIntExtra("plugged", 0) != 0;
        level = intent.getIntExtra("level", -1);
        if (false) {
            Slog.d(TAG, "updateBattery level=" + level
                    + " plugged=" + plugged
                    + " mBatteryPlugged=" + mBatteryPlugged
                    + " mBatteryLevel=" + mBatteryLevel
                    + " mBatteryFirst=" + mBatteryFirst);
        }

        boolean oldPlugged = mBatteryPlugged;

        mBatteryPlugged = plugged;
        mBatteryLevel = level;

        if (mBatteryFirst) {
            mBatteryFirst = false;
        }
        /*
         * No longer showing the battery view because it draws attention away
         * from the USB storage notification. We could still show it when
         * connected to a brick, but that could lead to the user into thinking
         * the device does not charge when plugged into USB (since he/she would
         * not see the same battery screen on USB as he sees on brick).
         */
        if (false) {
            Slog.d(TAG, "plugged=" + plugged + " oldPlugged=" + oldPlugged + " level=" + level);
        }
    }

    private void onBatteryLow(Intent intent) {
        if (SHOW_LOW_BATTERY_WARNING) {
            if (false) {
                Slog.d(TAG, "mPhoneState=" + mPhoneState
                      + " mLowBatteryDialog=" + mLowBatteryDialog
                      + " mBatteryShowLowOnEndCall=" + mBatteryShowLowOnEndCall);
            }

            if (SHOW_BATTERY_WARNINGS_IN_CALL || mPhoneState == TelephonyManager.CALL_STATE_IDLE) {
                showLowBatteryWarning();
            } else {
                mBatteryShowLowOnEndCall = true;
            }
        }
    }

    private void onBatteryOkay(Intent intent) {
        if (mLowBatteryDialog != null
                && SHOW_LOW_BATTERY_WARNING) {
            mLowBatteryDialog.dismiss();
            mBatteryShowLowOnEndCall = false;
        }
    }

    private void setBatteryLevel(View parent, int id, int height, int background, int level) {
        ImageView v = (ImageView)parent.findViewById(id);
        LinearLayout.LayoutParams lp = (LinearLayout.LayoutParams)v.getLayoutParams();
        lp.weight = height;
        if (background != 0) {
            v.setBackgroundResource(background);
            Drawable bkg = v.getBackground();
            bkg.setLevel(level);
        }
    }

    private void showLowBatteryWarning() {
        closeLastBatteryView();

        // Show exact battery level.
        CharSequence levelText = mContext.getString(
                    R.string.battery_low_percent_format, mBatteryLevel);

        if (mBatteryLevelTextView != null) {
            mBatteryLevelTextView.setText(levelText);
        } else {
            View v = View.inflate(mContext, R.layout.battery_low, null);
            mBatteryLevelTextView=(TextView)v.findViewById(R.id.level_percent);

            mBatteryLevelTextView.setText(levelText);

            AlertDialog.Builder b = new AlertDialog.Builder(mContext);
                b.setCancelable(true);
                b.setTitle(R.string.battery_low_title);
                b.setView(v);
                b.setIcon(android.R.drawable.ic_dialog_alert);
                b.setPositiveButton(android.R.string.ok, null);

                final Intent intent = new Intent(Intent.ACTION_POWER_USAGE_SUMMARY);
                intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                        | Intent.FLAG_ACTIVITY_MULTIPLE_TASK
                        | Intent.FLAG_ACTIVITY_EXCLUDE_FROM_RECENTS
                        | Intent.FLAG_ACTIVITY_NO_HISTORY);
                if (intent.resolveActivity(mContext.getPackageManager()) != null) {
                    b.setNegativeButton(R.string.battery_low_why,
                            new DialogInterface.OnClickListener() {
                        public void onClick(DialogInterface dialog, int which) {
                            mContext.startActivity(intent);
                            if (mLowBatteryDialog != null) {
                                mLowBatteryDialog.dismiss();
                            }
                        }
                    });
                }

            AlertDialog d = b.create();
            d.setOnDismissListener(mLowBatteryListener);
            d.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
            d.show();
            mLowBatteryDialog = d;
        }

        final ContentResolver cr = mContext.getContentResolver();
        if (Settings.System.getInt(cr,
                Settings.System.POWER_SOUNDS_ENABLED, 1) == 1)
        {
            final String soundPath = Settings.System.getString(cr,
                Settings.System.LOW_BATTERY_SOUND);
            if (soundPath != null) {
                final Uri soundUri = Uri.parse("file://" + soundPath);
                if (soundUri != null) {
                    final Ringtone sfx = RingtoneManager.getRingtone(mContext, soundUri);
                    if (sfx != null) {
                        sfx.setStreamType(AudioManager.STREAM_SYSTEM);
                        sfx.play();
                    }
                }
            }
        }
    }

    private final void updateCallState(int state) {
        mPhoneState = state;
        if (false) {
            Slog.d(TAG, "mPhoneState=" + mPhoneState
                    + " mLowBatteryDialog=" + mLowBatteryDialog
                    + " mBatteryShowLowOnEndCall=" + mBatteryShowLowOnEndCall);
        }
        if (mPhoneState == TelephonyManager.CALL_STATE_IDLE) {
            if (mBatteryShowLowOnEndCall) {
                if (!mBatteryPlugged) {
                    showLowBatteryWarning();
                }
                mBatteryShowLowOnEndCall = false;
            }
        } else {
            if (mLowBatteryDialog != null) {
                mLowBatteryDialog.dismiss();
                mBatteryShowLowOnEndCall = true;
            }
        }
    }

    private DialogInterface.OnDismissListener mLowBatteryListener
            = new DialogInterface.OnDismissListener() {
        public void onDismiss(DialogInterface dialog) {
            mLowBatteryDialog = null;
            mBatteryLevelTextView = null;
        }
    };

    private void scheduleCloseBatteryView() {
        Message m = mHandler.obtainMessage(EVENT_BATTERY_CLOSE);
        m.arg1 = (++mBatteryViewSequence);
        mHandler.sendMessageDelayed(m, 3000);
    }

    private void closeLastBatteryView() {
        if (mBatteryView != null) {
            //mBatteryView.debug();
            WindowManagerImpl.getDefault().removeView(mBatteryView);
            mBatteryView = null;
        }
    }

    private void updateConnectivity(Intent intent) {
        NetworkInfo info = (NetworkInfo)(intent.getParcelableExtra(
                ConnectivityManager.EXTRA_NETWORK_INFO));
        int connectionStatus = intent.getIntExtra(ConnectivityManager.EXTRA_INET_CONDITION, 0);

        int inetCondition = (connectionStatus > INET_CONDITION_THRESHOLD ? 1 : 0);

        switch (info.getType()) {
        case ConnectivityManager.TYPE_MOBILE:
            mInetCondition = inetCondition;
            updateDataNetType(info.getSubtype());
            updateDataIcon(TelephonyManager.getPreferredDataSubscription());
            updateSignalStrength(TelephonyManager.getPreferredDataSubscription()); // apply any change in connectionStatus
            break;
        case ConnectivityManager.TYPE_WIFI:
            mInetCondition = inetCondition;
            if (info.isConnected()) {
                mIsWifiConnected = true;
                int iconId;
                if (mLastWifiSignalLevel == -1) {
                    iconId = sWifiSignalImages[mInetCondition][0];
                } else {
                    iconId = sWifiSignalImages[mInetCondition][mLastWifiSignalLevel];
                }
                mService.setIcon("wifi", iconId, 0);
                // Show the icon since wi-fi is connected
                mService.setIconVisibility("wifi", true);
            } else {
                mLastWifiSignalLevel = -1;
                mIsWifiConnected = false;
                int iconId = sWifiSignalImages[0][0];

                mService.setIcon("wifi", iconId, 0);
                // Hide the icon since we're not connected
                mService.setIconVisibility("wifi", false);
            }
            updateSignalStrength(TelephonyManager.getPreferredDataSubscription()); // apply any change in mInetCondition
            break;
        }
    }

    private PhoneStateListener getPhoneStateListener(int subscription) {
        PhoneStateListener phoneStateListener = new PhoneStateListener(subscription) {
            @Override
            public void onSignalStrengthsChanged(SignalStrength signalStrength) {
                // mSubscription is a data member of PhoneStateListener class.
                Slog.d(TAG, "onSignalStrengthsChanged:" + signalStrength
                        + " for subscription" + mSubscription);
                mSignalStrength[mSubscription] = signalStrength;
                updateSignalStrength(mSubscription);
            }

            public void onServiceStateChanged(ServiceState state) {
                Slog.d(TAG, "onServiceStateChanged:" + state + "for subscription :" + mSubscription);
                mServiceState[mSubscription] = state;
                updateSignalStrength(mSubscription);
                updateDataIcon(mSubscription);
            }

            @Override
            public void onCallStateChanged(int state, String incomingNumber) {
                Slog.e(TAG, "onCallStateChanged Received on subscription :" + mSubscription);
                updateCallState(state);
                // In cdma, if a voice call is made, RSSI should switch to 1x.
                updateSignalStrength(mSubscription);
            }

            @Override
            public void onDataConnectionStateChanged(int state, int networkType) {
                Slog.d(TAG, "StatusBarPolicy onDataConnectionStateChanged to " + state
                        + "for nw : " + networkType + "on subscription : " + mSubscription);
                mDataState = state;
                updateDataNetType(networkType);
                updateDataIcon(mSubscription);
            }

            @Override
            public void onDataActivity(int direction) {
                mDataActivity = direction;
                updateDataIcon(mSubscription);
            }
        };
        return phoneStateListener;
    }

    private final void updateSimState(Intent intent) {
        IccCard.State simState;
        String stateExtra = intent.getStringExtra(IccCard.INTENT_KEY_ICC_STATE);
        // Obtain the subscription info from intent.
        int sub = intent.getIntExtra(IccCard.INTENT_KEY_SUBSCRIPTION, 0);
        Slog.d(TAG, "updateSimState for subscription :" + sub);
        if (IccCard.INTENT_VALUE_ICC_ABSENT.equals(stateExtra)) {
            simState = IccCard.State.ABSENT;
        }
        else if (IccCard.INTENT_VALUE_ICC_CARD_IO_ERROR.equals(stateExtra)) {
            simState = IccCard.State.CARD_IO_ERROR;
        }
        else if (IccCard.INTENT_VALUE_ICC_READY.equals(stateExtra)) {
            simState = IccCard.State.READY;
        }
        else if (IccCard.INTENT_VALUE_ICC_LOCKED.equals(stateExtra)) {
            final String lockedReason = intent.getStringExtra(IccCard.INTENT_KEY_LOCKED_REASON);
            if (IccCard.INTENT_VALUE_LOCKED_ON_PIN.equals(lockedReason)) {
                simState = IccCard.State.PIN_REQUIRED;
            } else if (IccCard.INTENT_VALUE_LOCKED_ON_PUK.equals(lockedReason)) {
                simState = IccCard.State.PUK_REQUIRED;
            } else if(IccCard.INTENT_VALUE_LOCKED_NETWORK.equals(lockedReason)) {
                simState = IccCard.State.NETWORK_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_NETWORK_SUBSET.equals(lockedReason)) {
                simState = IccCard.State.SIM_NETWORK_SUBSET_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_CORPORATE.equals(lockedReason)) {
                simState = IccCard.State.SIM_CORPORATE_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_SERVICE_PROVIDER.equals(lockedReason)) {
                simState = IccCard.State.SIM_SERVICE_PROVIDER_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_SIM.equals(lockedReason)) {
                simState = IccCard.State.SIM_SIM_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_NETWORK1.equals(lockedReason)) {
                simState = IccCard.State.RUIM_NETWORK1_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_NETWORK2.equals(lockedReason)) {
                simState = IccCard.State.RUIM_NETWORK2_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_HRPD.equals(lockedReason)) {
                simState = IccCard.State.RUIM_HRPD_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_CORPORATE.equals(lockedReason)) {
                simState = IccCard.State.RUIM_CORPORATE_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_SERVICE_PROVIDER.equals(lockedReason)) {
                simState = IccCard.State.RUIM_SERVICE_PROVIDER_LOCKED;
            } else if(IccCard.INTENT_VALUE_LOCKED_RUIM_RUIM.equals(lockedReason)) {
                simState = IccCard.State.RUIM_RUIM_LOCKED;
            } else {
                simState = IccCard.State.NETWORK_LOCKED;
            }
        } else {
            simState = IccCard.State.UNKNOWN;
        }
        mSimState[sub] = simState;
        updateDataIcon(sub);
    }

    private int dataRadio(int subscription) {

        if (mServiceState[subscription] == null) {
            Slog.e(TAG, "Service state not updated");
            return INVALID_DATA_RADIO;
        }

        /* find out radio technology by looking at service state */
        switch (mServiceState[subscription].getRadioTechnology()) {
            case ServiceState.RADIO_TECHNOLOGY_LTE:
                return LTE;
            case ServiceState.RADIO_TECHNOLOGY_EVDO_0:
            case ServiceState.RADIO_TECHNOLOGY_EVDO_A:
            case ServiceState.RADIO_TECHNOLOGY_EVDO_B:
            case ServiceState.RADIO_TECHNOLOGY_EHRPD:
                return EVDO;
            case ServiceState.RADIO_TECHNOLOGY_IS95A:
            case ServiceState.RADIO_TECHNOLOGY_IS95B:
            case ServiceState.RADIO_TECHNOLOGY_1xRTT:
                return CDMA;
            case ServiceState.RADIO_TECHNOLOGY_GPRS:
            case ServiceState.RADIO_TECHNOLOGY_EDGE:
            case ServiceState.RADIO_TECHNOLOGY_UMTS:
            case ServiceState.RADIO_TECHNOLOGY_HSDPA:
            case ServiceState.RADIO_TECHNOLOGY_HSUPA:
            case ServiceState.RADIO_TECHNOLOGY_HSPA:
                return GSM;
            default:
                return INVALID_DATA_RADIO;
        }
    }

    private boolean hasService(int subscription) {
        ServiceState ss = mServiceState[subscription];
        if (ss != null) {
            switch (ss.getState()) {
                case ServiceState.STATE_OUT_OF_SERVICE:
                case ServiceState.STATE_POWER_OFF:
                    return false;
                default:
                    return true;
            }
        } else {
            return false;
        }
    }

    private final void updateIcon(int subscription, int signal) {
        mPhoneSignalIconId[subscription] = signal;
        mService.setIcon(mSignalIcon[subscription], mPhoneSignalIconId[subscription], 0);
    }

    private final void updateSignalStrength(int subscription) {
        int iconLevel = -1;
        int[] iconList;

        Slog.d(TAG,"updateSignalStrength on subscription :" + subscription);
        updateCdmaRoamingIcon(subscription);
        // Display signal strength while in "emergency calls only" mode

        if ((mSignalStrength[subscription] == null) || (mServiceState[subscription] == null)
                || (!hasService(subscription) && !mServiceState[subscription].isEmergencyOnly())) {
            //Slog.d(TAG, "updateSignalStrength: no service");
            int numPhones = TelephonyManager.getPhoneCount();
            if (Settings.System.getInt(mContext.getContentResolver(),
                    Settings.System.AIRPLANE_MODE_ON, 0) == 1) {
                /*
                 * To display single airplane annuciator for both the subscriptions,
                 * while in  Airplane Mode.
                 */
                isInAirplaneMode = true;
                updateIcon(0, R.drawable.stat_sys_signal_flightmode);
                for (int sub=1; sub < numPhones; sub++) {
                    mService.setIconVisibility(mSignalIcon[sub], false);
                }
            } else {
                if (isInAirplaneMode == true) {
                    for (int i=0; i < numPhones; i++) {
                        updateIcon(i, R.drawable.stat_sys_signal_null);
                    }
                    isInAirplaneMode = false;
                } else {
                    updateIcon(subscription, R.drawable.stat_sys_signal_null);
                }
            }
            return;
        }

        /*
         * Determine which radio tech signal level should be displayed based on
         * 1.phone type (voice and/or/no data), 2. call state( idle/data or in
         * voice call ), 3. radio tech
         */
        iconLevel = getIconLevel(subscription);

        if (iconLevel == -1) {
            mPhoneSignalIconId[subscription] = R.drawable.stat_sys_signal_null;
        } else {
            /*
             * Determine which icon should be displayed. Assumption - for
             * roaming ( voice and/or/no data) the roaming icon corresponding to
             * voice technology will be displayed
             */
            if (mServiceState[subscription].getRoaming()) {
                if (mSignalStrength[subscription].isGsm()) {
                    iconList = sSignalImages_r[mInetCondition];
                } else {
                    /* roaming on CDMA, CDMA roaming indicator will be on */
                    iconList = sSignalImages[mInetCondition];
                }
            } else {
                iconList = sSignalImages[mInetCondition];
            }
            mPhoneSignalIconId[subscription] = iconList[iconLevel];
        }

        mService.setIcon(mSignalIcon[subscription], mPhoneSignalIconId[subscription], 0);
        return;
    }

    private int getIconLevel(int subscription) {
        int iconLevel = -1;
        int radio = dataRadio(subscription);
        if ((mPhoneState != TelephonyManager.CALL_STATE_IDLE)
                || (radio == INVALID_DATA_RADIO))
        {
            /*
             * phone is in voice call or voice only network
             * display voice tech signal. isGsm has voice tech.
             * For GSM - isGSM flag is on. For CDMA 1x -isGSM flag
             * is off
             */
            if (mSignalStrength[subscription].isGsm()) { // Gsm voice call
                iconLevel = getGsmLevel(subscription);
            } else { // cdma voice call
                iconLevel = getCdmaLevel(subscription);
            }
        } else {
            /*
             * phone is not in voice call display data radio tech signal by
             * looking at service state. If data radio tech is not available ,
             * display voice radio signal by looking at signal strength
             */
            switch (radio) {
                case LTE:
                    /* LTE data tech */
                    iconLevel = getLteLevel(subscription);
                    break;
                case GSM:
                    /* 3GPP GSM data tech */
                    iconLevel = getGsmLevel(subscription);
                    break;
                case EVDO:
                    /* 3GPP2 EVDO data tech or EHRPD */
                    iconLevel = getEvdoLevel(subscription);
                    break;
                case CDMA:
                    iconLevel = getCdmaLevel(subscription);
                    break;
            } // end of switch
        }// end of CALL_STATE_IDLE
        return iconLevel;
    }

    private int getGsmLevel(int subscription) {
        int asu = mSignalStrength[subscription].getGsmSignalStrength();
        int iconLevel = -1;

        // ASU ranges from 0 to 31 - TS 27.007 Sec 8.5
        // asu = 0 (-113dB or less) is very weak
        // signal, its better to show 0 bars to the user in such cases.
        // asu = 99 is a special case, where the signal strength is unknown.
        if (asu <= 2 || asu == 99) iconLevel = 0;
        else if (asu >= 12) iconLevel = 4;
        else if (asu >= 8)  iconLevel = 3;
        else if (asu >= 5)  iconLevel = 2;
        else iconLevel = 1;

        return iconLevel;
    }

    private int getLteLevel(int subscription) {

        /*
         * TS 36.214 Physical Layer Section 5.1.3
         * TS 36.331 RRC
         * RSSI = received signal + noise
         * RSRP = reference signal dBm
         * RSRQ = quality of signal dB= Number of Resource blocksxRSRP/RSSI
         * SNR = gain=signal/noise ratio = -10log P1/P2 dB
         * CQI = channel quality = ?
         */
        int rssi, rsrp, snr;
        int rssiIconLevel = 0, rsrpIconLevel = -1, snrIconLevel = -1;

        rsrp = mSignalStrength[subscription].getLteRsrp();

        /*
         *  The current Reference Signal Receive Power Range: -44 to -140 dBm
         *  RSRP >= -85 dBm => 4 bars
         * -95 dBm <= RSRP < -85 dBm => 3bars
         * -105 dBm <= RSRP < -95 dBm => 2
         * -115 dBm <= RSRP < -105 dBm => 1
         *  RSRP < -115 dBm/No Service Antenna Icon Only
         *  RSRP ref: TS 33.331 - 6.3.5 range 0-97
         */
        if (rsrp > -44) rsrpIconLevel = -1;
        else if (rsrp >= -85) rsrpIconLevel = 4;
        else if (rsrp >= -95) rsrpIconLevel = 3;
        else if (rsrp >= -105) rsrpIconLevel = 2;
        else if (rsrp >= -115) rsrpIconLevel = 1;
        else if (rsrp >= -140)rsrpIconLevel = 0;


        snr = mSignalStrength[subscription].getLteSnr();
        /*
         * Values are -200 dB to +300 (= SNR *10dB )
         * RS_SNR >= 13.0 dB =>4 bars
         * 4.5 dB <= RS_SNR < 13.0 dB => 3 bars
         * 1.0 dB <= RS_SNR < 4.5 dB => 2 bars
         * -3.0 dB <= RS_SNR < 1.0 dB 1 bar
         * RS_SNR < -3.0 dB/No Service Antenna Icon Only
         */
        if (snr > 300) snrIconLevel = -1;
        else if (snr >= 130) snrIconLevel = 4;
        else if (snr >= 45) snrIconLevel = 3;
        else if (snr >= 10) snrIconLevel = 2;
        else if (snr >= -30) snrIconLevel = 1;
        else if (snr >= -200)snrIconLevel = 0;

        Slog.d(TAG,"getLTELevel - rsrp:"+ rsrp + " snr:"+ snr);

        /* Choose a measurement type to use for notification */
        if ( snrIconLevel != -1 && rsrpIconLevel != -1){
            /*
             * The number of bars displayed shall be the smaller of the bars
             * associated with LTE RSRP and the bars associated with the LTE
             * RS_SNR
             */
            return (rsrpIconLevel < snrIconLevel ? rsrpIconLevel : snrIconLevel);
        }

        if (snrIconLevel != -1 )
        {
            return snrIconLevel;
        }

        if (rsrpIconLevel != -1 ) {
            return rsrpIconLevel;
        }

        rssi = mSignalStrength[subscription].getLteRssi();
        /* Valid values are (0-63, 99) as defined in TS 36.331 */
        Slog.d(TAG,"getLTELevel -rssi:"+ rssi);
        if (rssi > 63) rssiIconLevel = 0;
        else if (rssi >= 12) rssiIconLevel = 4;
        else if (rssi >= 8)  rssiIconLevel = 3;
        else if (rssi >= 5) rssiIconLevel = 2;
        else if ( rssi >= 0 ) rssiIconLevel = 1;

        return rssiIconLevel;

    }

    private int getCdmaLevel(int subscription) {
        final int cdmaDbm = mSignalStrength[subscription].getCdmaDbm();
        final int cdmaEcio = mSignalStrength[subscription].getCdmaEcio();
        int levelDbm = 0;
        int levelEcio = 0;

        if (cdmaDbm >= -75) levelDbm = 4;
        else if (cdmaDbm >= -85) levelDbm = 3;
        else if (cdmaDbm >= -95) levelDbm = 2;
        else if (cdmaDbm >= -100) levelDbm = 1;
        else levelDbm = 0;

        // Ec/Io are in dB*10
        if (cdmaEcio >= -90) levelEcio = 4;
        else if (cdmaEcio >= -110) levelEcio = 3;
        else if (cdmaEcio >= -130) levelEcio = 2;
        else if (cdmaEcio >= -150) levelEcio = 1;
        else levelEcio = 0;

        return (levelDbm < levelEcio) ? levelDbm : levelEcio;
    }

    private int getEvdoLevel(int subscription) {
        int evdoDbm = mSignalStrength[subscription].getEvdoDbm();
        int evdoSnr = mSignalStrength[subscription].getEvdoSnr();
        int levelEvdoDbm = 0;
        int levelEvdoSnr = 0;

        if (evdoDbm >= -65) levelEvdoDbm = 4;
        else if (evdoDbm >= -75) levelEvdoDbm = 3;
        else if (evdoDbm >= -90) levelEvdoDbm = 2;
        else if (evdoDbm >= -105) levelEvdoDbm = 1;
        else levelEvdoDbm = 0;

        if (evdoSnr >= 7) levelEvdoSnr = 4;
        else if (evdoSnr >= 5) levelEvdoSnr = 3;
        else if (evdoSnr >= 3) levelEvdoSnr = 2;
        else if (evdoSnr >= 1) levelEvdoSnr = 1;
        else levelEvdoSnr = 0;

        return (levelEvdoDbm < levelEvdoSnr) ? levelEvdoDbm : levelEvdoSnr;
    }

    private final void updateDataNetType(int net) {
        Slog.d(TAG,"Data network type changed to:" + net );
        switch (net) {
        case TelephonyManager.NETWORK_TYPE_EDGE:
            mDataIconList = sDataNetType_e[mInetCondition];
            break;
        case TelephonyManager.NETWORK_TYPE_UMTS:
            mDataIconList = sDataNetType_3g[mInetCondition];
            break;
        case TelephonyManager.NETWORK_TYPE_HSDPA:
        case TelephonyManager.NETWORK_TYPE_HSUPA:
        case TelephonyManager.NETWORK_TYPE_HSPA:
            if (mHspaDataDistinguishable) {
                mDataIconList = sDataNetType_h[mInetCondition];
            } else {
                mDataIconList = sDataNetType_3g[mInetCondition];
            }
            break;
        case TelephonyManager.NETWORK_TYPE_CDMA:
            // display 1xRTT for IS95A/B
            mDataIconList = sDataNetType_1x[mInetCondition];
            break;
        case TelephonyManager.NETWORK_TYPE_1xRTT:
            mDataIconList = sDataNetType_1x[mInetCondition];
            break;
        case TelephonyManager.NETWORK_TYPE_EVDO_0: //fall through
        case TelephonyManager.NETWORK_TYPE_EVDO_A:
        case TelephonyManager.NETWORK_TYPE_EVDO_B:
        case TelephonyManager.NETWORK_TYPE_EHRPD:
            mDataIconList = sDataNetType_3g[mInetCondition];
            break;
        case TelephonyManager.NETWORK_TYPE_LTE:
            mDataIconList = sDataNetType_lte[mInetCondition];
            break;
        default:
            mDataIconList = sDataNetType_g[mInetCondition];
        break;
        }
    }

    private final void updateDataIcon(int subscription) {
        Slog.d(TAG,"updateDataIcon subscription =" + subscription);
        int iconId;
        boolean visible = true;
        int dataSub = Settings.System.getInt(mContext.getContentResolver(),Settings.System.MULTI_SIM_DATA_CALL, 0);
        // Update icon only if DDS in properly set and "subscription" matches DDS.
        if (subscription != dataSub) {
            return;
        }

        if (mSignalStrength != null && mSignalStrength.length > subscription &&
               mSignalStrength[subscription] != null && mSignalStrength[subscription].isGsm() &&
               mSimState[subscription] != IccCard.State.READY && mSimState[subscription] != IccCard.State.UNKNOWN) {
               iconId = R.drawable.stat_sys_no_sim;
               mService.setIcon("data_connection", iconId, 0);
        } else if ((dataRadio(subscription) == GSM) ||
                    (dataRadio(subscription) == LTE)) {
            // GSM data, we have to check also the sim state
            if (mSimState[subscription] == IccCard.State.READY || mSimState[subscription] == IccCard.State.UNKNOWN) {
                if (hasService(subscription) && mDataState == TelephonyManager.DATA_CONNECTED) {
                    switch (mDataActivity) {
                        case TelephonyManager.DATA_ACTIVITY_IN:
                            iconId = mDataIconList[1];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_OUT:
                            iconId = mDataIconList[2];
                            break;
                        case TelephonyManager.DATA_ACTIVITY_INOUT:
                            iconId = mDataIconList[3];
                            break;
                        default:
                            iconId = mDataIconList[0];
                            break;
                    }
                    mService.setIcon("data_connection", iconId, 0);
                } else {
                    visible = false;
                }
            } else {
                iconId = R.drawable.stat_sys_no_sim;
                mService.setIcon("data_connection", iconId, 0);
            }
        } else if ((dataRadio(subscription) == CDMA) || (dataRadio(subscription) == EVDO)) {
            // CDMA case, mDataActivity can be also DATA_ACTIVITY_DORMANT
            if (hasService(subscription) && mDataState == TelephonyManager.DATA_CONNECTED) {
                switch (mDataActivity) {
                    case TelephonyManager.DATA_ACTIVITY_IN:
                        iconId = mDataIconList[1];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_OUT:
                        iconId = mDataIconList[2];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_INOUT:
                        iconId = mDataIconList[3];
                        break;
                    case TelephonyManager.DATA_ACTIVITY_DORMANT:
                    default:
                        iconId = mDataIconList[0];
                        break;
                }
                mService.setIcon("data_connection", iconId, 0);
            } else {
                visible = false;
            }
        } else {
            visible = false;
        }

        long ident = Binder.clearCallingIdentity();
        try {
            mBatteryStats.notePhoneDataConnectionState(mPhone.getNetworkType(), visible);
        } catch (RemoteException e) {
        } finally {
            Binder.restoreCallingIdentity(ident);
        }

        if (mDataIconVisible != visible) {
            mService.setIconVisibility("data_connection", visible);
            mDataIconVisible = visible;
        }
    }

    private final void updateVolume() {
        AudioManager audioManager = (AudioManager) mContext.getSystemService(Context.AUDIO_SERVICE);
        final int ringerMode = audioManager.getRingerMode();
        final boolean visible = ringerMode == AudioManager.RINGER_MODE_SILENT ||
                ringerMode == AudioManager.RINGER_MODE_VIBRATE;
        final int iconId = audioManager.shouldVibrate(AudioManager.VIBRATE_TYPE_RINGER)
                ? R.drawable.stat_sys_ringer_vibrate
                : R.drawable.stat_sys_ringer_silent;

        if (visible) {
            mService.setIcon("volume", iconId, 0);
        }
        if (visible != mVolumeVisible) {
            mService.setIconVisibility("volume", visible);
            mVolumeVisible = visible;
        }
    }

    private final void updateBluetooth(Intent intent) {
        int iconId = R.drawable.stat_sys_data_bluetooth;
        String action = intent.getAction();
        if (action.equals(BluetoothAdapter.ACTION_STATE_CHANGED)) {
            int state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR);
            mBluetoothEnabled = state == BluetoothAdapter.STATE_ON;
        } else if (action.equals(BluetoothHeadset.ACTION_STATE_CHANGED)) {
            mBluetoothHeadsetState = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE,
                    BluetoothHeadset.STATE_ERROR);
        } else if (action.equals(BluetoothA2dp.ACTION_SINK_STATE_CHANGED)) {
            BluetoothA2dp a2dp = new BluetoothA2dp(mContext);
            if (a2dp.getConnectedSinks().size() != 0) {
                mBluetoothA2dpConnected = true;
            } else {
                mBluetoothA2dpConnected = false;
            }
        } else if (action.equals(BluetoothPbap.PBAP_STATE_CHANGED_ACTION)) {
            mBluetoothPbapState = intent.getIntExtra(BluetoothPbap.PBAP_STATE,
                    BluetoothPbap.STATE_DISCONNECTED);
        } else {
            return;
        }

        if (mBluetoothHeadsetState == BluetoothHeadset.STATE_CONNECTED || mBluetoothA2dpConnected ||
                mBluetoothPbapState == BluetoothPbap.STATE_CONNECTED) {
            iconId = R.drawable.stat_sys_data_bluetooth_connected;
        }

        mService.setIcon("bluetooth", iconId, 0);
        mService.setIconVisibility("bluetooth", mBluetoothEnabled);
    }

    private final void updateWifi(Intent intent) {
        final String action = intent.getAction();
        if (action.equals(WifiManager.WIFI_STATE_CHANGED_ACTION)) {

            final boolean enabled = intent.getIntExtra(WifiManager.EXTRA_WIFI_STATE,
                    WifiManager.WIFI_STATE_UNKNOWN) == WifiManager.WIFI_STATE_ENABLED;

            if (!enabled) {
                // If disabled, hide the icon. (We show icon when connected.)
                mService.setIconVisibility("wifi", false);
            }

        } else if (action.equals(WifiManager.SUPPLICANT_CONNECTION_CHANGE_ACTION)) {
            final boolean enabled = intent.getBooleanExtra(WifiManager.EXTRA_SUPPLICANT_CONNECTED,
                                                           false);
            if (!enabled) {
                mService.setIconVisibility("wifi", false);
            }
        } else if (action.equals(WifiManager.RSSI_CHANGED_ACTION)) {
            int iconId;
            final int newRssi = intent.getIntExtra(WifiManager.EXTRA_NEW_RSSI, -200);
            int newSignalLevel = WifiManager.calculateSignalLevel(newRssi,
                                                                  sWifiSignalImages[0].length);
            if (newSignalLevel != mLastWifiSignalLevel) {
                mLastWifiSignalLevel = newSignalLevel;
                if (mIsWifiConnected) {
                    iconId = sWifiSignalImages[mInetCondition][newSignalLevel];
                } else {
                    iconId = sWifiTemporarilyNotConnectedImage;
                }
                mService.setIcon("wifi", iconId, 0);
            }
        }
    }

    private final void updateGps(Intent intent) {
        final String action = intent.getAction();
        final boolean enabled = intent.getBooleanExtra(LocationManager.EXTRA_GPS_ENABLED, false);

        if (action.equals(LocationManager.GPS_FIX_CHANGE_ACTION) && enabled) {
            // GPS is getting fixes
            mService.setIcon("gps", com.android.internal.R.drawable.stat_sys_gps_on, 0);
            mService.setIconVisibility("gps", true);
        } else if (action.equals(LocationManager.GPS_ENABLED_CHANGE_ACTION) && !enabled) {
            // GPS is off
            mService.setIconVisibility("gps", false);
        } else {
            // GPS is on, but not receiving fixes
            mService.setIcon("gps", R.drawable.stat_sys_gps_acquiring_anim, 0);
            mService.setIconVisibility("gps", true);
        }
    }

    private final void updateTTY(Intent intent) {
        final String action = intent.getAction();
        final boolean enabled = intent.getBooleanExtra(TtyIntent.TTY_ENABLED, false);

        if (false) Slog.v(TAG, "updateTTY: enabled: " + enabled);

        if (enabled) {
            // TTY is on
            if (false) Slog.v(TAG, "updateTTY: set TTY on");
            mService.setIcon("tty", R.drawable.stat_sys_tty_mode, 0);
            mService.setIconVisibility("tty", true);
        } else {
            // TTY is off
            if (false) Slog.v(TAG, "updateTTY: set TTY off");
            mService.setIconVisibility("tty", false);
        }
    }

    private final void updateCdmaRoamingIcon(int subscription) {
        if ((!hasService(subscription))
                || (mSignalStrength[subscription] == null)
                || (!mServiceState[subscription].getRoaming())
                || (mSignalStrength[subscription].isGsm())){
            mService.setIconVisibility("cdma_eri", false);
            return;
        }

        /* CDMA is voice tech and
         * 1. roaming on voice (cdma)
         * 2. not roaming on voice (cdma) and roaming on data (cdma /gsm )
         */

        int[] iconList = sRoamingIndicatorImages_cdma;
        int iconIndex = mServiceState[subscription].getCdmaEriIconIndex();
        int iconMode = mServiceState[subscription].getCdmaEriIconMode();

        if ((iconIndex != -1) && (iconMode != -1)) {
            // voice is roaming (CDMA)
            if (iconIndex == EriInfo.ROAMING_INDICATOR_OFF) {
                if (false) Slog.v(TAG, "Cdma ROAMING_INDICATOR_OFF, removing ERI icon");
                mService.setIconVisibility("cdma_eri", false);
                return;
            }
            switch (iconMode) {
                case EriInfo.ROAMING_ICON_MODE_NORMAL:
                    mService.setIcon("cdma_eri", iconList[iconIndex], 0);
                    break;
                case EriInfo.ROAMING_ICON_MODE_FLASH:
                    mService.setIcon("cdma_eri", R.drawable.stat_sys_roaming_cdma_flash, 0);
                    break;
            }
        } else {
            /* data is roaming
             * we decide to display cdma roaming icon as long as voice is cdma
             */
            mService.setIcon("cdma_eri", R.drawable.stat_sys_roaming_cdma_0, 0);
        }

        mService.setIconVisibility("cdma_eri", true);
        mService.setIcon(mSignalIcon[subscription], mPhoneSignalIconId[subscription], 0);
        return;
    }

    private class StatusBarHandler extends Handler {
        @Override
        public void handleMessage(Message msg) {
            switch (msg.what) {
            case EVENT_BATTERY_CLOSE:
                if (msg.arg1 == mBatteryViewSequence) {
                    closeLastBatteryView();
                }
                break;
            }
        }
    }
}
