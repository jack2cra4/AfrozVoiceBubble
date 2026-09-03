package com.afroz.voicebubble.bubble;

import android.app.Activity;
import android.app.Service;
import android.content.Intent;
import android.graphics.PixelFormat;
import android.graphics.Point;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.view.Display;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.view.animation.DecelerateInterpolator;
import android.view.animation.OvershootInterpolator;
import android.widget.Toast;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.R;
import com.afroz.voicebubble.reader.ScreenReaderAccessibilityService;
import com.afroz.voicebubble.speech.TtsEngine;

/**
 * Draggable, animated floating circle/bubble drawn via SYSTEM_ALERT_WINDOW.
 * Tapping it toggles the screen reader; dragging snaps it to the nearest edge.
 */
public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private WindowManager.LayoutParams params;
    private int screenWidth, screenHeight;
    private boolean screenReaderEnabled = false;
    private Handler handler;

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }

    @Override
    public void onCreate() {
        super.onCreate();
        handler = new Handler(Looper.getMainLooper());
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);

        computeScreenSize();

        bubbleView = LayoutInflater.from(this).inflate(R.layout.floating_bubble, null);

        params = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                        ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                        : WindowManager.LayoutParams.TYPE_PHONE,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE
                        | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                PixelFormat.TRANSLUCENT
        );
        params.gravity = Gravity.TOP | Gravity.START;
        params.x = screenWidth - dp(90);
        params.y = screenHeight / 3;

        windowManager.addView(bubbleView, params);
        startPulseAnimation();
        handleDrag();
    }

    private void computeScreenSize() {
        Display display = windowManager.getDefaultDisplay();
        Point size = new Point();
        display.getSize(size);
        screenWidth = size.x;
        screenHeight = size.y;
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private void startPulseAnimation() {
        Animation pulse = AnimationUtils.loadAnimation(this, R.anim.bubble_pulse);
        bubbleView.startAnimation(pulse);
    }

    private void handleDrag() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private float startRawX, startRawY;
            private int initialX, initialY;
            private boolean isDragging = false;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        startRawX = event.getRawX();
                        startRawY = event.getRawY();
                        initialX = params.x;
                        initialY = params.y;
                        isDragging = false;
                        return false;
                    case MotionEvent.ACTION_MOVE:
                        float dx = event.getRawX() - startRawX;
                        float dy = event.getRawY() - startRawY;
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) {
                            isDragging = true;
                        }
                        if (isDragging) {
                            params.x = initialX + (int) dx;
                            params.y = initialY + (int) dy;
                            windowManager.updateViewLayout(bubbleView, params);
                        }
                        return true;
                    case MotionEvent.ACTION_UP:
                        if (isDragging) {
                            snapToEdge();
                            return true;
                        } else {
                            bubbleView.postDelayed(() -> onBubbleTap(), 30);
                            return false;
                        }
                }
                return false;
            }
        });
    }

    private void onBubbleTap() {
        screenReaderEnabled = !screenReaderEnabled;
        TtsEngine tts = App.get().getTts();
        if (screenReaderEnabled) {
            tts.speak(getString(R.string.bubble_reader_on));
            ScreenReaderAccessibilityService.setScreenReaderEnabled(true);
            Toast.makeText(this, "स्क्रीन रीडर चालू", Toast.LENGTH_SHORT).show();
        } else {
            tts.speak(getString(R.string.bubble_reader_off));
            ScreenReaderAccessibilityService.setScreenReaderEnabled(false);
            Toast.makeText(this, "स्क्रीन रीडर बंद", Toast.LENGTH_SHORT).show();
        }
        feltScaleAnimation();
    }

    private void snapToEdge() {
        int bubbleWidth = bubbleView.getWidth() > 0 ? bubbleView.getWidth() : dp(64);
        int targetX = (params.x + bubbleWidth / 2) < screenWidth / 2 ? 0
                : screenWidth - bubbleWidth;
        params.x = targetX;
        if (params.y < 0) params.y = 0;
        if (params.y + bubbleView.getHeight() > screenHeight) {
            params.y = screenHeight - bubbleView.getHeight();
        }
        windowManager.updateViewLayout(bubbleView, params);
    }

    private void feltScaleAnimation() {
        Animation scale = AnimationUtils.loadAnimation(this, R.anim.bubble_felt);
        bubbleView.startAnimation(scale);
    }

    @Override
    public void onDestroy() {
        super.onDestroy();
        handler.removeCallbacksAndMessages(null);
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
        }
    }
}
