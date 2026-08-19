package tool.xfy9326.floatpicture.View;

import android.annotation.SuppressLint;
import android.content.Context;
import android.graphics.Bitmap;
import android.util.DisplayMetrics;
import android.view.MotionEvent;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.appcompat.widget.AppCompatImageView;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.Utils.Config;

public class FloatImageView extends AppCompatImageView {
    private String PictureId = "";
    private WindowManager windowManager;
    private boolean moveable = false;
    private boolean scalable = false;
    private boolean rotatable = false;
    private boolean overLayout = false;
    private boolean suppressMoveUntilNextDown = false;
    private boolean multiTouchInProgress = false;
    private float lastFingerSpan;
    private float lastFingerAngle;
    private float gestureStartWindowX;
    private float gestureStartWindowY;
    private int gestureStartWidth;
    private int gestureStartHeight;
    private float gestureStartFocusX;
    private float gestureStartFocusY;
    private float gestureStartRawFocusX;
    private float gestureStartRawFocusY;
    private float multiTouchMoveTolerance;

    private Bitmap gestureSourceBitmap;
    private float savedZoomX = 1f;
    private float savedZoomY = 1f;
    private float savedDegree = 0f;
    private float gestureScale = 1f;
    private float gestureDegreeOffset = 0f;
    private int savedRenderedWidth;
    private int savedRenderedHeight;
    private int currentRenderedWidth;
    private int currentRenderedHeight;

    private float mTouchStartX = 0;
    private float mTouchStartY = 0;
    private float x = 0;
    private float y = 0;
    private float mNowPositionX = Config.DATA_DEFAULT_PICTURE_POSITION_X;
    private float mNowPositionY = Config.DATA_DEFAULT_PICTURE_POSITION_Y;

    public FloatImageView(Context context) {
        super(context);
        init(context);
    }

    public void setWindowPosition(int x, int y) {
        mNowPositionX = x;
        mNowPositionY = y;
    }

    private void init(Context context) {
        windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        multiTouchMoveTolerance = 16f * getResources().getDisplayMetrics().density;
        // The window is sized to the rendered bitmap. Never stretch a rotated
        // bitmap into stale bounds, which can make it look diagonally sheared.
        setScaleType(ScaleType.CENTER);
    }

    public void configureGestureImage(Bitmap sourceBitmap, float zoomX, float zoomY, float degree) {
        gestureSourceBitmap = sourceBitmap;
        savedZoomX = zoomX;
        savedZoomY = zoomY;
        savedDegree = normalizeDegree(degree);
        gestureScale = 1f;
        gestureDegreeOffset = 0f;
        Bitmap renderedBitmap = ImageMethods.resizeBitmap(
                gestureSourceBitmap, savedZoomX, savedZoomY, savedDegree);
        setImageBitmap(renderedBitmap);
        if (renderedBitmap != null) {
            savedRenderedWidth = renderedBitmap.getWidth();
            savedRenderedHeight = renderedBitmap.getHeight();
            currentRenderedWidth = savedRenderedWidth;
            currentRenderedHeight = savedRenderedHeight;
        }
    }

    @SuppressWarnings("unused")
    public String getPictureId() {
        return PictureId;
    }

    public void setPictureId(String id) {
        PictureId = id;
    }

    public void setMoveable(boolean moveable) {
        this.moveable = moveable;
    }

    public void setScalable(boolean scalable) {
        this.scalable = scalable;
    }

    public void setRotatable(boolean rotatable) {
        this.rotatable = rotatable;
    }

    public void setOverLayout(boolean overLayout) {
        this.overLayout = overLayout;
    }

