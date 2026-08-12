package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/** دریافت پیامک و اسکن تمام پیامک‌های امروزِ دارای «مانده». */
public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    static final String API_URL =
            "https://script.google.com/macros/s/AKfycbxsXvURwus4QlQlVzhRed-FRONKB7tDojZCSnj5LJjm9YlGJUt5bLa17KjxjuNkEvme/exec";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null) return;
        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        new Thread(new Runnable() {
            @Override public void run() {
                try {
                    scanInboxBankSmsToday(appContext);
                } finally {
                    pendingResult.finish();
                }
            }
        }).start();
    }

    /** توسط MainActivity و NetworkReceiver فراخوانی می‌شود. */
    public static void retryPendingSms(final Context context) {
        if (context == null || !isOnline(context)) return;
        new Thread(new Runnable() {
            @Override public void run() {
                scanInboxBankSmsToday(context.getApplicationContext());
            }
        }).start();
    }

    /** بدون محدودیت تعداد، فقط پیامک‌های صندوق ورودیِ امروز را می‌خواند. */
    public static void scanInboxBankSmsToday(Context context) {
        if (context == null || !isOnline(context)) return;

        Cursor cursor = null;
        try {
            Calendar calendar = Calendar.getInstance();
            calendar.set(Calendar.HOUR_OF_DAY, 0);
            calendar.set(Calendar.MINUTE, 0);
            calendar.set(Calendar.SECOND, 0);
            calendar.set(Calendar.MILLISECOND, 0);
            long todayStart = calendar.getTimeInMillis();

            calendar.add(Calendar.DAY_OF_YEAR, 1);
            long tomorrowStart = calendar.getTimeInMillis();

            cursor = context.getContentResolver().query(
                    Uri.parse("content://sms/inbox"),
                    new String[]{"body", "address", "date"},
                    "date >= ? AND date < ?",
                    new String[]{String.valueOf(todayStart), String.valueOf(tomorrowStart)},
                    "date ASC"
            );
            if (cursor == null) return;

            int bodyIndex = cursor.getColumnIndexOrThrow("body");
            int senderIndex = cursor.getColumnIndexOrThrow("address");
            int dateIndex = cursor.getColumnIndexOrThrow("date");

            while (cursor.moveToNext()) {
                String body = cursor.getString(bodyIndex);
                if (body == null || !body.contains("مانده")) continue;
                sendToServer(body.trim(), cursor.getString(senderIndex), cursor.getLong(dateIndex));
            }
        } catch (SecurityException error) {
            Log.e(TAG, "READ_SMS permission was not granted.", error);
        } catch (Exception error) {
            Log.e(TAG, "Today's SMS scan failed.", error);
        } finally {
            if (cursor != null) cursor.close();
        }
    }

    /** پیامک یا اعلان را به صورت POST/JSON به Google Apps Script می‌فرستد. */
    static void sendToServer(String message, String sender, long receivedAt) {
        if (message == null || message.trim().length() == 0 || !isOnlineStatic()) return;

        HttpURLConnection connection = null;
        try {
            JSONObject payload = new JSONObject();
            payload.put("msg", message.trim());
            payload.put("sender", sender == null ? "" : sender);
            payload.put("receivedAt", receivedAt);

            connection = (HttpURLConnection) new URL(API_URL).openConnection();
            connection.setRequestMethod("POST");
            connection.setConnectTimeout(20000);
            connection.setReadTimeout(20000);
            connection.setDoOutput(true);
            connection.setRequestProperty("Content-Type", "application/json; charset=UTF-8");
            connection.setRequestProperty("Accept", "text/plain");

            OutputStream output = connection.getOutputStream();
            output.write(payload.toString().getBytes("UTF-8"));
            output.flush();
            output.close();

            Log.i(TAG, "Sent to script. HTTP " + connection.getResponseCode());
        } catch (Exception error) {
            Log.e(TAG, "Could not send to script.", error);
        } finally {
            if (connection != null) connection.disconnect();
        }
    }

    private static boolean isOnlineStatic() {
        // اتصال قبل از ارسال توسط scanInboxBankSmsToday یا listener کنترل می‌شود.
        return true;
    }

    static boolean isOnline(Context context) {
        ConnectivityManager manager = (ConnectivityManager)
                context.getSystemService(Context.CONNECTIVITY_SERVICE);
        if (manager == null) return false;
        android.net.NetworkInfo info = manager.getActiveNetworkInfo();
        return info != null && info.isConnected();
    }
}
