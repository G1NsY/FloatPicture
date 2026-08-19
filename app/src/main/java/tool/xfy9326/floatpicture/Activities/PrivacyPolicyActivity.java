package tool.xfy9326.floatpicture.Activities;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.widget.LinearLayout;
import android.widget.RelativeLayout;
import android.widget.TextView;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;

public class PrivacyPolicyActivity extends AppCompatActivity {
    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_license);
        ApplicationMethods.applyStatusBarTopInset(
                findViewById(R.id.layout_license_toolbar_container));
        ApplicationMethods.applyNavigationBarBottomInset(
                findViewById(R.id.layout_license_scroll));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setHomeButtonEnabled(true);
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        LinearLayout mainLayout = findViewById(R.id.layout_license);
        RelativeLayout card = (RelativeLayout) LayoutInflater.from(this).inflate(
                R.layout.widget_card_license,
                findViewById(R.id.layout_license_card));
        ((TextView) card.findViewById(R.id.licence_title)).setText(R.string.privacy_policy);
        ((TextView) card.findViewById(R.id.licence_url)).setText(R.string.privacy_contact_url);
        ((TextView) card.findViewById(R.id.licence_data)).setText(
                IOMethods.readAssetText(this, Config.PRIVACY_POLICY_PATH_APPLICATION));
        mainLayout.addView(card);
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
