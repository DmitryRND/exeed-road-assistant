package com.example.hudvhalrecovery;

import android.app.Activity;
import android.content.ComponentName;
import android.content.Context;
import android.content.ServiceConnection;
import android.graphics.Color;
import android.os.Bundle;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import java.lang.reflect.Method;

/** Reads and explicitly restores TXL2 HUD state property 557847263. */
public final class MainActivity extends Activity {
    private static final String TAG = "HudVhalRecovery";
    private static final int PROP_HUD_STATE = 557847263;

    private final Handler handler = new Handler(Looper.getMainLooper());
    private Object car;
    private Object vendorManager;
    private TextView valueView;
    private TextView logView;
    private Button refreshButton;
    private Button restoreButton;

    private final ServiceConnection carConnection = new ServiceConnection() {
        @Override public void onServiceConnected(ComponentName name, IBinder service) {
            append("CarService подключён: " + name.flattenToShortString());
            try {
                Method getCarManager = car.getClass().getMethod("getCarManager", String.class);
                vendorManager = getCarManager.invoke(car, "vendor_extension");
                if (vendorManager == null) {
                    throw new IllegalStateException("vendor_extension manager is null");
                }
                refreshButton.setEnabled(true);
                restoreButton.setEnabled(true);
                readHudState("Текущее значение");
            } catch (Throwable error) {
                vendorManager = null;
                appendFailure("Не удалось открыть vendor_extension", error);
            }
        }

        @Override public void onServiceDisconnected(ComponentName name) {
            vendorManager = null;
            refreshButton.setEnabled(false);
            restoreButton.setEnabled(false);
            valueView.setText("CarService отключён");
            append("CarService отключён");
        }
    };

    @Override protected void onCreate(Bundle state) {
        super.onCreate(state);
        buildUi();
        connectToCarService();
    }

    @Override protected void onDestroy() {
        handler.removeCallbacksAndMessages(null);
        if (car != null) {
            try {
                car.getClass().getMethod("disconnect").invoke(car);
            } catch (Throwable error) {
                Log.w(TAG, "CarService disconnect failed", error);
            }
        }
        car = null;
        vendorManager = null;
        super.onDestroy();
    }

