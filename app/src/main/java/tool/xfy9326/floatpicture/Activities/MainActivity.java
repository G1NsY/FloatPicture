package tool.xfy9326.floatpicture.Activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.coordinatorlayout.widget.CoordinatorLayout;
import androidx.core.app.ActivityCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.preference.PreferenceManager;
import androidx.recyclerview.widget.DefaultItemAnimator;
import androidx.recyclerview.widget.ItemTouchHelper;
import androidx.recyclerview.widget.RecyclerView;

import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;
import com.google.android.material.snackbar.Snackbar;

import tool.xfy9326.floatpicture.MainApplication;
import tool.xfy9326.floatpicture.Methods.ApplicationMethods;
import tool.xfy9326.floatpicture.Methods.IOMethods;
import tool.xfy9326.floatpicture.Methods.ManageMethods;
import tool.xfy9326.floatpicture.Methods.PermissionMethods;
import tool.xfy9326.floatpicture.R;
import tool.xfy9326.floatpicture.Utils.Config;
import tool.xfy9326.floatpicture.View.AdvancedRecyclerView;
import tool.xfy9326.floatpicture.View.ManageListAdapter;

public class MainActivity extends AppCompatActivity {
    private ManageListAdapter manageListAdapter;
    private long BackClickTime;
    private boolean navigateToGlobalSettings = false;

