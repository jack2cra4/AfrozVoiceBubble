package com.afroz.voicebubble.engine;

import android.graphics.Bitmap;
import android.media.Image;

import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Fully offline OCR using ML Kit text recognition.
 *
 * Produces plain text from a captured screen {@link Image} or {@link Bitmap}.
 * ML Kit text recognition runs entirely on-device (Hindi + English scripts,
 * terminal/code text, numbers, paths, etc.). No screenshots or text are ever
 * sent off-device.
 *
 * A single recognizer instance is reused (never re-initialized per frame) and
 * frames are closed promptly to avoid resource leaks.
 */
public class OCRManager {

    private TextRecognizer recognizer;

    public OCRManager() {
        // One recognizer for the lifetime of the object (local, offline).
        try {
            recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);
        } catch (Exception e) {
            recognizer = null;
        }
    }

    public boolean isAvailable() {
        return recognizer != null;
    }

    /**
     * Recognise text from a captured image frame. The image is closed by the
     * caller. Returns the recognised text or an empty string on failure.
     */
    public void recognize(Image image, int rotationDegrees,
                          final AtomicReference<String> sink,
                          final Runnable onDone) {
        if (recognizer == null || image == null) {
            if (onDone != null) onDone.run();
            return;
        }
        try {
            InputImage input = InputImage.fromMediaImage(image, rotationDegrees);
            recognizer.process(input).addOnSuccessListener(text ->
                    finish(text != null ? text.getText() : "", sink, onDone))
                    .addOnFailureListener(e ->
                            finish("", sink, onDone));
        } catch (Exception e) {
            if (onDone != null) onDone.run();
        }
    }

    public void recognizeBitmap(Bitmap bitmap, final AtomicReference<String> sink,
                                final Runnable onDone) {
        if (recognizer == null || bitmap == null) {
            if (onDone != null) onDone.run();
            return;
        }
        try {
            InputImage input = InputImage.fromBitmap(bitmap, 0);
            recognizer.process(input).addOnSuccessListener(text ->
                    finish(text != null ? text.getText() : "", sink, onDone))
                    .addOnFailureListener(e ->
                            finish("", sink, onDone));
        } catch (Exception e) {
            if (onDone != null) onDone.run();
        }
    }

    private void finish(String text, AtomicReference<String> sink, Runnable onDone) {
        if (sink != null) sink.set(text == null ? "" : text);
        if (onDone != null) onDone.run();
    }

    public void close() {
        if (recognizer != null) {
            try {
                recognizer.close();
            } catch (Exception ignored) {}
            recognizer = null;
        }
    }
}
