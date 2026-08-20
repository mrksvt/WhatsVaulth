package com.mrksvt.waen.ui.fragments;

import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.RadioButton;
import android.widget.RadioGroup;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.mrksvt.waen.ui.fragments.base.BackNavHelper;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.mrksvt.waen.xposed.core.HookOverrideStore;

import java.util.Map;

public class HookOverrideFragment extends Fragment {

    private LinearLayout listContainer;

    private static final String[][] BUILTIN_HOOKS = {
        {"composer_send_btn",         "send",                       "id",       "Tombol kirim pesan"},
        {"action_mode_bar",           "action_mode_bar",            "id",       "Action bar saat seleksi teks"},
        {"bottom_nav",                "bottom_nav",                 "id",       "Navigasi bawah (tab bar)"},
        {"bottom_nav_divider",        "bottom_nav_divider",         "id",       "Garis pemisah navigasi bawah"},
        {"broadcast_icon",            "broadcast_icon",             "id",       "Ikon siaran/broadcast"},
        {"caption",                   "caption",                    "id",       "Teks keterangan media"},
        {"contact_photo",             "contact_photo",              "id",       "Foto profil kontak"},
        {"container_view",            "container_view",             "id",       "Container utama layar"},
        {"control_btn",               "control_btn",                "id",       "Tombol kontrol"},
        {"conversation_contact",      "conversation_contact",       "id",       "Header info kontak di percakapan"},
        {"conversation_text_row",     "conversation_text_row",      "id",       "Baris teks pesan di chat (trigger terjemahan)"},
        {"conversations_row_content", "conversations_row_content",  "id",       "Konten baris daftar percakapan"},
        {"date",                      "date",                       "id",       "Label tanggal pemisah pesan"},
        {"entry",                     "entry",                      "id",       "Input teks pesan"},
        {"header",                    "header",                     "id",       "Header percakapan"},
        {"input_attach_button",       "input_attach_button",        "id",       "Tombol lampiran di input"},
        {"input_layout_content",      "input_layout_content",       "id",       "Layout area input pesan"},
        {"media_container",           "media_container",            "id",       "Container media (foto/video)"},
        {"message_text",              "message_text",               "id",       "TextView isi teks pesan (trigger tap terjemahan)"},
        {"menu",                      "menu",                       "id",       "Menu konteks/opsi"},
        {"menuitem_search",           "menuitem_search",            "id",       "Item search di menu"},
        {"name_in_group",             "name_in_group",              "id",       "Nama pengirim di grup"},
        {"pin_indicator",             "pin_indicator",              "id",       "Indikator pesan disematkan"},
        {"reactions_bubble_layout",   "reactions_bubble_layout",    "id",       "Layout balon reaksi emoji"},
        {"reply_bar_background",      "reply_bar_background",       "id",       "Background bar balas pesan"},
        {"root_view",                 "root_view",                  "id",       "Root view utama layar"},
        {"row_content",               "row_content",                "id",       "Konten baris item"},
        {"send",                      "send",                       "id",       "Tombol kirim (alias)"},
        {"sticker",                   "sticker",                    "id",       "Container stiker"},
        {"text_view",                 "text_view",                  "id",       "TextView generik"},
        {"toolbar",                   "toolbar",                    "id",       "Toolbar/AppBar atas"},
        {"toolbar_logo",              "toolbar_logo",               "id",       "Logo di toolbar"},
        {"version",                   "version",                    "id",       "Label versi"},
        {"home_tab_chats_selector",   "home_tab_chats_selector",    "drawable", "Drawable tab chat aktif"},
        {"ic_viewonce",               "ic_viewonce",                "drawable", "Ikon pesan sekali lihat"},
    };

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
            @Nullable Bundle savedInstanceState) {
        android.content.res.Resources res = requireContext().getResources();
        float density = res.getDisplayMetrics().density;
        int dp1  = (int) (1  * density + 0.5f);
        int dp8  = (int) (8  * density + 0.5f);
        int dp12 = (int) (12 * density + 0.5f);
        int dp16 = (int) (16 * density + 0.5f);

        // Root vertical layout
        LinearLayout root = new LinearLayout(requireContext());
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp16, dp16, dp16, dp16);

        // Title
        TextView title = new TextView(requireContext());
        title.setText("Hook Override Manager");
        title.setTextSize(18f);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, 0, 0, dp8);
        root.addView(title);

        // Divider
        View divider = new View(requireContext());
        divider.setBackgroundColor(0x1F000000);
        root.addView(divider, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp1));

        // "Tambah Override" button
        Button addBtn = new Button(requireContext());
        addBtn.setText("Tambah Override");
        addBtn.setOnClickListener(v -> showAddDialog(null, null, false));
        root.addView(addBtn, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));

        // Divider
        View divider2 = new View(requireContext());
        divider2.setBackgroundColor(0x1F000000);
        root.addView(divider2, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp1));

        // Scrollable list container
        ScrollView scrollView = new ScrollView(requireContext());
        listContainer = new LinearLayout(requireContext());
        listContainer.setOrientation(LinearLayout.VERTICAL);
        listContainer.setPadding(0, dp8, 0, dp8);
        scrollView.addView(listContainer);
        root.addView(scrollView, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 1f));

        BackNavHelper.install(this);

        refreshList();

        return root;
    }

    private void refreshList() {
        if (listContainer == null) return;
        listContainer.removeAllViews();

        Map<String, String> overrides = HookOverrideStore.getAllOverrides(requireContext());
        android.content.res.Resources res = requireContext().getResources();
        float density = res.getDisplayMetrics().density;
        int dp1  = (int) (1  * density + 0.5f);
        int dp4  = (int) (4  * density + 0.5f);
        int dp8  = (int) (8  * density + 0.5f);
        int dp12 = (int) (12 * density + 0.5f);

        // ── Section: Hook Bawaan ──────────────────────────────────────────
        TextView builtinHeader = new TextView(requireContext());
        builtinHeader.setText("Hook Bawaan");
        builtinHeader.setTextSize(14f);
        builtinHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        builtinHeader.setTextColor(0xFF888888);
        builtinHeader.setPadding(0, dp4, 0, 0);
        listContainer.addView(builtinHeader);

        TextView builtinSubtitle = new TextView(requireContext());
        builtinSubtitle.setText("Tap untuk override nilai default");
        builtinSubtitle.setTextSize(11f);
        builtinSubtitle.setTextColor(0xFF888888);
        builtinSubtitle.setPadding(0, 0, 0, dp8);
        listContainer.addView(builtinSubtitle);

        for (String[] hook : BUILTIN_HOOKS) {
            final String hookKey     = hook[0];
            final String defaultVal  = hook[1];
            final boolean isDrawable = "drawable".equals(hook[2]);

            // Check if user already has an override for this built-in key
            String existingOverride = HookOverrideStore.getResourceOverride(requireContext(), hookKey);

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp4, 0, dp4);
            row.setClickable(true);
            row.setFocusable(true);
            row.setOnClickListener(v -> showAddDialog(hookKey, defaultVal, isDrawable));

            LinearLayout infoCol = new LinearLayout(requireContext());
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoCol.setLayoutParams(infoParams);

            TextView keyView = new TextView(requireContext());
            keyView.setText(hookKey);
            keyView.setTypeface(android.graphics.Typeface.MONOSPACE, android.graphics.Typeface.BOLD);
            keyView.setTextSize(13f);
            infoCol.addView(keyView);

            if (hook.length > 3 && hook[3] != null && !hook[3].isEmpty()) {
                TextView labelView = new TextView(requireContext());
                labelView.setText(hook[3]);
                labelView.setTextSize(12f);
                labelView.setTextColor(0xFF333333);
                infoCol.addView(labelView);
            }

            TextView defaultView = new TextView(requireContext());
            defaultView.setText("default: " + defaultVal + "  [" + hook[2] + "]");
            defaultView.setTextSize(11f);
            defaultView.setPadding(0, dp4, 0, 0);
            defaultView.setTextColor(0xFF888888);
            infoCol.addView(defaultView);

            if (existingOverride != null && !existingOverride.isEmpty()) {
                TextView overrideView = new TextView(requireContext());
                overrideView.setText("▶ " + existingOverride);
                overrideView.setTextSize(11f);
                overrideView.setPadding(0, dp4, 0, 0);
                overrideView.setTextColor(0xFF2E7D32); // green
                infoCol.addView(overrideView);
            }

            row.addView(infoCol);

            // Tap hint arrow (no delete for built-in)
            TextView tapArrow = new TextView(requireContext());
            tapArrow.setText("›");
            tapArrow.setTextSize(20f);
            tapArrow.setTextColor(0xFF888888);
            tapArrow.setPadding(dp8, 0, 0, 0);
            row.addView(tapArrow);

            listContainer.addView(row);

            View rowDivider = new View(requireContext());
            rowDivider.setBackgroundColor(0x1F000000);
            listContainer.addView(rowDivider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp1));
        }

        // ── Divider between sections ──────────────────────────────────────
        View sectionDivider = new View(requireContext());
        sectionDivider.setBackgroundColor(0x3F000000);
        LinearLayout.LayoutParams divParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp1 * 2);
        divParams.topMargin = dp8;
        divParams.bottomMargin = dp8;
        listContainer.addView(sectionDivider, divParams);

        // ── Section: Override Kustom ──────────────────────────────────────
        TextView customHeader = new TextView(requireContext());
        customHeader.setText("Override Kustom");
        customHeader.setTextSize(14f);
        customHeader.setTypeface(null, android.graphics.Typeface.BOLD);
        customHeader.setTextColor(0xFF888888);
        customHeader.setPadding(0, dp4, 0, dp8);
        listContainer.addView(customHeader);

        if (overrides.isEmpty()) {
            TextView empty = new TextView(requireContext());
            empty.setText("(belum ada override)");
            empty.setPadding(dp8, dp12, dp8, dp12);
            listContainer.addView(empty);
            return;
        }

        for (Map.Entry<String, String> entry : overrides.entrySet()) {
            String rawKey = entry.getKey();    // e.g. "resource_composer_send_btn"
            String rawVal = entry.getValue();  // e.g. "send_button"

            // Determine display key (strip prefix)
            String displayKey;
            String type;
            if (rawKey.startsWith("resource_")) {
                displayKey = rawKey.substring("resource_".length());
                type = "Resource";
            } else if (rawKey.startsWith("method_")) {
                // method keys: "method_<hookKey>_class" or "method_<hookKey>_method"
                // skip _method suffix entries — show only _class entry as the row
                if (rawKey.endsWith("_method")) continue;
                displayKey = rawKey.substring("method_".length());
                if (displayKey.endsWith("_class")) {
                    displayKey = displayKey.substring(0, displayKey.length() - "_class".length());
                }
                type = "Method";
            } else {
                displayKey = rawKey;
                type = "Unknown";
            }

            LinearLayout row = new LinearLayout(requireContext());
            row.setOrientation(LinearLayout.HORIZONTAL);
            row.setPadding(0, dp4, 0, dp4);

            LinearLayout infoCol = new LinearLayout(requireContext());
            infoCol.setOrientation(LinearLayout.VERTICAL);
            LinearLayout.LayoutParams infoParams = new LinearLayout.LayoutParams(
                    0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
            infoCol.setLayoutParams(infoParams);

            TextView keyView = new TextView(requireContext());
            keyView.setText("[" + type + "] " + displayKey);
            keyView.setTypeface(android.graphics.Typeface.MONOSPACE);
            keyView.setTextSize(13f);
            infoCol.addView(keyView);

            TextView valView = new TextView(requireContext());
            valView.setText(rawVal);
            valView.setTextSize(11f);
            valView.setPadding(0, dp4, 0, 0);
            valView.setTextColor(0xFF666666);
            infoCol.addView(valView);

            row.addView(infoCol);

            // Delete button (X)
            final String hookKeyForDelete = displayKey;
            Button deleteBtn = new Button(requireContext());
            deleteBtn.setText("X");
            deleteBtn.setTextColor(0xFFCC0000);
            LinearLayout.LayoutParams deleteParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.WRAP_CONTENT, LinearLayout.LayoutParams.WRAP_CONTENT);
            deleteBtn.setLayoutParams(deleteParams);
            deleteBtn.setOnClickListener(v -> {
                HookOverrideStore.removeOverride(requireContext(), hookKeyForDelete);
                refreshList();
                Toast.makeText(requireContext(), "Override dihapus", Toast.LENGTH_SHORT).show();
            });
            row.addView(deleteBtn);

            listContainer.addView(row);

            // Divider between rows
            View rowDivider = new View(requireContext());
            rowDivider.setBackgroundColor(0x1F000000);
            listContainer.addView(rowDivider, new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, dp1));
        }
    }

    /**
     * Show the add-override dialog.
     * @param prefillKey     if non-null, pre-fill Hook Key field (built-in tap)
     * @param prefillResource if non-null, pre-fill Resource Name field
     * @param prefillIsDrawable if true, dialog title notes it's a drawable type
     */
    private void showAddDialog(@Nullable String prefillKey,
                               @Nullable String prefillResource,
                               boolean prefillIsDrawable) {
        android.content.res.Resources res = requireContext().getResources();
        float density = res.getDisplayMetrics().density;
        int dp8  = (int) (8  * density + 0.5f);

        LinearLayout dialogLayout = new LinearLayout(requireContext());
        dialogLayout.setOrientation(LinearLayout.VERTICAL);
        dialogLayout.setPadding(dp8 * 2, dp8, dp8 * 2, dp8);

        // Hook Type radio
        TextView typeLabel = new TextView(requireContext());
        typeLabel.setText("Hook Type:");
        dialogLayout.addView(typeLabel);

        RadioGroup typeGroup = new RadioGroup(requireContext());
        typeGroup.setOrientation(RadioGroup.HORIZONTAL);
        RadioButton rbResource = new RadioButton(requireContext());
        rbResource.setText("Resource");
        rbResource.setId(View.generateViewId());
        RadioButton rbMethod = new RadioButton(requireContext());
        rbMethod.setText("Method");
        rbMethod.setId(View.generateViewId());
        typeGroup.addView(rbResource);
        typeGroup.addView(rbMethod);
        typeGroup.check(rbResource.getId()); // pre-select Resource (built-in hooks are all resource type)
        dialogLayout.addView(typeGroup);

        // Hook Key
        TextView hookKeyLabel = new TextView(requireContext());
        hookKeyLabel.setText("Hook Key:");
        hookKeyLabel.setPadding(0, dp8, 0, 0);
        dialogLayout.addView(hookKeyLabel);

        EditText hookKeyInput = new EditText(requireContext());
        hookKeyInput.setHint("contoh: composer_send_btn");
        hookKeyInput.setSingleLine(true);
        if (prefillKey != null) {
            hookKeyInput.setText(prefillKey);
        }
        dialogLayout.addView(hookKeyInput);

        // Resource fields
        TextView resourceLabel = new TextView(requireContext());
        resourceLabel.setText(prefillIsDrawable ? "Drawable Name:" : "Resource Name:");
        resourceLabel.setPadding(0, dp8, 0, 0);
        dialogLayout.addView(resourceLabel);

        EditText resourceInput = new EditText(requireContext());
        resourceInput.setHint(prefillIsDrawable ? "contoh: ic_custom_drawable" : "contoh: send_button");
        resourceInput.setSingleLine(true);
        if (prefillResource != null) {
            resourceInput.setText(prefillResource);
        }
        dialogLayout.addView(resourceInput);

        // Method fields
        TextView classLabel = new TextView(requireContext());
        classLabel.setText("Class Name:");
        classLabel.setPadding(0, dp8, 0, 0);
        classLabel.setVisibility(View.GONE);
        dialogLayout.addView(classLabel);

        EditText classInput = new EditText(requireContext());
        classInput.setHint("contoh: com.whatsapp.ConversationFragment");
        classInput.setSingleLine(true);
        classInput.setVisibility(View.GONE);
        dialogLayout.addView(classInput);

        TextView methodLabel = new TextView(requireContext());
        methodLabel.setText("Method Name:");
        methodLabel.setPadding(0, dp8, 0, 0);
        methodLabel.setVisibility(View.GONE);
        dialogLayout.addView(methodLabel);

        EditText methodInput = new EditText(requireContext());
        methodInput.setHint("contoh: onResume");
        methodInput.setSingleLine(true);
        methodInput.setVisibility(View.GONE);
        dialogLayout.addView(methodInput);

        // Toggle visibility based on type
        typeGroup.setOnCheckedChangeListener((group, checkedId) -> {
            boolean isMethod = checkedId == rbMethod.getId();
            resourceLabel.setVisibility(isMethod ? View.GONE : View.VISIBLE);
            resourceInput.setVisibility(isMethod ? View.GONE : View.VISIBLE);
            classLabel.setVisibility(isMethod ? View.VISIBLE : View.GONE);
            classInput.setVisibility(isMethod ? View.VISIBLE : View.GONE);
            methodLabel.setVisibility(isMethod ? View.VISIBLE : View.GONE);
            methodInput.setVisibility(isMethod ? View.VISIBLE : View.GONE);
        });

        String dialogTitle = prefillKey != null
                ? "Override: " + prefillKey
                : "Tambah Hook Override";

        new MaterialAlertDialogBuilder(requireContext())
                .setTitle(dialogTitle)
                .setView(dialogLayout)
                .setPositiveButton("Simpan", (dialog, which) -> {
                    String hookKey = hookKeyInput.getText().toString().trim();
                    if (hookKey.isEmpty()) {
                        Toast.makeText(requireContext(), "Hook Key tidak boleh kosong", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    boolean isMethod = typeGroup.getCheckedRadioButtonId() == rbMethod.getId();
                    if (isMethod) {
                        String className = classInput.getText().toString().trim();
                        String methodName = methodInput.getText().toString().trim();
                        if (className.isEmpty() || methodName.isEmpty()) {
                            Toast.makeText(requireContext(), "Class Name dan Method Name wajib diisi", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        HookOverrideStore.setMethodOverride(requireContext(), hookKey, className, methodName);
                    } else {
                        String resourceName = resourceInput.getText().toString().trim();
                        if (resourceName.isEmpty()) {
                            Toast.makeText(requireContext(), "Resource Name tidak boleh kosong", Toast.LENGTH_SHORT).show();
                            return;
                        }
                        HookOverrideStore.setResourceOverride(requireContext(), hookKey, resourceName);
                    }
                    refreshList();
                    Toast.makeText(requireContext(), "Override disimpan", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Batal", null)
                .show();
    }
}
