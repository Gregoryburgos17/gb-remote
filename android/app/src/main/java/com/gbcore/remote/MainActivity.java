package com.gbcore.remote;

import android.app.Activity;
import android.app.AlertDialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.text.InputType;
import android.util.TypedValue;
import android.view.Gravity;
import android.view.View;
import android.view.inputmethod.EditorInfo;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;

import org.json.JSONObject;

/**
 * One screen, big letters, one field: the user writes the ID of the company, the phone
 * shows its device code and waits until support accepts it in GB Core
 * (Monitoring > Devices (GB Remote)); then "Abrir mi sistema" opens the system.
 */
public class MainActivity extends Activity {
    private static final int POLL_MS = 10000;
    private static final int TEXT = Color.WHITE;
    private static final int ERROR = Color.rgb(252, 165, 165);
    private static final int OK = Color.rgb(134, 239, 172);

    private final Handler handler = new Handler(Looper.getMainLooper());
    private final Runnable poll = new Runnable() {
        @Override
        public void run() {
            checkStatus();
        }
    };

    private Store store;
    private TextView message;
    private TextView code;
    private EditText company;
    private Button connect;
    private Button open;
    private String companyUrl = "";
    private boolean polling;

    @Override
    protected void onCreate(Bundle state) {
        super.onCreate(state);
        store = new Store(this);
        getWindow().setStatusBarColor(Color.rgb(15, 23, 42));

        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);
        layout.setPadding(dp(24), dp(48), dp(24), dp(24));

        TextView title = text("GB Remote", 36);
        title.setTypeface(Typeface.DEFAULT_BOLD);
        // long press on the title: change the address of the platform (for support only)
        title.setOnLongClickListener(new View.OnLongClickListener() {
            @Override
            public boolean onLongClick(View view) {
                askServer();
                return true;
            }
        });
        layout.addView(title);

        message = text("", 20);
        message.setPadding(0, dp(24), 0, dp(24));
        layout.addView(message);

