package tool.xfy9326.floatpicture.Services;

import android.annotation.SuppressLint;
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
import android.os.Build;
import android.os.IBinder;
import android.widget.RemoteViews;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "channel_default";
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builder_manage;
    private NotificationButtonBroadcastReceiver notificationButtonBroadcastReceiver;
    private FloatingControlManager floatingControlManager;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point size = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(size);
        if (floatingControlManager != null) {
            floatingControlManager.onConfigurationChanged();
        }
    }

    private static void createNotificationChannel(@NonNull Context context, @NonNull NotificationManagerCompat notificationManager) {
        if (Build.VERSION.SDK_INT >= 26) {
            NotificationChannel notificationChannel = notificationManager.getNotificationChannel(CHANNEL_ID);
            if (notificationChannel == null) {
                notificationChannel = new NotificationChannel(CHANNEL_ID, context.getString(R.string.notification_channel), NotificationManager.IMPORTANCE_LOW);
                notificationChannel.setDescription(context.getString(R.string.notification_channel_des));
                notificationChannel.setLockscreenVisibility(Notification.VISIBILITY_PUBLIC);
                notificationChannel.setShowBadge(false);
                notificationChannel.enableLights(false);
                notificationChannel.enableVibration(false);
                notificationManager.createNotificationChannel(notificationChannel);
            }
        }
    }

    @SuppressLint("UnspecifiedRegisterReceiverFlag")
    @Override
    public void onCreate() {
        super.onCreate();
        if (notificationButtonBroadcastReceiver == null) {
            notificationButtonBroadcastReceiver = new NotificationButtonBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_FLOATING_CONTROL_TOGGLE);
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT);
            intentFilter.addAction(Config.INTENT_ACTION_FLOATING_CONTROL_REFRESH);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationButtonBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notificationButtonBroadcastReceiver, intentFilter);
            }
        }
        if (builder_manage == null) {
            builder_manage = createNotification();
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                startForeground(
                        Config.NOTIFICATION_ID,
                        builder_manage.build(),
                        ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                startForeground(Config.NOTIFICATION_ID, builder_manage.build());
            }
        }
        if (floatingControlManager == null) {
            floatingControlManager = new FloatingControlManager(this);
        }
        floatingControlManager.refreshVisibility();
    }

    @Nullable
    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    /**
     * 【新增】：监听 App 从后台任务栏被划掉的事件
     */
    @Override
    public void onTaskRemoved(Intent rootIntent) {
        super.onTaskRemoved(rootIntent);
        // 移除所有悬浮窗
        ManageMethods.hideAllWindowsRuntime(this);
        // 停止前台服务
        stopSelf();
    }

    @Override
    public void onDestroy() {
        // 在服务销毁时也确保窗口被关闭
        ManageMethods.hideAllWindowsRuntime(this);
        if (floatingControlManager != null) {
            floatingControlManager.destroy();
            floatingControlManager = null;
        }
        
        if (notificationButtonBroadcastReceiver != null) {
            unregisterReceiver(notificationButtonBroadcastReceiver);
            notificationButtonBroadcastReceiver = null;
        }
        if (builder_manage != null) {
            stopForeground(true);
            builder_manage = null;
        }
        super.onDestroy();
    }

    private NotificationCompat.Builder createNotification() {
        MainApplication mainApplication = (MainApplication) getApplicationContext();
        NotificationCompat.Builder builder = new NotificationCompat.Builder(this, CHANNEL_ID);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(this);
        createNotificationChannel(this, notificationManager);

        builder.setSmallIcon(R.drawable.ic_notification);

        Intent intent_main = new Intent(this, MainActivity.class);
        intent_main.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
        PendingIntent pendingIntent_main = PendingIntent.getActivity(this, 0, intent_main, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        builder.setContentIntent(pendingIntent_main);

        remoteViews = new RemoteViews(getPackageName(), R.layout.notification_manage);
        updatePictureStatus(mainApplication);

        updateFloatingControlButton();
        Intent toggleFloatingControl = new Intent(
                Config.INTENT_ACTION_NOTIFICATION_FLOATING_CONTROL_TOGGLE)
                .setPackage(getPackageName());
        PendingIntent toggleFloatingControlIntent = PendingIntent.getBroadcast(
                this, 1, toggleFloatingControl,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.imageview_set_floating_control, toggleFloatingControlIntent);

        builder.setContent(remoteViews);
        return builder;
    }

    private void updatePictureStatus(MainApplication mainApplication) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        if (pictures.isEmpty()) {
            remoteViews.setTextViewText(R.id.textview_picture_num,
                    getString(R.string.floating_control_no_picture));
            return;
        }

        ArrayList<String> pictureIds = new ArrayList<>(pictures.keySet());
        String currentPictureId = mainApplication.getCurrentPictureId();
        int currentIndex = pictureIds.indexOf(currentPictureId);
        if (currentIndex < 0) {
            currentIndex = 0;
            currentPictureId = pictureIds.get(0);
            mainApplication.setCurrentPictureId(currentPictureId);
        }
        remoteViews.setTextViewText(R.id.textview_picture_num,
                getString(R.string.notification_picture_position,
                        currentIndex + 1,
                        pictureIds.size(),
                        pictures.get(currentPictureId)));
    }

    private void updateFloatingControlButton() {
        boolean visible = PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(Config.PREFERENCE_SHOW_FLOATING_CONTROL, true);
        remoteViews.setImageViewResource(
                R.id.imageview_set_floating_control,
                visible
                        ? R.drawable.ic_floating_control_on
                        : R.drawable.ic_floating_control_off);
    }

    private class NotificationButtonBroadcastReceiver extends BroadcastReceiver {
        @Override
        public void onReceive(Context context, Intent intent) {
            if (remoteViews != null) {
                MainApplication mainApplication = (MainApplication) getApplicationContext();
                if (Objects.equals(intent.getAction(),
                        Config.INTENT_ACTION_NOTIFICATION_FLOATING_CONTROL_TOGGLE)) {
                    boolean currentlyVisible = PreferenceManager
                            .getDefaultSharedPreferences(context)
                            .getBoolean(Config.PREFERENCE_SHOW_FLOATING_CONTROL, true);
                    PreferenceManager.getDefaultSharedPreferences(context).edit()
                            .putBoolean(Config.PREFERENCE_SHOW_FLOATING_CONTROL, !currentlyVisible)
                            .apply();
                    if (floatingControlManager != null) {
                        floatingControlManager.refreshVisibility();
                    }
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT)) {
                    // Status text is refreshed below after additions, deletions or reordering.
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_FLOATING_CONTROL_REFRESH)) {
                    if (floatingControlManager != null) {
                        floatingControlManager.refreshVisibility();
                    }
                }
                updateFloatingControlButton();
                updatePictureStatus(mainApplication);
                if (floatingControlManager != null) {
                    floatingControlManager.refreshState();
                }
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                builder_manage.setContent(remoteViews);
                Objects.requireNonNull(notificationManager).notify(Config.NOTIFICATION_ID, builder_manage.build());
            }
        }
    }
}
