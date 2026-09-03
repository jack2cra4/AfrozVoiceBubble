package com.afroz.voicebubble;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.view.View;
import android.view.animation.Animation;
import android.view.animation.AnimationUtils;
import android.widget.Button;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import com.afroz.voicebubble.bubble.FloatingBubbleService;
import com.afroz.voicebubble.engine.JarvisStateManager;
import com.afroz.voicebubble.engine.LocalMemory;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

/**
 * JARVIS — futuristic home dashboard.
 *
 * Shows the animated assistant core, a time/language-aware greeting, the live
 * status word, a START LIVE control, and the touch-friendly dashboard cards
 * that navigate to the Screen/Voice/Tasks/Agents/APIs/Memory/Settings screens.
 * Status text is driven by the assistant state machine so it reflects reality
 * and never fakes a state.
 */
public class MainActivity extends AppCompatActivity {

    private static final int REQ_RECORD_AUDIO = 100;

    private TextView liveStatus;
    private TextView watchMsg;
    private Button btnStartLive;
    private TextView scanEffect;
    private boolean live = false;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);

        liveStatus = findViewById(R.id.live_status);
        watchMsg = findViewById(R.id.watch_msg);
        btnStartLive = findViewById(R.id.btn_start_live);
        scanEffect = findViewById(R.id.scan_effect);

        startAnimations();
        setGreeting();

        // Live status reflects the actual state machine.
        App.get().getStateManager().setListener((prev, cur) ->
                runOnUiThread(() -> updateStatus(cur)));

        btnStartLive.setOnClickListener(v -> toggleLive());

        // Dashboard cards.
        findViewById(R.id.card_screen).setOnClickListener(v -> startLiveScreen());
        findViewById(R.id.card_voice).setOnClickListener(v -> App.get().getVoiceManager().test(
                com.afroz.voicebubble.speech.TtsEngine.detectLang("")));
        findViewById(R.id.card_tasks).setOnClickListener(v -> open(TasksActivity.class));
        findViewById(R.id.card_agents).setOnClickListener(v -> open(AgentsActivity.class));
        findViewById(R.id.card_apis).setOnClickListener(v -> open(ApisActivity.class));
        findViewById(R.id.card_memory).setOnClickListener(v -> open(MemoryActivity.class));
        findViewById(R.id.card_settings).setOnClickListener(v -> open(SettingsActivity.class));

        findViewById(R.id.btn_stop_bubble).setOnClickListener(v -> stopService(
                new Intent(this, FloatingBubbleService.class)));
        findViewById(R.id.btn_open_accessibility).setOnClickListener(v -> openAccessibilitySettings());

        updateStatus(App.get().getStateManager().getState());
    }

    private void startAnimations() {
        findViewById(R.id.core).startAnimation(AnimationUtils.loadAnimation(this, R.anim.core_fade));
        findViewById(R.id.ring).startAnimation(AnimationUtils.loadAnimation(this, R.anim.ring_spin));
        scanEffect.setVisibility(View.GONE);
    }

    private void setGreeting() {
        int hour = Calendar.getInstance().get(Calendar.HOUR_OF_DAY);
        String name = App.get().getMemory().getUserName();
        boolean hi = "hi".equals(resolveLang());
        String g;
        if (hi) {
            g = hour < 12 ? getString(R.string.dash_greeting_su_morning, name)
                 : hour < 16 ? getString(R.string.dash_greeting_su_afternoon, name)
                 : hour < 21 ? getString(R.string.dash_greeting_su_evening, name)
                 : getString(R.string.dash_greeting_su_night, name);
        } else {
            g = hour < 12 ? getString(R.string.dash_greeting_morning, name)
                 : hour < 16 ? getString(R.string.dash_greeting_afternoon, name)
                 : hour < 21 ? getString(R.string.dash_greeting_evening, name)
                 : getString(R.string.dash_greeting_night, name);
        }
        TextView greeting = findViewById(R.id.greeting);
        greeting.setText(g);
    }

    private String resolveLang() {
        String mode = App.get().getSettingsManager().getLanguageMode();
        if ("hi".equals(mode)) return "hi";
        if ("en".equals(mode)) return "en";
        // Auto: prefer Hindi if the last used language was Hindi.
        String last = App.get().getConversation().getLastLanguage();
        return "hi".equals(last) ? "hi" : "en";
    }

    private void toggleLive() {
        if (!live) {
            ensureRecordAudioPermission();
            if (!canDrawOverlays()) {
                promptOverlayPermission();
                return;
            }
            startService(new Intent(this, FloatingBubbleService.class));
            App.get().getLiveMode().start();
            live = true;
            btnStartLive.setText(getString(R.string.dash_stop_live));
            watchMsg.setVisibility(View.VISIBLE);
            scanEffect.setVisibility(View.VISIBLE);
            scanEffect.startAnimation(AnimationUtils.loadAnimation(this, R.anim.scan_move));
            Toast.makeText(this, "JARVIS live", Toast.LENGTH_SHORT).show();
        } else {
            App.get().getLiveMode().stop();
            stopService(new Intent(this, FloatingBubbleService.class));
            live = false;
            btnStartLive.setText(getString(R.string.dash_start_live));
            watchMsg.setVisibility(View.GONE);
            scanEffect.setVisibility(View.GONE);
            scanEffect.clearAnimation();
            Toast.makeText(this, "JARVIS stopped", Toast.LENGTH_SHORT).show();
        }
        updateStatus(App.get().getStateManager().getState());
    }

    private void startLiveScreen() {
        if (!live) toggleLive();
        openAccessibilitySettings();
    }

    private void open(Class<?> cls) {
        startActivity(new Intent(this, cls));
    }

    private void updateStatus(JarvisStateManager.State s) {
        if (liveStatus == null) return;
        if (!live) {
            liveStatus.setText(getString(R.string.dash_status_offline));
            liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_red));
            return;
        }
        switch (s) {
            case LISTENING:
                liveStatus.setText(getString(R.string.dash_status_listening));
                liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan));
                break;
            case PROCESSING:
                liveStatus.setText(getString(R.string.dash_status_thinking));
                liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_gold));
                break;
            case SPEAKING:
                liveStatus.setText(getString(R.string.dash_status_speaking));
                liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
                break;
            case LIVE_SCREEN:
                liveStatus.setText(getString(R.string.dash_status_watching));
                liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_green));
                break;
            default:
                liveStatus.setText(getString(R.string.dash_status_online));
                liveStatus.setTextColor(ContextCompat.getColor(this, R.color.accent_cyan));
        }
    }

    // ---- permissions -------------------------------------------------------

    private boolean canDrawOverlays() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            return Settings.canDrawOverlays(this);
        }
        return true;
    }

    private void promptOverlayPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            startActivity(new Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:" + getPackageName())));
            Toast.makeText(this, "Please allow 'Display over other apps'", Toast.LENGTH_LONG).show();
        }
    }

    private void ensureRecordAudioPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECORD_AUDIO)
                    != PackageManager.PERMISSION_GRANTED) {
                ActivityCompat.requestPermissions(this,
                        new String[]{Manifest.permission.RECORD_AUDIO}, REQ_RECORD_AUDIO);
            }
        }
    }

    private void openAccessibilitySettings() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        } else {
            startActivity(new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS));
        }
        Toast.makeText(this, "Enable AfrozVoiceBubble", Toast.LENGTH_LONG).show();
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions,
                                           @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == REQ_RECORD_AUDIO && grantResults.length > 0
                && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
            Toast.makeText(this, "Microphone permission granted", Toast.LENGTH_SHORT).show();
        }
    }
}
