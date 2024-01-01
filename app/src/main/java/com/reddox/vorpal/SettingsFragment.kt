package com.reddox.vorpal

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.util.Log
import androidx.core.app.ActivityCompat
import androidx.core.content.getSystemService
import androidx.preference.ListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceCategory
import androidx.preference.PreferenceFragmentCompat
import androidx.preference.PreferenceManager
import androidx.preference.SeekBarPreference

class SettingsFragment : PreferenceFragmentCompat() {
    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        setPreferencesFromResource(R.xml.preferences, rootKey)

        var bluetoothCategory: PreferenceCategory? =
            findPreference<PreferenceCategory?>("bluetooth")
        if (activity != null && bluetoothCategory != null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(requireActivity())

            var btPreferences = arrayOf<Pair<String, CharSequence>>(
                Pair("hexapod", getString(R.string.hexapod_bluetooth)),
                Pair("gamepad", getString(R.string.gamepad_bluetooth))
            )

            for (btPref in btPreferences) {
                var pref: ListPreference = ListPreference(requireActivity())
                pref.entries = arrayOf<CharSequence>()
                pref.entryValues = arrayOf<CharSequence>()
                pref.title = btPref.second
                pref.key = btPref.first

                pref.summary = sharedPreferences.getString(btPref.first, "")

                pref.setOnPreferenceChangeListener { preference, newValue ->
                    updatePrefSummary(
                        preference, newValue
                    )
                }
                pref.setOnPreferenceClickListener { preference -> updateBtDevices(preference) }

                bluetoothCategory.addPreference(pref)
            }
        }

    }

    private fun updatePrefSummary(p: Preference, value: Any?): Boolean {
        if (p is ListPreference) {
            if (value != null && value is CharSequence) {
                p.setSummary(value)
            } else {
                p.setSummary("")
            }
        }

        return true
    }

    private fun updateBtDevices(p: Preference): Boolean {
        val btManager: BluetoothManager? =
            requireActivity().getSystemService<BluetoothManager>() ?: return false
        val btAdapter: BluetoothAdapter = btManager!!.adapter


        if (!btAdapter.isEnabled) {
            val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
            startActivity(enableBtIntent)
        }

        val pairedDevices: Set<BluetoothDevice> = btAdapter.bondedDevices

        if (p is ListPreference) {
            val entries = ArrayList<CharSequence>()
            for (pairedDevice in pairedDevices) {
                entries.add(pairedDevice.name + " (" + pairedDevice.address + ")")
            }
            p.entries = entries.toTypedArray()
            p.entryValues = p.entries
        }
        return true
    }
}