package com.mrksvt.waen.ui.fragments;

import android.content.SharedPreferences;
import android.graphics.Color;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;
import android.widget.Toast;

import androidx.activity.OnBackPressedCallback;
import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.fragment.app.Fragment;
import androidx.fragment.app.FragmentManager;
import androidx.preference.PreferenceManager;

import com.google.android.material.button.MaterialButton;
import com.google.android.material.switchmaterial.SwitchMaterial;
import com.mrksvt.waen.R;

public class ThemeBuilderFragment extends Fragment {

    private static final String[] PRESET_NAMES = {"Default", "Blue", "Dark", "Pink", "Purple", "Ocean"};
    private static final int[][] PRESET_COLORS = {
            {0xFF00A884, 0xFFECE5DD, 0xFF111B21},   // Default WA green
            {0xFF0B57D0, 0xFFE8F0FE, 0xFF1F1F1F},   // Blue
            {0xFF0B141A, 0xFF111B21, 0xFFE9EDEF},   // Dark
            {0xFFD81B60, 0xFFFCE4EC, 0xFF1F1F1F},   // Pink
            {0xFF6750A4, 0xFFEDE7F6, 0xFF1F1F1F},   // Purple
            {0xFF00696D, 0xFFE0F7FA, 0xFF1F1F1F},   // Ocean
    };

    private SharedPreferences prefs;

    private View previewMockup;
    private View previewToolbar;
    private TextView previewToolbarTitle;
    private TextView previewBubbleIncoming;
    private TextView previewBubbleOutgoing;
    private TextView previewText;

    private View swatchPrimary;
    private View swatchBackground;
    private View swatchText;

    private SwitchMaterial switchMonet;

    private int primaryColor = 0xFF00A884;
    private int backgroundColor = 0xFFECE5DD;
    private int textColor = 0xFF111B21;