        company = new EditText(this);
        company.setTextSize(TypedValue.COMPLEX_UNIT_SP, 24);
        company.setGravity(Gravity.CENTER);
        company.setSingleLine(true);
        company.setHint("farmacia-moma");
        company.setTextColor(Color.rgb(15, 23, 42));
        company.setHintTextColor(Color.rgb(148, 163, 184));
        company.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_FLAG_NO_SUGGESTIONS);
        company.setImeOptions(EditorInfo.IME_ACTION_GO);
        company.setBackground(rounded(Color.WHITE));
        company.setPadding(dp(12), dp(14), dp(12), dp(14));
        company.setOnEditorActionListener(new TextView.OnEditorActionListener() {
            @Override
            public boolean onEditorAction(TextView view, int action, android.view.KeyEvent event) {
                register();
                return true;
            }
        });
        layout.addView(company, wide());

        code = text("", 44);
        code.setTypeface(Typeface.MONOSPACE, Typeface.BOLD);
        code.setTextColor(Color.rgb(96, 165, 250));
        layout.addView(code);

        connect = button("Conectar", Color.rgb(37, 99, 235));
        connect.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                register();
            }
        });
        layout.addView(connect, wide());

        open = button("Abrir mi sistema", Color.rgb(22, 163, 74));
        open.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                openSystem();
            }
        });
        layout.addView(open, wide());

        ScrollView scroll = new ScrollView(this);
        scroll.setFillViewport(true);
        scroll.setFitsSystemWindows(true);  // Android 15 draws behind the system bars
        scroll.setBackgroundColor(Color.rgb(15, 23, 42));
        scroll.addView(layout);
        setContentView(scroll);
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (store.deviceCode().isEmpty()) {
            showAskCompany(null);
        } else {
            showWaiting();
            checkStatus();
        }
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopPolling();
    }

    // ---------------------------------------------------------------- screens

    private void showAskCompany(String error) {
        stopPolling();
        message.setText(error != null ? error : "Escriba el ID de su empresa\n(se lo da GB, por ejemplo: farmacia-moma)");
        message.setTextColor(error != null ? ERROR : TEXT);
        if (company.getText().length() == 0) {
            company.setText(store.company());
        }
        show(company, true);
        show(connect, true);
        show(code, false);
        show(open, false);
        connect.setEnabled(true);
    }

    private void showWaiting() {
        message.setText("Esperando que GB apruebe este teléfono.\nSi le llaman, diga este código:");
        message.setTextColor(TEXT);
        code.setText(formatCode(store.deviceCode()));
        show(code, true);
        show(company, false);
        show(connect, false);
        show(open, false);
        startPolling();
    }

    private void showApproved(String companyName) {
        stopPolling();
        message.setText("Conectado a " + companyName);
        message.setTextColor(OK);
        code.setText(formatCode(store.deviceCode()));
        show(code, true);
        show(open, !companyUrl.isEmpty());
        show(company, false);
        show(connect, false);
    }

    // ---------------------------------------------------------------- calls

    private void register() {
        if (!connect.isEnabled()) {
            return;  // already asking (Enter pressed twice)
        }
        final String ref = company.getText().toString().trim();
        if (ref.isEmpty()) {
            showAskCompany("Escriba el ID de su empresa.");
            return;
        }
        connect.setEnabled(false);
        message.setText("Conectando…");
        message.setTextColor(TEXT);
        call("register", new Body() {
            @Override
            public JSONObject build() throws Exception {
                if (!Api.isGb(store.server())) {
                    throw new Api.NotGbServer();  // a wrong address never receives the details
                }
                return new JSONObject()
                        .put("company", ref)
                        .put("computer", DeviceInfo.name())
                        .put("secret", store.secret())
                        .put("info", DeviceInfo.collect(MainActivity.this));
            }
        }, new Answer() {
            @Override
            public void ok(JSONObject answer) {
                if (answer.has("error")) {
                    showAskCompany("company_not_found".equals(answer.optString("error"))
                            ? "No encontramos esa empresa. Revise el ID o llame a GB."
                            : "GB no aceptó la solicitud (" + answer.optString("error") + ").");
                    return;
                }
                store.setDevice(ref, answer.optString("device_code"));
                apply(answer);
            }

            @Override
            public void failed(Exception error) {
                showAskCompany(error instanceof Api.NotGbServer
                        ? "La dirección configurada no es de GB.\nLlame a soporte (no se envió nada)."
                        : "No hay conexión con GB. Revise el internet.");
            }
        });
    }

    private void checkStatus() {
        call("status", new Body() {
            @Override
            public JSONObject build() throws Exception {
                return new JSONObject()
                        .put("device_code", store.deviceCode())
                        .put("secret", store.secret());
            }
        }, new Answer() {
            @Override
            public void ok(JSONObject answer) {
                if (answer.has("error")) {
                    if ("too_many_requests".equals(answer.optString("error"))) {
                        return;  // keep waiting, the next poll tries again
                    }
                    store.forgetDevice();
                    showAskCompany(null);
                    return;
                }
                apply(answer);
            }

            @Override
            public void failed(Exception error) {
                message.setText("Sin internet. Se reintenta solo…");
            }
        });
    }

    private void apply(JSONObject answer) {
        String state = answer.optString("state");
        if ("approved".equals(state)) {
            companyUrl = answer.optString("url", "");
            showApproved(answer.optString("company", "GB"));
        } else if ("rejected".equals(state) || "revoked".equals(state)) {
            store.forgetDevice();
            showAskCompany("rejected".equals(state)
                    ? "GB no aprobó este teléfono. Llame a soporte."
                    : "Este teléfono fue desconectado. Llame a soporte.");
        } else {
            showWaiting();
        }
    }

    private void openSystem() {
        if (companyUrl.isEmpty()) {
            return;
        }
        Intent intent = new Intent(this, WebActivity.class);
        intent.putExtra(WebActivity.EXTRA_URL, companyUrl);
        startActivity(intent);
    }

    private void askServer() {
        final EditText input = new EditText(this);
        input.setSingleLine(true);
        input.setInputType(InputType.TYPE_CLASS_TEXT | InputType.TYPE_TEXT_VARIATION_URI);
        input.setText(store.server());
        new AlertDialog.Builder(this)
                .setTitle("Dirección de la plataforma")
                .setView(input)
                .setPositiveButton("Guardar", new DialogInterface.OnClickListener() {
                    @Override
                    public void onClick(DialogInterface dialog, int which) {
                        String value = input.getText().toString().trim();
                        if (!value.isEmpty()) {
                            store.setServer(value);
                        }
                    }
                })
                .setNegativeButton("Cancelar", null)
                .show();
    }

    // ---------------------------------------------------------------- plumbing

    private interface Body {
        JSONObject build() throws Exception;
    }

    private interface Answer {
        void ok(JSONObject answer);

        void failed(Exception error);
    }

    /** Network outside the main thread, answer back on it. */
    private void call(final String path, final Body body, final Answer answer) {
        final String server = store.server();
        new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    final JSONObject result = Api.post(server, path, body.build());
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            answer.ok(result);
                        }
                    });
                } catch (final Exception error) {
                    handler.post(new Runnable() {
                        @Override
                        public void run() {
                            answer.failed(error);
                        }
                    });
                }
            }
        }).start();
    }

    private void startPolling() {
        if (!polling) {
            polling = true;
            handler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    if (polling) {
                        poll.run();
                        handler.postDelayed(this, POLL_MS);
                    }
                }
            }, POLL_MS);
        }
    }

    private void stopPolling() {
        polling = false;
        handler.removeCallbacksAndMessages(null);
    }

    private static String formatCode(String value) {
        return value.length() == 9 ? value.substring(0, 3) + " " + value.substring(3, 6) + " " + value.substring(6) : value;
    }

    private TextView text(String value, int sp) {
        TextView view = new TextView(this);
        view.setText(value);
        view.setTextColor(TEXT);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, sp);
        view.setGravity(Gravity.CENTER);
        return view;
    }

    private Button button(String value, int color) {
        Button view = new Button(this);
        view.setText(value);
        view.setAllCaps(false);
        view.setTextColor(Color.WHITE);
        view.setTextSize(TypedValue.COMPLEX_UNIT_SP, 22);
        view.setTypeface(Typeface.DEFAULT_BOLD);
        view.setBackground(rounded(color));
        view.setPadding(dp(12), dp(16), dp(12), dp(16));
        return view;
    }

    private GradientDrawable rounded(int color) {
        GradientDrawable shape = new GradientDrawable();
        shape.setColor(color);
        shape.setCornerRadius(dp(12));
        return shape;
    }

    private LinearLayout.LayoutParams wide() {
        LinearLayout.LayoutParams params = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT);
        params.topMargin = dp(16);
        return params;
    }

    private static void show(View view, boolean visible) {
        view.setVisibility(visible ? View.VISIBLE : View.GONE);
    }

    private int dp(int value) {
        return Math.round(value * getResources().getDisplayMetrics().density);
    }
}
