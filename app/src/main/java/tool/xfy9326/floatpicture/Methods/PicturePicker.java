package tool.xfy9326.floatpicture.Methods;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;

import tool.xfy9326.floatpicture.R;

/** Shared picker behavior for adding and replacing a picture. */
public final class PicturePicker {
    private PicturePicker() {}

    public interface Launcher {
        void launch(Intent intent);
    }

    public static void launch(Context context, Launcher launcher) {
        for (String action : new String[]{Intent.ACTION_GET_CONTENT, Intent.ACTION_OPEN_DOCUMENT}) {
            Intent intent = new Intent(action)
                    .setType("image/*")
                    .addCategory(Intent.CATEGORY_OPENABLE)
                    .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
            try {
                launcher.launch(intent);
                return;
            } catch (ActivityNotFoundException | SecurityException exception) {
                // Fall back to the document picker if no gallery can handle selection.
            }
        }
        Toast.makeText(context, R.string.picture_picker_unavailable, Toast.LENGTH_LONG).show();
    }

    public static Uri getSelectedUri(Intent result) {
        if (result == null) return null;
        if (result.getData() != null) return result.getData();
        ClipData clip = result.getClipData();
        if (clip != null) {
            for (int i = 0; i < clip.getItemCount(); i++) {
                Uri uri = clip.getItemAt(i).getUri();
                if (uri != null) return uri;
            }
        }
        return null;
    }
}
