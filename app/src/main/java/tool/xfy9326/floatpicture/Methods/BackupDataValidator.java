package tool.xfy9326.floatpicture.Methods;

import android.graphics.BitmapFactory;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.*;
import java.util.HashSet;
import java.util.Iterator;
import java.util.Set;

/** Validate the actual library, not just the ZIP container, before replacing user data. */
public final class BackupDataValidator {
    private BackupDataValidator() { }

    public static int validate(File root) throws IOException, JSONException {
        JSONObject names = new JSONObject(read(new File(root, "Data/PictureList.list")));
        JSONObject details = new JSONObject(read(new File(root, "Data/PictureData.list")));
        if (names.length() != details.length()) throw new IOException("Mismatched picture metadata");
        Iterator<String> ids = names.keys();
        Set<String> expectedFiles = new HashSet<>();
        while (ids.hasNext()) {
            String id = ids.next();
            if (!id.matches("[A-Za-z0-9_-]+") || !(names.get(id) instanceof String)) {
                throw new IOException("Invalid picture ID/name");
            }
            JSONObject values = details.getJSONObject(id);
            checkNumber(values, "POSITION_X", Integer.MIN_VALUE, Integer.MAX_VALUE);
            checkNumber(values, "POSITION_Y", Integer.MIN_VALUE, Integer.MAX_VALUE);
            checkNumber(values, "ZOOM", Float.MIN_VALUE, Float.MAX_VALUE);
            checkNumber(values, "ZOOM_X", Float.MIN_VALUE, Float.MAX_VALUE);
            checkNumber(values, "ZOOM_Y", Float.MIN_VALUE, Float.MAX_VALUE);
            checkNumber(values, "DEFAULT_ZOOM", Float.MIN_VALUE, Float.MAX_VALUE);
            checkNumber(values, "ALPHA", 0, 1);
            checkNumber(values, "DEGREE", -Float.MAX_VALUE, Float.MAX_VALUE);
            for (String key : new String[]{"SHOW_ENABLED", "ALLOW_PICTURE_OVER_LAYOUT"}) {
                if (values.has(key) && !(values.get(key) instanceof Boolean)) {
                    throw new IOException("Invalid picture flag");
                }
            }
            validateImage(new File(root, "Pictures/" + id));
            expectedFiles.add(id);
            File original = new File(root, "Pictures/" + id + ".outline_source");
            if (original.exists()) {
                validateImage(original);
                expectedFiles.add(original.getName());
            }
        }
        File[] pictures = new File(root, "Pictures").listFiles();
        if (pictures == null) throw new IOException("Missing pictures directory");
        for (File picture : pictures) {
            if (!expectedFiles.contains(picture.getName())) throw new IOException("Unreferenced picture");
        }
        File orderFile = new File(root, "Data/PictureOrder.list");
        if (orderFile.exists()) {
            JSONArray order = new JSONArray(read(orderFile));
            Set<String> seen = new HashSet<>();
            for (int i = 0; i < order.length(); i++) {
                String id = order.getString(i);
                if (!names.has(id) || !seen.add(id)) throw new IOException("Invalid picture order");
            }
            if (seen.size() != names.length()) throw new IOException("Incomplete picture order");
        }
        return names.length();
    }

    private static void checkNumber(JSONObject values, String key, double min, double max)
            throws JSONException, IOException {
        if (!values.has(key)) return;
        if (!(values.get(key) instanceof Number)) throw new IOException("Invalid numeric parameter");
        double value = values.getDouble(key);
        if (Double.isNaN(value) || Double.isInfinite(value) || value < min || value > max) {
            throw new IOException("Invalid numeric parameter");
        }
    }

    private static void validateImage(File picture) throws IOException {
        BitmapFactory.Options options = new BitmapFactory.Options();
        options.inJustDecodeBounds = true;
        BitmapFactory.decodeFile(picture.getAbsolutePath(), options);
        if (options.outWidth <= 0 || options.outHeight <= 0) throw new IOException("Missing/invalid image");
    }

    private static String read(File file) throws IOException {
        try (InputStream input = new FileInputStream(file)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();
            BackupArchive.copy(input, bytes, null, 4 * 1024 * 1024);
            return bytes.toString("UTF-8");
        }
    }
}
