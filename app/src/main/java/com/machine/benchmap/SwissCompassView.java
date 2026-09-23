package com.machine.benchmap;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.Path;
import android.util.AttributeSet;
import android.view.View;

import androidx.annotation.Nullable;

/**
 * Swiss Minimalist Compass Navigation View.
 * Displays a two-tone True North needle (Swiss Red & Slate) that dynamically
 * tracks map rotation and allows one-tap reset to North.
 */
public class SwissCompassView extends View {

    private float compassRotation = 0f;
    private boolean isDarkMode = false;

    private final Paint paintNorthNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintSouthNeedle = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDialBg = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintDialStroke = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPivotRing = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint paintPivotDot = new Paint(Paint.ANTI_ALIAS_FLAG);

    private final Path northNeedlePath = new Path();
    private final Path southNeedlePath = new Path();

    public SwissCompassView(Context context) {
        super(context);
        init();
    }

    public SwissCompassView(Context context, @Nullable AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    public SwissCompassView(Context context, @Nullable AttributeSet attrs, int defStyleAttr) {
        super(context, attrs, defStyleAttr);
        init();
    }

    private void init() {
        paintNorthNeedle.setStyle(Paint.Style.FILL);
        paintSouthNeedle.setStyle(Paint.Style.FILL);
        paintDialBg.setStyle(Paint.Style.FILL);
        paintDialStroke.setStyle(Paint.Style.STROKE);
        paintPivotRing.setStyle(Paint.Style.FILL);
        paintPivotDot.setStyle(Paint.Style.FILL);

        float density = getResources().getDisplayMetrics().density;
        paintDialStroke.setStrokeWidth(1.0f * density);

        updateThemeColors();
    }

    public void setDarkMode(boolean darkMode) {
        if (this.isDarkMode != darkMode) {
            this.isDarkMode = darkMode;
            updateThemeColors();
            invalidate();
        }
    }

    private void updateThemeColors() {
        if (isDarkMode) {
            paintNorthNeedle.setColor(Color.parseColor("#FF383C")); // Vibrant Swiss Coral Red
            paintSouthNeedle.setColor(Color.parseColor("#64748B")); // Nocturne Slate
            paintDialBg.setColor(Color.parseColor("#20232B"));      // Dark Chip/Card Bg
            paintDialStroke.setColor(Color.parseColor("#2C303B"));  // Subtle Border
            paintPivotRing.setColor(Color.parseColor("#16171B"));   // Base Obsidian
            paintPivotDot.setColor(Color.parseColor("#FF383C"));
        } else {
            paintNorthNeedle.setColor(Color.parseColor("#E52B35")); // Classic Swiss Red
            paintSouthNeedle.setColor(Color.parseColor("#94A3B8")); // Classic Slate
            paintDialBg.setColor(Color.parseColor("#F2F4F7"));      // Light Chip Bg
            paintDialStroke.setColor(Color.parseColor("#E4E7EC"));  // Subtle Border
            paintPivotRing.setColor(Color.parseColor("#FFFFFF"));   // White Ring
            paintPivotDot.setColor(Color.parseColor("#E52B35"));
        }
    }

    public void setCompassRotation(float degrees) {
        float norm = (degrees % 360f + 360f) % 360f;
        if (Math.abs(norm - this.compassRotation) > 0.05f) {
            this.compassRotation = norm;
            invalidate();
        }
    }

    public float getCompassRotation() {
        return compassRotation;
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);

        int w = getWidth();
        int h = getHeight();
        if (w <= 0 || h <= 0) return;

        float cx = w / 2f;
        float cy = h / 2f;
        float padL = getPaddingLeft();
        float padR = getPaddingRight();
        float padT = getPaddingTop();
        float padB = getPaddingBottom();

        float availW = w - padL - padR;
        float availH = h - padT - padB;
        float radius = Math.min(availW, availH) / 2f;
        if (radius <= 0) return;

        // Dial background & subtle border
        canvas.drawCircle(cx, cy, radius, paintDialBg);
        canvas.drawCircle(cx, cy, radius, paintDialStroke);

        // Rotating compass needle
        canvas.save();
        canvas.rotate(compassRotation, cx, cy);

        float needleLen = radius * 0.70f;
        float needleW = radius * 0.28f;

        // North needle (Swiss Red pointing Up)
        northNeedlePath.reset();
        northNeedlePath.moveTo(cx, cy - needleLen);
        northNeedlePath.lineTo(cx - needleW, cy);
        northNeedlePath.lineTo(cx + needleW, cy);
        northNeedlePath.close();
        canvas.drawPath(northNeedlePath, paintNorthNeedle);

        // South needle (Slate pointing Down)
        southNeedlePath.reset();
        southNeedlePath.moveTo(cx, cy + needleLen);
        southNeedlePath.lineTo(cx - needleW, cy);
        southNeedlePath.lineTo(cx + needleW, cy);
        southNeedlePath.close();
        canvas.drawPath(southNeedlePath, paintSouthNeedle);

        // Center pivot dot (clean double dot)
        canvas.drawCircle(cx, cy, radius * 0.22f, paintPivotRing);
        canvas.drawCircle(cx, cy, radius * 0.12f, paintPivotDot);

        canvas.restore();
    }
}
