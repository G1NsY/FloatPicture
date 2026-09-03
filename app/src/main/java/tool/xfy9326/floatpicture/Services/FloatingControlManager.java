package tool.xfy9326.floatpicture.Services;

import android.annotation.SuppressLint;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.res.ColorStateList;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Color;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.LayerDrawable;
import android.os.Build;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewConfiguration;
import android.view.ViewTreeObserver;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.FrameLayout;
import android.widget.ImageView;
import android.widget.ImageButton;
import android.widget.LinearLayout;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.preference.PreferenceManager;

import java.util.ArrayList;
import java.util.LinkedHashMap;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.CircularDirectionPadView;
import tool.xfy9326.floatpicture.View.RotationDialView;

/** Owns the small, draggable overlay used to control the selected picture. */
final class FloatingControlManager {
    private static final int HIGHLIGHT_BLUE = 0xFF40C4FF;
    private static final int COLLAPSED_SIZE_DP = 44;
    private static final int PRECISION_CONTROL_NONE = 0;
    private static final int PRECISION_CONTROL_DIRECTION = 1;
    private static final int PRECISION_CONTROL_ROTATION = 2;
    private static final int PRECISION_CONTROL_TRANSPARENCY = 3;
    private static final int EXPANDED_HEIGHT_DP = 248;
    private static final int EXPANDED_PRECISION_HEIGHT_DP = 424;

    private final Context context;
    private final WindowManager windowManager;
    private final SharedPreferences preferences;
    private final int touchSlop;

    private LinearLayout root;
    private WindowManager.LayoutParams layoutParams;
    private TextView statusView;
    private ImageButton visibilityButton;
    private ImageButton gestureButton;
    private ImageButton rotationButton;
    private ImageButton overflowButton;
    private ImageButton directionPadButton;
    private ImageButton transparencyButton;
    private TextView transparencyLabel;
    private SeekBar transparencySeekBar;
    private RotationDialView rotationDialView;
    private Bitmap collapsedIconBitmap;
    private boolean expanded;
    private int precisionControlMode = PRECISION_CONTROL_NONE;
    private boolean dockOnRight;
    private int controllerX;
    private int controllerY;

    FloatingControlManager(@NonNull Context context) {
        this.context = context.getApplicationContext();
        windowManager = (WindowManager) this.context.getSystemService(Context.WINDOW_SERVICE);
        preferences = PreferenceManager.getDefaultSharedPreferences(this.context);
        touchSlop = ViewConfiguration.get(this.context).getScaledTouchSlop();
        controllerX = preferences.getInt(Config.PREFERENCE_FLOATING_CONTROL_X, dp(12));
        controllerY = preferences.getInt(Config.PREFERENCE_FLOATING_CONTROL_Y, dp(160));
        updateDockSideFromPosition(dp(COLLAPSED_SIZE_DP));
        dockOnRight = preferences.getBoolean(
                Config.PREFERENCE_FLOATING_CONTROL_DOCK_RIGHT, dockOnRight);
    }

    void refreshVisibility() {
        boolean shouldShow = preferences.getBoolean(
                Config.PREFERENCE_SHOW_FLOATING_CONTROL, true);
        if (shouldShow && PermissionMethods.canDrawOverlays(context)) {
            ManageMethods.ensureWindowsInitialized(context);
            show();
        } else {
            remove();
        }
    }

    void refreshState() {
        updateVisibilityButton();
        if (rotationDialView != null) {
            rotationDialView.setAngle(ManageMethods.getCurrentPictureDegree(context));
        }
        updateTransparencyControl();
        if (statusView == null) {
            return;
        }
        PictureData pictureData = new PictureData();
        LinkedHashMap<String, String> pictures = pictureData.getListArray();
        String currentId = ((MainApplication) context.getApplicationContext())
                .getCurrentPictureId();
        ArrayList<String> ids = new ArrayList<>(pictures.keySet());
        int index = ids.indexOf(currentId);
        if (index < 0) {
            statusView.setText(R.string.floating_control_no_picture);
            return;
        }
        statusView.setText(context.getString(
                R.string.floating_control_picture_status,
                index + 1,
                ids.size(),
                pictures.get(currentId)));
    }

    void onConfigurationChanged() {
        if (root != null && root.isAttachedToWindow()) {
            clampControllerPosition(
                    expanded ? dp(236) : dp(COLLAPSED_SIZE_DP),
                    expanded
                            ? dp(expandedEstimatedHeightDp())
                            : dp(COLLAPSED_SIZE_DP));
            if (!expanded) {
                snapCollapsedToDockedEdge();
                saveControllerPosition();
            }
            layoutParams.x = controllerX;
            layoutParams.y = controllerY;
            windowManager.updateViewLayout(root, layoutParams);
        }
    }

