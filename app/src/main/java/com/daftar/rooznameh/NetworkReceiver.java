package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

/** پس از وصل شدن اینترنت، اسکن پیامک‌های امروز دوباره اجرا می‌شود. */
public class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null) return;
        if (SmsReceiver.isOnline(context)) {
            SmsReceiver.retryPendingSms(context.getApplicationContext());
        }
    }
}
