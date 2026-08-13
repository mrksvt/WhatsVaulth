package com.mrksvt.waen.xposed.features.translator

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.mrksvt.waen.R
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment

class TranslatorSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.preference_general_translator, rootKey)
        setupSystemPromptValidation()
        setupResetSystemPrompt()
    }

    private fun setupResetSystemPrompt() {
        val resetPref = findPreference<Preference>("translator_reset_system_prompt") ?: return
        resetPref.setOnPreferenceClickListener {
            showResetConfirmationDialog()
            true
        }
    }

    private fun showResetConfirmationDialog() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.translator_reset_system_prompt_confirm_title)
            .setMessage(R.string.translator_reset_system_prompt_confirm_message)
            .setPositiveButton(android.R.string.ok) { _, _ ->
                resetSystemPromptToDefault()
            }
            .setNegativeButton(android.R.string.cancel, null)
            .show()
    }

    private fun resetSystemPromptToDefault() {
        mPrefs?.edit()?.remove("groq_custom_system_prompt")?.apply()
        val pref = findPreference<EditTextPreference>("groq_custom_system_prompt") ?: return
        pref.text = ""
        pref.summary = getString(R.string.groq_custom_system_prompt_sum)
    }

    private fun setupSystemPromptValidation() {
        val systemPromptPref = findPreference<EditTextPreference>("groq_custom_system_prompt")
            ?: return

        systemPromptPref.setOnPreferenceChangeListener { _, newValue ->
            val value = newValue as? String ?: return@setOnPreferenceChangeListener true
            // Empty string is valid (means use default), but pure whitespace is not
            if (value.isNotEmpty() && value.isBlank()) {
                showSystemPromptError()
                false
            } else {
                true
            }
        }
    }

    private fun showSystemPromptError() {
        MaterialAlertDialogBuilder(requireContext())
            .setTitle(R.string.groq_custom_system_prompt)
            .setMessage(R.string.groq_custom_system_prompt_error)
            .setPositiveButton(android.R.string.ok, null)
            .show()
    }

    override fun onSharedPreferenceChanged(sharedPreferences: SharedPreferences?, key: String?) {
        super.onSharedPreferenceChanged(sharedPreferences, key)
        if (key == "groq_custom_system_prompt") {
            updateSystemPromptSummary(sharedPreferences)
        }
    }

    private fun updateSystemPromptSummary(sharedPreferences: SharedPreferences?) {
        val prefs = sharedPreferences
            ?: PreferenceManager.getDefaultSharedPreferences(requireContext())
        val pref = findPreference<EditTextPreference>("groq_custom_system_prompt") ?: return
        val value = prefs.getString("groq_custom_system_prompt", "")
        pref.summary = if (value.isNullOrEmpty()) {
            getString(R.string.groq_custom_system_prompt_sum)
        } else {
            value
        }
    }

    override fun onResume() {
        super.onResume()
        updateSystemPromptSummary(mPrefs)
    }
}
