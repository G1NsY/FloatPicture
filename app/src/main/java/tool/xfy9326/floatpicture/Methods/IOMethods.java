package tool.xfy9326.floatpicture.Methods;

import android.content.ContentResolver;
import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;

import androidx.exifinterface.media.ExifInterface;

import java.io.BufferedReader;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.util.Objects;

import tool.xfy9326.floatpicture.Utils.Config;

/**
 * @noinspection IOStreamConstructor
 */
public class IOMethods {

    static Bitmap readImageByUri(Context context, Uri uri) {
        if (uri == null) return null;
        ContentResolver contentResolver = context.getContentResolver();
        File source = null;
        Bitmap bitmap = null;
        try {
            // Stage once: providers may supply a non-seekable stream. Bounds, decoding
            // and EXIF must all see the same complete photo.
            source = File.createTempFile("picture-import-", ".tmp", context.getCacheDir());
            try (InputStream inputStream = contentResolver.openInputStream(uri);
                 OutputStream outputStream = new FileOutputStream(source)) {
                if (inputStream == null) return null;
                byte[] buffer = new byte[8192];
                int count;
                while ((count = inputStream.read(buffer)) != -1) {
                    outputStream.write(buffer, 0, count);
                }
            }
            BitmapFactory.Options options = new BitmapFactory.Options();
            options.inJustDecodeBounds = true;
            BitmapFactory.decodeFile(source.getAbsolutePath(), options);
            if (options.outWidth <= 0 || options.outHeight <= 0) return null;
            options.inSampleSize = importSampleSize(options.outWidth, options.outHeight,
                    Runtime.getRuntime().maxMemory());
            options.inJustDecodeBounds = false;
            options.inPreferredConfig = Bitmap.Config.ARGB_8888;
            // Leave room for rotation and floating-window editing on older devices.
            for (int attempt = 0; attempt < 3; attempt++) {
                try {
                    bitmap = BitmapFactory.decodeFile(source.getAbsolutePath(), options);
                    break;
                } catch (OutOfMemoryError error) {
                    options.inSampleSize *= 2;
                }
            }
            if (bitmap == null) return null;
            try {
                ExifInterface exif = new ExifInterface(source.getAbsolutePath());
                Matrix matrix = new Matrix();
                if (exif.isFlipped()) matrix.postScale(-1f, 1f);
                matrix.postRotate(exif.getRotationDegrees());
                if (!matrix.isIdentity()) {
                    Bitmap oriented = Bitmap.createBitmap(bitmap, 0, 0,
                            bitmap.getWidth(), bitmap.getHeight(), matrix, true);
                    if (oriented != bitmap) bitmap.recycle();
                    bitmap = oriented;
                }
            } catch (IOException ignored) {
                // A decodable image need not contain readable EXIF metadata.
            }
            return bitmap;
        } catch (Exception | OutOfMemoryError e) {
            if (bitmap != null && !bitmap.isRecycled()) bitmap.recycle();
            e.printStackTrace();
            return null;
        } finally {
            if (source != null) {
                //noinspection ResultOfMethodCallIgnored
                source.delete();
            }
        }
    }

    static int importSampleSize(int width, int height, long heapBytes) {
        long maxPixels = Math.min(8_000_000L, Math.max(1L, heapBytes / 32L));
        int sample = 1;
        while (((long) width + sample - 1) / sample > 4096
                || ((long) height + sample - 1) / sample > 4096
                || (((long) width + sample - 1) / sample)
                * (((long) height + sample - 1) / sample) > maxPixels) {
            sample *= 2;
        }
        return sample;
    }

    static boolean replaceImageByUri(Context context, Uri uri, int quality, String targetPath) {
        Bitmap replacement = readImageByUri(context, uri);
        if (replacement == null) {
            return false;
        }

        File target = new File(targetPath);
        File temporary = new File(targetPath + ".replacement");
        File backup = new File(targetPath + ".backup");
        if ((temporary.exists() && !temporary.delete()) || (backup.exists() && !backup.delete())) {
            replacement.recycle();
            return false;
        }

        saveBitmap(replacement, quality, temporary.getAbsolutePath());
        if (!temporary.isFile() || temporary.length() == 0) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }

        boolean hadOriginal = target.isFile();
        if (hadOriginal && !target.renameTo(backup)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }

        if (!temporary.renameTo(target)) {
            if (hadOriginal) {
                //noinspection ResultOfMethodCallIgnored
                backup.renameTo(target);
            }
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }

