package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.widget.Toast;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.net.URLEncoder;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class SmsReceiver extends BroadcastReceiver {

    private static final String API_BASE =
            "https://script.google.com/macros/s/AKfycbyLjGFEBZuoF2HxMYHvbJaTEjM8NXf4_6mEUGd4iKE0Fp1xZwIwl3XfY5EhepGlKj72/exec?action=sms&msg=";

    private static final String PREF_NAME = "offline_sms_queue";
    private static final String KEY_QUEUE = "pending_messages_v7";
    private static final String KEY_SCANNED = "scanned_inbox_ids_v2";

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

        retryPendingSms(appContext);
        scanInboxBankSms(appContext, 80);

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        Bundle bundle = intent.getExtras();
        if (bundle == null) return;

        Object[] pdus = (Object[]) bundle.get("pdus");
        String format = bundle.getString("format");
        if (pdus == null) return;

        StringBuilder fullMessage = new StringBuilder();
        long smsTime = System.currentTimeMillis();

        for (Object pdu : pdus) {
            SmsMessage sms = SmsMessage.createFromPdu((byte[]) pdu, format);
            if (sms != null) {
                fullMessage.append(sms.getMessageBody());
                if (sms.getTimestampMillis() > 0) {
                    smsTime = sms.getTimestampMillis();
                }
            }
        }

        String msg = fullMessage.toString();

        if (!isBankSms(msg)) return;

        Toast.makeText(context, "پیامک بانکی ذخیره شد", Toast.LENGTH_LONG).show();

        final PendingResult pendingResult = goAsync();
        final long finalSmsTime = smsTime;

        new Thread(() -> {
            try {
                savePendingSms(appContext, msg, finalSmsTime);
                retryPendingSms(appContext);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    public static void scanInboxBankSms(Context context, int limit) {
        if (context == null) return;
        if (scanRunning) return;

        scanRunning = true;

        new Thread(() -> {
            Cursor cursor = null;

            try {
                Context appContext = context.getApplicationContext();
                SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);

                JSONArray scannedArr = new JSONArray(prefs.getString(KEY_SCANNED, "[]"));
                List<String> scannedIds = new ArrayList<>();

                for (int i = 0; i < scannedArr.length(); i++) {
                    scannedIds.add(scannedArr.optString(i, ""));
                }

                Uri uri = Uri.parse("content://sms/inbox");

                cursor = appContext.getContentResolver().query(
                        uri,
                        new String[]{"_id", "body", "date"},
                        null,
                        null,
                        "date DESC"
                );

                if (cursor == null) return;

                int count = 0;

                while (cursor.moveToNext() && count < limit) {
                    count++;

                    String id = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                    if (id == null || body == null) continue;

                    if (scannedIds.contains(id)) continue;

                    if (isBankSms(body)) {
                        savePendingSms(appContext, body, date);
                    }

                    scannedIds.add(id);
                }

                while (scannedIds.size() > 300) {
                    scannedIds.remove(0);
                }

                JSONArray newArr = new JSONArray();
                for (String id : scannedIds) {
                    if (id != null && id.length() > 0) {
                        newArr.put(id);
                    }
                }

                prefs.edit().putString(KEY_SCANNED, newArr.toString()).apply();

                retryPendingSms(appContext);

            } catch (Exception ignored) {

            } finally {
                try {
                    if (cursor != null) cursor.close();
                } catch (Exception ignored) {}

                scanRunning = false;
            }
        }).start();
    }

    public static boolean isBankSms(String msg) {
        if (msg == null) return false;

        String text = normalize(msg);

        boolean hasBalance = text.contains("مانده");

        boolean hasAccountOrCard =
                text.contains("حساب") ||
                text.contains("کارت");

        return hasBalance && hasAccountOrCard;
    }

    private static String normalize(String msg) {
        return String.valueOf(msg)
                .replace("ي", "ی")
                .replace("ك", "ک")
                .replace("،", ",")
                .replace("۰", "0")
                .replace("۱", "1")
                .replace("۲", "2")
                .replace("۳", "3")
                .replace("۴", "4")
                .replace("۵", "5")
                .replace("۶", "6")
                .replace("۷", "7")
                .replace("۸", "8")
                .replace("۹", "9")
                .replace("٠", "0")
                .replace("١", "1")
                .replace("٢", "2")
                .replace("٣", "3")
                .replace("٤", "4")
                .replace("٥", "5")
                .replace("٦", "6")
                .replace("٧", "7")
                .replace("٨", "8")
                .replace("٩", "9")
                .replaceAll("\\s+", " ")
                .trim();
    }

    private static boolean sendNow(String msg) {
        HttpURLConnection conn = null;
        BufferedReader br = null;

        try {
            String encoded = URLEncoder.encode(msg, "UTF-8");
            URL url = new URL(API_BASE + encoded);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(25000);
            conn.setReadTimeout(25000);
            conn.setUseCaches(false);

            int code = conn.getResponseCode();
            if (code < 200 || code >= 400) return false;

            br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));

            StringBuilder response = new StringBuilder();
            String line;

            while ((line = br.readLine()) != null) {
                response.append(line);
            }

            return response.toString().contains("\"ok\":true");

        } catch (Exception e) {
            return false;

        } finally {
            try {
                if (br != null) br.close();
            } catch (Exception ignored) {}

            if (conn != null) conn.disconnect();
        }
    }

    private static synchronized void savePendingSms(Context context, String msg, long time) {
        if (msg == null || msg.trim().length() == 0) return;

        List<SmsItem> queue = getQueue(context);
        queue.add(new SmsItem(time, msg));
        saveQueue(context, queue);
    }

    public static void retryPendingSms(Context context) {
        if (context == null) return;
        if (syncRunning) return;

        syncRunning = true;

        new Thread(() -> {
            try {
                Context appContext = context.getApplicationContext();

                List<SmsItem> queue = getQueue(appContext);
                if (queue.isEmpty()) return;

                Collections.sort(queue, new Comparator<SmsItem>() {
                    @Override
                    public int compare(SmsItem a, SmsItem b) {
                        return Long.compare(a.time, b.time);
                    }
                });

                List<SmsItem> remaining = new ArrayList<>();

                for (int i = 0; i < queue.size(); i++) {
                    SmsItem item = queue.get(i);

                    if (item == null || item.msg == null || item.msg.trim().length() == 0) {
                        continue;
                    }

                    boolean sent = sendNow(item.msg);

                    if (!sent) {
                        for (int j = i; j < queue.size(); j++) {
                            remaining.add(queue.get(j));
                        }
                        break;
                    }
                }

                saveQueue(appContext, remaining);

            } finally {
                syncRunning = false;
            }
        }).start();
    }

    private static List<SmsItem> getQueue(Context context) {
        List<SmsItem> list = new ArrayList<>();

        try {
            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            String json = prefs.getString(KEY_QUEUE, "[]");

            JSONArray arr = new JSONArray(json);

            for (int i = 0; i < arr.length(); i++) {
                JSONObject obj = arr.optJSONObject(i);
                if (obj == null) continue;

                String msg = obj.optString("msg", "");
                long time = obj.optLong("time", System.currentTimeMillis());

                if (msg != null && msg.trim().length() > 0) {
                    list.add(new SmsItem(time, msg));
                }
            }

        } catch (Exception ignored) {}

        return list;
    }

    private static void saveQueue(Context context, List<SmsItem> queue) {
        try {
            JSONArray arr = new JSONArray();

            for (SmsItem item : queue) {
                if (item != null && item.msg != null && item.msg.trim().length() > 0) {
                    JSONObject obj = new JSONObject();
                    obj.put("time", item.time);
                    obj.put("msg", item.msg);
                    arr.put(obj);
                }
            }

            SharedPreferences prefs = context.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
            prefs.edit().putString(KEY_QUEUE, arr.toString()).apply();

        } catch (Exception ignored) {}
    }

    public static int getPendingSmsCount(Context context) {
        if (context == null) return 0;
        return getQueue(context.getApplicationContext()).size();
    }
}
