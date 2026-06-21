package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.net.ConnectivityManager;
import android.net.Network;

public class NetworkReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null) return;

        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            if (cm == null) return;

            boolean isOnline = false;

            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                Network network = cm.getActiveNetwork();
                isOnline = network != null;
            } else {
                android.net.NetworkInfo info = cm.getActiveNetworkInfo();
                isOnline = info != null && info.isConnected();
            }

            if (isOnline) {
                Context app = context.getApplicationContext();

                SmsReceiver.scanInboxBankSmsToday(app, 300);
                SmsReceiver.retryPendingSms(app);
            }

        } catch (Exception ignored) {
        }
    }
}
