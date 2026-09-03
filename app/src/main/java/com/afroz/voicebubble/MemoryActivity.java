package com.afroz.voicebubble;

import android.graphics.Typeface;
import android.os.Bundle;
import android.widget.Button;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;

import com.afroz.voicebubble.engine.MemoryManager;

/**
 * LOCAL MEMORY.
 *
 * Shows what JARVIS currently remembers (user, language, voice, active tasks,
 * enabled agents, configured providers, last conversation topic) and provides
 * clear-conversation / clear-memory / reset actions. API keys are never shown
 * here — they stay encrypted in secure storage.
 */
public class MemoryActivity extends AppCompatActivity {

    private MemoryManager memory;
    private TextView body;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        memory = App.get().getMemoryManager();

        ScrollView scroll = new ScrollView(this);
        android.widget.LinearLayout root = new android.widget.LinearLayout(this);
        root.setOrientation(android.widget.LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        Button back = new Button(this);
        back.setBackgroundResource(R.drawable.btn_ghost_bg);
        back.setText(R.string.back);
        back.setTextColor(getColor(R.color.text_light));
        back.setOnClickListener(v -> finish());
        root.addView(back, lp());
        spacer(root, 8);

        body = new TextView(this);
        body.setTextColor(getColor(R.color.text_light));
        body.setTextSize(15);
        body.setTypeface(Typeface.MONOSPACE);
        body.setPadding(dp(8), dp(8), dp(8), dp(8));
        body.setBackgroundResource(R.drawable.card_bg);
        root.addView(body, lp());
        spacer(root, 12);

        Button view = new Button(this);
        view.setBackgroundResource(R.drawable.btn_start_bg);
        view.setText(R.string.btn_view_memory);
        view.setTextColor(getColor(R.color.bg_dark));
        view.setOnClickListener(v -> refresh());
        root.addView(view, lp());
        spacer(root, 8);

        Button clearConv = new Button(this);
        clearConv.setBackgroundResource(R.drawable.btn_ghost_bg);
        clearConv.setText(R.string.btn_clear_conversation);
        clearConv.setTextColor(getColor(R.color.text_light));
        clearConv.setOnClickListener(v -> {
            memory.clearConversation();
            Toast.makeText(this, "Conversation cleared", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(clearConv, lp());
        spacer(root, 8);

        Button clearMem = new Button(this);
        clearMem.setBackgroundResource(R.drawable.btn_ghost_bg);
        clearMem.setText(R.string.btn_clear_memory);
        clearMem.setTextColor(getColor(R.color.accent_red));
        clearMem.setOnClickListener(v -> {
            memory.clearMemory();
            Toast.makeText(this, "Memory cleared", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(clearMem, lp());
        spacer(root, 8);

        Button reset = new Button(this);
        reset.setBackgroundResource(R.drawable.btn_ghost_bg);
        reset.setText(R.string.btn_reset_jarvis);
        reset.setTextColor(getColor(R.color.accent_red));
        reset.setOnClickListener(v -> {
            App.get().getSettingsManager().resetJarvisPrefs(this);
            Toast.makeText(this, "JARVIS reset", Toast.LENGTH_SHORT).show();
            refresh();
        });
        root.addView(reset, lp());

        refresh();
    }

    private void refresh() {
        String summary = memory.summary();
        int tasks = App.get().getTaskManager().all().size();
        int agents = App.get().getAgentManager().enabledCount();
        int providers = App.get().getProviderManager().configuredCount();
        StringBuilder sb = new StringBuilder();
        sb.append(summary);
        sb.append("• Tasks: ").append(tasks).append("\n");
        sb.append("• Active agents: ").append(agents).append("\n");
        sb.append("• Providers ready: ").append(providers).append("\n");
        if (sb.toString().trim().isEmpty()) sb.append(getString(R.string.memory_empty));
        body.setText(sb.toString());
    }

    private void spacer(android.widget.LinearLayout parent, int h) {
        parent.addView(new android.view.View(this), new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT, dp(h)));
    }

    private android.widget.LinearLayout.LayoutParams lp() {
        return new android.widget.LinearLayout.LayoutParams(
                android.widget.LinearLayout.LayoutParams.MATCH_PARENT,
                android.widget.LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int v) {
        return Math.round(getResources().getDisplayMetrics().density * v);
    }
}
