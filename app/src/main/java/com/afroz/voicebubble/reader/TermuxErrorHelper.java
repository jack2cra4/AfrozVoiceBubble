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

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

/**
 * Detects common Termux/Linux terminal errors on screen, shows and speaks the
 * fix via the JARVIS assistant. Detection is silent until an actual blocking
 * error is found — no random unprompted speech.
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
            "Package not found. Run \"apt update\" first, then \"apt install <package>\". If it still fails, try \"pkg update\".");
        register(
            "Permission denied",
            "Permission denied. Run \"chmod 755 <filename>\". If root is required, use \"su\".");
        register(
            "No space left on device",
            "No disk space left. Check with \"df -h\", then run \"apt autoremove\" or remove old files.");
        register(
            "Connection refused",
            "Connection refused. Check your internet. If a VPN is on, turn it off and retry.");
        register(
            "Could not resolve host",
            "DNS could not resolve. Run \"termux-change-repo\" or connect to a different network.");
        register(
            "command not found",
            "Command not installed. Install it with \"pkg install <name>\", for example \"pkg install git\".");
        register(
            "No such file or directory",
            "File not found. Use \"pwd\" and \"ls\" to check your current path, then use the correct one.");
        register(
            "Segmentation fault",
            "Segmentation fault. This is a memory issue. Check pointers and array bounds in your code.");
        register(
            "fatal:",
            "Fatal error. In Git this is usually caused by an authentication or network problem.");
        register(
            "error:",
            "An error was found. Read the message carefully; it tells you what is wrong.");
        register(
            "npm ERR!",
            "NPM error. Run \"npm cache clean --force\", then \"npm install\" again. You can also delete node_modules and retry.");
        register(
            "pip: error:",
            "Pip error. Run \"pip install --upgrade pip\", then reinstall.");
        register(
            "ModuleNotFoundError",
            "Python module not found. Run \"pip install <module>\".");
        register(
            "ImportError",
            "Import error. Try \"pip install --force-reinstall <module>\".");
        register(
            "OSError:",
            "Operating system error. Check file permissions and paths.");
        register(
            "command not found: pkg",
            "This is not Termux or Termux is not installed properly. Install Termux from the Play Store.");
        register(
            "Fontconfig warning",
            "This is only a warning, you can ignore it. Nothing is affected.");
        register(
            "denied by selinux",
            "SELinux denied permission. This is an Android security policy; root access may be required.");
        register(
            "Killed",
            "Process was killed, usually due to low memory. Close other apps or increase swap.");
        register(
            "cannot allocate memory",
            "Could not allocate memory. Close other apps or create a swap file.");
        register(
            "disk quota exceeded",
            "Disk quota reached. Remove unnecessary files.");
        register(
            "bus error",
            "Memory alignment issue. Check pointer usage in your code.");
        register(
            "not a git command",
            "Git command not installed. Run \"pkg install git\".");
        register(
            "ssh: connect to host",
            "SSH connection failed. Check internet, your key, and port 22.");
        register(
            "refusing to merge unrelated histories",
            "Git merge failed. Run \"git merge --allow-unrelated-histories\".");
        register(
            "remote origin already exists",
            "Remote already exists. Run \"git remote set-url origin <URL>\".");
        register(
            "java.lang.OutOfMemoryError",
            "Java ran out of memory. Increase -Xmx, for example \"java -Xmx512m\".");
        register(
            "E: Failed to fetch",
            "Download failed. Change your mirror with \"termux-change-repo\" and run \"apt update\".");
        register(
            "node:internal/modules/cjs/loader",
            "Node.js loader error. Delete node_modules and rerun \"npm install\".");
        register(
            "Hash Sum mismatch",
            "Package hash mismatch. Run \"apt clean\" and \"apt update\" again.");
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
     * Scan terminal text for known errors; on a real blocking match show and
     * speak the English fix. Silent otherwise.
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
                    handler.postDelayed(() -> App.get().getTts().speak(fix, true), 600);
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
