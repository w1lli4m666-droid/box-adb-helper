package cn.boxadb.helper;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.ClipData;
import android.content.ClipboardManager;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.graphics.drawable.StateListDrawable;
import android.os.Build;
import android.os.Bundle;
import android.os.Handler;
import android.provider.Settings;
import android.text.InputType;
import android.view.Gravity;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class MainActivity extends Activity {
    private static final int BG = Color.rgb(10, 20, 32), CARD = Color.rgb(19, 37, 54);
    private static final int WHITE = Color.rgb(235, 243, 250), MUTED = Color.rgb(161, 182, 199);
    private static final int GREEN = Color.rgb(85, 219, 180), AMBER = Color.rgb(255, 205, 119);
    private final Handler handler = new Handler();
    private final ExecutorService executor = Executors.newSingleThreadExecutor();
    private TextView status, detail, addresses, system, command, caption;
    private Button detect, settings, portButton;
    private int port = 5555;
    private boolean running, destroyed;
    private List<String> currentIps = new ArrayList<String>();

    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN, WindowManager.LayoutParams.FLAG_FULLSCREEN);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                | View.SYSTEM_UI_FLAG_FULLSCREEN | View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY);
        port = getPreferences(MODE_PRIVATE).getInt("port", 5555);
        buildUi(); refreshInfo(); settings.requestFocus();
    }
    @Override protected void onResume() { super.onResume(); if (status != null) runProbe(); }
    @Override protected void onDestroy() {
        destroyed = true; handler.removeCallbacksAndMessages(null); executor.shutdownNow(); super.onDestroy();
    }
    private int dp(float n) { return Math.round(n * getResources().getDisplayMetrics().density); }
    private TextView text(String value, int size, int color) {
        TextView view = new TextView(this); view.setText(value); view.setTextSize(size); view.setTextColor(color);
        view.setFontFeatureSettings("kern"); view.setIncludeFontPadding(false); return view;
    }
    private LinearLayout column() {
        LinearLayout view = new LinearLayout(this); view.setOrientation(LinearLayout.VERTICAL); return view;
    }
    private LinearLayout.LayoutParams lp(int w, int h) { return new LinearLayout.LayoutParams(w, h); }
    private void add(LinearLayout parent, View child, int top) {
        LinearLayout.LayoutParams p = lp(-1, -2); p.topMargin = dp(top); parent.addView(child, p);
    }
    private GradientDrawable shape(int color, int stroke) {
        GradientDrawable d = new GradientDrawable(); d.setColor(color); d.setCornerRadius(dp(12));
        if (stroke != 0) d.setStroke(dp(2), stroke); return d;
    }
    private LinearLayout card() {
        LinearLayout card = column(); card.setPadding(dp(22), dp(20), dp(22), dp(20));
        card.setBackground(shape(CARD, 0)); return card;
    }
    private Button button(String label, View.OnClickListener click) {
        Button button = new Button(this); button.setText(label); button.setTextSize(17); button.setTextColor(WHITE);
        button.setAllCaps(false); button.setPadding(dp(12), dp(8), dp(12), dp(8)); button.setMinHeight(dp(52));
        StateListDrawable background = new StateListDrawable();
        background.addState(new int[]{android.R.attr.state_pressed}, shape(Color.rgb(44, 105, 99), GREEN));
        background.addState(new int[]{android.R.attr.state_focused}, shape(Color.rgb(34, 73, 78), GREEN));
        background.addState(new int[]{}, shape(Color.rgb(29, 51, 70), Color.rgb(47, 72, 90)));
        button.setBackground(background); button.setFocusable(true); button.setOnClickListener(click); return button;
    }
    private void buildUi() {
        ScrollView scroll = new ScrollView(this); scroll.setBackgroundColor(BG); scroll.setFillViewport(true);
        LinearLayout root = column(); root.setPadding(dp(40), dp(26), dp(40), dp(22));
        scroll.addView(root); setContentView(scroll);
        LinearLayout header = new LinearLayout(this); header.setGravity(Gravity.CENTER_VERTICAL);
        TextView title = text(getString(R.string.app_name), 29, WHITE); title.setTypeface(null, Typeface.BOLD);
        header.addView(title, new LinearLayout.LayoutParams(0, -2, 1));
        header.addView(text(getString(R.string.version_label, "1.1.0"), 15, GREEN)); add(root, header, 0);
        add(root, text(getString(R.string.tagline), 16, MUTED), 8);

        LinearLayout row = new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
        LinearLayout left = card(), right = card();
        LinearLayout.LayoutParams leftP = new LinearLayout.LayoutParams(0, -1, 1);
        leftP.rightMargin = dp(16); row.addView(left, leftP);
        row.addView(right, new LinearLayout.LayoutParams(0, -1, 1));
        add(left, text(getString(R.string.section_device), 14, GREEN), 0);
        addresses = text(getString(R.string.reading_ip), 23, WHITE); addresses.setTypeface(Typeface.MONOSPACE); add(left, addresses, 12);
        system = text("", 15, MUTED); add(left, system, 12);
        add(left, text(getString(R.string.no_root), 14, MUTED), 10);
        add(right, text(getString(R.string.section_adb), 14, GREEN), 0);
        status = text(getString(R.string.waiting), 23, AMBER); status.setTypeface(null, Typeface.BOLD); add(right, status, 12);
        detail = text("", 15, MUTED); detail.setMinLines(3); add(right, detail, 12);
        add(root, row, 18);

        LinearLayout actions = new LinearLayout(this);
        settings = button(getString(R.string.debug_settings), new View.OnClickListener() { public void onClick(View v) { openSettings(); } });
        detect = button(getString(R.string.check_again), new View.OnClickListener() { public void onClick(View v) { runProbe(); } });
        portButton = button(getString(R.string.port_label, port), new View.OnClickListener() { public void onClick(View v) { choosePort(); } });
        Button help = button(getString(R.string.help), new View.OnClickListener() { public void onClick(View v) { showHelp(); } });
        Button[] buttons = {settings, detect, portButton, help};
        for (int i=0; i<buttons.length; i++) {
            buttons[i].setId(100 + i);
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, dp(54), 1);
            if (i > 0) p.leftMargin = dp(10); actions.addView(buttons[i], p);
            if (i > 0) buttons[i].setNextFocusLeftId(99 + i);
            if (i < buttons.length-1) buttons[i].setNextFocusRightId(101 + i);
        }
        add(root, actions, 16);
        LinearLayout connection = card();
        connection.setPadding(dp(22), dp(16), dp(22), dp(16));
        add(connection, text(getString(R.string.section_guide), 14, GREEN), 0);
        LinearLayout steps = new LinearLayout(this);
        String[] titles = {getString(R.string.step1_title), getString(R.string.step2_title), getString(R.string.step3_title)};
        String[] descriptions = {getString(R.string.step1_body), getString(R.string.step2_body), getString(R.string.step3_body)};
        for (int i = 0; i < titles.length; i++) {
            LinearLayout step = column();
            LinearLayout.LayoutParams p = new LinearLayout.LayoutParams(0, -2, 1);
            if (i > 0) p.leftMargin = dp(18);
            steps.addView(step, p);
            TextView stepTitle = text(titles[i], 16, WHITE); stepTitle.setTypeface(null, Typeface.BOLD);
            add(step, stepTitle, 0); add(step, text(descriptions[i], 14, MUTED), 6);
            if (i == 1) {
                command = text("", 14, GREEN); command.setTypeface(Typeface.MONOSPACE); add(step, command, 6);
            }
        }
        add(connection, steps, 10);
        caption = text("", 12, MUTED); add(connection, caption, 8);
        connection.setFocusable(true); connection.setId(200); connection.setContentDescription(getString(R.string.guide_accessibility));
        StateListDrawable cbg = new StateListDrawable();
        cbg.addState(new int[]{android.R.attr.state_focused}, shape(CARD, GREEN)); cbg.addState(new int[]{}, shape(CARD,0));
        connection.setBackground(cbg);
        connection.setOnClickListener(new View.OnClickListener() { public void onClick(View v) {
            if (currentIps.isEmpty()) { Toast.makeText(MainActivity.this, getString(R.string.connect_network), Toast.LENGTH_SHORT).show(); return; }
            ClipboardManager clipboard = (ClipboardManager) getSystemService(CLIPBOARD_SERVICE);
            clipboard.setPrimaryClip(ClipData.newPlainText("ADB", command.getText()));
            Toast.makeText(MainActivity.this, getString(R.string.copied), Toast.LENGTH_SHORT).show();
        }});
        for (Button b : buttons) b.setNextFocusDownId(200);
        connection.setNextFocusUpId(101); add(root, connection, 16);
        add(root, text(getString(R.string.footer), 12, MUTED), 12);
    }
    private void refreshInfo() {
        currentIps = findIps();
        StringBuilder ipText = new StringBuilder();
        for (String ip : currentIps) { if (ipText.length() > 0) ipText.append("\n"); ipText.append(ip); }
        addresses.setText(currentIps.isEmpty() ? getString(R.string.no_ipv4) : ipText.toString());
        system.setText(Build.MANUFACTURER + " " + Build.MODEL + "\nAndroid " + Build.VERSION.RELEASE + "  ·  API " + Build.VERSION.SDK_INT);
        command.setText(currentIps.isEmpty() ? getString(R.string.connect_wifi) : "adb connect " + currentIps.get(0) + ":" + port);
        caption.setText(currentIps.isEmpty() ? getString(R.string.caption_offline) : getString(R.string.caption_pending));
    }
    private static List<String> findIps() {
        ArrayList<String> result = new ArrayList<String>();
        try {
            Enumeration<NetworkInterface> interfaces = NetworkInterface.getNetworkInterfaces();
            while (interfaces.hasMoreElements()) {
                NetworkInterface iface = interfaces.nextElement();
                if (!iface.isUp() || iface.isLoopback()) continue;
                Enumeration<InetAddress> addresses = iface.getInetAddresses();
                while (addresses.hasMoreElements()) {
                    InetAddress address = addresses.nextElement();
                    if (address instanceof Inet4Address && !address.isLoopbackAddress() && !address.isLinkLocalAddress()) {
                        String ip = address.getHostAddress(); if (!result.contains(ip)) result.add(ip);
                    }
                }
            }
        } catch (Exception ignored) { }
        Collections.sort(result); return result;
    }
    private void runProbe() {
        if (running || destroyed) return;
        refreshInfo(); running = true;
        final int probePort = port;
        final List<String> ips = new ArrayList<String>(currentIps);
        status.setText(getString(R.string.checking)); status.setTextColor(AMBER);
        detail.setText(getString(R.string.checking_detail, probePort));
        detect.setText(getString(R.string.checking_button)); detect.setEnabled(false); portButton.setEnabled(false);
        executor.execute(new Runnable() { public void run() {
            final AdbProbe.Result local = AdbProbe.check("127.0.0.1", probePort);
            String found = null; AdbProbe.Result foundResult = null;
            boolean other = local.state == AdbProbe.State.NOT_ADB;
            for (String ip : ips) {
                if (Thread.currentThread().isInterrupted()) return;
                AdbProbe.Result result = AdbProbe.check(ip, probePort);
                other |= result.state == AdbProbe.State.NOT_ADB;
                if (result.isAdb()) { found = ip; foundResult = result; break; }
            }
            final String lanIp = found;
            final AdbProbe.Result lanResult = foundResult;
            final boolean unconfirmed = other;
            handler.post(new Runnable() { public void run() {
                if (destroyed) return;
                running = false; detect.setEnabled(true); portButton.setEnabled(true); detect.setText(getString(R.string.check_again));
                if (lanIp != null) {
                    status.setText(getString(R.string.adb_detected)); status.setTextColor(GREEN);
                    detail.setText(getString(lanResult.state == AdbProbe.State.ADB_AUTH ? R.string.handshake_auth_detail : R.string.handshake_connected_detail));
                    command.setText("adb connect " + lanIp + ":" + probePort);
                    caption.setText(getString(R.string.caption_success));
                } else if (local.isAdb()) {
                    status.setText(getString(R.string.local_only)); status.setTextColor(AMBER);
                    detail.setText(getString(R.string.local_only_detail));
                } else if (unconfirmed) {
                    status.setText(getString(R.string.port_reachable)); status.setTextColor(AMBER);
                    detail.setText(getString(R.string.port_reachable_detail));
                } else {
                    status.setText(getString(R.string.adb_not_detected)); status.setTextColor(AMBER);
                    detail.setText(getString(R.string.adb_not_detected_detail));
                }
            }});
        }});
    }
    private void openSettings() {
        try { startActivity(new Intent(Settings.ACTION_APPLICATION_DEVELOPMENT_SETTINGS)); }
        catch (Exception missing) {
            try {
                startActivity(new Intent(Settings.ACTION_SETTINGS));
                Toast.makeText(this, getString(R.string.find_debug_settings), Toast.LENGTH_LONG).show();
            } catch (Exception unavailable) {
                new AlertDialog.Builder(this).setTitle(getString(R.string.manual_settings_title))
                        .setMessage(getString(R.string.manual_settings_body))
                        .setPositiveButton(getString(R.string.got_it), null).show();
            }
        }
    }
    private void choosePort() {
        final EditText input = new EditText(this); input.setInputType(InputType.TYPE_CLASS_NUMBER);
        input.setText(String.valueOf(port)); input.selectAll(); input.setSingleLine(true);
        LinearLayout wrap = column(); wrap.setPadding(dp(24), dp(8), dp(24), 0);
        add(wrap, text(getString(R.string.port_hint), 16, MUTED), 0); add(wrap, input, 8);
        final AlertDialog dialog = new AlertDialog.Builder(this).setTitle(getString(R.string.port_dialog_title))
                .setView(wrap).setNegativeButton(getString(R.string.cancel), null).setPositiveButton(getString(R.string.check), null).create();
        dialog.setOnShowListener(new android.content.DialogInterface.OnShowListener() {
            public void onShow(android.content.DialogInterface d) {
                dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(new View.OnClickListener() {
                    public void onClick(View v) {
                        int chosen;
                        try { chosen = Integer.parseInt(input.getText().toString().trim()); }
                        catch (Exception invalid) { input.setError(getString(R.string.invalid_port)); return; }
                        if (chosen < 1 || chosen > 65535) { input.setError(getString(R.string.invalid_port)); return; }
                        port = chosen; getPreferences(MODE_PRIVATE).edit().putInt("port", port).apply();
                        portButton.setText(getString(R.string.port_label, port)); dialog.dismiss(); runProbe();
                    }
                });
            }
        }); dialog.show();
    }
    private void showHelp() {
        new AlertDialog.Builder(this).setTitle(getString(R.string.help_title))
            .setMessage(getString(R.string.help_body))
            .setPositiveButton(getString(R.string.got_it), null).show();
    }
}
