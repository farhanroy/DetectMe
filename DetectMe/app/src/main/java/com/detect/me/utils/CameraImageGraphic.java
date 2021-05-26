package com.detect.me.utils;

import android.graphics.Bitmap;
import android.graphics.Canvas;

import com.detect.me.camerax.PoseGraphicOverlay;

/** Draw camera image to background. */
public class CameraImageGraphic extends PoseGraphicOverlay.Graphic {

    private final Bitmap bitmap;

    public CameraImageGraphic(PoseGraphicOverlay overlay, Bitmap bitmap) {
        super(overlay);
        this.bitmap = bitmap;
    }

    @Override
    public void draw(Canvas canvas) {
        canvas.drawBitmap(bitmap, getTransformationMatrix(), null);
    }
}
