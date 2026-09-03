package com.afroz.voicebubble.speech;

import android.content.Context;
import android.speech.tts.TextToSpeech;
import android.speech.tts.UtteranceProgressListener;
import android.speech.tts.Voice;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Set;

/**
 * Offline bilingual TTS engine with two selectable voice profiles:
 *
 *  - {@link VoiceProfile#JARVIS_MALE} (Mode A, default): a deep, crisp male
 *    voice with pitch 0.82f and rate 1.12f for a futuristic Tony-Stark tone.
 *  - {@link VoiceProfile#ASSISTANT_FEMALE} (Mode B): a standard female voice.
 *
 * Available TextToSpeech voices are filtered dynamically (by gender keyword in
 * the voice name and by hi-IN / en-IN locale tag) and the best match is set.
 *
 * Language routing is strict: Hindi input locks the locale to
 * {@code Locale("hi","IN")} and English input to {@code Locale("en","IN")}.
 * Raw unparsed English logs/system strings are never spoken; only clean
 * assistant responses are voiced.
 */
public class TtsEngine {

    /** Selectable voice profiles for the assistant. */
    public enum VoiceProfile {
        /** "JARVIS Male" core — deep, crisp, Tonk-Stark robotic tone. */
        JARVIS_MALE("JARVIS Male", 0.82f, 1.12f),
        /** "Assistant Female" — standard female voice. */
        ASSISTANT_FEMALE("Assistant Female", 1.0f, 1.1f);

        public final String label;
        public final float pitch;
        public final float rate;

        VoiceProfile(String label, float pitch, float rate) {
            this.label = label;
            this.pitch = pitch;
            this.rate = rate;
        }
    }

    /** Current conversation language tag ("hi" or "en"), auto-switched. */
    private String activeLang = "en";

    private final Context context;
    private TextToSpeech tts;
    private boolean ready = false;
    private boolean usingHindi = false;
    private VoiceProfile profile = VoiceProfile.JARVIS_MALE;

    public TtsEngine(Context context) {
        this.context = context.getApplicationContext();
        tts = new TextToSpeech(this.context, status -> {
            if (status == TextToSpeech.SUCCESS) {
                // Probe for a Hindi voice but don't lock the engine:
                // the profile + locale are selected per utterance below.
                int result = tts.setLanguage(new Locale("hi", "IN"));
                usingHindi = (result != TextToSpeech.LANG_MISSING_DATA
                        && result != TextToSpeech.LANG_NOT_SUPPORTED);
                if (!usingHindi) {
                    tts.setLanguage(Locale.getDefault());
                }
                ready = true;
                applyProfile(profile, activeLang);
            }
        });
    }

    // ============================================================
    // Voice-profile selection
    // ============================================================

    /** Current active profile. */
    public VoiceProfile getProfile() {
        return profile;
    }

    /** Set the active voice profile and re-apply it to the engine. */
    public void setProfile(VoiceProfile newProfile) {
        this.profile = newProfile;
        if (ready && tts != null) {
            applyProfile(newProfile, activeLang);
        }
    }

    /** Optional speech-rate override (multiplier, e.g. 1.0). Applies live. */
    public void setRateOverride(float rate) {
        this.rateOverride = rate;
        if (ready && tts != null) {
            try {
                tts.setSpeechRate(rate);
            } catch (Exception ignored) {}
        }
    }

    private float rateOverride = -1f;

    /**
     * Dynamically filter available voices and pick the best match for the
     * requested gender + language, then apply pitch/rate for the profile.
     */
    private void applyProfile(VoiceProfile p, String lang) {
        if (tts == null) return;
        try {
            boolean wantMale = (p == VoiceProfile.JARVIS_MALE);
            Voice chosen = pickVoice(wantMale, lang);
            if (chosen != null) {
                tts.setVoice(chosen);
            } else {
                // No voice matched; fall back to strict locale routing.
                tts.setLanguage(langFor(lang));
            }
            tts.setPitch(p.pitch);
            tts.setSpeechRate(rateOverride >= 0f ? rateOverride : p.rate);
        } catch (Exception ignored) {}
    }

