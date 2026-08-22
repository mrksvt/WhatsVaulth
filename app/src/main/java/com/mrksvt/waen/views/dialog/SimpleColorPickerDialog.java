package com.mrksvt.waen.views.dialog;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.ComposeShader;
import android.graphics.LinearGradient;
import android.graphics.Paint;
import android.graphics.PorterDuff;
import android.graphics.PorterDuffXfermode;
import android.graphics.RectF;
import android.graphics.Shader;
import android.graphics.drawable.BitmapDrawable;
import android.text.Editable;
import android.text.InputFilter;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.MotionEvent;
import android.view.View;
import android.view.ContextThemeWrapper;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Space;
import android.widget.TextView;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.google.android.material.textfield.TextInputLayout;
import com.mrksvt.waen.R;

import java.io.File;
import java.io.FileInputStream;
import java.util.concurrent.Executors;

public class SimpleColorPickerDialog {

    private final Context context;
    private final OnColorSelectedListener listener;
    private int selectedColor;
    private int initialColor;
    private String svgPreviewPath = null;

    public SimpleColorPickerDialog(Context context, OnColorSelectedListener listener) {
        this.context = wrapContext(context);
        this.listener = listener;
        this.selectedColor = Color.RED;
        this.initialColor = Color.RED;
    }

    public SimpleColorPickerDialog(Context context, int initialColor, OnColorSelectedListener listener) {
        this.context = wrapContext(context);
        this.listener = listener;
        this.selectedColor = initialColor;
        this.initialColor = initialColor;
    }

    // WhatsApp host context lacks MaterialComponents theme; force module theme so
    // TextInputLayout & MaterialAlertDialogBuilder can resolve their attributes.
    private static Context wrapContext(Context context) {
        if (context == null) return null;
        try {
            return new ContextThemeWrapper(context, R.style.AppTheme);
        } catch (Throwable t) {
            return context;
        }
    }

    public SimpleColorPickerDialog setSvgPreviewPath(String path) {
        this.svgPreviewPath = path;
        return this;
    }

    public void show() {
        float density = context.getResources().getDisplayMetrics().density;
        int dp8 = (int) (8 * density);
        int dp12 = (int) (12 * density);
        int dp24 = (int) (24 * density);
        int dp36 = (int) (36 * density);
        int dp48 = (int) (48 * density);
        int dp240 = (int) (240 * density);

        // Root scroll
        ScrollView scrollView = new ScrollView(context);
        LinearLayout layout = new LinearLayout(context);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(dp24, dp24, dp24, dp24);
        scrollView.addView(layout);

        // 2D saturation/value picker
        final ColorPickerView colorPickerView = new ColorPickerView(context);
        LinearLayout.LayoutParams cpParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp240);
        colorPickerView.setLayoutParams(cpParams);
        layout.addView(colorPickerView);

        layout.addView(makeSpace(context, dp8));

