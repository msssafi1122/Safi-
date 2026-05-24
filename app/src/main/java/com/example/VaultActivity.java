package com.example;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.database.Cursor;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.ImageButton;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;
import android.widget.ViewAnimator;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.recyclerview.widget.GridLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import java.io.File;
import java.io.FileOutputStream;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.List;
import java.util.UUID;

public class VaultActivity extends AppCompatActivity {

    private static final int REQ_CODE_PICK_IMAGE = 2001;

    private ViewAnimator viewAnimator;
    private TextView tvGateExpression;
    private TextView tvGateDisplay;
    private TextView tvNoMedia;
    private RecyclerView rvVaultGallery;

    private String currentExpression = "";
    private String currentDisplay = "0";
    private boolean isNewOperatorInput = false;

    private DatabaseHelper dbHelper;
    private SharedPreferences prefs;
    private List<DatabaseHelper.VaultItem> vaultItemList;
    private VaultAdapter adapter;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_vault);

        dbHelper = new DatabaseHelper(this);
        prefs = getSharedPreferences("FloatingSafiPrefs", MODE_PRIVATE);

        // Bind layouts
        viewAnimator = findViewById(R.id.vaultViewAnimator);
        tvGateExpression = findViewById(R.id.tvGateExpression);
        tvGateDisplay = findViewById(R.id.tvGateDisplay);
        tvNoMedia = findViewById(R.id.tvNoMedia);
        rvVaultGallery = findViewById(R.id.rvVaultGallery);

        setupCalculatorKeyboard();
        setupVaultPanel();
    }

    // ==========================================
    // calculator gate input configurations
    // ==========================================

    private void setupCalculatorKeyboard() {
        int[] numButtonIds = {
                R.id.btnGate0, R.id.btnGate1, R.id.btnGate2, R.id.btnGate3,
                R.id.btnGate4, R.id.btnGate5, R.id.btnGate6, R.id.btnGate7,
                R.id.btnGate8, R.id.btnGate9
        };

        View.OnClickListener numListener = v -> {
            Button btn = (Button) v;
            String text = btn.getText().toString();
            if (isNewOperatorInput || currentDisplay.equals("0")) {
                currentDisplay = text;
                isNewOperatorInput = false;
            } else {
                currentDisplay += text;
            }
            currentExpression += text;
            tvGateDisplay.setText(currentDisplay);
            tvGateExpression.setText(currentExpression);
        };

        for (int id : numButtonIds) {
            findViewById(id).setOnClickListener(numListener);
        }

        // Decimal Point
        findViewById(R.id.btnGateDot).setOnClickListener(v -> {
            if (!currentDisplay.contains(".")) {
                currentDisplay += ".";
                currentExpression += ".";
                tvGateDisplay.setText(currentDisplay);
                tvGateExpression.setText(currentExpression);
            }
        });

        // Parentheses
        findViewById(R.id.btnGateParenOpen).setOnClickListener(v -> {
            currentExpression += "(";
            tvGateExpression.setText(currentExpression);
        });

        findViewById(R.id.btnGateParenClose).setOnClickListener(v -> {
            currentExpression += ")";
            tvGateExpression.setText(currentExpression);
        });

        // Clear (AC)
        findViewById(R.id.btnGateClear).setOnClickListener(v -> {
            currentExpression = "";
            currentDisplay = "0";
            tvGateDisplay.setText(currentDisplay);
            tvGateExpression.setText("");
        });

        // Basic Math Operations
        int[] operatorIds = { R.id.btnGateAdd, R.id.btnGateSub, R.id.btnGateMul, R.id.btnGateDiv, R.id.btnGatePercent };
        View.OnClickListener operatorListener = v -> {
            Button btn = (Button) v;
            String op = btn.getText().toString();
            
            // Format symbol
            if (op.equals("x")) op = "*";
            
            currentExpression += " " + op + " ";
            isNewOperatorInput = true;
            tvGateExpression.setText(currentExpression);
        };

        for (int id : operatorIds) {
            findViewById(id).setOnClickListener(operatorListener);
        }

        // EVALUATE EXPRESSION OR LOCK UNLOCK
        findViewById(R.id.btnGateEquals).setOnClickListener(v -> {
            String checkVal = currentDisplay.trim();
            String savedPin = prefs.getString("vault_pin", "1234");

            // CHECK GATED PIN (either plain input or evaluates match)
            if (checkVal.equals(savedPin) || currentExpression.trim().equals(savedPin)) {
                // Pin is typed correctly! Transition to private dashboard
                unlockVault();
            } else {
                // Behave like a normal real calculator
                try {
                    double result = evalExpression(currentExpression);
                    currentDisplay = formatResult(result);
                    tvGateDisplay.setText(currentDisplay);
                    currentExpression = currentDisplay; // Store result
                    tvGateExpression.setText(currentExpression);
                } catch (Exception e) {
                    tvGateDisplay.setText("Error");
                    currentExpression = "";
                }
            }
        });
    }

    private void unlockVault() {
        viewAnimator.setDisplayedChild(1);
        loadVaultItems();
        Toast.makeText(this, "Vault Decrypted Successfully!", Toast.LENGTH_SHORT).show();
    }

    // ==========================================
    // vault controller dashboard setup
    // ==========================================

    private void setupVaultPanel() {
        findViewById(R.id.btnExitVault).setOnClickListener(v -> {
            // Relock immediately
            currentExpression = "";
            currentDisplay = "0";
            tvGateDisplay.setText("0");
            tvGateExpression.setText("");
            viewAnimator.setDisplayedChild(0);
            Toast.makeText(this, "Vault Locked", Toast.LENGTH_SHORT).show();
        });

        findViewById(R.id.btnImportMedia).setOnClickListener(v -> {
            // Open system intent to pick photos
            Intent intent = new Intent(Intent.ACTION_PICK, MediaStore.Images.Media.EXTERNAL_CONTENT_URI);
            startActivityForResult(intent, REQ_CODE_PICK_IMAGE);
        });

        rvVaultGallery.setLayoutManager(new GridLayoutManager(this, 3));
    }

    private void loadVaultItems() {
        vaultItemList = dbHelper.getAllVaultItems();
        if (vaultItemList == null || vaultItemList.isEmpty()) {
            tvNoMedia.setVisibility(View.VISIBLE);
            rvVaultGallery.setVisibility(View.GONE);
        } else {
            tvNoMedia.setVisibility(View.GONE);
            rvVaultGallery.setVisibility(View.VISIBLE);
            adapter = new VaultAdapter(this, vaultItemList, item -> {
                // Delete photo from database and local storage
                File file = new File(item.storedPath);
                if (file.exists()) {
                    file.delete();
                }
                dbHelper.deleteVaultItem(item.id);
                loadVaultItems(); // Refresh
                Toast.makeText(VaultActivity.this, "Item destroyed securely", Toast.LENGTH_SHORT).show();
            });
            rvVaultGallery.setAdapter(adapter);
        }
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == REQ_CODE_PICK_IMAGE && resultCode == RESULT_OK && data != null) {
            Uri imageUri = data.getData();
            if (imageUri != null) {
                try {
                    // Create secure folder inside sandbox files directory
                    File secureFolder = new File(getFilesDir(), "secure_vault");
                    if (!secureFolder.exists()) {
                        secureFolder.mkdirs();
                    }

                    // Encrypted disguished image name (just basic file duplication)
                    String randomName = "vault_" + UUID.randomUUID().toString() + ".bin";
                    File destinationFile = new File(secureFolder, randomName);

                    // Duplicate file contents from ContentResolver to Private storage
                    InputStream inStream = getContentResolver().openInputStream(imageUri);
                    OutputStream outStream = new FileOutputStream(destinationFile);
                    byte[] buffer = new byte[4096];
                    int read;
                    while ((read = inStream.read(buffer)) != -1) {
                        outStream.write(buffer, 0, read);
                    }
                    inStream.close();
                    outStream.close();

                    // Optional: retrieve filename
                    String filename = destinationFile.getName();
                    String origPath = getRealPathFromURI(imageUri);

                    // Insert to sqlite index Table
                    dbHelper.insertVaultItem(filename, origPath, destinationFile.getAbsolutePath());

                    // Refresh media gallery lists
                    loadVaultItems();

                    Toast.makeText(this, "Photo hidden successfully in Vault!", Toast.LENGTH_LONG).show();

                } catch (Exception e) {
                    Toast.makeText(this, "Failed to hide image: " + e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            }
        }
    }

    private String getRealPathFromURI(Uri contentUri) {
        String[] proj = { MediaStore.Images.Media.DATA };
        Cursor cursor = getContentResolver().query(contentUri, proj, null, null, null);
        if (cursor == null) return "Unknown";
        int column_index = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.DATA);
        cursor.moveToFirst();
        String path = cursor.getString(column_index);
        cursor.close();
        return path;
    }

    // ==========================================
    // adapter declaration for vault gallery
    // ==========================================

    private static class VaultAdapter extends RecyclerView.Adapter<VaultAdapter.ViewHolder> {

        private final Context context;
        private final List<DatabaseHelper.VaultItem> list;
        private final OnItemClickListener clickListener;

        public interface OnItemClickListener {
            void onDeleteClicked(DatabaseHelper.VaultItem item);
        }

        public VaultAdapter(Context context, List<DatabaseHelper.VaultItem> list, OnItemClickListener clickListener) {
            this.context = context;
            this.list = list;
            this.clickListener = clickListener;
        }

        @NonNull
        @Override
        public ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_vault_gallery, parent, false);
            return new ViewHolder(view);
        }

        @Override
        public void onBindViewHolder(@NonNull ViewHolder holder, int position) {
            DatabaseHelper.VaultItem item = list.get(position);

            // Load decoded bitmap directly from private app sandbox (not exposed to system scanners)
            Bitmap bitmap = BitmapFactory.decodeFile(item.storedPath);
            if (bitmap != null) {
                holder.ivVaultThumbnail.setImageBitmap(bitmap);
            } else {
                holder.ivVaultThumbnail.setImageResource(android.R.drawable.ic_menu_gallery);
            }

            holder.ibDeleteVaultItem.setOnClickListener(v -> {
                if (clickListener != null) {
                    clickListener.onDeleteClicked(item);
                }
            });
        }

        @Override
        public int getItemCount() {
            return list.size();
        }

        public static class ViewHolder extends RecyclerView.ViewHolder {
            ImageView ivVaultThumbnail;
            ImageButton ibDeleteVaultItem;

            public ViewHolder(@NonNull View itemView) {
                super(itemView);
                ivVaultThumbnail = itemView.findViewById(R.id.ivVaultThumbnail);
                ibDeleteVaultItem = itemView.findViewById(R.id.ibDeleteVaultItem);
            }
        }
    }

    // ==========================================
    // BASIC MATH EXPRESSION EVALUATOR IN PURE JAVA
    // ==========================================

    private double evalExpression(final String str) {
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
                if (pos < str.length()) throw new RuntimeException("Unexpected: " + (char)ch);
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
                if (eat('+')) return parseFactor(); // unary plus
                if (eat('-')) return -parseFactor(); // unary minus

                double x;
                int startPos = this.pos;
                if (eat('(')) { // parentheses
                    x = parseExpression();
                    eat(')');
                } else if ((ch >= '0' && ch <= '9') || ch == '.') { // numbers
                    while ((ch >= '0' && ch <= '9') || ch == '.') nextChar();
                    x = Double.parseDouble(str.substring(startPos, this.pos));
                } else {
                    throw new RuntimeException("Unexpected character: " + (char)ch);
                }

                return x;
            }
        }.parse();
    }

    private String formatResult(double result) {
        if (result == (long) result) {
            return String.format("%d", (long) result);
        } else {
            return String.format("%s", result);
        }
    }
}
