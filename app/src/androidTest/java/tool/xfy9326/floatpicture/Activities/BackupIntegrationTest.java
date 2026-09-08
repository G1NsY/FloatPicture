package tool.xfy9326.floatpicture.Activities;

import android.app.Application;
import android.app.Instrumentation;
import android.app.Activity;
import android.content.ContentResolver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.SystemClock;
import android.provider.DocumentsContract;
import android.widget.TextView;
import androidx.test.core.app.ActivityScenario;
import androidx.test.ext.junit.runners.AndroidJUnit4;
import androidx.test.platform.app.InstrumentationRegistry;
import org.junit.*;
import org.junit.runner.RunWith;
import java.io.*;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Methods.BackupArchive;
import tool.xfy9326.floatpicture.Methods.BackupDataValidator;
import tool.xfy9326.floatpicture.Methods.BackupTestProvider;
import tool.xfy9326.floatpicture.Methods.LegacyDataImporter;
import static org.junit.Assert.*;

@RunWith(AndroidJUnit4.class)
public class BackupIntegrationTest {
    private File temp;
    private final Context context = InstrumentationRegistry.getInstrumentation().getTargetContext();
    private BackupActivity.BackupModel model;

    @Before public void before() throws Exception {
        temp = File.createTempFile("backup-test-", ".tmp", context.getCacheDir());
        assertTrue(temp.delete());
        assertTrue(temp.mkdir());
    }
    @After public void after() {
        if (model != null) {
            InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.onCleared());
            SystemClock.sleep(100);
        }
        BackupArchive.deleteTree(temp);
    }

    private File library() throws Exception {
        File root = new File(temp, "FloatPicture");
        write(root, "Data/PictureList.list", BackupTestProvider.NAMES.getBytes("UTF-8"));
        write(root, "Data/PictureData.list", BackupTestProvider.DETAILS.getBytes("UTF-8"));
        write(root, "Data/PictureOrder.list", "[\"photo_1\"]".getBytes("UTF-8"));
        write(root, "Pictures/photo_1", BackupTestProvider.png());
        write(root, "Pictures/photo_1.outline_source", BackupTestProvider.png());
        return root;
    }
    private static void write(File root, String path, byte[] bytes) throws Exception {
        File file = new File(root, path);
        file.getParentFile().mkdirs();
        try (OutputStream output = new FileOutputStream(file)) { output.write(bytes); }
    }
    private void createModel() {
        Application application = new Application() {
            @Override public File getFilesDir() { return temp; }
            @Override public ContentResolver getContentResolver() { return context.getContentResolver(); }
        };
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model = new BackupActivity.BackupModel(application));
    }
    private void waitFor(int phase) {
        long end = SystemClock.uptimeMillis() + 15000;
        while (SystemClock.uptimeMillis() < end && model.busy()) SystemClock.sleep(30);
        assertEquals(Integer.valueOf(phase), model.state.getValue());
    }

    @Test public void exportAndReadBackThroughContentResolverPreservesParameters() throws Exception {
        library();
        createModel();
        File backup = new File(temp, "saved.fpbackup.zip");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.start(Uri.fromFile(backup), false, false));
        waitFor(BackupActivity.BackupModel.EXPORTED);
        assertEquals(1, model.pictureCount);
        File restored = new File(temp, "restored");
        BackupArchive.extract(backup, restored);
        assertEquals(1, BackupDataValidator.validate(restored));
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.start(Uri.fromFile(backup), true, false));
        waitFor(BackupActivity.BackupModel.READY);
        assertEquals(1, model.pictureCount);
    }

    @Test public void missingImageIsRejectedBeforeReplacingData() throws Exception {
        File root = library();
        assertTrue(new File(root, "Pictures/photo_1").delete());
        assertThrows(IOException.class, () -> BackupDataValidator.validate(root));
    }
    @Test public void confirmedRestoreInstallsValidatedLibraryAndKeepsExactParameters() throws Exception {
        File root = library();
        File backup = new File(temp, "restore.zip");
        BackupArchive.write(root, backup);
        write(root, "Data/PictureData.list", "{\"photo_1\":{\"ALPHA\":0.9}}".getBytes("UTF-8"));
        createModel();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.start(Uri.fromFile(backup), true, false));
        waitFor(BackupActivity.BackupModel.READY);
        try (ActivityScenario<BackupActivity> scenario = ActivityScenario.launch(
                new Intent(context, BackupActivity.class))) {
            scenario.onActivity(activity -> model.install(activity));
            assertEquals(Integer.valueOf(BackupActivity.BackupModel.RESTORED), model.state.getValue());
            assertEquals(1, BackupDataValidator.validate(root));
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            try (InputStream input = new FileInputStream(new File(root, "Data/PictureData.list"))) {
                BackupArchive.copy(input, bytes, null, 4096);
            }
            assertEquals(BackupTestProvider.DETAILS, bytes.toString("UTF-8"));
        }
    }
    @Test public void invalidImageIsRejected() throws Exception {
        File root = library();
        write(root, "Pictures/photo_1", new byte[]{1,2,3});
        assertThrows(IOException.class, () -> BackupDataValidator.validate(root));
    }
    @Test public void invalidParametersAreRejected() throws Exception {
        File root = library();
        write(root, "Data/PictureData.list", "{\"photo_1\":{\"ALPHA\":4}}".getBytes("UTF-8"));
        assertThrows(IOException.class, () -> BackupDataValidator.validate(root));
    }
    @Test public void invalidOrderIsRejected() throws Exception {
        File root = library();
        write(root, "Data/PictureOrder.list", "[\"missing\"]".getBytes("UTF-8"));
        assertThrows(IOException.class, () -> BackupDataValidator.validate(root));
    }
    @Test public void legacyFolderAndItsParentBothImportWithoutTemporaryFiles() throws Exception {
        for (String id : new String[]{"root", "parent"}) {
            File stage = new File(temp, id);
            Uri uri = DocumentsContract.buildTreeDocumentUri(BackupTestProvider.AUTHORITY, id);
            LegacyDataImporter.stageFrom(context, uri, stage);
            assertEquals(1, BackupDataValidator.validate(stage));
            assertFalse(new File(stage, "Pictures/.TEMP").exists());
        }
    }
    @Test public void legacyImportUsesTheSamePreviewAndDoesNotReplaceCurrentLibrary() throws Exception {
        File root = library();
        createModel();
        Uri uri = DocumentsContract.buildTreeDocumentUri(BackupTestProvider.AUTHORITY, "root");
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.start(uri, true, true));
        waitFor(BackupActivity.BackupModel.READY);
        assertEquals(1, BackupDataValidator.validate(root));
    }
    @Test public void malformedBackupShowsFailureAndKeepsCurrentLibrary() throws Exception {
        File root = library();
        write(temp, "broken.zip", new byte[]{1,2,3});
        createModel();
        InstrumentationRegistry.getInstrumentation().runOnMainSync(() -> model.start(
                Uri.fromFile(new File(temp, "broken.zip")), true, false));
        waitFor(BackupActivity.BackupModel.ERROR);
        assertEquals(1, BackupDataValidator.validate(root));
    }
    @Test public void backupScreenRecreatesAndBackButtonWorks() throws Exception {
        try (ActivityScenario<BackupActivity> scenario = ActivityScenario.launch(
                new Intent(context, BackupActivity.class).putExtra(BackupActivity.EXTRA_RESTORE, true))) {
            scenario.recreate();
            scenario.onActivity(activity -> {
                assertEquals(activity.getString(R.string.backup_choose_file),
                        ((TextView) activity.findViewById(R.id.backup_action)).getText().toString());
                assertTrue(activity.onSupportNavigateUp());
                assertTrue(activity.isFinishing());
            });
        }
    }

    @Test public void exportOpensSystemSaveDialogAndCancellationLeavesDataUntouched() throws Exception {
        Instrumentation instrumentation = InstrumentationRegistry.getInstrumentation();
        try (ActivityScenario<BackupActivity> scenario = ActivityScenario.launch(
                new Intent(context, BackupActivity.class))) {
            IntentFilter saveFilter = new IntentFilter(Intent.ACTION_CREATE_DOCUMENT);
            saveFilter.addCategory(Intent.CATEGORY_OPENABLE);
            saveFilter.addDataType("application/zip");
            Instrumentation.ActivityMonitor monitor = instrumentation.addMonitor(
                    saveFilter,
                    new Instrumentation.ActivityResult(Activity.RESULT_CANCELED, null), true);
            try {
                scenario.onActivity(activity -> activity.findViewById(R.id.backup_action).performClick());
                instrumentation.waitForIdleSync();
                assertEquals(1, monitor.getHits());
                scenario.onActivity(activity -> {
                    assertTrue(activity.findViewById(R.id.backup_action).isEnabled());
                    assertEquals(activity.getString(R.string.backup_ready),
                            ((TextView) activity.findViewById(R.id.backup_status)).getText().toString());
                });
            } finally { instrumentation.removeMonitor(monitor); }
            Bitmap screenshot = instrumentation.getUiAutomation().takeScreenshot();
            try (OutputStream output = new FileOutputStream(new File(context.getExternalFilesDir(null),
                    "backup-export-screen.png"))) {
                assertNotNull(screenshot);
                assertTrue(screenshot.compress(Bitmap.CompressFormat.PNG, 100, output));
            } finally { if (screenshot != null) screenshot.recycle(); }
        }
    }
}
