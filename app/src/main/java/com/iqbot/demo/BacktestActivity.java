package com.iqbot.demo;

import android.Manifest;
import android.app.Activity;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.os.Build;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.os.Bundle;
import android.text.InputType;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

public final class BacktestActivity extends Activity {
    private static final int PICK_CSV = 1;
    private static final int REQUEST_NOTIFICATIONS = 2;
    private Uri selectedCsv;
    private SharedPreferences replayState;
    private final SharedPreferences.OnSharedPreferenceChangeListener replayListener =
            (state, key) -> showReplayState();
    private TextView source, status, result;
    private EditText stake, maxLoss, payout, maxTrades;
    private Button importCsv, sample, run, replay, stop;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
        replayState = getSharedPreferences(ReplayService.STATE, MODE_PRIVATE);
        if (savedState != null) selectedCsv = savedState.getParcelable("csv");
        ScrollView scroll = new ScrollView(this);
        scroll.setFitsSystemWindows(true);
        scroll.setFillViewport(true);
        LinearLayout content = new LinearLayout(this);
        content.setOrientation(LinearLayout.VERTICAL);
        content.setPadding(dp(20), dp(20), dp(20), dp(20));
        content.setBackgroundColor(Color.rgb(9, 15, 29));
        scroll.addView(content);
        setContentView(scroll);

        Button back = button(content, "Voltar ao painel");
        back.setOnClickListener(v -> finish());
        label(content, "Backtest offline", 28);
        label(content, "Simulação local com saldo fictício de R$ 100. O motor avalia candles M5/M15 fechados e só simula CALL. Entrada na abertura do próximo M5, liquidação no fechamento e empate contado como perda.", 15);
        source = label(content, "", 16);
        importCsv = button(content, "Selecionar CSV local");
        importCsv.setOnClickListener(v -> {
            Intent picker = new Intent(Intent.ACTION_OPEN_DOCUMENT);
            picker.setType("*/*");
            picker.addCategory(Intent.CATEGORY_OPENABLE);
            picker.putExtra(Intent.EXTRA_LOCAL_ONLY, true);
            try {
                startActivityForResult(picker, PICK_CSV);
            } catch (android.content.ActivityNotFoundException e) {
                status.setText("Seletor de arquivos indisponível.");
            }
        });
        sample = button(content, "Usar série sintética");
        sample.setOnClickListener(v -> {
            selectedCsv = null;
            showSource();
        });
        stake = field(content, "Valor fixo por operação (R$)", "2", true);
        maxLoss = field(content, "Limite de perda sobre o saldo inicial (R$)", "10", true);
        payout = field(content, "Retorno líquido em caso de acerto (%)", "80", true);
        maxTrades = field(content, "Máximo de operações", "10", false);
        run = button(content, "Executar backtest");
        run.setOnClickListener(v -> runBacktest(false));
        replay = button(content, "Reproduzir em segundo plano");
        replay.setOnClickListener(v -> {
            if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)
                    != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS}, REQUEST_NOTIFICATIONS);
            } else {
                runBacktest(true);
            }
        });
        stop = button(content, "Parar simulação");
        stop.setOnClickListener(v -> startService(new Intent(this, ReplayService.class).setAction(ReplayService.STOP)));
        status = label(content, "", 15);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        result = label(content, "", 16);
        showSource();
        showReplayState();
    }

    @Override protected void onStart() {
        super.onStart();
        replayState.registerOnSharedPreferenceChangeListener(replayListener);
        showReplayState();
    }

    @Override protected void onStop() {
        replayState.unregisterOnSharedPreferenceChangeListener(replayListener);
        super.onStop();
    }

    @Override protected void onSaveInstanceState(Bundle state) {
        state.putParcelable("csv", selectedCsv);
        super.onSaveInstanceState(state);
    }

    private void showSource() {
        source.setText(selectedCsv == null ? "Próxima execução: série sintética" : "Próxima execução: CSV selecionado");
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request != PICK_CSV || code != RESULT_OK || data == null || data.getData() == null) return;
        selectedCsv = data.getData();
        showSource();
    }

    @Override public void onRequestPermissionsResult(int request, String[] permissions, int[] results) {
        super.onRequestPermissionsResult(request, permissions, results);
        if (request != REQUEST_NOTIFICATIONS) return;
        if (results.length > 0 && results[0] == PackageManager.PERMISSION_GRANTED) runBacktest(true);
        else status.setText("Ative as notificações para acompanhar e parar a reprodução pela tela bloqueada.");
    }

    private void runBacktest(boolean paced) {
        if (ReplayService.running) return;
        final double amount, loss, rate;
        final int limit;
        try {
            amount = decimal(stake);
            loss = decimal(maxLoss);
            rate = decimal(payout);
            limit = Integer.parseInt(maxTrades.getText().toString().trim());
            if (!(amount >= 0.01 && amount <= 100 && loss >= 0.01 && loss <= 100
                    && rate >= 0 && rate <= 100 && limit >= 1 && limit <= 10_000)) {
                throw new IllegalArgumentException();
            }
        } catch (IllegalArgumentException e) {
            status.setText("Use R$ 0,01 a R$ 100 para valor e perda, 0 a 100% para retorno e 1 a 10.000 operações.");
            return;
        }

        Intent service = new Intent(this, ReplayService.class)
                .putExtra(ReplayService.STAKE, amount)
                .putExtra(ReplayService.LOSS, loss)
                .putExtra(ReplayService.PAYOUT, rate / 100)
                .putExtra(ReplayService.LIMIT, limit)
                .putExtra(ReplayService.PACED, paced);
        if (selectedCsv != null) {
            service.setData(selectedCsv);
            service.addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION);
        }
        try {
            startForegroundService(service);
        } catch (IllegalStateException | SecurityException e) {
            status.setText("O Android não permitiu iniciar a simulação. Tente com o app aberto.");
        }
    }

    private void showReplayState() {
        boolean running = ReplayService.running;
        boolean interrupted = !running && replayState.getBoolean(ReplayService.PENDING, false);
        status.setText(interrupted ? "Simulação interrompida pelo Android."
                : replayState.getString(ReplayService.STATUS, ""));
        result.setText(replayState.getString(ReplayService.RESULT, ""));
        run.setEnabled(!running);
        replay.setEnabled(!running);
        stop.setEnabled(running);
        importCsv.setEnabled(!running);
        sample.setEnabled(!running);
    }

    private double decimal(EditText field) {
        return Double.parseDouble(field.getText().toString().trim().replace(',', '.'));
    }

    private EditText field(LinearLayout content, String title, String value, boolean decimal) {
        TextView label = label(content, title, 15);
        EditText field = new EditText(this);
        field.setId(android.view.View.generateViewId());
        label.setLabelFor(field.getId());
        field.setTextColor(Color.WHITE);
        field.setInputType(InputType.TYPE_CLASS_NUMBER | (decimal ? InputType.TYPE_NUMBER_FLAG_DECIMAL : 0));
        field.setText(value);
        content.addView(field);
        return field;
    }

    private TextView label(LinearLayout content, String value, int size) {
        TextView text = new TextView(this);
        text.setText(value);
        text.setTextSize(size);
        text.setTextColor(Color.WHITE);
        text.setPadding(0, dp(10), 0, dp(4));
        content.addView(text);
        return text;
    }

    private Button button(LinearLayout content, String title) {
        Button button = new Button(this);
        button.setText(title);
        button.setAllCaps(false);
        content.addView(button);
        return button;
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

}
