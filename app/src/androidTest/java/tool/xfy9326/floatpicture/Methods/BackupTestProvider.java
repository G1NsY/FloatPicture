package tool.xfy9326.floatpicture.Methods;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.database.Cursor;
import android.database.MatrixCursor;
import android.graphics.Bitmap;
import android.net.Uri;
import android.os.ParcelFileDescriptor;
import android.provider.DocumentsContract;
import java.io.*;

/** Test-only legacy document tree, including temporary images that must not be migrated. */
public class BackupTestProvider extends ContentProvider {
    public static final String AUTHORITY = "tool.g1nsy.floatpicture.test.backup";
    public static final String NAMES = "{\"photo_1\":\"参考图\"}";
    public static final String DETAILS = "{\"photo_1\":{\"POSITION_X\":-17,\"POSITION_Y\":321,"
            + "\"ZOOM_X\":1.234,\"ZOOM_Y\":0.987,\"DEGREE\":12.34,\"ALPHA\":0.42}}";
    public static byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        return bytes.toByteArray();
    }
    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "application/octet-stream"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection, String[] args, String sort) {
        MatrixCursor cursor = new MatrixCursor(projection);
        String id = DocumentsContract.getDocumentId(uri);
        String dir = DocumentsContract.Document.MIME_TYPE_DIR;
        if (id.equals("parent")) cursor.addRow(new Object[]{"root", "FloatPicture", dir});
        if (id.equals("root")) {
            cursor.addRow(new Object[]{"data", "Data", dir});
            cursor.addRow(new Object[]{"pictures", "Pictures", dir});
        }
        if (id.equals("data")) {
            cursor.addRow(new Object[]{"names", "PictureList.list", "application/octet-stream"});
            cursor.addRow(new Object[]{"details", "PictureData.list", "application/octet-stream"});
        }
        if (id.equals("pictures")) {
            cursor.addRow(new Object[]{"image", "photo_1", "image/png"});
            cursor.addRow(new Object[]{"original", "photo_1.outline_source", "image/png"});
            cursor.addRow(new Object[]{"temp", ".TEMP", dir});
        }
        return cursor;
    }
    @Override public ParcelFileDescriptor openFile(Uri uri, String mode) throws FileNotFoundException {
        String id = DocumentsContract.getDocumentId(uri);
        try {
            byte[] payload = id.equals("names") ? NAMES.getBytes("UTF-8")
                    : id.equals("details") ? DETAILS.getBytes("UTF-8") : png();
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(payload);
                } catch (IOException exception) { throw new AssertionError(exception); }
            }).start();
            return pipe[0];
        } catch (IOException exception) { throw new FileNotFoundException(exception.toString()); }
    }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }
}
