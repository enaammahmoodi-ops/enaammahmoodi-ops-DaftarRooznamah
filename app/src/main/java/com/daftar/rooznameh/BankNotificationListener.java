package com.daftar.rooznameh;

import android.os.Bundle;
import android.service.notification.NotificationListenerService;
import android.service.notification.StatusBarNotification;
import android.util.Log;

/** اعلان‌های دارای واژه «مانده» را نیز به Apps Script ارسال می‌کند. */
public class BankNotificationListener extends NotificationListenerService {
    private static final String TAG = "BankNotification";

    @Override
    public void onNotificationPosted(final StatusBarNotification notification) {
        if (notification == null || notification.getNotification() == null) return;

        try {
            Bundle extras = notification.getNotification().extras;
            if (extras == null) return;

            String title = asText(extras.getCharSequence("android.title"));
            String text = asText(extras.getCharSequence("android.text"));
            String bigText = asText(extras.getCharSequence("android.bigText"));
            final String message = (title + "\n" + text + "\n" + bigText).trim();

            if (!message.contains("مانده") || !SmsReceiver.isOnline(getApplicationContext())) return;

            new Thread(new Runnable() {
                @Override public void run() {
                    SmsReceiver.sendToServer(
                            message,
                            notification.getPackageName(),
                            notification.getPostTime()
                    );
                }
            }).start();
        } catch (Exception error) {
            Log.e(TAG, "Could not read notification.", error);
        }
    }

    private String asText(CharSequence value) {
        return value == null ? "" : value.toString();
    }
}
