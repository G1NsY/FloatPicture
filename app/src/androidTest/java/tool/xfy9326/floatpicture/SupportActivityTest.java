package tool.xfy9326.floatpicture;

import android.app.Instrumentation;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.view.View;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import tool.xfy9326.floatpicture.Activities.SupportActivity;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SupportActivityTest {
    @Test
    public void supportScreenSurvivesRecreationAndRespectsDistributionSetting() {
        try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(activity.getSupportActionBar());
                assertEquals(activity.getString(R.string.support_development),
                        activity.getSupportActionBar().getTitle());
                assertNotNull(activity.findViewById(R.id.support_scroll));
                assertEquals(activity.getResources().getBoolean(R.bool.support_external_enabled)
                                ? View.VISIBLE : View.GONE,
                        activity.findViewById(R.id.support_external_card).getVisibility());
                assertEquals(activity.getString(R.string.support_optional_message),
                        ((TextView) activity.findViewById(R.id.support_optional_message)).getText().toString());
            });
        }
    }

    @Test
    public void externalNavigationRequiresConfirmationAndIsBlockedWhenDisabled() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        IntentFilter filter = new IntentFilter(Intent.ACTION_VIEW);
        filter.addCategory(Intent.CATEGORY_BROWSABLE);
        filter.addDataScheme("https");
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(filter,
                new Instrumentation.ActivityResult(0, null), true);
        instrumentation.addMonitor(monitor);
        try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
            scenario.onActivity(activity -> activity.findViewById(R.id.support_open_afdian).performClick());
            instrumentation.waitForIdleSync();
            assertEquals("Opening the screen/button must not open another app immediately", 0, monitor.getHits());
            boolean enabled = instrumentation.getTargetContext().getResources()
                    .getBoolean(R.bool.support_external_enabled);
            if (enabled) {
                clickDialogButton(scenario, instrumentation, AlertDialog.BUTTON_NEGATIVE);
                assertEquals(0, monitor.getHits());
                scenario.onActivity(activity -> activity.findViewById(R.id.support_open_afdian).performClick());
                clickDialogButton(scenario, instrumentation, AlertDialog.BUTTON_POSITIVE);
                assertEquals(1, monitor.getHits());
            } else {
                // Even a programmatic click on the hidden control must remain inert.
                assertEquals(0, monitor.getHits());
            }
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    private static void clickDialogButton(ActivityScenario<SupportActivity> scenario,
                                          Instrumentation instrumentation, int buttonId) {
        scenario.onActivity(activity -> {
            try {
                java.lang.reflect.Field field = SupportActivity.class.getDeclaredField("externalDialog");
                field.setAccessible(true);
                AlertDialog dialog = (AlertDialog) field.get(activity);
                assertNotNull("Expected a confirmation dialog", dialog);
                assertTrue(dialog.isShowing());
                dialog.getButton(buttonId).performClick();
            } catch (ReflectiveOperationException exception) {
                throw new AssertionError(exception);
            }
        });
        instrumentation.waitForIdleSync();
    }

    @Test
    public void copyWritesOnlyThePublicLinkAndNeverWritesWhenDisabled() {
        try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
            scenario.onActivity(activity -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                assertNotNull(clipboard);
                clipboard.setPrimaryClip(android.content.ClipData.newPlainText("test", "unchanged"));
                activity.findViewById(R.id.support_copy_link).performClick();
                String expected = activity.getResources().getBoolean(R.bool.support_external_enabled)
                        ? "https://afdian.com/a/G1NsY" : "unchanged";
                assertNotNull(clipboard.getPrimaryClip());
                assertEquals(expected, clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            });
        }
    }

    @Test
    public void captureLocalizedLayouts() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try {
            for (String language : new String[]{"en", "zh"}) {
                for (int orientation : new int[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                    try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
                        scenario.onActivity(activity -> {
                            // AppCompat needs a live delegate to reach LocaleManager on Android 13+.
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.forLanguageTags(language));
                            activity.setRequestedOrientation(orientation);
                        });
                        instrumentation.waitForIdleSync();
                        android.os.SystemClock.sleep(600);
                        instrumentation.waitForIdleSync();
                        scenario.onActivity(activity -> assertEquals(
                                language.equals("zh") ? "支持开发" : "Support development",
                                activity.getString(R.string.support_development)));
                        String mode = instrumentation.getTargetContext().getResources()
                                .getBoolean(R.bool.support_external_enabled) ? "direct" : "no-external";
                        java.io.File output = new java.io.File(instrumentation.getTargetContext()
                                .getExternalFilesDir(null), "support-" + mode + "-" + language + "-" + orientation + ".png");
                        android.graphics.Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                        assertNotNull(screenshot);
                        try (java.io.FileOutputStream stream = new java.io.FileOutputStream(output)) {
                            assertTrue(screenshot.compress(android.graphics.Bitmap.CompressFormat.PNG, 100, stream));
                        } finally {
                            screenshot.recycle();
                        }
                    }
                }
            }
        } finally {
            try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
                scenario.onActivity(activity -> androidx.appcompat.app.AppCompatDelegate
                        .setApplicationLocales(androidx.core.os.LocaleListCompat.getEmptyLocaleList()));
            }
        }
    }
}