    @SuppressLint("ClickableViewAccessibility")
    @Override
    public boolean onTouchEvent(MotionEvent event) {
        int action = event.getActionMasked();
        if (action == MotionEvent.ACTION_POINTER_DOWN && event.getPointerCount() >= 2) {
            multiTouchInProgress = scalable || rotatable;
            suppressMoveUntilNextDown = true;
            lastFingerSpan = getFingerSpan(event);
            lastFingerAngle = getFingerAngle(event);
            captureTwoFingerGestureAnchor(event);
            return moveable || scalable || rotatable;
        }
        if (action == MotionEvent.ACTION_MOVE
                && multiTouchInProgress
                && event.getPointerCount() >= 2) {
            updateTwoFingerGesture(event);
            return true;
        }
        if (action == MotionEvent.ACTION_POINTER_UP) {
            multiTouchInProgress = false;
            suppressMoveUntilNextDown = true;
            return moveable || scalable || rotatable;
        }
        if (action == MotionEvent.ACTION_DOWN) {
            ManageMethods.selectCurrentPicture(getContext(), PictureId);
            multiTouchInProgress = false;
            suppressMoveUntilNextDown = false;
        }
        if (moveable) {
            if (suppressMoveUntilNextDown) {
                if (action == MotionEvent.ACTION_UP || action == MotionEvent.ACTION_CANCEL) {
                    suppressMoveUntilNextDown = false;
                }
                return true;
            }
            x = event.getRawX();
            y = event.getRawY();
            switch (action) {
                case MotionEvent.ACTION_DOWN -> {
                    mTouchStartX = event.getX();
                    mTouchStartY = event.getY();
                }
                case MotionEvent.ACTION_MOVE -> {
                    getNowPosition();
                    updatePosition();
                }
                case MotionEvent.ACTION_UP -> {
                    getNowPosition();
                    updatePosition();
                    mTouchStartX = mTouchStartY = 0;
                }
            }
            return true;
        }
        if (scalable || rotatable) {
            return true;
        }
        return super.onTouchEvent(event);
    }

    private void updateTwoFingerGesture(MotionEvent event) {
        float span = getFingerSpan(event);
        float angle = getFingerAngle(event);
        boolean changed = false;

        if (scalable && lastFingerSpan > 0f && span > 0f) {
            float nextScale = clampGestureScale(gestureScale * span / lastFingerSpan);
            changed = Math.abs(nextScale - gestureScale) > 0.0001f;
            gestureScale = nextScale;
        }
        if (rotatable) {
            float angleDelta = normalizeAngleDelta(angle - lastFingerAngle);
            if (Math.abs(angleDelta) > 0.01f) {
                gestureDegreeOffset += angleDelta;
                changed = true;
            }
        }

        lastFingerSpan = span;
        lastFingerAngle = angle;
        if (changed) {
            float focusX = (event.getX(0) + event.getX(1)) / 2f;
            float focusY = (event.getY(0) + event.getY(1)) / 2f;
            float rawFocusX = event.getRawX() - event.getX() + focusX;
            float rawFocusY = event.getRawY() - event.getY() + focusY;
            applyGestureTransform(rawFocusX, rawFocusY);
        }
    }

