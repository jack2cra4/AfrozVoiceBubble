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

import com.afroz.voicebubble.agent.Agent;
import com.afroz.voicebubble.agent.AgentManager;

import java.util.List;

/**
 * AGENT CENTER.
 *
 * Lists the built-in specialised agents (CODER, DEBUGGER, CODE REVIEWER,
 * SECURITY ANALYST, TESTER, ANDROID EXPERT, TERMUX EXPERT, RESEARCHER,
 * TRANSLATOR, SCREEN ANALYST) and lets the user enable/disable them or add
 * custom agents. JARVIS routes tasks to enabled agents (see ConversationManager).
 */
public class AgentsActivity extends AppCompatActivity {

    private AgentManager agents;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        agents = App.get().getAgentManager();

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
        add.setText(R.string.btn_add_agent);
        add.setTextColor(getColor(R.color.bg_dark));
        add.setOnClickListener(v -> promptAddAgent());
        root.addView(add, lp()); spacer(8);
    }

    private void list() {
        List<Agent> all = agents.all();
        for (Agent a : all) {
            root.addView(agentCard(a), lp());
            spacer(8);
        }
    }

    private View agentCard(final Agent a) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView name = new TextView(this);
        name.setText(a.name + (a.enabled ? "  ●" : ""));
        name.setTextColor(getColor(a.enabled ? R.color.accent_cyan : R.color.text_light));
        name.setTextSize(15);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);

        if (a.role != null && !a.role.isEmpty()) {
            TextView role = new TextView(this);
            role.setText(a.role);
            role.setTextColor(getColor(R.color.accent_gold));
            role.setTextSize(12);
            card.addView(role);
        }
        if (a.description != null && !a.description.isEmpty()) {
            TextView desc = new TextView(this);
            desc.setText(a.description);
            desc.setTextColor(getColor(R.color.text_dim));
            desc.setTextSize(12);
            card.addView(desc);
        }
        if (a.instructions != null && !a.instructions.isEmpty()) {
            TextView ins = new TextView(this);
            ins.setText("• " + a.instructions);
            ins.setTextColor(getColor(R.color.text_light));
            ins.setTextSize(12);
            card.addView(ins);
        }
        spacer(card, 4);

        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);

        Button toggle = smallBtn(a.enabled ? R.string.btn_disable : R.string.btn_enable);
        toggle.setOnClickListener(v -> {
            a.enabled = !a.enabled;
            agents.update(a);
            relist();
        });
        actions.addView(toggle, weight());
        card.addView(actions);

        return card;
    }

    private void promptAddAgent() {
        LinearLayout box = new LinearLayout(this);
        box.setOrientation(LinearLayout.VERTICAL);
        box.setPadding(dp(20), 0, dp(20), 0);

        final EditText name = field("Agent name");
        final EditText role = field("Role, e.g. Termux Expert");
        final EditText desc = field("Description");
        final EditText ins = field("System Instructions (e.g. explain errors in Hindi)");

        box.addView(name); spacer(box, 8);
        box.addView(role); spacer(box, 8);
        box.addView(desc); spacer(box, 8);
        box.addView(ins);

        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_add_agent)
                .setView(box)
                .setPositiveButton("Add", (d, w) -> {
                    String n = name.getText().toString().trim();
                    if (n.isEmpty()) { Toast.makeText(this, "Enter a name", Toast.LENGTH_SHORT).show(); return; }
                    Agent a = new Agent(String.valueOf(System.currentTimeMillis()), n.toUpperCase(),
                            role.getText().toString().trim(), desc.getText().toString().trim(),
                            "LOCAL", "", ins.getText().toString().trim(), true);
                    agents.add(a);
                    relist();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private EditText field(String hint) {
        EditText e = new EditText(this);
        e.setHint(hint);
        e.setBackgroundResource(R.drawable.edit_bg);
        return e;
    }

    private void relist() {
        root.removeAllViews();
        header();
        list();
    }

    private Button smallBtn(int resId) {
        Button b = new Button(this);
        b.setBackgroundResource(R.drawable.btn_ghost_bg);
        b.setText(resId);
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
