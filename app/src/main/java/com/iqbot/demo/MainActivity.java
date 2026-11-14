package com.iqbot.demo;

import android.app.Activity;
import android.os.Bundle;
import android.graphics.Color;
import android.view.Gravity;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;
import java.util.Random;

public class MainActivity extends Activity {
    private double balance = 100.0;
    private final Random random = new Random();
    private TextView status;
    @Override public void onCreate(Bundle state) {
        super.onCreate(state);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(40, 60, 40, 40);
        layout.setBackgroundColor(Color.rgb(245,247,250));
        TextView title = new TextView(this);
        title.setText("IQBot • Simulador");
        title.setTextSize(25);
        title.setTextColor(Color.BLACK);
        layout.addView(title);
        status = new TextView(this);
        status.setTextSize(18);
        status.setPadding(0,30,0,30);
        layout.addView(status);
        Button button = new Button(this);
        button.setText("Simular uma operação de R$ 2");
        layout.addView(button);
        button.setOnClickListener(v -> {
            if (balance < 2) { status.setText("Saldo insuficiente. Simulação encerrada."); return; }
            boolean win = random.nextBoolean();
            balance += win ? 1.6 : -2.0;
            update(win ? "Resultado fictício: +R$ 1,60" : "Resultado fictício: -R$ 2,00");
        });
        TextView warning = new TextView(this);
        warning.setText("DEMONSTRAÇÃO LOCAL — sem conexão com corretora, sem ordens reais. Resultados aleatórios não representam mercado.");
        warning.setPadding(0,35,0,0);
        layout.addView(warning);
        update("Pronto");
        setContentView(layout);
    }
    private void update(String message) {
        status.setText(message + "\nSaldo fictício: R$ " + String.format(java.util.Locale.forLanguageTag("pt-BR"), "%.2f", balance));
    }
}
