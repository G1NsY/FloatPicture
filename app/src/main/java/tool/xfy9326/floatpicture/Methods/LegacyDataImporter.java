package tool.xfy9326.floatpicture.Methods;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.StringRes;
import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public final class LegacyDataImporter {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_FILES = 10_000;
    private static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;

    private LegacyDataImporter() {
    }

    public static Result importFrom(Context context, Uri treeUri) {
        File stagingRoot = new File(context.getCacheDir(), "legacy_import_staging");
        File backupRoot = new File(context.getCacheDir(), "legacy_import_backup");
        deleteRecursively(stagingRoot);
        deleteRecursively(backupRoot);

        try {
            if (!stagingRoot.mkdirs()) {
                return Result.failure(R.string.legacy_import_failed_copy);
            }

            Uri selectedRoot = DocumentsContract.buildDocumentUriUsingTree(
                    treeUri, DocumentsContract.getTreeDocumentId(treeUri));
            List<DocumentEntry> rootEntries = listChildren(context, treeUri, selectedRoot);
            DocumentEntry dataDirectory = findDirectory(rootEntries, "Data");
            DocumentEntry picturesDirectory = findDirectory(rootEntries, "Pictures");

            if (dataDirectory == null || picturesDirectory == null) {
                DocumentEntry floatPictureDirectory = findDirectory(rootEntries, "FloatPicture");
                if (floatPictureDirectory != null) {
                    rootEntries = listChildren(context, treeUri, floatPictureDirectory.uri);
                    dataDirectory = findDirectory(rootEntries, "Data");
                    picturesDirectory = findDirectory(rootEntries, "Pictures");
                }
            }

            if (dataDirectory == null || picturesDirectory == null) {
                return Result.failure(R.string.legacy_import_invalid_folder);
            }

            ImportCounter counter = new ImportCounter();
            copyDirectory(context, treeUri, dataDirectory.uri,
                    new File(stagingRoot, "Data"), 0, counter);
            copyDirectory(context, treeUri, picturesDirectory.uri,
                    new File(stagingRoot, "Pictures"), 0, counter);

            File stagedData = new File(stagingRoot, "Data");
            if (!new File(stagedData, "PictureList.list").isFile()
                    || !new File(stagedData, "PictureData.list").isFile()) {
                return Result.failure(R.string.legacy_import_invalid_data);
            }

            if (!replaceCurrentData(stagingRoot, backupRoot)) {
                return Result.failure(R.string.legacy_import_failed_replace);
            }
            return Result.success(counter.fileCount);
        } catch (Exception exception) {
            exception.printStackTrace();
            return Result.failure(R.string.legacy_import_failed_copy);
        } finally {
            deleteRecursively(stagingRoot);
            deleteRecursively(backupRoot);
        }
    }

    private static List<DocumentEntry> listChildren(
            Context context, Uri treeUri, Uri directoryUri) throws IOException {
        List<DocumentEntry> result = new ArrayList<>();
        Uri childrenUri = DocumentsContract.buildChildDocumentsUriUsingTree(
                treeUri, DocumentsContract.getDocumentId(directoryUri));
        String[] projection = new String[]{
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,
                DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE
        };
        try (Cursor cursor = context.getContentResolver().query(
                childrenUri, projection, null, null, null)) {
            if (cursor == null) {
                throw new IOException("Unable to read selected directory");
            }
            while (cursor.moveToNext()) {
                String documentId = cursor.getString(0);
                String name = cursor.getString(1);
                String mimeType = cursor.getString(2);
                if (!isSafeName(name)) {
                    throw new IOException("Unsafe document name");
                }
                result.add(new DocumentEntry(
                        DocumentsContract.buildDocumentUriUsingTree(treeUri, documentId),
                        name,
                        DocumentsContract.Document.MIME_TYPE_DIR.equals(mimeType)));
            }
        }
        return result;
    }

    private static void copyDirectory(
            Context context,
            Uri treeUri,
            Uri sourceDirectory,
            File targetDirectory,
            int depth,
            ImportCounter counter) throws IOException {
        if (depth > MAX_DEPTH || (!targetDirectory.isDirectory() && !targetDirectory.mkdirs())) {
            throw new IOException("Import directory limit exceeded");
        }
        for (DocumentEntry child : listChildren(context, treeUri, sourceDirectory)) {
            File target = new File(targetDirectory, child.name);
            if (child.directory) {
                copyDirectory(context, treeUri, child.uri, target, depth + 1, counter);
            } else {
                copyFile(context.getContentResolver(), child.uri, target, counter);
            }
        }
    }

    private static void copyFile(
            ContentResolver resolver, Uri source, File target, ImportCounter counter)
            throws IOException {
        counter.fileCount++;
        if (counter.fileCount > MAX_FILES) {
            throw new IOException("Import file limit exceeded");
        }
        try (InputStream inputStream = resolver.openInputStream(source);
             OutputStream outputStream = new FileOutputStream(target)) {
            if (inputStream == null) {
                throw new IOException("Unable to open source file");
            }
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                counter.totalBytes += count;
                if (counter.totalBytes > MAX_TOTAL_BYTES) {
                    throw new IOException("Import size limit exceeded");
                }
                outputStream.write(buffer, 0, count);
            }
            outputStream.flush();
        }
    }

    private static boolean replaceCurrentData(File stagingRoot, File backupRoot) {
        File destinationRoot = Objects.requireNonNull(
                new File(Config.DEFAULT_PICTURE_DIR).getParentFile());
        File destinationData = new File(destinationRoot, "Data");
        File destinationPictures = new File(destinationRoot, "Pictures");
        File stagedData = new File(stagingRoot, "Data");
        File stagedPictures = new File(stagingRoot, "Pictures");
        File backupData = new File(backupRoot, "Data");
        File backupPictures = new File(backupRoot, "Pictures");

        if ((!destinationRoot.isDirectory() && !destinationRoot.mkdirs())
                || !backupRoot.mkdirs()) {
            return false;
        }

        boolean backedUpData = !destinationData.exists() || destinationData.renameTo(backupData);
        boolean backedUpPictures = !destinationPictures.exists()
                || destinationPictures.renameTo(backupPictures);
        if (!backedUpData || !backedUpPictures) {
            restoreBackup(destinationData, backupData);
            restoreBackup(destinationPictures, backupPictures);
            return false;
        }

        boolean installedData = stagedData.renameTo(destinationData);
        boolean installedPictures = stagedPictures.renameTo(destinationPictures);
        if (!installedData || !installedPictures) {
            deleteRecursively(destinationData);
            deleteRecursively(destinationPictures);
            restoreBackup(destinationData, backupData);
            restoreBackup(destinationPictures, backupPictures);
            return false;
        }
        IOMethods.setNoMedia();
        return true;
    }

    private static void restoreBackup(File destination, File backup) {
        if (backup.exists() && !destination.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backup.renameTo(destination);
        }
    }

    private static DocumentEntry findDirectory(List<DocumentEntry> entries, String name) {
        for (DocumentEntry entry : entries) {
            if (entry.directory && name.equals(entry.name)) {
                return entry;
            }
        }
        return null;
    }

    private static boolean isSafeName(String name) {
        return name != null
                && !name.isEmpty()
                && !".".equals(name)
                && !"..".equals(name)
                && name.indexOf('/') < 0
                && name.indexOf('\\') < 0
                && name.indexOf('\0') < 0;
    }

    private static void deleteRecursively(File file) {
        if (file == null || !file.exists()) {
            return;
        }
        if (file.isDirectory()) {
            File[] children = file.listFiles();
            if (children != null) {
                for (File child : children) {
                    deleteRecursively(child);
                }
            }
        }
        //noinspection ResultOfMethodCallIgnored
        file.delete();
    }

    private static final class DocumentEntry {
        private final Uri uri;
        private final String name;
        private final boolean directory;

        private DocumentEntry(Uri uri, String name, boolean directory) {
            this.uri = uri;
            this.name = name;
            this.directory = directory;
        }
    }

    private static final class ImportCounter {
        private int fileCount;
        private long totalBytes;
    }

    public static final class Result {
        private final boolean successful;
        @StringRes
        private final int messageResource;
        private final int importedFileCount;

        private Result(boolean successful, int messageResource, int importedFileCount) {
            this.successful = successful;
            this.messageResource = messageResource;
            this.importedFileCount = importedFileCount;
        }

        private static Result success(int importedFileCount) {
            return new Result(true, R.string.legacy_import_success, importedFileCount);
        }

        private static Result failure(@StringRes int messageResource) {
            return new Result(false, messageResource, 0);
        }

        public boolean isSuccessful() {
            return successful;
        }

        @StringRes
        public int getMessageResource() {
            return messageResource;
        }

        public int getImportedFileCount() {
            return importedFileCount;
        }
    }
}
