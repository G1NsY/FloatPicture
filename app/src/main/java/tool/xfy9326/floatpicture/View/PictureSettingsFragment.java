package tool.xfy9326.floatpicture.View;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Bitmap;
import android.graphics.Point;
import android.graphics.drawable.Drawable;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.view.LayoutInflater;
import android.view.View;
import android.view.WindowManager;
import android.view.inputmethod.EditorInfo;
import android.view.inputmethod.InputMethodManager;
import android.widget.EditText;
import android.widget.CheckBox;
import android.widget.ImageView;
import android.widget.ProgressBar;
import android.widget.SeekBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;
import androidx.core.content.ContextCompat;
import android.content.Context;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Objects;
import java.util.concurrent.atomic.AtomicInteger;

import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.WindowsMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class PictureSettingsFragment extends PreferenceFragmentCompat {
    private final static String WINDOW_CREATED = "WINDOW_CREATED";
    private boolean Edit_Mode;
    private boolean originallyVisible = true;
    private boolean Window_Created;
    private boolean onUseEditPicture = false;
    private LayoutInflater inflater;
    private PictureData pictureData;
    private String PictureId;
    private String PictureName;
    private WindowManager windowManager;
    private FloatImageView floatImageView;
    private Bitmap bitmap;
    private Bitmap bitmap_Edit;
    private FloatImageView floatImageView_Edit;
    private float default_zoom;
    private float zoom_x;
    private float zoom_y;
    private float zoom_x_temp;
    private float zoom_y_temp;
    private float picture_degree;
    private float picture_degree_temp;
    private float picture_alpha;
    private float picture_alpha_temp;
    private int position_x;
    private int position_y;
    private int position_x_temp;
    private int position_y_temp;
    private boolean allow_picture_over_layout;
    private int lastScreenWidth;
    private int lastScreenHeight;
    private AlertDialog currentDialog;
    private final AtomicInteger outlinePreviewGeneration = new AtomicInteger();

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        Window_Created = false;
        Edit_Mode = false;
        pictureData = new PictureData();
        inflater = LayoutInflater.from(getActivity());
        windowManager = WindowsMethods.getWindowManager(requireActivity());
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_picture_settings);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        // 记录启动时的屏幕尺寸
        Point size = new Point();
        requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
        lastScreenWidth = size.x;
        lastScreenHeight = size.y;

        restoreData(savedInstanceState);
        setMode();
        // PreferenceSet() will be called inside setMode's thread completion or here.
        // But setMode runs a thread. We should ensure PreferenceSet handles the initial summary.
    }

    public void onConfigurationChanged(@NonNull android.content.res.Configuration newConfig) {
        super.onConfigurationChanged(newConfig);

        // 1. 同步当前悬浮窗被拖动后的最新坐标
        // 2. 重新获取当前屏幕真实的物理宽高（解决高度识别错误的关键）
        Point size = new Point();
        requireActivity().getWindowManager().getDefaultDisplay().getRealSize(size);
        lastScreenWidth = size.x;
        lastScreenHeight = size.y;

        // 3. 刷新悬浮窗。注意：这里不需要修改 picture_degree，
        // 系统坐标系旋转后，重新 updateWindow 即可让悬浮窗适应新方向。
        refreshFloatingWindow();
    }

    private void refreshFloatingWindow() {
        if (floatImageView != null && !onUseEditPicture) {
            // 使用原始记录的 picture_degree，不要使用被修改过的偏移量
            floatImageView.configureGestureImage(bitmap, zoom_x, zoom_y, picture_degree);

            // 更新窗口
            WindowsMethods.updateWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);

            // 同步 View 内部坐标
            syncPositionToView(floatImageView, position_x, position_y);
        }
    }

    @Override
    public void onSaveInstanceState(Bundle outState) {
        outState.putBoolean(WINDOW_CREATED, true);
        super.onSaveInstanceState(outState);
    }

    private void restoreData(Bundle savedInstanceState) {
        if (savedInstanceState != null) {
            Window_Created = savedInstanceState.getBoolean(WINDOW_CREATED, false);
            windowManager = WindowsMethods.getWindowManager(requireActivity());
        }
    }

    private void setMode() {
        Intent intent = Objects.requireNonNull(requireActivity().getIntent());
        Edit_Mode = intent.getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false);
        AlertDialog.Builder loading = new AlertDialog.Builder(requireActivity());
        loading.setCancelable(false);
        if (!Edit_Mode) {
            loading.setOnCancelListener(dialog -> {
                WindowsMethods.createWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
                syncPositionToView(floatImageView, position_x, position_y);
            });
        }
        View mView = inflater.inflate(R.layout.dialog_loading, requireActivity().findViewById(R.id.layout_dialog_loading));
        loading.setView(mView);
        final AlertDialog alertDialog = loading.show();
        new Thread(() -> {
            if (!Window_Created) {
                if (Edit_Mode) {
                    //Edit
                    PictureId = intent.getStringExtra(Config.INTENT_PICTURE_EDIT_ID);
                    pictureData.setDataControl(PictureId);
                    PictureName = pictureData.getListArray().get(PictureId);
                    originallyVisible = pictureData.getBoolean(
                            Config.DATA_PICTURE_SHOW_ENABLED,
                            Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
                    position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
                    position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
                    picture_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
                    picture_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, Config.DATA_DEFAULT_PICTURE_ALPHA);
                    allow_picture_over_layout = ManageMethods.resolvePictureOverLayout(requireContext());
                    bitmap = ImageMethods.getShowBitmap(requireContext(), PictureId);
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, default_zoom);
                    zoom_x = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_X, zoom);
                    zoom_y = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_Y, zoom);
                    floatImageView = ImageMethods.getFloatImageViewById(requireContext(), PictureId);
                } else {
                    //New
                    originallyVisible = true;
                    PictureId = ImageMethods.setNewImage(getActivity(), intent.getData());
                    pictureData.setDataControl(PictureId);
                    PictureName = ImageMethods.getImageDisplayName(requireContext(), intent.getData());
                    if (PictureName == null || PictureName.isEmpty()) {
                        PictureName = getString(R.string.new_picture_name);
                    }
                    position_x = Config.DATA_DEFAULT_PICTURE_POSITION_X;
                    position_y = Config.DATA_DEFAULT_PICTURE_POSITION_Y;
                    picture_alpha = Config.DATA_DEFAULT_PICTURE_ALPHA;
                    picture_degree = Config.DATA_DEFAULT_PICTURE_DEGREE;
                    allow_picture_over_layout = ManageMethods.resolvePictureOverLayout(requireContext());
                    bitmap = ImageMethods.getShowBitmap(requireContext(), PictureId);
                    default_zoom = ImageMethods.getDefaultZoom(requireContext(), bitmap, false);
                    zoom_x = default_zoom;
                    zoom_y = default_zoom;
                    floatImageView = ImageMethods.createPictureView(requireContext(), bitmap, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree);
                    floatImageView.setAlpha(picture_alpha);
                    floatImageView.setPictureId(PictureId);
                }
                requireActivity().runOnUiThread(() -> {
                    alertDialog.cancel();
                    PreferenceSet();
                });
            }
        }).start();
    }

    @NonNull
    private Preference requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void PreferenceSet() {
        Preference namePref = requirePreference(Config.PREFERENCE_PICTURE_NAME);
        namePref.setSummary(PictureName);
        namePref.setOnPreferenceClickListener(preference -> {
            setPictureName(preference);
            return true;
        });
        Preference replacePreference = requirePreference(Config.PREFERENCE_PICTURE_REPLACE);
        replacePreference.setOnPreferenceClickListener(preference -> {
            selectReplacementPicture();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_RESIZE).setOnPreferenceClickListener(preference -> {
            setPictureSize();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_OUTLINE).setOnPreferenceClickListener(preference -> {
            showOutlineDialog();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_DEGREE).setOnPreferenceClickListener(preference -> {
            setPictureDegree();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_ALPHA).setOnPreferenceClickListener(preference -> {
            setPictureAlpha();
            return true;
        });
        requirePreference(Config.PREFERENCE_PICTURE_POSITION).setOnPreferenceClickListener(preference -> {
            setPicturePosition();
            return true;
        });
        Preference copyCategory = requirePreference(Config.PREFERENCE_PICTURE_COPY_CATEGORY);
        Preference copyPreference = requirePreference(Config.PREFERENCE_PICTURE_SAVE_AS_COPY);
        copyCategory.setVisible(Edit_Mode);
        copyPreference.setVisible(Edit_Mode);
        copyPreference.setOnPreferenceClickListener(preference -> {
            saveAsCopy(preference);
            return true;
        });
    }

    private void saveAsCopy(Preference preference) {
        if (!Edit_Mode || bitmap == null || bitmap.isRecycled()) return;

        preference.setEnabled(false);
        AlertDialog.Builder loading = new AlertDialog.Builder(requireActivity());
        loading.setCancelable(false);
        View loadingView = inflater.inflate(
                R.layout.dialog_loading,
                requireActivity().findViewById(R.id.layout_dialog_loading));
        loading.setView(loadingView);
        AlertDialog loadingDialog = loading.show();

        final String copyName = getString(R.string.settings_picture_copy_name, PictureName);
        final float copyZoomX = zoom_x;
        final float copyZoomY = zoom_y;
        final float copyDefaultZoom = default_zoom;
        final float copyAlpha = picture_alpha;
        final float copyDegree = picture_degree;
        final int copyPositionX = position_x;
        final int copyPositionY = position_y;

        new Thread(() -> {
            String copyId = ImageMethods.copyPictureFiles(PictureId);
            boolean copied = copyId != null;
            if (copied) {
                PictureData copyData = new PictureData();
                copyData.setDataControl(copyId);
                copyData.put(Config.DATA_PICTURE_SHOW_ENABLED, false);
                copyData.put(Config.DATA_PICTURE_ZOOM, copyZoomX);
                copyData.put(Config.DATA_PICTURE_ZOOM_X, copyZoomX);
                copyData.put(Config.DATA_PICTURE_ZOOM_Y, copyZoomY);
                copyData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, copyDefaultZoom);
                copyData.put(Config.DATA_PICTURE_ALPHA, copyAlpha);
                copyData.put(Config.DATA_PICTURE_POSITION_X, copyPositionX);
                copyData.put(Config.DATA_PICTURE_POSITION_Y, copyPositionY);
                copyData.put(Config.DATA_PICTURE_DEGREE, copyDegree);
                copyData.commit(copyName);
                copied = copyData.getListArray().containsKey(copyId);
                if (!copied) {
                    ImageMethods.clearAllTemp(requireContext(), copyId);
                }
            }

            final boolean copySucceeded = copied;
            Activity activity = getActivity();
            if (activity == null) return;
            activity.runOnUiThread(() -> {
                loadingDialog.dismiss();
                preference.setEnabled(true);
                if (copySucceeded) {
                    activity.setResult(Activity.RESULT_OK, new Intent());
                    Toast.makeText(
                            activity,
                            getString(R.string.settings_picture_copy_success, copyName),
                            Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(
                            activity,
                            R.string.settings_picture_copy_failed,
                            Toast.LENGTH_SHORT).show();
                }
            });
        }, "FloatPicture-copy-picture").start();
    }

    private void selectReplacementPicture() {
        Intent intent = new Intent(Intent.ACTION_GET_CONTENT);
        intent.setType("image/*");
        intent.addCategory(Intent.CATEGORY_OPENABLE);
        startActivityForResult(intent, Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_REPLACE);
    }

    private void showOutlineDialog() {
        if (bitmap == null || bitmap.isRecycled()) return;

        Bitmap savedOutlineSource = ImageMethods.getOutlineSourceBitmap(PictureId);
        Bitmap outlineSource = savedOutlineSource != null ? savedOutlineSource : bitmap;

        View dialogView = inflater.inflate(
                R.layout.dialog_extract_outline,
                requireActivity().findViewById(android.R.id.content),
                false);
        ImageView previewView = dialogView.findViewById(R.id.image_outline_preview);
        ProgressBar progressBar = dialogView.findViewById(R.id.progress_outline);
        TextView detailLabel = dialogView.findViewById(R.id.text_outline_detail);
        TextView contrastLabel = dialogView.findViewById(R.id.text_outline_contrast);
        Spinner colorSpinner = dialogView.findViewById(R.id.spinner_outline_color);
        CheckBox grayBackgroundCheck = dialogView.findViewById(
                R.id.check_outline_gray_background);
        SeekBar detailBar = dialogView.findViewById(R.id.seek_outline_detail);
        SeekBar contrastBar = dialogView.findViewById(R.id.seek_outline_contrast);
        detailBar.setProgress(1); // Radius 2: close to Photoshop's useful default.
        contrastBar.setProgress(65);

        int maxPreviewSide = 900;
        float previewScale = Math.min(1f, maxPreviewSide
                / (float) Math.max(outlineSource.getWidth(), outlineSource.getHeight()));
        int previewWidth = Math.max(1, Math.round(outlineSource.getWidth() * previewScale));
        int previewHeight = Math.max(1, Math.round(outlineSource.getHeight() * previewScale));
        Bitmap previewSource = previewScale < 1f
                ? Bitmap.createScaledBitmap(outlineSource, previewWidth, previewHeight, true)
                : outlineSource.copy(Bitmap.Config.ARGB_8888, false);
        Bitmap[] displayedPreview = new Bitmap[1];
        int originalFloatViewVisibility = floatImageView.getVisibility();
        int[] outlineColors = {
                ImageMethods.OUTLINE_RED,
                ImageMethods.OUTLINE_GREEN,
                ImageMethods.OUTLINE_BLUE,
                ImageMethods.OUTLINE_BLACK,
                ImageMethods.OUTLINE_WHITE
        };

        AlertDialog outlineDialog = new AlertDialog.Builder(requireContext())
                .setTitle(R.string.settings_picture_outline)
                .setView(dialogView)
                .setCancelable(false)
                .setPositiveButton(R.string.settings_picture_outline_apply, null)
                .setNegativeButton(R.string.cancel, null)
                .create();
        currentDialog = outlineDialog;

        Runnable refreshLabelsAndPreview = () -> {
            int radius = detailBar.getProgress() + 1;
            int contrast = contrastBar.getProgress();
            int outlineColor = outlineColors[colorSpinner.getSelectedItemPosition()];
            boolean keepGrayBackground = grayBackgroundCheck.isChecked();
            detailLabel.setText(getString(R.string.settings_picture_outline_detail, radius));
            contrastLabel.setText(getString(R.string.settings_picture_outline_contrast, contrast));
            requestOutlinePreview(previewSource, radius, contrast, outlineColor,
                    keepGrayBackground,
                    previewView, progressBar, displayedPreview, outlineDialog);
        };

        colorSpinner.setOnItemSelectedListener(new android.widget.AdapterView.OnItemSelectedListener() {
            @Override
            public void onItemSelected(android.widget.AdapterView<?> parent, View view,
                                       int position, long id) {
                if (outlineDialog.isShowing()) refreshLabelsAndPreview.run();
            }

            @Override
            public void onNothingSelected(android.widget.AdapterView<?> parent) {
            }
        });

        grayBackgroundCheck.setOnCheckedChangeListener((buttonView, isChecked) -> {
            if (outlineDialog.isShowing()) refreshLabelsAndPreview.run();
        });

        SeekBar.OnSeekBarChangeListener previewListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                int radius = detailBar.getProgress() + 1;
                detailLabel.setText(getString(R.string.settings_picture_outline_detail, radius));
                contrastLabel.setText(getString(
                        R.string.settings_picture_outline_contrast,
                        contrastBar.getProgress()));
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
                refreshLabelsAndPreview.run();
            }
        };
        detailBar.setOnSeekBarChangeListener(previewListener);
        contrastBar.setOnSeekBarChangeListener(previewListener);

        outlineDialog.setOnDismissListener(dialog -> {
            outlinePreviewGeneration.incrementAndGet();
            floatImageView.setVisibility(originalFloatViewVisibility);
            previewView.setImageDrawable(null);
            if (displayedPreview[0] != null && !displayedPreview[0].isRecycled()) {
                displayedPreview[0].recycle();
                displayedPreview[0] = null;
            }
            if (outlineSource != bitmap && !outlineSource.isRecycled()) {
                outlineSource.recycle();
            }
        });
        outlineDialog.setOnShowListener(unused -> {
            // The source is a system overlay and otherwise sits on top of the
            // outline dialog, making the two previews overlap.
            floatImageView.setVisibility(View.INVISIBLE);
            refreshLabelsAndPreview.run();
            outlineDialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(button -> {
                outlinePreviewGeneration.incrementAndGet();
                detailBar.setEnabled(false);
                contrastBar.setEnabled(false);
                colorSpinner.setEnabled(false);
                grayBackgroundCheck.setEnabled(false);
                outlineDialog.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(false);
                outlineDialog.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(false);
                progressBar.setVisibility(View.VISIBLE);

                int radius = detailBar.getProgress() + 1;
                int contrast = contrastBar.getProgress();
                int outlineColor = outlineColors[colorSpinner.getSelectedItemPosition()];
                boolean keepGrayBackground = grayBackgroundCheck.isChecked();
                new Thread(() -> applyOutline(outlineSource, radius, contrast, outlineColor,
                                keepGrayBackground,
                                outlineDialog, progressBar),
                        "FloatPicture-outline-apply").start();
            });
        });
        outlineDialog.show();
    }

    private void requestOutlinePreview(Bitmap source, int radius, int contrast, int outlineColor,
                                       boolean keepGrayBackground,
                                       ImageView previewView, ProgressBar progressBar,
                                       Bitmap[] displayedPreview, AlertDialog owner) {
        int generation = outlinePreviewGeneration.incrementAndGet();
        progressBar.setVisibility(View.VISIBLE);
        new Thread(() -> {
            Bitmap result = ImageMethods.createOutline(
                    source, radius, contrast, outlineColor, keepGrayBackground);
            if (!isAdded()) {
                if (result != null) result.recycle();
                return;
            }
            requireActivity().runOnUiThread(() -> {
                if (generation != outlinePreviewGeneration.get()
                        || !owner.isShowing()) {
                    if (result != null && !result.isRecycled()) result.recycle();
                    return;
                }
                progressBar.setVisibility(View.GONE);
                if (result == null) {
                    Toast.makeText(requireContext(),
                            R.string.settings_picture_outline_failed,
                            Toast.LENGTH_SHORT).show();
                    return;
                }
                Bitmap previous = displayedPreview[0];
                previewView.setImageBitmap(result);
                displayedPreview[0] = result;
                if (previous != null && !previous.isRecycled()) previous.recycle();
            });
        }, "FloatPicture-outline-preview").start();
    }

    private void applyOutline(Bitmap source, int radius, int contrast, int outlineColor,
                              boolean keepGrayBackground,
                              AlertDialog owner, ProgressBar progressBar) {
        boolean sourcePreserved = ImageMethods.ensureOutlineSource(source, PictureId);
        Bitmap result = sourcePreserved
                ? ImageMethods.createOutline(
                        source, radius, contrast, outlineColor, keepGrayBackground)
                : null;
        int quality = PreferenceManager.getDefaultSharedPreferences(requireContext())
                .getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
        boolean saved = result != null && IOMethods.replaceBitmap(
                result,
                quality,
                Config.DEFAULT_PICTURE_DIR + PictureId);

        if (!isAdded()) {
            if (result != null && !result.isRecycled()) result.recycle();
            return;
        }
        requireActivity().runOnUiThread(() -> {
            if (!saved || result == null) {
                if (result != null && !result.isRecycled()) result.recycle();
                progressBar.setVisibility(View.GONE);
                owner.getButton(AlertDialog.BUTTON_POSITIVE).setEnabled(true);
                owner.getButton(AlertDialog.BUTTON_NEGATIVE).setEnabled(true);
                Toast.makeText(requireContext(),
                        R.string.settings_picture_outline_failed,
                        Toast.LENGTH_SHORT).show();
                return;
            }

            Bitmap previous = bitmap;
            bitmap = result;
            if (floatImageView.isAttachedToWindow()) {
                WindowsMethods.updateWindow(windowManager, floatImageView, bitmap,
                        false, allow_picture_over_layout,
                        zoom_x, zoom_y, picture_degree, position_x, position_y);
            } else {
                floatImageView.configureGestureImage(
                        bitmap, zoom_x, zoom_y, picture_degree);
            }
            floatImageView.setAlpha(picture_alpha);
            if (previous != null && previous != bitmap && !previous.isRecycled()) {
                previous.recycle();
            }
            owner.dismiss();
            Toast.makeText(requireContext(),
                    R.string.settings_picture_outline_success,
                    Toast.LENGTH_SHORT).show();
        });
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        if (requestCode == Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_REPLACE
                && resultCode == Activity.RESULT_OK
                && data != null
                && data.getData() != null) {
            replacePicture(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    private void replacePicture(Uri replacementUri) {
        Activity activity = requireActivity();
        AlertDialog.Builder loading = new AlertDialog.Builder(activity);
        loading.setCancelable(false);
        View loadingView = inflater.inflate(
                R.layout.dialog_loading,
                activity.findViewById(R.id.layout_dialog_loading));
        loading.setView(loadingView);
        AlertDialog loadingDialog = loading.show();

        new Thread(() -> {
            boolean replaced = ImageMethods.replaceImage(activity, replacementUri, PictureId);
            Bitmap replacementBitmap = replaced
                    ? ImageMethods.getShowBitmap(activity, PictureId)
                    : null;
            activity.runOnUiThread(() -> {
                loadingDialog.cancel();
                if (replacementBitmap == null) {
                    Toast.makeText(activity, R.string.action_replace_picture_failed, Toast.LENGTH_SHORT).show();
                    return;
                }

                Bitmap previousBitmap = bitmap;
                bitmap = replacementBitmap;
                if (floatImageView.isAttachedToWindow()) {
                    WindowsMethods.updateWindow(
                            windowManager,
                            floatImageView,
                            bitmap,
                            false,
                            allow_picture_over_layout,
                            zoom_x,
                            zoom_y,
                            picture_degree,
                            position_x,
                            position_y);
                } else {
                    floatImageView.configureGestureImage(
                            bitmap, zoom_x, zoom_y, picture_degree);
                }
                floatImageView.setAlpha(picture_alpha);
                syncPositionToView(floatImageView, position_x, position_y);
                if (previousBitmap != null && !previousBitmap.isRecycled()) {
                    previousBitmap.recycle();
                }
                Toast.makeText(activity, R.string.action_replace_picture_success, Toast.LENGTH_SHORT).show();
            });
        }).start();
    }

    private void setPictureName(Preference preference) {
        View mView = inflater.inflate(R.layout.dialog_edit_text, requireActivity().findViewById(R.id.layout_dialog_edit_text));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_name);
        final EditText editText = mView.findViewById(R.id.edittext_dialog);
        editText.setText(PictureName);
        dialog.setPositiveButton(R.string.done, (dialog12, which) -> {
            if (editText.getText().toString().isEmpty()) {
                Toast.makeText(getActivity(), R.string.settings_picture_name_warn, Toast.LENGTH_SHORT).show();
            } else {
                PictureName = editText.getText().toString();
                preference.setSummary(PictureName);
            }
        });
        dialog.setNegativeButton(R.string.cancel, (dialog1, which) -> {
            if (editText.getText().toString().isEmpty()) {
                Toast.makeText(getActivity(), R.string.settings_picture_name_warn, Toast.LENGTH_SHORT).show();
            }
        });
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.create();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        currentDialog = alertDialog;
        currentDialog.show();
    }

    private void setPictureSize() {
        bitmap_Edit = ImageMethods.getEditBitmap(getActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(getActivity(), bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree);
        onEditPicture(floatImageView_Edit);

        View mView = inflater.inflate(R.layout.dialog_set_resize, requireActivity().findViewById(R.id.layout_dialog_set_resize));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_resize);
        dialog.setCancelable(false);

        final android.widget.CheckBox checkBoxLockRatio = mView.findViewById(R.id.checkbox_lock_ratio);
        // Default to true if zoom_x and zoom_y are roughly equal, otherwise false
        checkBoxLockRatio.setChecked(Math.abs(zoom_x - zoom_y) < 0.001f);

        // 获取包含状态栏和导航栏在内的真实物理尺寸
        android.util.DisplayMetrics dm = new android.util.DisplayMetrics();
        requireActivity().getWindowManager().getDefaultDisplay().getMetrics(dm);

        int baseScreenX = dm.widthPixels;
        int baseScreenY = dm.heightPixels;

        // 将当前旋转角度转换为弧度
        double angleRad = Math.toRadians(picture_degree);
        double absCos = Math.abs(Math.cos(angleRad));
        double absSin = Math.abs(Math.sin(angleRad));

        int w = bitmap.getWidth();
        int h = bitmap.getHeight();

        // 使用基准尺寸 (baseScreenX, baseScreenY) 来计算最大缩放值
        float limitX_W = (float) (absCos > 0.001 ? baseScreenX / (w * absCos) : Float.MAX_VALUE);
        float limitX_H = (float) (absSin > 0.001 ? baseScreenY / (w * absSin) : Float.MAX_VALUE);
        float calculatedMaxZoomX = Math.min(limitX_W, limitX_H);

        float limitY_W = (float) (absSin > 0.001 ? baseScreenX / (h * absSin) : Float.MAX_VALUE);
        float limitY_H = (float) (absCos > 0.001 ? baseScreenY / (h * absCos) : Float.MAX_VALUE);
        float calculatedMaxZoomY = Math.min(limitY_W, limitY_H);
        // ===========================================================================

        if (allow_picture_over_layout) {
            calculatedMaxZoomX = calculatedMaxZoomX * 1.1f;
            calculatedMaxZoomY = calculatedMaxZoomY * 1.1f;
        }

        // 最终限制：不再取最小值，而是分别限制
        final float absoluteMaxZoomX = calculatedMaxZoomX;
        final float absoluteMaxZoomY = calculatedMaxZoomY;

        // X Axis Controls
        final SeekBar seekBar_x = mView.findViewById(R.id.seekbar_set_size_x);
        final EditText editText_x = mView.findViewById(R.id.edittext_set_size_x);
        editText_x.setText(String.format("%.3f", zoom_x));
        editText_x.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        editText_x.setImeOptions(EditorInfo.IME_ACTION_DONE);

        // Y Axis Controls
        final SeekBar seekBar_y = mView.findViewById(R.id.seekbar_set_size_y);
        final EditText editText_y = mView.findViewById(R.id.edittext_set_size_y);
        editText_y.setText(String.format("%.3f", zoom_y));
        editText_y.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_DECIMAL);
        editText_y.setImeOptions(EditorInfo.IME_ACTION_DONE);

        final EditText editTextPixelWidth = mView.findViewById(R.id.edittext_pixel_width);
        final EditText editTextPixelHeight = mView.findViewById(R.id.edittext_pixel_height);
        final View buttonPixelWidthMinus = mView.findViewById(R.id.button_pixel_width_minus);
        final View buttonPixelWidthPlus = mView.findViewById(R.id.button_pixel_width_plus);
        final View buttonPixelHeightMinus = mView.findViewById(R.id.button_pixel_height_minus);
        final View buttonPixelHeightPlus = mView.findViewById(R.id.button_pixel_height_plus);
        final boolean[] pixelAdjustmentActive = {false};
        final boolean[] lastPixelAdjustmentWasWidth = {true};

        editText_x.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pixelAdjustmentActive[0] = false;
        });
        editText_y.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) pixelAdjustmentActive[0] = false;
        });
        editTextPixelWidth.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                pixelAdjustmentActive[0] = true;
                lastPixelAdjustmentWasWidth[0] = true;
            }
        });
        editTextPixelHeight.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus) {
                pixelAdjustmentActive[0] = true;
                lastPixelAdjustmentWasWidth[0] = false;
            }
        });

        zoom_x_temp = zoom_x;
        zoom_y_temp = zoom_y;

        Runnable syncPixelFields = () -> {
            editTextPixelWidth.setText(String.valueOf(getRenderedPixelWidth(zoom_x_temp, zoom_y_temp)));
            editTextPixelHeight.setText(String.valueOf(getRenderedPixelHeight(zoom_x_temp, zoom_y_temp)));
        };

        Runnable refreshSizeControls = () -> {
            seekBar_x.setProgress(Math.round(zoom_x_temp * 1000));
            seekBar_y.setProgress(Math.round(zoom_y_temp * 1000));
            editText_x.setText(String.format("%.3f", zoom_x_temp));
            editText_y.setText(String.format("%.3f", zoom_y_temp));
            syncPixelFields.run();
            WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit,
                    false, allow_picture_over_layout, zoom_x_temp, zoom_y_temp,
                    picture_degree, position_x, position_y);
        };

        // 动态更新最大值逻辑
        Runnable updateLimits = () -> {
            float targetMaxX = absoluteMaxZoomX;
            float targetMaxY = absoluteMaxZoomY;

            if (checkBoxLockRatio.isChecked()) {
                // 如果锁定比例，最大值取两者的较小值，防止一边拖动导致另一边超出屏幕
                float safeMax = Math.min(absoluteMaxZoomX, absoluteMaxZoomY);
                targetMaxX = safeMax;
                targetMaxY = safeMax;
            }

            seekBar_x.setMax((int) (targetMaxX * 1000));
            seekBar_y.setMax((int) (targetMaxY * 1000));

            boolean changed = false;
            // 检查当前值是否超出新限制
            if (zoom_x_temp > targetMaxX) {
                zoom_x_temp = targetMaxX;
                changed = true;
            }
            if (zoom_y_temp > targetMaxY) {
                zoom_y_temp = targetMaxY;
                changed = true;
            }

            if (changed) {
                seekBar_x.setProgress((int) (zoom_x_temp * 1000));
                editText_x.setText(String.format("%.3f", zoom_x_temp));
                seekBar_y.setProgress((int) (zoom_y_temp * 1000));
                editText_y.setText(String.format("%.3f", zoom_y_temp));
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x_temp, zoom_y_temp, picture_degree, position_x, position_y);
            } else {
                // 即使值没变，也要更新 Progress 以匹配新的 Max（如果 seekbar 逻辑需要）
                // 但通常 setMax 会保持 progress 比例或绝对值，这里为了保险重新设置
                seekBar_x.setProgress((int) (zoom_x_temp * 1000));
                seekBar_y.setProgress((int) (zoom_y_temp * 1000));
            }
            syncPixelFields.run();
        };

        // 初始执行一次
        updateLimits.run();

        checkBoxLockRatio.setOnCheckedChangeListener((buttonView, isChecked) -> {
            updateLimits.run();
            if (isChecked) {
                // 锁定瞬间，将 Y 同步为 X (或者取均值? 通常是以 X 为主)
                zoom_y_temp = zoom_x_temp;
                // 再次检查同步后的值是否合规
                float safeMax = Math.min(absoluteMaxZoomX, absoluteMaxZoomY);
                if (zoom_y_temp > safeMax) zoom_y_temp = safeMax;
                zoom_x_temp = zoom_y_temp;

                seekBar_x.setProgress((int) (zoom_x_temp * 1000));
                editText_x.setText(String.format("%.3f", zoom_x_temp));
                seekBar_y.setProgress((int) (zoom_y_temp * 1000));
                editText_y.setText(String.format("%.3f", zoom_y_temp));

                syncPixelFields.run();
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x_temp, zoom_y_temp, picture_degree, position_x, position_y);
            }
        });

        SeekBar.OnSeekBarChangeListener seekBarChangeListener = new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser && progress > 0) {
                    pixelAdjustmentActive[0] = false;
                    float newZoom = progress / 1000.0f;
                    if (seekBar == seekBar_x) {
                        zoom_x_temp = newZoom;

                        editText_x.setText(String.format("%.3f", zoom_x_temp));
                        if (checkBoxLockRatio.isChecked()) {
                            // Link Y to X
                            zoom_y_temp = zoom_x_temp;
                            // Ensure Y does not exceed its own limit if !allow_picture_over_layout
                            // But seekBar_y.setProgress will automatically clamp visual progress to max
                            // However, we should also clamp the value we use for updateWindow
                            if (!allow_picture_over_layout && zoom_y_temp > absoluteMaxZoomY) {
                                // If locked, and X is pushed beyond Y's limit, Y stops at its limit?
                                // Or X is also limited?
                                // If X limit > Y limit, and we drag X to max. Y tries to go to X's max (which is > Y's max).
                                // This would make height > screen height.
                                // If !allow_picture_over_layout, this is invalid.
                                // But if we clamp Y, ratio is broken.
                                // If we don't clamp Y, height > screen.
                                // The user's request "only limited by screen max" implies independence.
                                // If they check "Lock Ratio", they are creating a conflict if limits differ.
                                // Let's prioritize satisfying the "limit" over "ratio" if conflict, or just let it clip?
                                // Standard behavior: clamp to limit.
                                zoom_y_temp = absoluteMaxZoomY;
                            } else if (allow_picture_over_layout && zoom_y_temp > absoluteMaxZoomY) {
                                // Even if allowed over layout, we have a "Max" defined by 1.1x.
                                zoom_y_temp = absoluteMaxZoomY;
                            }

                            seekBar_y.setProgress((int)(zoom_y_temp * 1000));
                            editText_y.setText(String.format("%.3f", zoom_y_temp));
                        }
                    } else if (seekBar == seekBar_y) {
                        zoom_y_temp = newZoom;
                        editText_y.setText(String.format("%.3f", zoom_y_temp));
                        if (checkBoxLockRatio.isChecked()) {
                            zoom_x_temp = zoom_y_temp;
                            if (!allow_picture_over_layout && zoom_x_temp > absoluteMaxZoomX) {
                                zoom_x_temp = absoluteMaxZoomX;
                            } else if (allow_picture_over_layout && zoom_x_temp > absoluteMaxZoomX) {
                                zoom_x_temp = absoluteMaxZoomX;
                            }
                            seekBar_x.setProgress((int)(zoom_x_temp * 1000));
                            editText_x.setText(String.format("%.3f", zoom_x_temp));
                        }
                    }
                    syncPixelFields.run();
                    WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x_temp, zoom_y_temp, picture_degree, position_x, position_y);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        };

        seekBar_x.setOnSeekBarChangeListener(seekBarChangeListener);
        seekBar_y.setOnSeekBarChangeListener(seekBarChangeListener);

        TextView.OnEditorActionListener editorActionListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                try {
                    String input = v.getText().toString().trim();
                    if (!input.isEmpty()) {
                        float inputVal = Float.parseFloat(input);
                        pixelAdjustmentActive[0] = false;
                        float minZoom = 0.1f;
                        if (inputVal < minZoom) inputVal = minZoom;

                        // Check against specific limits
                        float currentMax = (v == editText_x) ? absoluteMaxZoomX : absoluteMaxZoomY;
                        if (inputVal > currentMax) inputVal = currentMax;

                        v.setText(String.format("%.3f", inputVal));

                        if (v == editText_x) {
                            zoom_x_temp = inputVal;
                            seekBar_x.setProgress((int) (zoom_x_temp * 1000));

                            if (checkBoxLockRatio.isChecked()) {
                                zoom_y_temp = zoom_x_temp;
                                if (zoom_y_temp > absoluteMaxZoomY) zoom_y_temp = absoluteMaxZoomY;
                                editText_y.setText(String.format("%.3f", zoom_y_temp));
                                seekBar_y.setProgress((int) (zoom_y_temp * 1000));
                            }
                        } else if (v == editText_y) {
                            zoom_y_temp = inputVal;
                            seekBar_y.setProgress((int) (zoom_y_temp * 1000));

                            if (checkBoxLockRatio.isChecked()) {
                                zoom_x_temp = zoom_y_temp;
                                if (zoom_x_temp > absoluteMaxZoomX) zoom_x_temp = absoluteMaxZoomX;
                                editText_x.setText(String.format("%.3f", zoom_x_temp));
                                seekBar_x.setProgress((int) (zoom_x_temp * 1000));
                            }
                        }

                        syncPixelFields.run();
                        WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit,
                                false, allow_picture_over_layout, zoom_x_temp, zoom_y_temp, picture_degree,
                                position_x, position_y);

                        InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                        if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                        v.clearFocus();
                        return true;
                    }
                } catch (NumberFormatException e) {
                    Toast.makeText(getActivity(), R.string.settings_picture_size_warn, Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        };

        editText_x.setOnEditorActionListener(editorActionListener);
        editText_y.setOnEditorActionListener(editorActionListener);

        TextView.OnEditorActionListener pixelEditorActionListener = (v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                try {
                    int targetPixels = Integer.parseInt(v.getText().toString().trim());
                    if (targetPixels <= 0) throw new NumberFormatException();
                    boolean changeWidth = v == editTextPixelWidth;
                    pixelAdjustmentActive[0] = true;
                    lastPixelAdjustmentWasWidth[0] = changeWidth;
                    applyRenderedPixelSize(changeWidth, targetPixels,
                            checkBoxLockRatio.isChecked(), absoluteMaxZoomX, absoluteMaxZoomY);
                    refreshSizeControls.run();

                    InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                    if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                    v.clearFocus();
                    return true;
                } catch (NumberFormatException e) {
                    Toast.makeText(getActivity(), R.string.settings_picture_resize_warn, Toast.LENGTH_SHORT).show();
                }
            }
            return false;
        };
        editTextPixelWidth.setOnEditorActionListener(pixelEditorActionListener);
        editTextPixelHeight.setOnEditorActionListener(pixelEditorActionListener);

        View.OnClickListener pixelStepListener = v -> {
            boolean changeWidth = v == buttonPixelWidthMinus || v == buttonPixelWidthPlus;
            pixelAdjustmentActive[0] = true;
            lastPixelAdjustmentWasWidth[0] = changeWidth;
            int currentPixels = changeWidth
                    ? getRenderedPixelWidth(zoom_x_temp, zoom_y_temp)
                    : getRenderedPixelHeight(zoom_x_temp, zoom_y_temp);
            int delta = (v == buttonPixelWidthMinus || v == buttonPixelHeightMinus) ? -1 : 1;
            applyRenderedPixelSize(changeWidth, Math.max(1, currentPixels + delta),
                    checkBoxLockRatio.isChecked(), absoluteMaxZoomX, absoluteMaxZoomY);
            refreshSizeControls.run();
        };
        buttonPixelWidthMinus.setOnClickListener(pixelStepListener);
        buttonPixelWidthPlus.setOnClickListener(pixelStepListener);
        buttonPixelHeightMinus.setOnClickListener(pixelStepListener);
        buttonPixelHeightPlus.setOnClickListener(pixelStepListener);

        dialog.setPositiveButton(R.string.done, (__, which) -> {
            // 【新增/修改】：在保存前，强制从 EditText 获取最新值
            try {
                if (pixelAdjustmentActive[0]) {
                    EditText activePixelField = lastPixelAdjustmentWasWidth[0]
                            ? editTextPixelWidth : editTextPixelHeight;
                    int targetPixels = Integer.parseInt(activePixelField.getText().toString().trim());
                    if (targetPixels > 0) {
                        applyRenderedPixelSize(lastPixelAdjustmentWasWidth[0], targetPixels,
                                checkBoxLockRatio.isChecked(), absoluteMaxZoomX, absoluteMaxZoomY);
                    }
                } else {
                    String inputX = editText_x.getText().toString().trim();
                    String inputY = editText_y.getText().toString().trim();
                    if (!inputX.isEmpty()) zoom_x_temp = Float.parseFloat(inputX);
                    if (!inputY.isEmpty()) zoom_y_temp = Float.parseFloat(inputY);
                }
            } catch (Exception e) {
                e.printStackTrace();
            }

            zoom_x = zoom_x_temp;
            zoom_y = zoom_y_temp;
            onSuccessEditPicture(floatImageView_Edit, bitmap_Edit);
        });

        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture(floatImageView_Edit, bitmap_Edit));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.create();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        showTranslucentAdjustmentDialog(alertDialog);
    }

    private int getRenderedPixelWidth(float zoomX, float zoomY) {
        double radians = Math.toRadians(picture_degree);
        double absCos = Math.abs(Math.cos(radians));
        double absSin = Math.abs(Math.sin(radians));
        return Math.max(1, (int) Math.round(
                bitmap.getWidth() * zoomX * absCos + bitmap.getHeight() * zoomY * absSin));
    }

    private int getRenderedPixelHeight(float zoomX, float zoomY) {
        double radians = Math.toRadians(picture_degree);
        double absCos = Math.abs(Math.cos(radians));
        double absSin = Math.abs(Math.sin(radians));
        return Math.max(1, (int) Math.round(
                bitmap.getWidth() * zoomX * absSin + bitmap.getHeight() * zoomY * absCos));
    }

    private void applyRenderedPixelSize(boolean changeWidth, int targetPixels,
                                        boolean lockRatio, float maxZoomX, float maxZoomY) {
        final float minZoom = 0.1f;
        double radians = Math.toRadians(picture_degree);
        double absCos = Math.abs(Math.cos(radians));
        double absSin = Math.abs(Math.sin(radians));

        if (lockRatio) {
            int currentPixels = changeWidth
                    ? getRenderedPixelWidth(zoom_x_temp, zoom_y_temp)
                    : getRenderedPixelHeight(zoom_x_temp, zoom_y_temp);
            float factor = targetPixels / (float) Math.max(1, currentPixels);
            float minFactor = Math.max(minZoom / zoom_x_temp, minZoom / zoom_y_temp);
            float maxFactor = Math.min(maxZoomX / zoom_x_temp, maxZoomY / zoom_y_temp);
            factor = Math.max(minFactor, Math.min(factor, maxFactor));
            zoom_x_temp *= factor;
            zoom_y_temp *= factor;
            return;
        }

        double sourceWidth = bitmap.getWidth();
        double sourceHeight = bitmap.getHeight();
        // Keep width/height controls intuitive across rotation: near 0 degrees,
        // width changes source X and height changes source Y; near 90 degrees they swap.
        boolean useZoomX = changeWidth ? absCos >= absSin : absSin > absCos;
        if (changeWidth) {
            if (useZoomX && sourceWidth * absCos > 0.0001) {
                zoom_x_temp = (float) ((targetPixels - sourceHeight * zoom_y_temp * absSin)
                        / (sourceWidth * absCos));
            } else if (sourceHeight * absSin > 0.0001) {
                zoom_y_temp = (float) ((targetPixels - sourceWidth * zoom_x_temp * absCos)
                        / (sourceHeight * absSin));
            }
        } else {
            if (useZoomX && sourceWidth * absSin > 0.0001) {
                zoom_x_temp = (float) ((targetPixels - sourceHeight * zoom_y_temp * absCos)
                        / (sourceWidth * absSin));
            } else if (sourceHeight * absCos > 0.0001) {
                zoom_y_temp = (float) ((targetPixels - sourceWidth * zoom_x_temp * absSin)
                        / (sourceHeight * absCos));
            }
        }

        zoom_x_temp = Math.max(minZoom, Math.min(zoom_x_temp, maxZoomX));
        zoom_y_temp = Math.max(minZoom, Math.min(zoom_y_temp, maxZoomY));
    }

    private void setPictureDegree() {
        bitmap_Edit = ImageMethods.getEditBitmap(getActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(getActivity(), bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree);
        onEditPicture(floatImageView_Edit);

        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_degree);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.degree);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(8);
        seekBar.setProgress((int) (picture_degree / 45));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        editText.setText(String.valueOf((int) picture_degree));
        picture_degree_temp = picture_degree;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) {
                    // 核心修改：每个进度代表 45 度
                    picture_degree_temp = progress * 45;

                    editText.setText(String.valueOf((int) picture_degree_temp));

                    WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit,
                            false, allow_picture_over_layout, zoom_x, zoom_y,
                            picture_degree_temp, position_x, position_y);
                }
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        // A. 处理软键盘上的 Done 或 实体键盘的回车
        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE || actionId == EditorInfo.IME_ACTION_UNSPECIFIED) {
                applyRotationInput(editText, seekBar);

                // 收起键盘并清除焦点
                InputMethodManager imm = (InputMethodManager) requireContext().getSystemService(Context.INPUT_METHOD_SERVICE);
                if (imm != null) imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                v.clearFocus();
                return true;
            }
            return false;
        });

        // B. 处理点击界面其他地方 (比如对话框的确定按钮)
        editText.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) {
                // 只要焦点一离开输入框，立刻应用数字
                applyRotationInput(editText, seekBar);
            }
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            // 【新增】：确保点击确定时，最后一次手动输入的数字被应用
            applyRotationInput(editText, seekBar);
            picture_degree = picture_degree_temp;
            onSuccessEditPicture(floatImageView_Edit, bitmap_Edit);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture(floatImageView_Edit, bitmap_Edit));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.create();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        showTranslucentAdjustmentDialog(alertDialog);
    }
    private void applyRotationInput(EditText v, SeekBar seekBar) {
        try {
            String input;
            input = v.getText().toString().trim();
            if (input.isEmpty()) return;
            float inputVal = Float.parseFloat(input);

            if (inputVal < 0) inputVal = 0;
            if (inputVal > 360) inputVal = 360;

            picture_degree_temp = inputVal;
            int nearestProgress = Math.round(inputVal / 45f);
            seekBar.setProgress(nearestProgress);

            // 确保文字显示用户输入的精确值
            v.setText(String.valueOf((int)inputVal));

            WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit,
                    false, allow_picture_over_layout, zoom_x, zoom_y,
                    picture_degree_temp, position_x, position_y);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
    private void setPictureAlpha() {
        View mView = inflater.inflate(R.layout.dialog_set_size, requireActivity().findViewById(R.id.layout_dialog_set_size));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_alpha);
        dialog.setCancelable(false);
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.transparency);
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        seekBar.setMax(100);
        seekBar.setProgress((int) (picture_alpha * 100));
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        editText.setText(String.valueOf(picture_alpha));
        picture_alpha_temp = picture_alpha;
        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                picture_alpha_temp = ((float) progress) / 100;
                editText.setText(String.valueOf(picture_alpha_temp));
                floatImageView.setAlpha(picture_alpha_temp);
                WindowsMethods.updateWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
                syncPositionToView(floatImageView, position_x, position_y);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText.setOnEditorActionListener((v, actionId, event) -> {
            float edittext_temp = Float.parseFloat(v.getText().toString());
            if (edittext_temp >= 0 && edittext_temp <= 100) {
                picture_alpha_temp = edittext_temp;
                seekBar.setProgress((int) (picture_alpha_temp * 100));
                floatImageView.setAlpha(picture_alpha_temp);
                WindowsMethods.updateWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
                syncPositionToView(floatImageView, position_x, position_y);
            } else {
                Toast.makeText(getActivity(), R.string.settings_number_warn, Toast.LENGTH_SHORT).show();
            }
            return false;
        });
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            // 【新增】：手动从输入框提取一次
            try {
                String input = editText.getText().toString().trim();
                if (!input.isEmpty()) {
                    picture_alpha_temp = Float.parseFloat(input);
                }
            } catch (Exception e) { }

            picture_alpha = picture_alpha_temp;
            floatImageView.setAlpha(picture_alpha);
            WindowsMethods.updateWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
            syncPositionToView(floatImageView, position_x, position_y);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> {
            floatImageView.setAlpha(picture_alpha);
            WindowsMethods.updateWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
            syncPositionToView(floatImageView, position_x, position_y);
        });
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.create();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        showTranslucentAdjustmentDialog(alertDialog);
    }

    private void syncPositionToView(FloatImageView view, int x, int y) {
        if (view != null) {
            view.setWindowPosition(x, y);
        }
    }

    private void setPicturePosition() {
        bitmap_Edit = ImageMethods.getEditBitmap(getActivity(), bitmap);
        floatImageView_Edit = ImageMethods.createPictureView(getActivity(), bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree);
        onEditPicture(floatImageView_Edit);

        View mView = inflater.inflate(R.layout.dialog_set_position, requireActivity().findViewById(R.id.layout_dialog_set_position));
        AlertDialog.Builder dialog = new AlertDialog.Builder(requireContext());
        dialog.setTitle(R.string.settings_picture_position);
        dialog.setCancelable(false);
        Point size = new Point();
        requireActivity().getWindowManager().getDefaultDisplay().getSize(size);
        final int Max_X = size.x;
        final int Max_Y = size.y;
        final int min_X = allow_picture_over_layout ? -Max_X : 0;
        final int min_Y = allow_picture_over_layout ? -Max_Y : 0;
        final SeekBar seekBar_x = mView.findViewById(R.id.seekbar_set_position_x);
        seekBar_x.setMax(Max_X - min_X);
        seekBar_x.setProgress(position_x - min_X);
        final EditText editText_x = mView.findViewById(R.id.edittext_set_position_x);
        editText_x.setText(String.valueOf(position_x));
        final SeekBar seekBar_y = mView.findViewById(R.id.seekbar_set_position_y);
        seekBar_y.setMax(Max_Y - min_Y);
        seekBar_y.setProgress(position_y - min_Y);
        final EditText editText_y = mView.findViewById(R.id.edittext_set_position_y);
        editText_y.setText(String.valueOf(position_y));
        if (allow_picture_over_layout) {
            editText_x.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
            editText_y.setInputType(InputType.TYPE_CLASS_NUMBER | InputType.TYPE_NUMBER_FLAG_SIGNED);
        }
        position_x_temp = position_x;
        position_y_temp = position_y;
        seekBar_x.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                position_x_temp = progress + min_X;
                editText_x.setText(String.valueOf(position_x_temp));
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree, position_x_temp, position_y_temp);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText_x.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE ||
                    actionId == EditorInfo.IME_ACTION_UNSPECIFIED ||
                    actionId == EditorInfo.IME_ACTION_NEXT) {
                try {
                    String input = v.getText().toString();
                    if (!input.isEmpty()) {
                        int edittext_temp = (int) Float.parseFloat(input);

                        // 2. 边界逻辑（使用你之前计算好的 Max_X）
                        if (allow_picture_over_layout || (edittext_temp >= 0 && edittext_temp <= Max_X)) {
                            position_x_temp = edittext_temp;
                            if (edittext_temp >= min_X && edittext_temp <= Max_X) {
                                seekBar_x.setProgress(edittext_temp - min_X);
                            }
                            // 更新窗口
                            Toast.makeText(getActivity(), "正在更新坐标到: " + position_x_temp, Toast.LENGTH_SHORT).show();
                            WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree, position_x_temp, position_y_temp);

                            // 3. 隐藏键盘逻辑
                            InputMethodManager imm = (InputMethodManager) getActivity().getSystemService(Context.INPUT_METHOD_SERVICE);
                            if (imm != null) {
                                imm.hideSoftInputFromWindow(v.getWindowToken(), 0);
                            }
                            v.clearFocus();
                            return true; // 成功处理，返回 true
                        } else {
                            Toast.makeText(getActivity(), R.string.settings_picture_position_warn, Toast.LENGTH_SHORT).show();
                        }
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
            return false;
        });
        seekBar_y.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                position_y_temp = progress + min_Y;
                editText_y.setText(String.valueOf(position_y_temp));
                WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false, allow_picture_over_layout, zoom_x, zoom_y, picture_degree, position_x_temp, position_y_temp);
            }

            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {
            }

            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {
            }
        });
        editText_y.setOnEditorActionListener((v, actionId, event) -> {
            try {
                String input = v.getText().toString();
                if (!input.isEmpty()) {
                    // 使用 Float 转换防止输入小数点时崩溃
                    int edittext_temp = (int) Float.parseFloat(input);

                    if (allow_picture_over_layout || (edittext_temp >= 0 && edittext_temp <= Max_Y)) {
                        position_y_temp = edittext_temp;
                        if (edittext_temp >= min_Y && edittext_temp <= Max_Y) {
                            seekBar_y.setProgress(edittext_temp - min_Y);
                        }
                        // 执行窗口更新
                        WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false,
                                allow_picture_over_layout, zoom_x, zoom_y, picture_degree, position_x_temp, position_y_temp);

                        // 移除焦点，这样用户知道已经输入成功了
                        v.clearFocus();
                        return true; // 修改这里：返回 true 确保动作被执行
                    } else {
                        Toast.makeText(getActivity(), R.string.settings_picture_position_warn, Toast.LENGTH_SHORT).show();
                    }
                }
            } catch (Exception e) {
                e.printStackTrace();
            }
            return false;
        });

