package tool.xfy9326.floatpicture.Services;

import android.app.Instrumentation;
import android.content.Context;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.InputDevice;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ViewGroup;
import android.view.WindowManager;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.preference.PreferenceManager;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.ArrayList;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.View.FloatImageView;

import static org.junit.Assert.*;

/** Device test: temporary windows only; does not change saved pictures or preferences. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 23)
public class FloatingControlLayerTest {
    private Instrumentation instrumentation;
    private Context context;
    private WindowManager windowManager;
    private FloatingControlManager controller;
    private FloatImageView picture;
    private Bitmap bitmap;
    private int pictureDownCount;

    @Before
    public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        // The emulator used for this test must already grant overlay permission.
        assertTrue("Grant overlay permission before running", PermissionMethods.canDrawOverlays(context));
        windowManager = WindowsMethods.getWindowManager(context);
        bitmap = Bitmap.createBitmap(240, 160, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        canvas.drawColor(Color.rgb(225, 180, 80));
        Paint paint = new Paint();
        paint.setColor(Color.rgb(100, 55, 20));
        paint.setTextSize(20);
        canvas.drawText("OVERLAY LAYER TEST", 10, 85, paint);
    }

    private void showController() {
        controller = new FloatingControlManager(context);
        // Isolate the real controller window from library initialization and service lifecycle.
        invoke(controller, "show");
    }

    private void showControllerOnSide(boolean right) {
        controller = new FloatingControlManager(context);
        setField(controller, "controllerX", right
                ? context.getResources().getDisplayMetrics().widthPixels : 0);
        setField(controller, "controllerY", 100);
        invoke(controller, "show");
    }

    @Test
    public void rightDockExpandsDirectlyInOneWindow() throws Exception {
        assertDirectExpansion(true);
    }

    @Test
    public void leftDockExpandsDirectlyInOneWindow() throws Exception {
        assertDirectExpansion(false);
    }

    private void assertDirectExpansion(boolean right) throws Exception {
        instrumentation.runOnMainSync(() -> showControllerOnSide(right));
        settle();
        View[] original = {null};
        int[] edge = {0};
        int[] originalWidth = {0};
        instrumentation.runOnMainSync(() -> {
            original[0] = (View) field(controller, "root");
            originalWidth[0] = original[0].getWidth();
            int[] location = new int[2];
            original[0].getLocationOnScreen(location);
            edge[0] = location[0] + (right ? original[0].getWidth() : 0);
        });
        assertControllerReceivesTap(right ? "direct-right" : "direct-left");
        instrumentation.runOnMainSync(() -> {
            View panel = (View) field(controller, "root");
            assertNotSame(original[0], panel);
            assertFalse("The old dot must be removed after the panel draws", original[0].isAttachedToWindow());
            assertEquals("Do not resize the dot before showing the final panel", originalWidth[0], original[0].getWidth());
            assertTrue(((ArrayList<?>) field(controller, "retiringRoots")).isEmpty());
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) panel.getLayoutParams();
            assertEquals(right ? Gravity.RIGHT : Gravity.LEFT, params.gravity & Gravity.HORIZONTAL_GRAVITY_MASK);
            assertEquals(0, params.x);
            int[] location = new int[2];
            panel.getLocationOnScreen(location);
            assertEquals("Docked edge must stay fixed", edge[0],
                    location[0] + (right ? panel.getWidth() : 0));
        });
    }

    @Test
    public void rightAnchoredControllerFollowsLeftwardDrag() {
        SharedPreferences preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String[] keys = {Config.PREFERENCE_FLOATING_CONTROL_X, Config.PREFERENCE_FLOATING_CONTROL_Y,
                Config.PREFERENCE_FLOATING_CONTROL_DOCK_RIGHT};
        java.util.Map<String, ?> saved = preferences.getAll();
        try {
            instrumentation.runOnMainSync(() -> showControllerOnSide(true));
            settle();
            int[] down = new int[2];
            int[] startX = {0};
            instrumentation.runOnMainSync(() -> {
                View root = (View) field(controller, "root");
                root.getLocationOnScreen(down);
                startX[0] = down[0];
                // Start inside the dot, away from Android's right-edge Back gesture.
                down[0] += root.getWidth() / 4;
                down[1] += root.getHeight() / 2;
            });
            long now = SystemClock.uptimeMillis();
            injectTapEvent(now, now, MotionEvent.ACTION_DOWN, down);
            int[] moved = {down[0] - 200, down[1]};
            injectTapEvent(now, now + 60, MotionEvent.ACTION_MOVE, moved);
            settle();
            instrumentation.runOnMainSync(() -> {
                int[] location = new int[2];
                ((View) field(controller, "root")).getLocationOnScreen(location);
                assertEquals(startX[0] - 200, location[0]);
            });
            injectTapEvent(now, now + 700, MotionEvent.ACTION_UP, moved);
            settle();
        } finally {
            SharedPreferences.Editor editor = preferences.edit();
            for (String key : keys) {
                Object value = saved.get(key);
                if (value instanceof Integer) editor.putInt(key, (Integer) value);
                else if (value instanceof Boolean) editor.putBoolean(key, (Boolean) value);
                else editor.remove(key);
            }
            editor.commit();
        }
    }

    private static void setField(Object target, String name, Object value) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            field.set(target, value);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private void showPicture() {
        picture = ImageMethods.createPictureView(context, bitmap, true, true, 1f, 1f, 0f);
        picture.setAlpha(0.75f);
        picture.setOnTouchListener((view, event) -> {
            if (event.getActionMasked() == MotionEvent.ACTION_DOWN) pictureDownCount++;
            return false;
        });
        WindowsMethods.createWindow(windowManager, picture, true, true, 300, 100);
    }

    private void enlargePicture() {
        float scaleX = context.getResources().getDisplayMetrics().widthPixels / 240f + 1f;
        float scaleY = context.getResources().getDisplayMetrics().heightPixels / 160f + 1f;
        // Same window resize path used by the picture size editor.
        WindowsMethods.updateWindow(windowManager, picture, bitmap, true, true,
                scaleX, scaleY, 0f, 0, 0);
    }

    @Test
    public void pictureFirst_controllerLast_staysClickableAfterResize() throws Exception {
        instrumentation.runOnMainSync(() -> {
            showPicture();
            showController();
        });
        settle();
        instrumentation.runOnMainSync(this::enlargePicture);
        assertControllerReceivesTap("picture-first");
    }

    @Test
    public void controllerFirst_newPicture_staysClickableAfterResize() throws Exception {
        instrumentation.runOnMainSync(this::showController);
        settle();
        instrumentation.runOnMainSync(() -> {
            showPicture();
            enlargePicture();
        });
        assertControllerReceivesTap("controller-first");
    }

    @Test
    public void pictureReattached_controllerStaysClickable() throws Exception {
        instrumentation.runOnMainSync(() -> {
            showPicture();
            showController();
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            windowManager.removeViewImmediate(picture);
            WindowsMethods.createWindow(windowManager, picture, true, true, 0, 0);
            enlargePicture();
        });
        assertControllerReceivesTap("picture-reattached");
    }

    private void assertControllerReceivesTap(String scenario) throws Exception {
        settle();
        screenshot(scenario + "-before");
        int[] location = new int[2];
        instrumentation.runOnMainSync(() -> {
            View root = (View) field(controller, "root");
            assertTrue(root.isAttachedToWindow());
            root.getLocationOnScreen(location);
            location[0] += root.getWidth() / 2;
            location[1] += root.getHeight() / 2;
        });
        long downTime = SystemClock.uptimeMillis();
        injectTapEvent(downTime, downTime, MotionEvent.ACTION_DOWN, location);
        injectTapEvent(downTime, downTime + 50, MotionEvent.ACTION_UP, location);
        settle();
        screenshot(scenario + "-after");
        boolean[] expanded = {false};
        instrumentation.runOnMainSync(() -> expanded[0] = (boolean) field(controller, "expanded"));
        String evidence = scenario + ": expanded=" + expanded[0]
                + ", pictureTouchDowns=" + pictureDownCount
                + ", tap=" + location[0] + "," + location[1];
        android.util.Log.i("FloatingControlLayerTest", evidence);
        assertTrue(evidence, expanded[0]);
        assertEquals(evidence, 0, pictureDownCount);
    }

    @Test
    public void confirmationSurvivesNewPictureAndStillAcceptsTouch() throws Exception {
        int[] confirmations = {0};
        Runnable confirm = () -> confirmations[0]++;
        instrumentation.runOnMainSync(this::showController);
        settle();
        instrumentation.runOnMainSync(() -> invoke(controller, "expand"));
        settle();
        instrumentation.runOnMainSync(() -> {
            try {
                Method method = FloatingControlManager.class.getDeclaredMethod(
                        "showConfirmation", int.class, Runnable.class);
                method.setAccessible(true);
                method.invoke(controller, R.string.floating_control_confirm_save, confirm);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        settle();
        instrumentation.runOnMainSync(() -> {
            showPicture();
            enlargePicture();
        });
        settle();
        screenshot("confirmation-preserved");
        int[] location = new int[2];
        instrumentation.runOnMainSync(() -> {
            assertSame(confirm, field(controller, "confirmationAction"));
            View button = findDescription((View) field(controller, "root"), context.getString(R.string.done));
            assertNotNull(button);
            button.getLocationOnScreen(location);
            location[0] += button.getWidth() / 2;
            location[1] += button.getHeight() / 2;
        });
        long downTime = SystemClock.uptimeMillis();
        injectTapEvent(downTime, downTime, MotionEvent.ACTION_DOWN, location);
        injectTapEvent(downTime, downTime + 50, MotionEvent.ACTION_UP, location);
        settle();
        assertEquals(1, confirmations[0]);
        assertEquals(0, pictureDownCount);
    }

    @Test
    public void precisionPanelSurvivesNewPicture() {
        instrumentation.runOnMainSync(this::showController);
        settle();
        instrumentation.runOnMainSync(() -> invoke(controller, "expand"));
        settle();
        instrumentation.runOnMainSync(() -> {
            invoke(controller, "toggleDirectionPad");
        });
        settle();
        instrumentation.runOnMainSync(this::showPicture);
        settle();
        instrumentation.runOnMainSync(() -> {
            assertEquals(true, field(controller, "expanded"));
            assertEquals(1, field(controller, "precisionControlMode"));
            assertTrue(((View) field(controller, "root")).isAttachedToWindow());
        });
    }

    @Test
    public void destroyDuringRaiseRemovesBothWindowsAndDoesNotReappear() {
        ArrayList<View> windows = new ArrayList<>();
        instrumentation.runOnMainSync(this::showController);
        settle();
        instrumentation.runOnMainSync(() -> {
            windows.add((View) field(controller, "root"));
            invoke(controller, "bringControlToFront");
            windows.add((View) field(controller, "root"));
            controller.destroy();
        });
        settle();
        instrumentation.runOnMainSync(this::showPicture);
        settle();
        instrumentation.runOnMainSync(() -> {
            for (View window : windows) assertFalse(window.isAttachedToWindow());
            assertNull(field(controller, "root"));
            assertTrue(((ArrayList<?>) field(controller, "retiringRoots")).isEmpty());
            assertTrue(((ArrayList<?>) field((MainApplication) context.getApplicationContext(),
                    "pictureWindowAttachedListeners")).isEmpty());
        });
    }

    private View findDescription(View view, String description) {
        if (description.contentEquals(view.getContentDescription() == null
                ? "" : view.getContentDescription())) return view;
        if (view instanceof ViewGroup) {
            ViewGroup group = (ViewGroup) view;
            for (int i = 0; i < group.getChildCount(); i++) {
                View found = findDescription(group.getChildAt(i), description);
                if (found != null) return found;
            }
        }
        return null;
    }

    private void injectTapEvent(long downTime, long eventTime, int action, int[] location) {
        MotionEvent event = MotionEvent.obtain(downTime, eventTime, action,
                location[0], location[1], 0);
        event.setSource(InputDevice.SOURCE_TOUCHSCREEN);
        try {
            assertTrue(instrumentation.getUiAutomation().injectInputEvent(event, true));
        } finally {
            event.recycle();
        }
    }

    private void screenshot(String name) throws Exception {
        File directory = new File(context.getExternalFilesDir(null), "layer-probe");
        assertTrue(directory.isDirectory() || directory.mkdirs());
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream output = new FileOutputStream(new File(directory, name + ".png"))) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
        } finally {
            screenshot.recycle();
        }
        try (ParcelFileDescriptor descriptor = instrumentation.getUiAutomation()
                .executeShellCommand("dumpsys window windows");
             FileInputStream input = new FileInputStream(descriptor.getFileDescriptor());
             FileOutputStream output = new FileOutputStream(new File(directory, name + "-windows.txt"))) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = input.read(buffer)) != -1) output.write(buffer, 0, count);
        }
    }

    private void settle() {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(500);
    }

    private static Object field(Object target, String name) {
        try {
            Field field = target.getClass().getDeclaredField(name);
            field.setAccessible(true);
            return field.get(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    private static void invoke(Object target, String name) {
        try {
            Method method = target.getClass().getDeclaredMethod(name);
            method.setAccessible(true);
            method.invoke(target);
        } catch (ReflectiveOperationException exception) {
            throw new AssertionError(exception);
        }
    }

    @After
    public void tearDown() {
        instrumentation.runOnMainSync(() -> {
            if (controller != null) controller.destroy();
            if (picture != null && picture.isAttachedToWindow()) windowManager.removeViewImmediate(picture);
        });
        settle();
        if (bitmap != null) bitmap.recycle();
    }
}
