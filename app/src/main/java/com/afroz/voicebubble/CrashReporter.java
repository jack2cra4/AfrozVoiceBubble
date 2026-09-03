package com.afroz.voicebubble;

import android.content.Context;
import android.os.Build;
import android.os.Process;
import android.util.Log;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.PrintWriter;
import java.io.StringWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Diagnostic startup crash capture.
 *
 * Installs a {@link Thread.UncaughtExceptionHandler} as the very first step of
 * {@code Application.onCreate} so that even a crash during class initialisation
 * or the first Activity inflation is recorded. On a crash it:
 *
 *  - dumps the failing thread's stack, every other live thread's stack, and
 *    basic device/env info to {@code getFilesDir()/crash.log};
 *  - forwards the exception to the previous default handler so the platform
 *    still reports/terminates normally (nothing is swallowed or hidden).
 *
 * This exists to surface the real root cause for diagnosis; it does not catch,
 * swallow, or otherwise work around a crash.
 */
public final class CrashReporter {

    private static final String TAG = "JARVIS-CRASH";
    private static final long MAX_LOG_BYTES = 256L * 1024L;
    private static Thread.UncaughtExceptionHandler previous;

    private CrashReporter() {}

    /** Install the handler once. Must be called on the main thread in onCreate. */
    public static void install(Context appContext) {
        if (previous != null) return; // already installed
        previous = Thread.getDefaultUncaughtExceptionHandler();
        Thread.setDefaultUncaughtExceptionHandler((t, e) -> {
            logCrash(appContext, t, e);
            if (previous != null) {
                previous.uncaughtException(t, e);
            }
        });
    }

    private static void logCrash(Context ctx, Thread t, Throwable e) {
        try {
            File crashLog = new File(ctx.getFilesDir(), "crash.log");
            StringBuilder sb = new StringBuilder(2048);
            sb.append("===== JARVIS CRASH ").append(timestamp()).append(" =====\n");
            sb.append("Thread: ").append(t.getName())
                    .append("  pid: ").append(Process.myPid()).append('\n');
            appendDevice(sb);
            sb.append("\n-- Failing thread stack --\n");
            appendThrowable(sb, e);
            sb.append("\n-- All thread stacks --\n");
            for (Map.Entry<Thread, StackTraceElement[]> entry : Thread.getAllStackTraces().entrySet()) {
                Thread th = entry.getKey();
                sb.append("\n").append(th.getName())
                        .append(th.isDaemon() ? " (daemon)" : "")
                        .append(" [").append(th.getState()).append("]\n");
                for (StackTraceElement el : entry.getValue()) {
                    sb.append("    at ").append(el).append('\n');
                }
            }
            appendPreviousCrashes(sb, crashLog);
            writeAtomic(crashLog, sb.toString());
            Log.e(TAG, sb.toString());
        } catch (Exception ignored) {
            // Never let the reporter itself add another failure.
        }
    }

    private static void appendDevice(StringBuilder sb) {
        sb.append("SDK: ").append(Build.VERSION.SDK_INT)
                .append("  device: ").append(Build.MANUFACTURER)
                .append(' ').append(Build.MODEL)
                .append("  brand: ").append(Build.BRAND).append('\n');
    }

    private static void appendThrowable(StringBuilder sb, Throwable e) {
        StringWriter sw = new StringWriter();
        e.printStackTrace(new PrintWriter(sw));
        sb.append(sw);
    }

    private static void appendPreviousCrashes(StringBuilder sb, File crashLog) {
        String prev = readString(crashLog, MAX_LOG_BYTES);
        if (prev != null && !prev.isEmpty()) {
            sb.append("\n-- Previous crash log (kept for continuity) --\n").append(prev);
        }
    }

    private static void writeAtomic(File f, String content) {
        File tmp = new File(f.getParentFile(), "crash.log.tmp");
        FileOutputStream fos = null;
        try {
            fos = new FileOutputStream(tmp);
            fos.write(content.getBytes("UTF-8"));
            fos.getFD().sync();
        } catch (Exception ignored) {
        } finally {
            if (fos != null) try { fos.close(); } catch (Exception ignored) {}
        }
        if (!tmp.renameTo(f)) {
            if (f.exists() && !f.delete()) {
                return; // keep old log rather than discard
            }
            if (!tmp.renameTo(f)) {
                try {
                    copy(tmp, f);
                } catch (Exception ignored) {}
            }
        }
    }

    private static void copy(File from, File to) throws Exception {
        InputStream in = new FileInputStream(from);
        try {
            FileOutputStream out = new FileOutputStream(to);
            try {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) > 0) {
                    out.write(buf, 0, n);
                }
                out.getFD().sync();
            } finally {
                out.close();
            }
        } finally {
            in.close();
        }
    }

    private static String readString(File f, long maxBytes) {
        if (f == null || !f.exists()) return null;
        FileInputStream fis = null;
        try {
            fis = new FileInputStream(f);
            ByteArrayOutputStream bos = new ByteArrayOutputStream();
            byte[] buf = new byte[8192];
            int n;
            long total = 0;
            while ((n = fis.read(buf)) > 0 && total < maxBytes) {
                int write = (int) Math.min(n, maxBytes - total);
                bos.write(buf, 0, write);
                total += write;
            }
            return new String(bos.toByteArray(), "UTF-8");
        } catch (Exception ignored) {
            return null;
        } finally {
            if (fis != null) try { fis.close(); } catch (Exception ignored) {}
        }
    }

    private static String timestamp() {
        return new SimpleDateFormat("yyyy-MM-dd HH:mm:ss.SSS", Locale.US)
                .format(new Date());
    }

    /** Exposed so a screen/button can reveal where crashes are recorded. */
    public static File crashFile() {
        App ctx = App.get();
        return ctx == null ? null : new File(ctx.getFilesDir(), "crash.log");
    }
}