    void destroy() {
        remove();
        if (collapsedIconBitmap != null && !collapsedIconBitmap.isRecycled()) {
            collapsedIconBitmap.recycle();
        }
        collapsedIconBitmap = null;
    }

    private void show() {
        if (root != null && root.isAttachedToWindow()) {
            refreshState();
            return;
        }
        root = new LinearLayout(context);
        root.setOrientation(LinearLayout.VERTICAL);
        buildCollapsedView();
        layoutParams = createLayoutParams(
                dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        clampControllerPosition(dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        updateDockSideFromPosition(dp(COLLAPSED_SIZE_DP));
        snapCollapsedToDockedEdge();
        layoutParams.x = controllerX;
        layoutParams.y = controllerY;
        try {
            windowManager.addView(root, layoutParams);
        } catch (RuntimeException ignored) {
            root = null;
        }
    }

    private void remove() {
        if (root != null && root.isAttachedToWindow()) {
            try {
                windowManager.removeView(root);
            } catch (IllegalArgumentException ignored) {
                // It may already have been removed during a service lifecycle change.
            }
        }
        root = null;
        statusView = null;
        visibilityButton = null;
        gestureButton = null;
        rotationButton = null;
        overflowButton = null;
        directionPadButton = null;
        transparencyButton = null;
        transparencyLabel = null;
        transparencySeekBar = null;
        rotationDialView = null;
        expanded = false;
        precisionControlMode = PRECISION_CONTROL_NONE;
    }

    private WindowManager.LayoutParams createLayoutParams(int width, int height) {
        WindowManager.LayoutParams params = new WindowManager.LayoutParams();
        params.type = Build.VERSION.SDK_INT >= Build.VERSION_CODES.O
                ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                : WindowManager.LayoutParams.TYPE_SYSTEM_ALERT;
        params.flags = WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                | WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL;
        params.format = PixelFormat.TRANSLUCENT;
        params.gravity = Gravity.START | Gravity.TOP;
        params.width = width;
        params.height = height;
        params.windowAnimations = 0;
        // The controller must not request a display orientation: on some devices an
        // application overlay with LOCKED also forces the foreground app to stay portrait.
        // The picture overlay keeps its own orientation policy in WindowsMethods.
        params.screenOrientation = android.content.pm.ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            params.alpha = ((MainApplication) context.getApplicationContext())
                    .getSafeWindowsAlpha();
        }
        return params;
    }

    private void buildCollapsedView() {
        expanded = false;
        precisionControlMode = PRECISION_CONTROL_NONE;
        transparencyButton = null;
        transparencyLabel = null;
        transparencySeekBar = null;
        rotationDialView = null;
        root.removeAllViews();
        root.setPadding(0, 0, 0, 0);
        root.setBackgroundColor(Color.TRANSPARENT);

        FrameLayout dotContainer = new FrameLayout(context);
        GradientDrawable circularBackground = circleDrawable(Color.rgb(25, 118, 210));
        circularBackground.setAlpha(196);
        dotContainer.setBackground(circularBackground);
        ImageView icon = new ImageView(context);
        Bitmap trimmedIcon = getTrimmedCollapsedIcon();
        if (trimmedIcon != null) {
            icon.setImageBitmap(trimmedIcon);
        } else {
            icon.setImageResource(R.drawable.floating_control_dot);
        }
        icon.setScaleType(ImageView.ScaleType.FIT_CENTER);
        icon.setAlpha(0.72f);
        icon.setPadding(dp(1), dp(1), dp(1), dp(1));
        icon.setContentDescription(context.getString(R.string.floating_control_open));
        dotContainer.addView(icon, new FrameLayout.LayoutParams(
                FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.MATCH_PARENT));
        LinearLayout.LayoutParams dotParams = new LinearLayout.LayoutParams(
                dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        dotParams.gravity = dockOnRight ? Gravity.END : Gravity.START;
        root.addView(dotContainer, dotParams);
        root.setOnTouchListener(createDragListener(true));
    }

    private void buildExpandedView() {
        expanded = true;
        gestureButton = null;
        rotationButton = null;
        overflowButton = null;
        directionPadButton = null;
        transparencyButton = null;
        transparencyLabel = null;
        transparencySeekBar = null;
        rotationDialView = null;
        root.removeAllViews();
        root.setOnTouchListener(null);
        root.setPadding(dp(10), dp(8), dp(10), dp(10));
        root.setBackground(roundedDrawable(Color.rgb(40, 45, 55), dp(16)));

        LinearLayout header = row();
        TextView title = label(context.getString(R.string.floating_control_title), 16);
        title.setTextColor(Color.WHITE);
        header.addView(title, new LinearLayout.LayoutParams(0, dp(42), 1f));
        ImageButton openApplication = imageButton(
                R.drawable.ic_main_application_home, R.string.floating_control_open_application);
        openApplication.clearColorFilter();
        openApplication.setAlpha(1f);
        openApplication.setPadding(dp(8), dp(8), dp(8), dp(8));
        openApplication.setOnClickListener(view -> toggleMainApplication());
        header.addView(openApplication, new LinearLayout.LayoutParams(dp(48), dp(42)));
        ImageButton collapse = imageButton(
                R.drawable.ic_floating_control_close_circle,
                R.string.floating_control_close);
        collapse.setPadding(dp(8), dp(8), dp(8), dp(8));
        collapse.setOnClickListener(view -> collapse());
        LinearLayout.LayoutParams collapseParams =
                new LinearLayout.LayoutParams(dp(48), dp(42));
        collapseParams.setMarginStart(dp(5));
        header.addView(collapse, collapseParams);
        header.setOnTouchListener(createDragListener(false));
        root.addView(header);

        statusView = label("", 13);
        statusView.setTextColor(Color.WHITE);
        statusView.setGravity(Gravity.CENTER);
        statusView.setPadding(dp(4), dp(2), dp(4), dp(6));
        root.addView(statusView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(44)));

        LinearLayout switchRow = row();
        Button previous = button("◀", R.string.imageview_notification_previous);
        previous.setOnClickListener(view -> switchPicture(-1));
        switchRow.addView(previous, weightedButtonParams());
        visibilityButton = imageButton(
                R.drawable.ic_invisible, R.string.floating_control_toggle_picture);
        visibilityButton.clearColorFilter();
        visibilityButton.setAlpha(1f);
        visibilityButton.setOnClickListener(view -> toggleCurrentPicture());
        switchRow.addView(visibilityButton, weightedButtonParams());
        Button next = button("▶", R.string.imageview_notification_next);
        next.setOnClickListener(view -> switchPicture(1));
        switchRow.addView(next, weightedButtonParams());
        root.addView(switchRow);

        LinearLayout gestureToggleRow = row();
        LinearLayout gestureGroup = row();
        gestureGroup.setBackground(roundedDrawable(Color.rgb(64, 72, 86), dp(10)));
        gestureButton = imageButton(
                R.drawable.ic_gesture_hand, R.string.floating_control_gesture_toggle);
        gestureButton.setBackgroundColor(Color.TRANSPARENT);
        gestureButton.setOnClickListener(view -> toggleGestureControl());
        gestureGroup.addView(gestureButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));

        View gestureDivider = new View(context);
        gestureDivider.setBackgroundColor(Color.rgb(100, 110, 124));
        LinearLayout.LayoutParams dividerParams =
                new LinearLayout.LayoutParams(dp(1), dp(26));
        dividerParams.gravity = Gravity.CENTER_VERTICAL;
        gestureGroup.addView(gestureDivider, dividerParams);

        rotationButton = imageButton(
                R.drawable.ic_gesture_rotate, R.string.floating_control_rotation_toggle);
        rotationButton.setBackgroundColor(Color.TRANSPARENT);
        rotationButton.setOnClickListener(view -> toggleRotationControl());
        gestureGroup.addView(rotationButton, new LinearLayout.LayoutParams(
                0, LinearLayout.LayoutParams.MATCH_PARENT, 1f));
        LinearLayout gestureGroupSlot = row();
        LinearLayout.LayoutParams gestureGroupParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        gestureGroupParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        gestureGroupSlot.addView(gestureGroup, gestureGroupParams);
        gestureToggleRow.addView(gestureGroupSlot,
                new LinearLayout.LayoutParams(0, dp(48), 2f));

        overflowButton = imageButton(
                R.drawable.ic_picture_overflow_off, R.string.floating_control_overflow_toggle);
        overflowButton.setOnClickListener(view -> toggleOverflowControl());
        LinearLayout overflowButtonSlot = row();
        LinearLayout.LayoutParams overflowButtonParams =
                new LinearLayout.LayoutParams(
                        LinearLayout.LayoutParams.MATCH_PARENT, dp(42));
        overflowButtonParams.setMargins(dp(3), dp(3), dp(3), dp(3));
        overflowButtonSlot.addView(overflowButton, overflowButtonParams);
        gestureToggleRow.addView(overflowButtonSlot,
                new LinearLayout.LayoutParams(0, dp(48), 1f));
        root.addView(gestureToggleRow);

        LinearLayout gestureActionRow = row();
        directionPadButton = imageButton(
                R.drawable.ic_picture_move_dpad, R.string.floating_control_direction_toggle);
        directionPadButton.setOnClickListener(view -> toggleDirectionPad());
        gestureActionRow.addView(directionPadButton, weightedButtonParams());
        transparencyButton = imageButton(
                R.drawable.ic_picture_opacity,
                R.string.floating_control_transparency_toggle);
        transparencyButton.setOnClickListener(view -> toggleTransparencyControl());
        gestureActionRow.addView(transparencyButton, weightedButtonParams());
        ImageButton save = imageButton(
                R.drawable.ic_gesture_save, R.string.floating_control_save_gesture);
        save.setOnClickListener(view -> {
            if (ManageMethods.hasCurrentPictureAdjustments(context)) {
                showSaveConfirmation();
            }
        });
        gestureActionRow.addView(save, weightedButtonParams());
        ImageButton revert = imageButton(
                R.drawable.ic_gesture_revert, R.string.floating_control_discard_gesture);
        revert.setOnClickListener(view -> {
            if (ManageMethods.hasCurrentPictureAdjustments(context)) {
                showDiscardConfirmation();
            }
        });
        gestureActionRow.addView(revert, weightedButtonParams());
        root.addView(gestureActionRow);

        if (precisionControlMode == PRECISION_CONTROL_DIRECTION) {
            addCircularDirectionPad();
        } else if (precisionControlMode == PRECISION_CONTROL_ROTATION) {
            addRotationDial();
        } else if (precisionControlMode == PRECISION_CONTROL_TRANSPARENCY) {
            addTransparencyControl();
        }
        refreshGestureToggleButtons();
        refreshState();
    }

