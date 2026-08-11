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
import android.os.Build;
import android.os.IBinder;
import android.view.View;
import android.widget.RemoteViews;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Objects;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.ManageListAdapter;

public class NotificationService extends Service {
    private static final String CHANNEL_ID = "channel_default";
    private RemoteViews remoteViews;
    private NotificationCompat.Builder builder_manage;
    private NotificationButtonBroadcastReceiver notificationButtonBroadcastReceiver;

    @Override
    public void onConfigurationChanged(android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);
        android.view.WindowManager wm = (android.view.WindowManager) getSystemService(Context.WINDOW_SERVICE);
        android.graphics.Point size = new android.graphics.Point();
        wm.getDefaultDisplay().getRealSize(size);
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

    @SuppressLint({"ForegroundServiceType", "UnspecifiedRegisterReceiverFlag"})
    @Override
    public void onCreate() {
        super.onCreate();
        if (notificationButtonBroadcastReceiver == null) {
            notificationButtonBroadcastReceiver = new NotificationButtonBroadcastReceiver();
            IntentFilter intentFilter = new IntentFilter();
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK);
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_PREVIOUS);
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_NEXT);
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_SAVE_GESTURE);
            intentFilter.addAction(Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT);
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                registerReceiver(notificationButtonBroadcastReceiver, intentFilter, Context.RECEIVER_NOT_EXPORTED);
            } else {
                registerReceiver(notificationButtonBroadcastReceiver, intentFilter);
            }
        }
        if (builder_manage == null) {
            builder_manage = createNotification();
            startForeground(Config.NOTIFICATION_ID, builder_manage.build());
        }
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
        remoteViews.setImageViewResource(R.id.imageview_notification_application, R.mipmap.ic_launcher);
        updatePictureStatus(mainApplication);

        remoteViews.setImageViewResource(R.id.imageview_previous_picture, android.R.drawable.ic_media_previous);
        Intent intent_previous = new Intent(Config.INTENT_ACTION_NOTIFICATION_PREVIOUS).setPackage(getPackageName());
        PendingIntent pendingIntent_previous = PendingIntent.getBroadcast(this, 1, intent_previous, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.imageview_previous_picture, pendingIntent_previous);

        remoteViews.setImageViewResource(
                R.id.imageview_set_picture_view,
                ManageMethods.isCurrentPictureVisible(this)
                        ? R.drawable.ic_invisible
                        : R.drawable.ic_visible);
        Intent intent_picture_show = new Intent(Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK).setPackage(getPackageName());
        PendingIntent pendingIntent_picture_show = PendingIntent.getBroadcast(this, 2, intent_picture_show, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.imageview_set_picture_view, pendingIntent_picture_show);

        remoteViews.setImageViewResource(R.id.imageview_next_picture, android.R.drawable.ic_media_next);
        Intent intent_next = new Intent(Config.INTENT_ACTION_NOTIFICATION_NEXT).setPackage(getPackageName());
        PendingIntent pendingIntent_next = PendingIntent.getBroadcast(this, 3, intent_next, PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(R.id.imageview_next_picture, pendingIntent_next);

        remoteViews.setImageViewResource(
                R.id.imageview_save_gesture, android.R.drawable.ic_menu_save);
        Intent intent_save_gesture = new Intent(
                Config.INTENT_ACTION_NOTIFICATION_SAVE_GESTURE).setPackage(getPackageName());
        PendingIntent pendingIntent_save_gesture = PendingIntent.getBroadcast(
                this, 4, intent_save_gesture,
                PendingIntent.FLAG_IMMUTABLE | PendingIntent.FLAG_UPDATE_CURRENT);
        remoteViews.setOnClickPendingIntent(
                R.id.imageview_save_gesture, pendingIntent_save_gesture);
        updateSaveGestureButtonVisibility();

        builder.setContent(remoteViews);
        return builder;
    }

    private void updatePictureStatus(MainApplication mainApplication) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        if (pictures.isEmpty()) {
            remoteViews.setTextViewText(R.id.textview_picture_num,
                    getString(R.string.notification_picture_count, "0"));
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

    private void updateSaveGestureButtonVisibility() {
        boolean visible = androidx.preference.PreferenceManager
                .getDefaultSharedPreferences(this)
                .getBoolean(Config.PREFERENCE_SAVE_GESTURE_ADJUSTMENTS, false);
        remoteViews.setViewVisibility(
                R.id.imageview_save_gesture, visible ? View.VISIBLE : View.GONE);
    }

    private class NotificationButtonBroadcastReceiver extends BroadcastReceiver {
        @SuppressLint("NotifyDataSetChanged")
        @Override
        public void onReceive(Context context, Intent intent) {
            if (remoteViews != null) {
                MainApplication mainApplication = (MainApplication) getApplicationContext();
                boolean refreshPictureList = false;
                if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_BUTTON_CLICK)) {
                    boolean showCurrentPicture = !ManageMethods.isCurrentPictureVisible(context);
                    refreshPictureList = ManageMethods.setCurrentPictureVisible(
                            context, showCurrentPicture);
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_PREVIOUS)) {
                    if (ManageMethods.switchPicture(context, -1)) {
                        remoteViews.setImageViewResource(R.id.imageview_set_picture_view, R.drawable.ic_invisible);
                        refreshPictureList = true;
                    }
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_NEXT)) {
                    if (ManageMethods.switchPicture(context, 1)) {
                        remoteViews.setImageViewResource(R.id.imageview_set_picture_view, R.drawable.ic_invisible);
                        refreshPictureList = true;
                    }
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_SAVE_GESTURE)) {
                    boolean lockAfterSave = androidx.preference.PreferenceManager
                            .getDefaultSharedPreferences(context)
                            .getBoolean(Config.PREFERENCE_LOCK_GESTURES_AFTER_SAVE, false);
                    boolean saved = ManageMethods.saveCurrentPictureGestureAdjustments(context);
                    Toast.makeText(
                            context,
                            saved
                                    ? (lockAfterSave
                                    ? R.string.gesture_adjustments_saved_and_locked
                                    : R.string.gesture_adjustments_saved)
                                    : R.string.gesture_adjustments_save_failed,
                            Toast.LENGTH_SHORT).show();
                } else if (Objects.equals(intent.getAction(), Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT)) {
                    // Status text is refreshed below after additions, deletions or reordering.
                }
                if (refreshPictureList) {
                    ManageListAdapter manageListAdapter = mainApplication.getManageListAdapter();
                    if (manageListAdapter != null) {
                        manageListAdapter.notifyDataSetChanged();
                    }
                }
                remoteViews.setImageViewResource(
                        R.id.imageview_set_picture_view,
                        ManageMethods.isCurrentPictureVisible(context)
                                ? R.drawable.ic_invisible
                                : R.drawable.ic_visible);
                updateSaveGestureButtonVisibility();
                updatePictureStatus(mainApplication);
                NotificationManager notificationManager = (NotificationManager) getSystemService(Context.NOTIFICATION_SERVICE);
                builder_manage.setContent(remoteViews);
                Objects.requireNonNull(notificationManager).notify(Config.NOTIFICATION_ID, builder_manage.build());
            }
        }
    }
}
