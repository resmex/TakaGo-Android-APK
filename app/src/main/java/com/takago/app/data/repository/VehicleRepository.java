package com.takago.app.data.repository;

import android.content.ContentValues;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;

import com.takago.app.data.model.VehicleRow;

import java.util.ArrayList;
import java.util.List;

public class VehicleRepository {

    private static final String VEHICLE_COLUMNS = "id, plate, model, status, operator_id, ward, rejection_reason";

    private final SQLiteOpenHelper dbHelper;
    private final NotificationRepository notificationRepository;

    public VehicleRepository(SQLiteOpenHelper dbHelper, NotificationRepository notificationRepository) {
        this.dbHelper = dbHelper;
        this.notificationRepository = notificationRepository;
    }

    public long submitVehicle(String plate, String model, String capacity, int operatorId, String ward) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("plate", plate);
        cv.put("model", model);
        cv.put("capacity", capacity);
        cv.put("status", "Pending");
        cv.put("operator_id", operatorId);
        cv.put("ward", ward);
        cv.put("rejection_reason", (String) null);
        return db.insert("vehicles", null, cv);
    }

    public void updateVehicleStatus(int vehicleId, String status) {
        updateVehicleStatus(vehicleId, status, null);
    }

    public void updateVehicleStatus(int vehicleId, String status, String rejectionReason) {
        SQLiteDatabase db = dbHelper.getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        cv.put("rejection_reason", "Rejected".equals(status) ? rejectionReason : null);
        db.update("vehicles", cv, "id = ?", new String[]{String.valueOf(vehicleId)});

        Cursor c = db.rawQuery("SELECT operator_id, plate FROM vehicles WHERE id = ?", new String[]{String.valueOf(vehicleId)});
        if (c.moveToFirst()) {
            int operatorId = c.getInt(0);
            String plate = c.getString(1);
            if (operatorId > 0) {
                if ("Approved".equals(status)) {
                    notificationRepository.insertNotification(operatorId, "Vehicle approved", "Your vehicle " + plate + " has been approved.", "vehicle");
                } else if ("Rejected".equals(status)) {
                    notificationRepository.insertNotification(operatorId, "Vehicle rejected",
                            "Your vehicle " + plate + " was rejected: " + (rejectionReason != null ? rejectionReason : "no reason given"),
                            "vehicle");
                }
            }
        }
        c.close();
    }

    public List<VehicleRow> getAllVehicles() {
        List<VehicleRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + VEHICLE_COLUMNS + " FROM vehicles ORDER BY id DESC", null);
        while (c.moveToNext()) {
            list.add(readVehicleRow(c));
        }
        c.close();
        return list;
    }

    public List<VehicleRow> getVehiclesForOperator(int operatorId) {
        List<VehicleRow> list = new ArrayList<>();
        SQLiteDatabase db = dbHelper.getReadableDatabase();
        Cursor c = db.rawQuery("SELECT " + VEHICLE_COLUMNS + " FROM vehicles WHERE operator_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(operatorId)});
        while (c.moveToNext()) {
            list.add(readVehicleRow(c));
        }
        c.close();
        return list;
    }

    private VehicleRow readVehicleRow(Cursor c) {
        VehicleRow row = new VehicleRow();
        row.id = c.getInt(0);
        row.plate = c.getString(1);
        row.model = c.getString(2);
        row.status = c.getString(3);
        row.operatorId = c.getInt(4);
        row.ward = c.getString(5);
        row.rejectionReason = c.getString(6);
        return row;
    }
}