    private void addTransparencyControl() {
        LinearLayout transparencyRow = row();
        transparencyLabel = label("", 13);
        transparencyLabel.setTextColor(Color.WHITE);
        transparencyLabel.setGravity(Gravity.CENTER);
        transparencyRow.addView(transparencyLabel,
                new LinearLayout.LayoutParams(dp(82), dp(48)));

        transparencySeekBar = new SeekBar(context);
        transparencySeekBar.setMax(100);
        tintTransparencySeekBar(transparencySeekBar);
        transparencySeekBar.setContentDescription(
                context.getString(R.string.settings_picture_alpha));
        transparencySeekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (!fromUser) {
                    return;
                }
                updateTransparencyLabel(progress);
                ManageMethods.setCurrentPictureAlpha(context, progress / 100f);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        LinearLayout.LayoutParams seekBarParams =
                new LinearLayout.LayoutParams(0, dp(48), 1f);
        seekBarParams.setMargins(dp(2), 0, dp(2), 0);
        transparencyRow.addView(transparencySeekBar, seekBarParams);
        root.addView(transparencyRow);
    }

    private void tintTransparencySeekBar(SeekBar seekBar) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            seekBar.setProgressTintList(ColorStateList.valueOf(HIGHLIGHT_BLUE));
            seekBar.setThumbTintList(ColorStateList.valueOf(HIGHLIGHT_BLUE));
            return;
        }
        Drawable progressDrawable = seekBar.getProgressDrawable();
        if (progressDrawable instanceof LayerDrawable) {
            Drawable progress = ((LayerDrawable) progressDrawable)
                    .findDrawableByLayerId(android.R.id.progress);
            if (progress != null) {
                progress.setColorFilter(HIGHLIGHT_BLUE, PorterDuff.Mode.SRC_IN);
            }
        }
        Drawable thumb = seekBar.getThumb();
        if (thumb != null) {
            thumb.setColorFilter(HIGHLIGHT_BLUE, PorterDuff.Mode.SRC_IN);
        }
    }

    private void updateTransparencyControl() {
        if (transparencyLabel == null || transparencySeekBar == null) {
            return;
        }
        String currentId = ((MainApplication) context.getApplicationContext())
                .getCurrentPictureId();
        boolean hasPicture = currentId != null
                && new PictureData().getListArray().containsKey(currentId);
        transparencySeekBar.setEnabled(hasPicture);
        if (!hasPicture) {
            transparencyLabel.setText(R.string.floating_control_transparency_unavailable);
            transparencySeekBar.setProgress(0);
            return;
        }
        int progress = Math.round(ManageMethods.getCurrentPictureAlpha(context) * 100f);
        transparencySeekBar.setProgress(progress);
        updateTransparencyLabel(progress);
    }

    private void updateTransparencyLabel(int progress) {
        if (transparencyLabel != null) {
            transparencyLabel.setText(context.getString(
                    R.string.floating_control_transparency, progress));
        }
    }

    private void addCircularDirectionPad() {
        CircularDirectionPadView directionPad = new CircularDirectionPadView(context);
        directionPad.setContentDescription(
                context.getString(R.string.floating_control_direction_pad));
        directionPad.setOnDirectionListener(this::moveCurrentPicture);
        addPrecisionControlView(directionPad);
    }

    private void addRotationDial() {
        rotationDialView = new RotationDialView(context);
        rotationDialView.setContentDescription(
                context.getString(R.string.floating_control_rotation_dial));
        rotationDialView.setAngle(ManageMethods.getCurrentPictureDegree(context));
        rotationDialView.setOnAngleChangeListener(
                angle -> ManageMethods.setCurrentPictureDegree(context, angle));
        addPrecisionControlView(rotationDialView);
    }

    private void addPrecisionControlView(View controlView) {
        FrameLayout container = new FrameLayout(context);
        FrameLayout.LayoutParams controlParams = new FrameLayout.LayoutParams(
                dp(176), dp(176), Gravity.CENTER);
        container.addView(controlView, controlParams);
        root.addView(container, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(176)));
    }

    private void expand() {
        if (root == null || layoutParams == null) {
            return;
        }
        buildExpandedView();
        layoutParams.width = dp(236);
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        clampControllerPosition(dp(236), dp(EXPANDED_HEIGHT_DP));
        layoutParams.x = controllerX;
        layoutParams.y = controllerY;
        windowManager.updateViewLayout(root, layoutParams);
    }

    private void collapse() {
        if (root == null || layoutParams == null) {
            return;
        }
        buildCollapsedView();
        layoutParams.width = dp(COLLAPSED_SIZE_DP);
        layoutParams.height = dp(COLLAPSED_SIZE_DP);
        clampControllerPosition(dp(COLLAPSED_SIZE_DP), dp(COLLAPSED_SIZE_DP));
        snapCollapsedToDockedEdge();
        layoutParams.x = controllerX;
        layoutParams.y = controllerY;
        windowManager.updateViewLayout(root, layoutParams);
        saveControllerPosition();
    }

    private void switchPicture(int direction) {
        if (ManageMethods.switchPicture(context, direction)) {
            ManageMethods.updateNotificationCount(context);
        }
        refreshState();
        bringControlToFront();
    }

    private void toggleCurrentPicture() {
        ManageMethods.setCurrentPictureVisible(
                context, !ManageMethods.isCurrentPictureVisible(context));
        ManageMethods.updateNotificationCount(context);
        refreshState();
        bringControlToFront();
    }

    private void toggleDirectionPad() {
        if (precisionControlMode == PRECISION_CONTROL_DIRECTION) {
            precisionControlMode = PRECISION_CONTROL_ROTATION;
        } else if (precisionControlMode == PRECISION_CONTROL_ROTATION) {
            precisionControlMode = PRECISION_CONTROL_NONE;
        } else {
            precisionControlMode = PRECISION_CONTROL_DIRECTION;
        }
        buildExpandedView();
        updateExpandedWindowLayout();
    }

    private void toggleTransparencyControl() {
        precisionControlMode = precisionControlMode == PRECISION_CONTROL_TRANSPARENCY
                ? PRECISION_CONTROL_NONE
                : PRECISION_CONTROL_TRANSPARENCY;
        buildExpandedView();
        updateExpandedWindowLayout();
    }

    private boolean moveCurrentPicture(int deltaX, int deltaY) {
        if (!ManageMethods.moveCurrentPicture(context, deltaX, deltaY)) {
            Toast.makeText(
                    context, R.string.floating_control_move_failed, Toast.LENGTH_SHORT).show();
            return false;
        }
        return true;
    }

    private void toggleMainApplication() {
        collapse();
        if (MainActivity.hideIfVisible()) {
            return;
        }
        Intent intent = new Intent(context, MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK
                | Intent.FLAG_ACTIVITY_CLEAR_TOP
                | Intent.FLAG_ACTIVITY_SINGLE_TOP);
        context.startActivity(intent);
    }

    private void updateVisibilityButton() {
        if (visibilityButton == null) {
            return;
        }
        boolean visible = ManageMethods.isCurrentPictureVisible(context);
        visibilityButton.setImageResource(
                visible ? R.drawable.ic_visible : R.drawable.ic_invisible);
        visibilityButton.setColorFilter(
                visible ? HIGHLIGHT_BLUE : Color.WHITE);
        visibilityButton.setAlpha(1f);
    }

    private void toggleGestureControl() {
        boolean enabled = !preferences.getBoolean(
                Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        ManageMethods.setMoveAndScaleGestureEnabled(context, enabled);
        refreshGestureToggleButtons();
        bringControlToFront();
    }

    private void toggleRotationControl() {
        boolean enabled = !preferences.getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
        ManageMethods.setRotationGestureEnabled(context, enabled);
        refreshGestureToggleButtons();
        bringControlToFront();
    }

    private void toggleOverflowControl() {
        boolean enabled = !ManageMethods.isCurrentPictureOverLayout(context);
        ManageMethods.setCurrentPictureOverLayout(context, enabled);
        refreshGestureToggleButtons();
        bringControlToFront();
    }

    private void refreshGestureToggleButtons() {
        applyToggleState(
                gestureButton,
                preferences.getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false));
        boolean rotationUnlocked = preferences.getBoolean(
                Config.PREFERENCE_PINCH_ROTATION, false);
        if (rotationButton != null) {
            rotationButton.setImageResource(rotationUnlocked
                    ? R.drawable.ic_gesture_rotate
                    : R.drawable.ic_gesture_rotate_locked);
        }
        applyToggleState(rotationButton, rotationUnlocked);
        boolean overflowEnabled = ManageMethods.isCurrentPictureOverLayout(context);
        if (overflowButton != null) {
            overflowButton.setImageResource(overflowEnabled
                    ? R.drawable.ic_picture_overflow_on
                    : R.drawable.ic_picture_overflow_off);
        }
        applyToggleState(overflowButton, overflowEnabled);
        if (directionPadButton != null) {
            directionPadButton.setImageResource(
                    precisionControlMode == PRECISION_CONTROL_ROTATION
                            ? R.drawable.ic_gesture_rotate
                            : R.drawable.ic_picture_move_dpad);
        }
        applyToggleState(
                directionPadButton,
                precisionControlMode == PRECISION_CONTROL_DIRECTION
                        || precisionControlMode == PRECISION_CONTROL_ROTATION);
        applyToggleState(
                transparencyButton,
                precisionControlMode == PRECISION_CONTROL_TRANSPARENCY);
    }

    private void applyToggleState(ImageButton button, boolean enabled) {
        if (button == null) {
            return;
        }
        button.setColorFilter(enabled ? HIGHLIGHT_BLUE : Color.LTGRAY);
        button.setAlpha(enabled ? 1f : 0.58f);
    }

    private void showSaveConfirmation() {
        showConfirmation(R.string.floating_control_confirm_save, () -> {
            boolean saved = ManageMethods.saveCurrentPictureGestureAdjustmentsFromControl(context);
            Toast.makeText(
                    context,
                    saved
                            ? R.string.gesture_adjustments_saved
                            : R.string.gesture_adjustments_save_failed,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showDiscardConfirmation() {
        showConfirmation(R.string.floating_control_confirm_discard, () -> {
            boolean discarded = ManageMethods.discardCurrentPictureGestureAdjustments(context);
            Toast.makeText(
                    context,
                    discarded
                            ? R.string.gesture_adjustments_discarded
                            : R.string.gesture_adjustments_discard_failed,
                    Toast.LENGTH_SHORT).show();
        });
    }

    private void showConfirmation(int messageResource, Runnable confirmedAction) {
        root.removeAllViews();
        root.setOnTouchListener(null);
        root.setPadding(dp(10), dp(8), dp(10), dp(10));
        root.setBackground(roundedDrawable(Color.rgb(40, 45, 55), dp(16)));

        TextView title = label(context.getString(R.string.floating_control_confirm_title), 16);
        title.setTextColor(Color.WHITE);
        title.setGravity(Gravity.CENTER);
        root.addView(title, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(40)));

        TextView message = label(context.getString(messageResource), 14);
        message.setTextColor(Color.WHITE);
        message.setGravity(Gravity.CENTER);
        message.setPadding(dp(6), dp(4), dp(6), dp(8));
        root.addView(message, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66)));

        LinearLayout actionRow = row();
        Button cancel = button(context.getString(R.string.cancel), R.string.cancel);
        cancel.setOnClickListener(view -> restoreExpandedControls());
        actionRow.addView(cancel, weightedButtonParams());
        Button confirm = button(context.getString(R.string.done), R.string.done);
        confirm.setOnClickListener(view -> {
            confirmedAction.run();
            restoreExpandedControls();
        });
        actionRow.addView(confirm, weightedButtonParams());
        root.addView(actionRow);
        updateExpandedWindowLayout();
    }

    private void restoreExpandedControls() {
        buildExpandedView();
        updateExpandedWindowLayout();
    }

    private void updateExpandedWindowLayout() {
        if (layoutParams == null || root == null || !root.isAttachedToWindow()) {
            return;
        }
        layoutParams.width = dp(236);
        layoutParams.height = WindowManager.LayoutParams.WRAP_CONTENT;
        clampControllerPosition(dp(236), dp(expandedEstimatedHeightDp()));
        layoutParams.x = controllerX;
        layoutParams.y = controllerY;
        windowManager.updateViewLayout(root, layoutParams);
    }

    private void bringControlToFront() {
        LinearLayout previousRoot = root;
        WindowManager.LayoutParams previousParams = layoutParams;
        if (previousRoot == null || previousParams == null
                || !previousRoot.isAttachedToWindow()) {
            return;
        }
        boolean wasExpanded = expanded;
        int previousPrecisionMode = precisionControlMode;
        LinearLayout replacementRoot = new LinearLayout(context);
        replacementRoot.setOrientation(LinearLayout.VERTICAL);
        WindowManager.LayoutParams replacementParams = new WindowManager.LayoutParams();
        replacementParams.copyFrom(previousParams);
        replacementParams.windowAnimations = 0;
        replacementRoot.setAlpha(0f);
        root = replacementRoot;
        layoutParams = replacementParams;
        if (wasExpanded) {
            buildExpandedView();
        } else {
            buildCollapsedView();
        }
        try {
            windowManager.addView(replacementRoot, replacementParams);
            ViewTreeObserver.OnDrawListener firstDrawListener =
                    new ViewTreeObserver.OnDrawListener() {
                        private boolean handled;

                        @Override
                        public void onDraw() {
                            if (handled) {
                                return;
                            }
                            handled = true;
                            replacementRoot.post(() -> {
                                ViewTreeObserver observer = replacementRoot.getViewTreeObserver();
                                if (observer.isAlive()) {
                                    observer.removeOnDrawListener(this);
                                }
                                replacementRoot.setAlpha(1f);
                                replacementRoot.postOnAnimation(() -> {
                                    if (previousRoot.isAttachedToWindow()) {
                                        try {
                                            windowManager.removeView(previousRoot);
                                        } catch (IllegalArgumentException ignored) {
                                            // The previous copy may already be gone.
                                        }
                                    }
                                });
                            });
                        }
                    };
            replacementRoot.getViewTreeObserver().addOnDrawListener(firstDrawListener);
            replacementRoot.invalidate();
        } catch (RuntimeException ignored) {
            root = previousRoot;
            layoutParams = previousParams;
            expanded = wasExpanded;
            precisionControlMode = previousPrecisionMode;
            if (wasExpanded) {
                buildExpandedView();
            } else {
                buildCollapsedView();
            }
        }
    }

    @SuppressLint("ClickableViewAccessibility")
    private View.OnTouchListener createDragListener(boolean openOnTap) {
        return new View.OnTouchListener() {
            private float downRawX;
            private float downRawY;
            private int downWindowX;
            private int downWindowY;
            private boolean dragged;

            @Override
            public boolean onTouch(View view, MotionEvent event) {
                switch (event.getActionMasked()) {
                    case MotionEvent.ACTION_DOWN -> {
                        downRawX = event.getRawX();
                        downRawY = event.getRawY();
                        downWindowX = layoutParams.x;
                        downWindowY = layoutParams.y;
                        dragged = false;
                        return true;
                    }
                    case MotionEvent.ACTION_MOVE -> {
                        float deltaX = event.getRawX() - downRawX;
                        float deltaY = event.getRawY() - downRawY;
                        if (Math.hypot(deltaX, deltaY) > touchSlop) {
                            dragged = true;
                        }
                        if (dragged) {
                            controllerX = downWindowX + Math.round(deltaX);
                            controllerY = downWindowY + Math.round(deltaY);
                            clampControllerPosition(
                                    expanded ? dp(236) : dp(COLLAPSED_SIZE_DP),
                                    expanded
                                            ? dp(expandedEstimatedHeightDp())
                                            : dp(COLLAPSED_SIZE_DP));
                            layoutParams.x = controllerX;
                            layoutParams.y = controllerY;
                            windowManager.updateViewLayout(root, layoutParams);
                        }
                        return true;
                    }
                    case MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (dragged) {
                            if (!expanded) {
                                updateDockSideFromPosition(dp(COLLAPSED_SIZE_DP));
                                snapCollapsedToDockedEdge();
                                layoutParams.x = controllerX;
                                layoutParams.y = controllerY;
                                windowManager.updateViewLayout(root, layoutParams);
                            } else {
                                updateDockSideFromPosition(dp(236));
                            }
                            saveControllerPosition();
                        } else if (openOnTap && event.getActionMasked() == MotionEvent.ACTION_UP) {
                            expand();
                        }
                        view.performClick();
                        return true;
                    }
                    default -> {
                        return false;
                    }
                }
            }
        };
    }

    private void clampControllerPosition(int width, int estimatedHeight) {
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);
        controllerX = Math.max(0, Math.min(controllerX, Math.max(0, displaySize.x - width)));
        controllerY = Math.max(0, Math.min(controllerY,
                Math.max(0, displaySize.y - estimatedHeight)));
    }

    private void updateDockSideFromPosition(int currentWidth) {
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);
        dockOnRight = controllerX + currentWidth / 2 >= displaySize.x / 2;
    }

    private void snapCollapsedToDockedEdge() {
        Point displaySize = new Point();
        windowManager.getDefaultDisplay().getSize(displaySize);
        int rightEdge = Math.max(0, displaySize.x - dp(COLLAPSED_SIZE_DP));
        controllerX = dockOnRight ? rightEdge : 0;
    }

    private Bitmap getTrimmedCollapsedIcon() {
        if (collapsedIconBitmap != null && !collapsedIconBitmap.isRecycled()) {
            return collapsedIconBitmap;
        }
        Bitmap source = BitmapFactory.decodeResource(
                context.getResources(), R.drawable.floating_control_dot);
        if (source == null) {
            return null;
        }

        int left = source.getWidth();
        int top = source.getHeight();
        int right = -1;
        int bottom = -1;
        for (int y = 0; y < source.getHeight(); y++) {
            for (int x = 0; x < source.getWidth(); x++) {
                if (Color.alpha(source.getPixel(x, y)) > 8) {
                    left = Math.min(left, x);
                    top = Math.min(top, y);
                    right = Math.max(right, x);
                    bottom = Math.max(bottom, y);
                }
            }
        }
        if (right < left || bottom < top) {
            collapsedIconBitmap = source;
        } else {
            collapsedIconBitmap = Bitmap.createBitmap(
                    source, left, top, right - left + 1, bottom - top + 1);
        }
        return collapsedIconBitmap;
    }

    private void saveControllerPosition() {
        preferences.edit()
                .putInt(Config.PREFERENCE_FLOATING_CONTROL_X, controllerX)
                .putInt(Config.PREFERENCE_FLOATING_CONTROL_Y, controllerY)
                .putBoolean(Config.PREFERENCE_FLOATING_CONTROL_DOCK_RIGHT, dockOnRight)
                .apply();
    }

    private LinearLayout row() {
        LinearLayout row = new LinearLayout(context);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER);
        return row;
    }

    private TextView label(String text, int textSizeSp) {
        TextView view = new TextView(context);
        view.setText(text);
        view.setTextSize(textSizeSp);
        view.setGravity(Gravity.CENTER_VERTICAL);
        return view;
    }

    private Button button(String text, int contentDescription) {
        Button button = new Button(context);
        button.setText(text);
        button.setTextSize(14);
        button.setTextColor(Color.WHITE);
        button.setPadding(0, 0, 0, 0);
        button.setMinWidth(0);
        button.setMinHeight(0);
        button.setAllCaps(false);
        button.setContentDescription(context.getString(contentDescription));
        button.setBackground(roundedDrawable(Color.rgb(64, 72, 86), dp(10)));
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(dp(48), dp(42));
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        button.setLayoutParams(params);
        return button;
    }

    private ImageButton imageButton(int drawableResource, int contentDescription) {
        ImageButton button = new ImageButton(context);
        button.setImageResource(drawableResource);
        button.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        button.setColorFilter(Color.WHITE);
        button.setPadding(dp(10), dp(8), dp(10), dp(8));
        button.setMinimumWidth(0);
        button.setMinimumHeight(0);
        button.setContentDescription(context.getString(contentDescription));
        button.setBackground(roundedDrawable(Color.rgb(64, 72, 86), dp(10)));
        return button;
    }

    private LinearLayout.LayoutParams weightedButtonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(0, dp(42), 1f);
        params.setMargins(dp(3), dp(3), dp(3), dp(3));
        return params;
    }

    private int expandedEstimatedHeightDp() {
        if (precisionControlMode == PRECISION_CONTROL_TRANSPARENCY) {
            return EXPANDED_HEIGHT_DP + 48;
        }
        return precisionControlMode == PRECISION_CONTROL_NONE
                ? EXPANDED_HEIGHT_DP : EXPANDED_PRECISION_HEIGHT_DP;
    }

    private GradientDrawable roundedDrawable(int color, int radius) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.RECTANGLE);
        drawable.setColor(color);
        drawable.setCornerRadius(radius);
        return drawable;
    }

    private GradientDrawable circleDrawable(int color) {
        GradientDrawable drawable = new GradientDrawable();
        drawable.setShape(GradientDrawable.OVAL);
        drawable.setColor(color);
        return drawable;
    }

    private int dp(int value) {
        return Math.round(value * context.getResources().getDisplayMetrics().density);
    }
}
