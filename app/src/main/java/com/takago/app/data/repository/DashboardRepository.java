package com.takago.app.data.repository;

import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import java.text.SimpleDateFormat;
import java.util.Calendar;
import java.util.Locale;

public class DashboardRepository {

    private final SQLiteOpenHelper dbHelper;

    public DashboardRepository(SQLiteOpenHelper dbHelper) {
        this.dbHelper = dbHelper;
    }

    public int getTotalUsers() {
        return scalarInt("SELECT COUNT(*) FROM users", null);
    }

    public int getTotalOperators(String roleTruckOwner) {
        return scalarInt("SELECT COUNT(*) FROM users WHERE role = ?", new String[]{roleTruckOwner});
    }

    public int getTotalDrivers(String roleDriver) {
        return scalarInt("SELECT COUNT(*) FROM users WHERE role = ?", new String[]{roleDriver});
    }

    public int getTotalTrucks() {
        return scalarInt("SELECT COUNT(*) FROM vehicles WHERE status = 'Approved'", null);
    }

    public int getVehiclesActivePercent(String roleDriver) {
        int totalDrivers = getTotalDrivers(roleDriver);
        if (totalDrivers == 0) {
            return 0;
        }
        int activeDrivers = scalarInt(
                "SELECT COUNT(*) FROM users WHERE role = ? AND availability_status != 'Offline'",
                new String[]{roleDriver});
        return Math.round(100f * activeDrivers / totalDrivers);
    }

    public double getRecycledTonsMonth() {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT SUM(COALESCE(measured_weight_kg, weight_kg)) FROM pickups " +
                        "WHERE status = 'Completed' AND strftime('%Y-%m', completed_at) = strftime('%Y-%m', 'now')", null);
        double totalKg = 0;
        if (c.moveToFirst()) {
            totalKg = c.getDouble(0);
        }
        c.close();
        return totalKg / 1000.0;
    }

    public int getTotalPickupsAllTime() {
        return scalarInt("SELECT COUNT(*) FROM pickups", null);
    }

    public double getAvgRating(String roleDriver) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT AVG(rating) FROM users WHERE role = ? AND rating > 0", new String[]{roleDriver});
        double value = 0;
        if (c.moveToFirst()) {
            value = c.getDouble(0);
        }
        c.close();
        return value;
    }

    public int getSlaPercent() {
        int completed = scalarInt("SELECT COUNT(*) FROM pickups WHERE status = 'Completed'", null);
        int failed = scalarInt("SELECT COUNT(*) FROM pickups WHERE status IN ('Cancelled', 'Expired')", null);
        int resolved = completed + failed;
        if (resolved == 0) {
            return 100;
        }
        return Math.round(100f * completed / resolved);
    }

    public int getPickupCountInWard(String ward, String status) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM pickups WHERE ward = ? AND status = ?",
                new String[]{ward, status});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public int getPendingVehicleApprovalsCount() {
        return countWhere("vehicles", "status = ?", "Pending");
    }

    public int getOpenComplaintsCount() {
        return countWhere("complaints", "status = ?", "Open");
    }

    public int[] getPickupsLast7Days() {
        int[] counts = new int[7];
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        for (int i = 0; i < 7; i++) {
            String date = daysAgo(6 - i);
            Cursor c = db.rawQuery("SELECT COUNT(*) FROM pickups WHERE pickup_date = ?", new String[]{date});
            if (c.moveToFirst()) {
                counts[i] = c.getInt(0);
            }
            c.close();
        }
        return counts;
    }

    private int scalarInt(String sql, String[] args) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery(sql, args);
        int value = 0;
        if (c.moveToFirst()) {
            value = c.getInt(0);
        }
        c.close();
        return value;
    }

    private int countWhere(String table, String where, String arg) {
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where, new String[]{arg});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    private static String daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return fmt.format(cal.getTime());
    }
}
