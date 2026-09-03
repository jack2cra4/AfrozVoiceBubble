package com.afroz.voicebubble.chat;

/**
 * Coordinates the two-way conversation loop: routes a {@link JarvisReply} to
 * speech and, when the reply carries a follow-up clarification question, keeps
 * the microphone active to receive the user's confirmation.
 *
 * The speech/listening provider (currently the floating bubble service)
 * registers itself here; the accessibility/error path (TermuxErrorHelper) can
 * then push replies through the same loop without owning the mic.
 */
public class ConversationGate {

    /** Provider that performs speech output and mic listening management. */
    public interface Provider {
        void speak(String text, String lang);
        void startActiveListening();
        void stopActiveListening();
    }

    private Provider provider;

    public void setProvider(Provider p) {
        this.provider = p;
    }

    /** Push a reply through the gate: speak, then open an active-listening session if needed. */
    public void say(JarvisReply reply) {
        if (reply == null) return;
        if (provider == null) return;

        if (reply.hasFollowup()) {
            provider.speak(reply.followupQuestion, reply.followupLang);
            provider.startActiveListening();
        } else {
            provider.speak(reply.text, reply.lang);
            provider.stopActiveListening();
        }
    }
}
