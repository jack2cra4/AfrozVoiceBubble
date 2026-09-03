package com.afroz.voicebubble.engine;

import com.afroz.voicebubble.engine.ContextAnalyzer.ScreenContext;
import com.afroz.voicebubble.engine.ErrorAnalyzer.Detection;

/**
 * Generates clean, localised responses from an intent and the analysed screen
 * context. Replaces the raw content with a concise summary / explanation and
 * localises between Hindi and English. The user name is used sparingly.
 */
public class ResponseGenerator {

    private final LocalMemory memory;

    public ResponseGenerator(LocalMemory memory) {
        this.memory = memory;
    }

    /** Build a response. `lan` is "hi"/"en"/"auto" resolved from language mode. */
    public String respond(IntentParser.Intent intent, String lang,
                          ScreenContext screen, Detection error) {
        boolean hi = "hi".equals(lang);
        String name = memory.getUserName();

        switch (intent) {
            case WAKE:
                return hi ? "जी " + name + " सर, बताइए क्या हुक्म है?"
                          : "Yes " + name + " sir, what would you like me to do?";
            case GREETING:
                return hi ? "नमस्ते " + name + " सर! जार्विस उपस्थित है।"
                          : "Hello " + name + " sir! JARVIS is present.";
            case EXPLAIN_ERROR:
                if (error != null) {
                    return hi ? "सर, यहाँ " + error.summaryEn + " दिख रहा है। "
                            + "फिक्स के लिए: " + error.fixEn + "।"
                            : "Sir, there is " + error.summaryEn + " here. To fix it: "
                            + error.fixEn + ".";
                }
                return hi ? "सर, फ़िलहाल स्क्रीन पर कोई स्पष्ट एरर नहीं दिख रहा।"
                          : "Sir, I don't see a clear error on screen right now.";
            case HOW_TO_FIX:
                if (error != null) {
                    return hi ? "सर, फिक्स यह है: " + error.fixEn + "।"
                              : "Sir, the fix is: " + error.fixEn + ".";
                }
                return hi ? "सर, अभी स्क्रीन पर fix करने लायक कुछ नहीं दिखता।"
                          : "Sir, nothing needs fixing on screen right now.";
            case READ_SCREEN:
            case WHAT_ON_SCREEN:
                if (screen != null && screen.errorSummary != null) {
                    return hi ? "सर, स्क्रीन पर " + screen.errorSummary + " दिख रहा है।"
                              : "Sir, on screen there is " + screen.errorSummary + ".";
                }
                return hi ? "जी सर, देख रहा हूँ।"
                          : "Yes sir, let me look.";
            case WHAT_IS_RUNNING:
                if (screen != null) {
                    if (screen.type == ContextAnalyzer.ContextType.BUILD) {
                        return hi ? "सर, build चल रही है।"
                                  : "Sir, a build is in progress.";
                    }
                    if (screen.type == ContextAnalyzer.ContextType.INSTALLATION) {
                        return hi ? "सर, installation चल रही है।"
                                  : "Sir, an installation is running.";
                    }
                }
                return hi ? "सर, अभी कुछ ख़ास चलता नहीं दिख रहा।"
                          : "Sir, nothing significant appears to be running.";
            case TIME_REMAINING:
                if (screen != null && screen.remainingTime != null) {
                    return hi ? "सर, लगभग " + screen.remainingTime + " बाकी हैं।"
                              : "Sir, about " + screen.remainingTime + " remain.";
                }
                if (screen != null && screen.progressPercent != null) {
                    return hi ? "सर, " + screen.progressPercent + " प्रतिशत पूर्ण है।"
                              : "Sir, the process is " + screen.progressPercent + " percent complete.";
                }
                return hi ? "सर, समय का अनुमान स्क्रीन पर नहीं दिख रहा।"
                          : "Sir, I can't see an estimated time on screen.";
            case IS_CORRECT:
                if (error != null) {
                    return hi ? "सर, यह बिल्कुल सही नहीं है, एरर दिख रहा है।"
                              : "Sir, this is not correct, there is an error.";
                }
                return hi ? "हाँ सर, जो दिख रहा है वह सही लगता है। कमांड रन कर दीजिए।"
                          : "Yes sir, what I see looks correct. Go ahead and run it.";
            case NEXT_STEP:
                if (error != null) {
                    return hi ? "अगला कदम: " + error.fixEn + "।"
                              : "Next step: " + error.fixEn + ".";
                }
                return hi ? "सर, मुझे बताइए क्या करना है।"
                          : "Sir, tell me what you want me to do.";
            case STOP_ASSISTANT:
                return hi ? "ठीक है सर, लाइव मोड बंद है।"
                          : "Okay sir, live mode is off.";
            default:
                return hi ? "सर, मैं समझ गया। कृपया और बताइए।"
                          : "Understood, sir. Please tell me more.";
        }
    }

    // Helper used by LiveModeController to avoid repeating the whole screen.
    public String proactive(boolean hi, ScreenContext screen) {
        if (screen == null) return null;
        if (screen.errorSummary != null) {
            return hi ? "सर, यहाँ " + screen.errorSummary + " आया है। चाहें तो मैं समझा दूँ।"
                      : "Sir, there is an error here. I can explain it if you want.";
        }
        if (screen.type == ContextAnalyzer.ContextType.BUILD && screen.progressPercent != null
                && screen.progressPercent >= 100) {
            return hi ? "सर, build पूरा हो गया है।" : "Sir, the build has completed.";
        }
        return null;
    }
}
