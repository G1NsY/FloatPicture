package tool.xfy9326.floatpicture.Methods;

import android.app.Instrumentation;
import android.content.Context;
import android.content.ContextWrapper;
import android.content.SharedPreferences;
import android.graphics.Bitmap;
import android.graphics.Rect;
import android.os.SystemClock;
import android.util.Log;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.filters.SdkSuppress;
import androidx.test.platform.app.InstrumentationRegistry;

import org.junit.After;
import org.junit.Before;
import org.junit.Test;
import org.junit.runner.RunWith;

import java.io.File;
import java.io.FileOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;
import tool.xfy9326.floatpicture.View.FloatImageView;

import static org.junit.Assert.*;

/** Exercises real imported windows in a temporary library, leaving the saved library intact. */
@RunWith(AndroidJUnit4.class)
@SdkSuppress(minSdkVersion = 30)
public class ImportedPicturePositionTest {
    private final Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
    private final Context context = instrumentation.getTargetContext();
    private File temp;
    private SharedPreferences preferences;
    private Boolean previousMultiple;
    private boolean redirected;

    @Before public void setUp() throws Exception {
        assertTrue("Overlay permission is required", PermissionMethods.canDrawOverlays(context));
        assertTrue("Run without a loaded UI library",
                ((MainApplication) context.getApplicationContext()).getRegister().isEmpty());
        temp = File.createTempFile("import-position-", ".tmp", context.getCacheDir());
        assertTrue(temp.delete());
        assertTrue(temp.mkdir());
        File stage = new File(temp, "stage");
        String suppliedArchive = InstrumentationRegistry.getArguments().getString("positionBackup");
        File archive;
        if (suppliedArchive != null) {
            archive = new File(suppliedArchive);
            assertTrue("Missing supplied backup", archive.isFile());
        } else {
            // Large rotated image with nonuniform scaling and a saved positive offset.
            File source = new File(temp, "source");
            write(source, "Data/PictureList.list", "{\"probe\":\"Position regression\"}");
            write(source, "Data/PictureOrder.list", "[\"probe\"]");
            write(source, "Data/PictureData.list", "{\"probe\":{\"SHOW_ENABLED\":false,"
                    + "\"ZOOM_X\":1.2,\"ZOOM_Y\":1,\"DEGREE\":90,"
                    + "\"POSITION_X\":100,\"POSITION_Y\":132,"
                    + "\"ALLOW_PICTURE_OVER_LAYOUT\":false}}");
            File picture = new File(source, "Pictures/probe");
            assertTrue(picture.getParentFile().mkdirs());
            Bitmap bitmap = Bitmap.createBitmap(2200, 1200, Bitmap.Config.ARGB_8888);
            bitmap.eraseColor(0xff557799);
            try (FileOutputStream output = new FileOutputStream(picture)) {
                assertTrue(bitmap.compress(Bitmap.CompressFormat.PNG, 100, output));
            } finally {
                bitmap.recycle();
            }
            archive = new File(temp, "fixture.fpbackup.zip");
            BackupArchive.write(source, archive);
        }
        BackupArchive.extract(archive, stage);
        assertTrue(BackupDataValidator.validate(stage) > 0);
        BackupArchive.install(stage, new File(temp, "FloatPicture"));
        preferences = PreferenceManager.getDefaultSharedPreferences(context);
        String key = Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES;
        previousMultiple = preferences.contains(key) ? preferences.getBoolean(key, false) : null;
        Config.initialize(new ContextWrapper(context) {
            @Override public File getFilesDir() { return temp; }
        });
        redirected = true;
    }

    @After public void tearDown() {
        if (redirected) {
            instrumentation.runOnMainSync(() -> ManageMethods.prepareForDataReload(context));
            Config.initialize(context);
            SharedPreferences.Editor editor = preferences.edit();
            String key = Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES;
            if (previousMultiple == null) editor.remove(key);
            else editor.putBoolean(key, previousMultiple);
            editor.commit();
        }
        if (temp != null) BackupArchive.deleteTree(temp);
    }

    @Test public void hiddenImportFirstDisplayMatchesReopen() {
        verify(false, false, false);
    }

