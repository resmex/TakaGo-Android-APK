package com.takago.app;

import android.content.Context;
import android.util.AttributeSet;

/**
 * Compatibility entry point for existing layout XML references.
 * The implementation lives in com.takago.app.ui.CircularProgressView.
 */
public class CircularProgressView extends com.takago.app.ui.CircularProgressView {

    public CircularProgressView(Context context) {
        super(context);
    }

    public CircularProgressView(Context context, AttributeSet attrs) {
        super(context, attrs);
    }
}
