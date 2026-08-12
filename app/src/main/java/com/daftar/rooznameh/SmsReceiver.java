package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.NetworkInfo;

public class NetworkReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null) return;

        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return;

            NetworkInfo info = cm.getActiveNetworkInfo();

            if (info != null && info.isConnected()) {
                SmsReceiver.retryPendingSms(context.getApplicationContext());
            }

        } catch (Exception ignored) {}
    }
}
