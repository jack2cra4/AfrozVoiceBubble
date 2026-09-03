package com.afroz.voicebubble.chat;

/**
 * A single JARVIS response in a conversation turn.
 *
 * Encapsulates the reply text, the language it should be voiced in, and
 * optionally a follow-up clarification question for the two-way Q&A loop.
 */
public class JarvisReply {

    /** The main reply text to speak. */
    public final String text;

    /** Language tag for the reply voice: "hi" or "en". */
    public final String lang;

    /**
     * Optional follow-up question. When non-null, JARVIS asks this question
     * and keeps the listener active to receive the user's confirmation,
     * entering a clarification sub-dialog.
     */
    public final String followupQuestion;

    /** Language tag for the follow-up question. */
    public final String followupLang;

    /**
     * True when this reply represents a clarification sub-dialog. The next
     * user utterance is treated as an answer to {@link #followupQuestion}.
     */
    public final boolean awaitingClarification;

    public JarvisReply(String text, String lang) {
        this(text, lang, null, lang, false);
    }

    public JarvisReply(String text, String lang, String followupQuestion,
                       String followupLang, boolean awaitingClarification) {
        this.text = text;
        this.lang = lang;
        this.followupQuestion = followupQuestion;
        this.followupLang = followupLang;
        this.awaitingClarification = awaitingClarification;
    }

    public boolean hasFollowup() {
        return followupQuestion != null && !followupQuestion.isEmpty();
    }
}
