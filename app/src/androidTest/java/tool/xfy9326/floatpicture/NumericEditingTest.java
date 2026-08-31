package tool.xfy9326.floatpicture;

import android.app.Instrumentation;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.os.ParcelFileDescriptor;
import android.os.SystemClock;
import android.view.inputmethod.EditorInfo;
import android.widget.CheckBox;
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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.Locale;

import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;

import static org.junit.Assert.*;
import static org.junit.Assume.assumeTrue;

/** Integration fixtures are only safe on a disposable, empty emulator installation. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 21)
public class NumericEditingTest {
    private static final String PICTURE_ID = "numeric-input-regression";
    private Instrumentation instrumentation;
    private Context context;
    private PictureSettingsActivity activity;
    private PictureSettingsFragment fragment;
    private AlertDialog currentDialog;
    private Locale originalLocale;
    private boolean fixtureCreated;
    private boolean saved;

    @Before
    public void setUp() throws Exception {
        instrumentation = InstrumentationRegistry.getInstrumentation();
        context = instrumentation.getTargetContext();
        originalLocale = Locale.getDefault();
        assumeTrue("Use an empty test installation", new PictureData().getListArray().isEmpty());
        try (ParcelFileDescriptor result = instrumentation.getUiAutomation().executeShellCommand(
                "appops set " + context.getPackageName() + " SYSTEM_ALERT_WINDOW allow");
             FileInputStream input = new FileInputStream(result.getFileDescriptor())) {
            byte[] buffer = new byte[1024];
            while (input.read(buffer) != -1) { }
        }
        PreferenceManager.getDefaultSharedPreferences(context).edit()
                .putBoolean(Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES, false)
                .putBoolean(Config.PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN, true).commit();
        Bitmap source = Bitmap.createBitmap(640, 480, Bitmap.Config.ARGB_8888);
        source.eraseColor(Color.CYAN);
        assertTrue(IOMethods.replaceBitmap(source, 100, Config.DEFAULT_PICTURE_DIR + PICTURE_ID));
        source.recycle();
        PictureData data = new PictureData();
        data.setDataControl(PICTURE_ID);
        data.put(Config.DATA_PICTURE_SHOW_ENABLED, true);
        data.put(Config.DATA_PICTURE_ZOOM_X, 1f);
        data.put(Config.DATA_PICTURE_ZOOM_Y, 1f);
        data.put(Config.DATA_PICTURE_ALPHA, 0.8f);
        data.put(Config.DATA_PICTURE_DEGREE, 0f);
        data.commit("Numeric regression");
        fixtureCreated = true;
        instrumentation.runOnMainSync(() -> {
            ManageMethods.RunWin(context);
            ManageMethods.selectCurrentPicture(context, PICTURE_ID);
        });
        // Window attachment is asynchronous; mirror the separate user action to edit.
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> ManageMethods.prepareWindowForEditing(context, PICTURE_ID));
        instrumentation.waitForIdleSync();
        activity = (PictureSettingsActivity) instrumentation.startActivitySync(
                new Intent(context, PictureSettingsActivity.class).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .putExtra(Config.INTENT_PICTURE_EDIT_MODE, true)
                        .putExtra(Config.INTENT_PICTURE_EDIT_ID, PICTURE_ID));
        long deadline = SystemClock.uptimeMillis() + 10000;
        boolean[] ready = {false};
        while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
            instrumentation.runOnMainSync(() -> {
                fragment = (PictureSettingsFragment) activity.getSupportFragmentManager()
                        .findFragmentById(R.id.layout_picture_settings_content);
                Preference preference = fragment == null ? null
                        : fragment.findPreference(Config.PREFERENCE_PICTURE_ALPHA);
                ready[0] = preference != null && preference.getOnPreferenceClickListener() != null;
            });
            if (!ready[0]) SystemClock.sleep(50);
        }
        assertTrue("Editor did not load", ready[0]);
    }

    private AlertDialog openControl(String key) {
        instrumentation.runOnMainSync(() -> {
            Preference preference = fragment.findPreference(key);
            preference.getOnPreferenceClickListener().onPreferenceClick(preference);
            try {
                Field field = PictureSettingsFragment.class.getDeclaredField("currentDialog");
                field.setAccessible(true);
                currentDialog = (AlertDialog) field.get(fragment);
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        instrumentation.waitForIdleSync();
        assertTrue(currentDialog.isShowing());
        return currentDialog;
    }

    private float savedValue(String key) {
        instrumentation.waitForIdleSync();
        if (!saved) {
            instrumentation.runOnMainSync(() -> fragment.saveAllData());
            instrumentation.waitForIdleSync();
            saved = true;
        }
        PictureData data = new PictureData();
        data.setDataControl(PICTURE_ID);
        return data.getFloat(key, -1f);
    }

    @Test
    public void alphaRejectsInvalidInputOnKeyboardAndConfirmWithoutChangingPicture() {
        AlertDialog dialog = openControl(Config.PREFERENCE_PICTURE_ALPHA);
        instrumentation.runOnMainSync(() -> {
            EditText input = dialog.findViewById(R.id.edittext_set_size);
            for (String invalid : new String[]{"", ".", "-", "-0.1", "1.1", "0..5"}) {
                input.setText(invalid);
                input.onEditorAction(EditorInfo.IME_ACTION_DONE);
                assertNotNull(input.getError());
                assertEquals(0.8f, ImageMethods.getFloatImageViewById(context, PICTURE_ID).getAlpha(), 0.001f);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertTrue("Invalid input must keep the dialog open", dialog.isShowing());
            }
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertEquals(0.8f, savedValue(Config.DATA_PICTURE_ALPHA), 0.001f);
    }

    @Test
    public void alphaAcceptsCommaAndPreservesPrecisionAndCancelRollsBack() {
        Locale.setDefault(Locale.GERMANY);
        AlertDialog dialog = openControl(Config.PREFERENCE_PICTURE_ALPHA);
        instrumentation.runOnMainSync(() -> {
            EditText input = dialog.findViewById(R.id.edittext_set_size);
            input.setText("0,375");
            input.onEditorAction(EditorInfo.IME_ACTION_DONE);
            assertNull(input.getError());
            assertEquals(0.375f, ImageMethods.getFloatImageViewById(context, PICTURE_ID).getAlpha(), 0.0001f);
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());
        });
        AlertDialog cancelled = openControl(Config.PREFERENCE_PICTURE_ALPHA);
        instrumentation.runOnMainSync(() -> {
            EditText input = cancelled.findViewById(R.id.edittext_set_size);
            input.setText("0.1");
            input.onEditorAction(EditorInfo.IME_ACTION_DONE);
            cancelled.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> assertEquals(0.375f,
                ImageMethods.getFloatImageViewById(context, PICTURE_ID).getAlpha(), 0.0001f));
        assertEquals(0.375f, savedValue(Config.DATA_PICTURE_ALPHA), 0.0001f);
    }

    @Test
    public void alphaAcceptsZeroAndOneUsingConfirmWithoutKeyboardAction() {
        for (String value : new String[]{"0", "1"}) {
            AlertDialog dialog = openControl(Config.PREFERENCE_PICTURE_ALPHA);
            instrumentation.runOnMainSync(() -> {
                ((EditText) dialog.findViewById(R.id.edittext_set_size)).setText(value);
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
                assertFalse(dialog.isShowing());
                assertEquals(Float.parseFloat(value),
                        ImageMethods.getFloatImageViewById(context, PICTURE_ID).getAlpha(), 0.0001f);
            });
        }
        assertEquals(1f, savedValue(Config.DATA_PICTURE_ALPHA), 0.0001f);
    }

    @Test
    public void zoomUsesStableDecimalTextAndAcceptsCommaFromGermanKeyboard() {
        Locale.setDefault(Locale.GERMANY);
        AlertDialog dialog = openControl(Config.PREFERENCE_PICTURE_RESIZE);
        instrumentation.runOnMainSync(() -> {
            EditText x = dialog.findViewById(R.id.edittext_set_size_x);
            EditText y = dialog.findViewById(R.id.edittext_set_size_y);
            assertEquals("1.000", x.getText().toString());
            ((CheckBox) dialog.findViewById(R.id.checkbox_lock_ratio)).setChecked(false);
            x.requestFocus();
            x.setText("0,500");
            x.onEditorAction(EditorInfo.IME_ACTION_DONE);
            assertEquals("0.500", x.getText().toString());
            assertNull(x.getError());
            y.requestFocus();
            y.setText("0,750");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertFalse(dialog.isShowing());
        });
        assertEquals(0.5f, savedValue(Config.DATA_PICTURE_ZOOM_X), 0.001f);
        assertEquals(0.75f, savedValue(Config.DATA_PICTURE_ZOOM_Y), 0.001f);
    }

    @Test
    public void zoomRejectsPartialInputAndDoesNotSaveOnlyOneAxis() {
        AlertDialog dialog = openControl(Config.PREFERENCE_PICTURE_RESIZE);
        instrumentation.runOnMainSync(() -> {
            ((CheckBox) dialog.findViewById(R.id.checkbox_lock_ratio)).setChecked(false);
            EditText x = dialog.findViewById(R.id.edittext_set_size_x);
            EditText y = dialog.findViewById(R.id.edittext_set_size_y);
            x.requestFocus();
            x.setText("");
            x.onEditorAction(EditorInfo.IME_ACTION_DONE);
            assertNotNull(x.getError());
            x.setText("0.5");
            y.setText(".");
            dialog.getButton(AlertDialog.BUTTON_POSITIVE).performClick();
            assertTrue(dialog.isShowing());
            assertNotNull(y.getError());
            dialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
        });
        assertEquals(1f, savedValue(Config.DATA_PICTURE_ZOOM_X), 0.001f);
        assertEquals(1f, savedValue(Config.DATA_PICTURE_ZOOM_Y), 0.001f);
    }

    @Test
    public void decimalParserRejectsNonFiniteValuesAndAcceptsLocalizedDigits() throws Exception {
        Method parser = PictureSettingsFragment.class.getDeclaredMethod("parseDecimalInput", String.class);
        parser.setAccessible(true);
        for (String value : new String[]{"NaN", "Infinity", "1e99", "0x1.0p1", "1,2.3",
                "999999999999999999999999999999999999999999999999999999"}) {
            try {
                parser.invoke(null, value);
                fail("Accepted invalid number: " + value);
            } catch (InvocationTargetException exception) {
                assertTrue(exception.getCause() instanceof NumberFormatException);
            }
        }
        Locale.setDefault(new Locale("ar"));
        assertEquals(0.5f, (Float) parser.invoke(null, "٠٫٥"), 0.001f);
    }

    @After
    public void tearDown() {
        if (originalLocale != null) Locale.setDefault(originalLocale);
        if (instrumentation == null || !fixtureCreated) return;
        instrumentation.runOnMainSync(() -> {
            if (currentDialog != null && currentDialog.isShowing()) {
                currentDialog.getButton(AlertDialog.BUTTON_NEGATIVE).performClick();
            }
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> {
            if (fragment != null) fragment.clearEditView();
            if (activity != null) activity.finish();
        });
        instrumentation.waitForIdleSync();
        instrumentation.runOnMainSync(() -> ManageMethods.DeleteWin(context, PICTURE_ID));
    }
}
