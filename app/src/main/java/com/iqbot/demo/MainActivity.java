package com.iqbot.demo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.os.Handler;
import android.os.Looper;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import java.util.ArrayList;
import java.util.Locale;
import java.util.Random;

public class MainActivity extends Activity {
    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Random random = new Random();
    private final ArrayList<Double> equity = new ArrayList<>();
    private double balance = 100.0;
    private int operations = 0, wins = 0;
    private boolean automatic = false;
    private TextView summary, history;
    private Button autoButton;
    private EquityChart chart;
    private final StringBuilder log = new StringBuilder();
    private final Runnable autoTick = new Runnable() {
        @Override public void run() {
            if (!automatic) return;
            simulate();
            if (operations >= 10 || balance < 2 || balance <= 90) {
                stopAuto();
            } else handler.postDelayed(this, 1200);
        }
    };
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        equity.add(balance);
        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(32, 44, 32, 32);
        root.setBackgroundColor(Color.rgb(15, 23, 42));
        scroll.addView(root);
        TextView title = label("IQBot 0.2 • DEMO", 26, Color.WHITE);
        root.addView(title);
        root.addView(label("SIMULAÇÃO LOCAL • nenhuma ordem real", 13, Color.rgb(148,163,184)));
        summary = label("", 20, Color.WHITE);
        summary.setPadding(0, 28, 0, 18);
        root.addView(summary);
        chart = new EquityChart();
        root.addView(chart, new LinearLayout.LayoutParams(-1, 230));
        Button single = new Button(this);
        single.setText("Simular operação de R$ 2");
        root.addView(single);
        single.setOnClickListener(v -> { if (!automatic) simulate(); });
        autoButton = new Button(this);
        autoButton.setText("Iniciar 10 simulações automáticas");
        root.addView(autoButton);
        autoButton.setOnClickListener(v -> {
            if (automatic) stopAuto();
            else {
                if (operations >= 10) { reset(); }
                automatic = true;
                autoButton.setText("Parar simulação");
                handler.post(autoTick);
            }
        });
        Button reset = new Button(this);
        reset.setText("Reiniciar saldo e histórico");
        root.addView(reset);
        reset.setOnClickListener(v -> reset());
        root.addView(label("Histórico de simulações", 18, Color.WHITE));
        history = label("Nenhuma operação ainda.", 15, Color.rgb(203,213,225));
        history.setPadding(0, 12, 0, 20);
        root.addView(history);
        root.addView(label("Resultados aleatórios (50% de chance). Ganho fictício +R$ 1,60; perda fictícia -R$ 2,00. Limite automático: 10 operações ou saldo de R$ 90. Não utiliza preços de mercado nem API da IQ Option.", 13, Color.rgb(148,163,184)));
        setContentView(scroll);
        refresh();
    }
    private TextView label(String text, int size, int color) {
        TextView t = new TextView(this);
        t.setText(text); t.setTextSize(size); t.setTextColor(color);
        return t;
    }
    private void simulate() {
        if (balance < 2 || balance <= 90) { stopAuto(); return; }
        boolean win = random.nextBoolean();
        balance += win ? 1.60 : -2.00;
        operations++;
        if (win) wins++;
        equity.add(balance);
        log.insert(0, String.format(Locale.forLanguageTag("pt-BR"), "#%d  %s  Saldo R$ %.2f\n", operations, win ? "+R$ 1,60" : "-R$ 2,00", balance));
        refresh();
    }
    private void refresh() {
        summary.setText(String.format(Locale.forLanguageTag("pt-BR"), "Saldo fictício: R$ %.2f\nOperações: %d  •  Vitórias: %d\nResultado: R$ %.2f", balance, operations, wins, balance - 100));
        history.setText(log.length() == 0 ? "Nenhuma operação ainda." : log.toString());
        chart.invalidate();
    }
    private void stopAuto() {
        automatic = false;
        handler.removeCallbacks(autoTick);
        if (autoButton != null) autoButton.setText("Iniciar 10 simulações automáticas");
    }
    private void reset() {
        stopAuto(); balance = 100; operations = 0; wins = 0;
        log.setLength(0); equity.clear(); equity.add(balance); refresh();
    }
    @Override protected void onDestroy() {
        stopAuto(); super.onDestroy();
    }
    private class EquityChart extends View {
        final Paint p = new Paint(3);
        EquityChart() { super(MainActivity.this); }
        @Override protected void onDraw(Canvas canvas) {
            super.onDraw(canvas);
            float w = getWidth(), h = getHeight(), pad = 24;
            canvas.drawColor(Color.rgb(30,41,59));
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
