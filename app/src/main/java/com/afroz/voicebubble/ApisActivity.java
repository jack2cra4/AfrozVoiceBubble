package com.afroz.voicebubble;

import android.app.AlertDialog;
import android.graphics.Typeface;
import android.os.Bundle;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.afroz.voicebubble.ai.AIProviderManager;
import com.afroz.voicebubble.ai.ProviderConfig;

import java.util.List;

/**
 * AI API CENTER.
 *
 * Add / test / save / edit / delete AI provider credentials. API keys are
 * stored securely (encrypted, in Android Keystore via APIKeyManager) and never
 * written into source, logs or commits. Provider status is real: NOT TESTED /
 * CONNECTED / INVALID, determined by an actual test call, never faked.
 */
public class ApisActivity extends AppCompatActivity {

    private AIProviderManager providers;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        providers = App.get().getProviderManager();

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        header();
        list();
    }

    private void header() {
        Button back = new Button(this);
        back.setBackgroundResource(R.drawable.btn_ghost_bg);
        back.setText(R.string.back);
        back.setTextColor(getColor(R.color.text_light));
        back.setOnClickListener(v -> finish());
        root.addView(back, lp()); spacer(8);

        Button add = new Button(this);
        add.setBackgroundResource(R.drawable.btn_start_bg);
        add.setText(R.string.btn_add_api);
        add.setTextColor(getColor(R.color.bg_dark));
        add.setOnClickListener(v -> promptAddProvider());
        root.addView(add, lp());
        spacer(8);
    }

    private void list() {
        List<ProviderConfig> all = providers.providers();
        for (ProviderConfig c : all) {
            root.addView(providerCard(c), lp());
            spacer(8);
        }
    }

    private View providerCard(final ProviderConfig c) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        LinearLayout head = new LinearLayout(this);
        head.setOrientation(LinearLayout.HORIZONTAL);
        head.setGravity(Gravity.CENTER_VERTICAL);

        TextView name = new TextView(this);
        name.setText(c.name.toUpperCase());
        name.setTextColor(getColor(R.color.text_light));
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        head.addView(name, new LinearLayout.LayoutParams(0, dp(38), 1));

        TextView status = new TextView(this);
        status.setText(statusLabel(c));
        status.setTextColor(getColor(statusColor(c)));
        status.setTextSize(12);
        head.addView(status, new LinearLayout.LayoutParams(dp(120), dp(38)));

        card.addView(head);

        TextView model = new TextView(this);
        String m = (c.model == null || c.model.isEmpty()) ? "model: —" : "model: " + c.model;
        model.setText(m);
        model.setTextColor(getColor(R.color.text_dim));
        model.setTextSize(12);
        card.addView(model);
        spacer(card, 4);

        // Actions
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button set = smallBtn("SET KEY");
        set.setBackgroundResource(R.drawable.btn_start_bg);
        set.setTextColor(getColor(R.color.bg_dark));
        set.setOnClickListener(v -> promptSetKey(c));
        actions.addView(set, weight());

        Button test = smallBtn(R.string.btn_test);
        test.setOnClickListener(v -> testProvider(c));
        actions.addView(test, weight());

        Button del = smallBtn(R.string.btn_delete);
        del.setOnClickListener(v -> {
            providers.remove(c.id);
            root.post(this::relist);
            Toast.makeText(this, "Removed", Toast.LENGTH_SHORT).show();
        });
        actions.addView(del, weight());

        card.addView(actions);
        return card;
    }

    private String statusLabel(ProviderConfig c) {
        if ("local".equalsIgnoreCase(c.name)) return getString(R.string.status_available);
        if (!providers.keys().has(c.name)) return getString(R.string.status_not_tested);
        if ("CONNECTED".equals(c.status)) return getString(R.string.status_connected);
        if ("INVALID".equals(c.status)) return getString(R.string.status_invalid);
        return getString(R.string.status_not_tested);
    }

    private int statusColor(ProviderConfig c) {
        if ("local".equalsIgnoreCase(c.name)) return R.color.accent_green;
        if ("CONNECTED".equals(c.status) || "INVALID".equals(c.status)) {
            return "CONNECTED".equals(c.status) ? R.color.accent_green : R.color.accent_red;
        }
        return R.color.text_dim;
    }

    private void testProvider(final ProviderConfig c) {
        if ("local".equalsIgnoreCase(c.name)) {
            Toast.makeText(this, R.string.status_available, Toast.LENGTH_SHORT).show();
            return;
        }
        if (!providers.keys().has(c.name)) {
            Toast.makeText(this, "Set an API key first", Toast.LENGTH_SHORT).show();
            return;
        }
        // Run the test off the UI thread.
        new Thread(() -> {
            String answer = providers.route(c.name, "Reply with the single word: OK",
                    "You are JARVIS.");
            runOnUiThread(() -> {
                boolean ok = answer != null && !answer.trim().isEmpty();
                c.status = ok ? "CONNECTED" : "INVALID";
                providers.update(c);
                Toast.makeText(this,
                        ok ? R.string.msg_test_ok : R.string.msg_test_fail, Toast.LENGTH_LONG).show();
                relist();
            });
        }).start();
    }

    private void promptAddProvider() {
        final EditText name = new EditText(this);
        name.setHint("Provider id (openai, gemini, anthropic, openrouter, groq, local, or custom)");
        name.setBackgroundResource(R.drawable.edit_bg);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_add_api)
                .setView(name)
                .setPositiveButton("Add", (d, w) -> {
                    String id = name.getText().toString().trim().toLowerCase();
                    if (id.isEmpty()) { Toast.makeText(this, "Enter a provider id", Toast.LENGTH_SHORT).show(); return; }
                    if (providers.get(id) != null) {
                        Toast.makeText(this, "Already exists", Toast.LENGTH_SHORT).show();
                        return;
                    }
                    providers.add(new ProviderConfig(id, id, "", ProviderConfig.defaultModel(id)));
                    relist();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptSetKey(final ProviderConfig c) {
        final EditText key = new EditText(this);
        key.setHint("Paste API key (stored encrypted on this device)");
        key.setBackgroundResource(R.drawable.edit_bg);

        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);

        final EditText baseUrl = new EditText(this);
        baseUrl.setHint("Base URL (optional)");
        baseUrl.setText(c.baseUrl == null ? "" : c.baseUrl);
        baseUrl.setBackgroundResource(R.drawable.edit_bg);

        final EditText model = new EditText(this);
        model.setHint("Model (optional)");
        model.setText(c.model == null ? "" : c.model);
        model.setBackgroundResource(R.drawable.edit_bg);

        box.addView(key);
        spacer(box, 8);
        box.addView(baseUrl);
        spacer(box, 8);
        box.addView(model);

        new AlertDialog.Builder(this)
                .setTitle(R.string.label_provider + " " + c.name)
                .setView(box)
                .setPositiveButton(R.string.btn_save, (d, w) -> {
                    String k = key.getText().toString().trim();
                    if (!k.isEmpty()) {
                        boolean stored = providers.keys().store(c.name, k);
                        if (!stored) {
                            Toast.makeText(this, "Secure storage failed", Toast.LENGTH_LONG).show();
                            return;
                        }
                    }
                    c.baseUrl = baseUrl.getText().toString().trim();
                    c.model = model.getText().toString().trim();
                    providers.update(c);
                    c.status = providers.keys().has(c.name) ? c.status : "NOT_TESTED";
                    Toast.makeText(this, "Saved securely", Toast.LENGTH_SHORT).show();
                    relist();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void relist() {
        root.removeAllViews();
        header();
        list();
    }

    private Button smallBtn(int resId) { return smallBtn(getString(resId)); }

    private Button smallBtn(String text) {
        Button b = new Button(this);
        b.setBackgroundResource(R.drawable.btn_ghost_bg);
        b.setText(text);
        b.setTextColor(getColor(R.color.text_light));
        b.setTextSize(12);
        b.setPadding(0, dp(6), 0, dp(6));
        return b;
    }

    private LinearLayout.LayoutParams weight() {
        return new LinearLayout.LayoutParams(0, dp(40), 1);
    }

    private void spacer() { spacer(8); }
    private void spacer(int h) {
        root.addView(new View(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(h)));
    }
    private void spacer(LinearLayout parent, int h) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(h)));
    }

    private LinearLayout.LayoutParams lp() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