        // Hue slider
        final HueSliderView hueSliderView = new HueSliderView(context);
        LinearLayout.LayoutParams hsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp36);
        hueSliderView.setLayoutParams(hsParams);
        layout.addView(hueSliderView);

        layout.addView(makeSpace(context, dp12));

        // SVG preview
        final ImageView iconPreview = new ImageView(context);
        iconPreview.setScaleType(ImageView.ScaleType.CENTER_INSIDE);
        LinearLayout.LayoutParams iconParams = new LinearLayout.LayoutParams(dp48, dp48);
        iconParams.gravity = Gravity.CENTER_HORIZONTAL;
        iconPreview.setLayoutParams(iconParams);
        if (svgPreviewPath != null && !svgPreviewPath.isEmpty()) {
            layout.addView(iconPreview);
            layout.addView(makeSpace(context, dp8));
        } else {
            iconPreview.setVisibility(View.GONE);
        }

        // HEX input
        TextInputLayout hexInputLayout = new TextInputLayout(context);
        hexInputLayout.setHint("#RRGGBB");
        LinearLayout.LayoutParams tilParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
        hexInputLayout.setLayoutParams(tilParams);

        final EditText hexInput = new EditText(context);
        hexInput.setFilters(new InputFilter[]{new InputFilter.LengthFilter(7)});
        hexInput.setSingleLine(true);
        hexInputLayout.addView(hexInput);
        layout.addView(hexInputLayout);

        layout.addView(makeSpace(context, dp8));

        // RGB / HSV / HEX info row
        LinearLayout infoRow = new LinearLayout(context);
        infoRow.setOrientation(LinearLayout.HORIZONTAL);
        infoRow.setWeightSum(3f);

        final TextView tvRgb = makeInfoLabel(context);
        final TextView tvHsv = makeInfoLabel(context);
        final TextView tvHex = makeInfoLabel(context);

        infoRow.addView(tvRgb);
        infoRow.addView(tvHsv);
        infoRow.addView(tvHex);
        layout.addView(infoRow);

        // State flag to suppress recursive updates
        final boolean[] isUpdating = {false};

        float[] currentHsv = new float[3];

        // Wire: hue slider → colorPickerView + labels
        hueSliderView.setOnHueChangedListener(hue -> {
            if (isUpdating[0]) return;
            isUpdating[0] = true;
            colorPickerView.setHue(hue);
            currentHsv[0] = hue;
            currentHsv[1] = colorPickerView.getSaturation();
            currentHsv[2] = colorPickerView.getValue();
            selectedColor = Color.HSVToColor(currentHsv);
            updateLabels(hexInput, tvRgb, tvHsv, tvHex, selectedColor, currentHsv);
            if (svgPreviewPath != null && !svgPreviewPath.isEmpty()) {
                loadIconPreview(iconPreview, svgPreviewPath, selectedColor);
            }
            isUpdating[0] = false;
        });

        // Wire: colorPickerView → labels
        colorPickerView.setOnSvChangedListener((sat, val) -> {
            if (isUpdating[0]) return;
            isUpdating[0] = true;
            currentHsv[0] = hueSliderView.getHue();
            currentHsv[1] = sat;
            currentHsv[2] = val;
            selectedColor = Color.HSVToColor(currentHsv);
            updateLabels(hexInput, tvRgb, tvHsv, tvHex, selectedColor, currentHsv);
            if (svgPreviewPath != null && !svgPreviewPath.isEmpty()) {
                loadIconPreview(iconPreview, svgPreviewPath, selectedColor);
            }
            isUpdating[0] = false;
        });

        // Wire: hex input → colorPickerView + hueSlider + labels
        hexInput.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int start, int count, int after) {}
            @Override public void afterTextChanged(Editable s) {}
            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {
                if (isUpdating[0]) return;
                if (s.length() == 7 && s.charAt(0) == '#') {
                    try {
                        isUpdating[0] = true;
                        int parsed = Color.parseColor(s.toString());
                        float[] hsv = new float[3];
                        Color.colorToHSV(parsed, hsv);
                        selectedColor = parsed;
                        currentHsv[0] = hsv[0];
                        currentHsv[1] = hsv[1];
                        currentHsv[2] = hsv[2];
                        hueSliderView.setHue(hsv[0]);
                        colorPickerView.setColor(parsed);
                        updateLabelsNoHex(tvRgb, tvHsv, tvHex, parsed, hsv);
                        if (svgPreviewPath != null && !svgPreviewPath.isEmpty()) {
                            loadIconPreview(iconPreview, svgPreviewPath, selectedColor);
                        }
                        isUpdating[0] = false;
                    } catch (IllegalArgumentException e) {
                        isUpdating[0] = false;
                    }
                }
            }
        });

        // Initial color setup
        float[] initHsv = new float[3];
        if (initialColor != 0) {
            Color.colorToHSV(initialColor, initHsv);
        } else {
            initHsv[0] = 0f;
            initHsv[1] = 1f;
            initHsv[2] = 1f;
        }
        currentHsv[0] = initHsv[0];
        currentHsv[1] = initHsv[1];
        currentHsv[2] = initHsv[2];

        hueSliderView.setHue(initHsv[0]);
        colorPickerView.setHue(initHsv[0]);
        colorPickerView.setSaturation(initHsv[1]);
        colorPickerView.setValue(initHsv[2]);
        selectedColor = Color.HSVToColor(initHsv);
        // Populate hex without triggering watcher (watcher checks length == 7)
        isUpdating[0] = true;
        hexInput.setText(String.format("#%06X", 0xFFFFFF & selectedColor));
        isUpdating[0] = false;
        updateLabelsNoHex(tvRgb, tvHsv, tvHex, selectedColor, initHsv);
        if (svgPreviewPath != null && !svgPreviewPath.isEmpty()) {
            loadIconPreview(iconPreview, svgPreviewPath, selectedColor);
        }

        new MaterialAlertDialogBuilder(context)
                .setTitle(R.string.select_a_color)
                .setView(scrollView)
                .setPositiveButton("OK", (dialog, which) -> {
                    if (listener != null) listener.onColorSelected(selectedColor);
                })
                .setNegativeButton(R.string.cancel, null)
                .show();
    }

    // Updates hex + all labels
    private void updateLabels(EditText hexInput, TextView tvRgb, TextView tvHsv, TextView tvHex,
                               int color, float[] hsv) {
        String hex = String.format("#%06X", 0xFFFFFF & color);
        hexInput.setText(hex);
        // Move cursor to end
        hexInput.setSelection(hexInput.getText().length());
        updateLabelsNoHex(tvRgb, tvHsv, tvHex, color, hsv);
    }

    // Updates only the 3 info labels (no hex EditText)
    private void updateLabelsNoHex(TextView tvRgb, TextView tvHsv, TextView tvHex,
                                    int color, float[] hsv) {
        int r = Color.red(color);
        int g = Color.green(color);
        int b = Color.blue(color);
        tvRgb.setText("RGB\n" + r + ", " + g + ", " + b);
        tvHsv.setText("HSV\n" + Math.round(hsv[0]) + "°, "
                + Math.round(hsv[1] * 100) + "%, "
                + Math.round(hsv[2] * 100) + "%");
        tvHex.setText("HEX\n" + String.format("#%06X", 0xFFFFFF & color));
    }

    private static Space makeSpace(Context ctx, int size) {
        Space space = new Space(ctx);
        space.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, size));
        return space;
    }

    private static TextView makeInfoLabel(Context ctx) {
        TextView tv = new TextView(ctx);
        tv.setGravity(Gravity.CENTER);
        tv.setTextSize(11f);
        LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0,
                LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        tv.setLayoutParams(p);
        return tv;
    }

    private void loadIconPreview(ImageView imageView, String path, int color) {
        Executors.newSingleThreadExecutor().execute(() -> {
            try {
                int size = 96;
                Bitmap bmp;
                if (path.endsWith(".svg")) {
                    com.caverock.androidsvg.SVG svg = com.caverock.androidsvg.SVG.getFromInputStream(
                            new FileInputStream(new File(path)));
                    bmp = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
                    Canvas canvas = new Canvas(bmp);
                    svg.setDocumentWidth(size);
                    svg.setDocumentHeight(size);
                    svg.renderToCanvas(canvas);
                } else {
                    bmp = android.graphics.BitmapFactory.decodeFile(path);
                }
                if (bmp == null) return;
                Bitmap tinted = Bitmap.createBitmap(bmp.getWidth(), bmp.getHeight(), Bitmap.Config.ARGB_8888);
                Canvas tc = new Canvas(tinted);
                Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
                tc.drawBitmap(bmp, 0f, 0f, paint);
                paint.setXfermode(new PorterDuffXfermode(PorterDuff.Mode.SRC_ATOP));
                paint.setColor(color);
                tc.drawRect(0, 0, tinted.getWidth(), tinted.getHeight(), paint);
                BitmapDrawable drawable = new BitmapDrawable(context.getResources(), tinted);
                imageView.post(() -> imageView.setImageDrawable(drawable));
            } catch (Exception ignored) {}
        });
    }

    // -------------------------------------------------------------------------
    // Inner class: 2D saturation/value picker
    // -------------------------------------------------------------------------
    public static class ColorPickerView extends View {

        public interface OnSvChangedListener {
            void onSvChanged(float sat, float val);
        }

        private float hue = 0f;
        private float sat = 1f;
        private float val = 1f;

        private Paint satPaint;
        private Paint valPaint;
        private Paint thumbPaint;
        private Paint thumbStrokePaint;

        private OnSvChangedListener svListener;

        private float thumbRadius;

        public ColorPickerView(Context context) {
            super(context);
            setLayerType(View.LAYER_TYPE_SOFTWARE, null);
            init(context);
        }

        private void init(Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            thumbRadius = 12 * density;

            satPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            valPaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thumbPaint.setColor(Color.WHITE);
            thumbPaint.setStyle(Paint.Style.FILL);
            thumbPaint.setAlpha(180);

            thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thumbStrokePaint.setColor(Color.WHITE);
            thumbStrokePaint.setStyle(Paint.Style.STROKE);
            thumbStrokePaint.setStrokeWidth(2 * density);
        }

        private void buildShaders(int w, int h) {
            if (w <= 0 || h <= 0) return;
            int pureHue = Color.HSVToColor(new float[]{hue, 1f, 1f});
            satPaint.setShader(new LinearGradient(0, 0, w, 0,
                    Color.WHITE, pureHue, Shader.TileMode.CLAMP));
            valPaint.setShader(new LinearGradient(0, 0, 0, h,
                    0x00000000, 0xFF000000, Shader.TileMode.CLAMP));
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            buildShaders(w, h);
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            canvas.drawRect(0, 0, w, h, satPaint);
            canvas.drawRect(0, 0, w, h, valPaint);

            float cx = sat * w;
            float cy = (1f - val) * h;

            canvas.drawCircle(cx, cy, thumbRadius, thumbPaint);
            canvas.drawCircle(cx, cy, thumbRadius, thumbStrokePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                float y = Math.max(0, Math.min(event.getY(), getHeight()));
                sat = x / getWidth();
                val = 1f - (y / getHeight());
                invalidate();
                if (svListener != null) svListener.onSvChanged(sat, val);
                return true;
            }
            return super.onTouchEvent(event);
        }

        public void setHue(float h) {
            this.hue = h;
            buildShaders(getWidth(), getHeight());
            invalidate();
        }

        public float getSaturation() { return sat; }
        public float getValue() { return val; }

        public void setSaturation(float s) {
            this.sat = Math.max(0f, Math.min(1f, s));
            invalidate();
        }

        public void setValue(float v) {
            this.val = Math.max(0f, Math.min(1f, v));
            invalidate();
        }

        public void setColor(int rgb) {
            float[] hsv = new float[3];
            Color.colorToHSV(rgb, hsv);
            this.hue = hsv[0];
            this.sat = hsv[1];
            this.val = hsv[2];
            buildShaders(getWidth(), getHeight());
            invalidate();
        }

        public void setOnSvChangedListener(OnSvChangedListener l) {
            this.svListener = l;
        }
    }

    // -------------------------------------------------------------------------
    // Inner class: horizontal hue slider
    // -------------------------------------------------------------------------
    public static class HueSliderView extends View {

        public interface OnHueChangedListener {
            void onHueChanged(float hue);
        }

        private float hue = 0f;
        private Paint huePaint;
        private Paint thumbPaint;
        private Paint thumbStrokePaint;
        private OnHueChangedListener hueListener;
        private float thumbRadius;
        private float cornerRadius;

        public HueSliderView(Context context) {
            super(context);
            init(context);
        }

        private void init(Context context) {
            float density = context.getResources().getDisplayMetrics().density;
            thumbRadius = 10 * density;
            cornerRadius = 4 * density;

            huePaint = new Paint(Paint.ANTI_ALIAS_FLAG);

            thumbPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thumbPaint.setColor(Color.WHITE);
            thumbPaint.setStyle(Paint.Style.FILL);
            thumbPaint.setAlpha(220);

            thumbStrokePaint = new Paint(Paint.ANTI_ALIAS_FLAG);
            thumbStrokePaint.setColor(Color.WHITE);
            thumbStrokePaint.setStyle(Paint.Style.STROKE);
            thumbStrokePaint.setStrokeWidth(2 * density);
        }

        @Override
        protected void onSizeChanged(int w, int h, int oldw, int oldh) {
            super.onSizeChanged(w, h, oldw, oldh);
            if (w > 0) {
                int[] colors = {
                        0xFFFF0000, // red
                        0xFFFFFF00, // yellow
                        0xFF00FF00, // green
                        0xFF00FFFF, // cyan
                        0xFF0000FF, // blue
                        0xFFFF00FF, // magenta
                        0xFFFF0000  // red (wrap)
                };
                LinearGradient hueGradient = new LinearGradient(0, 0, w, 0,
                        colors, null, Shader.TileMode.CLAMP);
                huePaint.setShader(hueGradient);
            }
        }

        @Override
        protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            int w = getWidth();
            int h = getHeight();
            RectF rect = new RectF(0, 0, w, h);
            canvas.drawRoundRect(rect, cornerRadius, cornerRadius, huePaint);

            float cx = (hue / 360f) * w;
            float cy = h / 2f;
            canvas.drawCircle(cx, cy, thumbRadius, thumbPaint);
            canvas.drawCircle(cx, cy, thumbRadius, thumbStrokePaint);
        }

        @Override
        public boolean onTouchEvent(MotionEvent event) {
            int action = event.getAction();
            if (action == MotionEvent.ACTION_DOWN || action == MotionEvent.ACTION_MOVE) {
                float x = Math.max(0, Math.min(event.getX(), getWidth()));
                hue = (x / getWidth()) * 360f;
                hue = Math.max(0f, Math.min(360f, hue));
                invalidate();
                if (hueListener != null) hueListener.onHueChanged(hue);
                return true;
            }
            return super.onTouchEvent(event);
        }

        public float getHue() { return hue; }

        public void setHue(float h) {
            this.hue = Math.max(0f, Math.min(360f, h));
            invalidate();
        }

        public void setOnHueChangedListener(OnHueChangedListener l) {
            this.hueListener = l;
        }
    }

    public interface OnColorSelectedListener {
        void onColorSelected(int color);
    }
}
