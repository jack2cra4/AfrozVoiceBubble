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
import android.widget.TextView;
import android.widget.Toast;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.R;
import com.afroz.voicebubble.reader.ScreenReaderAccessibilityService;
import com.afroz.voicebubble.speech.SttEngine;
import com.afroz.voicebubble.speech.TtsEngine;

import java.util.Locale;

/**
 * Draggable, animated glowing orb (JARVIS). Dragging snaps it to the nearest
 * edge. Tapping it or saying the "Open JARVIS" wake word instantly expands the
 * JARVIS control card and reads the screen on explicit trigger only — there is
 * no random unprompted speech.
 */
public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private View cardView;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams cardParams;
    private int screenWidth, screenHeight;
    private Handler handler;
    private SttEngine sttEngine;
    private boolean cardShowing = false;

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

        // Pre-warm the assistant so the first command is instant.
        App.get().getTts().prewarm();
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
                            repositionCard();
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

    // -----------------------------------------------------------
    // Wake trigger: bubble tap instantly opens JARVIS + reads screen.
    // -----------------------------------------------------------
    private void onBubbleTap() {
        TtsEngine tts = App.get().getTts();
        showCard();
        // Explicit bubble-tap trigger — allowed speech, instant read.
        tts.speak(getString(R.string.jarvis_active), true);
        ScreenReaderAccessibilityService.getInstance().triggerRead();
        feltScaleAnimation();
    }

    // -----------------------------------------------------------
    // JARVIS control card overlay.
    // -----------------------------------------------------------
    private void showCard() {
        if (cardShowing) {
            // Retrigger read if already open.
            ScreenReaderAccessibilityService.getInstance().triggerRead();
            return;
        }
        try {
            cardView = LayoutInflater.from(this).inflate(R.layout.jarvis_card, null);

            cardParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            cardParams.gravity = Gravity.TOP | Gravity.START;
            cardParams.x = params.x - dp(20);
            cardParams.y = params.y + dp(80);
            if (cardParams.y < 0) cardParams.y = 0;

            cardView.findViewById(R.id.jarvis_close).setOnClickListener(v -> hideCard());
            cardView.findViewById(R.id.jarvis_listen).setOnClickListener(v -> startVoiceWake());
            cardView.findViewById(R.id.jarvis_read).setOnClickListener(v -> {
                App.get().getTts().speak(getString(R.string.tts_reading_screen), true);
                ScreenReaderAccessibilityService.getInstance().triggerRead();
            });
            cardView.findViewById(R.id.jarvis_stop).setOnClickListener(v -> {
                ScreenReaderAccessibilityService.getInstance().stopReading();
                setCardStatus(getString(R.string.status_ready));
            });

            windowManager.addView(cardView, cardParams);
            cardShowing = true;
            setCardStatus(getString(R.string.status_ready));
        } catch (Exception ignored) {}
    }

    private void hideCard() {
        if (cardView != null && cardShowing) {
            try {
                windowManager.removeView(cardView);
            } catch (Exception ignored) {}
            cardView = null;
            cardShowing = false;
        }
        stopVoiceWake();
    }

    private void repositionCard() {
        if (cardShowing && cardView != null) {
            cardParams.x = params.x - dp(20);
            cardParams.y = params.y + dp(80);
            if (cardParams.y < 0) cardParams.y = 0;
            try {
                windowManager.updateViewLayout(cardView, cardParams);
            } catch (Exception ignored) {}
        }
    }

    private void setCardStatus(String status) {
        if (cardView != null) {
            TextView statusText = cardView.findViewById(R.id.jarvis_status);
            if (statusText != null) statusText.setText(status);
        }
    }

    // -----------------------------------------------------------
    // Voice wake: "Open JARVIS", "Jarvis", "Read Screen", "Stop".
    // -----------------------------------------------------------
    private void startVoiceWake() {
        if (sttEngine == null) {
            sttEngine = new SttEngine(this);
        }
        if (!sttEngine.isAvailable()) {
            Toast.makeText(this, "Voice not available", Toast.LENGTH_SHORT).show();
            return;
        }
        setCardStatus("Listening...");
        sttEngine.setListener(new SttEngine.Listener() {
            @Override
            public void onResult(String text) {
                handleWakeCommand(text);
            }

            @Override
            public void onError(String message) {
                setCardStatus(getString(R.string.status_ready));
            }
        });
        sttEngine.startListening("en-US");
    }

    private void stopVoiceWake() {
        if (sttEngine != null) {
            sttEngine.stopListening();
            sttEngine.setListener(null);
        }
    }

    private void handleWakeCommand(String text) {
        setCardStatus(getString(R.string.status_ready));
        if (text == null) return;
        String lower = text.toLowerCase(Locale.US).trim();

        if (lower.contains("open jarvis") || lower.contains("jarvis")) {
            if (!cardShowing) showCard();
            App.get().getTts().speak(getString(R.string.jarvis_active), true);
            ScreenReaderAccessibilityService.getInstance().triggerRead();
        } else if (lower.contains("read screen") || lower.contains("read")) {
            App.get().getTts().speak(getString(R.string.tts_reading_screen), true);
            ScreenReaderAccessibilityService.getInstance().triggerRead();
        } else if (lower.contains("stop")) {
            App.get().getTts().speak(getString(R.string.btn_stop), true);
            ScreenReaderAccessibilityService.getInstance().stopReading();
        }
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
        stopVoiceWake();
        if (sttEngine != null) {
            sttEngine.destroy();
            sttEngine = null;
        }
        if (cardView != null && windowManager != null) {
            try {
                windowManager.removeView(cardView);
            } catch (Exception ignored) {}
            cardView = null;
        }
        if (bubbleView != null && windowManager != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception ignored) {}
        }
    }
}
