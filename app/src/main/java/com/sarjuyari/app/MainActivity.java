package com.sarjuyari.app;

import android.Manifest;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public class MainActivity extends Activity {

    private TextView status;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp(20), dp(32), dp(20), dp(20));

        TextView title = new TextView(this);
        title.setText("Şarj Uyarısı");
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        title.setTextColor(0xFFB00020);
        title.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.addView(title);

        TextView info = new TextView(this);
        info.setText("Pil %25'e düştüğünde ve telefon şarjda değilse ekranda kırmızı "
                + "\"Şarj Edin\" uyarısı çıkar.\n\nÇalışması için aşağıdaki adımları sırayla yapın:");
        info.setPadding(0, dp(16), 0, dp(8));
        layout.addView(info);

        status = new TextView(this);
        status.setPadding(0, dp(8), 0, dp(8));
        layout.addView(status);

        addButton(layout, "1) Diğer uygulamaların üzerinde göster iznini ver", v -> {
            Intent i = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        addButton(layout, "2) Bildirim iznini ver", v -> {
            if (Build.VERSION.SDK_INT >= 33) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, 1);
            }
        });

        addButton(layout, "3) Pil optimizasyonundan muaf tut (önerilir)", v -> {
            Intent i = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS,
                    Uri.parse("package:" + getPackageName()));
            startActivity(i);
        });

        addButton(layout, "4) Servisi başlat", v -> {
            startForegroundService(new Intent(this, BatteryService.class));
            refreshStatus();
        });

        addButton(layout, "Uyarıyı şimdi test et", v -> {
            Intent i = new Intent(this, BatteryService.class);
            i.setAction(BatteryService.ACTION_TEST);
            startForegroundService(i);
        });

        ScrollView scroll = new ScrollView(this);
        scroll.addView(layout, new ViewGroup.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        refreshStatus();
        // İzin verilmişse servisi kendiliğinden başlat.
        if (Settings.canDrawOverlays(this)) {
            startForegroundService(new Intent(this, BatteryService.class));
        }
    }

    private void refreshStatus() {
        boolean overlay = Settings.canDrawOverlays(this);
        boolean notif = Build.VERSION.SDK_INT < 33
                || checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                == PackageManager.PERMISSION_GRANTED;
        PowerManager pm = (PowerManager) getSystemService(POWER_SERVICE);
        boolean battery = pm.isIgnoringBatteryOptimizations(getPackageName());
        status.setText("Üzerinde göster izni: " + (overlay ? "✔ verildi" : "✘ gerekli")
                + "\nBildirim izni: " + (notif ? "✔ verildi" : "✘ verilmedi")
                + "\nPil optimizasyonu muafiyeti: " + (battery ? "✔ var" : "✘ yok"));
    }

    private void addButton(LinearLayout parent, String text, View.OnClickListener l) {
        Button b = new Button(this);
        b.setText(text);
        b.setAllCaps(false);
        b.setOnClickListener(l);
        parent.addView(b, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.WRAP_CONTENT));
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }
}
