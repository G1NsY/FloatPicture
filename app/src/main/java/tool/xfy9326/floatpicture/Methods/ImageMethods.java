package tool.xfy9326.floatpicture.Methods;

import android.app.Activity;
import android.content.Context;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.graphics.Matrix;
import android.net.Uri;
import android.provider.OpenableColumns;
import android.util.DisplayMetrics;
import android.view.View;
import android.view.WindowManager;

import androidx.preference.PreferenceManager;

import java.io.File;
import java.util.HashMap;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.FloatImageView;

public class ImageMethods {

    private static final int OUTLINE_RED = 0xFFEB2020;
    private static final int EIGHTEEN_PERCENT_GRAY = 0xFF777777;

    public static String getImageDisplayName(Context context, Uri uri) {
        if (uri == null) {
            return null;
        }
        try (Cursor cursor = context.getContentResolver().query(
                uri,
                new String[]{OpenableColumns.DISPLAY_NAME},
                null,
                null,
                null)) {
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumn = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME);
                if (nameColumn >= 0) {
                    String displayName = cursor.getString(nameColumn);
                    if (displayName != null && !displayName.trim().isEmpty()) {
                        return displayName.trim();
                    }
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        }

        String lastPathSegment = uri.getLastPathSegment();
        if (lastPathSegment != null && !lastPathSegment.trim().isEmpty()) {
            int separatorIndex = Math.max(
                    lastPathSegment.lastIndexOf('/'),
                    lastPathSegment.lastIndexOf('\\'));
            return lastPathSegment.substring(separatorIndex + 1).trim();
        }
        return null;
    }

    public static String setNewImage(Activity activity, Uri uri) {
        String md5 = CodeMethods.getFileMD5String(activity, uri);
        if (md5 == null) return null;

        // 【修改点】：为了支持导入相同图片，在 ID 后追加时间戳，确保 ID 唯一性
        String uniqueId = md5 + "_" + System.currentTimeMillis();

        Bitmap bitmap = IOMethods.readImageByUri(activity, uri);
        if (bitmap != null) {
            String path = Config.DEFAULT_PICTURE_DIR + uniqueId;
            int quality = PreferenceManager.getDefaultSharedPreferences(activity).getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
            IOMethods.saveBitmap(bitmap, quality, path);
            return uniqueId;
        }
        return null;
    }

    public static boolean replaceImage(Activity activity, Uri uri, String pictureId) {
        if (uri == null || pictureId == null || pictureId.isEmpty()) {
            return false;
        }
        int quality = PreferenceManager.getDefaultSharedPreferences(activity)
                .getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
        return IOMethods.replaceImageByUri(
                activity,
                uri,
                quality,
                Config.DEFAULT_PICTURE_DIR + pictureId);
    }

    public static Bitmap getShowBitmap(Context context, String pictureId) {
        String path = Config.DEFAULT_PICTURE_DIR + pictureId;
        File file = new File(path);
        if (file.exists()) {
            return BitmapFactory.decodeFile(path);
        }
        return null;
    }

    public static Bitmap getPreviewBitmap(Context context, String pictureId) {
        return getShowBitmap(context, pictureId);
    }

    public static boolean isPictureFileExist(String pictureId) {
        String path = Config.DEFAULT_PICTURE_DIR + pictureId;
        File file = new File(path);
        return file.exists();
    }

    public static Bitmap getEditBitmap(Context context, Bitmap bitmap) {
        if (bitmap == null) return null;
        return bitmap.copy(Bitmap.Config.ARGB_8888, true);
    }

