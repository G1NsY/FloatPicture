package tool.xfy9326.floatpicture.Methods;


import android.Manifest;
import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Build;
import android.os.Handler;
import android.provider.Settings;
import android.widget.Toast;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.preference.PreferenceManager;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

public class PermissionMethods {

    public static void askNotificationPermission(Activity activity) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
                && !PreferenceManager.getDefaultSharedPreferences(activity)
                .getBoolean(Config.PREFERENCE_NOTIFICATION_PERMISSION_REQUESTED, false)) {
            PreferenceManager.getDefaultSharedPreferences(activity).edit()
                    .putBoolean(Config.PREFERENCE_NOTIFICATION_PERMISSION_REQUESTED, true)
                    .apply();
            ActivityCompat.requestPermissions(
                    activity,
                    new String[]{Manifest.permission.POST_NOTIFICATIONS},
                    Config.REQUEST_CODE_PERMISSION_NOTIFICATION);
        }
    }

    @SuppressWarnings("SameParameterValue")
    public static void askOverlayPermission(final Activity mActivity, final int requestCode) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (!Settings.canDrawOverlays(mActivity)) {
                AlertDialog.Builder overlayPermission = new AlertDialog.Builder(mActivity);
                overlayPermission.setTitle(R.string.permission_warn);
                overlayPermission.setMessage(R.string.permission_warn_overlay_explanation);
                overlayPermission.setPositiveButton(R.string.done, (dialogInterface, i) -> {
                    Intent intent = new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION);
                    intent.setData(Uri.parse("package:" + mActivity.getPackageName()));
                    mActivity.startActivityForResult(intent, requestCode);
                });
                overlayPermission.setNegativeButton(R.string.cancel, (dialogInterface, i) -> mActivity.finish());
                overlayPermission.setCancelable(false);
                overlayPermission.show();
            }
        }
    }

    public static void delayOverlayPermissionCheck(final Context mContext) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(mContext)) {
                ManageMethods.RunWin(mContext);
            } else {
                new Handler().postDelayed(() -> {
                    if (Settings.canDrawOverlays(mContext)) {
                        ManageMethods.RunWin(mContext);
                    } else {
                        Toast.makeText(mContext, R.string.permission_warn_overlay_intent, Toast.LENGTH_SHORT).show();
                    }
                }, 1500);
            }
        } else {
            ManageMethods.RunWin(mContext);
        }
    }

    public static boolean canDrawOverlays(Context context) {
        return Build.VERSION.SDK_INT < Build.VERSION_CODES.M || Settings.canDrawOverlays(context);
    }
}
