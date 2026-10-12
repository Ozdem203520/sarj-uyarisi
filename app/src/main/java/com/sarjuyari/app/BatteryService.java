package com.sarjuyari.app;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.app.Service;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.pm.ServiceInfo;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.os.BatteryManager;
import android.os.Build;
import android.os.IBinder;
import android.provider.Settings;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

/**
 * Pil seviyesini izler. Seviye %25 veya altına düşer ve telefon şarjda değilse
 * tüm ekranı kaplayan kırmızı "Şarj Edin" uyarısını gösterir.
 */
public class BatteryService extends Service {

    public static final String ACTION_TEST = "com.sarjuyari.app.TEST";
    private static final int THRESHOLD = 25;
    private static final String CHANNEL_ID = "pil_izleme";
    private static final int NOTIF_ID = 1;

    private WindowManager windowManager;
    private View overlayView;
    private TextView percentView;
    private boolean dismissedByUser = false;
    private int lastPercent = 100;
    private boolean lastCharging = false;

    private final BroadcastReceiver batteryReceiver = new BroadcastReceiver() {
        @Override
        public void onReceive(Context context, Intent intent) {
            handleBattery(intent);
        }
    };

    @Override
    public void onCreate() {
        super.onCreate();
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        createChannel();
        startAsForeground();
        // ACTION_BATTERY_CHANGED yapışkan (sticky) bir yayındır; kayıt anında son değeri döndürür.
        Intent sticky = registerReceiver(batteryReceiver, new IntentFilter(Intent.ACTION_BATTERY_CHANGED));
        if (sticky != null) {
            handleBattery(sticky);
        }
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null && ACTION_TEST.equals(intent.getAction())) {
            showOverlay(lastPercent);
        }
        return START_STICKY;
    }

    private void handleBattery(Intent intent) {
        int level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1);
        int scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, 100);
        int status = intent.getIntExtra(BatteryManager.EXTRA_STATUS, -1);
        int plugged = intent.getIntExtra(BatteryManager.EXTRA_PLUGGED, 0);
        if (level < 0 || scale <= 0) {
            return;
        }
        int percent = Math.round(level * 100f / scale);
        boolean charging = plugged != 0
                || status == BatteryManager.BATTERY_STATUS_CHARGING
                || status == BatteryManager.BATTERY_STATUS_FULL;

        lastPercent = percent;
        lastCharging = charging;

        if (charging || percent > THRESHOLD) {
            // Şarja takıldı veya seviye yükseldi: uyarıyı kapat, bir sonraki düşüş için sıfırla.
            dismissedByUser = false;
            removeOverlay();
        } else if (!dismissedByUser) {
            showOverlay(percent);
        }
        updateNotification(percent);
    }

    private void showOverlay(int percent) {
        if (!Settings.canDrawOverlays(this)) {
            return; // İzin yok; ana ekrandan izin verilmesi gerekir.
        }
        if (overlayView != null) {
            percentView.setText("%" + percent);
            return;
        }

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundColor(Color.parseColor("#F2B00020"));
        root.setPadding(dp(24), dp(24), dp(24), dp(24));

        ImageView icon = new ImageView(this);
        icon.setImageResource(R.drawable.ic_low_battery);
        root.addView(icon, new LinearLayout.LayoutParams(dp(160), dp(160)));

        TextView title = new TextView(this);
        title.setText("Şarj Edin");
        title.setTextColor(Color.WHITE);
        title.setTextSize(TypedValue.COMPLEX_UNIT_SP, 48);
        title.setTypeface(title.getTypeface(), android.graphics.Typeface.BOLD);
        title.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams titleLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        titleLp.topMargin = dp(16);
        root.addView(title, titleLp);

        percentView = new TextView(this);
        percentView.setText("%" + percent);
        percentView.setTextColor(Color.WHITE);
        percentView.setTextSize(TypedValue.COMPLEX_UNIT_SP, 28);
        percentView.setGravity(Gravity.CENTER);
        root.addView(percentView, new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT));

        TextView hint = new TextView(this);
        hint.setText("Kapatmak için ekrana dokunun");
        hint.setTextColor(Color.parseColor("#FFCDD2"));
        hint.setTextSize(TypedValue.COMPLEX_UNIT_SP, 14);
        hint.setGravity(Gravity.CENTER);
        LinearLayout.LayoutParams hintLp = new LinearLayout.LayoutParams(
                ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
        hintLp.topMargin = dp(40);
        root.addView(hint, hintLp);

        root.setOnClickListener(v -> {
            dismissedByUser = true; // Şarja takılana veya %25 üstüne çıkana kadar tekrar gösterme.
            removeOverlay();
        });

        WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.MATCH_PARENT,
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN
                        | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS,
                PixelFormat.TRANSLUCENT);
        lp.gravity = Gravity.CENTER;

        try {
            windowManager.addView(root, lp);
            overlayView = root;
        } catch (RuntimeException e) {
            overlayView = null;
            percentView = null;
        }
    }

    private void removeOverlay() {
        if (overlayView != null) {
            try {
                windowManager.removeView(overlayView);
            } catch (RuntimeException ignored) {
            }
            overlayView = null;
            percentView = null;
        }
    }

    private int dp(int value) {
        return Math.round(TypedValue.applyDimension(
                TypedValue.COMPLEX_UNIT_DIP, value, getResources().getDisplayMetrics()));
    }

    private void createChannel() {
        NotificationChannel channel = new NotificationChannel(
                CHANNEL_ID, "Pil izleme", NotificationManager.IMPORTANCE_LOW);
        channel.setDescription("Pil seviyesini izlemek için çalışır");
        getSystemService(NotificationManager.class).createNotificationChannel(channel);
    }

    private Notification buildNotification(int percent) {
        PendingIntent pi = PendingIntent.getActivity(
                this, 0, new Intent(this, MainActivity.class),
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        String text = Settings.canDrawOverlays(this)
                ? "Pil %" + percent + " — %" + THRESHOLD + " altında uyarı verilecek"
                : "Uyarı için \"diğer uygulamaların üzerinde göster\" izni gerekli";
        return new Notification.Builder(this, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_low_battery)
                .setContentTitle("Şarj Uyarısı çalışıyor")
                .setContentText(text)
                .setContentIntent(pi)
                .setOngoing(true)
                .build();
    }

    private void startAsForeground() {
        Notification n = buildNotification(lastPercent);
        if (Build.VERSION.SDK_INT >= 34) {
            startForeground(NOTIF_ID, n, ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
        } else {
            startForeground(NOTIF_ID, n);
        }
    }

    private void updateNotification(int percent) {
        getSystemService(NotificationManager.class).notify(NOTIF_ID, buildNotification(percent));
    }

    @Override
    public void onDestroy() {
        try {
            unregisterReceiver(batteryReceiver);
        } catch (IllegalArgumentException ignored) {
        }
        removeOverlay();
        super.onDestroy();
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
