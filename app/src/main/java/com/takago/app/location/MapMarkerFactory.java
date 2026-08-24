package com.takago.app.location;

import com.takago.app.R;

import android.content.Context;
import android.graphics.Bitmap;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.BitmapDrawable;
import android.graphics.drawable.Drawable;

import androidx.core.content.ContextCompat;
import androidx.core.graphics.drawable.DrawableCompat;

/** Shared, tinted marker icons so the driver/resident live-tracking maps look consistent. */
public class MapMarkerFactory {

    private MapMarkerFactory() {
    }

    /** Green destination pin for the final pickup location. */
    public static Drawable pickupPin(Context context) {
        Drawable drawable = ContextCompat.getDrawable(context, R.drawable.ic_location_pin);
        if (drawable == null) return null;
        Drawable wrapped = DrawableCompat.wrap(drawable.mutate());
        DrawableCompat.setTint(wrapped, 0xFF2E7D32);
        return wrapped;
    }

    /** Strong black bin marker for the resident's requested waste pickup point. */
    public static Drawable residentWaste(Context context) {
        return sizedTinted(context, R.drawable.ic_waste_small, 38, 0xFF151515);
    }

    /** Green truck marker for the assigned driver's live position. */
    public static Drawable driverTruck(Context context) {
        return sizedTinted(context, R.drawable.ic_truck_outline, 42, 0xFF1FAF5A);
    }

    private static Drawable sizedTinted(Context context, int resource, int sizeDp, int color) {
        Drawable source = ContextCompat.getDrawable(context, resource);
        if (source == null) return null;
        Drawable wrapped = DrawableCompat.wrap(source.mutate());
        DrawableCompat.setTint(wrapped, color);
        int size = Math.round(sizeDp * context.getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        wrapped.setBounds(0, 0, size, size);
        wrapped.draw(canvas);
        return new BitmapDrawable(context.getResources(), bitmap);
    }

    public static Drawable intermediateStop(Context context, int number, boolean completed) {
        return numberedStop(context, number, completed, false, false);
    }

    public static Drawable finalStop(Context context, int number, boolean completed) {
        return completed ? numberedStop(context, number, true, false, true) : pickupPin(context);
    }

    public static Drawable numberedStop(Context context, int number, boolean completed, boolean current) {
        return numberedStop(context, number, completed, current, false);
    }

    private static Drawable numberedStop(Context context, int number, boolean completed,
                                        boolean current, boolean finalStop) {
        int size = Math.round((current ? 42 : 34) * context.getResources().getDisplayMetrics().density);
        Bitmap bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.ARGB_8888);
        Canvas canvas = new Canvas(bitmap);
        Paint paint = new Paint(Paint.ANTI_ALIAS_FLAG);
        paint.setColor(completed || finalStop ? 0xFF2E7D32 : 0xFFE53935);
        canvas.drawCircle(size / 2f, size / 2f, size / 2f - 2, paint);

        paint.setColor(0xFFFFFFFF);
        paint.setTextAlign(Paint.Align.CENTER);
        paint.setTypeface(android.graphics.Typeface.DEFAULT_BOLD);
        paint.setTextSize(size * 0.46f);
        String label = completed ? "✓" : String.valueOf(number);
        Paint.FontMetrics fm = paint.getFontMetrics();
        canvas.drawText(label, size / 2f, size / 2f - (fm.ascent + fm.descent) / 2f, paint);
        return new BitmapDrawable(context.getResources(), bitmap);
    }
}
