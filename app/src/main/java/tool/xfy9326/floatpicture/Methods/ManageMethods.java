package tool.xfy9326.floatpicture.Methods;


import static tool.xfy9326.floatpicture.Methods.WindowsMethods.getWindowManager;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.view.View;
import android.view.ViewGroup;
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
import tool.xfy9326.floatpicture.View.ManageListAdapter;


public class ManageMethods {

    public static void SelectPicture(Activity mActivity) {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        mActivity.startActivityForResult(intent, Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_GET_PICTURE);
    }

    public static void RunWin(Context mContext) {
        if (!PermissionMethods.canDrawOverlays(mContext)) {
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> list = pictureData.getListArray();
        WindowManager windowManager = getWindowManager(mContext);
        if (list.size() > 0) {
            if (!allowsMultiplePictures(mContext)) {
                pictureData.setExclusivePictureVisible(findFirstConfiguredVisiblePicture(pictureData, list));
            }
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
            if (currentPictureId == null
                    || !list.containsKey(currentPictureId)
                    || (!allowsMultiplePictures(mContext)
                    && firstVisiblePictureId != null
                    && !firstVisiblePictureId.equals(currentPictureId))) {
                mainApplication.setCurrentPictureId(firstVisiblePictureId != null
                        ? firstVisiblePictureId
                        : list.keySet().iterator().next());
            }
            mainApplication.setWinVisible(firstVisiblePictureId != null);
        } else {
            ((MainApplication) mContext.getApplicationContext()).setWinVisible(false);
        }
    }

    private static void StartWin(Context mContext, WindowManager windowManager, PictureData pictureData, String id) {
        pictureData.setDataControl(id);
        Bitmap bitmap = ImageMethods.getShowBitmap(mContext, id);
        float default_zoom = pictureData.getFloat(Config.DATA_PICTURE_DEFAULT_ZOOM, ImageMethods.getDefaultZoom(mContext, bitmap, false));
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
        float zoom_x = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_X, zoom);
        float zoom_y = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_Y, zoom);
        float picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        float picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
        int position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean global_rotatable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
        boolean over_layout = resolvePictureOverLayout(mContext);
        FloatImageView floatImageView = ImageMethods.createPictureView(mContext, bitmap, global_touchable, over_layout, zoom_x, zoom_y, picture_degree);
        floatImageView.setScalable(global_touchable);
        floatImageView.setRotatable(global_rotatable);
        floatImageView.setAlpha(picture_alpha);
        floatImageView.setWindowPosition(position_x, position_y);
        ImageMethods.saveFloatImageViewById(mContext, id, floatImageView);
        if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
            WindowsMethods.createWindow(windowManager, floatImageView, global_touchable || global_rotatable, over_layout, position_x, position_y);
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

    public static void prepareForDataReload(Context context) {
        hideAllWindowsRuntime(context);
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        mainApplication.getRegister().clear();
        mainApplication.setCurrentPictureId(null);
        mainApplication.setPictureSequenceMode(false);
        mainApplication.setWinVisible(false);
        mainApplication.setAppInit(false);
    }

    public static void updateAllWindowsGestureState(Context mContext, boolean allowGlobalGestures) {
        HashMap<String, View> hashMap = ((MainApplication) mContext.getApplicationContext()).getRegister();
        WindowManager windowManager = getWindowManager(mContext);
        PictureData pictureData = new PictureData();
        boolean global_touchable = allowGlobalGestures
                && PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean global_rotatable = allowGlobalGestures
                && PreferenceManager.getDefaultSharedPreferences(mContext)
                .getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
        if (hashMap.size() > 0) {
            for (HashMap.Entry<?, ?> entry : hashMap.entrySet()) {
                String id = entry.getKey().toString();
                pictureData.setDataControl(id);
                if (pictureData.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                    FloatImageView floatImageView = (FloatImageView) entry.getValue();
                    boolean over_layout = resolvePictureOverLayout(mContext);
                    boolean isMoveable = global_touchable;
                    boolean isScalable = global_touchable;
                    boolean isTouchable = isMoveable || isScalable || global_rotatable;

                    floatImageView.setMoveable(isMoveable);
                    floatImageView.setScalable(isScalable);
                    floatImageView.setRotatable(global_rotatable);
                    floatImageView.setOverLayout(over_layout);
                    WindowManager.LayoutParams layoutParams = WindowsMethods.getDefaultLayout(
                            mContext,
                            (int) floatImageView.getMovedPositionX(),
                            (int) floatImageView.getMovedPositionY(),
                            isTouchable,
                            over_layout);
                    WindowsMethods.preserveCurrentWindowSize(floatImageView, layoutParams);
                    windowManager.updateViewLayout(floatImageView, layoutParams);
                }
            }
        }
    }