    @Test public void multiplePictureImportFirstDisplayMatchesReopen() {
        verify(false, true, false);
    }

    @Test public void overflowImportPreservesSavedPositionImmediately() {
        verify(true, true, false);
    }

    @Test public void visibleImportMatchesReopen() {
        verify(false, true, true);
    }

    private void verify(boolean overflow, boolean multiple, boolean initiallyVisible) {
        preferences.edit().putBoolean(Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES, multiple).commit();
        PictureData data = new PictureData();
        List<String> ids = new ArrayList<>(data.getListArray().keySet());
        for (String id : ids) {
            data.setDataControl(id);
            data.put(Config.DATA_PICTURE_SHOW_ENABLED, initiallyVisible);
            data.put(Config.DATA_ALLOW_PICTURE_OVER_LAYOUT, overflow);
            data.commit(null);
        }
        instrumentation.runOnMainSync(() -> ManageMethods.RunWin(context));
        settle();
        Rect display = WindowsMethods.getWindowManager(context).getCurrentWindowMetrics().getBounds();
        List<String> failures = new ArrayList<>();
        for (String id : ids) {
            data.setDataControl(id);
            int savedX = data.getInt(Config.DATA_PICTURE_POSITION_X, 100);
            int savedY = data.getInt(Config.DATA_PICTURE_POSITION_Y, 100);
            FloatImageView view = ImageMethods.getFloatImageViewById(context, id);
            assertNotNull(view);
            if (!initiallyVisible) {
                assertNull("Hidden imports have never had a window", view.getLayoutParams());
                show(id, true);
            }
            Rect first = bounds(view);
            int width = view.getRenderedImageWidth();
            int height = view.getRenderedImageHeight();
            int expectedX = overflow ? savedX : constrain(savedX, display.width(), width);
            int expectedY = overflow ? savedY : constrain(savedY, display.height(), height);
            WindowManager.LayoutParams params = (WindowManager.LayoutParams) view.getLayoutParams();
            if (params.x != expectedX || params.y != expectedY
                    || params.width != width || params.height != height) {
                failures.add(id + " first layout=" + params.x + "," + params.y
                        + " " + params.width + "x" + params.height
                        + " expected=" + expectedX + "," + expectedY + " " + width + "x" + height);
            }
            show(id, false);
            show(id, true);
            Rect reopened = bounds(view);
            Log.i("ImportPositionTest", id + " overflow=" + overflow + " multiple=" + multiple
                    + " first=" + first + " reopened=" + reopened);
            if (!first.equals(reopened)) failures.add(id + " first=" + first + " reopened=" + reopened);
            data.setDataControl(id);
            assertEquals("Display must not rewrite saved X", savedX, data.getInt(Config.DATA_PICTURE_POSITION_X, 100));
            assertEquals("Display must not rewrite saved Y", savedY, data.getInt(Config.DATA_PICTURE_POSITION_Y, 100));
            show(id, false);
        }
        assertTrue(failures.toString(), failures.isEmpty());
    }

    private void show(String id, boolean visible) {
        instrumentation.runOnMainSync(() -> ManageMethods.setWindowVisible(context, new PictureData(), id, visible));
        settle();
    }

    private void settle() {
        instrumentation.waitForIdleSync();
        SystemClock.sleep(180);
    }

    private Rect bounds(FloatImageView view) {
        Rect result = new Rect();
        instrumentation.runOnMainSync(() -> {
            assertTrue(view.isAttachedToWindow());
            int[] location = new int[2];
            view.getLocationOnScreen(location);
            result.set(location[0], location[1], location[0] + view.getWidth(), location[1] + view.getHeight());
        });
        return result;
    }

    private static int constrain(int coordinate, int screen, int image) {
        return Math.max(Math.min(0, screen - image), Math.min(coordinate, Math.max(0, screen - image)));
    }

    private static void write(File root, String path, String contents) throws Exception {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        try (FileOutputStream output = new FileOutputStream(file)) {
            output.write(contents.getBytes(StandardCharsets.UTF_8));
        }
    }
}
