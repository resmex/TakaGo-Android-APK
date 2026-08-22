package com.takago.app.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.takago.app.data.model.ComplaintRow;

import java.util.ArrayList;
import java.util.List;

public class ComplaintRepository {

    private final SQLiteOpenHelper dbHelper;

    public ComplaintRepository(SQLiteOpenHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public void resolveComplaint(int complaintId) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Resolved");
        db.update("complaints", cv, "id = ?", new String[]{String.valueOf(complaintId)});
    }

    public List<ComplaintRow> getAllComplaints() {
        return getComplaints(null);
    }

    public List<ComplaintRow> getComplaintsInWard(String ward) {
        int wardId = findWardId(ward);
        return getComplaints(wardId, ward);
    }

    public int getOpenComplaintsCountInWard(int wardId, String ward) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = wardId > 0
                ? db.rawQuery("SELECT COUNT(*) FROM complaints WHERE ward_id = ? AND status = 'Open'",
                new String[]{String.valueOf(wardId)})
                : db.rawQuery("SELECT COUNT(*) FROM complaints WHERE ward = ? AND status = 'Open'",
                new String[]{ward});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    private List<ComplaintRow> getComplaints(String ward) {
        return getComplaints(-1, ward);
    }

    private List<ComplaintRow> getComplaints(int wardId, String ward) {
        List<ComplaintRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = ward == null
                ? db.rawQuery("SELECT id, subject, reporter, date_text, status, ward FROM complaints ORDER BY id DESC", null)
                : wardId > 0
                ? db.rawQuery("SELECT id, subject, reporter, date_text, status, ward FROM complaints WHERE ward_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(wardId)})
                : db.rawQuery("SELECT id, subject, reporter, date_text, status, ward FROM complaints WHERE ward = ? ORDER BY id DESC",
                new String[]{ward});
        while (c.moveToNext()) {
            ComplaintRow row = new ComplaintRow();
            row.id = c.getInt(0);
            row.subject = c.getString(1);
            row.reporter = c.getString(2);
            row.dateText = c.getString(3);
            row.status = c.getString(4);
            row.ward = c.getString(5);
            list.add(row);
        }
        c.close();
        return list;
    }

    private int findWardId(String ward) {
        if (ward == null || ward.trim().isEmpty()) return -1;
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id FROM wards WHERE name_normalized = ? LIMIT 1",
                new String[]{ward.trim().replaceAll("\\s+", " ").toLowerCase()});
        try {
            return c.moveToFirst() ? c.getInt(0) : -1;
        } finally {
            c.close();
        }
    }
}
