/*
 * Copyright (c) 2016-2017 Projekt Substratum
 * This file is part of Substratum.
 *
 * Substratum is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * Substratum is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with Substratum.  If not, see <http://www.gnu.org/licenses/>.
 */

package projekt.substratum;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.NotificationManager;
import android.app.ProgressDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageManager;
import android.graphics.Bitmap;
import android.graphics.Color;
import android.graphics.PorterDuff;
import android.graphics.drawable.Drawable;
import android.os.AsyncTask;
import android.os.Bundle;
import android.os.Handler;
import android.preference.PreferenceManager;
import android.support.design.widget.Lunchbar;
import android.support.v13.app.ActivityCompat;
import android.support.v4.content.ContextCompat;
import android.support.v4.content.LocalBroadcastManager;
import android.support.v7.app.AlertDialog;
import android.support.v7.graphics.Palette;
import android.support.v7.widget.AppCompatImageView;
import android.support.v7.widget.Toolbar;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.view.ViewTreeObserver;
import android.widget.LinearLayout;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import com.gordonwong.materialsheetfab.MaterialSheetFab;
import com.gordonwong.materialsheetfab.MaterialSheetFabEventListener;

import org.omnirom.substratum.R;

import java.io.File;
import java.util.ArrayList;
import java.util.List;

import projekt.substratum.activities.base.SubstratumActivity;
import projekt.substratum.common.References;
import projekt.substratum.common.commands.ElevatedCommands;
import projekt.substratum.common.commands.FileOperations;
import projekt.substratum.common.platform.ThemeManager;
import projekt.substratum.util.files.Root;
import projekt.substratum.util.injectors.AOPTCheck;
import projekt.substratum.util.views.FloatingActionMenu;

import static android.content.om.OverlayInfo.STATE_APPROVED_DISABLED;
import static android.content.om.OverlayInfo.STATE_APPROVED_ENABLED;
import static projekt.substratum.common.References.BYPASS_SUBSTRATUM_BUILDER_DELETION;
import static projekt.substratum.common.References.MANAGER_REFRESH;
import static projekt.substratum.common.References.metadataOverlayParent;

public class InformationActivity extends SubstratumActivity {

    private static final int LUNCHBAR_DISMISS_FAB_CLICK_DELAY = 200;
    public static String theme_name;
    public static String theme_pid;
    public static String theme_mode;
    public static byte[] encryption_key;
    public static byte[] iv_encrypt_key;
    public static Lunchbar currentShownLunchBar;
    private static List<String> tab_checker;
    private Boolean uninstalled = false;
    private byte[] byteArray;
    private SharedPreferences prefs;
    private ProgressDialog mProgressDialog;
    private MaterialSheetFab materialSheetFab;
    private int tabPosition;
    private LocalBroadcastManager localBroadcastManager;
    private BroadcastReceiver refreshReceiver;
    private boolean mStoragePerms;
    private FloatingActionMenu floatingActionButton;

    private static final int PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE = 1;

    public static String getThemeName() {
        return theme_name;
    }

    public static String getThemePID() {
        return theme_pid;
    }

    public static byte[] getEncryptionKey() {
        return encryption_key;
    }

    public static byte[] getIVEncryptKey() {
        return iv_encrypt_key;
    }

    public static Lunchbar getCurrentShownLunchBar() {
        return currentShownLunchBar;
    }

    private static int getDominantColor(Bitmap bitmap) {
        try {
            Palette palette = Palette.from(bitmap).generate();
            return palette.getDominantColor(Color.TRANSPARENT);
        } catch (IllegalArgumentException iae) {
            // Suppress warning
        }
        return Color.TRANSPARENT;
    }

