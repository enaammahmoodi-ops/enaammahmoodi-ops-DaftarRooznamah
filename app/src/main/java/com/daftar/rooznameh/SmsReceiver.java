package com.daftar.rooznameh;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.net.ConnectivityManager;
import android.net.Network;
import android.net.NetworkCapabilities;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.telephony.SmsMessage;
import android.util.Log;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.Calendar;

public class SmsReceiver extends BroadcastReceiver {
    private static final String API = "https://script.google.com/macros/s/AKfycbxsXvURwus4QlQlVzhRed-FRONKB7tDojZCSnj5LJjm9YlGJUt5bLa17KjxjuNkEvme/exec";
    private static final String PREF = "bank_sms_queue";
    private static final String QUEUE = "items";

    @Override public void onReceive(Context context, Intent intent) {
        if (context == null || intent == null || !"android.provider.Telephony.SMS_RECEIVED".equals(intent.getAction())) return;
        Bundle b = intent.getExtras(); if (b == null) return;
        Object[] pdus = (Object[]) b.get("pdus"); if (pdus == null) return;
        StringBuilder body = new StringBuilder(); String sender = ""; long receivedAt = System.currentTimeMillis();
        for (Object pdu : pdus) {
            SmsMessage sms;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                sms = SmsMessage.createFromPdu((byte[]) pdu, b.getString("format"));
            } else {
                sms = SmsMessage.createFromPdu((byte[]) pdu);
            }
            if (sms != null) { body.append(sms.getMessageBody()); if (sender.isEmpty()) sender = sms.getOriginatingAddress(); receivedAt = sms.getTimestampMillis(); }
        }
        String text = body.toString().trim();
        if (text.isEmpty() || !text.contains("مانده")) return;
        Context app = context.getApplicationContext(); add(app, text, sender, receivedAt);
        PendingResult result = goAsync(); new Thread(() -> { try { retryPendingSms(app); } finally { result.finish(); } }).start();
    }

    public static void retryPendingSms(Context context) {
        if (context == null || !online(context)) return;
        scanInboxBankSmsToday(context, 300);
        JSONArray old = read(context), keep = new JSONArray();
        for (int i = 0; i < old.length(); i++) try { JSONObject sms = old.getJSONObject(i); if (!post(sms)) keep.put(sms); } catch (Exception ignored) { }
        write(context, keep);
    }

    public static void scanInboxBankSmsToday(Context context, int limit) {
        Calendar c = Calendar.getInstance(); c.set(Calendar.HOUR_OF_DAY,0); c.set(Calendar.MINUTE,0); c.set(Calendar.SECOND,0); c.set(Calendar.MILLISECOND,0);
        long start = c.getTimeInMillis(), end = start + 86400000L; Cursor cursor = null;
        try {
            cursor = context.getContentResolver().query(Uri.parse("content://sms/inbox"), new String[]{"body","address","date"}, "date >= ? AND date < ?", new String[]{String.valueOf(start),String.valueOf(end)}, "date DESC");
            if (cursor == null) return; int n=0, bi=cursor.getColumnIndexOrThrow("body"), ai=cursor.getColumnIndexOrThrow("address"), di=cursor.getColumnIndexOrThrow("date");
            while (cursor.moveToNext() && n++ < limit) { String body=cursor.getString(bi); if (body != null && body.contains("مانده")) add(context,body.trim(),cursor.getString(ai),cursor.getLong(di)); }
        } catch (Exception e) { Log.e("SmsReceiver","Scan error",e); } finally { if (cursor != null) cursor.close(); }
    }

    private static synchronized void add(Context c, String body, String sender, long time) {
        try { String key=hash((sender==null?"":sender)+"|"+time+"|"+body); JSONArray q=read(c); for(int i=0;i<q.length();i++) if(key.equals(q.getJSONObject(i).optString("key"))) return; JSONObject x=new JSONObject(); x.put("key",key);x.put("msg",body);x.put("sender",sender==null?"":sender);x.put("receivedAt",time);q.put(x);write(c,q); } catch(Exception ignored) { }
    }

    private static boolean post(JSONObject sms) {
        HttpURLConnection conn=null; try { byte[] bytes=sms.toString().getBytes(StandardCharsets.UTF_8); conn=(HttpURLConnection)new URL(API).openConnection(); conn.setRequestMethod("POST");conn.setConnectTimeout(20000);conn.setReadTimeout(20000);conn.setDoOutput(true);conn.setRequestProperty("Content-Type","application/json; charset=UTF-8");try(OutputStream os=conn.getOutputStream()){os.write(bytes);}int code=conn.getResponseCode();return code>=200&&code<300; } catch(Exception e){return false;} finally {if(conn!=null)conn.disconnect();}
    }

    private static JSONArray read(Context c){try{return new JSONArray(c.getSharedPreferences(PREF,Context.MODE_PRIVATE).getString(QUEUE,"[]"));}catch(Exception e){return new JSONArray();}}
    private static void write(Context c, JSONArray q){c.getSharedPreferences(PREF,Context.MODE_PRIVATE).edit().putString(QUEUE,q.toString()).apply();}
    private static boolean online(Context c){ConnectivityManager m=(ConnectivityManager)c.getSystemService(Context.CONNECTIVITY_SERVICE);if(m==null)return false;if(Build.VERSION.SDK_INT>=23){Network n=m.getActiveNetwork();NetworkCapabilities p=m.getNetworkCapabilities(n);return p!=null&&p.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET);}android.net.NetworkInfo i=m.getActiveNetworkInfo();return i!=null&&i.isConnected();}
    private static String hash(String value){try{byte[] b=MessageDigest.getInstance("SHA-256").digest(value.getBytes(StandardCharsets.UTF_8));StringBuilder r=new StringBuilder();for(byte x:b)r.append(String.format("%02x",x));return r.toString();}catch(Exception e){return String.valueOf(value.hashCode());}}
}
