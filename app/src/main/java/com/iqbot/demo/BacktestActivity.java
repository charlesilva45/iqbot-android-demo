package com.iqbot.demo;

import android.app.Activity;
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

import java.io.IOException;
import java.io.InputStream;
import java.util.List;
import java.util.Locale;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public final class BacktestActivity extends Activity {
    private static final int PICK_CSV = 1;
    private static final Locale PT_BR = Locale.forLanguageTag("pt-BR");
    private final ExecutorService worker = Executors.newSingleThreadExecutor();
    private List<StrategyEngine.Candle> candles = OfflineBacktest.sample();
    private TextView source, status, result;
    private EditText stake, maxLoss, payout, maxTrades;
    private Button importCsv, sample, run;

    @Override public void onCreate(Bundle savedState) {
        super.onCreate(savedState);
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
        label(content, "SIMULAÇÃO OFFLINE · SEM ORDENS REAIS. Apenas sinais CALL em tendência de alta; sem PUT. Saldo inicial fictício de R$ 100. Entrada na abertura do candle M5 seguinte ao sinal e liquidação no fechamento. Empates contam como perdas. Resultados históricos não garantem ganhos.", 15);
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
            candles = OfflineBacktest.sample();
            showSource("Série sintética");
        });
        stake = field(content, "Valor fixo por operação (R$)", "2", true);
        maxLoss = field(content, "Limite de perda sobre o saldo inicial (R$)", "10", true);
        payout = field(content, "Retorno líquido em caso de acerto (%)", "80", true);
        maxTrades = field(content, "Máximo de operações", "10", false);
        run = button(content, "Executar backtest");
        run.setOnClickListener(v -> runBacktest());
        status = label(content, "", 15);
        status.setAccessibilityLiveRegion(TextView.ACCESSIBILITY_LIVE_REGION_POLITE);
        result = label(content, "", 16);
        showSource("Série sintética");
    }

    private void showSource(String name) {
        source.setText(String.format(PT_BR, "%s · %d candles M5", name, candles.size()));
        result.setText("");
        status.setText("");
    }

    @Override protected void onActivityResult(int request, int code, Intent data) {
        super.onActivityResult(request, code, data);
        if (request != PICK_CSV || code != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        busy(true, "Lendo CSV…");
        worker.execute(() -> {
            try (InputStream input = getContentResolver().openInputStream(uri)) {
                if (input == null) throw new IOException("Arquivo indisponível.");
                List<StrategyEngine.Candle> imported = OfflineBacktest.readCsv(input);
                runOnUiThread(() -> {
                    if (isDestroyed() || isFinishing()) return;
                    candles = imported;
                    busy(false, "");
                    showSource("CSV importado");
                });
            } catch (IOException | SecurityException e) {
                finishWork("CSV rejeitado. Confira o formato, os candles M5 consecutivos e o limite de 2 MiB / 10.000 candles. " + e.getMessage(), "");
            }
        });
    }

    private void runBacktest() {
        result.setText("");
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

        List<StrategyEngine.Candle> input = candles;
        busy(true, "Executando backtest…");
        worker.execute(() -> {
            try {
                StrategyEngine.Report report = StrategyEngine.backtestWithRiskFloor(
                        input, 100, amount, rate / 100, 100 - loss, limit);
                StrategyEngine.Indicators indicators = StrategyEngine.indicators(input);
                // Diagnostic signal from the final CLOSED M5 bar; never sends an order.
                StrategySelector.Recommendation recommendation =
                        StrategySelector.recommend(input, input.size() - 1);
                String signal = recommendation.selected.signal == StrategyEngine.Signal.CALL
                        ? "CALL (apenas estudo; nenhuma ordem)"
                        : "WAIT (sem entrada)";

                int total = report.trades.size();
                String accuracy = total == 0 ? "—" : String.format(PT_BR, "%.1f%%", 100.0 * report.wins / total);
                String output = String.format(PT_BR,
                        "Saldo final: R$ %.2f\nResultado: R$ %+.2f\nQueda máxima desde o pico: R$ %.2f\n"
                                + "Operações: %d · Acertos: %d · Perdas: %d\nEmpates incluídos nas perdas: %d\nTaxa de acerto: %s\n\n"
                                + "Recomendação CALL/WAIT: %s\nÚltimo candle M5 fechado\nEMA 9: %.6g · EMA 21: %.6g\nRSI 14: %.2f · ATR 14: %.6g",
                        report.finalBalance, report.finalBalance - 100, report.maxDrawdown,
                        total, report.wins, report.losses, report.ties, accuracy, signal,
                        indicators.ema9, indicators.ema21, indicators.rsi14, indicators.atr14);
                String message = amount > loss ? "Valor por operação excede a perda disponível. Nenhuma entrada autorizada."
                        : total == 0 ? "Nenhuma entrada simulada nas condições selecionadas." : "Backtest concluído.";
                finishWork(message, output);
            } catch (IllegalArgumentException e) {
                finishWork("Não foi possível executar o backtest com esses dados.", "");
            }
        });
    }

    private void finishWork(String message, String output) {
        runOnUiThread(() -> {
            if (isDestroyed() || isFinishing()) return;
            busy(false, message);
            result.setText(output);
        });
    }

    private void busy(boolean busy, String message) {
        importCsv.setEnabled(!busy);
        sample.setEnabled(!busy);
        run.setEnabled(!busy);
        status.setText(message);
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

    @Override protected void onDestroy() {
        worker.shutdownNow();
        super.onDestroy();
    }
}
