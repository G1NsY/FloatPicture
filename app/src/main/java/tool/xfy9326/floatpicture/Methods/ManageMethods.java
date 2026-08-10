package tool.xfy9326.floatpicture.Methods;


import static tool.xfy9326.floatpicture.Methods.WindowsMethods.getWindowManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.WindowManager;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

import androidx.preference.PreferenceManager;
import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;


public class ManageMethods {

    public static void SelectPicture(Activity mActivity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        mActivity.startActivityForResult(intent, Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_GET_PICTURE);
    }

    public static void RunWin(Context mContext) {
        if (PermissionMethods.checkPermission(mContext, PermissionMethods.StoragePermission)) {
            PictureData pictureData = new PictureData();
            LinkedHashMap<String, String> list = pictureData.getListArray();
            WindowManager windowManager = getWindowManager(mContext);
            if (list.size() > 0) {
                String firstVisiblePictureId = null;
                for (LinkedHashMap.Entry<?, ?> entry : list.entrySet()) {
                    String pictureId = entry.getKey().toString();
                    pictureData.setDataControl(pictureId);
                    if (firstVisiblePictureId == null
                            && pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                        firstVisiblePictureId = pictureId;
                    }
                    StartWin(mContext, windowManager, pictureData, pictureId);
                }
                MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
                String currentPictureId = mainApplication.getCurrentPictureId();
                if (currentPictureId == null || !list.containsKey(currentPictureId)) {
                    mainApplication.setCurrentPictureId(firstVisiblePictureId != null
                            ? firstVisiblePictureId
                            : list.keySet().iterator().next());
                }
                mainApplication.setWinVisible(firstVisiblePictureId != null);
            } else {
                ((MainApplication) mContext.getApplicationContext()).setWinVisible(false);
            }
        }
    }

