package com.daftar.rooznameh;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final int REQ_SMS = 1001;
    private static final String ADMIN_URL =
            "https://script.google.com/macros/s/AKfycbxsXvURwus4QlQlVzhRed-FRONKB7tDojZCSnj5LJjm9YlGJUt5bLa17KjxjuNkEvme/exec?page=admin";

    private WebView webView;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        webView = new WebView(this);
        setContentView(webView);

        WebSettings settings = webView.getSettings();
        settings.setJavaScriptEnabled(true);
        settings.setDomStorageEnabled(true);
        settings.setDatabaseEnabled(true);
        settings.setLoadWithOverviewMode(true);
        settings.setUseWideViewPort(true);
        settings.setBuiltInZoomControls(false);
        settings.setDisplayZoomControls(false);

        webView.setWebViewClient(new WebViewClient());
        webView.loadUrl(ADMIN_URL);

        requestSmsPermission();
        syncTodaySms();
    }

    private void requestSmsPermission() {
        if (android.os.Build.VERSION.SDK_INT < 23) return;

        if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(new String[]{
                    Manifest.permission.RECEIVE_SMS,
                    Manifest.permission.READ_SMS
            }, REQ_SMS);
        }
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_SMS) syncTodaySms();
    }

    private void syncTodaySms() {
        // SmsReceiver فقط پیامک‌های دارای «مانده» را اسکن/ارسال می‌کند.
        SmsReceiver.retryPendingSms(getApplicationContext());
    }

    @Override
    protected void onResume() {
        super.onResume();
        syncTodaySms();
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) webView.goBack();
        else super.onBackPressed();
    }
}
