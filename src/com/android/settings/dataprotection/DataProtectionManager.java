package com.android.settings.dataprotection;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.AppOpsManager;
import android.app.Dialog;
import android.app.DialogFragment;
import android.app.Fragment;
import android.app.FragmentManager;
import android.content.Context;
import android.content.DialogInterface;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.ApplicationInfo;
import android.content.pm.PackageInfo;
import android.content.pm.PackageManager;
import android.content.pm.Signature;
import android.os.Bundle;
import android.preference.PreferenceActivity;
import android.text.TextUtils;
import android.view.LayoutInflater;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;
import android.widget.AdapterView;
import android.widget.AdapterView.OnItemClickListener;
import android.widget.AdapterView.OnItemLongClickListener;
import android.widget.ListView;
import android.widget.TextView;

import com.android.settings.R;
import com.android.settings.Settings.AppOpsSummaryActivity;
import com.android.settings.applications.AppOpsDetails;

import java.util.ArrayList;
import java.util.Collections;
import java.util.Comparator;
import java.util.List;

public class DataProtectionManager extends Fragment
implements OnItemClickListener, OnItemLongClickListener {

    private TextView mNoUserAppsInstalled;
    private ListView mAppsList;
    private DataProtectionListAdapter mAdapter;
    private List<AppInfo> mApps;

    private PackageManager mPm;
    private Activity mActivity;

    private SharedPreferences mPreferences;
    private AppOpsManager mAppOps;

    // Data structure for package data passed into the adapter
    public static final class AppInfo {
        String title;
        String packageName;
        boolean enabled;
        boolean DataProtectionEnabled;
        int uid;
    }

    @Override
    public View onCreateView(LayoutInflater inflater, ViewGroup container,
            Bundle savedInstanceState) {

        mActivity = getActivity();
        mPm = mActivity.getPackageManager();
        mAppOps = (AppOpsManager)getActivity().getSystemService(Context.APP_OPS_SERVICE);

        return inflater.inflate(R.layout.data_protection_manager, container, false);
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        FragmentManager fm = getFragmentManager();
        Fragment f = fm.findFragmentById(R.id.data_protection_prefs);
        if (f != null && !fm.isDestroyed()) {
            fm.beginTransaction().remove(f).commit();
        }
    }

    @Override
    public void onActivityCreated(Bundle savedInstanceState) {
        super.onActivityCreated(savedInstanceState);

        mNoUserAppsInstalled = (TextView) mActivity.findViewById(R.id.error);

        mAppsList = (ListView) mActivity.findViewById(R.id.apps_list);
        mAppsList.setOnItemClickListener(this);
        mAppsList.setOnItemLongClickListener(this);

        // get shared preference
        mPreferences = mActivity.getSharedPreferences("data_protection_manager", Activity.MODE_PRIVATE);
        if (!mPreferences.getBoolean("first_help_shown", false)) {
            showHelp();
        }

        // load apps and construct the list
        loadApps();
        setHasOptionsMenu(true);
    }

    private void loadApps() {
        mApps = loadInstalledApps();

        // if app list is empty inform the user
        // else go ahead and construct the list
        if (mApps == null || mApps.isEmpty()) {
            mNoUserAppsInstalled.setText(R.string.data_protection_no_user_apps);
            mNoUserAppsInstalled.setVisibility(View.VISIBLE);
            mAppsList.setVisibility(View.GONE);
        } else {
            mNoUserAppsInstalled.setVisibility(View.GONE);
            mAppsList.setVisibility(View.VISIBLE);
            mAdapter = createAdapter();
            mAppsList.setAdapter(mAdapter);
            mAppsList.setFastScrollEnabled(true);
        }
    }

    private DataProtectionListAdapter createAdapter() {
        String lastSectionIndex = null;
        ArrayList<String> sections = new ArrayList<String>();
        ArrayList<Integer> positions = new ArrayList<Integer>();
        int count = mApps.size(), offset = 0;

        for (int i = 0; i < count; i++) {
            AppInfo app = mApps.get(i);
            String sectionIndex;

            if (!app.enabled) {
                sectionIndex = "--"; //XXX
            } else if (app.title.isEmpty()) {
                sectionIndex = "";
            } else {
                sectionIndex = app.title.substring(0, 1).toUpperCase();
            }
            if (lastSectionIndex == null) {
                lastSectionIndex = sectionIndex;
            }

            if (!TextUtils.equals(sectionIndex, lastSectionIndex)) {
                sections.add(sectionIndex);
                positions.add(offset);
                lastSectionIndex = sectionIndex;
            }
            offset++;
        }

        return new DataProtectionListAdapter(mActivity, mApps, sections, positions);
    }

    private void resetDataProtection() {
        if (mApps == null || mApps.isEmpty()) {
            return;
        }
        // turn off data protection for all apps shown in the current list
        for (AppInfo app : mApps) {
            app.DataProtectionEnabled = false;
        }
        mAppOps.resetAllModes();
        mAdapter.notifyDataSetChanged();
    }

    @Override
    public void onItemClick(AdapterView<?> parent, View view, int position, long id) {
        // on click change the data protection status for this item
        final AppInfo app = (AppInfo) parent.getItemAtPosition(position);

        app.DataProtectionEnabled = !app.DataProtectionEnabled;
        mAppOps.setDataProtectionSettingForPackage(app.uid, app.packageName, app.DataProtectionEnabled);

        mAdapter.notifyDataSetChanged();
    }

    @Override
    public boolean onItemLongClick(AdapterView<?> parent, View view, int position, long id) {
        // on long click open app details window
        final AppInfo app = (AppInfo) parent.getItemAtPosition(position);

        Bundle args = new Bundle();
        args.putString(AppOpsDetails.ARG_PACKAGE_NAME, app.packageName);

        PreferenceActivity pa = (PreferenceActivity)getActivity();
        pa.startPreferencePanel(AppOpsDetails.class.getName(), args,
                R.string.app_ops_settings, null, this, 2);
        return true;
    }

    /**
     * Uses the package manager to query for all currently installed apps
     * for the list.
     *
     * @return the complete List off installed applications (@code DataProtectionAppInfo)
     */
    private List<AppInfo> loadInstalledApps() {
        List<AppInfo> apps = new ArrayList<AppInfo>();
        List<PackageInfo> packages = mPm.getInstalledPackages(
                PackageManager.GET_PERMISSIONS | PackageManager.GET_SIGNATURES);
        boolean showSystemApps = shouldShowSystemApps();
        Signature platformCert;

        try {
            PackageInfo sysInfo = mPm.getPackageInfo("android", PackageManager.GET_SIGNATURES);
            platformCert = sysInfo.signatures[0];
        } catch (PackageManager.NameNotFoundException e) {
            platformCert = null;
        }

        for (PackageInfo info : packages) {
            final ApplicationInfo appInfo = info.applicationInfo;

            // hide apps signed with the platform certificate to avoid the user
            // shooting himself in the foot
            if (platformCert != null && info.signatures != null
                    && platformCert.equals(info.signatures[0])) {
                continue;
            }

            // skip all system apps if they shall not be included
            if (!showSystemApps && (appInfo.flags & ApplicationInfo.FLAG_SYSTEM) != 0) {
                continue;
            }

            AppInfo app = new AppInfo();
            app.title = appInfo.loadLabel(mPm).toString();
            app.packageName = info.packageName;
            app.enabled = appInfo.enabled;
            app.uid = info.applicationInfo.uid;
            app.DataProtectionEnabled = mAppOps.getDataProtectionSettingForPackage(
                    app.uid, app.packageName);
            apps.add(app);
        }

        // sort the apps by their enabled state, then by title
        Collections.sort(apps, new Comparator<AppInfo>() {
            @Override
            public int compare(AppInfo lhs, AppInfo rhs) {
                if (lhs.enabled != rhs.enabled) {
                    return lhs.enabled ? -1 : 1;
                }
                return lhs.title.compareToIgnoreCase(rhs.title);
            }
        });

        return apps;
    }

    private boolean shouldShowSystemApps() {
        return mPreferences.getBoolean("show_system_apps", false);
    }

    private class HelpDialogFragment extends DialogFragment {
        @Override
        public Dialog onCreateDialog(Bundle savedInstanceState) {
            return new AlertDialog.Builder(getActivity())
            .setTitle(R.string.data_protection_help_title)
            .setMessage(R.string.data_protection_help_text)
            .setNegativeButton(android.R.string.ok, new DialogInterface.OnClickListener() {
                @Override
                public void onClick(DialogInterface dialog, int which) {
                    dialog.cancel();
                }
            })
            .create();
        }

        @Override
        public void onCancel(DialogInterface dialog) {
            mPreferences.edit().putBoolean("first_help_shown", true).commit();
        }
    }

    private void showHelp() {
        HelpDialogFragment fragment = new HelpDialogFragment();
        fragment.show(getFragmentManager(), "help_dialog");
    }

    @Override
    public void onCreateOptionsMenu(Menu menu, MenuInflater inflater) {
        super.onCreateOptionsMenu(menu, inflater);
        inflater.inflate(R.menu.data_protection_manager, menu);
        menu.findItem(R.id.show_system_apps).setChecked(shouldShowSystemApps());
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        switch (item.getItemId()) {
            case R.id.help:
                showHelp();
                return true;
            case R.id.reset:
                resetDataProtection();
                return true;
            case R.id.show_system_apps:
                final String prefName = "show_system_apps";
                // set the menu checkbox and save it in
                // shared preference and rebuild the list
                item.setChecked(!item.isChecked());
                mPreferences.edit().putBoolean(prefName, item.isChecked()).commit();
                loadApps();
                return true;
            case R.id.advanced:
                Intent i = new Intent(Intent.ACTION_MAIN);
                i.setClass(mActivity, AppOpsSummaryActivity.class);
                mActivity.startActivity(i);
                return true;
            default:
                return super.onContextItemSelected(item);
        }
    }

    @Override
    public void onResume() {
        super.onResume();
        // rebuild the list; the user might have changed settings inbetween
        loadApps();
    }
}