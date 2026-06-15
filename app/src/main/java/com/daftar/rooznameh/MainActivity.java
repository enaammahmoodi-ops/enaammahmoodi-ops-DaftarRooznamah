package com.daftar.rooznameh;

import android.Manifest;
import android.app.Activity;
import android.os.Bundle;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;
import android.content.pm.PackageManager;
import android.widget.Toast;

public class MainActivity extends Activity {
    private static final int REQ_SMS = 1001;

    private static final String ADMIN_URL =
            "https://script.google.com/macros/s/AKfycbyLjGFEBZuoF2HxMYHvbJaTEjM8NXf4_6mEUGd4iKE0Fp1xZwIwl3XfY5EhepGlKj72/exec?page=admin";

    @Override
    protected void onCreate(Bundle b) {
        super.onCreate(b);

        WebView web = new WebView(this);
        setContentView(web);

        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(true);
        s.setDomStorageEnabled(true);
        s.setLoadWithOverviewMode(true);
        s.setUseWideViewPort(true);

        web.setWebViewClient(new WebViewClient());
        web.loadUrl(ADMIN_URL);

        requestSmsPermission();

        // اگر قبلاً پیامکی به دلیل قطع اینترنت ذخیره شده باشد، با باز شدن برنامه دوباره ارسال می‌شود.
        SmsReceiver.retryPendingSms(this);
    }

    @Override
    protected void onResume() {
        super.onResume();
        // هر بار برگشت به برنامه، صف پیامک‌های ارسال‌نشده دوباره بررسی می‌شود.
        SmsReceiver.retryPendingSms(this);
    }

    private void requestSmsPermission() {
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
        if (requestCode == REQ_SMS) {
            boolean ok = true;
            for (int r : grantResults) {
                if (r != PackageManager.PERMISSION_GRANTED) ok = false;
            }
            Toast.makeText(this, ok ? "اجازه پیامک فعال شد" : "اجازه پیامک داده نشد", Toast.LENGTH_LONG).show();
        }
    }
}
