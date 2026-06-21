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
    private static final String KEY_QUEUE = "pending_messages_same_day_v13";
    private static final String KEY_SCANNED = "scanned_same_day_sms_v13";

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

        if (!"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) {
            retryPendingSms(appContext);
            scanInboxBankSmsToday(appContext, 300);
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
        final String dateTagFromMsg = extractDateTag(msg);

        new Thread(() -> {
            try {
                // اول پیامک تازه را ذخیره و ارسال می‌کنیم
                savePendingSms(appContext, msg, finalSmsTime);
                retryPendingSms(appContext);

                // بعد فقط Inbox همان تاریخ را اسکن می‌کنیم
                if (dateTagFromMsg != null && dateTagFromMsg.length() > 0) {
                    scanInboxBankSmsForDate(appContext, dateTagFromMsg, 300);
                } else {
                    scanInboxBankSmsToday(appContext, 300);
                }

                retryPendingSms(appContext);
            } finally {
                pendingResult.finish();
            }
        }).start();
    }

    public static void scanInboxBankSmsToday(Context context, int limit) {
        String todayTag = getTodayShamsiTag();
        scanInboxBankSmsForDate(context, todayTag, limit);
    }

    public static void scanInboxBankSmsForDate(Context context, String targetDateTag, int limit) {
        if (context == null) return;
        if (scanRunning) return;
        if (targetDateTag == null || targetDateTag.trim().length() == 0) return;

        scanRunning = true;

        new Thread(() -> {
            Cursor cursor = null;

            try {
                Context appContext = context.getApplicationContext();
                SharedPreferences prefs = appContext.getSharedPreferences(PREF_NAME, Context.MODE_PRIVATE);
                List<String> scanned = getScannedKeys(prefs);

                String target = normalizeDateTag(targetDateTag);

                cursor = appContext.getContentResolver().query(
                        Uri.parse("content://sms/inbox"),
                        new String[]{"body", "date"},
                        null,
                        null,
                        "date DESC"
                );

                if (cursor == null) return;

                int count = 0;

                while (cursor.moveToNext() && count < limit) {
                    count++;

                    String body = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                    long date = cursor.getLong(cursor.getColumnIndexOrThrow("date"));

                    if (body == null || body.trim().length() == 0) continue;
                    if (!isBankSms(body)) continue;

                    String bodyDate = extractDateTag(body);
                    if (bodyDate == null || bodyDate.length() == 0) continue;

                    if (!normalizeDateTag(bodyDate).equals(target)) continue;

                    String key = messageQueueKey(body, date);

                    // فقط برای جلوگیری از تکرار داخل صف گوشی؛ حذف تکراری اصلی در Code.gs است
                    if (!scanned.contains(key)) {
                        savePendingSms(appContext, body, date);
                        scanned.add(key);
                    }
                }

                while (scanned.size() > 3000) {
                    scanned.remove(0);
                }

                saveScannedKeys(prefs, scanned);
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
        boolean hasAccountOrCard = text.contains("حساب") || text.contains("کارت");

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

    private static String extractDateTag(String msg) {
        String text = normalize(msg);

        java.util.regex.Matcher full =
                java.util.regex.Pattern.compile("(\\d{4}/\\d{1,2}/\\d{1,2})").matcher(text);
        if (full.find()) return full.group(1);

        java.util.regex.Matcher compact =
                java.util.regex.Pattern.compile("(?:^|\\s)(\\d{2})(\\d{2})(?:\\s|[-–]|$)").matcher(text);
        if (compact.find()) {
            return getCurrentShamsiYear() + "/" + compact.group(1) + "/" + compact.group(2);
        }

        java.util.regex.Matcher afterTime =
                java.util.regex.Pattern.compile("\\d{1,2}:\\d{2}\\s*[-–]?\\s*(\\d{2})(\\d{2})").matcher(text);
        if (afterTime.find()) {
            return getCurrentShamsiYear() + "/" + afterTime.group(1) + "/" + afterTime.group(2);
        }

        return "";
    }

    private static String normalizeDateTag(String d) {
        String s = normalize(d);
        String[] p = s.split("/");
        if (p.length == 3) {
            return p[0] + "/" + pad2(p[1]) + "/" + pad2(p[2]);
        }
        return s;
    }

    private static String pad2(String s) {
        try {
            int n = Integer.parseInt(String.valueOf(s).replaceAll("[^0-9]", ""));
            return n < 10 ? "0" + n : String.valueOf(n);
        } catch (Exception e) {
            return s;
        }
    }

    private static String getTodayShamsiTag() {
        try {
            java.util.Calendar cal = java.util.Calendar.getInstance();

            int gy = cal.get(java.util.Calendar.YEAR);
            int gm = cal.get(java.util.Calendar.MONTH) + 1;
            int gd = cal.get(java.util.Calendar.DAY_OF_MONTH);

            int[] j = gregorianToJalali(gy, gm, gd);
            return j[0] + "/" + pad2(String.valueOf(j[1])) + "/" + pad2(String.valueOf(j[2]));

        } catch (Exception e) {
            return "";
        }
    }

    private static String getCurrentShamsiYear() {
        try {
            String today = getTodayShamsiTag();
            if (today.contains("/")) return today.split("/")[0];
        } catch (Exception ignored) {}
        return "1405";
    }

    private static int[] gregorianToJalali(int gy, int gm, int gd) {
        int[] g_d_m = {0,31,59,90,120,151,181,212,243,273,304,334};
        int jy;

        if (gy > 1600) {
            jy = 979;
            gy -= 1600;
        } else {
            jy = 0;
            gy -= 621;
        }

        int gy2 = (gm > 2) ? (gy + 1) : gy;
        int days = (365 * gy)
                + ((gy2 + 3) / 4)
                - ((gy2 + 99) / 100)
                + ((gy2 + 399) / 400)
                - 80 + gd + g_d_m[gm - 1];

        jy += 33 * (days / 12053);
        days %= 12053;

        jy += 4 * (days / 1461);
        days %= 1461;

        if (days > 365) {
            jy += (days - 1) / 365;
            days = (days - 1) % 365;
        }

        int jm;
        int jd;

        if (days < 186) {
            jm = 1 + (days / 31);
            jd = 1 + (days % 31);
        } else {
            jm = 7 + ((days - 186) / 30);
            jd = 1 + ((days - 186) % 30);
        }

        return new int[]{jy, jm, jd};
    }

    private static boolean sendNow(String msg) {
        HttpURLConnection conn = null;
        BufferedReader br = null;

        try {
            String encoded = URLEncoder.encode(msg, "UTF-8");
            URL url = new URL(API_BASE + encoded);

            conn = (HttpURLConnection) url.openConnection();
            conn.setRequestMethod("GET");
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(30000);
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
        if (context == null) return;
        if (msg == null || msg.trim().length() == 0) return;

        List<SmsItem> queue = getQueue(context);
        String newKey = messageQueueKey(msg, time);

        for (SmsItem item : queue) {
            if (item != null && messageQueueKey(item.msg, item.time).equals(newKey)) {
                return;
            }
        }

        queue.add(new SmsItem(time, msg));

        while (queue.size() > 1000) {
            queue.remove(0);
        }

        saveQueue(context, queue);
    }

    private static String messageQueueKey(String msg, long time) {
        return normalize(msg) + "|" + time;
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

    private static List<String> getScannedKeys(SharedPreferences prefs) {
        List<String> list = new ArrayList<>();

        try {
            JSONArray arr = new JSONArray(prefs.getString(KEY_SCANNED, "[]"));
            for (int i = 0; i < arr.length(); i++) {
                String s = arr.optString(i, "");
                if (s != null && s.length() > 0) list.add(s);
            }
        } catch (Exception ignored) {}

        return list;
    }

    private static void saveScannedKeys(SharedPreferences prefs, List<String> keys) {
        try {
            JSONArray arr = new JSONArray();

            for (String k : keys) {
                if (k != null && k.length() > 0) arr.put(k);
            }

            prefs.edit().putString(KEY_SCANNED, arr.toString()).apply();

        } catch (Exception ignored) {}
    }

    public static int getPendingSmsCount(Context context) {
        if (context == null) return 0;
        return getQueue(context.getApplicationContext()).size();
    }
}
