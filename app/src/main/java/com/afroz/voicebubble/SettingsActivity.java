package com.afroz.voicebubble;

import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.CompoundButton;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.Switch;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.afroz.voicebubble.engine.SettingsManager;

/**
 * Complete SETTINGS center.
 *
 * Sections: GENERAL, VOICE, LANGUAGE, LIVE SCREEN, SUBTITLES, PRIVACY,
 * PERFORMANCE. Every control writes through {@link SettingsManager} which
 * persists locally and is respected by the live pipeline. No fake state.
 */
public class SettingsActivity extends AppCompatActivity {

    private SettingsManager s;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        s = App.get().getSettingsManager();

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        addHeader();
        generalSection();
        voiceSection();
        languageSection();
        liveScreenSection();
        subtitlesSection();
        privacySection();
        performanceSection();
    }

    private void addHeader() {
        Button back = new Button(this);
        back.setBackgroundResource(R.drawable.btn_ghost_bg);
        back.setText(R.string.back);
        back.setTextColor(getColorCompat(R.color.text_light));
        back.setOnClickListener(v -> finish());
        root.addView(back, lp());
        spacer();
    }

    private void generalSection() {
        section(R.string.sec_general);
        rowSwitch(R.string.label_auto_start, s.isAutoStart(), v -> s.setAutoStart(v));
        rowSwitch(R.string.label_wake_word, s.isWakeWordEnabled(), v -> s.setWakeWordEnabled(v));
        rowSwitch(R.string.label_proactive, s.isProactive(), v -> s.setProactive(v));
    }

    private void voiceSection() {
        section(R.string.sec_voice);
        rowSwitch(R.string.voice_male, s.isMaleVoice(), v -> {
            s.setMaleVoice(v);
            App.get().getVoiceManager().apply();
        });
        Button test = ghostButton(R.string.btn_voice_test);
        test.setOnClickListener(v ->
                App.get().getVoiceManager().test(App.get().getConversation().resolveLang()));
        root.addView(test, lp());
        spacer();
    }

    private void languageSection() {
        section(R.string.sec_language);
        rowChoice(R.string.lang_hindi, "hi".equals(s.getLanguageMode()),
                () -> s.setLanguageMode("hi"));
        rowChoice(R.string.lang_english, "en".equals(s.getLanguageMode()),
                () -> s.setLanguageMode("en"));
        rowChoice(R.string.lang_auto, "auto".equals(s.getLanguageMode()),
                () -> s.setLanguageMode("auto"));
        spacer();
    }

    private void liveScreenSection() {
        section(R.string.sec_live_screen);
        rowSwitch(R.string.label_screen_monitor, s.isScreenMonitoring(),
                v -> s.setScreenMonitoring(v));
        rowSwitch(R.string.label_performance, s.isPerformanceMode(),
                v -> s.setPerformanceMode(v));
        spacer();
    }

    private void subtitlesSection() {
        section(R.string.sec_subtitles);
        rowChoice(R.string.sub_off, "off".equals(s.getSubtitleMode()),
                () -> s.setSubtitleMode("off"));
        rowChoice(R.string.sub_only, "subtitles_only".equals(s.getSubtitleMode()),
                () -> s.setSubtitleMode("subtitles_only"));
        rowChoice(R.string.sub_important, "important".equals(s.getSubtitleMode()),
                () -> s.setSubtitleMode("important"));
        rowChoice(R.string.sub_full, "full".equals(s.getSubtitleMode()),
                () -> s.setSubtitleMode("full"));
        rowSwitch(R.string.label_translate_subs, s.isTranslateSubtitles(),
                v -> s.setTranslateSubtitles(v));
        rowSwitch(R.string.label_speak_translation, s.isSpeakTranslation(),
                v -> s.setSpeakTranslation(v));
        spacer();
    }

    private void privacySection() {
        section(R.string.sec_privacy);
        rowSwitch(R.string.label_local_only, s.isLocalOnly(), v -> {
            s.setLocalOnly(v);
            Toast.makeText(this, v ? "Local only — no cloud calls."
                                   : "Cloud AI allowed when keys are set.",
                    Toast.LENGTH_SHORT).show();
        });
        Button clearConv = ghostButton(R.string.btn_clear_conversation);
        clearConv.setOnClickListener(v -> {
            App.get().getMemoryManager().clearConversation();
            Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearConv, lp());

        Button clearHist = ghostButton(R.string.btn_clear_history);
        clearHist.setOnClickListener(v -> {
            App.get().getMemoryManager().clearMemory();
            Toast.makeText(this, "History cleared", Toast.LENGTH_SHORT).show();
        });
        root.addView(clearHist, lp());

        Button reset = ghostButton(R.string.btn_reset_jarvis);
        reset.setOnClickListener(v -> {
            s.resetJarvisPrefs(this);
            Toast.makeText(this, "JARVIS reset", Toast.LENGTH_SHORT).show();
        });
        root.addView(reset, lp());
        spacer();
    }

    private void performanceSection() {
        section(R.string.sec_performance);
        rowSwitch(R.string.label_performance, s.isPerformanceMode(),
                v -> s.setPerformanceMode(v));
        spacer();
    }

    // ---- UI helpers --------------------------------------------------------

    private void section(int resId) {
        TextView t = new TextView(this);
        t.setText(resId);
        t.setTextColor(getColorCompat(R.color.accent_cyan));
        t.setTextSize(14);
        t.setTypeface(Typeface.DEFAULT_BOLD);
        t.setLetterSpacing(0.12f);
        t.setPadding(0, dp(16), 0, dp(6));
        root.addView(t, lp());
    }

    private void rowSwitch(final int titleRes, boolean initial,
                           final SwitchListener onToggle) {
        TextView label = new TextView(this);
        label.setText(titleRes);
        label.setTextColor(getColorCompat(R.color.text_light));
        label.setTextSize(15);

        Switch sw = new Switch(this);
        sw.setChecked(initial);
        sw.setOnCheckedChangeListener((buttonView, isChecked) -> onToggle.toggle(isChecked));

        LinearLayout row = new LinearLayout(this);
        row.setOrientation(LinearLayout.HORIZONTAL);
        row.setGravity(Gravity.CENTER_VERTICAL);
        row.setPadding(dp(4), dp(6), dp(4), dp(6));
        row.addView(label, new LinearLayout.LayoutParams(0, dp(44), 1));
        row.addView(sw, new LinearLayout.LayoutParams(dp(110), dp(44)));

        root.addView(row, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT));
    }

    private void rowChoice(final int titleRes, boolean selected, final Runnable onSelect) {
        TextView t = new TextView(this);
        t.setText(titleRes);
        t.setTextColor(getColorCompat(selected ? R.color.accent_cyan : R.color.text_light));
        t.setTextSize(15);
        t.setBackgroundResource(R.drawable.card_bg);
        t.setPadding(dp(14), dp(14), dp(14), dp(14));
        t.setGravity(Gravity.CENTER_VERTICAL);
        t.setOnClickListener(v -> {
            onSelect.run();
            Toast.makeText(this, titleRes, Toast.LENGTH_SHORT).show();
        });
        root.addView(t, lp());
        spacer(6);
    }

    private Button ghostButton(int resId) {
        Button b = new Button(this);
        b.setBackgroundResource(R.drawable.btn_ghost_bg);
        b.setText(resId);
        b.setTextColor(getColorCompat(R.color.text_light));
        return b;
    }

    private void spacer() { spacer(12); }

    private void spacer(int h) {
        View v = new View(this);
        root.addView(v, new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(h)));
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int getColorCompat(int resId) {
        // minSdk 21: use getResources().getColor
        return getResources().getColor(resId);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }

    private interface SwitchListener {
        void toggle(boolean checked);
    }
}
