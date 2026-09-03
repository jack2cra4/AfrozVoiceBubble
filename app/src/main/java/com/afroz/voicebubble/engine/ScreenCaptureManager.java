package com.afroz.voicebubble.engine;

import android.app.Activity;
import android.content.Context;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.hardware.display.DisplayManager;
import android.hardware.display.VirtualDisplay;
import android.media.Image;
import android.media.ImageReader;
import android.media.projection.MediaProjection;
import android.media.projection.MediaProjectionManager;
import android.os.Build;
import android.util.DisplayMetrics;
import android.view.WindowManager;

/**
 * Optional screen-capture source using MediaProjection.
 *
 * Captures image frames of the current screen for local OCR. The user must
 * grant the screen-capture permission (a system dialog surfaced by the app).
 * If permission is not granted, {@link #isAvailable()} returns false and the
 * pipeline falls back to accessibility text scraping and shows a clear
 * message. All frames stay on-device; nothing is ever uploaded.
 */
public class ScreenCaptureManager {

    private final Context context;
    private MediaProjection projection;
    private ImageReader imageReader;
    private VirtualDisplay virtualDisplay;
    private int width, height, density;

    public ScreenCaptureManager(Context context) {
        this.context = context.getApplicationContext();
        WindowManager wm = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
        DisplayMetrics metrics = new DisplayMetrics();
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            context.getDisplay().getRealMetrics(metrics);
        } else if (wm != null) {
            wm.getDefaultDisplay().getRealMetrics(metrics);
        }
        width = metrics.widthPixels > 0 ? metrics.widthPixels : 1080;
        height = metrics.heightPixels > 0 ? metrics.heightPixels : 1920;
        density = metrics.densityDpi > 0 ? metrics.densityDpi : 420;
    }

    /** Build the system screen-capture permission intent (launch from Activity). */
    public static Intent buildPermissionIntent(Activity activity) {
        MediaProjectionManager mgr = (MediaProjectionManager)
                activity.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
        return mgr.createScreenCaptureIntent();
    }

    /** Complete capture setup after the user grants permission. */
    public void setProjection(int resultCode, Intent data) {
        try {
            MediaProjectionManager mgr = (MediaProjectionManager)
                    context.getSystemService(Context.MEDIA_PROJECTION_SERVICE);
            if (mgr == null) return;
            projection = mgr.getMediaProjection(resultCode, data);
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 1);
            virtualDisplay = projection.createVirtualDisplay(
                    "jarvis-capture", width, height, density,
                    DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                    imageReader.getSurface(), null, null);
        } catch (Exception e) {
            // Capture unavailable; accessibility text scraping remains active.
        }
    }

    public boolean isAvailable() {
        return projection != null && virtualDisplay != null;
    }

    /** Latest captured image frame, or null if capture is unavailable. */
    public Image acquireFrame() {
        if (!isAvailable() || imageReader == null) return null;
        try {
            return imageReader.acquireLatestImage();
        } catch (Exception e) {
            return null;
        }
    }

    public void stop() {
        if (virtualDisplay != null) {
            virtualDisplay.release();
            virtualDisplay = null;
        }
        if (imageReader != null) {
            imageReader.close();
            imageReader = null;
        }
        if (projection != null) {
            projection.stop();
            projection = null;
        }
    }
}
