package tool.xfy9326.floatpicture.Activities;

import android.app.Application;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.os.Build;
import android.provider.DocumentsContract;
import android.view.View;
import android.widget.Button;
import android.widget.TextView;
import androidx.activity.OnBackPressedCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.ViewModelProvider;
import java.io.*;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.BackupArchive;
import tool.xfy9326.floatpicture.Methods.BackupDataValidator;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.LegacyDataImporter;

public class BackupActivity extends AppCompatActivity {
    public static final String EXTRA_RESTORE = "restore";
    private BackupModel model;
    private boolean restore;
    private boolean legacyFolder;
    private boolean reloaded;
    private final ActivityResultLauncher<Intent> picker = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(), result -> {
                if (result.getResultCode() == RESULT_OK && result.getData() != null
                        && result.getData().getData() != null) {
                    model.start(result.getData().getData(), restore, legacyFolder);
                }
            });

    @Override
    protected void onCreate(@Nullable Bundle state) {
        super.onCreate(state);
        setContentView(R.layout.activity_backup);
        restore = getIntent().getBooleanExtra(EXTRA_RESTORE, false);
        legacyFolder = state != null && state.getBoolean("legacyFolder");
        setTitle(restore ? R.string.backup_restore : R.string.backup_export);
        ApplicationMethods.applyStatusBarTopInset(findViewById(R.id.backup_toolbar));
        ApplicationMethods.applyNavigationBarBottomInset(findViewById(R.id.backup_content));
        setSupportActionBar((Toolbar) findViewById(R.id.toolbar));
        if (getSupportActionBar() != null) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        ((TextView) findViewById(R.id.backup_description)).setText(
                restore ? R.string.backup_restore_description : R.string.backup_export_description);
        model = new ViewModelProvider(this).get(BackupModel.class);
        OnBackPressedCallback back = new OnBackPressedCallback(true) {
            @Override public void handleOnBackPressed() {
                if (!model.busy()) finish();
            }
        };
        getOnBackPressedDispatcher().addCallback(this, back);
        model.state.observe(this, this::render);
    }

    private void render(int phase) {
        Button action = findViewById(R.id.backup_action);
        TextView status = findViewById(R.id.backup_status);
        findViewById(R.id.backup_progress).setVisibility(model.busy() ? View.VISIBLE : View.GONE);
        action.setEnabled(!model.busy());
        if (phase == BackupModel.RESTORED) {
            if (!reloaded) {
                reloaded = true;
                startActivity(new Intent(this, MainActivity.class)
                        .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                finish();
            }
            return;
        }
        if (phase == BackupModel.READY) {
            status.setText(getString(R.string.backup_restore_confirm, model.pictureCount));
            action.setText(R.string.backup_restore_confirm_button);
            action.setOnClickListener(v -> model.install(this));
        } else if (phase == BackupModel.EXPORTED) {
            status.setText(getString(R.string.backup_export_success, model.pictureCount));
            action.setText(R.string.done);
            action.setOnClickListener(v -> finish());
        } else {
            status.setText(model.busy() ? R.string.backup_working
                    : phase == BackupModel.ERROR
                    ? (restore ? R.string.backup_restore_failed : R.string.backup_export_failed)
                    : R.string.backup_ready);
            action.setText(restore ? R.string.backup_choose_file : R.string.backup_choose_location);
            action.setOnClickListener(v -> chooseFile());
        }
    }

    private void chooseFile() {
        if (restore && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            new AlertDialog.Builder(this).setTitle(R.string.backup_choose_file)
                    .setItems(new String[]{getString(R.string.backup_source_zip),
                            getString(R.string.backup_source_folder)}, (dialog, choice) -> launchPicker(choice == 1))
                    .show();
        } else {
            launchPicker(false);
        }
    }

    private void launchPicker(boolean folder) {
        legacyFolder = folder;
        if (folder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            try { picker.launch(new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE)); }
            catch (ActivityNotFoundException exception) {
                ((TextView) findViewById(R.id.backup_status)).setText(R.string.backup_picker_unavailable);
            }
            return;
        }
        Intent intent = new Intent(restore ? Intent.ACTION_OPEN_DOCUMENT : Intent.ACTION_CREATE_DOCUMENT);
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        // Some document providers report ZIP backups as application/octet-stream.
        intent.setType(restore ? "*/*" : "application/zip");
        if (!restore) intent.putExtra(Intent.EXTRA_TITLE, "FloatPicture-"
                + new SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(new Date()) + ".fpbackup.zip");
        try { picker.launch(intent); }
        catch (ActivityNotFoundException exception) {
            ((TextView) findViewById(R.id.backup_status)).setText(R.string.backup_picker_unavailable);
        }
    }

    @Override public boolean onSupportNavigateUp() {
        if (!model.busy()) finish();
        return true;
    }

    @Override protected void onSaveInstanceState(@NonNull Bundle state) {
        state.putBoolean("legacyFolder", legacyFolder);
        super.onSaveInstanceState(state);
    }

    /** Keeps file operations alive across screen rotation without retaining the Activity. */
    public static class BackupModel extends AndroidViewModel {
        static final int IDLE = 0, RUNNING = 1, READY = 2, EXPORTED = 3, ERROR = 4, RESTORED = 5;
        final MutableLiveData<Integer> state = new MutableLiveData<>(IDLE);
        private final ExecutorService worker = Executors.newSingleThreadExecutor();
        private File work;
        private File stage;
        private volatile boolean cleared;
        int pictureCount;

        public BackupModel(@NonNull Application application) { super(application); }
        boolean busy() { return Integer.valueOf(RUNNING).equals(state.getValue()); }
        private File root() { return new File(getApplication().getFilesDir(), "FloatPicture"); }

        void start(Uri uri, boolean restore, boolean legacyFolder) {
            if (busy()) return;
            state.setValue(RUNNING);
            worker.execute(() -> {
                try {
                    cleanup();
                    work = File.createTempFile("backup-", ".work", getApplication().getFilesDir());
                    if (!work.delete() || !work.mkdir()) throw new IOException("Cannot create work directory");
                    File archive = new File(work, "backup.zip");
                    stage = new File(work, "library");
                    if (legacyFolder && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                        LegacyDataImporter.stageFrom(getApplication(), uri, stage);
                    } else if (restore) {
                        try (InputStream input = getApplication().getContentResolver().openInputStream(uri);
                             OutputStream output = new FileOutputStream(archive)) {
                            if (input == null) throw new IOException("Cannot open backup");
                            BackupArchive.copy(input, output, null, BackupArchive.MAX_BYTES + 16 * 1024 * 1024);
                        }
                    } else {
                        BackupArchive.write(root(), archive);
                    }
                    if (!legacyFolder) BackupArchive.extract(archive, stage);
                    pictureCount = BackupDataValidator.validate(stage);
                    if (restore) {
                        if (cleared) cleanup(); else state.postValue(READY);
                    } else {
                        try (InputStream input = new FileInputStream(archive);
                             OutputStream output = getApplication().getContentResolver().openOutputStream(uri, "wt")) {
                            if (output == null) throw new IOException("Cannot create backup");
                            BackupArchive.copy(input, output, null, BackupArchive.MAX_BYTES + 16 * 1024 * 1024);
                        }
                        cleanup();
                        state.postValue(EXPORTED);
                    }
                } catch (Exception exception) {
                    exception.printStackTrace();
                    cleanup();
                    if (!restore) {
                        try { DocumentsContract.deleteDocument(getApplication().getContentResolver(), uri); }
                        catch (Exception ignored) { /* Provider may leave an incomplete file. */ }
                    }
                    state.postValue(ERROR);
                }
            });
        }

        void install(BackupActivity activity) {
            if (!Integer.valueOf(READY).equals(state.getValue())) return;
            state.setValue(RUNNING);
            // Renames run on the UI thread so floating-control writes cannot interleave.
            ManageMethods.prepareForDataReload(activity);
            try {
                BackupArchive.install(stage, root());
                // Move cleanup to this operation's unique directory before starting a worker.
                // A later restore must never race with deletion of the shared rollback path.
                File previous = BackupArchive.rollbackDirectory(root());
                if (previous.exists()) {
                    //noinspection ResultOfMethodCallIgnored
                    previous.renameTo(new File(work, "previous-library"));
                }
                state.setValue(RESTORED);
                worker.execute(this::cleanup);
            } catch (IOException exception) {
                exception.printStackTrace();
                ManageMethods.ensureWindowsInitialized(activity);
                state.setValue(ERROR);
            }
        }

        private void cleanup() {
            if (work != null) BackupArchive.deleteTree(work);
            work = null;
            stage = null;
        }

        @Override protected void onCleared() {
            cleared = true;
            worker.execute(this::cleanup);
            worker.shutdown();
        }
    }
}