        if (backup.exists()) {
            //noinspection ResultOfMethodCallIgnored
            backup.delete();
        }
        return true;
    }

    public static boolean replaceBitmap(Bitmap replacement, int quality, String targetPath) {
        if (replacement == null || replacement.isRecycled()) return false;

        File target = new File(targetPath);
        File temporary = new File(targetPath + ".replacement");
        File backup = new File(targetPath + ".backup");
        try {
            if ((temporary.exists() && !temporary.delete())
                    || (backup.exists() && !backup.delete())) {
                return false;
            }
            if (!createPath(temporary) && temporary.getParentFile() != null
                    && !temporary.getParentFile().isDirectory()) {
                return false;
            }
            try (OutputStream outputStream = new FileOutputStream(temporary)) {
                if (!replacement.compress(Bitmap.CompressFormat.WEBP, quality, outputStream)) {
                    return false;
                }
                outputStream.flush();
            }
            if (!temporary.isFile() || temporary.length() == 0) return false;

            boolean hadOriginal = target.isFile();
            if (hadOriginal && !target.renameTo(backup)) return false;
            if (!temporary.renameTo(target)) {
                if (hadOriginal) {
                    //noinspection ResultOfMethodCallIgnored
                    backup.renameTo(target);
                }
                return false;
            }
            if (backup.exists()) {
                //noinspection ResultOfMethodCallIgnored
                backup.delete();
            }
            return true;
        } catch (IOException exception) {
            exception.printStackTrace();
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }
    }

    public static boolean copyFile(String sourcePath, String targetPath) {
        File source = new File(sourcePath);
        File target = new File(targetPath);
        File temporary = new File(targetPath + ".copying");
        if (!source.isFile() || target.exists()) return false;

        File parent = target.getParentFile();
        if (parent != null && !parent.isDirectory() && !parent.mkdirs()) return false;
        if (temporary.exists() && !temporary.delete()) return false;

        try (InputStream inputStream = new FileInputStream(source);
             OutputStream outputStream = new FileOutputStream(temporary)) {
            byte[] buffer = new byte[8192];
            int count;
            while ((count = inputStream.read(buffer)) != -1) {
                outputStream.write(buffer, 0, count);
            }
            outputStream.flush();
        } catch (IOException exception) {
            exception.printStackTrace();
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }

        if (!temporary.isFile() || temporary.length() != source.length()
                || !temporary.renameTo(target)) {
            //noinspection ResultOfMethodCallIgnored
            temporary.delete();
            return false;
        }
        return true;
    }

    @SuppressWarnings("SameParameterValue")
//    static void saveBitmap(Bitmap bitmap, int quality, String path) {
//        File file = new File(path);
//        try {
//            if (!CheckFile(file, true)) {
//                try (OutputStream outputStream = new FileOutputStream(file)) {
//                    // 改用 JPEG，质量调节效果会非常明显
//                    bitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream);
//                    outputStream.flush();
//                }
//                bitmap.recycle();
//            }
//        } catch (IOException e) {
//            e.printStackTrace();
//        }
//    }
    static void saveBitmap(Bitmap bitmap, int quality, String path) {
        File file = new File(path);
        try {
            if (!CheckFile(file, true)) {
                try (OutputStream outputStream = new FileOutputStream(file)) {
                    // 改回 WEBP 格式以支持透明度
                    // 注意：WEBP 在质量 40 以下才会有明显的画质下降感
                    bitmap.compress(Bitmap.CompressFormat.WEBP, quality, outputStream);
                    outputStream.flush();
                }
                bitmap.recycle();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
    }

    public static String readAssetText(Context mContext, String path) {
        try {
            StringBuilder result = new StringBuilder();
            InputStreamReader inputReader = new InputStreamReader(mContext.getResources().getAssets().open(path));
            BufferedReader bufReader = new BufferedReader(inputReader);
            String line;
            while ((line = bufReader.readLine()) != null) {
                result.append(line).append("\n");
            }
            return result.toString();
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

    @SuppressWarnings("UnusedReturnValue")
    public static boolean setNoMedia() {
        File nomedia = new File(Config.NO_MEDIA_FILE_DIR);
        if (!nomedia.exists()) {
            try {
                return nomedia.createNewFile();
            } catch (IOException e) {
                e.printStackTrace();
                return false;
            }
        } else {
            return true;
        }
    }

    private static boolean createPath(File file) {
        if (Objects.requireNonNull(file.getParent()).trim().length() != 1) {
            File filepath = file.getParentFile();
            if (!Objects.requireNonNull(filepath).exists()) {
                return filepath.mkdirs();
            }
        }
        return true;
    }

    private static boolean CheckFile(File file, boolean delete) throws IOException {
        if (file.exists()) {
            if (file.isFile()) {
                if (delete) {
                    if (file.delete()) {
                        return !file.createNewFile();
                    }
                } else {
                    return false;
                }
            }
        } else {
            if (!createPath(file)) {
                return true;
            }
            return !file.createNewFile();
        }
        return true;
    }

    public static boolean writeFile(String content, String path) {
        File file = new File(path);
        try (OutputStream writer = new FileOutputStream(file)) {
            if (CheckFile(file, false)) {
                return false;
            }
            byte[] Bytes = content.getBytes();
            writer.write(Bytes);
            writer.flush();
            return true;
        } catch (IOException e) {
            e.printStackTrace();
        }
        return false;
    }

    public static String readFile(String path) {
        File file = new File(path);
        try {
            if (CheckFile(file, false)) {
                return null;
            }
            try (InputStream file_stream = new FileInputStream(file);
                 BufferedReader reader = new BufferedReader(new InputStreamReader(file_stream))) {
                String line;
                StringBuilder result = new StringBuilder();
                while ((line = reader.readLine()) != null) {
                    result.append(line).append("\n");
                }
                return result.toString();
            }
        } catch (IOException e) {
            e.printStackTrace();
        }
        return null;
    }

}