    private static void StartWin(Context mContext, WindowManager windowManager, PictureData pictureData, String id) {
        pictureData.setDataControl(id);
        Bitmap bitmap = ImageMethods.getShowBitmap(mContext, id);
        float default_zoom = pictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, ImageMethods.getDefaultZoom(mContext, bitmap, false));
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
        float picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        float picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        int position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        FloatImageView floatImageView = ImageMethods.createPictureView(mContext, bitmap, touch_and_move || global_touchable, over_layout, zoom, picture_degree);
        floatImageView.setAlpha(picture_alpha);
        ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
        if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
            WindowsMethods.createWindow(windowManager, floatImageView, touch_and_move || global_touchable, over_layout, position_x, position_y);
        }
    }

    public static void DeleteWin(Context mContext, String id) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        if (floatImageView != null) {
            removeWindowIfAttached(getWindowManager(mContext), floatImageView);
            floatImageView.refreshDrawableState();
        }
        pictureData.remove();
        ImageMethods.clearAllTemp(mContext, id);
        MainApplication mainApplication = (MainApplication) mContext.getApplicationContext();
        mainApplication.unregisterView(id);
        if (id.equals(mainApplication.getCurrentPictureId())) {
            mainApplication.setCurrentPictureId(null);
            mainApplication.setPictureSequenceMode(false);
        }
    }

    static void CloseAllWindows(Context mContext) {
        hideAllWindowsRuntime(mContext);
    }

    public static void updateAllWindowsMovability(Context mContext, boolean global_touchable) {
        HashMap<String, View> hashMap = ((MainApplication) mContext.getApplicationContext()).getRegister();
        WindowManager windowManager = getWindowManager(mContext);
        PictureData pictureData = new PictureData();
        if (hashMap.size() > 0) {
            for (HashMap.Entry<?, ?> entry : hashMap.entrySet()) {
                String id = entry.getKey().toString();
                pictureData.setDataControl(id);
                if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                    FloatImageView floatImageView = (FloatImageView) entry.getValue();
                    boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
                    boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
                    boolean isMoveable = touch_and_move || global_touchable;

                    floatImageView.setMoveable(isMoveable);
                    WindowsMethods.updateWindow(windowManager, floatImageView, isMoveable, over_layout, (int)floatImageView.getMovedPositionX(), (int)floatImageView.getMovedPositionY());
                }
            }
        }
    }

    public static void updateNotificationCount(Context context) {
        context.sendBroadcast(new Intent().setAction(Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT));
    }

    public static boolean switchPicture(Context context, int direction) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        if (pictures.isEmpty()) {
            return false;
        }

        List<String> pictureIds = new ArrayList<>(pictures.keySet());
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        int currentIndex = pictureIds.indexOf(mainApplication.getCurrentPictureId());
        int targetIndex;
        if (currentIndex < 0) {
            targetIndex = direction < 0 ? pictureIds.size() - 1 : 0;
        } else {
            int step = direction < 0 ? -1 : 1;
            targetIndex = Math.floorMod(currentIndex + step, pictureIds.size());
        }

        String targetPictureId = pictureIds.get(targetIndex);
        hideAllWindowsRuntime(context);
        if (!showWindowById(context, targetPictureId)) {
            return false;
        }
        pictureData.setExclusivePictureVisible(targetPictureId);
        mainApplication.setCurrentPictureId(targetPictureId);
        mainApplication.setPictureSequenceMode(true);
        mainApplication.setWinVisible(true);
        return true;
    }

    public static boolean showFirstPicture(Context context) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        if (pictures.isEmpty()) {
            ((MainApplication) context.getApplicationContext()).setWinVisible(false);
            return false;
        }

        String firstPictureId = pictures.keySet().iterator().next();
        hideAllWindowsRuntime(context);
        if (!showWindowById(context, firstPictureId)) {
            return false;
        }
        pictureData.setExclusivePictureVisible(firstPictureId);
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        mainApplication.setCurrentPictureId(firstPictureId);
        mainApplication.setPictureSequenceMode(true);
        mainApplication.setWinVisible(true);
        return true;
    }

    public static void prepareWindowForEditing(Context context, String pictureId) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        if (showWindowById(context, pictureId)) {
            mainApplication.setWinVisible(true);
        }
    }

    public static void finishWindowEditing(Context context, String pictureId, boolean originallyVisible) {
        if (originallyVisible) {
            showWindowById(context, pictureId);
        } else {
            hideWindowById(context, pictureId);
        }
        ((MainApplication) context.getApplicationContext()).setWinVisible(hasAttachedWindow(context));
    }

    public static void setSequenceWindowVisible(Context context, boolean visible) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        if (visible) {
            String currentPictureId = mainApplication.getCurrentPictureId();
            if (currentPictureId != null) {
                showWindowById(context, currentPictureId);
                PictureData pictureData = new PictureData();
                pictureData.setDataControl(currentPictureId);
                pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
                pictureData.commit(null);
            }
        } else {
            hideAllWindowsRuntime(context);
            String currentPictureId = mainApplication.getCurrentPictureId();
            if (currentPictureId != null) {
                PictureData pictureData = new PictureData();
                pictureData.setDataControl(currentPictureId);
                pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
                pictureData.commit(null);
            }
        }
        mainApplication.setWinVisible(visible);
    }

    public static void hideAllWindowsRuntime(Context context) {
        HashMap<String, View> registeredViews = ((MainApplication) context.getApplicationContext()).getRegister();
        WindowManager windowManager = getWindowManager(context);
        for (View view : registeredViews.values()) {
            if (view instanceof FloatImageView) {
                removeWindowIfAttached(windowManager, (FloatImageView) view);
            }
        }
    }

    public static void setAllWindowsVisible(Context context, boolean visible) {
        String id;
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> linkedHashMap = pictureData.getListArray();
        for (Map.Entry<?, ?> o : linkedHashMap.entrySet()) {
            id = o.getKey().toString();
            setWindowVisible(context, pictureData, id, visible);
        }
    }

    public static void setWindowVisible(Context context, PictureData pictureData, String id, boolean visible) {
        pictureData.setDataControl(id);
        if (visible) {
            showWindowById(context, id);
        } else {
            hideWindowById(context, id);
        }
        pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
        pictureData.commit(null);

        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        mainApplication.setPictureSequenceMode(false);
        if (visible) {
            mainApplication.setCurrentPictureId(id);
        }
        mainApplication.setWinVisible(hasAttachedWindow(context));
    }

    private static void hideWindowById(Context mContext, String id) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        if (floatImageView != null) {
            removeWindowIfAttached(getWindowManager(mContext), floatImageView);
        }
    }

    private static boolean showWindowById(Context mContext, String id) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        if (floatImageView == null) {
            return false;
        }
        if (floatImageView.isAttachedToWindow()) {
            return true;
        }
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        int positionX = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int positionY = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        boolean touch_and_move = pictureData.getBoolean(Config.DATA_PICTURE_TOUCH_AND_MOVE, Config.DATA_DEFAULT_PICTURE_TOUCH_AND_MOVE);
        boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean over_layout = pictureData.getBoolean(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, Config.DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT);
        WindowManager.LayoutParams layoutParams = WindowsMethods.getDefaultLayout(mContext, positionX, positionY, touch_and_move || global_touchable, over_layout);
        getWindowManager(mContext).addView(floatImageView, layoutParams);
        floatImageView.setMoveable(touch_and_move || global_touchable);
        return true;
    }

    private static void removeWindowIfAttached(WindowManager windowManager, FloatImageView floatImageView) {
        if (floatImageView.isAttachedToWindow()) {
            try {
                windowManager.removeView(floatImageView);
            } catch (IllegalArgumentException ignored) {
                // The window may already have been removed by a concurrent lifecycle callback.
            }
        }
    }

    private static boolean hasAttachedWindow(Context context) {
        for (View view : ((MainApplication) context.getApplicationContext()).getRegister().values()) {
            if (view instanceof FloatImageView && view.isAttachedToWindow()) {
                return true;
            }
        }
        return false;
    }

}