    private static void setOverflowButtonColor(final Activity activity, final Boolean dark_mode) {
        @SuppressLint("PrivateResource") final String overflowDescription =
                activity.getString(R.string.abc_action_menu_overflow_description);
        final ViewGroup decorView = (ViewGroup) activity.getWindow().getDecorView();
        final ViewTreeObserver viewTreeObserver = decorView.getViewTreeObserver();
        viewTreeObserver.addOnGlobalLayoutListener(() -> {
            final ArrayList<View> outViews = new ArrayList<>();
            decorView.findViewsWithText(
                    outViews,
                    overflowDescription,
                    View.FIND_VIEWS_WITH_CONTENT_DESCRIPTION);
            if (outViews.isEmpty()) {
                return;
            }
            AppCompatImageView overflow = (AppCompatImageView) outViews.get(0);
            overflow.setImageResource(dark_mode ? R.drawable.information_activity_overflow_dark :
                    R.drawable.information_activity_overflow_light);
        });
    }

    private boolean closeAllLunchBars() {
        if (currentShownLunchBar != null) {
            currentShownLunchBar.dismiss();
            currentShownLunchBar = null;
            return true;
        } else {
            return false;
        }
    }

    private View getView() {
        return ((ViewGroup) this.findViewById(android.R.id.content)).getChildAt(0);
    }

    private boolean checkColorDarkness(int color) {
        double darkness =
                1 - (0.299 * Color.red(color) +
                        0.587 * Color.green(color) +
                        0.114 * Color.blue(color)) / 255;
        return darkness < 0.5;
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
    }

    @SuppressWarnings("ConstantConditions")
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        Intent currentIntent = getIntent();
        theme_name = currentIntent.getStringExtra("theme_name");
        theme_pid = currentIntent.getStringExtra("theme_pid");
        theme_mode = currentIntent.getStringExtra("theme_mode");
        encryption_key = currentIntent.getByteArrayExtra("encryption_key");
        iv_encrypt_key = currentIntent.getByteArrayExtra("iv_encrypt_key");

        if (theme_mode == null) {
            theme_mode = "";
        }

        setContentView(R.layout.information_activity);

        prefs = PreferenceManager.getDefaultSharedPreferences(getApplicationContext());

        // Register the theme install receiver to auto refresh the fragment
        refreshReceiver = new RefreshReceiver();
        IntentFilter if1 = new IntentFilter(MANAGER_REFRESH);
        localBroadcastManager = LocalBroadcastManager.getInstance(getApplicationContext());
        localBroadcastManager.registerReceiver(refreshReceiver, if1);

        Toolbar toolbar = findViewById(R.id.toolbar);
        if (toolbar != null) {
            toolbar.setTitle(theme_name);
            toolbar.setTitleTextColor(getColor(R.color.information_activity_light_icon_mode));
        }

