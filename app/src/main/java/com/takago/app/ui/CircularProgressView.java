package com.takago.app.ui;

import android.content.Context;
import android.graphics.Canvas;
import android.graphics.Color;
import android.graphics.Paint;
import android.graphics.RectF;
import android.util.AttributeSet;
import android.view.View;

/** Simple ring progress indicator used for percentage stats (e.g. "76% SLA"). */
public class CircularProgressView extends View {

    private final Paint trackPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final Paint progressPaint = new Paint(Paint.ANTI_ALIAS_FLAG);
    private final RectF arcRect = new RectF();
    private int progress = 0;

    public CircularProgressView(Context context) {
        super(context);
        init();
    }

    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
        init();
    }

    private void init() {
        float strokeWidth = getResources().getDisplayMetrics().density * 6;

        trackPaint.setStyle(Paint.Style.STROKE);
        trackPaint.setStrokeWidth(strokeWidth);
        trackPaint.setStrokeCap(Paint.Cap.ROUND);
        trackPaint.setColor(Color.parseColor("#33FFFFFF"));

        progressPaint.setStyle(Paint.Style.STROKE);
        progressPaint.setStrokeWidth(strokeWidth);
        progressPaint.setStrokeCap(Paint.Cap.ROUND);
        progressPaint.setColor(Color.WHITE);
    }

    public void setProgress(int percent) {
        this.progress = percent;
        invalidate();
    }

    public void setColors(int trackColor, int progressColor) {
        trackPaint.setColor(trackColor);
        progressPaint.setColor(progressColor);
        invalidate();
    }

    @Override
    protected void onDraw(Canvas canvas) {
        super.onDraw(canvas);
        float strokeWidth = trackPaint.getStrokeWidth();
        arcRect.set(strokeWidth / 2f, strokeWidth / 2f,
                getWidth() - strokeWidth / 2f, getHeight() - strokeWidth / 2f);

        canvas.drawArc(arcRect, 0, 360, false, trackPaint);
        float sweepAngle = 360f * progress / 100f;
        canvas.drawArc(arcRect, -90, sweepAngle, false, progressPaint);
    }
}
