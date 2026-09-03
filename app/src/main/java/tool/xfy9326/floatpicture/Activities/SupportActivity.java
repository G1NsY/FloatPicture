package tool.xfy9326.floatpicture.Activities;

import android.app.Dialog;
import android.os.Bundle;
import android.view.View;
import android.view.ViewGroup;
import android.view.Window;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.ActionBar;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDialog;
import androidx.appcompat.widget.Toolbar;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.fragment.app.DialogFragment;

import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.R;

/** Local, optional support information; never launches or processes a payment. */
public class SupportActivity extends AppCompatActivity {
    private static final String QR_DIALOG_TAG = "support_qr_preview";

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

        boolean enabled = getResources().getBoolean(R.bool.support_external_enabled);
        findViewById(R.id.support_external_card).setVisibility(enabled ? View.VISIBLE : View.GONE);
        findViewById(R.id.support_view_qr).setOnClickListener(view -> {
            if (enabled && !getSupportFragmentManager().isStateSaved()
                    && getSupportFragmentManager().findFragmentByTag(QR_DIALOG_TAG) == null) {
                new QrPreviewDialog().showNow(getSupportFragmentManager(), QR_DIALOG_TAG);
            }
        });
    }

    @Override
    public boolean onSupportNavigateUp() {
        finish();
        return true;
    }

    /** Uses the exact bundled image and survives rotation without retaining an Activity. */
    public static class QrPreviewDialog extends DialogFragment {
        @NonNull
        @Override
        public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
            AppCompatDialog dialog = new AppCompatDialog(
                    requireContext(), androidx.appcompat.R.style.Theme_AppCompat_Light_NoActionBar);
            dialog.setContentView(R.layout.dialog_support_qr);
            dialog.findViewById(R.id.support_close_qr).setOnClickListener(view -> dismiss());

            View content = dialog.findViewById(R.id.support_qr_preview_content);
            int left = content.getPaddingLeft();
            int top = content.getPaddingTop();
            int right = content.getPaddingRight();
            int bottom = content.getPaddingBottom();
            ViewCompat.setOnApplyWindowInsetsListener(content, (view, windowInsets) -> {
                Insets bars = windowInsets.getInsets(
                        WindowInsetsCompat.Type.systemBars() | WindowInsetsCompat.Type.displayCutout());
                view.setPadding(left + bars.left, top + bars.top,
                        right + bars.right, bottom + bars.bottom);
                return windowInsets;
            });
            return dialog;
        }

        @Override
        public void onStart() {
            super.onStart();
            Window window = requireDialog().getWindow();
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, false);
                WindowCompat.getInsetsController(window, window.getDecorView())
                        .setAppearanceLightStatusBars(true);
                WindowCompat.getInsetsController(window, window.getDecorView())
                        .setAppearanceLightNavigationBars(true);
                window.setLayout(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT);
                ViewCompat.requestApplyInsets(requireDialog().findViewById(R.id.support_qr_preview_content));
            }
        }
    }
}
