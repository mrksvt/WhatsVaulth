package com.mrksvt.waen.xposed.features.translator

import android.content.SharedPreferences
import android.os.Bundle
import androidx.preference.EditTextPreference
import androidx.preference.MultiSelectListPreference
import androidx.preference.Preference
import androidx.preference.PreferenceManager
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import com.google.mlkit.nl.translate.TranslateLanguage
import com.google.mlkit.nl.translate.Translation
import com.google.mlkit.nl.translate.TranslatorOptions
import com.mrksvt.waen.R
import com.mrksvt.waen.ui.fragments.base.BasePreferenceFragment

class TranslatorSettingsFragment : BasePreferenceFragment() {

    override fun onCreatePreferences(savedInstanceState: Bundle?, rootKey: String?) {
        super.onCreatePreferences(savedInstanceState, rootKey)
        setPreferencesFromResource(R.xml.preference_general_translator, rootKey)
        setupSystemPromptValidation()
        setupResetSystemPrompt()
        setupOfflineTranslator()
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

    private fun setupOfflineTranslator() {
        val downloadPref = findPreference<Preference>("offline_translator_download") ?: return
        downloadPref.setOnPreferenceClickListener {
            val langsPref = findPreference<MultiSelectListPreference>("offline_translator_languages")
            val selectedLangs = langsPref?.values ?: emptySet()
            if (selectedLangs.isEmpty()) {
                MaterialAlertDialogBuilder(requireContext())
                    .setTitle(R.string.offline_translator_title)
                    .setMessage(R.string.offline_translator_no_lang_selected)
                    .setPositiveButton(android.R.string.ok, null)
                    .show()
                return@setOnPreferenceClickListener true
            }
            downloadOfflineModels(selectedLangs, downloadPref)
            true
        }
    }

    private fun downloadOfflineModels(langs: Set<String>, pref: Preference) {
        try {
            val sourceLang = TranslateLanguage.INDONESIAN
            pref.summary = getString(R.string.offline_translator_downloading)
            pref.isEnabled = false

            val ctx = requireContext()
            val progressBar = android.widget.ProgressBar(ctx, null, android.R.attr.progressBarStyleHorizontal).apply {
                max = langs.size
                progress = 0
                isIndeterminate = false
            }
            val dp16 = (16 * ctx.resources.displayMetrics.density).toInt()
            val container = android.widget.LinearLayout(ctx).apply {
                orientation = android.widget.LinearLayout.VERTICAL
                setPadding(dp16 * 2, dp16, dp16 * 2, dp16)
                addView(progressBar)
            }
            val progressDialog = MaterialAlertDialogBuilder(ctx)
                .setTitle(R.string.offline_translator_title)
                .setMessage(R.string.offline_translator_downloading)
                .setView(container)
                .setCancelable(false)
                .show()

            val normalizedLangs = langs.map { code ->
                val normalized = when (code) {
                    "zh-CN", "zh-TW", "zh" -> "zh"
                    "jv" -> "jw"
                    else -> code.substringBefore("-")
                }
                normalized
            }.filter { it.isNotBlank() }.toSet()

            if (normalizedLangs.isEmpty()) {
                progressDialog.dismiss()
                pref.isEnabled = true
                pref.summary = getString(R.string.offline_translator_download_sum)
                android.widget.Toast.makeText(ctx, "Tidak ada kode bahasa valid", android.widget.Toast.LENGTH_SHORT).show()
                return
            }

            val remaining = java.util.concurrent.atomic.AtomicInteger(normalizedLangs.size)
            val done = java.util.concurrent.atomic.AtomicInteger(0)
            val failed = java.util.concurrent.CopyOnWriteArrayList<String>()
            for (langCode in normalizedLangs) {
                val targetLang = TranslateLanguage.fromLanguageTag(langCode) ?: run {
                    failed.add(langCode)
                    if (remaining.decrementAndGet() == 0) finishDownload(progressDialog, progressBar, pref, failed)
                    continue
                }
                val options = TranslatorOptions.Builder()
                    .setSourceLanguage(sourceLang)
                    .setTargetLanguage(targetLang)
                    .build()
                val translator = Translation.getClient(options)
                translator.downloadModelIfNeeded()
                    .addOnSuccessListener {
                        activity?.runOnUiThread { progressBar.progress = done.incrementAndGet() }
                        if (remaining.decrementAndGet() == 0) finishDownload(progressDialog, progressBar, pref, failed)
                        translator.close()
                    }
                    .addOnFailureListener { e ->
                        failed.add(langCode)
                        activity?.runOnUiThread { progressBar.progress = done.incrementAndGet() }
                        if (remaining.decrementAndGet() == 0) finishDownload(progressDialog, progressBar, pref, failed)
                        translator.close()
                    }
            }
        } catch (e: Exception) {
            pref.isEnabled = true
            pref.summary = getString(R.string.offline_translator_download_sum)
            android.widget.Toast.makeText(requireContext(), "Error: ${e.message}", android.widget.Toast.LENGTH_LONG).show()
        }
    }

    private fun finishDownload(
        dialog: androidx.appcompat.app.AlertDialog,
        progressBar: android.widget.ProgressBar,
        pref: Preference,
        failed: List<String>
    ) {
        activity?.runOnUiThread {
            dialog.dismiss()
            pref.isEnabled = true
            pref.summary = if (failed.isEmpty())
                getString(R.string.offline_translator_download_success)
            else
                getString(R.string.offline_translator_download_partial, failed.joinToString(", "))
        }
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
