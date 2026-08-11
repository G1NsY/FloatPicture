package tool.xfy9326.floatpicture.View;

import android.app.Activity;
import android.content.Intent;
import android.graphics.Color;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import androidx.annotation.NonNull;
import androidx.appcompat.widget.SwitchCompat;

import com.google.android.material.snackbar.Snackbar;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;

import tool.xfy9326.floatpicture.Activities.MainActivity;
import tool.xfy9326.floatpicture.Activities.PictureSettingsActivity;
import tool.xfy9326.floatpicture.Methods.ImageMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.Utils.PictureData;

public class ManageListAdapter extends AdvancedRecyclerView.Adapter<ManageListViewHolder> {
    private final Activity mActivity;
    private final PictureData pictureData;
    private LinkedHashMap<String, String> pictureInfo;
    private ArrayList<String> PictureId_Array;
    private ArrayList<String> PictureName_Array;

    public ManageListAdapter(Activity mActivity) {
        this.mActivity = mActivity;
        pictureData = new PictureData();
        updateData();
    }

    public void updateData() {
        pictureInfo = pictureData.getListArray();
        PictureId_Array = new ArrayList<>();
        PictureName_Array = new ArrayList<>();
        for (Map.Entry<?, ?> entry : pictureInfo.entrySet()) {
            PictureId_Array.add(entry.getKey().toString());
            PictureName_Array.add(entry.getValue().toString());
        }
    }

    public boolean moveItem(int fromPosition, int toPosition) {
        if (fromPosition < 0 || toPosition < 0
                || fromPosition >= PictureId_Array.size()
                || toPosition >= PictureId_Array.size()) {
            return false;
        }
        String pictureId = PictureId_Array.remove(fromPosition);
        String pictureName = PictureName_Array.remove(fromPosition);
        PictureId_Array.add(toPosition, pictureId);
        PictureName_Array.add(toPosition, pictureName);
        notifyItemMoved(fromPosition, toPosition);
        return true;
    }

    public void saveCurrentOrder() {
        pictureData.savePictureOrder(new ArrayList<>(PictureId_Array));
        rebuildPictureInfo();
        ManageMethods.updateNotificationCount(mActivity);
    }

    private void rebuildPictureInfo() {
        LinkedHashMap<String, String> orderedInfo = new LinkedHashMap<>();
        for (int i = 0; i < PictureId_Array.size(); i++) {
            orderedInfo.put(PictureId_Array.get(i), PictureName_Array.get(i));
        }
        pictureInfo = orderedInfo;
    }

    @Override
    public int getItemCount() {
        return pictureInfo.size();
    }

    @Override
    public void onBindViewHolder(final ManageListViewHolder holder, int position) {
        final String mPictureId = PictureId_Array.get(holder.getAdapterPosition());
        final String mPictureName = PictureName_Array.get(holder.getAdapterPosition());
        holder.textView_Picture_Name.setText(mPictureName);
        final PictureData pictureData = new PictureData();
        pictureData.setDataControl(mPictureId);
        android.graphics.Bitmap previewBitmap = ImageMethods.getPreviewBitmap(mActivity, mPictureId);
        holder.imageView_Picture_Preview.setImageBitmap(previewBitmap);

        float defaultZoom = ImageMethods.getDefaultZoom(mActivity, previewBitmap, false);
        float zoom = pictureData.getFloat(Config.DATA_PICTURE_ZOOM, defaultZoom);
        float zoomX = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_X, zoom);
        float zoomY = pictureData.getFloat(Config.DATA_PICTURE_ZOOM_Y, zoom);
        int positionX = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_X, Config.DATA_DEFAULT_PICTURE_POSITION_X);
        int positionY = pictureData.getInt(
                Config.DATA_PICTURE_POSITION_Y, Config.DATA_DEFAULT_PICTURE_POSITION_Y);
        float degree = pictureData.getFloat(
                Config.DATA_PICTURE_DEGREE, Config.DATA_DEFAULT_PICTURE_DEGREE);
        holder.textView_Picture_Parameters.setText(String.format(
                Locale.US,
                "S(%.3f,%.3f)\nP(%d,%d)  R(%.1f°)",
                zoomX,
                zoomY,
                positionX,
                positionY,
                degree));

        if (!ImageMethods.isPictureFileExist(mPictureId)) {
            holder.textView_Picture_Error.setVisibility(View.VISIBLE);
        } else {
            holder.textView_Picture_Error.setVisibility(View.GONE);
        }

        SwitchCompat switch_Picture_Show = holder.switch_Picture_Show;
        switch_Picture_Show.setOnCheckedChangeListener(null);
        boolean configuredVisible = pictureData.getBoolean(
                Config.DATA_PICTURE_SHOW_ENABLED, Config.DATA_DEFAULT_PICTURE_SHOW_ENABLED);
        switch_Picture_Show.setChecked(configuredVisible);
        switch_Picture_Show.setOnCheckedChangeListener((compoundButton, visible) -> {
            ManageMethods.setWindowVisible(mActivity, pictureData, mPictureId, visible);
            if (visible && !ManageMethods.allowsMultiplePictures(mActivity)) {
                notifyDataSetChanged();
            }
            ManageMethods.updateNotificationCount(mActivity);
        });

        holder.button_Picture_Edit.setOnClickListener(view -> {
            ManageMethods.prepareWindowForEditing(mActivity, mPictureId);
            Intent intent = new Intent(mActivity, PictureSettingsActivity.class);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_MODE, true);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_ID, mPictureId);
            intent.putExtra(Config.INTENT_PICTURE_EDIT_POSITION, holder.getAdapterPosition());
            mActivity.startActivityForResult(intent, Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_CHANGE);
        });

        holder.button_Picture_Delete.setOnClickListener(v -> {
            View coordinatorLayout = mActivity.findViewById(R.id.main_layout_content);
            if (coordinatorLayout != null) {
                Snackbar snackbar = Snackbar.make(coordinatorLayout, R.string.action_confirm_delete_hint, Snackbar.LENGTH_LONG);
                snackbar.setAction(R.string.action_confirm_delete, view -> {
                    ManageMethods.DeleteWin(mActivity, mPictureId);
                    updateData();
                    holder.switch_Picture_Show.setOnCheckedChangeListener(null);
                    holder.button_Picture_Edit.setOnClickListener(null);
                    holder.button_Picture_Delete.setOnClickListener(null);
                    int position1 = holder.getAdapterPosition();
                    if (position1 != -1) {
                        notifyItemRemoved(position1);
                        notifyItemRangeChanged(position1, getItemCount() - position1);
                    }
                    MainActivity.SnackShow(mActivity, R.string.action_delete_window);
                    ManageMethods.updateNotificationCount(mActivity);
                });
                snackbar.setActionTextColor(Color.RED);
                snackbar.show();
            }
        });
    }

    @Override
    @NonNull
    public ManageListViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        LayoutInflater inflater = LayoutInflater.from(mActivity);
        View mView = inflater.inflate(R.layout.adapter_manage_list, parent, false);
        return new ManageListViewHolder(mView);
    }
}
