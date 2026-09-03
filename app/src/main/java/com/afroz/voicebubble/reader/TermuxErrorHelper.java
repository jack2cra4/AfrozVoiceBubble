package com.afroz.voicebubble.reader;

import android.content.Context;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.Looper;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.TextView;

import com.afroz.voicebubble.App;
import com.afroz.voicebubble.R;
import com.afroz.voicebubble.speech.TtsEngine;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects common Termux/Linux terminal errors on screen and shows + speaks the
 * fix in clear Hindi. Runs entirely offline.
 */
public class TermuxErrorHelper {

    private final Context context;
    private final WindowManager windowManager;
    private View panelView;
    private boolean panelShowing = false;
    private String lastError = "";
    private final Handler handler = new Handler(Looper.getMainLooper());

    private static final List<Pattern> ERROR_PATTERNS = new ArrayList<>();
    private static final List<String> ERROR_FIXES = new ArrayList<>();

    static {
        register(
            "E: Unable to locate package",
            "पैकेज नहीं मिला। पहले apt update चलाएं, फिर apt install पैकेज_नाम करें। यदि नहीं होगा तो pkg update आज़माएँ।");
        register(
            "Permission denied",
            "अनुमति नहीं मिली। chmod 755 फ़ाइल का नाम चलाएं। यदि root चाहिए तो su का उपयोग करें।");
        register(
            "No space left on device",
            "डिस्क में जगह नहीं है। df -h देखें फिर apt autoremove करें या पुरानी फ़ाइलें हटाएं।");
        register(
            "Connection refused",
            "कनेक्शन अस्वीकृत। इंटरनेट जाँचें। VPN चालू है तो बंद करके फिर कोशिश करें।");
        register(
            "Could not resolve host",
            "DNS हल नहीं हो रहा। termux-change-repo चलाएँ या अन्य नेटवर्क से जुड़ें।");
        register(
            "command not found",
            "कमांड इंस्टॉल नहीं है। pkg install कमांड का नाम चलाकर इंस्टॉल करें। जैसे: pkg install git");
        register(
            "No such file or directory",
            "फ़ाइल नहीं मिली। pwd और ls से सही रास्ता देखें, फिर सही path डालें।");
        register(
            "Segmentation fault",
            "सेगमेंटेशन फॉल्ट हुआ। यह मेमोरी समस्या है। कोड में pointer और array की सीमा जाँचें।");
        register(
            "fatal:",
            "घातक त्रुटि। Git में यह अक्सर authentication या network समस्या से आती है।");
        register(
            "error:",
            "त्रुटि मिली। संदेश ध्यान से पढ़ें, यह बताता है कि क्या गलत है।");
        register(
            "npm ERR!",
            "npm त्रुटि। npm cache clean --force चलाएं, फिर npm install। node_modules हटाकर कोशिश करें।");
        register(
            "pip: error:",
            "pip त्रुटि। pip install --upgrade pip चलाएं, फिर दोबारा install करें।");
        register(
            "ModuleNotFoundError",
            "पाइथन मॉड्यूल नहीं मिला। pip install मॉड्यूल_नाम चलाएं।");
        register(
            "ImportError",
            "इम्पोर्ट त्रुटि। pip install --force-reinstall मॉड्यूल_नाम आज़माएँ।");
        register(
            "OSError:",
            "ऑपरेटिंग सिस्टम त्रुटि। फ़ाइल अनुमति और रास्ता जाँचें।");
        register(
            "command not found: pkg",
            "यह Termux नहीं है या Termux सही इंस्टॉल नहीं है। Play Store से Termux इंस्टॉल करें।");
        register(
            "Fontconfig warning",
            "यह सिर्फ़ चेतावनी है, इसे अनदेखा कर सकते हैं। कोई असर नहीं होगा।");
        register(
            "denied by selinux",
            "SELinux ने अनुमति नहीं दी। यह एंड्रॉयड सुरक्षा नीति है, root access चाहिए हो सकता है।");
        register(
            "Killed",
            "प्रक्रिया खत्म हुई, आमतौर पर कम मेमोरी से। अन्य ऐप बंद करें या swap बढ़ाएं।");
        register(
            "cannot allocate memory",
            "मेमोरी नहीं मिली। अन्य ऐप बंद करें या swap बनाएं।");
        register(
            "disk quota exceeded",
            "डिस्क कोटा भर गया। अनावश्यक फ़ाइलें हटाएं।");
        register(
            "bus error",
            "मेमोरी एलाइनमेंट समस्या। कोड में pointer उपयोग जाँचें।");
        register(
            "not a git command",
            "git कमांड इंस्टॉल नहीं। pkg install git चलाएं।");
        register(
            "ssh: connect to host",
            "ssh कनेक्शन विफल। इंटरनेट, की और पोर्ट 22 जाँचें।");
        register(
            "refusing to merge unrelated histories",
            "git merge विफल। git merge --allow-unrelated-histories चलाएं।");
        register(
            "remote origin already exists",
            "remote पहले से है। git remote set-url origin URL चलाएं।");
        register(
            "java.lang.OutOfMemoryError",
            "जावा मेमोरी खत्म। -Xmx बढ़ाएं, जैसे java -Xmx512m");
        register(
            "E: Failed to fetch",
            "डाउनलोड विफल। termux-change-repo से मिरर बदलें और apt update करें।");
        register(
            "node:internal/modules/cjs/loader",
            "node.js लोडर त्रुटि। node_modules हटाकर npm install दोबारा करें।");
        register(
            "Hash Sum mismatch",
            "पैकेज हैश मेल नहीं। apt clean करें और apt update फिर चलाएं।");
    }

