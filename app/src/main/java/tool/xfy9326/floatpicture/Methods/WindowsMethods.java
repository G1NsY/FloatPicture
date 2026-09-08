package tool.xfy9326.floatpicture.Methods;


import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.PixelFormat;
import android.graphics.Rect;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.Gravity;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.View.FloatImageView;


public class WindowsMethods {
    public static WindowManager getWindowManager(Context mContext) {
        return (WindowManager) mContext.getApplicationContext().getSystemService(Context.WINDOW_SERVICE);
    }

    @SuppressWarnings("SameParameterValue")
    public static void createWindow(WindowManager windowManager, View pictureView, boolean touchable, boolean overLayout, int layoutPositionX, int layoutPositionY) {
        WindowManager.LayoutParams layoutParams = getDefaultLayout(pictureView.getContext(), layoutPositionX, layoutPositionY, touchable, overLayout);
        applyRenderedImageSize(pictureView, layoutParams);
        if (!overLayout) {
            constrainPositionToScreen(pictureView.getContext(), pictureView, layoutParams);
        }
        syncWindowPosition(pictureView, layoutParams);
        windowManager.addView(pictureView, layoutParams);
    }

    public static WindowManager.LayoutParams getDefaultLayout(Context context, int layoutPositionX, int layoutPositionY, boolean touchable, boolean overLayout) {
        WindowManager.LayoutParams layoutParams = new WindowManager.LayoutParams();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            layoutParams.type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            layoutParams.type = WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        }
        layoutParams.flags = WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                | WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS;
        if (!touchable) {
            layoutParams.flags = layoutParams.flags | WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE;
        }
        layoutParams.x = layoutPositionX;
        layoutParams.y = layoutPositionY;
        layoutParams.gravity = Gravity.START | Gravity.TOP;
        // 锁定悬浮窗创建时的显示方向，避免相机等前台应用带动悬浮图片旋转。
        layoutParams.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LOCKED;
        layoutParams.width = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        layoutParams.format = PixelFormat.TRANSLUCENT;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            layoutParams.alpha = ((MainApplication) context.getApplicationContext()).getSafeWindowsAlpha();
        }
        return layoutParams;
    }

    public static void updateWindow(WindowManager windowManager, FloatImageView pictureView, boolean touchable, boolean overLayout, int layoutPositionX, int layoutPositionY) {
        WindowManager.LayoutParams layoutParams = getDefaultLayout(pictureView.getContext(), layoutPositionX, layoutPositionY, touchable, overLayout);
        applyRenderedImageSize(pictureView, layoutParams);
        if (!overLayout) {
            constrainPositionToScreen(pictureView.getContext(), pictureView, layoutParams);
        }
        syncWindowPosition(pictureView, layoutParams);
        windowManager.updateViewLayout(pictureView, layoutParams);
    }

    private static void applyRenderedImageSize(
            View pictureView, WindowManager.LayoutParams layoutParams) {
        if (pictureView instanceof FloatImageView) {
            FloatImageView floatImageView = (FloatImageView) pictureView;
            if (floatImageView.getRenderedImageWidth() > 0
                    && floatImageView.getRenderedImageHeight() > 0) {
                layoutParams.width = floatImageView.getRenderedImageWidth();
                layoutParams.height = floatImageView.getRenderedImageHeight();
            }
        }
    }

    public static void preserveCurrentWindowSize(FloatImageView pictureView, WindowManager.LayoutParams targetParams) {
        ViewGroup.LayoutParams currentParams = pictureView.getLayoutParams();
        if (currentParams != null && currentParams.width > 0 && currentParams.height > 0) {
            targetParams.width = currentParams.width;
            targetParams.height = currentParams.height;
        } else {
            // A picture restored as hidden has no window yet. Use its rendered
            // size before constraining its position or attaching it; WRAP_CONTENT
            // can measure against the screen and crop a large/rotated image.
            applyRenderedImageSize(pictureView, targetParams);
        }
    }

    /**
     * Keeps a window within the display while preserving the same full-screen
     * coordinate space used when overflow is enabled. Oversized windows can
     * still be panned between their two edges because they cannot fully fit.
     */
    public static void constrainPositionToScreen(
            Context context, View pictureView, WindowManager.LayoutParams layoutParams) {
        int windowWidth = layoutParams.width > 0
                ? layoutParams.width : Math.max(1, pictureView.getWidth());
        int windowHeight = layoutParams.height > 0
                ? layoutParams.height : Math.max(1, pictureView.getHeight());
        int screenWidth;
        int screenHeight;
        WindowManager windowManager = getWindowManager(context);
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            Rect bounds = windowManager.getCurrentWindowMetrics().getBounds();
            screenWidth = bounds.width();
            screenHeight = bounds.height();
        } else {
            DisplayMetrics metrics = new DisplayMetrics();
            windowManager.getDefaultDisplay().getRealMetrics(metrics);
            screenWidth = metrics.widthPixels;
            screenHeight = metrics.heightPixels;
        }
        layoutParams.x = clampWindowCoordinate(
                layoutParams.x, screenWidth, windowWidth);
        layoutParams.y = clampWindowCoordinate(
                layoutParams.y, screenHeight, windowHeight);
    }

    private static int clampWindowCoordinate(int coordinate, int screenSize, int windowSize) {
        int minimum = Math.min(0, screenSize - windowSize);
        int maximum = Math.max(0, screenSize - windowSize);
        return Math.max(minimum, Math.min(coordinate, maximum));
    }

    private static void syncWindowPosition(
            View pictureView, WindowManager.LayoutParams layoutParams) {
        if (pictureView instanceof FloatImageView) {
            ((FloatImageView) pictureView).setWindowPosition(layoutParams.x, layoutParams.y);
        }
    }

    public static void updateWindow(WindowManager windowManager, FloatImageView pictureView, Bitmap bitmap, boolean touchable, boolean overLayout, float zoom_x, float zoom_y, float degree, int layoutPositionX, int layoutPositionY) {
        pictureView.refreshDrawableState();
        pictureView.configureGestureImage(bitmap, zoom_x, zoom_y, degree);
        updateWindow(windowManager, pictureView, touchable, overLayout, layoutPositionX, layoutPositionY);
    }
    
    // Backward compatibility
    public static void updateWindow(WindowManager windowManager, FloatImageView pictureView, Bitmap bitmap, boolean touchable, boolean overLayout, float zoom, float degree, int layoutPositionX, int layoutPositionY) {
        updateWindow(windowManager, pictureView, bitmap, touchable, overLayout, zoom, zoom, degree, layoutPositionX, layoutPositionY);
    }
}
