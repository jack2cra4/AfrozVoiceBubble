package com.afroz.voicebubble.engine;

import android.content.Context;
import android.os.Handler;
import android.os.Looper;

import com.afroz.voicebubble.speech.BilingualStt;

import java.util.Locale;

/**
 * Always-on conversational driving loop for JARVIS.
 *
 * Wraps {@link BilingualStt} and routes every utterance through the full new
 * {@link ConversationManager} pipeline (wake handling, agents, AI providers,
 * task monitoring, subtitle translation, Hindi/English generation). It is the
 * single place that decides what to speak, so there is exactly one TTS path and
 * recognition is paused while JARVIS speaks — preventing the microphone from
 * hearing JARVIS's own voice and eliminating overlapping/echoed speech:
 *
 *  - Hearing a wake word halts any running TTS immediately, then responds.
 *  - While a response is being voiced the microphone is quiet; listening
 *    resumes automatically once speech finishes (or after a short guard delay).
 *  - After every utterance (or recognition error) it automatically re-listens.
 *  - {@link #stop()} halts recognition + TTS and cancels all pending work.
 *
 * All STT callbacks arrive on the STT worker thread; view/status updates are
 * marshalled to the main looper so the bubble never touches views off-main.
 */
public class ConversationLoop {

    /** Ui-facing callbacks (invoked on the main thread). */
    public interface Callback {
        void onListening();
        void onWake();
        void onStatus(String status);
    }

    private final Context context;
    private final ConversationManager conversation;
    private final TTSManager tts;
    private final Handler mainHandler = new Handler(Looper.getMainLooper());

    private BilingualStt stt;
    private Callback callback;
    private volatile boolean running = false;
    private String lastLang = "en";
    private volatile boolean speaking = false;
    private final Runnable relisten = this::listen;

    public ConversationLoop(Context context, ConversationManager conversation, TTSManager tts) {
        this.context = context.getApplicationContext();
        this.conversation = conversation;
        this.tts = tts;
    }

    public void setCallback(Callback cb) {
        this.callback = cb;
    }

    public synchronized void start() {
        if (running) return;
        if (stt == null) stt = new BilingualStt(context);
        if (!stt.isAvailable()) {
            post(() -> {
                if (callback != null) callback.onStatus("Voice not available");
            });
            return;
        }
        running = true;
        stt.setListener(createListener());
        listen();
    }

    public synchronized void stop() {
        running = false;
        speaking = false;
        mainHandler.removeCallbacksAndMessages(null);
        if (stt != null) {
            try {
                stt.stopListening();
            } catch (Exception ignored) {}
        }
        tts.stop();
    }

    public boolean isRunning() {
        return running;
    }

    public synchronized void destroy() {
        stop();
        if (stt != null) {
            stt.destroy();
            stt = null;
        }
    }

    private BilingualStt.Listener createListener() {
        return new BilingualStt.Listener() {
            @Override
            public void onResult(String text, String lang) {
                if (!running) return;
                lastLang = "hi".equals(lang) ? "hi" : "en";
                process(text, lang);
            }

            @Override
            public void onPartial(String text) {
                if (running && !speaking && text != null && isWake(text)) {
                    // Wake heard live: halt TTS right now, before speech overlaps.
                    tts.stop();
                    post(() -> {
                        if (callback != null) callback.onWake();
                    });
                }
            }

            @Override
            public void onError(String message) {
                if (!running) return;
                post(() -> {
                    if (callback != null) callback.onStatus("Listening...");
                });
                // Recognition ended without a usable result; retry shortly.
                mainHandler.postDelayed(relisten, 400);
            }
        };
    }

    /** Single decision point for what gets spoken next. */
    private void process(final String text, final String lang) {
        final boolean wake = isWake(text);
        if (wake) {
            // Interrupt before responding so nothing overlaps.
            tts.stop();
        }
        // Run the full new pipeline on the STT worker thread (network/agents
        // are never on the main thread). Then marshal speech to main.
        final String response = conversation.respondToSpeech(text, lang);
        mainHandler.post(() -> {
            if (!running && !wake) {
                // Stopped while processing — never speak late responses.
                return;
            }
            if (wake) {
                if (callback != null) callback.onWake();
            } else if (callback != null) {
                callback.onStatus("...");
            }
            if (response != null && !response.isEmpty()) {
                holdMicWhileSpeaking();
                tts.setOnSpeechDone(this::resumeAfterSpeech);
                tts.speak(response, lang);
            } else {
                scheduleRelisten();
            }
            maybeStopLive();
        });
    }

    /** A voice "stop" command also halts live screen monitoring. */
    private void maybeStopLive() {
        if (conversation.consumeStopCommand()) {
            if (!conversation.getState().isStopped()) {
                com.afroz.voicebubble.App.get().getLiveMode().stop();
            }
        }
    }

    private void holdMicWhileSpeaking() {
        speaking = true;
        if (stt != null) {
            try {
                stt.stopListening();
            } catch (Exception ignored) {}
        }
    }

    /** Fired (on the TTS thread) after speech ends -> resume listening. */
    private void resumeAfterSpeech() {
        mainHandler.post(() -> {
            speaking = false;
            if (running) {
                scheduleRelisten();
            }
        });
    }

    /** Schedule a single re-listen, cancelling any previous scheduled one. */
    private void scheduleRelisten() {
        mainHandler.removeCallbacks(relisten);
        mainHandler.postDelayed(relisten, 250);
    }

    private void listen() {
        if (!running || stt == null || speaking) return;
        mainHandler.removeCallbacks(relisten);
        try {
            stt.setSessionLang(BilingualStt.langToLocale(lastLang));
        } catch (Exception ignored) {}
        post(() -> {
            if (callback != null) callback.onListening();
        });
        try {
            stt.startListening();
        } catch (Exception ignored) {
            // Never crash if a recognition session can't start.
        }
    }

    private boolean isWake(String text) {
        if (text == null) return false;
        String t = text.toLowerCase(Locale.US).replaceAll("[!.,?]", "");
        return t.contains("jarvis") || t.contains("जार्विस") || t.contains("जारविस")
                || t.contains("जारवीस");
    }

    private void post(Runnable r) {
        mainHandler.post(r);
    }
}