        setSupportActionBar(toolbar);
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setDisplayShowHomeEnabled(true);
        }
        if (toolbar != null) toolbar.setNavigationOnClickListener(v -> onBackPressed());

        View sheetView = findViewById(R.id.fab_sheet);
        View overlay = findViewById(R.id.overlay);
        int sheetColor = getApplicationContext().getColor(R.color.fab_menu_background_card);
        int fabColor = getApplicationContext().getColor(R.color.fab_background_color);

        floatingActionButton = findViewById(R.id.apply_fab);

        // Create material sheet FAB
        if (sheetView != null && overlay != null) {
            materialSheetFab = new MaterialSheetFab<>(
                    floatingActionButton,
                    sheetView,
                    overlay,
                    sheetColor,
                    fabColor);
        }

        Drawable upArrow = getDrawable(R.drawable.information_activity_back_light);
        if (upArrow != null)
            upArrow.setColorFilter(getColor(R.color.information_activity_light_icon_mode),
                    PorterDuff.Mode.SRC_ATOP);
        getSupportActionBar().setHomeAsUpIndicator(upArrow);
        setOverflowButtonColor(this, false);

        LocalBroadcastManager localBroadcastManager =
                LocalBroadcastManager.getInstance(getApplicationContext());
        floatingActionButton.setOnClickListener(v -> {
            try {
                boolean isLunchbarOpen = closeAllLunchBars();
                final Handler handler = new Handler();
                handler.postDelayed(() -> {
                    materialSheetFab.showSheet();
                }, isLunchbarOpen ? LUNCHBAR_DISMISS_FAB_CLICK_DELAY : 0);
            } catch (NullPointerException npe) {
                // Suppress warning
            }
        });

        Intent intent = new Intent("Overlays.START_JOB");
        Switch enable_swap = findViewById(R.id.enable_swap);
        if (!References.checkOMS(this)) {
            enable_swap.setText(getString(R.string.fab_menu_swap_toggle_legacy));
        }
        if (enable_swap != null) {
            boolean enabled = prefs.getBoolean("enable_swapping_overlays", true);
            intent.putExtra("command", "MixAndMatchMode");
            intent.putExtra("newValue", enabled);
            localBroadcastManager.sendBroadcast(intent);
            enable_swap.setChecked(enabled);

            enable_swap.setOnCheckedChangeListener((buttonView, isChecked) -> {
                prefs.edit().putBoolean("enable_swapping_overlays", isChecked).apply();
                intent.putExtra("command", "MixAndMatchMode");
                intent.putExtra("newValue", isChecked);
                localBroadcastManager.sendBroadcast(intent);
            });
        }

        final TextView compile_enable_selected = findViewById(R.id.compile_enable_selected);
        if (!References.checkOMS(this)) compile_enable_selected.setVisibility(View.GONE);
        if (compile_enable_selected != null) {
            compile_enable_selected.setOnClickListener(v -> {
                materialSheetFab.setEventListener(new MaterialSheetFabEventListener() {
                    @Override
                    public void onSheetHidden() {
                        super.onSheetHidden();
                        intent.putExtra("command", "CompileEnable");
                        localBroadcastManager.sendBroadcast(intent);
                        materialSheetFab.setEventListener(null);
                    }
                });
                materialSheetFab.hideSheet();
            });
        }

        TextView compile_update_selected = findViewById(R.id.compile_update_selected);
        if (!References.checkOMS(this)) {
            compile_update_selected.setText(getString(R.string.fab_menu_compile_install));
        }
        if (compile_update_selected != null) {
            compile_update_selected.setOnClickListener(v -> {
                materialSheetFab.setEventListener(new MaterialSheetFabEventListener() {
                    @Override
                    public void onSheetHidden() {
                        super.onSheetHidden();
                        intent.putExtra("command", "CompileUpdate");
                        localBroadcastManager.sendBroadcast(intent);
                        materialSheetFab.setEventListener(null);
                    }
                });
                materialSheetFab.hideSheet();
            });
        }

        TextView disable_selected = findViewById(R.id.disable_selected);
        if (!References.checkOMS(this)) {
            disable_selected.setText(getString(R.string.fab_menu_uninstall));
        }
        if (disable_selected != null) {
            disable_selected.setOnClickListener(v -> {
                materialSheetFab.setEventListener(new MaterialSheetFabEventListener() {
                    @Override
                    public void onSheetHidden() {
                        super.onSheetHidden();
                        intent.putExtra("command", "Disable");
                        localBroadcastManager.sendBroadcast(intent);
                        materialSheetFab.setEventListener(null);
                    }
                });
                materialSheetFab.hideSheet();
            });
        }

        LinearLayout enable_zone = findViewById(R.id.enable);
        if (!References.checkOMS(this)) enable_zone.setVisibility(View.GONE);
        TextView enable_selected = findViewById(R.id.enable_selected);
        if (enable_selected != null) {
            enable_selected.setOnClickListener(v -> {
                materialSheetFab.setEventListener(new MaterialSheetFabEventListener() {
                    @Override
                    public void onSheetHidden() {
                        super.onSheetHidden();
                        intent.putExtra("command", "Enable");
                        localBroadcastManager.sendBroadcast(intent);
                        materialSheetFab.setEventListener(null);
                    }
                });
                materialSheetFab.hideSheet();
            });
        }

        requestStoragePermissions();
        new AOPTCheck().injectAOPT(this, false);
    }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.theme_information_menu, menu);

        // Start dynamically showing menu items
        boolean isOMS = References.checkOMS(getApplicationContext());

        if (!isOMS && !Root.checkRootAccess()) {
            menu.findItem(R.id.restart_systemui).setVisible(false);
        }
        if (!isOMS) {
            menu.findItem(R.id.disable).setVisible(false);
            menu.findItem(R.id.enable).setVisible(false);
        }
        if (isOMS) {
            menu.findItem(R.id.reboot_device).setVisible(false);
            menu.findItem(R.id.soft_reboot).setVisible(false);
            menu.findItem(R.id.uninstall).setVisible(false);
        }

        if (!References.isUserApp(getApplicationContext(), theme_pid)) {
            menu.findItem(R.id.uninstall).setVisible(false);
        }

        menu.findItem(R.id.clean_cache).setVisible(prefs.getBoolean("caching_enabled", false));

        return true;
    }

    @Override
    public boolean onPrepareOptionsMenu(Menu menu) {
        boolean isOMS = References.checkOMS(getApplicationContext());
        if (isOMS) {
            menu.findItem(R.id.disable).setVisible(mStoragePerms);
            menu.findItem(R.id.enable).setVisible(mStoragePerms);
            menu.findItem(R.id.clean).setVisible(mStoragePerms);
        }
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        int id = item.getItemId();

        switch (id) {
            case R.id.clean:
                AlertDialog.Builder builder1 = new AlertDialog.Builder(InformationActivity.this);
                builder1.setTitle(theme_name);
                builder1.setIcon(References.grabAppIcon(getApplicationContext(), theme_pid));
                builder1.setMessage(R.string.clean_dialog_body)
                        .setPositiveButton(R.string.dialog_ok, (dialog, id18) -> {
                            // Dismiss the dialog
                            dialog.dismiss();
                            // Get all installed overlays
                            List<String> stateAll = ThemeManager.listAllOverlays(
                                    getApplicationContext());

                            ArrayList<String> all_overlays = new ArrayList<>();
                            for (int j = 0; j < stateAll.size(); j++) {
                                try {
                                    String current = stateAll.get(j);
                                    ApplicationInfo appInfo = getApplicationContext()
                                            .getPackageManager().getApplicationInfo(
                                                    current, PackageManager.GET_META_DATA);
                                    if (appInfo.metaData != null &&
                                            appInfo.metaData.getString(
                                                    metadataOverlayParent) != null) {
                                        String parent =
                                                appInfo.metaData.getString(metadataOverlayParent);
                                        if (parent != null && parent.equals(theme_pid)) {
                                            all_overlays.add(current);
                                        }
                                    }
                                } catch (Exception e) {
                                    // NameNotFound
                                }
                            }

                            // Begin uninstalling overlays for this package
                            ThemeManager.uninstallOverlay(
                                    getApplicationContext(),
                                    all_overlays
                            );
                        })
                        .setNegativeButton(R.string.dialog_cancel, (dialog, id19) -> {
                            // User cancelled the dialog
                        });
                // Create the AlertDialog object and return it
                builder1.create();
                builder1.show();
                return true;
            case R.id.clean_cache:
                AlertDialog.Builder builder2 = new AlertDialog.Builder(InformationActivity.this);
                builder2.setTitle(theme_name);
                builder2.setIcon(References.grabAppIcon(getApplicationContext(), theme_pid));
                builder2.setMessage(R.string.clean_cache_dialog_body)
                        .setPositiveButton(R.string.dialog_ok, (dialog, id110) -> {
                            // Dismiss the dialog
                            dialog.dismiss();
                            FileOperations.delete(
                                    getApplicationContext(), getBuildDirPath() + theme_pid + "/");
                            String format =
                                    String.format(
                                            getString(R.string.cache_clear_completion),
                                            theme_name);
                            createToast(format, Toast.LENGTH_LONG);
                            finish();
                        })
                        .setNegativeButton(R.string.dialog_cancel, (dialog, id17) ->
                                dialog.cancel());
                // Create the AlertDialog object and return it
                builder2.create();
                builder2.show();
                return true;
            case R.id.disable:
                AlertDialog.Builder builder3 = new AlertDialog.Builder(InformationActivity.this);
                builder3.setTitle(theme_name);
                builder3.setIcon(References.grabAppIcon(getApplicationContext(), theme_pid));
                builder3.setMessage(R.string.disable_dialog_body)
                        .setPositiveButton(R.string.dialog_ok, (dialog, id16) -> {
                            // Dismiss the dialog
                            dialog.dismiss();
                            // Get all enabled overlays
                            List<String> stateAll = ThemeManager.listOverlays(
                                    getApplicationContext(), STATE_APPROVED_ENABLED);

                            ArrayList<String> all_overlays = new ArrayList<>();
                            for (int j = 0; j < stateAll.size(); j++) {
                                try {
                                    String current = stateAll.get(j);
                                    ApplicationInfo appInfo = getApplicationContext()
                                            .getPackageManager().getApplicationInfo(
                                                    current, PackageManager.GET_META_DATA);
                                    if (appInfo.metaData != null &&
                                            appInfo.metaData.getString(
                                                    metadataOverlayParent) != null) {
                                        String parent =
                                                appInfo.metaData.getString(metadataOverlayParent);
                                        if (parent != null && parent.equals(theme_pid)) {
                                            all_overlays.add(current);
                                        }
                                    }
                                } catch (Exception e) {
                                    // NameNotFound
                                }
                            }
                            createToast(getString(R.string.disable_completion), Toast.LENGTH_LONG);

                            // Begin disabling overlays
                            ThemeManager.disableOverlay(getApplicationContext(), all_overlays);
                        })
                        .setNegativeButton(R.string.dialog_cancel, (dialog, id15) -> {
                            // User cancelled the dialog
                        });
                // Create the AlertDialog object and return it
                builder3.create();
                builder3.show();
                return true;
            case R.id.enable:
                AlertDialog.Builder builder4 = new AlertDialog.Builder(InformationActivity.this);
                builder4.setTitle(theme_name);
                builder4.setIcon(References.grabAppIcon(getApplicationContext(), theme_pid));
                builder4.setMessage(R.string.enable_dialog_body)
                        .setPositiveButton(R.string.dialog_ok, (dialog, id14) -> {
                            // Dismiss the dialog
                            dialog.dismiss();
                            // Get all disabled overlays
                            List<String> stateAll = ThemeManager.listOverlays(
                                    getApplicationContext(), STATE_APPROVED_DISABLED);

                            ArrayList<String> all_overlays = new ArrayList<>();
                            for (int j = 0; j < stateAll.size(); j++) {
                                try {
                                    String current = stateAll.get(j);
                                    ApplicationInfo appInfo = getApplicationContext()
                                            .getPackageManager().getApplicationInfo(
                                                    current, PackageManager.GET_META_DATA);
                                    if (appInfo.metaData != null &&
                                            appInfo.metaData.getString(
                                                    metadataOverlayParent) != null) {
                                        String parent =
                                                appInfo.metaData.getString(metadataOverlayParent);
                                        if (parent != null && parent.equals(theme_pid)) {
                                            all_overlays.add(current);
                                        }
                                    }
                                } catch (Exception e) {
                                    // NameNotFound
                                }
                            }
                            createToast(getString(R.string.enable_completion), Toast.LENGTH_LONG);

                            // Begin enabling overlays
                            ThemeManager.enableOverlay(getApplicationContext(), all_overlays);
                        })
                        .setNegativeButton(R.string.dialog_cancel, (dialog, id13) ->
                                dialog
                                        .cancel());
                // Create the AlertDialog object and return it
                builder4.create();
                builder4.show();
                return true;
            case R.id.uninstall:
                AlertDialog.Builder builder5 = new AlertDialog.Builder(InformationActivity.this);
                builder5.setTitle(theme_name);
                builder5.setIcon(References.grabAppIcon(getApplicationContext(), theme_pid));
                builder5.setMessage(R.string.uninstall_dialog_text)
                        .setPositiveButton(R.string.dialog_ok, (dialog, id12) -> {
                            // Dismiss the dialog
                            dialog.dismiss();
                            new uninstallTheme().execute("");
                        })
                        .setNegativeButton(R.string.dialog_cancel, (dialog, id1) -> {
                            // User cancelled the dialog
                        });
                // Create the AlertDialog object and return it
                builder5.create();
                builder5.show();
                return true;
            case R.id.restart_systemui:
                ThemeManager.restartSystemUI(getApplicationContext());
                return true;
            case R.id.reboot_device:
                ElevatedCommands.reboot();
                return true;
            case R.id.soft_reboot:
                ElevatedCommands.softReboot();
                return true;
            case R.id.about_substratum:
                startActivity(new Intent(this, TeamActivity.class));
                return true;
        }
        return super.onOptionsItemSelected(item);
    }

    @Override
    public void onBackPressed() {
        if (materialSheetFab.isSheetVisible()) {
            materialSheetFab.hideSheet();
        } else {
            if (uninstalled)
                References.sendRefreshMessage(getApplicationContext());
            finish();
        }
    }

    @Override
    public void onDestroy() {
        super.onDestroy();

        try {
            localBroadcastManager.unregisterReceiver(refreshReceiver);
        } catch (IllegalArgumentException e) {
            // Unregistered already
        }

        // Reset all of the parameters of this IA instance
        theme_name = null;
        theme_pid = null;
        theme_mode = null;
        encryption_key = null;
        iv_encrypt_key = null;

        if (!BYPASS_SUBSTRATUM_BUILDER_DELETION &&
                !References.isCachingEnabled(getApplicationContext())) {
            String workingDirectory =
                    getApplicationContext().getCacheDir().getAbsolutePath();
            File deleted = new File(workingDirectory);
            FileOperations.delete(getApplicationContext(), deleted.getAbsolutePath());
            if (!deleted.exists()) Log.d(References.SUBSTRATUM_BUILDER,
                    "Successfully cleared Substratum cache!");
        }
    }

    private class uninstallTheme extends AsyncTask<String, Integer, String> {
        @Override
        protected void onPreExecute() {
            String parseMe = String.format(getString(R.string.adapter_uninstalling), theme_name);
            mProgressDialog = new ProgressDialog(InformationActivity.this);
            mProgressDialog.setMessage(parseMe);
            mProgressDialog.setIndeterminate(true);
            mProgressDialog.setCancelable(false);
            mProgressDialog.show();
            // Clear the notification of building theme if shown
            NotificationManager manager = (NotificationManager)
                    getApplicationContext().getSystemService(Context.NOTIFICATION_SERVICE);
            manager.cancel(References.notification_id);
        }

        @Override
        protected void onPostExecute(String result) {
            mProgressDialog.cancel();
            uninstalled = true;
            onBackPressed();
        }

        @Override
        protected String doInBackground(String... sUrl) {
            References.uninstallPackage(getApplicationContext(), theme_pid);
            return null;
        }
    }

    class RefreshReceiver extends BroadcastReceiver {

        @Override
        public void onReceive(Context context, Intent intent) {
            if (!References.isPackageInstalled(context, theme_pid)) {
                Log.d("ThemeUninstaller",
                        "The theme was uninstalled, so the activity is now closing!");
                References.sendRefreshMessage(context);
                finish();
            }
        }
    }

    private boolean checkPermissionGrantResults(int[] grantResults) {
        for (int result : grantResults) {
            if (result != PackageManager.PERMISSION_GRANTED) {
                return false;
            }
        }
        return true;
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String permissions[],
                                           int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE: {
                if (checkPermissionGrantResults(grantResults)) {
                    mStoragePerms = true;
                    floatingActionButton.show();
                }
            }
        }
    }
    private void requestStoragePermissions() {
        int permissionCheck = ContextCompat.checkSelfPermission(
                getApplicationContext(),
                Manifest.permission.WRITE_EXTERNAL_STORAGE);

        if (permissionCheck != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(this,
                    new String[]{Manifest.permission.WRITE_EXTERNAL_STORAGE},
                    PERMISSIONS_REQUEST_WRITE_EXTERNAL_STORAGE);
            mStoragePerms = false;
            floatingActionButton.hide();
            return;
        }
        mStoragePerms = true;
        floatingActionButton.show();
    }
}
