package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

public class NetworkReceiver extends BroadcastReceiver {
    @Override
    public void onReceive(Context context, Intent intent) {
        Intent smsIntent = new Intent(context, SmsReceiver.class);
        context.sendBroadcast(smsIntent);
    }
}