    public static void updateNotificationCount(Context context) {
        context.sendBroadcast(new Intent(Config.INTENT_ACTION_NOTIFICATION_UPDATE_COUNT)
                .setPackage(context.getPackageName()));
    }

    public static void selectCurrentPicture(Context context, String pictureId) {
        if (pictureId == null || pictureId.isEmpty()) {
            return;
        }
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        if (!pictureId.equals(mainApplication.getCurrentPictureId())) {
            mainApplication.setCurrentPictureId(pictureId);
            mainApplication.setPictureSequenceMode(false);
            updateNotificationCount(context);
        }
    }

    public static boolean allowsMultiplePictures(Context context) {
        return PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES, true);
    }

    public static boolean isCurrentPictureVisible(Context context) {
        String currentPictureId = ((MainApplication) context.getApplicationContext())
                .getCurrentPictureId();
        if (currentPictureId == null) {
            return false;
        }
        PictureData pictureData = new PictureData();
        if (!pictureData.getListArray().containsKey(currentPictureId)) {
            return false;
        }
        pictureData.setDataControl(currentPictureId);
        return pictureData.getBoolean(
                Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
    }

    public static boolean setCurrentPictureVisible(Context context, boolean visible) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        if (pictures.isEmpty()) {
            return false;
        }

        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String currentPictureId = mainApplication.getCurrentPictureId();
        if (currentPictureId == null || !pictures.containsKey(currentPictureId)) {
            currentPictureId = pictures.keySet().iterator().next();
            mainApplication.setCurrentPictureId(currentPictureId);
        }
        setWindowVisible(context, pictureData, currentPictureId, visible);
        return true;
    }

    /** Moves the selected floating picture without committing the new position. */
    public static boolean moveCurrentPicture(Context context, int deltaX, int deltaY) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String currentPictureId = mainApplication.getCurrentPictureId();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, currentPictureId);
        if (currentPictureId == null
                || floatImageView == null
                || !floatImageView.isAttachedToWindow()) {
            return false;
        }

        ViewGroup.LayoutParams currentParams = floatImageView.getLayoutParams();
        if (!(currentParams instanceof WindowManager.LayoutParams)) {
            return false;
        }
        WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) currentParams;
        int positionX = Math.round(floatImageView.getMovedPositionX()) + deltaX;
        int positionY = Math.round(floatImageView.getMovedPositionY()) + deltaY;
        windowParams.x = positionX;
        windowParams.y = positionY;
        floatImageView.setWindowPosition(positionX, positionY);
        getWindowManager(context).updateViewLayout(floatImageView, windowParams);
        return true;
    }

    public static boolean hasCurrentPictureAdjustments(Context context) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String currentPictureId = mainApplication.getCurrentPictureId();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, currentPictureId);
        if (currentPictureId == null || floatImageView == null) {
            return false;
        }
        PictureData pictureData = new PictureData();
        if (!pictureData.getListArray().containsKey(currentPictureId)) {
            return false;
        }
        pictureData.setDataControl(currentPictureId);
        int savedPositionX = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int savedPositionY = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        return floatImageView.hasUncommittedAdjustments(savedPositionX, savedPositionY);
    }

    public static float getCurrentPictureDegree(Context context) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, mainApplication.getCurrentPictureId());
        return floatImageView == null ? 0f : floatImageView.getCurrentDegree();
    }

    public static boolean setCurrentPictureDegree(Context context, float degree) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, mainApplication.getCurrentPictureId());
        return floatImageView != null
                && floatImageView.isAttachedToWindow()
                && floatImageView.setCurrentDegreeFromControl(degree);
    }

    @SuppressWarnings("NotifyDataSetChanged")
    public static boolean saveCurrentPictureGestureAdjustments(Context context) {
        if (!PreferenceManager.getDefaultSharedPreferences(context).getBoolean(
                Config.PREFERENCE_SAVE_GESTURE_ADJUSTMENTS, false)) {
            return false;
        }
        boolean lockAfterSave = PreferenceManager.getDefaultSharedPreferences(context)
                .getBoolean(Config.PREFERENCE_LOCK_GESTURES_AFTER_SAVE, false);
        return saveCurrentPictureGestureAdjustments(context, lockAfterSave);
    }

    /** Saves the selected picture's gesture state and immediately locks gestures. */
    public static boolean saveAndLockCurrentPictureGestureAdjustments(Context context) {
        return saveCurrentPictureGestureAdjustments(context, true);
    }

    /** Saves gesture changes from the floating control without changing lock state. */
    public static boolean saveCurrentPictureGestureAdjustmentsFromControl(Context context) {
        return saveCurrentPictureGestureAdjustments(context, false);
    }

    @SuppressWarnings("NotifyDataSetChanged")
    private static boolean saveCurrentPictureGestureAdjustments(
            Context context, boolean lockAfterSave) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String currentPictureId = mainApplication.getCurrentPictureId();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, currentPictureId);
        if (currentPictureId == null || floatImageView == null) {
            return false;
        }

        PictureData pictureData = new PictureData();
        if (!pictureData.getListArray().containsKey(currentPictureId)) {
            return false;
        }
        pictureData.setDataControl(currentPictureId);
        float zoomX = floatImageView.getCurrentZoomX();
        float zoomY = floatImageView.getCurrentZoomY();
        pictureData.put(Config.DATA_PICTURE_POSITION_X,
                Math.round(floatImageView.getMovedPositionX()));
        pictureData.put(Config.DATA_PICTURE_POSITION_Y,
                Math.round(floatImageView.getMovedPositionY()));
        pictureData.put(Config.DATA_PICTURE_ZOOM, zoomX);
        pictureData.put(Config.DATA_PICTURE_ZOOM_X, zoomX);
        pictureData.put(Config.DATA_PICTURE_ZOOM_Y, zoomY);
        pictureData.put(Config.DATA_PICTURE_DEGREE,
                floatImageView.getCurrentDegree());
        pictureData.commit(null);
        floatImageView.commitGestureAdjustments();

        if (lockAfterSave) {
            PreferenceManager.getDefaultSharedPreferences(context).edit()
                    .putBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false)
                    .apply();
            setRotationGestureEnabled(context, false);
        }

        ManageListAdapter manageListAdapter = mainApplication.getManageListAdapter();
        if (manageListAdapter != null) {
            manageListAdapter.notifyDataSetChanged();
        }
        return true;
    }

    /** Restores the selected picture to its last persisted gesture state. */
    public static boolean discardCurrentPictureGestureAdjustments(Context context) {
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String currentPictureId = mainApplication.getCurrentPictureId();
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(
                context, currentPictureId);
        if (currentPictureId == null || floatImageView == null) {
            return false;
        }

        resetRuntimeGestureAdjustments(context, currentPictureId, floatImageView);
        if (floatImageView.isAttachedToWindow()) {
            android.content.SharedPreferences preferences = PreferenceManager
                    .getDefaultSharedPreferences(context);
            boolean moveable = preferences.getBoolean(
                    Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
            boolean rotatable = preferences.getBoolean(
                    Config.PREFERENCE_PINCH_ROTATION, false);
            boolean overLayout = resolvePictureOverLayout(context);
            WindowManager.LayoutParams restoredParams = WindowsMethods.getDefaultLayout(
                    context,
                    Math.round(floatImageView.getMovedPositionX()),
                    Math.round(floatImageView.getMovedPositionY()),
                    moveable || rotatable,
                    overLayout);
            WindowsMethods.preserveCurrentWindowSize(floatImageView, restoredParams);
            floatImageView.setMoveable(moveable);
            floatImageView.setScalable(moveable);
            floatImageView.setRotatable(rotatable);
            floatImageView.setOverLayout(overLayout);
            getWindowManager(context).updateViewLayout(floatImageView, restoredParams);
        }
        ManageListAdapter manageListAdapter = mainApplication.getManageListAdapter();
        if (manageListAdapter != null) {
            manageListAdapter.notifyDataSetChanged();
        }
        return true;
    }

    public static boolean resolvePictureOverLayout(Context context) {
        android.content.SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        return preferences.getBoolean(
                Config.PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN, false);
    }

    /** Rotation starts with overflow enabled, which can then be changed independently. */
    public static void setRotationGestureEnabled(Context context, boolean enabled) {
        android.content.SharedPreferences preferences = PreferenceManager
                .getDefaultSharedPreferences(context);
        android.content.SharedPreferences.Editor editor = preferences.edit()
                .putBoolean(Config.PREFERENCE_PINCH_ROTATION, enabled)
                .putBoolean(Config.PREFERENCE_ROTATION_OVERFLOW_DECOUPLED, true);
        if (enabled) {
            editor.putBoolean(Config.PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN, true);
        }
        editor.apply();
        updateAllWindowsGestureState(context, true);
    }

    public static void enforceSingleVisiblePicture(Context context) {
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        MainApplication mainApplication = (MainApplication) context.getApplicationContext();
        String visiblePictureId = mainApplication.getCurrentPictureId();

        if (!isWindowAttached(context, visiblePictureId)) {
            visiblePictureId = null;
            for (String pictureId : pictures.keySet()) {
                if (isWindowAttached(context, pictureId)) {
                    visiblePictureId = pictureId;
                    break;
                }
            }
        }

        hideAllWindowsRuntime(context);
        if (visiblePictureId != null) {
            showWindowById(context, visiblePictureId);
        }
        pictureData.setExclusivePictureVisible(visiblePictureId);
        mainApplication.setCurrentPictureId(visiblePictureId);
        mainApplication.setPictureSequenceMode(false);
        mainApplication.setWinVisible(visiblePictureId != null);

        ManageListAdapter manageListAdapter = mainApplication.getManageListAdapter();
        if (manageListAdapter != null) {
            manageListAdapter.notifyDataSetChanged();
        }
        updateNotificationCount(context);
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
        if (!allowsMultiplePictures(context)) {
            hideAllWindowsRuntime(context);
        }
        if (showWindowById(context, pictureId)) {
            mainApplication.setWinVisible(true);
        }
    }

    public static void finishWindowEditing(Context context, String pictureId, boolean originallyVisible) {
        if (originallyVisible) {
            showWindowById(context, pictureId);
        } else {
            hideWindowById(context, pictureId);
            if (!allowsMultiplePictures(context)) {
                PictureData pictureData = new PictureData();
                String configuredVisiblePictureId = findFirstConfiguredVisiblePicture(
                        pictureData, pictureData.getListArray());
                if (configuredVisiblePictureId != null) {
                    showWindowById(context, configuredVisiblePictureId);
                    ((MainApplication) context.getApplicationContext())
                            .setCurrentPictureId(configuredVisiblePictureId);
                }
            }
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
        for (Map.Entry<String, View> entry : registeredViews.entrySet()) {
            if (entry.getValue() instanceof FloatImageView) {
                FloatImageView floatImageView = (FloatImageView) entry.getValue();
                removeWindowIfAttached(windowManager, floatImageView);
                resetRuntimeGestureAdjustments(context, entry.getKey(), floatImageView);
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
        if (visible && !allowsMultiplePictures(context)) {
            hideAllWindowsRuntime(context);
            showWindowById(context, id);
            pictureData.setExclusivePictureVisible(id);
        } else {
            if (visible) {
                showWindowById(context, id);
            } else {
                hideWindowById(context, id);
            }
            pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, visible);
            pictureData.commit(null);
        }

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
            resetRuntimeGestureAdjustments(mContext, id, floatImageView);
        }
    }

    private static boolean showWindowById(Context mContext, String id) {
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(mContext, id);
        if (floatImageView == null) {
            return false;
        }
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(id);
        boolean alreadyAttached = floatImageView.isAttachedToWindow();
        int positionX = alreadyAttached
                ? (int) floatImageView.getMovedPositionX()
                : pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int positionY = alreadyAttached
                ? (int) floatImageView.getMovedPositionY()
                : pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean global_rotatable = PreferenceManager.getDefaultSharedPreferences(mContext).getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
        boolean over_layout = resolvePictureOverLayout(mContext);
        WindowManager.LayoutParams layoutParams = WindowsMethods.getDefaultLayout(mContext, positionX, positionY, global_touchable || global_rotatable, over_layout);
        WindowsMethods.preserveCurrentWindowSize(floatImageView, layoutParams);
        floatImageView.setMoveable(global_touchable);
        floatImageView.setScalable(global_touchable);
        floatImageView.setRotatable(global_rotatable);
        floatImageView.setOverLayout(over_layout);
        floatImageView.setWindowPosition(positionX, positionY);
        if (alreadyAttached) {
            getWindowManager(mContext).updateViewLayout(floatImageView, layoutParams);
        } else {
            getWindowManager(mContext).addView(floatImageView, layoutParams);
        }
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

    private static void resetRuntimeGestureAdjustments(
            Context context, String pictureId, FloatImageView floatImageView) {
        PictureData pictureData = new PictureData();
        pictureData.setDataControl(pictureId);
        int positionX = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int positionY = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        floatImageView.resetGestureAdjustments();
        floatImageView.setWindowPosition(positionX, positionY);
        ViewGroup.LayoutParams currentParams = floatImageView.getLayoutParams();
        if (currentParams instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) currentParams;
            windowParams.x = positionX;
            windowParams.y = positionY;
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

    private static boolean isWindowAttached(Context context, String pictureId) {
        if (pictureId == null) {
            return false;
        }
        FloatImageView floatImageView = ImageMethods.getFloatImageViewById(context, pictureId);
        return floatImageView != null && floatImageView.isAttachedToWindow();
    }

    private static String findFirstConfiguredVisiblePicture(
            PictureData pictureData, LinkedHashMap<String, String> pictures) {
        for (String pictureId : pictures.keySet()) {
            pictureData.setDataControl(pictureId);
            if (pictureData.getBoolean(
                    Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED)) {
                return pictureId;
            }
        }
        return null;
    }

}