// 2. 增加失去焦点监听 (针对模拟器没有软键盘的情况)
        editText_y.setOnFocusChangeListener((v, hasFocus) -> {
            if (!hasFocus) { // 当鼠标点击其他地方，输入框失去焦点时，自动应用数字
                try {
                    String input = editText_y.getText().toString();
                    if (!input.isEmpty()) {
                        int val = (int) Float.parseFloat(input);
                        // 简单的边界纠正
                        if (!allow_picture_over_layout) {
                            val = Math.max(0, Math.min(val, Max_Y));
                        }
                        position_y_temp = val;

                        WindowsMethods.updateWindow(windowManager, floatImageView_Edit, bitmap_Edit, false,
                                allow_picture_over_layout, zoom_x, zoom_y, picture_degree, position_x_temp, position_y_temp);
                    }
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        });
        if (allow_picture_over_layout) {

        }
        dialog.setPositiveButton(R.string.done, (__, which) -> {
            // 【核心修复】：在点击“确定”时，强制从 EditText 中重新抓取一次数值
            try {
                String inputX = editText_x.getText().toString().trim();
                String inputY = editText_y.getText().toString().trim();

                if (!inputX.isEmpty()) {
                    position_x_temp = (int) Float.parseFloat(inputX);
                }
                if (!inputY.isEmpty()) {
                    position_y_temp = (int) Float.parseFloat(inputY);
                }
            } catch (Exception e) {
                // 如果输入了非法字符，则回退到最后一次有效的 temp 值
                e.printStackTrace();
            }

            // 接下来再进行最终赋值
            if (allow_picture_over_layout) {
                // 在允许超出边界的情况下，直接使用抓取到的值
                position_x = position_x_temp;
                position_y = position_y_temp;
            } else {
                // 如果不允许超出边界，可以加一个简单的范围限制（兜底）
                position_x = Math.max(0, Math.min(position_x_temp, Max_X));
                position_y = Math.max(0, Math.min(position_y_temp, Max_Y));
            }

            onSuccessEditPicture(floatImageView_Edit, bitmap_Edit);
        });
        dialog.setNegativeButton(R.string.cancel, (__, which) -> onFailedEditPicture(floatImageView_Edit, bitmap_Edit));
        dialog.setView(mView);
        AlertDialog alertDialog = dialog.create();
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY);
        } else {
            alertDialog.getWindow().setType(WindowManager.LayoutParams.TYPE_SYSTEM_ALERT);
        }
        showTranslucentAdjustmentDialog(alertDialog);
    }

    private void onEditPicture(FloatImageView FloatImageView_Edit) {
        if (!onUseEditPicture) {
            windowManager.removeView(floatImageView);
            floatImageView.refreshDrawableState();
            WindowsMethods.createWindow(windowManager, FloatImageView_Edit, false,
                    allow_picture_over_layout, position_x, position_y);
            syncPositionToView(FloatImageView_Edit, position_x, position_y);
            onUseEditPicture = true;
        }
    }

    private void showTranslucentAdjustmentDialog(AlertDialog dialog) {
        currentDialog = dialog;
        currentDialog.show();
        if (currentDialog.getWindow() != null) {
            Drawable background = currentDialog.getWindow().getDecorView().getBackground();
            if (background != null) {
                background = background.mutate();
                background.setAlpha(204);
                currentDialog.getWindow().getDecorView().setBackground(background);
            }
        }
    }

    private void onSuccessEditPicture(FloatImageView floatImageView_Edit, Bitmap bitmap_Edit) {
        if (onUseEditPicture) {
            windowManager.removeView(floatImageView_Edit);
            floatImageView_Edit.refreshDrawableState();
            bitmap_Edit.recycle();
            floatImageView.configureGestureImage(bitmap, zoom_x, zoom_y, picture_degree);
            WindowsMethods.createWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
            syncPositionToView(floatImageView, position_x, position_y);
            onUseEditPicture = false;
        }
    }

    private void onFailedEditPicture(FloatImageView floatImageView_Edit, Bitmap bitmap_Edit) {
        if (onUseEditPicture) {
            windowManager.removeView(floatImageView_Edit);
            floatImageView_Edit.refreshDrawableState();
            bitmap_Edit.recycle();
            WindowsMethods.createWindow(windowManager, floatImageView, false, allow_picture_over_layout, position_x, position_y);
            syncPositionToView(floatImageView, position_x, position_y);
            onUseEditPicture = false;
        }
    }

    public void saveAllData() {
        pictureData.put(Config.DATA_PICTURE_SHOW_ENABLED, Edit_Mode ? originallyVisible : true);
        pictureData.put(Config.DATA_PICTURE_ZOOM, zoom_x); // Backward compatibility: store X as main ZOOM? Or just ignore ZOOM? Let's update ZOOM to match X.
        pictureData.put(Config.DATA_PICTURE_ZOOM_X, zoom_x);
        pictureData.put(Config.DATA_PICTURE_ZOOM_Y, zoom_y);
        pictureData.put(Config.DATA_PICTURE_DEFAULT_ZOOM, default_zoom);
        pictureData.put(Config.DATA_PICTURE_ALPHA, picture_alpha);
        pictureData.put(Config.DATA_PICTURE_POSITION_X, position_x);
        pictureData.put(Config.DATA_PICTURE_POSITION_Y, position_y);
        pictureData.put(Config.DATA_PICTURE_DEGREE, picture_degree);
        pictureData.commit(PictureName);
        boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
        boolean global_rotatable = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
        boolean effective_over_layout = ManageMethods.resolvePictureOverLayout(requireContext());
        floatImageView.setMoveable(global_touchable);
        floatImageView.setScalable(global_touchable);
        floatImageView.setRotatable(global_rotatable);
        floatImageView.setOverLayout(effective_over_layout);
        WindowsMethods.updateWindow(windowManager, floatImageView, bitmap, global_touchable || global_rotatable, effective_over_layout, zoom_x, zoom_y, picture_degree, position_x, position_y);
        syncPositionToView(floatImageView, position_x, position_y);
        ImageMethods.saveFloatImageViewById(requireActivity(), PictureId, floatImageView);
        if (Edit_Mode) {
            ManageMethods.finishWindowEditing(requireContext(), PictureId, originallyVisible);
        } else if (!ManageMethods.allowsMultiplePictures(requireContext())) {
            ManageMethods.setWindowVisible(requireContext(), pictureData, PictureId, true);
        }
    }

    public void clearEditView() {
        if (onUseEditPicture) {
            if (floatImageView_Edit != null && bitmap_Edit != null) {
                onFailedEditPicture(floatImageView_Edit, bitmap_Edit);
            }
        }
    }

    public void exit() {
        if (!Edit_Mode) {
            if (floatImageView != null) {
                windowManager.removeView(floatImageView);
                bitmap.recycle();
                floatImageView = null;
            }
            ImageMethods.clearAllTemp(requireActivity(), PictureId);
        } else {
            float original_zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, zoom_x);
            float original_zoom_x = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_X, original_zoom);
            float original_zoom_y = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_Y, original_zoom);

            float original_alpha = pictureData.getFloat(Config.DATA_PICTURE_ALPHA, picture_alpha);
            float original_degree = pictureData.getFloat(Config.DATA_PICTURE_DEGREE, picture_degree);
            int original_position_x = pictureData.getInt(Config.DATA_PICTURE_POSITION_X, position_x);
            int original_position_y = pictureData.getInt(Config.DATA_PICTURE_POSITION_Y, position_y);
            boolean global_touchable = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT, false);
            boolean global_rotatable = PreferenceManager.getDefaultSharedPreferences(requireContext()).getBoolean(Config.PREFERENCE_PINCH_ROTATION, false);
            boolean effective_over_layout = ManageMethods.resolvePictureOverLayout(requireContext());
            floatImageView.setAlpha(original_alpha);
            floatImageView.setOverLayout(effective_over_layout);
            floatImageView.setMoveable(global_touchable);
            floatImageView.setScalable(global_touchable);
            floatImageView.setRotatable(global_rotatable);
            WindowsMethods.updateWindow(windowManager, floatImageView, bitmap, global_touchable || global_rotatable, effective_over_layout, original_zoom_x, original_zoom_y, original_degree, original_position_x, original_position_y);
            syncPositionToView(floatImageView, original_position_x, original_position_y);
            ManageMethods.finishWindowEditing(requireContext(), PictureId, originallyVisible);
        }

    }

}
