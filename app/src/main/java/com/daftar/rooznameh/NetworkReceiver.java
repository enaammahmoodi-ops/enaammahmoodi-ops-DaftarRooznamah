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
        if (isOnline(context)) {
            new Handler(Looper.getMainLooper()).postDelayed(() -> {
                SmsReceiver.retryPendingSms(context);
            }, 3000);
        }
    }

    private boolean isOnline(Context context) {
        try {
            ConnectivityManager cm =
                    (ConnectivityManager) context.getSystemService(Context.CONNECTIVITY_SERVICE);

            NetworkInfo info = cm.getActiveNetworkInfo();
            return info != null && info.isConnected();

        } catch (Exception e) {
            return false;
        }
    }
}