    private void buildUi() {
        int pad = dp(20);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setPadding(pad, pad, pad, pad);
        root.setBackgroundColor(Color.rgb(245, 244, 240));

        TextView title = new TextView(this);
        title.setText("Восстановление HUD");
        title.setTextSize(28f);
        title.setTextColor(Color.rgb(25, 25, 24));
        root.addView(title, matchWrap());

        TextView explanation = new TextView(this);
        explanation.setText("Утилита работает только со свойством VHAL 557847263. "
                + "Сначала она читает текущее значение; запись 1 выполняется только по кнопке.");
        explanation.setTextSize(18f);
        explanation.setTextColor(Color.rgb(65, 64, 60));
        explanation.setPadding(0, dp(12), 0, dp(18));
        root.addView(explanation, matchWrap());

        valueView = new TextView(this);
        valueView.setText("Подключение к CarService…");
        valueView.setTextSize(25f);
        valueView.setTextColor(Color.rgb(133, 82, 45));
        valueView.setGravity(Gravity.CENTER);
        valueView.setPadding(dp(12), dp(18), dp(12), dp(18));
        root.addView(valueView, matchWrap());

        refreshButton = button("Прочитать текущее значение");
        refreshButton.setEnabled(false);
        refreshButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { readHudState("Текущее значение"); }
        });
        root.addView(refreshButton, buttonParams());

        restoreButton = button("Вернуть значение 1 и проверить");
        restoreButton.setEnabled(false);
        restoreButton.setOnClickListener(new View.OnClickListener() {
            @Override public void onClick(View view) { restoreHudState(); }
        });
        root.addView(restoreButton, buttonParams());

        logView = new TextView(this);
        logView.setTextSize(15f);
        logView.setTextColor(Color.rgb(70, 70, 67));
        logView.setPadding(0, dp(12), 0, 0);
        root.addView(logView, matchWrap());

        ScrollView scroll = new ScrollView(this);
        scroll.addView(root);
        setContentView(scroll);
    }

    private void connectToCarService() {
        try {
            Class<?> carClass = Class.forName("android.car.Car");
            Method createCar = carClass.getMethod(
                    "createCar", Context.class, ServiceConnection.class);
            car = createCar.invoke(null, this, carConnection);
            carClass.getMethod("connect").invoke(car);
            append("Подключение к CarService запрошено");
        } catch (Throwable error) {
            car = null;
            appendFailure("Не удалось подключиться к CarService", error);
        }
    }

    private void readHudState(String prefix) {
        if (vendorManager == null) {
            valueView.setText("vendor_extension не подключён");
            return;
        }
        try {
            Method getProperty = vendorManager.getClass().getMethod(
                    "getProperty", Class.class, int.class, int.class);
            Object result = getProperty.invoke(
                    vendorManager, Integer.class, PROP_HUD_STATE, 0);
            int value = extractInteger(result);
            valueView.setText(prefix + ": " + value);
            append(prefix + " свойства " + PROP_HUD_STATE + " = " + value);
        } catch (Throwable error) {
            valueView.setText("Ошибка чтения свойства");
            appendFailure("Чтение " + PROP_HUD_STATE + " не удалось", error);
        }
    }

    private int extractInteger(Object result) throws Exception {
        if (result instanceof Integer) return ((Integer) result).intValue();
        if (result == null) throw new IllegalStateException("VHAL returned null");
        Object nested = result.getClass().getMethod("getValue").invoke(result);
        if (nested instanceof Integer) return ((Integer) nested).intValue();
        throw new IllegalStateException("Unexpected VHAL value: " + result);
    }

    private void restoreHudState() {
        if (vendorManager == null) return;
        restoreButton.setEnabled(false);
        readHudState("До восстановления");
        writeHudStateOne(0L);
        writeHudStateOne(120L);
        writeHudStateOne(280L);
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                readHudState("После восстановления");
                restoreButton.setEnabled(vendorManager != null);
            }
        }, 700L);
    }

    private void writeHudStateOne(long delayMs) {
        handler.postDelayed(new Runnable() {
            @Override public void run() {
                Object manager = vendorManager;
                if (manager == null) return;
                try {
                    Method setProperty = manager.getClass().getMethod(
                            "setProperty", Class.class, int.class, int.class, Object.class);
                    setProperty.invoke(manager, Integer.class,
                            PROP_HUD_STATE, 0, Integer.valueOf(1));
                    append("Запись " + PROP_HUD_STATE + " = 1 выполнена");
                } catch (Throwable error) {
                    appendFailure("Запись " + PROP_HUD_STATE + " = 1 не удалась", error);
                }
            }
        }, delayMs);
    }

    private Button button(String text) {
        Button button = new Button(this);
        button.setText(text);
        button.setTextSize(19f);
        button.setAllCaps(false);
        return button;
    }

    private LinearLayout.LayoutParams buttonParams() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, dp(66));
        params.setMargins(0, 0, 0, dp(12));
        return params;
    }

    private LinearLayout.LayoutParams matchWrap() {
        return new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.WRAP_CONTENT);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }

    private void append(String message) {
        Log.i(TAG, message);
        if (logView != null) {
            CharSequence old = logView.getText();
            logView.setText((old.length() == 0 ? "" : old + "\n") + message);
        }
    }

    private void appendFailure(String prefix, Throwable error) {
        Throwable cause = error;
        while (cause.getCause() != null) cause = cause.getCause();
        Log.e(TAG, prefix, cause);
        append(prefix + ": " + cause.getClass().getSimpleName()
                + (cause.getMessage() == null ? "" : " — " + cause.getMessage()));
    }
}
