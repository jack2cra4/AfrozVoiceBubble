package com.afroz.voicebubble.bubble;

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

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.R;
import com.afroz.voicebubble.chat.WakeWordListener;
import com.afroz.voicebubble.reader.ScreenReaderAccessibilityService;
import com.afroz.voicebubble.speech.TtsEngine;
import com.afroz.voicebubble.speech.TtsEngine.VoiceProfile;

/**
 * Draggable, animated glowing orb (JARVIS).
 *
 * Houses the always-on conversational loop via {@link WakeWordListener}: the
 * assistant continuously listens and, on hearing the wake word
 * ("हेलो जार्विस" / "Hello Jarvis"), instantly halts any TTS, pulses the
 * bubble, replies in the deep male voice, and keeps listening for the
 * follow-up. A voice toggle on the overlay switches between the JARVIS Male
 * core and the Assistant Female voice.
 */
public class FloatingBubbleService extends Service {

    private WindowManager windowManager;
    private View bubbleView;
    private View cardView;
    private WindowManager.LayoutParams params;
    private WindowManager.LayoutParams cardParams;
    private int screenWidth, screenHeight;
    private Handler handler;
    private WakeWordListener wakeListener;
    private boolean cardShowing = false;
    private VoiceProfile currentProfile = VoiceProfile.JARVIS_MALE;

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

        // Default deep JARVIS male profile applied up front.
        App.get().getTts().setProfile(VoiceProfile.JARVIS_MALE);
        App.get().getTts().prewarm();

        startConversationLoop();
    }

    /** Boot the always-on conversational listener. */
    private void startConversationLoop() {
        wakeListener = new WakeWordListener();
        wakeListener.setCallback(new WakeWordListener.Callback() {
            @Override
            public void onListening() {
                // Keep the loop alive; optionally reflect in the card status.
            }

            @Override
            public void onWake() {
                // Instant cue: pulse the bubble to signal the wake.
                feltScaleAnimation();
                mainWakePulse();
            }

            @Override
            public void onStatus(String status) {
                setCardStatus(status);
            }
        });
        wakeListener.start(this);
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

    private void mainWakePulse() {
        bubbleView.postDelayed(() -> {
            if (bubbleView != null) {
                Animation pulse = AnimationUtils.loadAnimation(this, R.anim.bubble_pulse);
                bubbleView.startAnimation(pulse);
            }
        }, 150);
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
                        if (Math.abs(dx) > 12 || Math.abs(dy) > 12) isDragging = true;
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
    // Wake via bubble tap: open card, single screen read.
    // -----------------------------------------------------------
    private void onBubbleTap() {
        showCard();
        App.get().getTts().speak("जी सर, बताइए", "hi");
        ScreenReaderAccessibilityService.getInstance().triggerRead();
        feltScaleAnimation();
    }

    // -----------------------------------------------------------
    // JARVIS control card overlay.
    // -----------------------------------------------------------
    private void showCard() {
        if (cardShowing) {
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
            cardView.findViewById(R.id.jarvis_listen).setOnClickListener(v -> {
                if (wakeListener != null && !wakeListener.isRunning()) {
                    wakeListener.start(this);
                }
            });
            cardView.findViewById(R.id.jarvis_read).setOnClickListener(v -> {
                App.get().getTts().speak(getString(R.string.tts_reading_screen), true);
                ScreenReaderAccessibilityService.getInstance().triggerRead();
            });
            TextView voiceBtn = cardView.findViewById(R.id.jarvis_voice);
            updateVoiceLabel(voiceBtn);
            voiceBtn.setOnClickListener(v -> toggleVoiceProfile(voiceBtn));
            cardView.findViewById(R.id.jarvis_stop).setOnClickListener(v -> {
                if (wakeListener != null) wakeListener.stop();
                App.get().getTts().stop();
                setCardStatus(getString(R.string.status_ready));
            });

            windowManager.addView(cardView, cardParams);
            cardShowing = true;
            setCardStatus(getString(R.string.status_ready));
        } catch (Exception ignored) {}
    }

    private void toggleVoiceProfile(TextView voiceBtn) {
        currentProfile = (currentProfile == VoiceProfile.JARVIS_MALE)
                ? VoiceProfile.ASSISTANT_FEMALE
                : VoiceProfile.JARVIS_MALE;
        App.get().getTts().setProfile(currentProfile);
        updateVoiceLabel(voiceBtn);
        String tag = currentProfile == VoiceProfile.JARVIS_MALE ? "hi" : "hi";
        App.get().getTts().speak(
                currentProfile == VoiceProfile.JARVIS_MALE
                        ? "जार्विस मेल वॉइस चालू"
                        : "असिस्टेंट फीमेल वॉइस चालू",
                tag);
    }

    private void updateVoiceLabel(TextView voiceBtn) {
        if (voiceBtn != null) {
            boolean male = (currentProfile == VoiceProfile.JARVIS_MALE);
            voiceBtn.setText(male ? getString(R.string.voice_male)
                                  : getString(R.string.voice_female));
        }
    }

    private void hideCard() {
        if (cardView != null && cardShowing) {
            try {
                windowManager.removeView(cardView);
            } catch (Exception ignored) {}
            cardView = null;
            cardShowing = false;
        }
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
        if (wakeListener != null) {
            wakeListener.destroy();
            wakeListener = null;
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
