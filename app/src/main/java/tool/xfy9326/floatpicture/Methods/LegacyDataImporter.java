package tool.xfy9326.floatpicture.Methods;

import android.content.ContentResolver;
import android.content.Context;
import android.database.Cursor;
import android.net.Uri;
import android.os.Build;
import android.provider.DocumentsContract;

import androidx.annotation.RequiresApi;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;


@RequiresApi(Build.VERSION_CODES.LOLLIPOP)
public final class LegacyDataImporter {
    private static final int MAX_DEPTH = 8;
    private static final int MAX_FILES = 10_000;
    private static final long MAX_TOTAL_BYTES = 2L * 1024L * 1024L * 1024L;

    private LegacyDataImporter() {
    }

    /** Copy only into staging; the shared restore flow validates and installs it later. */
    public static void stageFrom(Context context, Uri treeUri, File stagingRoot) throws IOException {
        if (stagingRoot.exists() || !stagingRoot.mkdirs()) {
            throw new IOException("Cannot create staging directory");
        }
        Uri selectedRoot = DocumentsContract.buildDocumentUriUsingTree(
                treeUri, DocumentsContract.getTreeDocumentId(treeUri));
        List<DocumentEntry> entries = listChildren(context, treeUri, selectedRoot);
        DocumentEntry data = findDirectory(entries, "Data");
        DocumentEntry pictures = findDirectory(entries, "Pictures");
        if (data == null || pictures == null) {
            DocumentEntry folder = findDirectory(entries, "FloatPicture");
            if (folder != null) {
                entries = listChildren(context, treeUri, folder.uri);
                data = findDirectory(entries, "Data");
                pictures = findDirectory(entries, "Pictures");
            }
        }
        if (data == null || pictures == null) throw new IOException("Invalid legacy folder");
        ImportCounter counter = new ImportCounter();
        copyDirectory(context, treeUri, data.uri, new File(stagingRoot, "Data"), 0, counter);
        copyDirectory(context, treeUri, pictures.uri, new File(stagingRoot, "Pictures"), 0, counter);
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
            if (child.name.equals(".TEMP") || child.name.equals(".nomedia")) continue;
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

}
