package tool.xfy9326.floatpicture.Methods;

import android.content.ContentProvider;
import android.content.ContentValues;
import android.content.res.AssetFileDescriptor;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.net.Uri;
import android.os.ParcelFileDescriptor;

import android.media.ExifInterface;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.OutputStream;

/** Test-only provider exercising gallery streams, file slices and camera metadata. */
public class ImportTestProvider extends ContentProvider {
    static Uri uri(String kind) {
        return Uri.parse("content://tool.g1nsy.floatpicture.test.import/" + kind);
    }

    static byte[] png() {
        Bitmap bitmap = Bitmap.createBitmap(12, 8, Bitmap.Config.ARGB_8888);
        bitmap.eraseColor(Color.RED);
        ByteArrayOutputStream bytes = new ByteArrayOutputStream();
        bitmap.compress(Bitmap.CompressFormat.PNG, 100, bytes);
        bitmap.recycle();
        return bytes.toByteArray();
    }

    @Override public boolean onCreate() { return true; }
    @Override public String getType(Uri uri) { return "image/png"; }
    @Override public Cursor query(Uri uri, String[] projection, String selection,
                                  String[] args, String sort) { return null; }
    @Override public Uri insert(Uri uri, ContentValues values) { return null; }
    @Override public int delete(Uri uri, String selection, String[] args) { return 0; }
    @Override public int update(Uri uri, ContentValues values, String selection, String[] args) { return 0; }

    @Override public AssetFileDescriptor openAssetFile(Uri uri, String mode)
            throws FileNotFoundException {
        if ("denied".equals(uri.getLastPathSegment())) throw new SecurityException("Test denied URI");
        try {
            byte[] bytes = png();
            if ("slice".equals(uri.getLastPathSegment())) {
                File file = new File(getContext().getCacheDir(), "import-provider-slice");
                try (OutputStream out = new FileOutputStream(file)) {
                    out.write(new byte[97]);
                    out.write(bytes);
                    out.write(new byte[31]);
                }
                return new AssetFileDescriptor(ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_READ_ONLY), 97, bytes.length);
            }
            if ("photo".equals(uri.getLastPathSegment())) {
                File file = new File(getContext().getCacheDir(), "import-provider-photo.jpg");
                Bitmap photo = Bitmap.createBitmap(4800, 100, Bitmap.Config.ARGB_8888);
                photo.eraseColor(Color.BLUE);
                try (OutputStream out = new FileOutputStream(file)) {
                    photo.compress(Bitmap.CompressFormat.JPEG, 95, out);
                } finally {
                    photo.recycle();
                }
                ExifInterface exif = new ExifInterface(file.getAbsolutePath());
                exif.setAttribute(ExifInterface.TAG_ORIENTATION,
                        String.valueOf(ExifInterface.ORIENTATION_ROTATE_90));
                exif.saveAttributes();
                return new AssetFileDescriptor(ParcelFileDescriptor.open(file,
                        ParcelFileDescriptor.MODE_READ_ONLY), 0, file.length());
            }
            byte[] payload = "invalid".equals(uri.getLastPathSegment()) ? new byte[]{1, 2, 3} : bytes;
            ParcelFileDescriptor[] pipe = ParcelFileDescriptor.createPipe();
            new Thread(() -> {
                try (OutputStream out = new ParcelFileDescriptor.AutoCloseOutputStream(pipe[1])) {
                    out.write(payload);
                } catch (IOException exception) {
                    throw new AssertionError(exception);
                }
            }).start();
            return new AssetFileDescriptor(pipe[0], 0, AssetFileDescriptor.UNKNOWN_LENGTH);
        } catch (IOException exception) {
            throw new FileNotFoundException(exception.toString());
        }
    }
}
