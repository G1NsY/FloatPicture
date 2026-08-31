package tool.xfy9326.floatpicture.Activities;

import android.content.ActivityNotFoundException;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AlertDialog;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.R;

/** An optional support screen; it never changes picture features or payment state. */
public class SupportActivity extends AppCompatActivity {
    private static final String SUPPORT_URL = "https://afdian.com/a/G1NsY";
    private AlertDialog externalDialog;

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_support);
        ApplicationMethods.applyStatusBarTopInset(findViewById(R.id.support_toolbar_container));
        ApplicationMethods.applyNavigationBarBottomInset(findViewById(R.id.support_scroll));

        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        ActionBar actionBar = getSupportActionBar();
        if (actionBar != null) {
            actionBar.setDisplayHomeAsUpEnabled(true);
        }

        View externalCard = findViewById(R.id.support_external_card);
        externalCard.setVisibility(externalSupportEnabled() ? View.VISIBLE : View.GONE);
        findViewById(R.id.support_open_afdian).setOnClickListener(view -> {
            if (!externalSupportEnabled()) {
                return;
            }
            if (externalDialog != null && externalDialog.isShowing()) {
                return;
            }
            externalDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.support_leave_title)
                    .setMessage(R.string.support_leave_message)
                    .setPositiveButton(R.string.support_continue, (dialog, which) -> openSupportPage())
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            externalDialog.show();
        });
        findViewById(R.id.support_copy_link).setOnClickListener(view -> copySupportLink());
    }

    private boolean externalSupportEnabled() {
        return getResources().getBoolean(R.bool.support_external_enabled);
    }

    private void openSupportPage() {
        if (!externalSupportEnabled()) {
            return;
        }
        // A fixed public HTTPS URL only: no picture URI, account data or tracking parameters.
        Intent intent = new Intent(Intent.ACTION_VIEW, Uri.parse(SUPPORT_URL));
        intent.addCategory(Intent.CATEGORY_BROWSABLE);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException | SecurityException exception) {
            externalDialog = new AlertDialog.Builder(this)
                    .setTitle(R.string.support_cannot_open_title)
                    .setMessage(R.string.support_cannot_open_message)
                    .setPositiveButton(R.string.support_copy_link, (dialog, which) -> copySupportLink())
                    .setNegativeButton(R.string.cancel, null)
                    .create();
            externalDialog.show();
        }
    }

    private void copySupportLink() {
        if (!externalSupportEnabled()) {
            return;
        }
        ClipboardManager clipboard = (ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
        if (clipboard != null) {
            clipboard.setPrimaryClip(ClipData.newPlainText(getString(R.string.support_development), SUPPORT_URL));
            Toast.makeText(this, R.string.support_link_copied, Toast.LENGTH_SHORT).show();
        }
    }

    @Override
    protected void onDestroy() {
        if (externalDialog != null) {
            externalDialog.dismiss();
        }
        super.onDestroy();
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }
}
