package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.database.Cursor;
import android.content.pm.PackageManager;
import android.net.ConnectivityManager;
import android.net.Uri;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.Calendar;

/** دریافت سریع پیامک تازه و اسکن پیامک‌های امروز هنگام بازشدن برنامه/وصل‌شدن اینترنت. */
public class SmsReceiver extends BroadcastReceiver {

    private static final String TAG = "SmsReceiver";

    static final String API_URL =
            "https://script.google.com/macros/s/AKfycbxsXvURwus4QlQlVzhRed-FRONKB7tDojZCSnj5LJjm9YlGJUt5bLa17KjxjuNkEvme/exec";

    @Override
    public void onReceive(Context context, Intent intent) {

        if (context == null || intent == null) return;

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            return;
        }

        final String message = getMessage(intent);

        // فقط پیامک‌هایی که کلمه «مانده» دارند
        if (message.length() == 0 || !isBankSms(message)) {
            return;
        }

        final PendingResult pendingResult = goAsync();
        final Context appContext = context.getApplicationContext();
        final long receivedAt = System.currentTimeMillis();

        new Thread(new Runnable() {

            @Override
            public void run() {

                try {

                    // =====================================================
                    // 1. اول خود پیامک جدید فوری ارسال می‌شود
                    // =====================================================
                    sendToServer(
                            message,
                            "SMS",
                            receivedAt
                    );

                    // =====================================================
                    // 2. بعد از پایان ارسال پیامک جدید، اسکن امروز شروع می‌شود
                    // =====================================================
                    scanInboxBankSmsToday(appContext);

                } finally {

                    // بعد از پایان ارسال + اسکن
                    pendingResult.finish();
                }
            }

        }).start();
    }

    /**
     * توسط MainActivity و NetworkReceiver فراخوانی می‌شود.
     */
    public static void retryPendingSms(final Context context) {

        if (context == null || !isOnline(context)) {
            return;
        }

        new Thread(new Runnable() {

            @Override
            public void run() {

                scanInboxBankSmsToday(
                        context.getApplicationContext()
                );
            }

        }).start();
    }

    /**
     * بدون محدودیت تعداد،
     * فقط پیامک‌های صندوق ورودی امروز را می‌خواند.
     *
     * فقط پیامک‌هایی ارسال می‌شوند که کلمه «مانده» داشته باشند.
     */
    public static void scanInboxBankSmsToday(Context context) {

        if (context == null || !isOnline(context)) {
            return;
        }

        if (
                context.checkCallingOrSelfPermission(
                        "android.permission.READ_SMS"
                ) != PackageManager.PERMISSION_GRANTED
        ) {

            Log.e(
                    TAG,
                    "READ_SMS permission was not granted."
            );

            return;
        }

        Cursor cursor = null;

        try {

            // =============================================================
            // شروع امروز ساعت 00:00:00
            // =============================================================
            Calendar calendar = Calendar.getInstance();

            calendar.set(
                    Calendar.HOUR_OF_DAY,
                    0
            );

            calendar.set(
                    Calendar.MINUTE,
                    0
            );

            calendar.set(
                    Calendar.SECOND,
                    0
            );

            calendar.set(
                    Calendar.MILLISECOND,
                    0
            );

            long todayStart =
                    calendar.getTimeInMillis();

            // =============================================================
            // شروع فردا
            // =============================================================
            calendar.add(
                    Calendar.DAY_OF_YEAR,
                    1
            );

            long tomorrowStart =
                    calendar.getTimeInMillis();

            // =============================================================
            // خواندن پیامک‌های امروز از Inbox
            // =============================================================
            cursor =
                    context
                            .getContentResolver()
                            .query(
                                    Uri.parse(
                                            "content://sms/inbox"
                                    ),

                                    new String[]{
                                            "body",
                                            "address",
                                            "date"
                                    },

                                    "date >= ? AND date < ?",

                                    new String[]{
                                            String.valueOf(todayStart),
                                            String.valueOf(tomorrowStart)
                                    },

                                    "date ASC"
                            );

            if (cursor == null) {
                return;
            }

            int bodyIndex =
                    cursor.getColumnIndexOrThrow(
                            "body"
                    );

            int senderIndex =
                    cursor.getColumnIndexOrThrow(
                            "address"
                    );

            int dateIndex =
                    cursor.getColumnIndexOrThrow(
                            "date"
                    );

            // =============================================================
            // بررسی همه پیامک‌های امروز
            // =============================================================
            while (cursor.moveToNext()) {

                String body =
                        cursor.getString(
                                bodyIndex
                        );

                if (body == null) {
                    continue;
                }

                // =========================================================
                // فقط پیامک دارای «مانده»
                // بدون حساسیت به فاصله و نیم‌فاصله
                // =========================================================
                if (!isBankSms(body)) {
                    continue;
                }

                sendToServer(
                        body.trim(),
                        cursor.getString(senderIndex),
                        cursor.getLong(dateIndex)
                );
            }

        } catch (SecurityException error) {

            Log.e(
                    TAG,
                    "READ_SMS permission was not granted.",
                    error
            );

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "Today's SMS scan failed.",
                    error
            );

        } finally {

            if (cursor != null) {
                cursor.close();
            }
        }
    }

    /**
     * یکسان‌سازی متن برای تشخیص پیامک بانکی.
     *
     * موارد زیر نادیده گرفته می‌شوند:
     *
     * فاصله
     * نیم‌فاصله
     * فاصله‌های Unicode
     * کاراکترهای نامرئی
     * ي / ی
     * ى / ی
     * ك / ک
     * کشیده ـ
     */
    private static String normalizeForMatch(String value) {

        if (value == null) {
            return "";
        }

        return value

                // نیم‌فاصله
                .replace('‌', ' ')

                // Zero Width Joiner
                .replace('‍', ' ')

                // NBSP
                .replace(' ', ' ')

                // حروف عربی به فارسی
                .replace('ي', 'ی')
                .replace('ى', 'ی')
                .replace('ك', 'ک')

                // حذف کشیده
                .replace("ـ", "")

                // حذف تمام فاصله‌ها و کاراکترهای نامرئی
                .replaceAll(
                        "[\\s\\p{Z}\\p{Cf}]+",
                        ""
                )

                .trim();
    }

    /**
     * فقط پیامک دارای کلمه «مانده» معتبر است.
     */
    private static boolean isBankSms(String value) {

        return normalizeForMatch(value)
                .contains("مانده");
    }

    /**
     * پیامک‌های چندبخشی را به یک متن کامل تبدیل می‌کند.
     */
    private static String getMessage(Intent intent) {

        try {

            Bundle bundle =
                    intent.getExtras();

            if (bundle == null) {
                return "";
            }

            Object[] pdus =
                    (Object[]) bundle.get(
                            "pdus"
                    );

            if (pdus == null) {
                return "";
            }

            StringBuilder text =
                    new StringBuilder();

            String format =
                    bundle.getString(
                            "format"
                    );

            for (Object pdu : pdus) {

                SmsMessage sms;

                if (
                        android.os.Build.VERSION.SDK_INT
                                >= android.os.Build.VERSION_CODES.M
                ) {

                    sms =
                            SmsMessage.createFromPdu(
                                    (byte[]) pdu,
                                    format
                            );

                } else {

                    sms =
                            SmsMessage.createFromPdu(
                                    (byte[]) pdu
                            );
                }

                if (
                        sms != null &&
                        sms.getMessageBody() != null
                ) {

                    text.append(
                            sms.getMessageBody()
                    );
                }
            }

            return text
                    .toString()
                    .trim();

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "Could not read incoming SMS.",
                    error
            );

            return "";
        }
    }

    /**
     * پیامک یا اعلان را به صورت POST/JSON
     * به Google Apps Script می‌فرستد.
     */
    static void sendToServer(
            String message,
            String sender,
            long receivedAt
    ) {

        if (
                message == null ||
                message.trim().length() == 0 ||
                !isOnlineStatic()
        ) {
            return;
        }

        HttpURLConnection connection = null;

        try {

            JSONObject payload =
                    new JSONObject();

            payload.put(
                    "msg",
                    message.trim()
            );

            payload.put(
                    "sender",
                    sender == null
                            ? ""
                            : sender
            );

            payload.put(
                    "receivedAt",
                    receivedAt
            );

            connection =
                    (HttpURLConnection)
                            new URL(
                                    API_URL
                            ).openConnection();

            connection.setRequestMethod(
                    "POST"
            );

            connection.setConnectTimeout(
                    20000
            );

            connection.setReadTimeout(
                    20000
            );

            connection.setDoOutput(
                    true
            );

            connection.setRequestProperty(
                    "Content-Type",
                    "application/json; charset=UTF-8"
            );

            connection.setRequestProperty(
                    "Accept",
                    "text/plain"
            );

            OutputStream output =
                    connection.getOutputStream();

            output.write(
                    payload
                            .toString()
                            .getBytes(
                                    "UTF-8"
                            )
            );

            output.flush();
            output.close();

            Log.i(
                    TAG,
                    "Sent to script. HTTP "
                            + connection.getResponseCode()
            );

        } catch (Exception error) {

            Log.e(
                    TAG,
                    "Could not send to script.",
                    error
            );

        } finally {

            if (connection != null) {
                connection.disconnect();
            }
        }
    }

    private static boolean isOnlineStatic() {

        // اتصال قبل از ارسال توسط
        // scanInboxBankSmsToday
        // یا listener کنترل می‌شود.
        return true;
    }

    static boolean isOnline(Context context) {

        ConnectivityManager manager =
                (ConnectivityManager)
                        context.getSystemService(
                                Context.CONNECTIVITY_SERVICE
                        );

        if (manager == null) {
            return false;
        }

        android.net.NetworkInfo info =
                manager.getActiveNetworkInfo();

        return info != null &&
                info.isConnected();
    }
}