    private static void register(String pattern, String fix) {
        ERROR_PATTERNS.add(Pattern.compile(Pattern.quote(pattern), Pattern.CASE_INSENSITIVE));
        ERROR_FIXES.add(fix);
    }

    public TermuxErrorHelper(Context context) {
        this.context = context;
        this.windowManager = (WindowManager) context.getSystemService(Context.WINDOW_SERVICE);
    }

    /**
     * Scan terminal text for known errors; on a match show + speak the Hindi fix.
     */
    public void analyze(String screenText) {
        if (screenText == null || screenText.isEmpty()) return;

        String[] lines = screenText.split("\n");
        for (String line : lines) {
            String trimmed = line.trim();
            if (trimmed.isEmpty()) continue;
            for (int i = 0; i < ERROR_PATTERNS.size(); i++) {
                Matcher m = ERROR_PATTERNS.get(i).matcher(trimmed);
                if (m.find()) {
                    if (trimmed.equals(lastError)) return;
                    lastError = trimmed;
                    final String fix = ERROR_FIXES.get(i);
                    handler.post(() -> showPanel(trimmed, fix));
                    TtsEngine tts = App.get().getTts();
                    handler.postDelayed(() -> tts.speak(fix, true), 800);
                    return;
                }
            }
        }
    }

    private void showPanel(String error, String fix) {
        if (panelShowing) {
            updatePanel(error, fix);
            return;
        }
        try {
            panelView = LayoutInflater.from(context).inflate(R.layout.termux_error_panel, null);
            WindowManager.LayoutParams lp = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O)
                            ? WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
                            : WindowManager.LayoutParams.TYPE_PHONE,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );
            lp.gravity = Gravity.TOP | Gravity.CENTER_HORIZONTAL;
            lp.y = 40;

            TextView errorText = panelView.findViewById(R.id.error_text);
            TextView fixText = panelView.findViewById(R.id.fix_text);

            String shortError = error.length() > 120 ? error.substring(0, 120) + "..." : error;
            errorText.setText(shortError);
            fixText.setText(fix);

            panelView.findViewById(R.id.speak_btn).setOnClickListener(v ->
                    App.get().getTts().speak(fix, true));
            panelView.findViewById(R.id.close_btn).setOnClickListener(v -> hidePanel());

            panelView.setOnTouchListener(new View.OnTouchListener() {
                private float sx, sy;
                private int ix, iy;
                private boolean moving = false;

                @Override
                public boolean onTouch(View v, MotionEvent event) {
                    switch (event.getAction()) {
                        case MotionEvent.ACTION_DOWN:
                            sx = event.getRawX(); sy = event.getRawY();
                            ix = lp.x; iy = lp.y; moving = false;
                            return true;
                        case MotionEvent.ACTION_MOVE:
                            float dx = event.getRawX() - sx;
                            float dy = event.getRawY() - sy;
                            if (Math.abs(dx) > 10 || Math.abs(dy) > 10) moving = true;
                            if (moving) {
                                lp.x = ix + (int) dx;
                                lp.y = iy + (int) dy;
                                windowManager.updateViewLayout(panelView, lp);
                            }
                            return true;
                        case MotionEvent.ACTION_UP:
                            return true;
                    }
                    return false;
                }
            });

            windowManager.addView(panelView, lp);
            panelShowing = true;
        } catch (Exception ignored) {}
    }

    private void updatePanel(String error, String fix) {
        if (panelView == null) return;
        try {
            TextView errorText = panelView.findViewById(R.id.error_text);
            TextView fixText = panelView.findViewById(R.id.fix_text);
            String shortError = error.length() > 120 ? error.substring(0, 120) + "..." : error;
            errorText.setText(shortError);
            fixText.setText(fix);
        } catch (Exception ignored) {}
    }

    private void hidePanel() {
        if (panelView != null && panelShowing) {
            try {
                windowManager.removeView(panelView);
            } catch (Exception ignored) {}
            panelView = null;
            panelShowing = false;
        }
    }

    public void destroy() {
        handler.removeCallbacksAndMessages(null);
        hidePanel();
    }
}
