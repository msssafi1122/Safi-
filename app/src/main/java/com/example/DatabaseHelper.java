package com.example;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.util.ArrayList;
import java.util.List;

public class DatabaseHelper extends SQLiteOpenHelper {

    private static final String DATABASE_NAME = "floating_safi.db";
    private static final int DATABASE_VERSION = 1;

    // Table Notes Constants
    public static final String TABLE_NOTES = "notes";
    public static final String COLUMN_NOTE_ID = "id";
    public static final String COLUMN_NOTE_TITLE = "title";
    public static final String COLUMN_NOTE_CONTENT = "content";
    public static final String COLUMN_NOTE_IS_PINNED = "is_pinned"; // 0 = false, 1 = true
    public static final String COLUMN_NOTE_TIMESTAMP = "timestamp";

    // Table Vault Constants
    public static final String TABLE_VAULT = "vault_items";
    public static final String COLUMN_VAULT_ID = "id";
    public static final String COLUMN_VAULT_FILENAME = "filename";
    public static final String COLUMN_VAULT_ORIG_PATH = "original_path";
    public static final String COLUMN_VAULT_STORED_PATH = "stored_path";
    public static final String COLUMN_VAULT_TIMESTAMP = "timestamp";

    public DatabaseHelper(Context context) {
        super(context, DATABASE_NAME, null, DATABASE_VERSION);
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        // Create Notes Table
        String createNotesTable = "CREATE TABLE " + TABLE_NOTES + " (" +
                COLUMN_NOTE_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_NOTE_TITLE + " TEXT, " +
                COLUMN_NOTE_CONTENT + " TEXT, " +
                COLUMN_NOTE_IS_PINNED + " INTEGER DEFAULT 0, " +
                COLUMN_NOTE_TIMESTAMP + " INTEGER" +
                ")";
        db.execSQL(createNotesTable);

        // Create Vault Items Table
        String createVaultTable = "CREATE TABLE " + TABLE_VAULT + " (" +
                COLUMN_VAULT_ID + " INTEGER PRIMARY KEY AUTOINCREMENT, " +
                COLUMN_VAULT_FILENAME + " TEXT, " +
                COLUMN_VAULT_ORIG_PATH + " TEXT, " +
                COLUMN_VAULT_STORED_PATH + " TEXT, " +
                COLUMN_VAULT_TIMESTAMP + " INTEGER" +
                ")";
        db.execSQL(createVaultTable);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_NOTES);
        db.execSQL("DROP TABLE IF EXISTS " + TABLE_VAULT);
        onCreate(db);
    }

    // ===================================
    // NOTES CRUD OPERATIONS
    // ===================================

    public long insertNote(String title, String content, boolean isPinned) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NOTE_TITLE, title);
        cv.put(COLUMN_NOTE_CONTENT, content);
        cv.put(COLUMN_NOTE_IS_PINNED, isPinned ? 1 : 0);
        cv.put(COLUMN_NOTE_TIMESTAMP, System.currentTimeMillis());
        long result = db.insert(TABLE_NOTES, null, cv);
        db.close();
        return result;
    }

    public int updateNote(int id, String title, String content, boolean isPinned) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_NOTE_TITLE, title);
        cv.put(COLUMN_NOTE_CONTENT, content);
        cv.put(COLUMN_NOTE_IS_PINNED, isPinned ? 1 : 0);
        cv.put(COLUMN_NOTE_TIMESTAMP, System.currentTimeMillis());
        int rows = db.update(TABLE_NOTES, cv, COLUMN_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    public int deleteNote(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_NOTES, COLUMN_NOTE_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    public List<NoteItem> getAllNotes(String searchQuery) {
        List<NoteItem> notesList = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        
        String query;
        String[] args = null;
        
        if (searchQuery == null || searchQuery.isEmpty()) {
            query = "SELECT * FROM " + TABLE_NOTES + 
                    " ORDER BY " + COLUMN_NOTE_IS_PINNED + " DESC, " + COLUMN_NOTE_TIMESTAMP + " DESC";
        } else {
            query = "SELECT * FROM " + TABLE_NOTES + 
                    " WHERE " + COLUMN_NOTE_TITLE + " LIKE ? OR " + COLUMN_NOTE_CONTENT + " LIKE ?" +
                    " ORDER BY " + COLUMN_NOTE_IS_PINNED + " DESC, " + COLUMN_NOTE_TIMESTAMP + " DESC";
            args = new String[]{"%" + searchQuery + "%", "%" + searchQuery + "%"};
        }

        Cursor cursor = db.rawQuery(query, args);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NOTE_ID));
                String title = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TITLE));
                String content = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_NOTE_CONTENT));
                boolean isPinned = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_NOTE_IS_PINNED)) == 1;
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_NOTE_TIMESTAMP));
                
                notesList.add(new NoteItem(id, title, content, isPinned, timestamp));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return notesList;
    }

    // ===================================
    // VAULT OPERATIONS
    // ===================================

    public long insertVaultItem(String filename, String originalPath, String storedPath) {
        SQLiteDatabase db = this.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put(COLUMN_VAULT_FILENAME, filename);
        cv.put(COLUMN_VAULT_ORIG_PATH, originalPath);
        cv.put(COLUMN_VAULT_STORED_PATH, storedPath);
        cv.put(COLUMN_VAULT_TIMESTAMP, System.currentTimeMillis());
        long result = db.insert(TABLE_VAULT, null, cv);
        db.close();
        return result;
    }

    public int deleteVaultItem(int id) {
        SQLiteDatabase db = this.getWritableDatabase();
        int rows = db.delete(TABLE_VAULT, COLUMN_VAULT_ID + " = ?", new String[]{String.valueOf(id)});
        db.close();
        return rows;
    }

    public List<VaultItem> getAllVaultItems() {
        List<VaultItem> items = new ArrayList<>();
        SQLiteDatabase db = this.getReadableDatabase();
        String query = "SELECT * FROM " + TABLE_VAULT + " ORDER BY " + COLUMN_VAULT_TIMESTAMP + " DESC";
        Cursor cursor = db.rawQuery(query, null);
        if (cursor.moveToFirst()) {
            do {
                int id = cursor.getInt(cursor.getColumnIndexOrThrow(COLUMN_VAULT_ID));
                String filename = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VAULT_FILENAME));
                String origPath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VAULT_ORIG_PATH));
                String storedPath = cursor.getString(cursor.getColumnIndexOrThrow(COLUMN_VAULT_STORED_PATH));
                long timestamp = cursor.getLong(cursor.getColumnIndexOrThrow(COLUMN_VAULT_TIMESTAMP));
                
                items.add(new VaultItem(id, filename, origPath, storedPath, timestamp));
            } while (cursor.moveToNext());
        }
        cursor.close();
        db.close();
        return items;
    }

    // ===================================
    // HELPER MODEL CLASSES
    // ===================================

    public static class NoteItem {
        public int id;
        public String title;
        public String content;
        public boolean isPinned;
        public long timestamp;

        public NoteItem(int id, String title, String content, boolean isPinned, long timestamp) {
            this.id = id;
            this.title = title;
            this.content = content;
            this.isPinned = isPinned;
            this.timestamp = timestamp;
        }
    }

    public static class VaultItem {
        public int id;
        public String filename;
        public String originalPath;
        public String storedPath;
        public long timestamp;

        public VaultItem(int id, String filename, String originalPath, String storedPath, long timestamp) {
            this.id = id;
            this.filename = filename;
            this.originalPath = originalPath;
            this.storedPath = storedPath;
            this.timestamp = timestamp;
        }
    }
}
