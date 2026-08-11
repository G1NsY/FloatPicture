package tool.xfy9326.floatpicture.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.fragment.app.FragmentTransaction;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;

public class PictureSettingsActivity extends AppCompatActivity {
    private PictureSettingsFragment mPictureSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ViewSet();
        fragmentSet(savedInstanceState);
        setResult(Activity.RESULT_CANCELED);
    }

    private void ViewSet() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            // 无论是否是编辑模式，都显示左上角的返回按钮
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }
    }

    private void fragmentSet(Bundle savedInstanceState) {
        if (savedInstanceState == null) {
            mPictureSettingsFragment = new PictureSettingsFragment();
            FragmentTransaction fragmentTransaction = getSupportFragmentManager().beginTransaction();
            fragmentTransaction.replace(R.id.layout_picture_settings_content, mPictureSettingsFragment);
            fragmentTransaction.commit();
        } else {
            mPictureSettingsFragment = (PictureSettingsFragment) getSupportFragmentManager().findFragmentById(R.id.layout_picture_settings_content);
        }
    }

    @Override
    public void onBackPressed() {
        mPictureSettingsFragment.exit();
        finish();
        // 注意：这里已经 finish 了，不需要再执行 super.onBackPressed() 否则可能导致重复退出
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.activity_picture_settings, menu);
        return super.onCreateOptionsMenu(menu);
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int itemId = item.getItemId();
        if (itemId == R.id.menu_picture_settings_save) {
            mPictureSettingsFragment.saveAllData();
            Intent resultIntent = new Intent();
            if (getIntent().getBooleanExtra(Config.INTENT_PICTURE_EDIT_MODE, false)) {
                resultIntent.putExtra(
                        Config.INTENT_PICTURE_EDIT_POSITION,
                        getIntent().getIntExtra(Config.INTENT_PICTURE_EDIT_POSITION, -1));
            }
            setResult(Activity.RESULT_OK, resultIntent);
            finish();
        } else if (itemId == android.R.id.home) {
            // 当点击左上角返回按钮（id 为 android.R.id.home）时
            // 调用 fragment 的 exit() 方法来执行不保存的清理/还原逻辑
            mPictureSettingsFragment.exit();
            finish();
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    protected void onDestroy() {
        if (mPictureSettingsFragment != null) {
            mPictureSettingsFragment.clearEditView();
        }
        System.gc();
        super.onDestroy();
    }
}
