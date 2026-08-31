package tool.xfy9326.floatpicture;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.widget.EditText;

import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.FileInputStream;
import java.lang.reflect.Field;

import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 21)
// Run on a disposable emulator: these integration tests exercise global window settings.
public class RotationEditingTest {
    private static final String PICTURE_ID = "rotation-edit-regression";
    private Instrumentation instrumentation;
    private Context context;
    private PictureSettingsActivity activity;
    private PictureSettingsFragment fragment;
    private boolean fixtureCreated;

    @Before
    public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        // Never run global visibility changes against a device containing user pictures.
        assumeTrue("Use an empty test installation", new PictureData().getListArray().isEmpty());
        try (ParcelFileDescriptor result = instrumentation.getUiAutomation()
                .executeShellCommand("appops set " + context.getPackageName()
                        + " SYSTEM_ALERT_WINDOW allow");
             FileInputStream input = new FileInputStream(result.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES, false)
                .putBoolean(Config.PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN, true)
                .commit();
        Bitmap source = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.CYAN);
        assertTrue(IOMethods.replaceBitmap(source, 100, Config.DEFAULT_PICTURE_DIR + PICTURE_ID));
        source.recycle();
        PictureData data = new PictureData();
        data.setDataControl(PICTURE_ID);
        data.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
        data.put(Config.DATA_PICTURE_ZOOM_X, 1f);
        data.put(Config.DATA_PICTURE_ZOOM_Y, 1f);
        data.put(Config.DATA_PICTURE_DEGREE, 0f);
        data.commit("Rotation regression");
        fixtureCreated = true;
        instrumentation.runOnMainSync(() -> {
            ManageMethods.RunWin(context);
            ManageMethods.selectCurrentPicture(context, PICTURE_ID);
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            assertTrue(ManageMethods.setCurrentPictureDegree(context, 37.25f));
            assertTrue(ManageMethods.saveCurrentPictureGestureAdjustmentsFromControl(context));
            ManageMethods.prepareWindowForEditing(context, PICTURE_ID);
        });
        openEditor();
    }

    private void openEditor() {
        Intent intent = new Intent(context, PictureSettingsActivity.class)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                .putExtra(Config.INTENT_PICTURE_EDIT_MODE, true)
                .putExtra(Config.INTENT_PICTURE_EDIT_ID, PICTURE_ID);
        activity = (PictureSettingsActivity) instrumentation.startActivitySync(intent);
        long deadline = SystemClock.uptimeMillis() + 10000;
        boolean[] ready = {false};
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                fragment = (PictureSettingsFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.layout_picture_settings_content);
                Preference preference = fragment == null ? null
                        : fragment.findPreference(Config.PREFERENCE_PICTURE_DEGREE);
                ready[0] = preference != null && preference.getOnPreferenceClickListener() != null;
            });
            if (!ready[0]) SystemClock.sleep(50);
        }
        assertTrue("Editor did not finish loading", ready[0]);
        instrumentation.waitForIdleSync();
    }

    private AlertDialog openAngle() {
        return openControl(Config.PREFERENCE_PICTURE_DEGREE);
    }

    private AlertDialog openControl(String key) {
        AlertDialog[] result = {null};
        instrumentation.runOnMainSync(() -> {
            Preference preference = fragment.findPreference(key);
            preference.getOnPreferenceClickListener().onPreferenceClick(preference);
            try {
                Field field = PictureSettingsFragment.class.getDeclaredField("currentDialog");
                field.setAccessible(true);
                result[0] = (AlertDialog) field.get(fragment);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        instrumentation.waitForIdleSync();
        assertTrue(result[0].isShowing());
        return result[0];
    }

    @Test
    public void visiblePictureStaysAttachedAfterEnteringEditor() {
        instrumentation.runOnMainSync(() -> assertTrue("The editing picture disappeared",
                ImageMethods.getFloatImageViewById(context, PICTURE_ID).isAttachedToWindow()));
    }

    @Test
    public void savedGestureAngleCanBeEditedRepeatedly() {
        for (int i = 0; i < 3; i++) {
            AlertDialog dialog = openAngle();
            instrumentation.runOnMainSync(() -> {
                EditText input = dialog.findViewById(R.id.edittext_set_size);
                input.requestFocus();
                input.setText("123.45");
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            });
            instrumentation.waitForIdleSync();
        }
        instrumentation.runOnMainSync(() -> fragment.saveAllData());
        PictureData data = new PictureData();
        data.setDataControl(PICTURE_ID);
        assertEquals(123.45f, data.getFloat(Config.DATA_PICTURE_DEGREE, -1f), 0.001f);
        closeEditor();
        instrumentation.runOnMainSync(() -> ManageMethods.prepareWindowForEditing(context, PICTURE_ID));
        instrumentation.waitForIdleSync();
        openEditor();
        AlertDialog reopened = openAngle();
        instrumentation.runOnMainSync(() -> {
            EditText input = reopened.findViewById(R.id.edittext_set_size);
            assertEquals("123.45", input.getText().toString());
            input.setText("90.00");
            reopened.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> fragment.saveAllData());
        data.setDataControl(PICTURE_ID);
        assertEquals(123.45f, data.getFloat(Config.DATA_PICTURE_DEGREE, -1f), 0.001f);
    }

    @Test
    public void allPictureControlsCanConfirmAndCancelInSinglePictureMode() {
        exercisePictureControls();
    }

    @Test
    public void allPictureControlsWorkInMultiplePictureMode() {
        closeEditor();
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES, true).commit();
        instrumentation.runOnMainSync(() -> ManageMethods.prepareWindowForEditing(context, PICTURE_ID));
        openEditor();
        exercisePictureControls();
    }

    private void exercisePictureControls() {
        String[] controls = {Config.PREFERENCE_PICTURE_RESIZE, Config.PREFERENCE_PICTURE_POSITION,
                Config.PREFERENCE_PICTURE_DEGREE, Config.PREFERENCE_PICTURE_ALPHA};
        for (String control : controls) {
            for (int button : new int[]{AlertDialog.BUTTON_POSITIVE, AlertDialog.BUTTON_NEGATIVE}) {
                AlertDialog dialog = openControl(control);
                instrumentation.runOnMainSync(() -> dialog.getButton(button).performClick());
                instrumentation.waitForIdleSync();
                instrumentation.runOnMainSync(() -> assertTrue("Picture disappeared after " + control,
                        ImageMethods.getFloatImageViewById(context, PICTURE_ID).isAttachedToWindow()));
            }
        }
    }

    @Test
    public void editingHiddenPictureRestoresItsHiddenStateOnSaveAndCancel() {
        closeEditor();
        for (boolean save : new boolean[]{true, false}) {
            instrumentation.runOnMainSync(() -> {
                ManageMethods.setWindowVisible(context, new PictureData(), PICTURE_ID, false);
                ManageMethods.prepareWindowForEditing(context, PICTURE_ID);
            });
            instrumentation.waitForIdleSync();
            openEditor();
            AlertDialog dialog = openAngle();
            instrumentation.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick());
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> {
                if (save) fragment.saveAllData();
                else fragment.exit();
            });
            instrumentation.waitForIdleSync();
            instrumentation.runOnMainSync(() -> assertFalse(
                    ImageMethods.getFloatImageViewById(context, PICTURE_ID).isAttachedToWindow()));
            PictureData data = new PictureData();
            data.setDataControl(PICTURE_ID);
            assertFalse(data.getBoolean(Config.DATA_PICTURE_SHOW_ENABLED, true));
            closeEditor();
        }
    }

    @Test
    public void gestureStateRefreshWhileEditingDoesNotCrash() {
        AlertDialog dialog = openAngle();
        instrumentation.runOnMainSync(() -> ManageMethods.setRotationGestureEnabled(context, false));
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick());
        instrumentation.waitForIdleSync();
    }

    private void closeEditor() {
        instrumentation.runOnMainSync(() -> {
            fragment.clearEditView();
            activity.finish();
        });
        instrumentation.waitForIdleSync();
        fragment = null;
        activity = null;
    }

    @After
    public void tearDown() {
        if (instrumentation == null || !fixtureCreated) return;
        instrumentation.runOnMainSync(() -> {
            if (fragment != null) fragment.clearEditView();
            if (activity != null) activity.finish();
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> ManageMethods.DeleteWin(context, PICTURE_ID));
    }
}
