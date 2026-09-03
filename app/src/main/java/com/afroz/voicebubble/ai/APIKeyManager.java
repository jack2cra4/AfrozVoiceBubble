package com.afroz.voicebubble.ai;

import android.content.Context;
import android.content.SharedPreferences;
import android.security.keystore.KeyGenParameterSpec;
import android.security.keystore.KeyProperties;
import android.util.Base64;

import java.nio.charset.StandardCharsets;
import java.security.KeyStore;

import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import javax.crypto.spec.GCMParameterSpec;

/**
 * Secure API-key storage.
 *
 * API keys are NEVER stored in source code, logs, or plain SharedPreferences.
 * Each key is encrypted with an AES/GCM key held inside the Android Keystore,
 * and only the ciphertext is persisted. Keys cannot be recovered without the
 * device's Keystore, satisfying the "secure storage" requirement.
 */
public class APIKeyManager {

    private static final String ANDROID_KEYSTORE = "AndroidKeyStore";
    private static final String KEY_ALIAS = "jarvis_api_master";
    private static final String PREFS = "jarvis_api_keys";
    private static final String TRANSFORM = "AES/GCM/NoPadding";

    private final SharedPreferences prefs;

    public APIKeyManager(Context context) {
        this.prefs = context.getApplicationContext()
                .getSharedPreferences(PREFS, Context.MODE_PRIVATE);
    }

    /** Encrypt and store the key for a provider. */
    public boolean store(String provider, String apiKey) {
        if (provider == null || apiKey == null || apiKey.isEmpty()) return false;
        try {
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            SecretKey key = getOrCreateKey();
            cipher.init(Cipher.ENCRYPT_MODE, key);
            byte[] enc = cipher.doFinal(apiKey.getBytes(StandardCharsets.UTF_8));
            byte[] iv = cipher.getIV();
            // Store iv + ciphertext (iv is not secret).
            byte[] blob = new byte[iv.length + enc.length];
            System.arraycopy(iv, 0, blob, 0, iv.length);
            System.arraycopy(enc, 0, blob, iv.length, enc.length);
            prefs.edit().putString(provider, Base64.encodeToString(blob, Base64.NO_WRAP)).apply();
            return true;
        } catch (Exception e) {
            return false;
        }
    }

    /** Decrypt and return the key for a provider, or null. */
    public String get(String provider) {
        if (provider == null) return null;
        String b64 = prefs.getString(provider, null);
        if (b64 == null) return null;
        try {
            byte[] blob = Base64.decode(b64, Base64.NO_WRAP);
            byte[] iv = new byte[12];
            System.arraycopy(blob, 0, iv, 0, 12);
            Cipher cipher = Cipher.getInstance(TRANSFORM);
            cipher.init(Cipher.DECRYPT_MODE, getOrCreateKey(), new GCMParameterSpec(128, iv));
            byte[] dec = cipher.doFinal(blob, 12, blob.length - 12);
            return new String(dec, StandardCharsets.UTF_8);
        } catch (Exception e) {
            return null;
        }
    }

    public boolean has(String provider) {
        return prefs.getString(provider, null) != null;
    }

    public boolean remove(String provider) {
        if (provider == null) return false;
        prefs.edit().remove(provider).apply();
        return true;
    }

    /** Number of stored keys (for the MEMORY display). */
    public int count() {
        return prefs.getAll().size();
    }

    private SecretKey getOrCreateKey() throws Exception {
        KeyStore ks = KeyStore.getInstance(ANDROID_KEYSTORE);
        ks.load(null);
        KeyStore.Entry e = ks.getEntry(KEY_ALIAS, null);
        if (e != null && e instanceof KeyStore.SecretKeyEntry) {
            return ((KeyStore.SecretKeyEntry) e).getSecretKey();
        }
        KeyGenerator kg = KeyGenerator.getInstance(
                KeyProperties.KEY_ALGORITHM_AES, ANDROID_KEYSTORE);
        kg.init(new KeyGenParameterSpec.Builder(KEY_ALIAS,
                KeyProperties.PURPOSE_ENCRYPT | KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM)
                .setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE)
                .build());
        return kg.generateKey();
    }
}
