
package com.android.settings.dataprotection;

import android.os.Bundle;
import android.preference.CheckBoxPreference;
import android.preference.Preference;
import android.preference.Preference.OnPreferenceChangeListener;
import android.preference.PreferenceScreen;
import android.provider.Settings;
import android.provider.Settings.SettingNotFoundException;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.ListView;

import com.android.settings.R;
import com.android.settings.SettingsPreferenceFragment;

public class DataProtectionPrefs extends SettingsPreferenceFragment implements
OnPreferenceChangeListener {

    private static final String TAG = "DataProtectionPrefs";

    private static final String KEY_DATA_PROTECTION_DEFAULT = "data_protection_default";

    private CheckBoxPreference mDataProtectionDefault;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        addPreferencesFromResource(R.xml.data_protection_prefs);
        PreferenceScreen prefSet = getPreferenceScreen();

        mDataProtectionDefault = (CheckBoxPreference) findPreference(KEY_DATA_PROTECTION_DEFAULT);
        mDataProtectionDefault.setOnPreferenceChangeListener(this);

        try {
            mDataProtectionDefault.setChecked(Settings.Secure.getInt(getContentResolver(),
                    Settings.Secure.DATA_PROTECTION_DEFAULT) == 1);
        } catch (SettingNotFoundException e) {
            mDataProtectionDefault.setChecked(false);
        }
    }

    @Override
    public View onCreateView(LayoutInflater inflater,
            ViewGroup container, Bundle savedInstanceState) {
        final View view = super.onCreateView(inflater, container, savedInstanceState);
        final ListView list = (ListView) view.findViewById(android.R.id.list);
        // our container already takes care of the padding
        int paddingTop = list.getPaddingTop();
        int paddingBottom = list.getPaddingBottom();
        list.setPadding(0, paddingTop, 0, paddingBottom);
        return view;
    }

    @Override
    public boolean onPreferenceChange(Preference preference, Object newValue) {
        if (preference == mDataProtectionDefault) {
            boolean value = (Boolean) newValue;
            Settings.Secure.putInt(getContentResolver(),
                    Settings.Secure.DATA_PROTECTION_DEFAULT, value ? 1 : 0);
            return true;
        }
        return false;
    }
}