    private void captureTwoFingerGestureAnchor(MotionEvent event) {
        ViewGroup.LayoutParams currentParams = getLayoutParams();
        if (currentParams instanceof WindowManager.LayoutParams) {
            WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) currentParams;
            gestureStartWindowX = layoutParams.x;
            gestureStartWindowY = layoutParams.y;
            gestureStartWidth = layoutParams.width > 0
                    ? layoutParams.width : Math.max(1, getWidth());
            gestureStartHeight = layoutParams.height > 0
                    ? layoutParams.height : Math.max(1, getHeight());
        } else {
            gestureStartWindowX = mNowPositionX;
            gestureStartWindowY = mNowPositionY;
            gestureStartWidth = Math.max(1, getWidth());
            gestureStartHeight = Math.max(1, getHeight());
        }
        gestureStartFocusX = (event.getX(0) + event.getX(1)) / 2f;
        gestureStartFocusY = (event.getY(0) + event.getY(1)) / 2f;
        gestureStartRawFocusX = event.getRawX() - event.getX() + gestureStartFocusX;
        gestureStartRawFocusY = event.getRawY() - event.getY() + gestureStartFocusY;
    }

    private void applyGestureTransform(float rawFocusX, float rawFocusY) {
        if (gestureSourceBitmap == null || gestureSourceBitmap.isRecycled()) {
            return;
        }
        Bitmap renderedBitmap = ImageMethods.resizeBitmap(
                gestureSourceBitmap,
                getCurrentZoomX(),
                getCurrentZoomY(),
                getCurrentDegree());
        if (renderedBitmap == null) {
            return;
        }

        ViewGroup.LayoutParams currentParams = getLayoutParams();
        setImageBitmap(renderedBitmap);
        if (!(currentParams instanceof WindowManager.LayoutParams)) {
            return;
        }

        WindowManager.LayoutParams layoutParams = (WindowManager.LayoutParams) currentParams;
        int newWidth = renderedBitmap.getWidth();
        int newHeight = renderedBitmap.getHeight();
        currentRenderedWidth = newWidth;
        currentRenderedHeight = newHeight;
        float focusMoveX = applyMoveTolerance(
                rawFocusX - gestureStartRawFocusX, multiTouchMoveTolerance);
        float focusMoveY = applyMoveTolerance(
                rawFocusY - gestureStartRawFocusY, multiTouchMoveTolerance);
        layoutParams.x = Math.round(
                gestureStartWindowX + focusMoveX
                        + gestureStartFocusX * (1f - (float) newWidth / gestureStartWidth));
        layoutParams.y = Math.round(
                gestureStartWindowY + focusMoveY
                        + gestureStartFocusY * (1f - (float) newHeight / gestureStartHeight));
        layoutParams.width = newWidth;
        layoutParams.height = newHeight;
        mNowPositionX = layoutParams.x;
        mNowPositionY = layoutParams.y;
        windowManager.updateViewLayout(this, layoutParams);
    }

    private static float applyMoveTolerance(float movement, float tolerance) {
        float absoluteMovement = Math.abs(movement);
        if (absoluteMovement <= tolerance) {
            return 0f;
        }
        return Math.copySign(absoluteMovement - tolerance, movement);
    }

    private float clampGestureScale(float scale) {
        DisplayMetrics metrics = getResources().getDisplayMetrics();
        int baseWidth = Math.max(1, savedRenderedWidth);
        int baseHeight = Math.max(1, savedRenderedHeight);
        int minimumSize = Math.round(48 * metrics.density);
        int maximumWidth = Math.max(baseWidth, metrics.widthPixels * 4);
        int maximumHeight = Math.max(baseHeight, metrics.heightPixels * 4);
        float minimumScale = Math.max(
                (float) minimumSize / baseWidth,
                (float) minimumSize / baseHeight);
        float maximumScale = Math.max(minimumScale, Math.min(
                (float) maximumWidth / baseWidth,
                (float) maximumHeight / baseHeight));
        return Math.max(minimumScale, Math.min(scale, maximumScale));
    }

    private static float getFingerSpan(MotionEvent event) {
        return (float) Math.hypot(
                event.getX(1) - event.getX(0),
                event.getY(1) - event.getY(0));
    }

    private static float getFingerAngle(MotionEvent event) {
        return (float) Math.toDegrees(Math.atan2(
                event.getY(1) - event.getY(0),
                event.getX(1) - event.getX(0)));
    }

    private static float normalizeAngleDelta(float degree) {
        while (degree > 180f) degree -= 360f;
        while (degree < -180f) degree += 360f;
        return degree;
    }

    private static float normalizeDegree(float degree) {
        degree %= 360f;
        if (degree < 0f) degree += 360f;
        return degree;
    }

    public float getCurrentZoomX() {
        return savedZoomX * gestureScale;
    }

    public float getCurrentZoomY() {
        return savedZoomY * gestureScale;
    }

    public float getCurrentDegree() {
        return normalizeDegree(savedDegree + gestureDegreeOffset);
    }

    public boolean setCurrentDegreeFromControl(float degree) {
        if (gestureSourceBitmap == null || gestureSourceBitmap.isRecycled()) {
            return false;
        }
        float normalizedDegree = normalizeDegree(degree);
        Bitmap renderedBitmap = ImageMethods.resizeBitmap(
                gestureSourceBitmap,
                getCurrentZoomX(),
                getCurrentZoomY(),
                normalizedDegree);
        ViewGroup.LayoutParams currentParams = getLayoutParams();
        if (renderedBitmap == null
                || !(currentParams instanceof WindowManager.LayoutParams)) {
            return false;
        }

        WindowManager.LayoutParams windowParams = (WindowManager.LayoutParams) currentParams;
        int oldWidth = windowParams.width > 0 ? windowParams.width : Math.max(1, getWidth());
        int oldHeight = windowParams.height > 0 ? windowParams.height : Math.max(1, getHeight());
        float centerX = windowParams.x + oldWidth / 2f;
        float centerY = windowParams.y + oldHeight / 2f;
        int newWidth = renderedBitmap.getWidth();
        int newHeight = renderedBitmap.getHeight();

        gestureDegreeOffset = normalizeAngleDelta(normalizedDegree - savedDegree);
        currentRenderedWidth = newWidth;
        currentRenderedHeight = newHeight;
        windowParams.width = newWidth;
        windowParams.height = newHeight;
        windowParams.x = Math.round(centerX - newWidth / 2f);
        windowParams.y = Math.round(centerY - newHeight / 2f);
        mNowPositionX = windowParams.x;
        mNowPositionY = windowParams.y;
        setImageBitmap(renderedBitmap);
        windowManager.updateViewLayout(this, windowParams);
        return true;
    }

    public boolean hasUncommittedAdjustments(int savedPositionX, int savedPositionY) {
        return Math.round(mNowPositionX) != savedPositionX
                || Math.round(mNowPositionY) != savedPositionY
                || Math.abs(getCurrentZoomX() - savedZoomX) > 0.0001f
                || Math.abs(getCurrentZoomY() - savedZoomY) > 0.0001f
                || Math.abs(normalizeAngleDelta(getCurrentDegree() - savedDegree)) > 0.01f;
    }

    public void commitGestureAdjustments() {
        savedZoomX = getCurrentZoomX();
        savedZoomY = getCurrentZoomY();
        savedDegree = getCurrentDegree();
        gestureScale = 1f;
        gestureDegreeOffset = 0f;
        ViewGroup.LayoutParams layoutParams = getLayoutParams();
        savedRenderedWidth = layoutParams != null && layoutParams.width > 0
                ? layoutParams.width : Math.max(1, getWidth());
        savedRenderedHeight = layoutParams != null && layoutParams.height > 0
                ? layoutParams.height : Math.max(1, getHeight());
        currentRenderedWidth = savedRenderedWidth;
        currentRenderedHeight = savedRenderedHeight;
    }

    public void resetGestureAdjustments() {
        multiTouchInProgress = false;
        suppressMoveUntilNextDown = false;
        gestureScale = 1f;
        gestureDegreeOffset = 0f;
        if (gestureSourceBitmap != null && !gestureSourceBitmap.isRecycled()) {
            Bitmap renderedBitmap = ImageMethods.resizeBitmap(
                    gestureSourceBitmap, savedZoomX, savedZoomY, savedDegree);
            setImageBitmap(renderedBitmap);
            ViewGroup.LayoutParams layoutParams = getLayoutParams();
            if (layoutParams != null && renderedBitmap != null) {
                layoutParams.width = renderedBitmap.getWidth();
                layoutParams.height = renderedBitmap.getHeight();
            }
            if (renderedBitmap != null) {
                currentRenderedWidth = renderedBitmap.getWidth();
                currentRenderedHeight = renderedBitmap.getHeight();
            }
        }
    }

    public int getRenderedImageWidth() {
        return currentRenderedWidth;
    }

    public int getRenderedImageHeight() {
        return currentRenderedHeight;
    }

    public float getMovedPositionX() {
        return mNowPositionX;
    }

    public float getMovedPositionY() {
        return mNowPositionY;
    }

    private void getNowPosition() {
        mNowPositionX = x - mTouchStartX;
        mNowPositionY = y - mTouchStartY;
    }

    private void updatePosition() {
        WindowManager.LayoutParams layoutParams = WindowsMethods.getDefaultLayout(
                getContext(), (int) mNowPositionX, (int) mNowPositionY,
                moveable || scalable || rotatable, overLayout);
        WindowsMethods.preserveCurrentWindowSize(this, layoutParams);
        windowManager.updateViewLayout(this, layoutParams);
    }

}
