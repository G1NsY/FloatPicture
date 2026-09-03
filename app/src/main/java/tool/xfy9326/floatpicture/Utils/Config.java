package tool.xfy9326.floatpicture.Utils;

import android.content.Context;

import java.io.File;

public class Config {
    public final static int NOTIFICATION_ID = 4500;

    public final static int REQUEST_CODE_PERMISSION_NOTIFICATION = 1;
    public final static int REQUEST_CODE_PERMISSION_OVERLAY = 2;
    public final static int REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_ADD = 3;
    public final static int REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_GET_PICTURE = 4;
    public final static int REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_CHANGE = 5;
    public final static int REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_REPLACE = 6;
    public final static int REQUEST_CODE_ACTIVITY_IMPORT_LEGACY_DATA = 7;

    public final static String INTENT_PICTURE_EDIT_POSITION = "EDIT_POSITION";
    public final static String INTENT_PICTURE_EDIT_ID = "EDIT_ID";
    public final static String INTENT_PICTURE_EDIT_MODE = "EDIT_MODE";

    public final static String INTENT_ACTION_NOTIFICATION_FLOATING_CONTROL_TOGGLE = "ACTION_NOTIFICATION_FLOATING_CONTROL_TOGGLE";
    public final static String INTENT_ACTION_NOTIFICATION_UPDATE_COUNT = "ACTION_NOTIFICATION_UPDATE_COUNT";
    public final static String INTENT_ACTION_FLOATING_CONTROL_REFRESH = "ACTION_FLOATING_CONTROL_REFRESH";

    public final static String DATA_PICTURE_SHOW_ENABLED = "SHOW_ENABLED";
    public final static String DATA_PICTURE_POSITION_X = "POSITION_X";
    public final static String DATA_PICTURE_POSITION_Y = "POSITION_Y";
    public final static String DATA_PICTURE_ZOOM = "ZOOM";
    public final static String DATA_PICTURE_ZOOM_X = "ZOOM_X";
    public final static String DATA_PICTURE_ZOOM_Y = "ZOOM_Y";
    public final static String DATA_PICTURE_DEFAULT_ZOOM = "DEFAULT_ZOOM";
    public final static String DATA_PICTURE_ALPHA = "ALPHA";
    public final static String DATA_PICTURE_DEGREE = "DEGREE";
    public final static String DATA_ALLOW_PICTURE_OVER_LAYOUT = "ALLOW_PICTURE_OVER_LAYOUT";

    public final static boolean DATA_DEFAULT_PICTURE_SHOW_ENABLED = true;
    public final static int DATA_DEFAULT_PICTURE_POSITION_X = 100;
    public final static int DATA_DEFAULT_PICTURE_POSITION_Y = 100;
    public final static float DATA_DEFAULT_PICTURE_ALPHA = 0.5f;
    public final static float DATA_DEFAULT_PICTURE_DEGREE = 0f;
    public final static boolean DATA_DEFAULT_ALLOW_PICTURE_OVER_LAYOUT = false;

    public final static String PREFERENCE_PICTURE_NAME = "settings_picture_name";
    public final static String PREFERENCE_PICTURE_REPLACE = "settings_picture_replace";
    public final static String PREFERENCE_PICTURE_COPY_CATEGORY = "settings_picture_copy_category";
    public final static String PREFERENCE_PICTURE_SAVE_AS_COPY = "settings_picture_save_as_copy";
    public final static String PREFERENCE_ALLOW_PICTURE_OVER_LAYOUT = "settings_allow_picture_over_layout";
    public final static String PREFERENCE_PICTURE_OUTLINE = "settings_picture_outline";
    public final static String PREFERENCE_PICTURE_RESIZE = "settings_picture_resize";
    public final static String PREFERENCE_PICTURE_ALPHA = "settings_picture_alpha";
    public final static String PREFERENCE_PICTURE_POSITION = "settings_picture_position";
    public final static String PREFERENCE_PICTURE_DEGREE = "settings_picture_degree";

    public final static String PREFERENCE_SHOW_NOTIFICATION_CONTROL = "show_notification_control";
    public final static String PREFERENCE_SHOW_FLOATING_CONTROL = "show_floating_control";
    public final static String PREFERENCE_FLOATING_CONTROL_X = "floating_control_x";
    public final static String PREFERENCE_FLOATING_CONTROL_Y = "floating_control_y";
    public final static String PREFERENCE_FLOATING_CONTROL_DOCK_RIGHT = "floating_control_dock_right";
    public final static String PREFERENCE_IMPORT_LEGACY_DATA = "import_legacy_data";
    public final static String PREFERENCE_NOTIFICATION_PERMISSION_REQUESTED = "notification_permission_requested";
    public final static String PREFERENCE_PRIVACY_ACCEPTED = "privacy_policy_accepted";
    public final static String PREFERENCE_NEW_PICTURE_QUALITY = "new_picture_quality";
    public final static String PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES = "allow_multiple_floating_pictures";
    public final static String PREFERENCE_TOUCHABLE_POSITION_EDIT = "touchable_position_edit";
    public final static String PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN = "allow_global_drag_over_screen";
    public final static String PREFERENCE_PINCH_ROTATION = "pinch_rotation";
    public final static String PREFERENCE_ROTATION_OVERFLOW_DECOUPLED = "rotation_overflow_decoupled";
    public final static String PREFERENCE_SAVE_GESTURE_ADJUSTMENTS = "save_gesture_adjustments";
    public final static String PREFERENCE_LOCK_GESTURES_AFTER_SAVE = "lock_gestures_after_save";

    public final static String LICENSE_PATH_APPLICATION = "LICENSE";
    public final static String PRIVACY_POLICY_PATH_APPLICATION = "PRIVACY_POLICY.txt";
    public static String DEFAULT_PICTURE_TEMP_DIR;
    static String DEFAULT_DATA_DIR;
    public static String DEFAULT_PICTURE_DIR;
    public final static String PICTURE_OUTLINE_SOURCE_SUFFIX = ".outline_source";
    public static String NO_MEDIA_FILE_DIR;

    public static void initialize(Context context) {
        String applicationDir = new File(context.getFilesDir(), "FloatPicture").getAbsolutePath()
                + File.separator;
        DEFAULT_PICTURE_DIR = applicationDir + "Pictures" + File.separator;
        DEFAULT_PICTURE_TEMP_DIR = DEFAULT_PICTURE_DIR + ".TEMP" + File.separator;
        DEFAULT_DATA_DIR = applicationDir + "Data" + File.separator;
        NO_MEDIA_FILE_DIR = applicationDir + ".nomedia";
    }
}
