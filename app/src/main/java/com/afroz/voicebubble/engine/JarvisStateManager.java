package com.afroz.voicebubble.engine;

/**
 * Explicit interaction state machine for JARVIS.
 *
 * Guarantees deterministic transitions and clean teardown (no background
 * processing after STOPPED). Avoids race conditions between OCR, speech
 * recognition, TTS, screen capture and conversation processing.
 */
public class JarvisStateManager {

    public enum State {
        IDLE,
        LISTENING,
        PROCESSING,
        SPEAKING,
        LIVE_SCREEN,
        WAITING_FOR_FOLLOWUP,
        STOPPED
    }

    public interface Listener {
        void onStateChanged(State previous, State current);
    }

    private State state = State.IDLE;
    private Listener listener;

    public void setListener(Listener l) {
        this.listener = l;
    }

    public State getState() {
        return state;
    }

    public boolean isLive() {
        return state == State.LIVE_SCREEN;
    }

    public boolean isStopped() {
        return state == State.STOPPED || state == State.IDLE;
    }

    public void transitionTo(State next) {
        if (state == next) return;
        State prev = state;
        state = next;
        if (listener != null) listener.onStateChanged(prev, state);
    }

    /** Wake word -> LISTENING (also stops any speaking in progress). */
    public void onWake() {
        transitionTo(State.LISTENING);
    }

    /** User utterance received -> PROCESSING. */
    public void onProcessing() {
        transitionTo(State.PROCESSING);
    }

    /** A response is being voiced -> SPEAKING. */
    public void onSpeaking() {
        transitionTo(State.SPEAKING);
    }

    /** Start live screen mode. */
    public void startLive() {
        transitionTo(State.LIVE_SCREEN);
    }

    /** Enter follow-up listening after a response/question. */
    public void onFollowUp() {
        transitionTo(State.WAITING_FOR_FOLLOWUP);
    }

    /** Idle listening after wake/follow-up. */
    public void onListening() {
        transitionTo(State.LISTENING);
    }

    /** STOP -> STOPPED; nothing runs afterwards. */
    public void stop() {
        transitionTo(State.STOPPED);
    }

    public void idle() {
        transitionTo(State.IDLE);
    }
}
