package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import org.json.JSONArray;

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
    private static final String KEY_QUEUE = "pending_messages";
    private static boolean syncRunning = false;

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            retryPendingSms(context);
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) return;

        StringBuilder fullMessage = new StringBuilder();

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) fullMessage.append(sms.getMessageBody());
        }

        String msg = fullMessage.toString();
        if (!isBankSms(msg)) return;

        Toast.makeText(context, "پیامک بانکی دریافت شد", Toast.LENGTH_LONG).show();

        final PendingResult pendingResult = goAsync();

        new Thread(() -> {
            try {
                // همه پیامک‌ها اول داخل صف ذخیره می‌شوند
                // بعد صف به ترتیب ارسال می‌شود
                savePendingSms(context, msg);
                retryPendingSms(context);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    private boolean isBankSms(String msg) {
        if (msg == null) return false;

        return msg.contains("حساب") &&
                (msg.contains("29002") ||
                 msg.contains("33002") ||
                 msg.contains("41002") ||
                 msg.contains("84009") ||
                 msg.contains("73004") ||
                 msg.contains("38006") ||
                 msg.contains("04004"));
    }

    private static boolean sendNow(String msg) {
        HttpURLConnection conn = null;
        BufferedReader br = null;

        try {
            String encoded = URLEncoder.encode(msg, "UTF-8");
            URL url = new URL(API_BASE + encoded);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(15000);
            conn.setReadTimeout(15000);

            int code = conn.getResponseCode();

            if (code < 200 || code >= 400) {
                return false;
            }

            br = new BufferedReader(new InputStreamReader(conn.getInputStream()));
            while (br.readLine() != null) {}

            return true;

        } catch (Exception e) {
            return false;

        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {}

            if (conn != null) conn.disconnect();
        }
    }

    private static synchronized void savePendingSms(Context context, String msg) {
        List<String> queue = getQueue(context);

        // پیام‌های مشابه هم باید ثبت شوند، چون ممکن است مبلغ‌ها یا زمان‌ها نزدیک باشند
        if (msg != null && msg.trim().length() > 0) {
            queue.add(msg);
            saveQueue(context, queue);
        }
    }

    public static void retryPendingSms(Context context) {
        if (syncRunning) return;

        syncRunning = true;

        new Thread(() -> {
            try {
                List<String> queue = getQueue(context);

                if (queue.isEmpty()) return;

                List<String> failed = new ArrayList<>();

                // ارسال به ترتیب دریافت پیامک‌ها
                for (String msg : queue) {
                    boolean sent = sendNow(msg);

                    if (!sent) {
                        failed.add(msg);
                    }
                }

                saveQueue(context, failed);

            } finally {
                syncRunning = false;
            }
        }).start();
    }

    private static List<String> getQueue(Context context) {
        List<String> list = new ArrayList<>();

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_QUEUE, "[]");

            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                String item = arr.optString(i, "");
                if (item != null && item.trim().length() > 0) {
                    list.add(item);
                }
            }

        } catch (Exception ignored) {}

        return list;
    }

    private static void saveQueue(Context context, List<String> queue) {
        try {
            JSONArray arr = new JSONArray();

            for (String msg : queue) {
                if (msg != null && msg.trim().length() > 0) {
                    arr.put(msg);
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_QUEUE, arr.toString()).apply();

        } catch (Exception ignored) {}
    }
}