    /**
     * Reproduces the common Photoshop pencil-outline workflow: grayscale, inverted
     * color dodge, Minimum, Levels, white removal, red ink and an 18% gray base.
     */
    public static Bitmap createRedOutline(Bitmap source, int minimumRadius, int contrast) {
        if (source == null || source.isRecycled()) return null;

        int width = source.getWidth();
        int height = source.getHeight();
        if (width <= 0 || height <= 0) return null;

        int radius = Math.max(1, Math.min(8, minimumRadius));
        int strength = Math.max(0, Math.min(100, contrast));
        int count = width * height;

        try {
            int[] pixels = new int[count];
            byte[] gray = new byte[count];
            byte[] invertedMinimum = new byte[count];
            byte[] work = new byte[count];
            source.getPixels(pixels, 0, width, 0, 0, width, height);

            for (int i = 0; i < count; i++) {
                int color = pixels[i];
                int luminance = (77 * ((color >> 16) & 0xFF)
                        + 150 * ((color >> 8) & 0xFF)
                        + 29 * (color & 0xFF)) >> 8;
                gray[i] = (byte) luminance;
                work[i] = (byte) (255 - luminance);
            }

            minimumHorizontal(work, invertedMinimum, width, height, radius);
            minimumVertical(invertedMinimum, work, width, height, radius);

            float gain = 1f + strength / 35f;
            int noiseFloor = 1 + strength / 25;
            int redR = (OUTLINE_RED >> 16) & 0xFF;
            int redG = (OUTLINE_RED >> 8) & 0xFF;
            int redB = OUTLINE_RED & 0xFF;
            int grayBase = EIGHTEEN_PERCENT_GRAY & 0xFF;

            for (int i = 0; i < count; i++) {
                int base = gray[i] & 0xFF;
                int blend = work[i] & 0xFF;
                int denominator = 255 - blend;
                int dodge = denominator == 0
                        ? 255
                        : Math.min(255, (base * 255) / denominator);
                int ink = Math.max(0, Math.min(255,
                        Math.round(((255 - dodge) - noiseFloor) * gain)));
                int inverseInk = 255 - ink;
                int r = (redR * ink + grayBase * inverseInk + 127) / 255;
                int g = (redG * ink + grayBase * inverseInk + 127) / 255;
                int b = (redB * ink + grayBase * inverseInk + 127) / 255;
                pixels[i] = 0xFF000000 | (r << 16) | (g << 8) | b;
            }

            Bitmap result = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888);
            result.setPixels(pixels, 0, width, 0, 0, width, height);
            return result;
        } catch (OutOfMemoryError error) {
            return null;
        }
    }

    private static void minimumHorizontal(byte[] source, byte[] target,
                                          int width, int height, int radius) {
        int[] deque = new int[width];
        for (int y = 0; y < height; y++) {
            int row = y * width;
            int head = 0;
            int tail = 0;
            for (int i = 0; i < width + radius; i++) {
                if (i < width) {
                    int value = source[row + i] & 0xFF;
                    while (tail > head
                            && (source[row + deque[tail - 1]] & 0xFF) >= value) {
                        tail--;
                    }
                    deque[tail++] = i;
                }
                int x = i - radius;
                if (x >= 0) {
                    int left = Math.max(0, x - radius);
                    while (tail > head && deque[head] < left) head++;
                    target[row + x] = source[row + deque[head]];
                }
            }
        }
    }

    private static void minimumVertical(byte[] source, byte[] target,
                                        int width, int height, int radius) {
        int[] deque = new int[height];
        for (int x = 0; x < width; x++) {
            int head = 0;
            int tail = 0;
            for (int i = 0; i < height + radius; i++) {
                if (i < height) {
                    int value = source[i * width + x] & 0xFF;
                    while (tail > head
                            && (source[deque[tail - 1] * width + x] & 0xFF) >= value) {
                        tail--;
                    }
                    deque[tail++] = i;
                }
                int y = i - radius;
                if (y >= 0) {
                    int top = Math.max(0, y - radius);
                    while (tail > head && deque[head] < top) head++;
                    target[y * width + x] = source[deque[head] * width + x];
                }
            }
        }
    }

    public static FloatImageView getFloatImageViewById(Context context, String pictureId) {
        MainApplication app = (MainApplication) context.getApplicationContext();
        HashMap<String, View> register = app.getRegister();
        if (register.containsKey(pictureId)) {
            return (FloatImageView) register.get(pictureId);
        }
        return null;
    }

    public static void saveFloatImageViewById(Context context, String pictureId, FloatImageView view) {
        MainApplication app = (MainApplication) context.getApplicationContext();
        HashMap<String, View> register = app.getRegister();
        register.put(pictureId, view);
        view.setPictureId(pictureId);
    }

    public static float getDefaultZoom(Context context, Bitmap bitmap, boolean b) {
        if (bitmap == null) return 1.0f;
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics dm = new DisplayMetrics();
        wm.getDefaultDisplay().getMetrics(dm);

        float widthRatio = (float) dm.widthPixels / bitmap.getWidth();
        float heightRatio = (float) dm.heightPixels / bitmap.getHeight();
        float zoom = Math.min(widthRatio, heightRatio);
        return Math.min(zoom, 1.0f);
    }

    public static FloatImageView createPictureView(Context context, Bitmap bitmap, boolean touch_and_move, boolean allow_picture_over_layout, float zoom_x, float zoom_y, float degree) {
        FloatImageView view = new FloatImageView(context);
        view.setImageBitmap(resizeBitmap(bitmap, zoom_x, zoom_y, degree));
        view.setMoveable(touch_and_move);
        view.setOverLayout(allow_picture_over_layout);
        return view;
    }

    // Overload for backward compatibility
    public static FloatImageView createPictureView(Context context, Bitmap bitmap, boolean touch_and_move, boolean allow_picture_over_layout, float zoom, float degree) {
        return createPictureView(context, bitmap, touch_and_move, allow_picture_over_layout, zoom, zoom, degree);
    }

    public static Bitmap resizeBitmap(Bitmap bitmap, float zoom_x, float zoom_y, float degree) {
        if (bitmap == null) return null;
        int width = bitmap.getWidth();
        int height = bitmap.getHeight();
        Matrix matrix = new Matrix();
        if (zoom_x != 0 && zoom_y != 0) {
            matrix.postScale(zoom_x, zoom_y);
        }
        if (degree != -1) {
            matrix.postRotate(degree);
        }
        return Bitmap.createBitmap(bitmap, 0, 0, width, height, matrix, true);
    }

    // Backward compatibility
    public static Bitmap resizeBitmap(Bitmap bitmap, float zoom, float degree) {
        return resizeBitmap(bitmap, zoom, zoom, degree);
    }

    public static void clearAllTemp(Context context, String id) {
        String path = Config.DEFAULT_PICTURE_DIR + id;
        File file = new File(path);
        if (file.exists()) {
            file.delete();
        }
    }
}