    /**
     * Iterate {@code tts.getVoices()}, preferring a voice whose name carries
     * the requested gender keyword and whose locale is hi-IN or en-IN, with
     * very high quality as a tie-breaker.
     */
    private Voice pickVoice(boolean wantMale, String lang) {
        Set<Voice> voices;
        try {
            voices = tts.getVoices();
        } catch (Exception e) {
            return null;
        }
        if (voices == null || voices.isEmpty()) return null;

        String gender = wantMale ? "male" : "female";
        Locale pref = langFor(lang);

        List<Voice> langMatches = new ArrayList<>();
        for (Voice v : voices) {
            if (v == null || v.isNetworkConnectionRequired()) continue;
            String name = v.getName() == null ? "" : v.getName().toLowerCase(Locale.US);
            Locale l = v.getLocale();
            boolean localeOk = false;
            if (l != null) {
                localeOk = l.getLanguage().equalsIgnoreCase(pref.getLanguage())
                        || l.getLanguage().equalsIgnoreCase("hi")
                        || l.getLanguage().equalsIgnoreCase("en");
            }
            // Prefer very high quality.
            if ((name.contains(gender) || name.contains("female") || name.contains("male"))
                    && localeOk) {
                langMatches.add(v);
            }
        }

        if (langMatches.isEmpty()) {
            // Looser pass: any local voice in the preferred language.
            for (Voice v : voices) {
                if (v == null || v.isNetworkConnectionRequired()) continue;
                Locale l = v.getLocale();
                if (l != null && l.getLanguage().equalsIgnoreCase(pref.getLanguage())) {
                    langMatches.add(v);
                }
            }
        }

        if (langMatches.isEmpty()) return null;

        // Prefer very-high quality first, then by name depth/local marker.
        Voice best = langMatches.get(0);
        for (Voice v : langMatches) {
            if (v.getQuality() > best.getQuality()) {
                best = v;
            } else if (v.getQuality() == best.getQuality()
                    && (v.getName() != null ? v.getName().length() : 0)
                       < (best.getName() != null ? best.getName().length() : Integer.MAX_VALUE)) {
                best = v;
            }
        }
        return best;
    }

    private Locale langFor(String lang) {
        return "hi".equals(lang) ? new Locale("hi", "IN") : new Locale("en", "IN");
    }

    // ============================================================
    // Pre-warm / readiness
    // ============================================================

    public void prewarm() {
        if (ready && tts != null) {
            tts.speak(" ", TextToSpeech.QUEUE_FLUSH, null, "afroz_prewarm");
        }
    }

    public boolean isReady() {
        return ready;
    }

    public boolean isUsingHindi() {
        return usingHindi;
    }

    public String getActiveLang() {
        return activeLang;
    }

    /** Auto-detect language ("hi"/"en") from script (Devanagari check). */
    public static String detectLang(String text) {
        if (text == null) return "en";
        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c >= 0x0900 && c <= 0x097F) return "hi";
        }
        return "en";
    }

    // ============================================================
    // Speaking (strict language routing)
    // ============================================================

    /**
     * Speak in the given language ("hi" or "en"), strictly locking the locale
     * and applying the active voice profile. No raw logs are voiced — only
     * clean assistant text should be passed here.
     */
    public void speak(String text, String lang) {
        if (!ready || tts == null || text == null) return;
        String requested = (lang == null) ? activeLang : lang;
        if ("hi".equals(requested)) {
            if (usingHindi) {
                tts.setLanguage(new Locale("hi", "IN"));
                activeLang = "hi";
            } else {
                tts.setLanguage(new Locale("en", "IN"));
                activeLang = "en";
            }
        } else {
            tts.setLanguage(new Locale("en", "IN"));
            activeLang = "en";
        }
        // Re-apply the profile voice so pitch/rate + gender persist per turn.
        applyProfile(profile, activeLang);
        tts.speak(clean(text), TextToSpeech.QUEUE_FLUSH, null, "afroz_utterance");
    }

    public void setLanguage(String lang) {
        if (!ready || tts == null) return;
        activeLang = "hi".equals(lang) ? "hi" : "en";
        try {
            tts.setLanguage(langFor(activeLang));
            applyProfile(profile, activeLang);
        } catch (Exception ignored) {}
    }

    public void speakAuto(String text) {
        speak(text, detectLang(text));
    }

    public void speak(String text) {
        speak(text, true);
    }

    public void speak(String text, boolean flush) {
        if (!ready || tts == null || text == null || text.isEmpty()) return;
        int queue = flush ? TextToSpeech.QUEUE_FLUSH : TextToSpeech.QUEUE_ADD;
        tts.speak(clean(text), queue, null, "afroz_utterance");
    }

    public void speakClean(String text) {
        if (text == null) return;
        String c = clean(text);
        if (c.isEmpty() || !ready || tts == null) return;
        tts.speak(c, TextToSpeech.QUEUE_FLUSH, null, "afroz_utterance");
    }

    /** Strip noise: collapse whitespace and drop raw terminal-log noise lines. */
    private String clean(String text) {
        if (text == null) return "";
        String s = text.trim().replaceAll("\\s+", " ");
        if (s.isEmpty()) return "";
        // Never speak pure log/system noise (e.g. stray bracket lines).
        if (s.length() <= 2 && s.matches("[\\[\\]{}():;=*#-]+")) return "";
        return s;
    }

    // ============================================================
    // Control
    // ============================================================

    public void stop() {
        if (tts != null) tts.stop();
    }

    public void setListener(UtteranceProgressListener listener) {
        if (tts != null) tts.setOnUtteranceProgressListener(listener);
    }

    public void shutdown() {
        if (tts != null) {
            tts.stop();
            tts.shutdown();
            tts = null;
        }
    }
}
