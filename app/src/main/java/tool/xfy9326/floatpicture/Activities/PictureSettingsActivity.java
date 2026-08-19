package tool.xfy9326.floatpicture.Activities;

import android.app.Activity;
import android.content.Intent;
import android.os.Bundle;
import android.view.Menu;
import android.view.MenuItem;

import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.activity.OnBackPressedCallback;
import androidx.fragment.app.FragmentTransaction;

import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.PictureSettingsFragment;

public class PictureSettingsActivity extends AppCompatActivity {
    private PictureSettingsFragment mPictureSettingsFragment;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_settings);
        ApplicationMethods.applyStatusBarTopInset(
                findViewById(R.id.layout_picture_settings_toolbar));
        ApplicationMethods.applyNavigationBarBottomInset(
                findViewById(R.id.layout_picture_settings_content));
        ViewSet();
        fragmentSet(savedInstanceState);
        getOnBackPressedDispatcher().addCallback(this, new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                exitWithoutSaving();
            }
        });
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

    private void exitWithoutSaving() {
        if (mPictureSettingsFragment != null) {
            mPictureSettingsFragment.exit();
        }
        finish();
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
            exitWithoutSaving();
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
