package com.example;

import android.app.Notification;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.Service;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.graphics.PixelFormat;
import android.os.Build;
import android.os.Handler;
import android.os.IBinder;
import android.os.Looper;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.Gravity;
import android.view.LayoutInflater;
import android.view.MotionEvent;
import android.view.View;
import android.view.WindowManager;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.core.app.NotificationCompat;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import org.json.JSONArray;
import org.json.JSONObject;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.io.OutputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class FloatingService extends Service {

    public static volatile boolean isRunning = false;

    private static final int NOTIFICATION_ID = 2002;
    private static final String CHANNEL_ID = "FloatingSafiChannel";

    private WindowManager windowManager;
    private SharedPreferences prefs;
    private DatabaseHelper dbHelper;
    private ExecutorService threadExecutor;

    // Overlay Views
    private View bubbleView;
    private View menuView;
    private View chatView;
    private View calcView;
    private View notesView;

    // Overlay States
    private boolean isMenuShowing = false;
    private boolean isChatShowing = false;
    private boolean isCalcShowing = false;
    private boolean isNotesShowing = false;

    // Layout Params Config
    private WindowManager.LayoutParams bubbleParams;
    private WindowManager.LayoutParams menuParams;

    // Elements & Controllers: AI CHAT
    private RecyclerView rvChatHistory;
    private ChatAdapter chatAdapter;
    private List<ChatAdapter.ChatMessage> chatHistoryList;
    private TextView tvChatTypingIndicator;
    private EditText etChatMessage;

    // Elements & Controllers: CALCULATOR
    private TextView tvCalcExpression;
    private TextView tvCalcDisplay;
    private String currentCalcExpression = "";
    private String currentCalcDisplay = "0";
    private boolean isCalcOperatorInput = false;

    // Elements & Controllers: QUICK NOTES
    private RecyclerView rvNotesHistory;
    private NoteAdapter noteAdapter;
    private List<DatabaseHelper.NoteItem> noteList;
    private EditText etNoteSearch;
    private TextView tvNoNotes;
    
    // Notes Editor Fields
    private EditText etNoteTitleInput;
    private EditText etNoteContentInput;
    private CheckBox cbPinNote;
    private Button btnDeleteNote;
    private DatabaseHelper.NoteItem activeEditingNote = null;

    private void startForegroundSpecialUse() {
        createNotificationChannel();
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating SAFI")
                .setContentText("Multi-utility overlay hub is active.")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= 34) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE);
            } else {
                // Under API 34, a standard foreground service does not require declaring a specific type on startup
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    private void promoteToMediaProjection() {
        Notification notification = new NotificationCompat.Builder(this, CHANNEL_ID)
                .setContentTitle("Floating SAFI")
                .setContentText("Screen capture in progress...")
                .setSmallIcon(android.R.drawable.ic_dialog_info)
                .setPriority(NotificationCompat.PRIORITY_MIN)
                .build();
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                startForeground(NOTIFICATION_ID, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_MEDIA_PROJECTION);
            } else {
                startForeground(NOTIFICATION_ID, notification);
            }
        } catch (Exception e) {
            e.printStackTrace();
            try {
                startForeground(NOTIFICATION_ID, notification);
            } catch (Exception ex) {
                ex.printStackTrace();
            }
        }
    }

    @Override
    public void onCreate() {
        super.onCreate();
        isRunning = true;

        prefs = getSharedPreferences("FloatingSafiPrefs", MODE_PRIVATE);
        dbHelper = new DatabaseHelper(this);
        windowManager = (WindowManager) getSystemService(WINDOW_SERVICE);
        threadExecutor = Executors.newSingleThreadExecutor();

        // 1. Establish O+ Foreground notification channels
        startForegroundSpecialUse();

        // 2. Load and paint floating circular bubble overlay
        initFloatingBubble();
    }

    @Override
    public int onStartCommand(Intent intent, int flags, int startId) {
        if (intent != null) {
            String action = intent.getAction();
            if ("ACTION_CONFIG_CHANGED".equals(action)) {
                // Apply size/opacity on-the-fly
                updateBubbleVisualPreferences();
            } else if ("ACTION_START_PROJECTION".equals(action)) {
                promoteToMediaProjection();
            } else if ("ACTION_STOP_PROJECTION".equals(action)) {
                startForegroundSpecialUse();
            }
        }
        return START_NOT_STICKY;
    }

    private void createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            NotificationChannel serviceChannel = new NotificationChannel(
                    CHANNEL_ID,
                    "Floating SAFI Foreground Channel",
                    NotificationManager.IMPORTANCE_MIN
            );
            NotificationManager manager = getSystemService(NotificationManager.class);
            if (manager != null) {
                manager.createNotificationChannel(serviceChannel);
            }
        }
    }

    // ============================================
    // 1. FLOATING BUBBLE OVERLAY CONTROL
    // ============================================

    private void initFloatingBubble() {
        bubbleView = LayoutInflater.from(this).inflate(R.layout.layout_floating_bubble, null);

        int flagOverlay;
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            flagOverlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
        } else {
            flagOverlay = WindowManager.LayoutParams.TYPE_PHONE;
        }

        bubbleParams = new WindowManager.LayoutParams(
                WindowManager.LayoutParams.WRAP_CONTENT,
                WindowManager.LayoutParams.WRAP_CONTENT,
                flagOverlay,
                WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                PixelFormat.TRANSLUCENT
        );

        bubbleParams.gravity = Gravity.TOP | Gravity.START;
        bubbleParams.x = 200;
        bubbleParams.y = 400;

        try {
            windowManager.addView(bubbleView, bubbleParams);
            updateBubbleVisualPreferences();
            setupBubbleTouchListener();
        } catch (Exception e) {
            e.printStackTrace();
            Toast.makeText(this, "Failed to display floating bubble! Please ensure Overlay permission is granted.", Toast.LENGTH_LONG).show();
            stopSelf();
        }
    }

    private void updateBubbleVisualPreferences() {
        if (bubbleView == null) return;
        View innerBubble = bubbleView.findViewById(R.id.bubbleInnerContainer);
        if (innerBubble != null) {
            // Transparency Preference
            int opacityPercent = prefs.getInt("bubble_opacity", 80);
            float opacityFloat = opacityPercent / 100.0f;
            innerBubble.setAlpha(opacityFloat);

            // Size Preferences (0=Small, 1=Medium, 2=Large)
            int sizeType = prefs.getInt("bubble_size", 1);
            int dps = 56;
            if (sizeType == 0) dps = 44;
            if (sizeType == 2) dps = 68;

            int pixels = (int) (dps * getResources().getDisplayMetrics().density);
            innerBubble.getLayoutParams().width = pixels;
            innerBubble.getLayoutParams().height = pixels;
            innerBubble.requestLayout();
        }
    }

    private void setupBubbleTouchListener() {
        bubbleView.setOnTouchListener(new View.OnTouchListener() {
            private int initialX;
            private int initialY;
            private float initialTouchX;
            private float initialTouchY;
            private long touchStartTime;

            @Override
            public boolean onTouch(View v, MotionEvent event) {
                switch (event.getAction()) {
                    case MotionEvent.ACTION_DOWN:
                        initialX = bubbleParams.x;
                        initialY = bubbleParams.y;
                        initialTouchX = event.getRawX();
                        initialTouchY = event.getRawY();
                        touchStartTime = System.currentTimeMillis();
                        return true;

                    case MotionEvent.ACTION_MOVE:
                        bubbleParams.x = initialX + (int) (event.getRawX() - initialTouchX);
                        bubbleParams.y = initialY + (int) (event.getRawY() - initialTouchY);
                        windowManager.updateViewLayout(bubbleView, bubbleParams);
                        return true;

                    case MotionEvent.ACTION_UP:
                        long duration = System.currentTimeMillis() - touchStartTime;
                        float diffX = Math.abs(event.getRawX() - initialTouchX);
                        float diffY = Math.abs(event.getRawY() - initialTouchY);

                        if (duration < 200 && diffX < 10 && diffY < 10) {
                            // Real click: Toggle menu window
                            toggleMenuWindow();
                        } else {
                            // Snapping alignment animation to walls
                            animateSnapToEdge();
                        }
                        return true;
                }
                return false;
            }
        });
    }

    private void animateSnapToEdge() {
        int screenWidthPixels = getResources().getDisplayMetrics().widthPixels;
        int currentX = bubbleParams.x;
        int targetX;

        if (currentX < screenWidthPixels / 2) {
            targetX = 10;
        } else {
            int bubbleWidth = bubbleView.getWidth();
            targetX = screenWidthPixels - bubbleWidth - 10;
        }

        bubbleParams.x = targetX;
        windowManager.updateViewLayout(bubbleView, bubbleParams);
    }

    // ============================================
    // 2. CONTEXT SHORTCUTS MENU OVERLAY
    // ============================================

    private void toggleMenuWindow() {
        if (isMenuShowing) {
            hideMenuWindow();
        } else {
            showMenuWindow();
        }
    }

    private void showMenuWindow() {
        if (menuView == null) {
            menuView = LayoutInflater.from(this).inflate(R.layout.layout_floating_menu, null);

            int flagOverlay;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flagOverlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                flagOverlay = WindowManager.LayoutParams.TYPE_PHONE;
            }

            menuParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flagOverlay,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
                    PixelFormat.TRANSLUCENT
            );

            menuParams.gravity = Gravity.CENTER;

            // Shortcut button actions
            menuView.findViewById(R.id.btnMenuClose).setOnClickListener(v -> stopSelf());
            menuView.findViewById(R.id.btnMenuMinimize).setOnClickListener(v -> hideMenuWindow());

            menuView.findViewById(R.id.btnMenuChat).setOnClickListener(v -> {
                hideMenuWindow();
                toggleChatWindow();
            });

            menuView.findViewById(R.id.btnMenuCalc).setOnClickListener(v -> {
                hideMenuWindow();
                toggleCalcWindow();
            });

            menuView.findViewById(R.id.btnMenuNotes).setOnClickListener(v -> {
                hideMenuWindow();
                toggleNotesWindow();
            });

            menuView.findViewById(R.id.btnMenuScreenshot).setOnClickListener(v -> {
                hideMenuWindow();
                // Promote service to media projection type immediately before starting ScreenshotHelperActivity
                try {
                    Intent serviceIntent = new Intent(FloatingService.this, FloatingService.class);
                    serviceIntent.setAction("ACTION_START_PROJECTION");
                    startService(serviceIntent);
                } catch (Exception e) {
                    e.printStackTrace();
                }
                Intent intent = new Intent(FloatingService.this, ScreenshotHelperActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });

            menuView.findViewById(R.id.btnMenuVault).setOnClickListener(v -> {
                hideMenuWindow();
                // Launcher cryptographic entry calculator activity
                Intent intent = new Intent(FloatingService.this, VaultActivity.class);
                intent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK);
                startActivity(intent);
            });
        }

        if (!isMenuShowing) {
            try {
                windowManager.addView(menuView, menuParams);
                isMenuShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to display multitasking menu.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideMenuWindow() {
        if (isMenuShowing && menuView != null) {
            try {
                windowManager.removeView(menuView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            isMenuShowing = false;
        }
    }

    // ============================================
    // 3. FLOATING AI ASSISTANT POPUP
    // ============================================

    private void toggleChatWindow() {
        if (isChatShowing) {
            hideChatWindow();
        } else {
            showChatWindow();
        }
    }

    private void showChatWindow() {
        if (chatView == null) {
            chatView = LayoutInflater.from(this).inflate(R.layout.layout_floating_chat, null);

            int flagOverlay;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flagOverlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                flagOverlay = WindowManager.LayoutParams.TYPE_PHONE;
            }

            // Need Focusable so we can type in EditText
            WindowManager.LayoutParams chatParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flagOverlay,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            chatParams.gravity = Gravity.CENTER;

            // Bind components
            rvChatHistory = chatView.findViewById(R.id.rvChatHistory);
            tvChatTypingIndicator = chatView.findViewById(R.id.tvChatTypingIndicator);
            etChatMessage = chatView.findViewById(R.id.etChatMessage);
            ImageButton btnSend = chatView.findViewById(R.id.btnChatSend);
            ImageButton btnClear = chatView.findViewById(R.id.btnChatClear);
            ImageButton btnClose = chatView.findViewById(R.id.btnChatClose);

            // Configure Recycler List
            chatHistoryList = new ArrayList<>();
            chatHistoryList.add(new ChatAdapter.ChatMessage("Hello! I am SAFI, your overlay AI assistant. Ask me anything! Note: Make sure to set your Groq API key in the main app.", false));
            chatAdapter = new ChatAdapter(this, chatHistoryList);
            rvChatHistory.setLayoutManager(new LinearLayoutManager(this));
            rvChatHistory.setAdapter(chatAdapter);

            // Drag handler setup
            chatView.findViewById(R.id.chatHeader).setOnTouchListener(new DragTouchListener(chatParams, chatView));

            // Closers & Clearers
            btnClose.setOnClickListener(v -> hideChatWindow());
            btnClear.setOnClickListener(v -> {
                chatHistoryList.clear();
                chatHistoryList.add(new ChatAdapter.ChatMessage("Conversation cleared.", false));
                chatAdapter.notifyDataSetChanged();
            });

            // Send Prompt logic
            btnSend.setOnClickListener(v -> {
                String prompt = etChatMessage.getText().toString().trim();
                if (!prompt.isEmpty()) {
                    etChatMessage.setText("");
                    processUserMessage(prompt);
                }
            });

            try {
                windowManager.addView(chatView, chatParams);
                isChatShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to display AI Chat.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideChatWindow() {
        if (isChatShowing && chatView != null) {
            try {
                windowManager.removeView(chatView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            chatView = null;
            isChatShowing = false;
        }
    }

    private void processUserMessage(String prompt) {
        // Appends to list
        chatHistoryList.add(new ChatAdapter.ChatMessage(prompt, true));
        chatAdapter.notifyItemInserted(chatHistoryList.size() - 1);
        rvChatHistory.scrollToPosition(chatHistoryList.size() - 1);

        String savedKey = prefs.getString("groq_api_key", "").trim();
        if (savedKey.isEmpty()) {
            chatHistoryList.add(new ChatAdapter.ChatMessage("Error: Groq API Key is not set! Please open the Floating SAFI dashboard to save one.", false));
            chatAdapter.notifyItemInserted(chatHistoryList.size() - 1);
            rvChatHistory.scrollToPosition(chatHistoryList.size() - 1);
            return;
        }

        // Display typing status indicator
        tvChatTypingIndicator.setVisibility(View.VISIBLE);

        // Dispatches thread request to API
        threadExecutor.execute(() -> {
            String reply;
            try {
                reply = postGroqChatRequest(savedKey, prompt);
            } catch (Exception e) {
                reply = "Error running query: " + e.getMessage();
            }

            final String finalReply = reply;
            new Handler(Looper.getMainLooper()).post(() -> {
                if (isChatShowing && chatView != null) {
                    if (tvChatTypingIndicator != null) {
                        tvChatTypingIndicator.setVisibility(View.GONE);
                    }
                    if (chatHistoryList != null && chatAdapter != null && rvChatHistory != null) {
                        chatHistoryList.add(new ChatAdapter.ChatMessage(finalReply, false));
                        chatAdapter.notifyItemInserted(chatHistoryList.size() - 1);
                        rvChatHistory.scrollToPosition(chatHistoryList.size() - 1);
                    }
                }
            });
        });
    }

    private String postGroqChatRequest(String apiKey, String prompt) throws Exception {
        URL url = new URL("https://api.groq.com/openai/v1/chat/completions");
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();
        conn.setRequestMethod("POST");
        conn.setRequestProperty("Content-Type", "application/json");
        conn.setRequestProperty("Authorization", "Bearer " + apiKey);
        conn.setConnectTimeout(8000);
        conn.setReadTimeout(12000);
        conn.setDoOutput(true);

        // Native JSONObject formulation
        JSONObject requestBody = new JSONObject();
        requestBody.put("model", "llama-3.3-70b-versatile");

        JSONArray messagesArray = new JSONArray();
        JSONObject messageObj = new JSONObject();
        messageObj.put("role", "user");
        messageObj.put("content", prompt);
        messagesArray.put(messageObj);
        requestBody.put("messages", messagesArray);

        // Write OutputStream
        OutputStream os = conn.getOutputStream();
        os.write(requestBody.toString().getBytes("UTF-8"));
        os.flush();
        os.close();

        int responseCode = conn.getResponseCode();
        if (responseCode == HttpURLConnection.HTTP_OK) {
            BufferedReader br = new BufferedReader(new InputStreamReader(conn.getInputStream(), "UTF-8"));
            StringBuilder response = new StringBuilder();
            String responseLine;
            while ((responseLine = br.readLine()) != null) {
                response.append(responseLine.trim());
            }
            br.close();

            // Native JSON Parsing
            JSONObject responseObject = new JSONObject(response.toString());
            JSONArray choices = responseObject.getJSONArray("choices");
            return choices.getJSONObject(0).getJSONObject("message").getString("content");
        } else {
            return "Failure code: " + responseCode + " - " + conn.getResponseMessage();
        }
    }

    // ============================================
    // 4. FLOATING SCIENTIFIC CALCULATOR POPUP
    // ============================================

    private void toggleCalcWindow() {
        if (isCalcShowing) {
            hideCalcWindow();
        } else {
            showCalcWindow();
        }
    }

    private void showCalcWindow() {
        if (calcView == null) {
            calcView = LayoutInflater.from(this).inflate(R.layout.layout_floating_calculator, null);

            int flagOverlay;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flagOverlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                flagOverlay = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams calcParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flagOverlay,
                    WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            calcParams.gravity = Gravity.CENTER;

            tvCalcExpression = calcView.findViewById(R.id.tvCalcExpression);
            tvCalcDisplay = calcView.findViewById(R.id.tvCalcDisplay);

            setupScientificCalculatorKeyboard();

            calcView.findViewById(R.id.btnCalcClose).setOnClickListener(v -> hideCalcWindow());
            calcView.findViewById(R.id.calcHeader).setOnTouchListener(new DragTouchListener(calcParams, calcView));

            try {
                windowManager.addView(calcView, calcParams);
                isCalcShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to display Calculator.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideCalcWindow() {
        if (isCalcShowing && calcView != null) {
            try {
                windowManager.removeView(calcView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            calcView = null;
            isCalcShowing = false;
        }
    }

    private void setupScientificCalculatorKeyboard() {
        // Binds number buttons listener
        int[] numButtons = {
                R.id.btnCalc0, R.id.btnCalc1, R.id.btnCalc2, R.id.btnCalc3,
                R.id.btnCalc4, R.id.btnCalc5, R.id.btnCalc6, R.id.btnCalc7,
                R.id.btnCalc8, R.id.btnCalc9
        };

        View.OnClickListener numListener = v -> {
            Button btn = (Button) v;
            String text = btn.getText().toString();
            if (isCalcOperatorInput || currentCalcDisplay.equals("0")) {
                currentCalcDisplay = text;
                isCalcOperatorInput = false;
            } else {
                currentCalcDisplay += text;
            }
            currentCalcExpression += text;
            tvCalcDisplay.setText(currentCalcDisplay);
            tvCalcExpression.setText(currentCalcExpression);
        };

        for (int id : numButtons) {
            calcView.findViewById(id).setOnClickListener(numListener);
        }

        // Clear AC click
        calcView.findViewById(R.id.btnCalcClear).setOnClickListener(v -> {
            currentCalcExpression = "";
            currentCalcDisplay = "0";
            tvCalcDisplay.setText("0");
            tvCalcExpression.setText("");
        });

        // Decimal point
        calcView.findViewById(R.id.btnCalcDot).setOnClickListener(v -> {
            if (!currentCalcDisplay.contains(".")) {
                currentCalcDisplay += ".";
                currentCalcExpression += ".";
                tvCalcDisplay.setText(currentCalcDisplay);
                tvCalcExpression.setText(currentCalcExpression);
            }
        });

        // Basic Math Operations (+ - x /)
        int[] opButtons = { R.id.btnCalcAdd, R.id.btnCalcSub, R.id.btnCalcMul, R.id.btnCalcDiv };
        View.OnClickListener opListener = v -> {
            Button btn = (Button) v;
            String op = btn.getText().toString();
            if (op.equals("x")) op = "*";
            currentCalcExpression += " " + op + " ";
            isCalcOperatorInput = true;
            tvCalcExpression.setText(currentCalcExpression);
        };
        for (int id : opButtons) {
            calcView.findViewById(id).setOnClickListener(opListener);
        }

        // Bracket Parentheses
        calcView.findViewById(R.id.btnParenO).setOnClickListener(v -> {
            currentCalcExpression += "(";
            tvCalcExpression.setText(currentCalcExpression);
        });

        calcView.findViewById(R.id.btnParenC).setOnClickListener(v -> {
            currentCalcExpression += ")";
            tvCalcExpression.setText(currentCalcExpression);
        });

        // Exponents Power
        calcView.findViewById(R.id.btnPower).setOnClickListener(v -> {
            currentCalcExpression += "^";
            tvCalcExpression.setText(currentCalcExpression);
        });

        // Math formulas: sin, cos, tan, log, ln, sqrt
        int[] scientificButtons = { R.id.btnSin, R.id.btnCos, R.id.btnTan, R.id.btnLog, R.id.btnLn, R.id.btnSqrt };
        View.OnClickListener sciListener = v -> {
            Button btn = (Button) v;
            String text = btn.getText().toString();
            currentCalcExpression += text + "(";
            tvCalcExpression.setText(currentCalcExpression);
        };
        for (int id : scientificButtons) {
            calcView.findViewById(id).setOnClickListener(sciListener);
        }

        // Evaluate '='
        calcView.findViewById(R.id.btnCalcEquals).setOnClickListener(v -> {
            try {
                double resValue = evaluateParser(currentCalcExpression);
                currentCalcDisplay = formatDouble(resValue);
                tvCalcDisplay.setText(currentCalcDisplay);
                currentCalcExpression = currentCalcDisplay; // Save result
                tvCalcExpression.setText(currentCalcExpression);
            } catch (Exception e) {
                tvCalcDisplay.setText("Error");
                currentCalcExpression = "";
            }
        });
    }

    private String formatDouble(double value) {
        if (value == (long) value) {
            return String.format("%d", (long) value);
        } else {
            return String.format("%s", value);
        }
    }

    // ============================================
    // 5. FLOATING QUICK NOTES MODULE POPUP
    // ============================================

    private void toggleNotesWindow() {
        if (isNotesShowing) {
            hideNotesWindow();
        } else {
            showNotesWindow();
        }
    }

    private void showNotesWindow() {
        if (notesView == null) {
            notesView = LayoutInflater.from(this).inflate(R.layout.layout_floating_notes, null);

            int flagOverlay;
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                flagOverlay = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY;
            } else {
                flagOverlay = WindowManager.LayoutParams.TYPE_PHONE;
            }

            WindowManager.LayoutParams notesParams = new WindowManager.LayoutParams(
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    WindowManager.LayoutParams.WRAP_CONTENT,
                    flagOverlay,
                    WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL | WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH,
                    PixelFormat.TRANSLUCENT
            );
            notesParams.gravity = Gravity.CENTER;

            // Bind layout components
            final ViewAnimator noteAnimator = notesView.findViewById(R.id.notesViewAnimator);
            rvNotesHistory = notesView.findViewById(R.id.rvNotesHistory);
            etNoteSearch = notesView.findViewById(R.id.etNoteSearch);
            tvNoNotes = notesView.findViewById(R.id.tvNoNotes);

            // Editor layouts binding
            etNoteTitleInput = notesView.findViewById(R.id.etNoteTitleInput);
            etNoteContentInput = notesView.findViewById(R.id.etNoteContentInput);
            cbPinNote = notesView.findViewById(R.id.cbPinNote);
            btnDeleteNote = notesView.findViewById(R.id.btnDeleteNote);

            // Initialize Recycler Note lists
            rvNotesHistory.setLayoutManager(new LinearLayoutManager(this));
            loadNotesList("");

            // Setup Search watch
            etNoteSearch.addTextChangedListener(new TextWatcher() {
                @Override
                public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

                @Override
                public void onTextChanged(CharSequence s, int start, int before, int count) {
                    loadNotesList(s.toString());
                }

                @Override
                public void afterTextChanged(Editable s) {}
            });

            // Drag setup
            notesView.findViewById(R.id.notesListHeader).setOnTouchListener(new DragTouchListener(notesParams, notesView));
            notesView.findViewById(R.id.notesEditorHeader).setOnTouchListener(new DragTouchListener(notesParams, notesView));

            // View buttons trigger actions
            notesView.findViewById(R.id.btnNotesClose).setOnClickListener(v -> hideNotesWindow());
            
            notesView.findViewById(R.id.btnCreateNote).setOnClickListener(v -> {
                // Navigate to Editor Pane (Screen 1) for creation
                activeEditingNote = null;
                etNoteTitleInput.setText("");
                etNoteContentInput.setText("");
                cbPinNote.setChecked(false);
                btnDeleteNote.setVisibility(View.GONE);
                ((TextView) notesView.findViewById(R.id.tvEditorTitle)).setText("Compose Note");
                noteAnimator.setDisplayedChild(1);
            });

            notesView.findViewById(R.id.btnNoteEditorBack).setOnClickListener(v -> {
                // Collapse editor
                noteAnimator.setDisplayedChild(0);
                activeEditingNote = null;
            });

            notesView.findViewById(R.id.btnSaveNote).setOnClickListener(v -> {
                // CRUD Save logic
                String title = etNoteTitleInput.getText().toString().trim();
                String content = etNoteContentInput.getText().toString().trim();
                boolean isPinned = cbPinNote.isChecked();

                if (title.isEmpty() && content.isEmpty()) {
                    Toast.makeText(FloatingService.this, "Cannot save empty note!", Toast.LENGTH_SHORT).show();
                    return;
                }

                if (activeEditingNote == null) {
                    // Create entry
                    dbHelper.insertNote(title, content, isPinned);
                    Toast.makeText(FloatingService.this, "Note saved!", Toast.LENGTH_SHORT).show();
                } else {
                    // Update entry
                    dbHelper.updateNote(activeEditingNote.id, title, content, isPinned);
                    Toast.makeText(FloatingService.this, "Note updated!", Toast.LENGTH_SHORT).show();
                }

                // Return to main and reload
                loadNotesList(etNoteSearch.getText().toString());
                noteAnimator.setDisplayedChild(0);
                activeEditingNote = null;
            });

            btnDeleteNote.setOnClickListener(v -> {
                if (activeEditingNote != null) {
                    dbHelper.deleteNote(activeEditingNote.id);
                    Toast.makeText(FloatingService.this, "Note deleted successfully", Toast.LENGTH_SHORT).show();
                    loadNotesList(etNoteSearch.getText().toString());
                    noteAnimator.setDisplayedChild(0);
                    activeEditingNote = null;
                }
            });

            try {
                windowManager.addView(notesView, notesParams);
                isNotesShowing = true;
            } catch (Exception e) {
                e.printStackTrace();
                Toast.makeText(this, "Failed to display Notes.", Toast.LENGTH_SHORT).show();
            }
        }
    }

    private void hideNotesWindow() {
        if (isNotesShowing && notesView != null) {
            try {
                windowManager.removeView(notesView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            notesView = null;
            isNotesShowing = false;
        }
    }

    private void loadNotesList(String query) {
        noteList = dbHelper.getAllNotes(query);
        if (noteList == null || noteList.isEmpty()) {
            tvNoNotes.setVisibility(View.VISIBLE);
            rvNotesHistory.setVisibility(View.GONE);
        } else {
            tvNoNotes.setVisibility(View.GONE);
            rvNotesHistory.setVisibility(View.VISIBLE);

            noteAdapter = new NoteAdapter(this, noteList, note -> {
                // Edit note click event
                activeEditingNote = note;
                etNoteTitleInput.setText(note.title);
                etNoteContentInput.setText(note.content);
                cbPinNote.setChecked(note.isPinned);
                btnDeleteNote.setVisibility(View.VISIBLE);
                ((TextView) notesView.findViewById(R.id.tvEditorTitle)).setText("Update Note");
                ((ViewAnimator) notesView.findViewById(R.id.notesViewAnimator)).setDisplayedChild(1);
            });
            rvNotesHistory.setAdapter(noteAdapter);
        }
    }

    // ============================================
    // HELPER DRAG GESTURE TOUCH DETECTOR CLASS
    // ============================================

    private class DragTouchListener implements View.OnTouchListener {
        private final WindowManager.LayoutParams elementParams;
        private final View elementView;
        private int initX;
        private int initY;
        private float initTouchX;
        private float initTouchY;

        public DragTouchListener(WindowManager.LayoutParams elementParams, View elementView) {
            this.elementParams = elementParams;
            this.elementView = elementView;
        }

        @Override
        public boolean onTouch(View v, MotionEvent event) {
            switch (event.getAction()) {
                case MotionEvent.ACTION_DOWN:
                    initX = elementParams.x;
                    initY = elementParams.y;
                    initTouchX = event.getRawX();
                    initTouchY = event.getRawY();
                    return true;

                case MotionEvent.ACTION_MOVE:
                    elementParams.x = initX + (int) (event.getRawX() - initTouchX);
                    elementParams.y = initY + (int) (event.getRawY() - initTouchY);
                    try {
                        if (elementView != null && elementView.isAttachedToWindow()) {
                            windowManager.updateViewLayout(elementView, elementParams);
                        }
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                    return true;
            }
            return false;
        }
    }

    // ========================================================
    // RECURSIVE DESCENT MATH PARSER FOR CALCULATOR WINDOW
    // ========================================================

    private double evaluateParser(final String str) {
        return new Object() {
            int pos = -1, ch;

            void nextChar() {
                ch = (++pos < str.length()) ? str.charAt(pos) : -1;
            }

            boolean eat(int charToEat) {
                while (ch == ' ') nextChar();
                if (ch == charToEat) {
                    nextChar();
                    return true;
                }
                return false;
            }

            double parse() {
                nextChar();
                double x = parseExpression();
                if (pos < str.length()) throw new RuntimeException("Unexpected expression: " + (char)ch);
                return x;
            }

            double parseExpression() {
                double x = parseTerm();
                for (;;) {
                    if      (eat('+')) x += parseTerm(); // addition
                    else if (eat('-')) x -= parseTerm(); // subtraction
                    else return x;
                }
            }

            double parseTerm() {
                double x = parseFactor();
                for (;;) {
                    if      (eat('*')) x *= parseFactor(); // multiplication
                    else if (eat('/')) x /= parseFactor(); // division
                    else return x;
                }
            }

            double parseFactor() {
                if (eat('+')) return parseFactor(); // unary priority
                if (eat('-')) return -parseFactor(); // negative

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // plain numerical inputs
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else if (ch >= 'a' && ch <= 'z') { // math scientific formulas
                    while (ch >= 'a' && ch <= 'z') nextChar();
                    String func = str.substring(startPos, this.pos);
                    if (eat('(')) {
                        x = parseExpression();
                        eat(')');
                    } else {
                        x = parseFactor();
                    }
                    if (func.equals("sin")) x = Math.sin(Math.toRadians(x));
                    else if (func.equals("cos")) x = Math.cos(Math.toRadians(x));
                    else if (func.equals("tan")) x = Math.tan(Math.toRadians(x));
                    else if (func.equals("sqrt")) x = Math.sqrt(x);
                    else if (func.equals("log")) x = Math.log10(x);
                    else if (func.equals("ln")) x = Math.log(x);
                    else throw new RuntimeException("Unsupported function: " + func);
                } else {
                    throw new RuntimeException("Unknown calculation input char: " + (char)ch);
                }

                if (eat('^')) x = Math.pow(x, parseFactor()); // exponents / powers sequence

                return x;
            }
        }.parse();
    }

    // ============================================
    // SERVICES SYSTEM CALLBACK CLEARS
    // ============================================

    @Override
    public void onDestroy() {
        super.onDestroy();
        isRunning = false;
        // Remove all floating layouts to prevent raw window leakage overlay warnings
        hideMenuWindow();
        hideChatWindow();
        hideCalcWindow();
        hideNotesWindow();

        if (bubbleView != null) {
            try {
                windowManager.removeView(bubbleView);
            } catch (Exception e) {
                e.printStackTrace();
            }
            bubbleView = null;
        }

        if (threadExecutor != null) {
            threadExecutor.shutdown();
        }
    }

    @Override
    public IBinder onBind(Intent intent) {
        return null;
    }
}
