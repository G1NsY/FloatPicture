package tool.xfy9326.floatpicture;

import android.app.Instrumentation;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.view.View;
import android.widget.ImageView;
import android.widget.TextView;

import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import tool.xfy9326.floatpicture.Activities.SupportActivity;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class SupportActivityTest {
    private static SupportActivity.QrPreviewDialog preview(SupportActivity activity) {
        return (SupportActivity.QrPreviewDialog) activity.getSupportFragmentManager()
                .findFragmentByTag("support_qr_preview");
    }

    @Test
    public void supportScreenSurvivesRecreationAndRespectsDistributionSetting() {
        try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertNotNull(activity.getSupportActionBar());
                assertEquals(activity.getString(R.string.support_development),
                        activity.getSupportActionBar().getTitle());
                assertEquals(activity.getResources().getBoolean(R.bool.support_external_enabled)
                                ? View.VISIBLE : View.GONE,
                        activity.findViewById(R.id.support_external_card).getVisibility());
                assertEquals(activity.getString(R.string.support_optional_message),
                        ((TextView) activity.findViewById(R.id.support_optional_message)).getText().toString());
                assertNotNull(((ImageView) activity.findViewById(R.id.support_qr_image)).getDrawable());
            });
        }
    }

    @Test
    public void qrViewerStaysLocalSurvivesRecreationAndCanClose() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        Instrumentation.ActivityMonitor monitor = new Instrumentation.ActivityMonitor(
                new IntentFilter(Intent.ACTION_VIEW), new Instrumentation.ActivityResult(0, null), true);
        instrumentation.addMonitor(monitor);
        try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
            scenario.onActivity(activity -> {
                ClipboardManager clipboard = (ClipboardManager) activity.getSystemService(Context.CLIPBOARD_SERVICE);
                clipboard.setPrimaryClip(ClipData.newPlainText("test", "unchanged"));
                activity.findViewById(R.id.support_view_qr).performClick();
                activity.findViewById(R.id.support_view_qr).performClick();
                assertEquals("unchanged", clipboard.getPrimaryClip().getItemAt(0).getText().toString());
            });
            instrumentation.waitForIdleSync();
            scenario.recreate();
            scenario.onActivity(activity -> {
                boolean enabled = activity.getResources().getBoolean(R.bool.support_external_enabled);
                if (enabled) {
                    SupportActivity.QrPreviewDialog dialog = preview(activity);
                    assertNotNull(dialog);
                    assertTrue(dialog.requireDialog().isShowing());
                    ImageView image = dialog.requireDialog().findViewById(R.id.support_qr_full_image);
                    assertNotNull(image.getDrawable());
                    assertTrue(image.getWidth() > 0 && image.getHeight() > 0);
                    dialog.requireDialog().findViewById(R.id.support_close_qr).performClick();
                } else {
                    assertNull(preview(activity));
                }
            });
            instrumentation.waitForIdleSync();
            scenario.onActivity(activity -> assertNull(preview(activity)));
            assertEquals("Viewing the QR must not launch a payment app or browser", 0, monitor.getHits());
        } finally {
            instrumentation.removeMonitor(monitor);
        }
    }

    @Test
    public void captureLocalizedLayouts() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        boolean enabled = instrumentation.getTargetContext().getResources()
                .getBoolean(R.bool.support_external_enabled);
        try {
            for (String language : new String[]{"en", "zh"}) {
                for (int orientation : new int[]{android.content.pm.ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                        android.content.pm.ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                    try (ActivityScenario<SupportActivity> scenario = ActivityScenario.launch(SupportActivity.class)) {
                        scenario.onActivity(activity -> {
                            androidx.appcompat.app.AppCompatDelegate.setApplicationLocales(
                                    androidx.core.os.LocaleListCompat.forLanguageTags(language));
                            activity.setRequestedOrientation(orientation);
                        });
                        instrumentation.waitForIdleSync();
                        android.os.SystemClock.sleep(600);
                        instrumentation.waitForIdleSync();
                        scenario.onActivity(activity -> assertEquals(
                                language.equals("zh") ? "通过支付宝支持" : "Support via Alipay",
                                activity.getString(R.string.support_alipay_title)));
                        String name = (enabled ? "alipay" : "disabled") + "-" + language + "-" + orientation;
                        capture(instrumentation, "support-" + name);
                        if (enabled) {
                            scenario.onActivity(activity -> activity.findViewById(R.id.support_view_qr).performClick());
                            instrumentation.waitForIdleSync();
                            android.os.SystemClock.sleep(300);
                            capture(instrumentation, "qr-" + name);
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

    private static void capture(Instrumentation instrumentation, String name) throws Exception {
        File output = new File(instrumentation.getTargetContext().getExternalFilesDir(null), name + ".png");
        Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
        assertNotNull(screenshot);
        try (FileOutputStream stream = new FileOutputStream(output)) {
            assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
        } finally {
            screenshot.recycle();
        }
    }
}