    public static void SnackShow(Activity mActivity, int resourceId) {
        CoordinatorLayout coordinatorLayout = mActivity.findViewById(R.id.main_layout_content);
        Snackbar.make(coordinatorLayout, mActivity.getString(resourceId), Snackbar.LENGTH_SHORT).show();
        System.gc();
    }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        init(savedInstanceState);
        ApplicationMethods.startNotificationControl(this);
        ApplicationMethods.ClearUselessTemp(this);
    }

    private void init(Bundle savedInstanceState) {
        BackClickTime = System.currentTimeMillis();
        PermissionMethods.askOverlayPermission(this, Config.REQUEST_CODE_PERMISSION_OVERLAY);
        PermissionMethods.askPermission(this, PermissionMethods.StoragePermission, Config.REQUEST_CODE_PERMISSION_STORAGE);
        ViewSet();
        MainApplication mainApplication = (MainApplication) getApplicationContext();
        if (mainApplication.isAppInit() || savedInstanceState == null) {
            ManageMethods.RunWin(this);
            mainApplication.setAppInit(true);
            IOMethods.setNoMedia();
        }
    }

    private void ViewSet() {
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);

        manageListAdapter = new ManageListAdapter(this);
        ((MainApplication) getApplicationContext()).setManageListAdapter(manageListAdapter);
        AdvancedRecyclerView recyclerView = findViewById(R.id.main_list_manage);
        recyclerView.setAdapter(manageListAdapter);
        recyclerView.setItemAnimator(new DefaultItemAnimator());
        recyclerView.setEmptyView(findViewById(R.id.layout_widget_empty_view));
        attachPictureReordering(recyclerView);

        FloatingActionButton floatingActionButton = findViewById(R.id.main_button_add);
        floatingActionButton.setOnClickListener(view -> ManageMethods.SelectPicture(MainActivity.this));

        final DrawerLayout drawerLayout = findViewById(R.id.main_drawer_layout);
        ActionBarDrawerToggle actionBarDrawerToggle = new ActionBarDrawerToggle(this, drawerLayout, toolbar, R.string.open, R.string.close);
        drawerLayout.addDrawerListener(actionBarDrawerToggle);
        drawerLayout.addDrawerListener(new DrawerLayout.DrawerListener() {
            @Override
            public void onDrawerSlide(@NonNull android.view.View drawerView, float slideOffset) {
            }

            @Override
            public void onDrawerOpened(@NonNull android.view.View drawerView) {
                ManageMethods.updateAllWindowsGestureState(MainActivity.this, false);
            }

            @Override
            public void onDrawerClosed(@NonNull android.view.View drawerView) {
                if (navigateToGlobalSettings) {
                    navigateToGlobalSettings = false;
                } else {
                    ManageMethods.updateAllWindowsGestureState(MainActivity.this, true);
                }
            }

            @Override
            public void onDrawerStateChanged(int newState) {
            }
        });
        actionBarDrawerToggle.syncState();

        NavigationView navigationView = findViewById(R.id.main_navigation_view);
        ApplicationMethods.disableNavigationViewScrollbars(navigationView);
        navigationView.setNavigationItemSelectedListener(item -> {
            drawerLayout.closeDrawer(GravityCompat.START);
            int itemId = item.getItemId();
            if (itemId == R.id.menu_global_settings) {
                navigateToGlobalSettings = true;
                startActivity(new Intent(MainActivity.this, GlobalSettingsActivity.class));
            } else if (itemId == R.id.menu_about) {
                startActivity(new Intent(MainActivity.this, AboutActivity.class));
            } else if (itemId == R.id.menu_back_to_launcher) {
                MainActivity.this.moveTaskToBack(true);
            } else if (itemId == R.id.menu_exit) {
                ApplicationMethods.CloseApplication(MainActivity.this);
            }
            return false;
        });
    }

    private void attachPictureReordering(AdvancedRecyclerView recyclerView) {
        ItemTouchHelper.SimpleCallback callback = new ItemTouchHelper.SimpleCallback(
                ItemTouchHelper.UP | ItemTouchHelper.DOWN, 0) {
            private boolean orderChanged;

            @Override
            public boolean onMove(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder,
                                  @NonNull RecyclerView.ViewHolder target) {
                int fromPosition = viewHolder.getAdapterPosition();
                int toPosition = target.getAdapterPosition();
                boolean moved = manageListAdapter.moveItem(fromPosition, toPosition);
                orderChanged = orderChanged || moved;
                return moved;
            }

            @Override
            public void onSwiped(@NonNull RecyclerView.ViewHolder viewHolder, int direction) {
                // Swipe actions are deliberately disabled.
            }

            @Override
            public void clearView(@NonNull RecyclerView recyclerView,
                                  @NonNull RecyclerView.ViewHolder viewHolder) {
                super.clearView(recyclerView, viewHolder);
                if (orderChanged) {
                    manageListAdapter.saveCurrentOrder();
                    orderChanged = false;
                    SnackShow(MainActivity.this, R.string.action_picture_order_saved);
                }
            }
        };
        new ItemTouchHelper(callback).attachToRecyclerView(recyclerView);
    }

    @SuppressLint("NotifyDataSetChanged")
    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode == Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_ADD) {
            if (resultCode == RESULT_OK) {
                int previousCount = manageListAdapter.getItemCount();
                manageListAdapter.updateData();
                if (previousCount == 0) {
                    manageListAdapter.notifyDataSetChanged();
                } else {
                    manageListAdapter.notifyItemInserted(
                            manageListAdapter.getItemCount() - 1);
                }
                SnackShow(this, R.string.action_add_window);
                ManageMethods.updateNotificationCount(this);
            }
        } else if (requestCode == Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_CHANGE) {
            if (resultCode == RESULT_OK) {
                int previousCount = manageListAdapter.getItemCount();
                int position = data == null
                        ? -1
                        : data.getIntExtra(Config.INTENT_PICTURE_EDIT_POSITION, -1);
                manageListAdapter.updateData();
                if (position >= 0 && previousCount == manageListAdapter.getItemCount()) {
                    manageListAdapter.notifyItemChanged(position);
                } else {
                    manageListAdapter.notifyDataSetChanged();
                }
                ManageMethods.updateNotificationCount(this);
            }
        } else if (requestCode == Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_GET_PICTURE) {
            if (resultCode == RESULT_OK && data != null) {
                Intent intent = new Intent(MainActivity.this, PictureSettingsActivity.class);
                intent.putExtra(Config.INTENT_PICTURE_EDIT_MODE, false);
                intent.setData(data.getData());
                startActivityForResult(intent, Config.REQUEST_CODE_ACTIVITY_PICTURE_SETTINGS_ADD);
            }
        } else if (requestCode == Config.REQUEST_CODE_PERMISSION_OVERLAY) {
            PermissionMethods.delayOverlayPermissionCheck(this);
        }
        super.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        if (requestCode == Config.REQUEST_CODE_PERMISSION_STORAGE) {
            boolean run = true;
            for (int i = 0; i < grantResults.length; i++) {
                if (grantResults[i] == PackageManager.PERMISSION_DENIED) {
                    if (ActivityCompat.shouldShowRequestPermissionRationale(this, permissions[i])) {
                        PermissionMethods.explainPermission(this, PermissionMethods.StoragePermission, Config.REQUEST_CODE_PERMISSION_STORAGE);
                    } else {
                        Toast.makeText(this, R.string.permission_warn_storage_intent, Toast.LENGTH_SHORT).show();
                    }
                    run = false;
                    break;
                }
            }
            if (run && Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
                if (Settings.canDrawOverlays(this)) {
                    ManageMethods.RunWin(this);
                }
            }
        }
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
    }

    @SuppressLint("MissingSuperCall")
    @Override
    public void onBackPressed() {
        DrawerLayout drawerLayout = findViewById(R.id.main_drawer_layout);
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        }
        long BackNowClickTime = System.currentTimeMillis();
        if ((BackNowClickTime - BackClickTime) < 2200) {
            MainApplication mainApplication = (MainApplication) getApplicationContext();
            mainApplication.setAppInit(false);
            ApplicationMethods.DoubleClickCloseSnackBar(this, true);
        } else {
            ApplicationMethods.DoubleClickCloseSnackBar(this, false);
            BackClickTime = System.currentTimeMillis();
        }
    }
}