    @Nullable
    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, @Nullable ViewGroup container,
                             @Nullable Bundle savedInstanceState) {
        return inflater.inflate(R.layout.fragment_theme_builder, container, false);
    }

    @Override
    public void onViewCreated(@NonNull View view, @Nullable Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);

        prefs = PreferenceManager.getDefaultSharedPreferences(requireContext());

        previewMockup = view.findViewById(R.id.preview_mockup);
        previewToolbar = view.findViewById(R.id.preview_toolbar);
        previewToolbarTitle = view.findViewById(R.id.preview_toolbar_title);
        previewBubbleIncoming = view.findViewById(R.id.preview_bubble_incoming);
        previewBubbleOutgoing = view.findViewById(R.id.preview_bubble_outgoing);
        previewText = view.findViewById(R.id.preview_text);

        swatchPrimary = view.findViewById(R.id.swatch_primary);
        swatchBackground = view.findViewById(R.id.swatch_background);
        swatchText = view.findViewById(R.id.swatch_text);

        switchMonet = view.findViewById(R.id.switch_monet);

        loadCurrentColors();
        applyPreview();

        view.findViewById(R.id.row_primary).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), primaryColor,
                        color -> {
                            primaryColor = color;
                            saveAndRefresh();
                        }).show());

        view.findViewById(R.id.row_background).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), backgroundColor,
                        color -> {
                            backgroundColor = color;
                            saveAndRefresh();
                        }).show());

        view.findViewById(R.id.row_text).setOnClickListener(v ->
                new com.mrksvt.waen.views.dialog.SimpleColorPickerDialog(
                        requireContext(), textColor,
                        color -> {
                            textColor = color;
                            saveAndRefresh();
                        }).show());

        switchMonet.setOnCheckedChangeListener((buttonView, isChecked) -> {
            prefs.edit().putBoolean("changecolor", isChecked)
                    .putString("changecolor_mode", "monet").apply();
            applyPreview();
        });

        view.findViewById(R.id.btn_reset_theme).setOnClickListener(v -> {
            primaryColor = 0xFF00A884;
            backgroundColor = 0xFFECE5DD;
            textColor = 0xFF111B21;
            switchMonet.setChecked(false);
            prefs.edit().putBoolean("changecolor", false)
                    .putString("changecolor_mode", "manual")
                    .putInt("primary_color", 0)
                    .putInt("background_color", 0)
                    .putInt("text_color", 0).apply();
            applyPreview();
            Toast.makeText(requireContext(), R.string.theme_builder_reset, Toast.LENGTH_SHORT).show();
        });

        buildPresetRow(view.findViewById(R.id.preset_row));

        requireActivity().getOnBackPressedDispatcher().addCallback(getViewLifecycleOwner(), new OnBackPressedCallback(true) {
            @Override
            public void handleOnBackPressed() {
                FragmentManager mgr = getParentFragment() != null
                        ? getParentFragment().getChildFragmentManager()
                        : getParentFragmentManager();
                if (mgr.getBackStackEntryCount() > 0) {
                    mgr.popBackStack();
                } else {
                    setEnabled(false);
                    requireActivity().getOnBackPressedDispatcher().onBackPressed();
                }
            }
        });
    }

    private void loadCurrentColors() {
        boolean changeColorEnabled = prefs.getBoolean("changecolor", false);
        boolean monet = changeColorEnabled && "monet".equals(prefs.getString("changecolor_mode", "manual"));
        switchMonet.setChecked(monet);
        if (changeColorEnabled && !monet) {
            primaryColor = prefs.getInt("primary_color", 0xFF00A884);
            backgroundColor = prefs.getInt("background_color", 0xFFECE5DD);
            textColor = prefs.getInt("text_color", 0xFF111B21);
        } else {
            primaryColor = 0xFF00A884;
            backgroundColor = 0xFFECE5DD;
            textColor = 0xFF111B21;
        }
    }

    private void saveAndRefresh() {
        prefs.edit()
                .putBoolean("changecolor", true)
                .putString("changecolor_mode", "manual")
                .putInt("primary_color", primaryColor)
                .putInt("background_color", backgroundColor)
                .putInt("text_color", textColor)
                .apply();
        applyPreview();
    }

    private void applyPreview() {
        int primary = switchMonet.isChecked() ? 0xFF00A884 : primaryColor;
        int background = switchMonet.isChecked() ? 0xFFECE5DD : backgroundColor;
        int text = switchMonet.isChecked() ? 0xFF111B21 : textColor;

        previewToolbar.setBackgroundColor(primary);
        previewToolbarTitle.setTextColor(contrastOn(primary));
        previewBubbleOutgoing.setBackgroundColor(primary);
        previewBubbleOutgoing.setTextColor(contrastOn(primary));
        previewBubbleIncoming.setTextColor(text);
        previewText.setTextColor(text);
        previewMockup.setBackgroundColor(background);

        swatchPrimary.setBackgroundColor(primary);
        swatchBackground.setBackgroundColor(background);
        swatchText.setBackgroundColor(text);
    }

    private int contrastOn(int color) {
        double luminance = (0.299 * Color.red(color) + 0.587 * Color.green(color) + 0.114 * Color.blue(color)) / 255.0;
        return luminance > 0.5 ? 0xFF000000 : 0xFFFFFFFF;
    }

    private void buildPresetRow(LinearLayout row) {
        row.removeAllViews();
        for (int i = 0; i < PRESET_NAMES.length; i++) {
            final int idx = i;
            MaterialButton btn = new MaterialButton(requireContext());
            btn.setText(PRESET_NAMES[i]);
            btn.setTextSize(12);
            btn.setAllCaps(false);
            LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(
                    ViewGroup.LayoutParams.WRAP_CONTENT, ViewGroup.LayoutParams.WRAP_CONTENT);
            lp.setMarginEnd(dp(8));
            btn.setLayoutParams(lp);
            btn.setOnClickListener(v -> {
                primaryColor = PRESET_COLORS[idx][0];
                backgroundColor = PRESET_COLORS[idx][1];
                textColor = PRESET_COLORS[idx][2];
                switchMonet.setChecked(false);
                saveAndRefresh();
            });
            row.addView(btn);
        }
    }

    private int dp(int value) {
        return (int) (value * getResources().getDisplayMetrics().density);
    }
}
