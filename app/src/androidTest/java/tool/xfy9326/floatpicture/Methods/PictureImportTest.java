package tool.xfy9326.floatpicture.Methods;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.graphics.Bitmap;
import android.net.Uri;
import android.app.Instrumentation;
import android.os.SystemClock;

import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.List;
import java.lang.reflect.Field;

import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;
import tool.xfy9326.floatpicture.View.FloatImageView;

import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class PictureImportTest {
    private Context context() {
        return InstrumentationRegistry.getInstrumentation().getTargetContext();
    }

    private String expectedHash() throws Exception {
        StringBuilder hex = new StringBuilder();
        for (byte value : MessageDigest.getInstance("MD5").digest(ImportTestProvider.png())) {
            hex.append(String.format(java.util.Locale.ROOT, "%02x", value & 0xff));
        }
        return hex.toString();
    }

    @Test public void hashesNonSeekableGalleryPipe() throws Exception {
        assertEquals(expectedHash(), CodeMethods.getFileMD5String(context(), ImportTestProvider.uri("pipe")));
    }

    @Test public void hashesOnlyTheSelectedFileSlice() throws Exception {
        assertEquals(expectedHash(), CodeMethods.getFileMD5String(context(), ImportTestProvider.uri("slice")));
    }

    @Test public void decodesPipeAndSliceWithoutChangingScreenshotSize() {
        for (String kind : new String[]{"pipe", "slice"}) {
            Bitmap bitmap = IOMethods.readImageByUri(context(), ImportTestProvider.uri(kind));
            assertNotNull(bitmap);
            assertEquals(12, bitmap.getWidth());
            assertEquals(8, bitmap.getHeight());
            bitmap.recycle();
        }
    }

    @Test public void largeCameraJpegIsSampledAndExifRotationIsApplied() {
        Bitmap bitmap = IOMethods.readImageByUri(context(), ImportTestProvider.uri("photo"));
        assertNotNull(bitmap);
        assertEquals(50, bitmap.getWidth());
        assertEquals(2400, bitmap.getHeight());
        bitmap.recycle();
    }

    @Test public void invalidAndInaccessibleImagesFailCleanlyAndRemoveStagingFiles() {
        assertNull(IOMethods.readImageByUri(context(), ImportTestProvider.uri("invalid")));
        assertNull(IOMethods.readImageByUri(context(), ImportTestProvider.uri("denied")));
        assertNull(IOMethods.readImageByUri(context(), null));
        assertNull(CodeMethods.getFileMD5String(context(), ImportTestProvider.uri("denied")));
        File[] remaining = context().getCacheDir().listFiles(
                (dir, name) -> name.startsWith("picture-import-"));
        assertNotNull(remaining);
        assertEquals(0, remaining.length);
    }

    @Test public void highResolutionPhotosRespectHeapAndTextureLimits() {
        for (long heap : new long[]{64L << 20, 256L << 20, 1L << 30}) {
            int sample = IOMethods.importSampleSize(7296, 5472, heap);
            long width = (7296L + sample - 1) / sample;
            long height = (5472L + sample - 1) / sample;
            assertTrue(width <= 4096 && height <= 4096);
            assertTrue(width * height <= Math.min(8_000_000L, heap / 32L));
        }
    }

    @Test public void acceptsClipOnlyResultsAndRejectsEmptyResults() {
        Uri uri = ImportTestProvider.uri("pipe");
        Intent result = new Intent();
        result.setClipData(ClipData.newRawUri("picture", uri));
        assertEquals(uri, PicturePicker.getSelectedUri(result));
        Uri direct = ImportTestProvider.uri("photo");
        result.setData(direct);
        assertEquals(direct, PicturePicker.getSelectedUri(result));
        assertNull(PicturePicker.getSelectedUri(new Intent()));
        assertNull(PicturePicker.getSelectedUri(null));
    }

    @Test public void fallsBackWhenGalleryPickerIsUnavailable() {
        List<String> actions = new ArrayList<>();
        PicturePicker.launch(context(), intent -> {
            actions.add(intent.getAction());
            assertEquals("image/*", intent.getType());
            if (Intent.ACTION_GET_CONTENT.equals(intent.getAction())) {
                throw new ActivityNotFoundException();
            }
        });
        assertEquals(java.util.Arrays.asList(Intent.ACTION_GET_CONTENT,
                Intent.ACTION_OPEN_DOCUMENT), actions);
    }

    @Test public void photoReachesEditorAndExitToleratesMissingBitmap() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        PictureSettingsActivity activity = (PictureSettingsActivity) instrumentation.startActivitySync(
                new Intent(context(), PictureSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setData(ImportTestProvider.uri("photo")));
        Field bitmapField = PictureSettingsFragment.class.getDeclaredField("bitmap");
        Field viewField = PictureSettingsFragment.class.getDeclaredField("floatImageView");
        bitmapField.setAccessible(true);
        viewField.setAccessible(true);
        boolean[] ready = {false};
        try {
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (!ready[0] && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> {
                    try {
                        PictureSettingsFragment fragment = (PictureSettingsFragment) activity
                                .getSupportFragmentManager().findFragmentById(R.id.layout_picture_settings_content);
                        if (fragment == null) return;
                        FloatImageView view = (FloatImageView) viewField.get(fragment);
                        if (view == null || !view.isAttachedToWindow()) return;
                        Bitmap bitmap = (Bitmap) bitmapField.get(fragment);
                        assertNotNull(bitmap);
                        assertEquals(50, bitmap.getWidth());
                        assertEquals(2400, bitmap.getHeight());
                        // Reproduce the screenshot's missing-bitmap cleanup state.
                        bitmapField.set(fragment, null);
                        fragment.exit();
                        bitmap.recycle();
                        ready[0] = true;
                    } catch (IllegalAccessException exception) {
                        throw new AssertionError(exception);
                    }
                });
                if (!ready[0]) SystemClock.sleep(100);
            }
            assertTrue("Photo did not reach the editor", ready[0]);
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }

    @Test public void invalidPhotoClosesEditorWithoutCreatingAnEntry() {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        int before = new tool.xfy9326.floatpicture.Utils.PictureData().getListArray().size();
        PictureSettingsActivity activity = (PictureSettingsActivity) instrumentation.startActivitySync(
                new Intent(context(), PictureSettingsActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                        .setData(ImportTestProvider.uri("invalid")));
        boolean[] finished = {false};
        try {
            long deadline = SystemClock.uptimeMillis() + 10000;
            while (!finished[0] && SystemClock.uptimeMillis() < deadline) {
                instrumentation.runOnMainSync(() -> finished[0] = activity.isFinishing());
                if (!finished[0]) SystemClock.sleep(100);
            }
            assertTrue("Failed import left the editor open", finished[0]);
            assertEquals(before, new tool.xfy9326.floatpicture.Utils.PictureData().getListArray().size());
        } finally {
            instrumentation.runOnMainSync(activity::finish);
        }
    }
}
