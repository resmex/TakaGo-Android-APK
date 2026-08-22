package com.takago.app.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.takago.app.data.model.NotificationRow;

import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;
import java.util.List;
import java.util.Locale;

public class NotificationRepository {

    private final SQLiteOpenHelper dbHelper;

    public NotificationRepository(SQLiteOpenHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void insertNotification(int userId, String title, String message) {
        insertNotification(userId, title, message, "general");
    }

    public void insertNotification(int userId, String title, String message, String type) {
        insertNotification(dbHelper.getWritableDatabase(), userId, title, message, type);
    }

    public void insertNotification(SQLiteDatabase db, long userId, String title, String message) {
        insertNotification(db, userId, title, message, "general");
    }

    public void insertNotification(SQLiteDatabase db, long userId, String title, String message, String type) {
        ContentValues cv = new ContentValues();
        cv.put("user_id", userId);
        cv.put("title", title);
        cv.put("message", message);
        cv.put("type", type);
        cv.put("created_at", nowTimestamp());
        db.insert("notifications", null, cv);
    }

    public List<NotificationRow> getNotificationsForUser(int userId) {
        List<NotificationRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, title, message, created_at, is_read, type FROM notifications " +
                        "WHERE user_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(userId)});
        while (c.moveToNext()) {
            NotificationRow row = new NotificationRow();
            row.id = c.getInt(0);
            row.title = c.getString(1);
            row.message = c.getString(2);
            row.createdAt = c.getString(3);
            row.isRead = c.getInt(4) != 0;
            row.type = c.getString(5);
            list.add(row);
        }
        c.close();
        return list;
    }

    public int getUnreadNotificationCount(int userId) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COUNT(*) FROM notifications WHERE user_id = ? AND is_read = 0",
                new String[]{String.valueOf(userId)});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public void markAllNotificationsRead(int userId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("is_read", 1);
        db.update("notifications", cv, "user_id = ?", new String[]{String.valueOf(userId)});
    }

    private static String nowTimestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return fmt.format(new Date());
    }
}
