package tool.xfy9326.floatpicture.View;

import android.content.Intent;
import android.content.SharedPreferences;
import android.content.Context;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.LayoutInflater;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.EditText;
import android.widget.SeekBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.preference.Preference;
import androidx.preference.PreferenceFragmentCompat;
import androidx.preference.PreferenceManager;

import java.util.Objects;

import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.LegacyDataImporter;
import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

public class GlobalSettingsFragment extends PreferenceFragmentCompat {
    private SharedPreferences sharedPreferences;
    private Preference legacyImportPreference;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        // 必须在 super 之前初始化，防止 onCreatePreferences 调用时为空
        sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        super.onCreate(savedInstanceState);
    }

    @Override
    public void onCreatePreferences(Bundle savedInstanceState, String rootKey) {
        addPreferencesFromResource(R.xml.fragment_global_settings);
        PreferenceSet();
    }

    @NonNull
    private Preference requirePreference(CharSequence key) {
        return Objects.requireNonNull(findPreference(key));
    }

    private void PreferenceSet() {
        // 容错处理：如果调用此方法时 sharedPreferences 仍为空（虽然 onCreate 已处理）
        if (sharedPreferences == null) {
            sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireContext());
        }

        Preference qualityPref = requirePreference(Config.PREFERENCE_NEW_PICTURE_QUALITY);
        int currentVal = sharedPreferences.getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
        qualityPref.setSummary(currentVal + "%");

        qualityPref.setOnPreferenceClickListener(preference -> {
            PictureQualitySet(preference);
            return true;
        });

        legacyImportPreference = requirePreference(Config.PREFERENCE_IMPORT_LEGACY_DATA);
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.LOLLIPOP) {
            legacyImportPreference.setEnabled(false);
            legacyImportPreference.setSummary(R.string.legacy_import_unsupported);
        }
        legacyImportPreference.setOnPreferenceClickListener(preference -> {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.legacy_import_confirm_title)
                    .setMessage(R.string.legacy_import_confirm_message)
                    .setPositiveButton(R.string.select_folder, (dialog, which) -> {
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                            Intent intent = new Intent(Intent.ACTION_OPEN_DOCUMENT_TREE);
                            intent.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION
                                    | Intent.FLAG_GRANT_PREFIX_URI_PERMISSION);
                            startActivityForResult(
                                    intent, Config.REQUEST_CODE_ACTIVITY_IMPORT_LEGACY_DATA);
                        }
                    })
                    .setNegativeButton(R.string.cancel, null)
                    .show();
            return true;
        });
        
        requirePreference(Config.PREFERENCE_SHOW_NOTIFICATION_CONTROL).setOnPreferenceChangeListener((preference, newValue) -> {
            Toast.makeText(getActivity(), R.string.restart_to_apply_changes, Toast.LENGTH_SHORT).show();
            return true;
        });

        requirePreference(Config.PREFERENCE_SHOW_FLOATING_CONTROL)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    requireActivity().getWindow().getDecorView().post(() -> {
                        if (isAdded()) {
                            requireContext().sendBroadcast(new Intent(
                                    Config.INTENT_ACTION_FLOATING_CONTROL_REFRESH)
                                    .setPackage(requireContext().getPackageName()));
                        }
                    });
                    return true;
                });

        requirePreference(Config.PREFERENCE_ALLOW_MULTIPLE_FLOATING_PICTURES)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    if (!((Boolean) newValue)) {
                        ManageMethods.enforceSingleVisiblePicture(requireContext());
                    }
                    return true;
                });

        requirePreference(Config.PREFERENCE_TOUCHABLE_POSITION_EDIT)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    refreshGlobalDragWindowState();
                    return true;
                });

        requirePreference(Config.PREFERENCE_ALLOW_GLOBAL_DRAG_OVER_SCREEN)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    refreshGlobalDragWindowState();
                    return true;
                });

        requirePreference(Config.PREFERENCE_PINCH_ROTATION)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    ManageMethods.setRotationGestureEnabled(
                            requireContext(), (Boolean) newValue);
                    return true;
                });

        requirePreference(Config.PREFERENCE_SAVE_GESTURE_ADJUSTMENTS)
                .setOnPreferenceChangeListener((preference, newValue) -> {
                    requireActivity().getWindow().getDecorView().post(() -> {
                        if (isAdded()) {
                            ManageMethods.updateNotificationCount(requireContext());
                        }
                    });
                    return true;
                });

    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Config.REQUEST_CODE_ACTIVITY_IMPORT_LEGACY_DATA
                && resultCode == android.app.Activity.RESULT_OK
                && data != null
                && data.getData() != null
                && Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
            importLegacyFolder(data.getData());
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private void importLegacyFolder(Uri treeUri) {
        if (treeUri == null || legacyImportPreference == null) {
            return;
        }
        try {
            requireContext().getContentResolver().takePersistableUriPermission(
                    treeUri, Intent.FLAG_GRANT_READ_URI_PERMISSION);
        } catch (SecurityException ignored) {
            // The temporary grant is sufficient for the immediate import.
        }

        legacyImportPreference.setEnabled(false);
        legacyImportPreference.setSummary(R.string.legacy_import_in_progress);
        Context applicationContext = requireContext().getApplicationContext();
        android.app.Activity activity = requireActivity();
        new Thread(() -> {
            LegacyDataImporter.Result result = LegacyDataImporter.importFrom(
                    applicationContext, treeUri);
            activity.runOnUiThread(() -> showLegacyImportResult(result));
        }).start();
    }

    @RequiresApi(Build.VERSION_CODES.LOLLIPOP)
    private void showLegacyImportResult(LegacyDataImporter.Result result) {
        if (!isAdded()) {
            return;
        }
        if (legacyImportPreference != null) {
            legacyImportPreference.setEnabled(true);
            legacyImportPreference.setSummary(R.string.settings_import_legacy_data_sum);
        }
        if (result.isSuccessful()) {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.legacy_import_success_title)
                    .setMessage(getString(
                            result.getMessageResource(), result.getImportedFileCount()))
                    .setPositiveButton(R.string.restart_now, (dialog, which) -> reloadImportedData())
                    .setCancelable(false)
                    .show();
        } else {
            new AlertDialog.Builder(requireActivity())
                    .setTitle(R.string.legacy_import_failed_title)
                    .setMessage(result.getMessageResource())
                    .setPositiveButton(R.string.done, null)
                    .show();
        }
    }

    private void reloadImportedData() {
        ManageMethods.prepareForDataReload(requireContext());
        Intent intent = new Intent(requireContext(), MainActivity.class);
        intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(intent);
        requireActivity().finishAffinity();
    }

    private void refreshGlobalDragWindowState() {
        requireActivity().getWindow().getDecorView().post(() -> {
            if (isAdded()) {
                // Keep overlays non-touchable while this settings screen is open,
                // but immediately apply the latest over-screen boundary setting.
                ManageMethods.updateAllWindowsGestureState(requireActivity(), false);
            }
        });
    }

    private void PictureQualitySet(Preference preference) {
        int picture_size = sharedPreferences.getInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, 80);
        View mView = LayoutInflater.from(requireContext()).inflate(R.layout.dialog_set_size, null);
        AlertDialog.Builder builder = new AlertDialog.Builder(requireActivity());
        builder.setTitle(R.string.settings_global_picture_quality);
        
        TextView name = mView.findViewById(R.id.textview_set_size);
        name.setText(R.string.settings_global_picture_quality_quality);
        
        final SeekBar seekBar = mView.findViewById(R.id.seekbar_set_size);
        final EditText editText = mView.findViewById(R.id.edittext_set_size);
        
        seekBar.setMax(100);
        seekBar.setProgress(picture_size);
        editText.setText(String.valueOf(picture_size));

        seekBar.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener() {
            @Override
            public void onProgressChanged(SeekBar seekBar, int progress, boolean fromUser) {
                if (fromUser) editText.setText(String.valueOf(progress));
            }
            @Override
            public void onStartTrackingTouch(SeekBar seekBar) {}
            @Override
            public void onStopTrackingTouch(SeekBar seekBar) {}
        });

        editText.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}
            @Override
            public void afterTextChanged(Editable s) {
                try {
                    String str = s.toString();
                    if (!str.isEmpty()) {
                        int val = Integer.parseInt(str);
                        if (val >= 0 && val <= 100) seekBar.setProgress(val);
                    }
                } catch (Exception ignored) {}
            }
        });

        builder.setView(mView);
        AlertDialog dialog = builder.create();

        Runnable saveAction = () -> {
            try {
                String input = editText.getText().toString();
                int quality = Integer.parseInt(input);
                if (quality >= 1 && quality <= 100) {
                    sharedPreferences.edit().putInt(Config.PREFERENCE_NEW_PICTURE_QUALITY, quality).apply();
                    preference.setSummary(quality + "%");
                    dialog.dismiss();
                    Toast.makeText(getContext(), R.string.done, Toast.LENGTH_SHORT).show();
                } else {
                    Toast.makeText(getActivity(), R.string.settings_global_picture_quality_warn, Toast.LENGTH_SHORT).show();
                }
            } catch (Exception e) {
                Toast.makeText(getActivity(), R.string.settings_global_picture_quality_warn, Toast.LENGTH_SHORT).show();
            }
        };

        editText.setOnEditorActionListener((v, actionId, event) -> {
            if (actionId == EditorInfo.IME_ACTION_DONE) {
                saveAction.run();
                return true;
            }
            return false;
        });

        dialog.setButton(AlertDialog.BUTTON_POSITIVE, getString(R.string.done), (__, ___) -> saveAction.run());
        dialog.setButton(AlertDialog.BUTTON_NEGATIVE, getString(R.string.cancel), (__, ___) -> {});
        
        dialog.show();
    }

    @Override
    public void onResume() {
        super.onResume();
        ManageMethods.updateAllWindowsGestureState(requireActivity(), false);
    }

    @Override
    public void onPause() {
        super.onPause();
        ManageMethods.updateAllWindowsGestureState(requireActivity(), true);
    }
}
