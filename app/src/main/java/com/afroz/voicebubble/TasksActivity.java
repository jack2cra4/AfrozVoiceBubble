package com.afroz.voicebubble;

import android.app.AlertDialog;
import android.content.DialogInterface;
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

import com.afroz.voicebubble.engine.TaskManager;

import java.util.List;

/**
 * TASK MONITORING.
 *
 * List active / completed / failed / paused tasks, create new monitored tasks,
 * and pause / resume / rename / delete / view details. Status is only ever
 * changed from observable information via TaskManager; never invented.
 */
public class TasksActivity extends AppCompatActivity {

    private TaskManager tasks;
    private LinearLayout root;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        tasks = App.get().getTaskManager();

        ScrollView scroll = new ScrollView(this);
        root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(16), dp(16), dp(16), dp(24));
        scroll.addView(root);
        setContentView(scroll);

        header();
        refresh();
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
        add.setText(R.string.btn_add_task);
        add.setTextColor(getColor(R.color.bg_dark));
        add.setOnClickListener(v -> promptNewTask());
        root.addView(add, lp()); spacer(8);
    }

    private void refresh() {
        root.removeViews(2, root.getChildCount() - 2);
        List<TaskManager.Task> all = tasks.all();
        if (all.isEmpty()) {
            TextView empty = new TextView(this);
            empty.setText("No tasks yet. Tell JARVIS: \"इस काम को monitor करना\" or press ADD TASK.");
            empty.setTextColor(getColor(R.color.text_dim));
            empty.setGravity(Gravity.CENTER);
            empty.setPadding(dp(8), dp(20), dp(8), dp(20));
            root.addView(empty, lp());
            return;
        }
        for (TaskManager.Task t : all) {
            root.addView(taskCard(t), lp());
            spacer(8);
        }
    }

    private View taskCard(final TaskManager.Task t) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setBackgroundResource(R.drawable.card_bg);
        card.setPadding(dp(12), dp(10), dp(12), dp(10));

        TextView name = new TextView(this);
        name.setText(t.name);
        name.setTextColor(getColor(R.color.text_light));
        name.setTextSize(16);
        name.setTypeface(Typeface.DEFAULT_BOLD);
        card.addView(name);

        TextView status = new TextView(this);
        status.setText(statusText(t.status, t.elapsedMs()));
        status.setTextColor(getColor(statusColor(t.status)));
        status.setTextSize(13);
        card.addView(status); spacer(card, 4);

        // Actions row: pause/resume, rename, delete
        LinearLayout actions = new LinearLayout(this);
        actions.setOrientation(LinearLayout.HORIZONTAL);
        actions.setGravity(Gravity.CENTER);

        Button toggle = smallBtn(
                t.status == TaskManager.Status.RUNNING ? R.string.btn_pause : R.string.btn_resume);
        toggle.setOnClickListener(v -> {
            tasks.setStatus(t.id, t.status == TaskManager.Status.RUNNING
                    ? TaskManager.Status.PAUSED : TaskManager.Status.RUNNING);
            refresh();
        });
        actions.addView(toggle, weight());

        Button rename = smallBtn(R.string.btn_rename);
        rename.setOnClickListener(v -> promptRename(t));
        actions.addView(rename, weight());

        Button del = smallBtn(R.string.btn_delete);
        del.setOnClickListener(v -> {
            tasks.delete(t.id);
            refresh();
        });
        actions.addView(del, weight());

        card.addView(actions);
        return card;
    }

    private void spacer(LinearLayout parent, int h) {
        parent.addView(new View(this), new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT, dp(h)));
    }

    private String statusText(TaskManager.Status s, long elapsed) {
        String base;
        switch (s) {
            case RUNNING: base = getString(R.string.status_running); break;
            case COMPLETED: base = getString(R.string.status_completed); break;
            case FAILED: base = getString(R.string.status_failed); break;
            default: base = getString(R.string.status_paused);
        }
        long min = elapsed / 60000;
        String time = min >= 60 ? (min / 60) + "h " + (min % 60) + "m" : min + "m";
        if (s == TaskManager.Status.RUNNING || s == TaskManager.Status.PAUSED) {
            return base + " · Elapsed: " + time;
        }
        return base;
    }

    private int statusColor(TaskManager.Status s) {
        switch (s) {
            case COMPLETED: return R.color.accent_green;
            case FAILED: return R.color.accent_red;
            case PAUSED: return R.color.accent_gold;
            default: return R.color.accent_cyan;
        }
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

    private void promptNewTask() {
        final EditText input = new EditText(this);
        input.setHint("Task name, e.g. Android Build");
        input.setBackgroundResource(R.drawable.edit_bg);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_add_task)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    String name = input.getText().toString().trim();
                    if (name.isEmpty()) name = "Task";
                    tasks.create(name, TaskManager.Status.RUNNING, null);
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void promptRename(final TaskManager.Task t) {
        final EditText input = new EditText(this);
        input.setText(t.name);
        input.setBackgroundResource(R.drawable.edit_bg);
        new AlertDialog.Builder(this)
                .setTitle(R.string.btn_rename)
                .setView(input)
                .setPositiveButton("OK", (d, w) -> {
                    tasks.rename(t.id, input.getText().toString().trim());
                    refresh();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void spacer() { spacer(8); }

    private void spacer(int h) {
        root.addView(new View(this), new LinearLayout.LayoutParams(
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
