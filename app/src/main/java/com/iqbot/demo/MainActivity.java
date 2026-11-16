package com.iqbot.demo;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.net.Uri;
import android.widget.Toast;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.graphics.drawable.GradientDrawable;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private final Random random = new Random();
    private final ArrayList<Double> equity = new ArrayList<>();
    private double balance = 100.0;
    private int operations = 0, wins = 0;
    private TextView summary, history;
    private EquityChart chart;
    private final StringBuilder log = new StringBuilder();
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        equity.add(balance);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(dp(20), dp(30), dp(20), dp(28));
        root.setBackgroundColor(Color.rgb(9, 15, 29));
        scroll.addView(root);
        TextView brand = label("IQBOT   /   LAB", 14, Color.rgb(103, 232, 249));
        brand.setTypeface(null, android.graphics.Typeface.BOLD);
        root.addView(brand);
        TextView title = label("Painel de simulação", 28, Color.WHITE);
        title.setTypeface(null, android.graphics.Typeface.BOLD);
        title.setPadding(0, dp(8), 0, dp(8));
        root.addView(title);
        TextView badge = label("●  MODO DEMONSTRAÇÃO  •  SEM ORDENS REAIS", 11, Color.rgb(165, 243, 209));
        badge.setPadding(dp(12), dp(10), dp(12), dp(10));
        badge.setBackground(surface(Color.rgb(17, 54, 51), 14));
        root.addView(badge);
        Button backtest = actionButton("Executar estratégia CALL/WAIT (offline)", Color.rgb(8, 145, 178), Color.WHITE);
        root.addView(backtest, buttonParams());
        TextView strategyNote = label("Este é o motor de sinais CALL/WAIT com candles M5/M15 fechados. Importe CSV para avaliar dados históricos; nenhuma ordem é enviada.", 12, Color.rgb(148, 163, 184));
        strategyNote.setPadding(0, dp(8), 0, dp(4));
        root.addView(strategyNote);
        backtest.setOnClickListener(v -> startActivity(new Intent(this, BacktestActivity.class)));
        // This panel uses the same selector as the offline backtest, not the random demo.
        // The bundled candles are synthetic and must never be presented as live quotes.
        java.util.List<StrategyEngine.Candle> previewCandles = OfflineBacktest.sample();
        StrategySelector.Recommendation preview =
                StrategySelector.recommend(previewCandles, previewCandles.size() - 1);
        String previewSignal = preview.selected.signal == StrategyEngine.Signal.CALL ? "CALL" : "WAIT";
        TextView previewLabel = label(
                "SINAL DO MOTOR: " + previewSignal
                        + " · série SINTÉTICA, último M5 fechado · apenas estudo, sem ordem",
                13, Color.rgb(165, 243, 209));
        previewLabel.setPadding(0, dp(10), 0, dp(10));
        root.addView(previewLabel);

        LinearLayout balanceCard = new LinearLayout(this);
        balanceCard.setOrientation(LinearLayout.VERTICAL);
        balanceCard.setPadding(dp(18), dp(18), dp(18), dp(18));
        balanceCard.setBackground(surface(Color.rgb(22, 34, 55), 20));
        LinearLayout.LayoutParams cardSpacing = new LinearLayout.LayoutParams(-1, -2);
        cardSpacing.topMargin = dp(18);
        root.addView(balanceCard, cardSpacing);
        balanceCard.addView(label("CARTEIRA VIRTUAL", 12, Color.rgb(148, 163, 184)));
        summary = label("", 23, Color.WHITE);
        summary.setTypeface(null, android.graphics.Typeface.BOLD);
        summary.setPadding(0, dp(12), 0, dp(4));
        balanceCard.addView(summary);
        TextView chartTitle = label("Evolução do saldo fictício", 17, Color.WHITE);
        chartTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        chartTitle.setPadding(0, dp(24), 0, dp(12));
        root.addView(chartTitle);
        chart = new EquityChart();
        chart.setBackground(surface(Color.rgb(22, 34, 55), 20));
        root.addView(chart, new LinearLayout.LayoutParams(-1, dp(220)));
        TextView actions = label("DEMONSTRAÇÃO ALEATÓRIA (NÃO É A ESTRATÉGIA)", 12, Color.rgb(148, 163, 184));
        actions.setPadding(0, dp(24), 0, dp(8));
        root.addView(actions);
        Button single = actionButton("Simular operação aleatória", Color.rgb(8, 145, 178), Color.WHITE);
        root.addView(single, buttonParams());
        single.setOnClickListener(v -> simulate());
        Button reset = actionButton("Reiniciar dados fictícios", Color.rgb(30, 41, 59), Color.rgb(203, 213, 225));
        root.addView(reset, buttonParams());
        reset.setOnClickListener(v -> new AlertDialog.Builder(this)
            .setTitle("Reiniciar simulação?")
            .setMessage("O saldo e o histórico fictícios desta sessão serão apagados. Nenhuma ordem real será afetada.")
            .setNegativeButton("Cancelar", (dialog, which) -> dialog.dismiss())
            .setPositiveButton("Reiniciar", (dialog, which) -> reset())
            .show());
        TextView accessTitle = label("ACESSO À CORRETORA", 12, Color.rgb(148, 163, 184));
        accessTitle.setPadding(0, dp(24), 0, dp(8));
        root.addView(accessTitle);
        Button brokerAccess = actionButton("Abrir site da IQ Option", Color.rgb(30, 41, 59), Color.WHITE);
        root.addView(brokerAccess, buttonParams());
        brokerAccess.setOnClickListener(v -> {
            // External browser only: no OAuth tokens, passwords, cookies or broker API in IQBot.
            Intent browser = new Intent(Intent.ACTION_VIEW, Uri.parse("https://iqoption.com/"));
            try {
                startActivity(browser);
            } catch (android.content.ActivityNotFoundException ex) {
                Toast.makeText(this, "Nenhum navegador disponível.", Toast.LENGTH_LONG).show();
            }
        });
        TextView brokerNote = label("O login, inclusive com Google se oferecido pela corretora, acontece somente no site oficial. Abrir o site não conecta sua conta ao IQBot.", 12, Color.rgb(148, 163, 184));
        brokerNote.setPadding(0, dp(8), 0, dp(4));
        root.addView(brokerNote);
        TextView historyTitle = label("Histórico de operações", 17, Color.WHITE);
        historyTitle.setTypeface(null, android.graphics.Typeface.BOLD);
        historyTitle.setPadding(0, dp(22), 0, dp(10));
        root.addView(historyTitle);
        history = label("Nenhuma operação ainda.", 14, Color.rgb(203, 213, 225));
        history.setPadding(dp(16), dp(16), dp(16), dp(16));
        history.setBackground(surface(Color.rgb(22, 34, 55), 18));
        root.addView(history);
        TextView disclaimer = label("SIMULAÇÃO LOCAL: resultados aleatórios, sem dados de mercado. Cada operação fictícia usa R$ 2, com retorno de R$ 1,60 em caso de acerto e perda de R$ 2 em caso de erro. Limite de 10 operações ou saldo de R$ 90. Não há integração com corretoras.", 12, Color.rgb(148, 163, 184));
        disclaimer.setPadding(0, dp(20), 0, dp(10));
        root.addView(disclaimer);
        setContentView(scroll);
        refresh();
    }
    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    private int dp(int value) {
        return (int)(getResources().getDisplayMetrics().density * value + 0.5f);
    }
    private GradientDrawable surface(int color, int radius) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(radius));
        return shape;
    }
    private Button actionButton(String text, int background, int foreground) {
        Button button = new Button(this);
        button.setText(text);
        button.setAllCaps(false);
        button.setTextColor(foreground);
        button.setTextSize(14);
        button.setBackground(surface(background, 14));
        button.setMinHeight(dp(52));
        return button;
    }
    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(-1, dp(52));
        params.topMargin = dp(10);
        return params;
    }
    private void simulate() {
        if (operations >= 10 || balance < 2 || balance - 2 < 90) return;
        boolean win = random.nextBoolean();
        balance += win ? 1.60 : -2.00;
        operations++;
        if (win) wins++;
        equity.add(balance);
        log.insert(0, String.format(Locale.forLanguageTag("pt-BR"), "#%d  %s  Saldo R$ %.2f\n", operations, win ? "+R$ 1,60" : "-R$ 2,00", balance));
        refresh();
    }
    private void refresh() {
        summary.setText(String.format(Locale.forLanguageTag("pt-BR"), "R$ %.2f\n%d operações   •   %d vitórias\nResultado: %+.2f R$", balance, operations, wins, balance - 100));
        history.setText(log.length() == 0 ? "Nenhuma operação ainda." : log.toString());
        chart.invalidate();
    }
    private void reset() {
        balance = 100; operations = 0; wins = 0;
        log.setLength(0); equity.clear(); equity.add(balance); refresh();
    }
    private class EquityChart extends View {
        final Paint p = new Paint(3);
        EquityChart() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), pad = 24;
            // Parent view provides the rounded card background.
            p.setColor(Color.rgb(100,116,139)); p.setStrokeWidth(2);
            canvas.drawLine(pad, h/2, w-pad, h/2, p);
            if (equity.size() < 2) return;
            double min = 90, max = 110;
            for (double v : equity) { min = Math.min(min,v); max = Math.max(max,v); }
            p.setColor(Color.rgb(56,189,248)); p.setStrokeWidth(5);
            for (int i=1; i<equity.size(); i++) {
                float x1 = pad + (w-2*pad)*(i-1)/(equity.size()-1);
                float x2 = pad + (w-2*pad)*i/(equity.size()-1);
                float y1 = h-pad-(float)((equity.get(i-1)-min)/(max-min))*(h-2*pad);
                float y2 = h-pad-(float)((equity.get(i)-min)/(max-min))*(h-2*pad);
                canvas.drawLine(x1,y1,x2,y2,p);
            }
        }
    }
}
