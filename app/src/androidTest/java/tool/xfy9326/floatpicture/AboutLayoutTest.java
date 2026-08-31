package tool.xfy9326.floatpicture;

import android.app.Instrumentation;
import android.content.pm.ActivityInfo;
import android.content.res.Configuration;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ScrollView;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.os.LocaleListCompat;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;

import tool.xfy9326.floatpicture.Activities.AboutActivity;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class AboutLayoutTest {
    @Test
    public void englishAboutSurvivesRotationAndRecreation() throws Exception {
        exerciseLayouts("en");
    }

    @Test
    public void chineseAboutSurvivesRotationAndRecreation() throws Exception {
        exerciseLayouts("zh");
    }

    private void exerciseLayouts(String language) throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        LocaleListCompat previous = AppCompatDelegate.getApplicationLocales();
        try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
            scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(
                    LocaleListCompat.forLanguageTags(language)));
            for (int orientation : new int[]{ActivityInfo.SCREEN_ORIENTATION_PORTRAIT,
                    ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE}) {
                scenario.onActivity(activity -> activity.setRequestedOrientation(orientation));
                instrumentation.waitForIdleSync();
                SystemClock.sleep(600);
                scenario.recreate();
                instrumentation.waitForIdleSync();
                scenario.onActivity(activity -> {
                    assertEquals(orientation == ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE
                                    ? Configuration.ORIENTATION_LANDSCAPE : Configuration.ORIENTATION_PORTRAIT,
                            activity.getResources().getConfiguration().orientation);
                    assertEquals(language.equals("zh") ? "悬浮图片2" : "FloatPicture 2",
                            activity.getString(R.string.app_name));
                    assertNotNull(activity.findViewById(R.id.layout_about_toolbar_container));
                    View content = activity.findViewById(R.id.layout_about_content);
                    assertNotNull(content);
                    assertTrue(content.getHeight() > 0);
                    assertEquals(BuildConfig.VERSION_NAME,
                            ((TextView) activity.findViewById(R.id.textview_about_version))
                                    .getText().toString());
                    assertNotNull(activity.findViewById(R.id.textview_about_open_source));
                    View firstChild = ((ViewGroup) content).getChildAt(0);
                    if (firstChild instanceof ScrollView) ((ScrollView) firstChild).fullScroll(View.FOCUS_DOWN);
                });
                instrumentation.waitForIdleSync();
                SystemClock.sleep(300);
                scenario.onActivity(activity -> {
                    View website = activity.findViewById(R.id.textview_about_website);
                    Rect visible = new Rect();
                    assertTrue("Website must be reachable", website.getGlobalVisibleRect(visible));
                    assertEquals(website.getHeight(), visible.height());
                });
                Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
                assertNotNull(screenshot);
                File output = new File(instrumentation.getTargetContext().getExternalFilesDir(null),
                        "about-fixed-" + language + "-" + orientation + ".png");
                try (FileOutputStream stream = new FileOutputStream(output)) {
                    assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, stream));
                } finally {
                    screenshot.recycle();
                }
            }
        } finally {
            try (ActivityScenario<AboutActivity> scenario = ActivityScenario.launch(AboutActivity.class)) {
                scenario.onActivity(activity -> AppCompatDelegate.setApplicationLocales(previous));
            }
        }
    }
}
