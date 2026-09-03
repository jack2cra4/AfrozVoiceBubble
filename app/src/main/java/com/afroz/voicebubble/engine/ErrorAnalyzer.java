package com.afroz.voicebubble.engine;

import java.util.Locale;

/**
 * Detects and explains common terminal errors (Termux, npm, node, git,
 * python, compilation, permission, missing commands, paths, stack traces).
 * Produces a concise English summary plus a spoken English fix; the
 * ConversationManager localises these into Hindi when required.
 */
public class ErrorAnalyzer {

    public static final class Detection {
        public final String summaryEn;
        public final String fixEn;

        Detection(String summaryEn, String fixEn) {
            this.summaryEn = summaryEn;
            this.fixEn = fixEn;
        }
    }

    /** Returns a detection for the first recognised error, or null. */
    public Detection detect(String text) {
        if (text == null) return null;
        String lower = text.toLowerCase(Locale.US);

        if (lower.contains("module not found") || lower.contains("importerror")
                || lower.contains("modulenotfounderror")) {
            return new Detection(
                    "a Python module is missing",
                    "install the missing module with pip install, or check your virtual environment");
        }
        if (lower.contains("command not found") || lower.contains("not found")) {
            if (lower.contains("command not found")) {
                return new Detection(
                        "a command is not installed",
                        "install it with pkg install <name>, for example pkg install git");
            }
            return new Detection(
                    "a file or dependency was not found",
                    "check the path with pwd and ls, or reinstall the dependency");
        }
        if (lower.contains("permission denied") || lower.contains("forbidden")) {
            return new Detection(
                    "a permission problem",
                    "change permissions with chmod 755, or use su if root is required");
        }
        if (lower.contains("npm err") || lower.contains("eresolve")
                || lower.contains("dependency conflict")) {
            return new Detection(
                    "an npm dependency conflict",
                    "run npm cache clean --force, delete node_modules, then npm install");
        }
        if (lower.contains("no space left") || lower.contains("disk full")) {
            return new Detection(
                    "no disk space left",
                    "check df -h and run apt autoremove or remove old files");
        }
        if (lower.contains("connection refused") || lower.contains("could not resolve")
                || lower.contains("network")) {
            return new Detection(
                    "a network or connection problem",
                    "check your internet, or run termux-change-repo and try again");
        }
        if (lower.contains("segmentation fault")) {
            return new Detection(
                    "a segmentation fault (memory issue)",
                    "check pointers and array bounds in your code");
        }
        if (lower.contains("outofmemoryerror") || lower.contains("cannot allocate memory")) {
            return new Detection(
                    "an out-of-memory error",
                    "close other apps or increase the memory limit (e.g. -Xmx)");
        }
        if (lower.contains("error") || lower.contains("exception")) {
            return new Detection(
                    "an error or exception",
                    "read the message above carefully, then retry corrected");
        }
        return null;
    }
}
