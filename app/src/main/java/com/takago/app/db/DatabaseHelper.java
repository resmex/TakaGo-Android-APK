package com.takago.app.db;

import android.content.ContentValues;
import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.database.sqlite.SQLiteOpenHelper;
import android.util.Log;

import com.takago.app.data.local.TakaGoDatabaseContract;
import com.takago.app.data.model.ComplaintRow;
import com.takago.app.data.model.NotificationRow;
import com.takago.app.data.model.PickupRow;
import com.takago.app.data.model.PriceResult;
import com.takago.app.data.model.RouteStopRow;
import com.takago.app.data.model.PricingSettings;
import com.takago.app.data.model.UserAccount;
import com.takago.app.data.model.VehicleRow;
import com.takago.app.data.model.WardRow;
import com.takago.app.data.repository.ComplaintRepository;
import com.takago.app.data.repository.DashboardRepository;
import com.takago.app.data.repository.NotificationRepository;
import com.takago.app.data.repository.VehicleRepository;
import org.json.JSONArray;
import org.json.JSONException;
import org.json.JSONObject;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * Single SQLite database for the whole app (all roles).
 * Role names used everywhere: "Resident", "Driver", "Waste Operator", "Ward Admin", "Municipal Admin".
 *
 * Design note: drivers and waste operators are just {@code users} rows (role=Driver /
 * role="Waste Operator") rather than separate "drivers"/"operators" tables - that was already
 * the established pattern before this change, so live-location fields, vehicle links, etc. are
 * added as extra columns on {@code users} instead of introducing a parallel table that would
 * require rewriting every existing driver/operator query.
 */
public class DatabaseHelper extends SQLiteOpenHelper {

    public static final String ROLE_RESIDENT = TakaGoDatabaseContract.Roles.RESIDENT;
    public static final String ROLE_DRIVER = TakaGoDatabaseContract.Roles.DRIVER;
    public static final String ROLE_TRUCK_OWNER = TakaGoDatabaseContract.Roles.TRUCK_OWNER;
    public static final String ROLE_WARD_ADMIN = TakaGoDatabaseContract.Roles.WARD_ADMIN;
    public static final String ROLE_MUNICIPAL_ADMIN = TakaGoDatabaseContract.Roles.MUNICIPAL_ADMIN;

    private static final String DB_NAME = TakaGoDatabaseContract.DB_NAME;
    private static final int DB_VERSION = TakaGoDatabaseContract.DB_VERSION;
    private static final String TAG = "DatabaseHelper";
    private static final String WARD_GEOJSON_ASSET = "takago_dar_es_salaam_wards.geojson";
    private static final String WARD_GEOJSON_IMPORT_KEY = "ward_geojson_import_version";
    private static final int WARD_GEOJSON_IMPORT_VERSION = 1;
    private static final String GROUP_OPEN_FOR_STOPS = "OPEN_FOR_STOPS";
    private static final String GROUP_LOCKED = "LOCKED";
    private static final String GROUP_IN_PROGRESS = "IN_PROGRESS";
    private static final String GROUP_COMPLETED = "COMPLETED";
    private static final String BATCH_ASSIGNED_TO_ROUTE = "ASSIGNED_TO_ROUTE";
    private static final String BATCH_PENDING_NEXT = "PENDING_NEXT_BATCH";
    private static final String BATCH_PENDING_ASSIGNMENT = "PENDING_ASSIGNMENT";
    private static final double DEFAULT_NEARBY_RADIUS_METERS = 200.0;
    private static final int DEFAULT_JOIN_WINDOW_MINUTES = 30;
    private static final int DEFAULT_MAXIMUM_STOPS = 10;
    private static final int DEFAULT_MAX_ADDED_DELAY_MINUTES = 10;
    private static final int DEFAULT_ROUTE_DURATION_LIMIT_MINUTES = 90;
    private static final double DEFAULT_CAPACITY_THRESHOLD_PERCENT = 90.0;

    private final NotificationRepository notificationRepository = new NotificationRepository(this);
    private final VehicleRepository vehicleRepository = new VehicleRepository(this, notificationRepository);
    private final ComplaintRepository complaintRepository = new ComplaintRepository(this);
    private final DashboardRepository dashboardRepository = new DashboardRepository(this);
    private final Context appContext;

    public DatabaseHelper(Context context) {
        super(context.getApplicationContext(), DB_NAME, null, DB_VERSION);
        appContext = context.getApplicationContext();
    }

    @Override
    public void onCreate(SQLiteDatabase db) {
        db.execSQL("CREATE TABLE users (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "name TEXT, email TEXT, phone TEXT, password TEXT, role TEXT, " +
                "status TEXT DEFAULT 'Active', " +
                "rating REAL DEFAULT 0, total_distance_km REAL DEFAULT 0, " +
                "license_info TEXT, vehicle_info TEXT, " +
                "trips_count INTEGER DEFAULT 0, driver_plate TEXT, availability_status TEXT DEFAULT 'Active', " +
                "fleet_trucks INTEGER, fleet_drivers INTEGER, fleet_earnings_week TEXT, fleet_earnings_change TEXT, " +
                "ward TEXT, profile_image_path TEXT, " +
                "operator_id INTEGER, vehicle_id INTEGER, " +
                "latitude REAL, longitude REAL, last_location_update TEXT, " +
                "ward_lat REAL, ward_lng REAL, ward_radius_km REAL)");

        db.execSQL("CREATE TABLE vehicles (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "plate TEXT, model TEXT, capacity TEXT, status TEXT, " +
                "operator_id INTEGER, ward TEXT, rejection_reason TEXT)");

        db.execSQL("CREATE TABLE complaints (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "subject TEXT, reporter TEXT, date_text TEXT, status TEXT, ward TEXT)");

        db.execSQL("CREATE TABLE pickups (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "code TEXT, ward TEXT, category TEXT, status TEXT, pickup_date TEXT, " +
                "resident_id INTEGER, driver_id INTEGER, weight_kg REAL, time_text TEXT, " +
                "distance_km REAL, eta_min INTEGER, resident_display_name TEXT, " +
                "latitude REAL, longitude REAL, address TEXT, house_number TEXT, " +
                "street_name TEXT, formatted_address TEXT, photo_path TEXT, " +
                "created_at TEXT, completed_at TEXT, " +
                "assigned_vehicle_id INTEGER, assignment_type TEXT, driver_response_status TEXT, " +
                "accepted_at TEXT, timeout_at TEXT, cancel_reason TEXT, " +
                "waste_type TEXT DEFAULT 'Household', " +
                "estimated_price_min REAL, estimated_price_max REAL, " +
                "measured_weight_kg REAL, included_weight_kg REAL, rate_per_kg REAL, booking_fee REAL, " +
                "distance_fee REAL, waste_type_multiplier REAL, final_price REAL, " +
                "scale_photo_path TEXT, pricing_status TEXT DEFAULT 'Estimated', " +
                "payment_status TEXT DEFAULT 'Unpaid', proof_photo_path TEXT)");

        db.execSQL("CREATE TABLE notifications (" +
                "id INTEGER PRIMARY KEY AUTOINCREMENT, " +
                "user_id INTEGER, title TEXT, message TEXT, type TEXT, created_at TEXT, is_read INTEGER DEFAULT 0)");

        db.execSQL("CREATE TABLE pricing_settings (" +
                "id INTEGER PRIMARY KEY CHECK (id = 1), " +
                "booking_fee REAL, included_weight_kg REAL, rate_per_kg REAL, " +
                "distance_free_km REAL, distance_fee_per_km REAL, " +
                "mult_household REAL, mult_garden REAL, mult_recyclables REAL, " +
                "mult_construction REAL, mult_electronic REAL)");

        seedPricingSettings(db);
        ensureLocationRoutingSchema(db);
        resetOfficialDarDemoData(db);
        importWardBoundariesFromAssets(appContext, db);
        removeWardsWithoutBoundaries(db);
        seedTestUsersOnly(db);
    }

    @Override
    public void onUpgrade(SQLiteDatabase db, int oldVersion, int newVersion) {
        if (oldVersion < 14) {
            ensureLocationRoutingSchema(db);
            resetOfficialDarDemoData(db);
        }
        if (oldVersion < 15) {
            ensureLocationRoutingSchema(db);
            importWardBoundariesFromAssets(appContext, db);
        }
        if (oldVersion < 16) {
            ensureLocationRoutingSchema(db);
            removeWardsWithoutBoundaries(db);
        }
        if (oldVersion < 17) {
            ensureLocationRoutingSchema(db);
        }
        if (oldVersion < 18) {
            ensureLocationRoutingSchema(db);
            seedTestUsersOnly(db);
        }
        if (oldVersion < 19) {
            ensureLocationRoutingSchema(db);
            seedTestUsersOnly(db);
        }
        if (oldVersion < 20) {
            ensureLocationRoutingSchema(db);
        }
        if (oldVersion < 21) {
            ensureLocationRoutingSchema(db);
        }
    }

    /** Non-destructive v11 migration. Every ALTER is guarded by PRAGMA table_info. */
    private void ensureLocationRoutingSchema(SQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        try {
            db.execSQL("CREATE TABLE IF NOT EXISTS wards (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL, " +
                    "name_normalized TEXT NOT NULL UNIQUE, municipality TEXT, boundary_geojson TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS municipalities (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, name TEXT NOT NULL UNIQUE, " +
                    "code TEXT NOT NULL UNIQUE, region TEXT NOT NULL, " +
                    "is_active INTEGER NOT NULL DEFAULT 1, created_at TEXT, updated_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS service_areas (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, ward_id INTEGER NOT NULL, " +
                    "operator_id INTEGER, boundary_geojson TEXT, active INTEGER DEFAULT 1)");
            db.execSQL("CREATE TABLE IF NOT EXISTS waste_operator_wards (" +
                    "operator_id INTEGER NOT NULL, ward_id INTEGER NOT NULL, " +
                    "PRIMARY KEY(operator_id, ward_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS request_groups (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, driver_id INTEGER, vehicle_id INTEGER, " +
                    "ward_id INTEGER, operator_id INTEGER, status TEXT DEFAULT 'Open', " +
                    "encoded_polyline TEXT, distance_meters INTEGER, duration_seconds INTEGER, " +
                    "calculated_at TEXT, created_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS group_members (" +
                    "group_id INTEGER NOT NULL, pickup_id INTEGER NOT NULL UNIQUE, joined_at TEXT, " +
                    "PRIMARY KEY(group_id, pickup_id))");
            db.execSQL("CREATE TABLE IF NOT EXISTS route_stops (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, group_id INTEGER NOT NULL, " +
                    "pickup_id INTEGER NOT NULL UNIQUE, stop_order INTEGER NOT NULL, " +
                    "status TEXT DEFAULT 'Pending', latitude REAL, longitude REAL, eta_seconds INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS request_status_history (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, pickup_id INTEGER NOT NULL, " +
                    "status TEXT NOT NULL, changed_at TEXT NOT NULL, changed_by INTEGER)");
            db.execSQL("CREATE TABLE IF NOT EXISTS app_settings (" +
                    "key TEXT PRIMARY KEY, value TEXT, updated_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS hotspot_recommendations (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, municipality_id INTEGER, ward_id INTEGER, " +
                    "operator_id INTEGER, latitude REAL, longitude REAL, request_count INTEGER, " +
                    "status TEXT DEFAULT 'Recommended', created_at TEXT, updated_at TEXT)");
            db.execSQL("CREATE TABLE IF NOT EXISTS collection_schedules (" +
                    "id INTEGER PRIMARY KEY AUTOINCREMENT, hotspot_id INTEGER, municipality_id INTEGER, " +
                    "ward_id INTEGER, operator_id INTEGER, schedule_text TEXT, active INTEGER DEFAULT 1, " +
                    "created_at TEXT, updated_at TEXT)");

            addColumnIfMissing(db, "users", "ward_id", "INTEGER");
            addColumnIfMissing(db, "users", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "users", "bearing", "REAL");
            addColumnIfMissing(db, "users", "speed", "REAL");
            addColumnIfMissing(db, "users", "accuracy", "REAL");
            addColumnIfMissing(db, "users", "house_number", "TEXT");
            addColumnIfMissing(db, "users", "street_name", "TEXT");
            addColumnIfMissing(db, "users", "place_name", "TEXT");
            addColumnIfMissing(db, "users", "formatted_address", "TEXT");
            addColumnIfMissing(db, "users", "plus_code", "TEXT");
            addColumnIfMissing(db, "users", "location_ward_name", "TEXT");
            addColumnIfMissing(db, "users", "last_location_updated_at", "TEXT");
            addColumnIfMissing(db, "vehicles", "ward_id", "INTEGER");
            addColumnIfMissing(db, "vehicles", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "vehicles", "capacity_weight_kg", "REAL");
            addColumnIfMissing(db, "vehicles", "capacity_volume_m3", "REAL");
            addColumnIfMissing(db, "pickups", "place_id", "TEXT");
            addColumnIfMissing(db, "pickups", "house_number", "TEXT");
            addColumnIfMissing(db, "pickups", "street_name", "TEXT");
            addColumnIfMissing(db, "pickups", "formatted_address", "TEXT");
            addColumnIfMissing(db, "pickups", "place_name", "TEXT");
            addColumnIfMissing(db, "pickups", "plus_code", "TEXT");
            addColumnIfMissing(db, "pickups", "last_location_updated_at", "TEXT");
            addColumnIfMissing(db, "pickups", "ward_id", "INTEGER");
            addColumnIfMissing(db, "pickups", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "pickups", "group_id", "INTEGER");
            addColumnIfMissing(db, "pickups", "stop_order", "INTEGER");
            addColumnIfMissing(db, "pickups", "encoded_polyline", "TEXT");
            addColumnIfMissing(db, "pickups", "route_distance_meters", "INTEGER");
            addColumnIfMissing(db, "pickups", "proof_photo_path", "TEXT");
            addColumnIfMissing(db, "pickups", "route_duration_seconds", "INTEGER");
            addColumnIfMissing(db, "pickups", "route_calculated_at", "TEXT");
            addColumnIfMissing(db, "pickups", "batching_status", "TEXT");
            addColumnIfMissing(db, "pickups", "submitted_at", "TEXT");
            addColumnIfMissing(db, "complaints", "ward", "TEXT");
            addColumnIfMissing(db, "complaints", "ward_id", "INTEGER");
            addColumnIfMissing(db, "complaints", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "wards", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "wards", "normalized_name", "TEXT");
            addColumnIfMissing(db, "wards", "boundary_status", "TEXT");
            addColumnIfMissing(db, "wards", "source_shape_id", "TEXT");
            addColumnIfMissing(db, "wards", "is_active", "INTEGER NOT NULL DEFAULT 1");
            addColumnIfMissing(db, "wards", "assigned_operator_id", "INTEGER");
            addColumnIfMissing(db, "wards", "created_at", "TEXT");
            addColumnIfMissing(db, "wards", "updated_at", "TEXT");
            addColumnIfMissing(db, "municipalities", "normalized_name", "TEXT");
            addColumnIfMissing(db, "service_areas", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "service_areas", "boundary_status", "TEXT");
            addColumnIfMissing(db, "service_areas", "source_shape_id", "TEXT");
            addColumnIfMissing(db, "request_groups", "municipality_id", "INTEGER");
            addColumnIfMissing(db, "request_groups", "join_deadline", "TEXT");
            addColumnIfMissing(db, "request_groups", "route_locked_at", "TEXT");
            addColumnIfMissing(db, "request_groups", "maximum_stops", "INTEGER");
            addColumnIfMissing(db, "route_stops", "request_id", "INTEGER");
            addColumnIfMissing(db, "route_stops", "eta", "TEXT");
            addColumnIfMissing(db, "route_stops", "added_at", "TEXT");
            db.execSQL("UPDATE wards SET normalized_name = name_normalized " +
                    "WHERE normalized_name IS NULL OR TRIM(normalized_name) = ''");
            db.execSQL("UPDATE municipalities SET normalized_name = LOWER(TRIM(name)) " +
                    "WHERE normalized_name IS NULL OR TRIM(normalized_name) = ''");
            db.execSQL("UPDATE pickups SET submitted_at = created_at " +
                    "WHERE submitted_at IS NULL OR TRIM(submitted_at) = ''");

            db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_ward_role ON users(ward_id, role)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_users_municipality_role ON users(municipality_id, role)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pickups_ward_status ON pickups(ward_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pickups_municipality_status ON pickups(municipality_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pickups_driver_status ON pickups(driver_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_wards_municipality ON wards(municipality_id, name_normalized)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_wards_municipality_normalized_name ON wards(municipality_id, normalized_name)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_wards_source_shape_id ON wards(source_shape_id)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_municipalities_code ON municipalities(code)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_route_stops_group_order ON route_stops(group_id, stop_order)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_groups_batching ON request_groups(municipality_id, ward_id, operator_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_pickups_batching ON pickups(municipality_id, ward_id, batching_status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_hotspots_ward ON hotspot_recommendations(municipality_id, ward_id, status)");
            db.execSQL("CREATE INDEX IF NOT EXISTS idx_status_history_pickup ON request_status_history(pickup_id, changed_at)");
            seedBatchingSettings(db);
            if (ownsTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    private static void addColumnIfMissing(SQLiteDatabase db, String table, String column,
                                           String definition) {
        if (!hasColumn(db, table, column)) {
            db.execSQL("ALTER TABLE " + table + " ADD COLUMN " + column + " " + definition);
        }
    }

    private static boolean hasColumn(SQLiteDatabase db, String table, String column) {
        Cursor cursor = db.rawQuery("PRAGMA table_info(" + table + ")", null);
        try {
            int nameIndex = cursor.getColumnIndex("name");
            while (cursor.moveToNext()) {
                if (column.equalsIgnoreCase(cursor.getString(nameIndex))) return true;
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    public void importWardBoundariesFromAssets(Context context) {
        SQLiteDatabase db = getWritableDatabase();
        ensureLocationRoutingSchema(db);
        importWardBoundariesFromAssets(context.getApplicationContext(), db);
    }

    private void importWardBoundariesFromAssets(Context context, SQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        ImportStats stats = new ImportStats();
        try {
            String importedVersion = getSetting(db, WARD_GEOJSON_IMPORT_KEY);
            if (importedVersion != null) {
                try {
                    if (Integer.parseInt(importedVersion) >= WARD_GEOJSON_IMPORT_VERSION) {
                        if (ownsTransaction) db.setTransactionSuccessful();
                        Log.i(TAG, "Ward GeoJSON import already completed at version " + importedVersion);
                        return;
                    }
                } catch (NumberFormatException ignored) {
                    // Bad marker values are treated as missing so the one-time import can repair itself.
                }
            }

            JSONObject root = new JSONObject(readAssetText(context, WARD_GEOJSON_ASSET));
            if (!"FeatureCollection".equals(root.optString("type"))) {
                throw new JSONException("Ward asset must be a FeatureCollection");
            }
            JSONArray features = root.getJSONArray("features");
            Set<String> seenShapeIds = new HashSet<>();
            Set<String> duplicateShapeIds = new HashSet<>();
            Map<Integer, Integer> mappedByMunicipality = new HashMap<>();
            Map<String, WardImportRecord> wardRecords = new LinkedHashMap<>();

            for (int i = 0; i < features.length(); i++) {
                JSONObject feature = features.optJSONObject(i);
                String featureName = "feature #" + i;
                try {
                    if (feature == null || !"Feature".equals(feature.optString("type"))) {
                        throw new JSONException("Missing Feature object");
                    }
                    JSONObject props = feature.getJSONObject("properties");
                    String wardName = requiredProperty(props, "ward_name");
                    featureName = wardName;
                    String normalizedWard = normalizeWardName(requiredProperty(props, "normalized_ward_name"));
                    String municipalityCode = requiredProperty(props, "municipality_code");
                    String sourceShapeId = requiredProperty(props, "source_shape_id");
                    String boundaryStatus = requiredProperty(props, "boundary_status");
                    requiredProperty(props, "municipality_name");
                    JSONObject geometry = feature.getJSONObject("geometry");
                    String geometryType = geometry.optString("type");
                    if (!"Polygon".equals(geometryType) && !"MultiPolygon".equals(geometryType)) {
                        throw new JSONException("Unsupported geometry type " + geometryType);
                    }
                    geometry.getJSONArray("coordinates");
                    if (!seenShapeIds.add(sourceShapeId)) duplicateShapeIds.add(sourceShapeId);

                    String key = municipalityCode + "|" + normalizedWard;
                    WardImportRecord record = wardRecords.get(key);
                    if (record == null) {
                        record = new WardImportRecord(wardName, normalizedWard, municipalityCode);
                        wardRecords.put(key, record);
                    }
                    record.geometries.add(geometry);
                    record.sourceShapeIds.add(sourceShapeId);
                    if ("MAPPED".equalsIgnoreCase(boundaryStatus)) record.boundaryStatus = "MAPPED";
                } catch (JSONException | IllegalArgumentException e) {
                    stats.invalidFeatures++;
                    Log.e(TAG, "Invalid ward GeoJSON " + featureName + ": " + e.getMessage());
                    throw e;
                }
            }

            for (WardImportRecord record : wardRecords.values()) {
                try {
                    String municipalityName = municipalityNameForCode(record.municipalityCode);
                    int municipalityId = upsertMunicipality(db, municipalityName, record.municipalityCode, stats);
                    int wardId = findWardIdByMunicipalityAndNormalized(db, municipalityId, record.normalizedWard);
                    String geometryJson = mergeWardGeometries(record.geometries);
                    String sourceShapeIds = joinStrings(record.sourceShapeIds);
                    ContentValues values = new ContentValues();
                    values.put("name", record.wardName.trim().replaceAll("\\s+", " "));
                    values.put("name_normalized", record.normalizedWard);
                    values.put("normalized_name", record.normalizedWard);
                    values.put("municipality", municipalityName);
                    values.put("municipality_id", municipalityId);
                    values.put("boundary_geojson", geometryJson);
                    values.put("boundary_status", record.boundaryStatus);
                    values.put("source_shape_id", sourceShapeIds);
                    values.put("is_active", 1);
                    values.put("updated_at", nowTimestamp());
                    if (wardId > 0) {
                        db.update("wards", values, "id = ?", new String[]{String.valueOf(wardId)});
                        stats.wardsUpdated++;
                    } else {
                        values.put("created_at", nowTimestamp());
                        long inserted = db.insertWithOnConflict("wards", null, values, SQLiteDatabase.CONFLICT_IGNORE);
                        if (inserted <= 0) {
                            throw new JSONException("Ward insert conflicted for " + municipalityName + " / " + record.wardName);
                        }
                        wardId = (int) inserted;
                        stats.wardsInserted++;
                    }
                    upsertServiceAreaBoundary(db, municipalityId, wardId, geometryJson,
                            record.boundaryStatus, sourceShapeIds);
                    mappedByMunicipality.put(municipalityId, mappedByMunicipality.getOrDefault(municipalityId, 0) + 1);
                } catch (JSONException | IllegalArgumentException e) {
                    stats.invalidFeatures++;
                    Log.e(TAG, "Invalid ward GeoJSON " + record.wardName + ": " + e.getMessage());
                    throw e;
                }
            }

            putSetting(db, WARD_GEOJSON_IMPORT_KEY, String.valueOf(WARD_GEOJSON_IMPORT_VERSION));
            if (ownsTransaction) db.setTransactionSuccessful();
            Log.i(TAG, "Ward GeoJSON import complete. municipalities inserted=" + stats.municipalitiesInserted
                    + ", municipalities updated=" + stats.municipalitiesUpdated
                    + ", wards inserted=" + stats.wardsInserted
                    + ", wards updated=" + stats.wardsUpdated
                    + ", invalid features=" + stats.invalidFeatures
                    + ", duplicate source_shape_id values=" + duplicateShapeIds.size()
                    + ", mapped per municipality=" + mappedByMunicipality);
        } catch (IOException | JSONException | IllegalArgumentException e) {
            Log.e(TAG, "Ward GeoJSON import failed and was rolled back: " + e.getMessage(), e);
            if (!ownsTransaction) {
                throw new IllegalStateException("Ward GeoJSON import failed", e);
            }
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    private static String readAssetText(Context context, String assetName) throws IOException {
        StringBuilder builder = new StringBuilder();
        try (InputStream in = context.getAssets().open(assetName);
             BufferedReader reader = new BufferedReader(
                     new InputStreamReader(in, StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) builder.append(line);
        }
        return builder.toString();
    }

    private static String requiredProperty(JSONObject object, String key) throws JSONException {
        String value = object.optString(key, null);
        if (value == null || value.trim().isEmpty()) {
            throw new JSONException("Missing required property " + key);
        }
        return value;
    }

    private static String mergeWardGeometries(List<JSONObject> geometries) throws JSONException {
        if (geometries.size() == 1) return geometries.get(0).toString();
        JSONArray multiPolygonCoordinates = new JSONArray();
        for (JSONObject geometry : geometries) {
            JSONArray coordinates = geometry.getJSONArray("coordinates");
            if ("Polygon".equals(geometry.optString("type"))) {
                multiPolygonCoordinates.put(coordinates);
            } else if ("MultiPolygon".equals(geometry.optString("type"))) {
                for (int i = 0; i < coordinates.length(); i++) {
                    multiPolygonCoordinates.put(coordinates.getJSONArray(i));
                }
            } else {
                throw new JSONException("Unsupported geometry type " + geometry.optString("type"));
            }
        }
        JSONObject merged = new JSONObject();
        merged.put("type", "MultiPolygon");
        merged.put("coordinates", multiPolygonCoordinates);
        return merged.toString();
    }

    private static String joinStrings(List<String> values) {
        StringBuilder builder = new StringBuilder();
        for (String value : values) {
            if (builder.length() > 0) builder.append(",");
            builder.append(value);
        }
        return builder.toString();
    }

    private int upsertMunicipality(SQLiteDatabase db, String name, String code, ImportStats stats) {
        int id = findMunicipalityIdByCodeOrName(db, code, normalizeWardName(name));
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("code", code);
        values.put("region", "Dar es Salaam");
        values.put("normalized_name", normalizeWardName(name));
        values.put("is_active", 1);
        values.put("updated_at", nowTimestamp());
        if (id > 0) {
            db.update("municipalities", values, "id = ?", new String[]{String.valueOf(id)});
            stats.municipalitiesUpdated++;
            return id;
        }
        values.put("created_at", nowTimestamp());
        stats.municipalitiesInserted++;
        return (int) db.insert("municipalities", null, values);
    }

    private int findMunicipalityIdByCodeOrName(SQLiteDatabase db, String code, String normalizedName) {
        Cursor cursor = db.rawQuery(
                "SELECT id FROM municipalities WHERE code = ? OR normalized_name = ? " +
                        "OR LOWER(TRIM(name)) = ? LIMIT 1",
                new String[]{code, normalizedName, normalizedName});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private int findWardIdByMunicipalityAndNormalized(SQLiteDatabase db, int municipalityId,
                                                       String normalizedWard) {
        Cursor cursor = db.rawQuery(
                "SELECT id FROM wards WHERE municipality_id = ? " +
                        "AND (normalized_name = ? OR name_normalized = ?) LIMIT 1",
                new String[]{String.valueOf(municipalityId), normalizedWard, normalizedWard});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private void upsertServiceAreaBoundary(SQLiteDatabase db, int municipalityId, int wardId,
                                           String geometryJson, String boundaryStatus,
                                           String sourceShapeId) {
        ContentValues values = new ContentValues();
        values.put("municipality_id", municipalityId);
        values.put("ward_id", wardId);
        values.put("boundary_geojson", geometryJson);
        values.put("boundary_status", boundaryStatus);
        values.put("source_shape_id", sourceShapeId);
        values.put("active", 1);
        int updated = db.update("service_areas", values, "ward_id = ? AND operator_id IS NULL",
                new String[]{String.valueOf(wardId)});
        if (updated == 0) db.insert("service_areas", null, values);
    }

    private static String municipalityNameForCode(String code) {
        switch (code) {
            case "ILALA_MC":
                return "Ilala MC";
            case "KINONDONI_MC":
                return "Kinondoni MC";
            case "UBUNGO_MC":
                return "Ubungo MC";
            case "TEMEKE_MC":
                return "Temeke MC";
            case "KIGAMBONI_MC":
                return "Kigamboni MC";
            default:
                throw new IllegalArgumentException("Unexpected municipality_code " + code);
        }
    }

    private static String getSetting(SQLiteDatabase db, String key) {
        Cursor cursor = db.rawQuery("SELECT value FROM app_settings WHERE [key] = ? LIMIT 1",
                new String[]{key});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    private static void putSetting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated_at", nowTimestamp());
        db.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_REPLACE);
    }

    private static void seedBatchingSettings(SQLiteDatabase db) {
        putDefaultSetting(db, "batch_nearby_radius_meters", String.valueOf((int) DEFAULT_NEARBY_RADIUS_METERS));
        putDefaultSetting(db, "batch_join_window_minutes", String.valueOf(DEFAULT_JOIN_WINDOW_MINUTES));
        putDefaultSetting(db, "batch_maximum_stops", String.valueOf(DEFAULT_MAXIMUM_STOPS));
        putDefaultSetting(db, "batch_max_added_delay_minutes", String.valueOf(DEFAULT_MAX_ADDED_DELAY_MINUTES));
        putDefaultSetting(db, "batch_route_duration_limit_minutes", String.valueOf(DEFAULT_ROUTE_DURATION_LIMIT_MINUTES));
        putDefaultSetting(db, "batch_capacity_threshold_percent", String.valueOf((int) DEFAULT_CAPACITY_THRESHOLD_PERCENT));
    }

    private static void putDefaultSetting(SQLiteDatabase db, String key, String value) {
        ContentValues values = new ContentValues();
        values.put("key", key);
        values.put("value", value);
        values.put("updated_at", nowTimestamp());
        db.insertWithOnConflict("app_settings", null, values, SQLiteDatabase.CONFLICT_IGNORE);
    }

    private static final class ImportStats {
        int municipalitiesInserted;
        int municipalitiesUpdated;
        int wardsInserted;
        int wardsUpdated;
        int invalidFeatures;
    }

    private static final class WardImportRecord {
        final String wardName;
        final String normalizedWard;
        final String municipalityCode;
        final List<JSONObject> geometries = new ArrayList<>();
        final List<String> sourceShapeIds = new ArrayList<>();
        String boundaryStatus = "NOT_MAPPED";

        WardImportRecord(String wardName, String normalizedWard, String municipalityCode) {
            this.wardName = wardName;
            this.normalizedWard = normalizedWard;
            this.municipalityCode = municipalityCode;
        }
    }

    private void removeWardsWithoutBoundaries(SQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        try {
            List<String> wardIds = new ArrayList<>();
            Cursor cursor = db.rawQuery(
                    "SELECT id FROM wards WHERE boundary_geojson IS NULL OR TRIM(boundary_geojson) = '' " +
                            "OR COALESCE(boundary_status, 'NOT_MAPPED') <> 'MAPPED'",
                    null);
            try {
                while (cursor.moveToNext()) wardIds.add(String.valueOf(cursor.getInt(0)));
            } finally {
                cursor.close();
            }
            if (wardIds.isEmpty()) {
                if (ownsTransaction) db.setTransactionSuccessful();
                return;
            }

            String placeholders = placeholders(wardIds.size());
            String[] args = wardIds.toArray(new String[0]);
            db.delete("users", "role = ? AND ward_id IN (" + placeholders + ")",
                    prependArg(ROLE_WARD_ADMIN, args));
            clearWardReferences(db, "users", placeholders, args);
            clearWardReferences(db, "vehicles", placeholders, args);
            clearWardReferences(db, "pickups", placeholders, args);
            clearWardReferences(db, "complaints", placeholders, args);
            db.delete("service_areas", "ward_id IN (" + placeholders + ")", args);
            db.delete("waste_operator_wards", "ward_id IN (" + placeholders + ")", args);
            db.delete("wards", "id IN (" + placeholders + ")", args);
            Log.i(TAG, "Removed wards without mapped boundaries: " + wardIds.size());
            if (ownsTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    private static void clearWardReferences(SQLiteDatabase db, String table,
                                            String placeholders, String[] wardIds) {
        ContentValues values = new ContentValues();
        values.putNull("ward_id");
        values.putNull("municipality_id");
        db.update(table, values, "ward_id IN (" + placeholders + ")", wardIds);
    }

    private static String placeholders(int count) {
        StringBuilder builder = new StringBuilder();
        for (int i = 0; i < count; i++) {
            if (i > 0) builder.append(",");
            builder.append("?");
        }
        return builder.toString();
    }

    private static String[] prependArg(String first, String[] rest) {
        String[] args = new String[rest.length + 1];
        args[0] = first;
        System.arraycopy(rest, 0, args, 1, rest.length);
        return args;
    }

    private void seedTestUsersOnly(SQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        try {
            List<TestWardSpec> specs = testWardSpecs();
            Set<String> targetEmails = testSeedEmails();
            cleanOldDemoUsersAndVehicles(db, targetEmails);

            for (TestWardSpec spec : specs) {
                int municipalityId = findMunicipalityIdByCodeOrName(db,
                        spec.municipalityCode, normalizeWardName(spec.municipalityName));
                int wardId = findWardIdByMunicipalityAndNormalized(db,
                        municipalityId, normalizeWardName(spec.wardName));
                if (municipalityId <= 0 || wardId <= 0) {
                    Log.e(TAG, "Skipping test seed for missing ward " + spec.wardName);
                    continue;
                }
                List<double[]> points = pointsInsideWard(db, wardId, 8);
                upsertTestUser(db, spec.municipalAdminName(), spec.municipalAdminEmail,
                        ROLE_MUNICIPAL_ADMIN, municipalityId, -1, "All Wards", 0, 0, -1, -1);

                int operatorId = (int) upsertTestUser(db, spec.operatorName(), spec.operatorEmail,
                        ROLE_TRUCK_OWNER, municipalityId, wardId, spec.wardName, 0, 0, -1, -1);
                ContentValues wardValues = new ContentValues();
                wardValues.put("assigned_operator_id", operatorId);
                db.update("wards", wardValues, "id = ?", new String[]{String.valueOf(wardId)});

                int adminId = (int) upsertTestUser(db, spec.wardAdminName(), spec.wardAdminEmail,
                        ROLE_WARD_ADMIN, municipalityId, wardId, spec.wardName, 0, 0, -1, -1);
                clearOtherWardAdmins(db, wardId, adminId);

                for (int i = 1; i <= 2; i++) {
                    double[] point = points.get(i - 1);
                    String email = spec.slug + ".driver" + i + "@takago.com";
                    int driverId = (int) upsertTestUser(db, spec.wardName + " Driver " + i, email,
                            ROLE_DRIVER, municipalityId, wardId, spec.wardName, point[0], point[1],
                            operatorId, -1);
                    int vehicleId = upsertTestVehicle(db, spec, i, operatorId, municipalityId, wardId);
                    ContentValues driverValues = new ContentValues();
                    driverValues.put("vehicle_id", vehicleId);
                    driverValues.put("driver_plate", testVehiclePlate(spec.slug, i));
                    driverValues.put("vehicle_info", i == 1 ? "Small Truck" : "Large Truck");
                    db.update("users", driverValues, "id = ?", new String[]{String.valueOf(driverId)});
                }

                for (int i = 1; i <= 6; i++) {
                    double[] point = points.get(i + 1);
                    upsertTestUser(db, spec.wardName + " Resident " + i,
                            spec.slug + ".resident" + i + "@takago.com",
                            ROLE_RESIDENT, municipalityId, wardId, spec.wardName,
                            point[0], point[1], -1, -1);
                }
            }
            if (ownsTransaction) db.setTransactionSuccessful();
            Log.i(TAG, "Seeded takaGo test users only: municipal admins=5, operators=6, ward admins=6, drivers=12, vehicles=12, residents=36");
        } catch (Exception e) {
            Log.e(TAG, "Test user seed skipped to keep app startup safe: " + e.getMessage(), e);
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    private void cleanOldDemoUsersAndVehicles(SQLiteDatabase db, Set<String> targetEmails) {
        List<String> demoUserIds = new ArrayList<>();
        Cursor cursor = db.rawQuery("SELECT id, email FROM users WHERE email LIKE '%@takago.com'", null);
        try {
            while (cursor.moveToNext()) {
                String email = cursor.getString(1);
                if (!targetEmails.contains(email)) demoUserIds.add(String.valueOf(cursor.getInt(0)));
            }
        } finally {
            cursor.close();
        }
        db.delete("vehicles", "plate LIKE 'TEST-%'", null);
        if (!demoUserIds.isEmpty()) {
            String placeholders = placeholders(demoUserIds.size());
            String[] args = demoUserIds.toArray(new String[0]);
            db.delete("notifications", "user_id IN (" + placeholders + ")", args);
            db.delete("vehicles", "operator_id IN (" + placeholders + ")", args);
            db.delete("users", "id IN (" + placeholders + ")", args);
        }
    }

    private long upsertTestUser(SQLiteDatabase db, String name, String email, String role,
                                int municipalityId, int wardId, String ward, double latitude,
                                double longitude, int operatorId, int vehicleId) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("phone", "");
        values.put("password", hashPassword(DEMO_PASSWORD));
        values.put("role", role);
        values.put("status", "Active");
        values.put("ward", ward);
        values.put("municipality_id", municipalityId);
        if (wardId > 0) values.put("ward_id", wardId);
        else values.putNull("ward_id");
        if (latitude != 0 || longitude != 0) {
            values.put("latitude", latitude);
            values.put("longitude", longitude);
            values.put("last_location_update", nowTimestamp());
        }
        if (ROLE_DRIVER.equals(role)) values.put("availability_status", "Available");
        if (operatorId > 0) values.put("operator_id", operatorId);
        if (vehicleId > 0) values.put("vehicle_id", vehicleId);

        int existingId = findUserIdByEmail(db, email);
        if (existingId > 0) {
            db.update("users", values, "id = ?", new String[]{String.valueOf(existingId)});
            return existingId;
        }
        return db.insert("users", null, values);
    }

    private int upsertTestVehicle(SQLiteDatabase db, TestWardSpec spec, int driverIndex,
                                  int operatorId, int municipalityId, int wardId) {
        String plate = testVehiclePlate(spec.slug, driverIndex);
        ContentValues values = new ContentValues();
        values.put("plate", plate);
        values.put("model", driverIndex == 1 ? "Small Truck" : "Large Truck");
        values.put("capacity", driverIndex == 1 ? "200 KG" : "500 KG");
        values.put("capacity_weight_kg", driverIndex == 1 ? 200 : 500);
        values.put("capacity_volume_m3", driverIndex == 1 ? 4 : 10);
        values.put("status", "Approved");
        values.put("operator_id", operatorId);
        values.put("municipality_id", municipalityId);
        values.put("ward_id", wardId);
        values.put("ward", spec.wardName);
        values.putNull("rejection_reason");
        Cursor cursor = db.rawQuery("SELECT id FROM vehicles WHERE plate = ? LIMIT 1", new String[]{plate});
        try {
            if (cursor.moveToFirst()) {
                int id = cursor.getInt(0);
                db.update("vehicles", values, "id = ?", new String[]{String.valueOf(id)});
                return id;
            }
        } finally {
            cursor.close();
        }
        return (int) db.insert("vehicles", null, values);
    }

    private void clearOtherWardAdmins(SQLiteDatabase db, int wardId, int keepAdminId) {
        ContentValues values = new ContentValues();
        values.putNull("ward_id");
        values.putNull("municipality_id");
        values.put("ward", "Unassigned");
        db.update("users", values, "role = ? AND ward_id = ? AND id <> ?",
                new String[]{ROLE_WARD_ADMIN, String.valueOf(wardId), String.valueOf(keepAdminId)});
    }

    private int findUserIdByEmail(SQLiteDatabase db, String email) {
        Cursor cursor = db.rawQuery("SELECT id FROM users WHERE email = ? LIMIT 1", new String[]{email});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private List<double[]> pointsInsideWard(SQLiteDatabase db, int wardId, int count) {
        String boundary = null;
        Cursor cursor = db.rawQuery(
                "SELECT boundary_geojson FROM wards WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(wardId)});
        try {
            if (cursor.moveToFirst()) boundary = cursor.getString(0);
        } finally {
            cursor.close();
        }
        if (boundary == null || boundary.trim().isEmpty()) {
            throw new IllegalStateException("Ward boundary is missing for seed ward_id=" + wardId);
        }
        List<double[]> points = new ArrayList<>();
        double[] center = wardCenter(boundary);
        double[][] offsets = {
                {-0.0006, -0.0002}, {0.0006, 0.0002},
                {0.0000, 0.0000}, {0.0004, 0.0001}, {0.0008, 0.0001},
                {-0.0008, -0.0004}, {-0.0012, -0.0004},
                {0.0020, -0.0015}
        };
        for (int i = 0; i < count; i++) {
            points.add(findInsideSeedPoint(boundary,
                    center[0] + offsets[i % offsets.length][0],
                    center[1] + offsets[i % offsets.length][1],
                    center, i));
        }
        return points;
    }

    private double[] findInsideSeedPoint(String boundary, double targetLat, double targetLng,
                                         double[] center, int index) {
        if (com.takago.app.location.WardBoundaryUtils.containsPoint(boundary, targetLat, targetLng)) {
            return new double[]{targetLat, targetLng};
        }
        for (int radiusStep = 1; radiusStep <= 30; radiusStep++) {
            double radius = radiusStep * 0.0001;
            for (int angle = 0; angle < 360; angle += 45) {
                double radians = Math.toRadians(angle);
                double lat = targetLat + Math.sin(radians) * radius;
                double lng = targetLng + Math.cos(radians) * radius;
                if (com.takago.app.location.WardBoundaryUtils.containsPoint(boundary, lat, lng)) {
                    return new double[]{lat, lng};
                }
            }
        }
        for (int radiusStep = 0; radiusStep <= 30; radiusStep++) {
            double radius = (index + 1) * 0.00005 + radiusStep * 0.0001;
            for (int angle = 0; angle < 360; angle += 30) {
                double radians = Math.toRadians(angle);
                double lat = center[0] + Math.sin(radians) * radius;
                double lng = center[1] + Math.cos(radians) * radius;
                if (com.takago.app.location.WardBoundaryUtils.containsPoint(boundary, lat, lng)) {
                    return new double[]{lat, lng};
                }
            }
        }
        throw new IllegalStateException("Could not generate an inside seed point for ward boundary");
    }

    private double[] wardCenter(String boundaryGeoJson) {
        if (boundaryGeoJson != null) {
            try {
                List<List<com.takago.app.location.LatLngPoint>> rings =
                        com.takago.app.location.WardBoundaryUtils.exteriorRings(boundaryGeoJson);
                double lat = 0;
                double lng = 0;
                double minLat = Double.MAX_VALUE;
                double maxLat = -Double.MAX_VALUE;
                double minLng = Double.MAX_VALUE;
                double maxLng = -Double.MAX_VALUE;
                int points = 0;
                for (com.takago.app.location.LatLngPoint point : rings.get(0)) {
                    lat += point.lat;
                    lng += point.lng;
                    minLat = Math.min(minLat, point.lat);
                    maxLat = Math.max(maxLat, point.lat);
                    minLng = Math.min(minLng, point.lng);
                    maxLng = Math.max(maxLng, point.lng);
                    points++;
                }
                if (points > 0) {
                    double centerLat = lat / points;
                    double centerLng = lng / points;
                    if (com.takago.app.location.WardBoundaryUtils.containsPoint(
                            boundaryGeoJson, centerLat, centerLng)) {
                        return new double[]{centerLat, centerLng};
                    }
                    for (int row = 1; row < 10; row++) {
                        for (int col = 1; col < 10; col++) {
                            double candidateLat = minLat + ((maxLat - minLat) * row / 10.0);
                            double candidateLng = minLng + ((maxLng - minLng) * col / 10.0);
                            if (com.takago.app.location.WardBoundaryUtils.containsPoint(
                                    boundaryGeoJson, candidateLat, candidateLng)) {
                                return new double[]{candidateLat, candidateLng};
                            }
                        }
                    }
                }
            } catch (Exception ignored) {
            }
        }
        return new double[]{-6.8, 39.2};
    }

    private static String testVehiclePlate(String slug, int driverIndex) {
        if ("mzimuni".equals(slug)) return driverIndex == 1 ? "T790MZM" : "T791MZM";
        return "TEST-" + slug.toUpperCase(Locale.US) + "-" + driverIndex;
    }

    private static List<TestWardSpec> testWardSpecs() {
        List<TestWardSpec> specs = new ArrayList<>();
        specs.add(new TestWardSpec("Ubungo MC", "UBUNGO_MC", "Goba", "goba", "ubungo@takago.com"));
        specs.add(new TestWardSpec("Kinondoni MC", "KINONDONI_MC", "Magomeni", "magomeni", "kinondoni@takago.com"));
        specs.add(new TestWardSpec("Kinondoni MC", "KINONDONI_MC", "Mzimuni", "mzimuni", "kinondoni@takago.com"));
        specs.add(new TestWardSpec("Ilala MC", "ILALA_MC", "Tabata", "tabata", "ilala@takago.com"));
        specs.add(new TestWardSpec("Temeke MC", "TEMEKE_MC", "Temeke", "temeke", "temeke@takago.com"));
        specs.add(new TestWardSpec("Kigamboni MC", "KIGAMBONI_MC", "Kigamboni", "kigamboni", "kigamboni@takago.com"));
        return specs;
    }

    private static Set<String> testSeedEmails() {
        Set<String> emails = new HashSet<>();
        for (TestWardSpec spec : testWardSpecs()) {
            emails.add(spec.municipalAdminEmail);
            emails.add(spec.operatorEmail);
            emails.add(spec.wardAdminEmail);
            for (int i = 1; i <= 2; i++) emails.add(spec.slug + ".driver" + i + "@takago.com");
            for (int i = 1; i <= 6; i++) emails.add(spec.slug + ".resident" + i + "@takago.com");
        }
        return emails;
    }

    private static final class TestWardSpec {
        final String municipalityName;
        final String municipalityCode;
        final String wardName;
        final String slug;
        final String municipalAdminEmail;
        final String operatorEmail;
        final String wardAdminEmail;

        TestWardSpec(String municipalityName, String municipalityCode, String wardName,
                     String slug, String municipalAdminEmail) {
            this.municipalityName = municipalityName;
            this.municipalityCode = municipalityCode;
            this.wardName = wardName;
            this.slug = slug;
            this.municipalAdminEmail = municipalAdminEmail;
            this.operatorEmail = slug + ".operator@takago.com";
            this.wardAdminEmail = slug + ".admin@takago.com";
        }

        String municipalAdminName() {
            return municipalityName.replace(" MC", "") + " Municipal Administrator";
        }

        String operatorName() {
            return wardName + " Waste Operator";
        }

        String wardAdminName() {
            return wardName + " Ward Administrator";
        }
    }

    private void resetOfficialDarDemoData(SQLiteDatabase db) {
        boolean ownsTransaction = !db.inTransaction();
        if (ownsTransaction) db.beginTransaction();
        try {
            db.delete("route_stops", null, null);
            db.delete("group_members", null, null);
            db.delete("request_groups", null, null);
            db.delete("request_status_history", null, null);
            db.delete("notifications", null, null);
            db.delete("complaints", null, null);
            db.delete("pickups", null, null);
            db.delete("vehicles", null, null);
            db.delete("waste_operator_wards", null, null);
            db.delete("service_areas", null, null);
            db.delete("users", null, null);
            db.delete("wards", null, null);
            db.delete("municipalities", null, null);

            for (String[] municipality : OFFICIAL_MUNICIPALITIES) {
                int municipalityId = insertMunicipality(db, municipality[0], municipality[1]);
                insertOfficialUser(db, municipality[0].replace(" MC", "") + " Municipal Administrator",
                        municipality[0].toLowerCase(Locale.US).replace(" mc", "") + "@takago.com",
                        ROLE_MUNICIPAL_ADMIN, municipalityId, -1, null);

                for (String ward : wardsForMunicipality(municipality[0])) {
                    int wardId = insertOfficialWard(db, ward, municipality[0], municipalityId);
                    insertOfficialUser(db, ward + " Ward Administrator",
                            emailForWard(ward), ROLE_WARD_ADMIN, municipalityId, wardId, ward);
                }
            }

            if (ownsTransaction) db.setTransactionSuccessful();
        } finally {
            if (ownsTransaction) db.endTransaction();
        }
    }

    private int insertMunicipality(SQLiteDatabase db, String name, String code) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("code", code);
        values.put("region", "Dar es Salaam");
        values.put("normalized_name", normalizeWardName(name));
        values.put("is_active", 1);
        values.put("created_at", nowTimestamp());
        values.put("updated_at", nowTimestamp());
        long id = db.insertWithOnConflict("municipalities", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return (int) id;
    }

    private int insertOfficialWard(SQLiteDatabase db, String ward, String municipality, int municipalityId) {
        ContentValues values = new ContentValues();
        values.put("name", ward);
        values.put("name_normalized", normalizeWardName(ward));
        values.put("normalized_name", normalizeWardName(ward));
        values.put("municipality", municipality);
        values.put("municipality_id", municipalityId);
        values.putNull("boundary_geojson");
        values.put("boundary_status", "NOT_MAPPED");
        values.put("is_active", 1);
        values.put("created_at", nowTimestamp());
        values.put("updated_at", nowTimestamp());
        long id = db.insertWithOnConflict("wards", null, values, SQLiteDatabase.CONFLICT_REPLACE);
        return (int) id;
    }

    private void insertOfficialUser(SQLiteDatabase db, String name, String email, String role,
                                    int municipalityId, int wardId, String ward) {
        ContentValues values = new ContentValues();
        values.put("name", name);
        values.put("email", email);
        values.put("phone", "");
        values.put("password", hashPassword(DEMO_PASSWORD));
        values.put("role", role);
        values.put("status", "Active");
        values.put("municipality_id", municipalityId);
        if (wardId > 0) values.put("ward_id", wardId);
        if (ward != null) values.put("ward", ward);
        else values.put("ward", "All Wards");
        db.insert("users", null, values);
    }

    private static String emailForWard(String ward) {
        String local = normalizeWardName(ward).replace("'", "").replace(" ", ".");
        if ("ilala".equals(local) || "kinondoni".equals(local) || "ubungo".equals(local)
                || "temeke".equals(local) || "kigamboni".equals(local)) {
            local += ".ward";
        }
        return local + "@takago.com";
    }

    private static final String[][] OFFICIAL_MUNICIPALITIES = {
            {"Ilala MC", "ILALA_MC"},
            {"Kinondoni MC", "KINONDONI_MC"},
            {"Ubungo MC", "UBUNGO_MC"},
            {"Temeke MC", "TEMEKE_MC"},
            {"Kigamboni MC", "KIGAMBONI_MC"}
    };

    private static String[] wardsForMunicipality(String municipality) {
        switch (municipality) {
            case "Ubungo MC":
                return new String[]{"Goba", "Kibamba", "Kimara", "Kwembe", "Mabibo", "Makuburi",
                        "Makurumla", "Manzese", "Mbezi", "Mburahati", "Msigani", "Saranga", "Sinza", "Ubungo"};
            case "Kinondoni MC":
                return new String[]{"Bunju", "Kawe", "Kigogo", "Kijitonyama", "Kinondoni",
                        "Kunduchi", "Mabwepande", "Magomeni", "Makongo", "Makumbusho", "Mbezi Juu",
                        "Mikocheni", "Msasani", "Mwananyamala", "Mzimuni", "Ndugumbi", "Tandale", "Wazo"};
            case "Ilala MC":
                return new String[]{"Buguruni", "Buyuni", "Chanika", "Gerezani", "Gongo la Mboto",
                        "Ilala", "Jangwani", "Kariakoo", "Kimanga", "Kinyerezi", "Kipawa", "Kisukuru",
                        "Kisutu", "Kitunda", "Kivukoni", "Kivule", "Kiwalani", "Majohe", "Mchafukoge",
                        "Mchikichini", "Msongola", "Pugu", "Pugu Station", "Segerea", "Tabata",
                        "Ukonga", "Upanga Mashariki", "Upanga Magharibi", "Vingunguti", "Zingiziwa"};
            case "Temeke MC":
                return new String[]{"Azimio", "Buza", "Chamazi", "Chang'ombe", "Charambe", "Keko",
                        "Kiburugwa", "Kijichi", "Kilakala", "Kibondemaji", "Kurasini", "Makangarawe",
                        "Mbagala", "Mbagala Kuu", "Miburani", "Mtoni", "Sandali", "Tandika",
                        "Temeke", "Toangoma", "Yombo Vituka"};
            case "Kigamboni MC":
                return new String[]{"Kigamboni", "Kibada", "Kimbiji", "Kisarawe II", "Mjimwema",
                        "Pemba Mnazi", "Somangila", "Tungi", "Vijibweni"};
            default:
                return new String[0];
        }
    }

    private void seedPricingSettings(SQLiteDatabase db) {
        ContentValues cv = new ContentValues();
        cv.put("id", 1);
        cv.put("booking_fee", 2000.0);
        cv.put("included_weight_kg", 20.0);
        cv.put("rate_per_kg", 100.0);
        cv.put("distance_free_km", 3.0);
        cv.put("distance_fee_per_km", 300.0);
        cv.put("mult_household", 1.0);
        cv.put("mult_garden", 0.8);
        cv.put("mult_recyclables", 0.7);
        cv.put("mult_construction", 1.5);
        cv.put("mult_electronic", 1.3);
        db.insert("pricing_settings", null, cv);
    }

    /**
     * ==================================================================================
     * takaGo DEMO ACCOUNT DIRECTORY - every seeded login, all using the same password.
     * ==================================================================================
     * Password for every account below: Password123
     *
     * Role              | Name                      | Email                          | Ward
     * ------------------|---------------------------|--------------------------------|----------
     * Municipal Admin   | Municipal Administrator   | admin@takago.com               | All Wards
     * Waste Operator    | Green Waste Services      | green@takago.com               | Magomeni
     * Waste Operator    | EcoClean Tanzania         | eco@takago.com                 | Mbezi
     * Ward Admin        | Amina Hassan              | magomeni.admin@takago.com      | Magomeni
     * Ward Admin        | Peter Michael             | goba.admin@takago.com          | Goba
     * Ward Admin        | Neema Ally                | mbezi.admin@takago.com         | Mbezi
     * Ward Admin        | Hassan Said               | kivukoni.admin@takago.com      | Kivukoni
     * Ward Admin        | Fatma Omary               | sinza.admin@takago.com         | Sinza
     * Driver            | Joshua Swai               | joshua.swai@takago.com         | Magomeni
     * Driver            | Kibu Montana              | kibu.montana@takago.com        | Magomeni
     * Driver            | John Mrema                | john.mrema@takago.com          | Goba
     * Driver            | Neema Joseph              | neema.joseph@takago.com        | Mbezi
     * Driver            | Hassan Ally               | hassan.ally@takago.com         | Kivukoni
     * Driver            | Asha Kweka                | asha.kweka@takago.com          | Sinza
     * Resident          | Zena Diode                | zenadiode@takago.com           | Magomeni
     * Resident          | Florine Koddy             | florine.koddy@takago.com       | Magomeni
     * Resident          | Michael George            | michael.george@takago.com      | Goba
     * Resident          | Asha Suleiman             | asha.suleiman@takago.com       | Mbezi
     * Resident          | David Peter               | david.peter@takago.com         | Kivukoni
     * Resident          | Grace Mollel              | grace.mollel@takago.com        | Sinza
     */
    private static final String DEMO_PASSWORD = "Password123";


    private void seedData(SQLiteDatabase db) {
        // ---------- Municipal Admin ----------
        long municipalAdminId = insertUser(db, "Municipal Administrator", "admin@takago.com", "+255700100001", DEMO_PASSWORD, ROLE_MUNICIPAL_ADMIN);
        setWard(db, municipalAdminId, "All Wards");

        // ---------- Waste Operators ----------
        long greenId = insertUser(db, "Green Waste Services", "green@takago.com", "+255700100002", DEMO_PASSWORD, ROLE_TRUCK_OWNER);
        long ecoId = insertUser(db, "EcoClean Tanzania", "eco@takago.com", "+255700100003", DEMO_PASSWORD, ROLE_TRUCK_OWNER);

        // ---------- Ward Admins (one per ward) ----------
        long magomeniAdminId = insertUser(db, "Amina Hassan", "magomeni.admin@takago.com", "+255700100004", DEMO_PASSWORD, ROLE_WARD_ADMIN);
        long gobaAdminId = insertUser(db, "Peter Michael", "goba.admin@takago.com", "+255700100005", DEMO_PASSWORD, ROLE_WARD_ADMIN);
        long mbeziAdminId = insertUser(db, "Neema Ally", "mbezi.admin@takago.com", "+255700100006", DEMO_PASSWORD, ROLE_WARD_ADMIN);
        long kivukoniAdminId = insertUser(db, "Hassan Said", "kivukoni.admin@takago.com", "+255700100007", DEMO_PASSWORD, ROLE_WARD_ADMIN);
        long sinzaAdminId = insertUser(db, "Fatma Omary", "sinza.admin@takago.com", "+255700100008", DEMO_PASSWORD, ROLE_WARD_ADMIN);
        setWard(db, magomeniAdminId, "Magomeni");
        setWard(db, gobaAdminId, "Goba");
        setWard(db, mbeziAdminId, "Mbezi");
        setWard(db, kivukoniAdminId, "Kivukoni");
        setWard(db, sinzaAdminId, "Sinza");

        // ---------- Vehicles ----------
        long v1 = insertVehicle(db, "T216KDE", "Isuzu N-series 3T", "3T", "Approved", greenId, "Magomeni", null);
        long v2 = insertVehicle(db, "T218KDE", "Isuzu N-series 3T", "3T", "Approved", greenId, "Magomeni", null);
        long v3 = insertVehicle(db, "T301TKG", "Mitsubishi Canter 3T", "3T", "Approved", greenId, "Goba", null);
        long v4 = insertVehicle(db, "T401TKG", "Mitsubishi Canter 3T", "3T", "Approved", ecoId, "Mbezi", null);
        long v5 = insertVehicle(db, "T501TKG", "Isuzu N-series 3T", "3T", "Pending", ecoId, "Kivukoni", null);
        long v6 = insertVehicle(db, "T601TKG", "Isuzu N-series 3T", "3T", "Rejected", ecoId, "Sinza",
                "Insurance document expired.\nRenew insurance and upload a valid copy.");

        // ---------- Waste Operators' fleet-wide stats (dashboard cards) ----------
        // 3 trucks / 3 drivers each, earnings matched to this seed's own completed pickups below.
        setFleetStats(db, greenId, "Magomeni", 3, 3, "TZS 10,150", "+8% vs last week");
        setFleetStats(db, ecoId, "Mbezi", 3, 3, "TZS 2,640", "New this week");

        // ---------- Drivers ----------
        // Magomeni
        long joshuaId = insertDriver(db, "Joshua Swai", "joshua.swai@takago.com", "+255700100010", "Magomeni",
                greenId, v1, "T216KDE", "Available", 4.9, -6.8010, 39.2595);
        long kibuId = insertDriver(db, "Kibu Montana", "kibu.montana@takago.com", "+255700100011", "Magomeni",
                greenId, v2, "T218KDE", "Available", 4.7, -6.7995, 39.2580);
        // Goba
        long johnId = insertDriver(db, "John Mrema", "john.mrema@takago.com", "+255700100012", "Goba",
                greenId, v3, "T301TKG", "Busy", 4.6, -6.7430, 39.1740);
        // Mbezi
        long neemaId = insertDriver(db, "Neema Joseph", "neema.joseph@takago.com", "+255700100013", "Mbezi",
                ecoId, v4, "T401TKG", "Available", 4.8, -6.7505, 39.2010);
        // Kivukoni
        long hassanAllyId = insertDriver(db, "Hassan Ally", "hassan.ally@takago.com", "+255700100014", "Kivukoni",
                ecoId, v5, "T501TKG", "Offline", 4.4, -6.8175, 39.2925);
        // Sinza
        long ashaKwekaId = insertDriver(db, "Asha Kweka", "asha.kweka@takago.com", "+255700100015", "Sinza",
                ecoId, v6, "T601TKG", "Available", 4.7, -6.7840, 39.2340);

        // ---------- Residents ----------
        long zenaId = insertResident(db, "Zena Diode", "zenadiode@takago.com", "+255700100020", "Magomeni");
        long florineId = insertResident(db, "Florine Koddy", "florine.koddy@takago.com", "+255700100021", "Magomeni");
        long michaelId = insertResident(db, "Michael George", "michael.george@takago.com", "+255700100022", "Goba");
        long ashaSId = insertResident(db, "Asha Suleiman", "asha.suleiman@takago.com", "+255700100023", "Mbezi");
        long davidId = insertResident(db, "David Peter", "david.peter@takago.com", "+255700100024", "Kivukoni");
        long graceId = insertResident(db, "Grace Mollel", "grace.mollel@takago.com", "+255700100025", "Sinza");

        // ==================================================================================
        // PICKUP REQUESTS - 4 Completed, 2 On the way, 2 Assigned, 2 Pending, 1 Cancelled, 1 Expired
        // Assignment follows the ward rules: Magomeni -> Joshua (fallback Kibu), Goba -> John only,
        // Mbezi -> Neema only, Kivukoni -> Hassan Ally (offline -> stays Pending), Sinza -> Asha Kweka.
        // ==================================================================================

        // --- Completed (4) ---
        insertDemoPickup(db, "#5001", "Magomeni", "House 12, Magomeni", "Medium", "Household", "Completed",
                daysAgo(3), dateTimeAt(3, "08:00:00"), dateTimeAt(3, "09:15:00"),
                zenaId, "Zena Diode", joshuaId, v1, "Accepted", dateTimeAt(3, "08:05:00"), null, null,
                -6.8000, 39.2600, 1.1, 8, "08:00",
                "Finalized", null, null, 24.0, 1.0, 2400.0);

        insertDemoPickup(db, "#5002", "Magomeni", "Plot 5, Magomeni", "Small", "Recyclables", "Completed",
                daysAgo(5), dateTimeAt(5, "10:00:00"), dateTimeAt(5, "11:00:00"),
                florineId, "Florine Koddy", kibuId, v2, "Accepted", dateTimeAt(5, "10:05:00"), null, null,
                -6.8020, 39.2570, 0.9, 6, "10:00",
                "Finalized", null, null, 12.0, 0.7, 2000.0);

        insertDemoPickup(db, "#5003", "Goba", "Goba Central Road", "Large", "Construction", "Completed",
                daysAgo(2), dateTimeAt(2, "09:00:00"), dateTimeAt(2, "10:30:00"),
                michaelId, "Michael George", johnId, v3, "Accepted", dateTimeAt(2, "09:10:00"), null, null,
                -6.7415, 39.1725, 1.6, 10, "09:00",
                "Finalized", null, null, 45.0, 1.5, 5750.0);

        insertDemoPickup(db, "#5004", "Sinza", "Sinza Mori", "Medium", "Garden", "Completed",
                daysAgo(4), dateTimeAt(4, "08:30:00"), dateTimeAt(4, "09:45:00"),
                graceId, "Grace Mollel", ashaKwekaId, v6, "Accepted", dateTimeAt(4, "08:40:00"), null, null,
                -6.7825, 39.2325, 1.3, 9, "08:30",
                "Finalized", null, null, 28.0, 0.8, 2640.0);

        // --- On the way (2) ---
        insertDemoPickup(db, "#5005", "Magomeni", "House 12, Magomeni", "Small", "Household", "On the way",
                daysAgo(0), dateTimeAt(0, "07:30:00"), null,
                zenaId, "Zena Diode", joshuaId, v1, "Accepted", dateTimeAt(0, "07:40:00"), null, null,
                -6.8000, 39.2600, 1.1, 8, "07:30",
                "Estimated", 2000.0, 2000.0, null, null, null);

        insertDemoPickup(db, "#5006", "Mbezi", "Mbezi Beach Road", "Large", "Recyclables", "On the way",
                daysAgo(0), dateTimeAt(0, "08:00:00"), null,
                ashaSId, "Asha Suleiman", neemaId, v4, "Accepted", dateTimeAt(0, "08:10:00"), null, null,
                -6.7490, 39.1995, 0.8, 6, "08:00",
                "Estimated", 2070.0, 3330.0, null, null, null);

        // --- Assigned (2) - proposed to the driver, awaiting their accept ---
        insertDemoPickup(db, "#5007", "Goba", "Goba Central Road", "Large", "Construction", "Assigned",
                daysAgo(0), dateTimeAt(0, "09:00:00"), null,
                michaelId, "Michael George", johnId, v3, null, null, timestampPlusMinutes(10), null,
                -6.7415, 39.1725, 1.6, 10, "09:00",
                "Estimated", 2150.0, 4850.0, null, null, null);

        insertDemoPickup(db, "#5008", "Sinza", "Sinza Mori", "Small", "Garden", "Assigned",
                daysAgo(0), dateTimeAt(0, "09:30:00"), null,
                graceId, "Grace Mollel", ashaKwekaId, v6, null, null, timestampPlusMinutes(10), null,
                -6.7825, 39.2325, 1.3, 9, "09:30",
                "Estimated", 2000.0, 2000.0, null, null, null);

        // --- Pending (2) ---
        insertDemoPickup(db, "#5009", "Mbezi", "Mbezi Beach Road", "Medium", "Recyclables", "Pending",
                daysAgo(0), dateTimeAt(0, "10:00:00"), null,
                ashaSId, "Asha Suleiman", null, null, null, null, null, null,
                -6.7490, 39.1995, 0, 0, "10:00",
                "Estimated", 2000.0, 2000.0, null, null, null);

        // Kivukoni: Hassan Ally is Offline, so this request stays Pending and ward staff are notified.
        insertDemoPickup(db, "#5010", "Kivukoni", "Kivukoni Front", "Large", "Household", "Pending",
                daysAgo(0), dateTimeAt(0, "07:00:00"), null,
                davidId, "David Peter", null, null, null, null, null, null,
                -6.8190, 39.2940, 0, 0, "07:00",
                "Estimated", 2100.0, 3900.0, null, null, null);
        insertNotification(db, kivukoniAdminId, "No driver available",
                "Pickup #5010 in Kivukoni has no available driver (Hassan Ally is currently Offline). The request will stay Pending until a driver is free.", "assignment");
        insertNotification(db, greenId, "No driver available",
                "Kivukoni has a pending pickup request (#5010) with no available driver right now.", "assignment");
        insertNotification(db, ecoId, "No driver available",
                "Kivukoni has a pending pickup request (#5010) with no available driver right now.", "assignment");

        // --- Cancelled (1) ---
        insertDemoPickup(db, "#5011", "Magomeni", "Plot 5, Magomeni", "Medium", "Household", "Cancelled",
                daysAgo(1), dateTimeAt(1, "08:00:00"), null,
                florineId, "Florine Koddy", joshuaId, v1, "Accepted", dateTimeAt(1, "08:05:00"), null,
                "Resident no longer needs this pickup.",
                -6.8020, 39.2570, 0.9, 6, "08:00",
                "Estimated", 2000.0, 3200.0, null, null, null);

        // --- Expired (1) - assigned to Hassan Ally but he never responded within the 10 minute window ---
        insertDemoPickup(db, "#5012", "Kivukoni", "Kivukoni Front", "Small", "Household", "Expired",
                daysAgo(1), dateTimeAt(1, "07:00:00"), null,
                davidId, "David Peter", hassanAllyId, v5, null, null, dateTimeAt(1, "07:10:00"), null,
                -6.8190, 39.2940, 0.5, 4, "07:00",
                "Estimated", 2000.0, 2000.0, null, null, null);

        // ---------- Complaints ----------
        insertComplaint(db, "Missed pickup", "David Peter", formatComplaintDate(1), "Open", "Kivukoni");
        insertComplaint(db, "Late arrival", "Michael George", formatComplaintDate(2), "Open", "Goba");
        insertComplaint(db, "Vehicle leak", "Grace Mollel", formatComplaintDate(3), "Open", "Sinza");
        insertNotification(db, municipalAdminId, "Complaint submitted", "New complaint: Missed pickup, reported by David Peter.", "complaint");
        insertNotification(db, municipalAdminId, "Complaint submitted", "New complaint: Late arrival, reported by Michael George.", "complaint");
        insertNotification(db, municipalAdminId, "Complaint submitted", "New complaint: Vehicle leak, reported by Grace Mollel.", "complaint");

        // ---------- Vehicle approval / rejection notifications ----------
        insertNotification(db, greenId, "Vehicle approved", "Your vehicle T216KDE has been approved.", "vehicle");
        insertNotification(db, greenId, "Vehicle approved", "Your vehicle T218KDE has been approved.", "vehicle");
        insertNotification(db, greenId, "Vehicle approved", "Your vehicle T301TKG has been approved.", "vehicle");
        insertNotification(db, ecoId, "Vehicle approved", "Your vehicle T401TKG has been approved.", "vehicle");
        insertNotification(db, ecoId, "Vehicle rejected",
                "Your vehicle T601TKG was rejected: Insurance document expired. Renew insurance and upload a valid copy.", "vehicle");

        // ---------- Driver assigned / accepted notifications ----------
        insertNotification(db, joshuaId, "New pickup assigned", "You've been assigned pickup #5005 in Magomeni.", "assignment");
        insertNotification(db, johnId, "New pickup assigned", "You've been assigned pickup #5007 in Goba.", "assignment");
        insertNotification(db, ashaKwekaId, "New pickup assigned", "You've been assigned pickup #5008 in Sinza.", "assignment");
        insertNotification(db, zenaId, "Driver accepted", "Joshua Swai accepted your request and is on the way.", "assignment");
        insertNotification(db, ashaSId, "Driver accepted", "Neema Joseph accepted your request and is on the way.", "assignment");

        // ---------- Pickup completed notifications ----------
        insertNotification(db, zenaId, "Pickup completed", "Your waste pickup #5001 has been collected. Final price: TZS 2,400.", "pickup");
        insertNotification(db, florineId, "Pickup completed", "Your waste pickup #5002 has been collected. Final price: TZS 2,000.", "pickup");
        insertNotification(db, michaelId, "Pickup completed", "Your waste pickup #5003 has been collected. Final price: TZS 5,750.", "pickup");
        insertNotification(db, graceId, "Pickup completed", "Your waste pickup #5004 has been collected. Final price: TZS 2,640.", "pickup");

        // ---------- Welcome notifications for every seeded account ----------
        insertWelcomeNotification(db, municipalAdminId, "admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, greenId, "green@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, ecoId, "eco@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, magomeniAdminId, "magomeni.admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, gobaAdminId, "goba.admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, mbeziAdminId, "mbezi.admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, kivukoniAdminId, "kivukoni.admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, sinzaAdminId, "sinza.admin@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, joshuaId, "joshua.swai@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, kibuId, "kibu.montana@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, johnId, "john.mrema@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, neemaId, "neema.joseph@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, hassanAllyId, "hassan.ally@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, ashaKwekaId, "asha.kweka@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, zenaId, "zenadiode@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, florineId, "florine.koddy@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, michaelId, "michael.george@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, ashaSId, "asha.suleiman@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, davidId, "david.peter@takago.com", DEMO_PASSWORD);
        insertWelcomeNotification(db, graceId, "grace.mollel@takago.com", DEMO_PASSWORD);
    }

    private long insertResident(SQLiteDatabase db, String name, String email, String phone, String ward) {
        long id = insertUser(db, name, email, phone, DEMO_PASSWORD, ROLE_RESIDENT);
        setWard(db, id, ward);
        return id;
    }

    private long insertDriver(SQLiteDatabase db, String name, String email, String phone, String ward,
                               long operatorId, long vehicleId, String plate, String availabilityStatus,
                               double rating, double latitude, double longitude) {
        long id = insertUser(db, name, email, phone, DEMO_PASSWORD, ROLE_DRIVER);
        ContentValues cv = new ContentValues();
        cv.put("ward", ward);
        cv.put("operator_id", operatorId);
        cv.put("vehicle_id", vehicleId);
        cv.put("driver_plate", plate);
        cv.put("availability_status", availabilityStatus);
        cv.put("rating", rating);
        cv.put("latitude", latitude);
        cv.put("longitude", longitude);
        cv.put("last_location_update", nowTimestamp());
        db.update("users", cv, "id = ?", new String[]{String.valueOf(id)});
        return id;
    }

    private void setFleetStats(SQLiteDatabase db, long operatorId, String ward, int fleetTrucks, int fleetDrivers,
                                String earningsWeek, String earningsChange) {
        ContentValues cv = new ContentValues();
        cv.put("ward", ward);
        cv.put("rating", 4.8);
        cv.put("fleet_trucks", fleetTrucks);
        cv.put("fleet_drivers", fleetDrivers);
        cv.put("fleet_earnings_week", earningsWeek);
        cv.put("fleet_earnings_change", earningsChange);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(operatorId)});
    }

    /**
     * One row in the pickups table covering every pricing-engine field. Pass null for
     * driverId/vehicleId/driverResponseStatus/acceptedAt/timeoutAt/cancelReason when not
     * applicable. For a finalized price pass measuredWeightKg + multiplier + finalPrice (the
     * included weight/rate/booking fee are the seeded pricing_settings defaults); for an
     * estimate-only pickup pass estMin/estMax instead and leave the finalized trio null.
     */
    private long insertDemoPickup(SQLiteDatabase db, String code, String ward, String address,
                                   String wasteSize, String wasteType, String status,
                                   String pickupDate, String createdAt, String completedAt,
                                   long residentId, String residentName, Long driverId, Long vehicleId,
                                   String driverResponseStatus, String acceptedAt, String timeoutAt, String cancelReason,
                                   double latitude, double longitude, double distanceKm, int etaMin, String timeText,
                                   String pricingStatus, Double estMin, Double estMax,
                                   Double measuredWeightKg, Double wasteTypeMultiplier, Double finalPrice) {
        ContentValues cv = new ContentValues();
        cv.put("code", code);
        cv.put("ward", ward);
        cv.put("category", wasteSize);
        cv.put("waste_type", wasteType);
        cv.put("status", status);
        cv.put("pickup_date", pickupDate);
        cv.put("resident_id", residentId);
        cv.put("resident_display_name", residentName);
        cv.put("weight_kg", weightKgForWasteSize(wasteSize));
        cv.put("time_text", timeText);
        cv.put("distance_km", distanceKm);
        cv.put("eta_min", etaMin);
        cv.put("latitude", latitude);
        cv.put("longitude", longitude);
        cv.put("address", address);
        cv.put("created_at", createdAt);
        cv.put("pricing_status", pricingStatus);
        if (completedAt != null) {
            cv.put("completed_at", completedAt);
        }
        if (driverId != null) {
            cv.put("driver_id", driverId);
        }
        if (vehicleId != null) {
            cv.put("assigned_vehicle_id", vehicleId);
        }
        if (driverResponseStatus != null) {
            cv.put("driver_response_status", driverResponseStatus);
        }
        if (acceptedAt != null) {
            cv.put("accepted_at", acceptedAt);
        }
        if (timeoutAt != null) {
            cv.put("timeout_at", timeoutAt);
        }
        if (cancelReason != null) {
            cv.put("cancel_reason", cancelReason);
        }
        if (estMin != null) {
            cv.put("estimated_price_min", estMin);
        }
        if (estMax != null) {
            cv.put("estimated_price_max", estMax);
        }
        if (measuredWeightKg != null) {
            cv.put("measured_weight_kg", measuredWeightKg);
            cv.put("included_weight_kg", 20.0);
            cv.put("rate_per_kg", 100.0);
            cv.put("booking_fee", 2000.0);
            cv.put("distance_fee", 0.0);
            cv.put("waste_type_multiplier", wasteTypeMultiplier);
            cv.put("final_price", finalPrice);
        }
        return db.insert("pickups", null, cv);
    }

    private static String dateTimeAt(int daysAgoCount, String time) {
        return daysAgo(daysAgoCount) + " " + time;
    }

    private static String formatComplaintDate(int daysAgoCount) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -daysAgoCount);
        SimpleDateFormat fmt = new SimpleDateFormat("d MMMM", Locale.US);
        return fmt.format(cal.getTime());
    }

    private long insertUser(SQLiteDatabase db, String name, String email, String phone, String password, String role) {
        return insertUserWithStatus(db, name, email, phone, password, role, "Active");
    }

    private long insertUserWithStatus(SQLiteDatabase db, String name, String email, String phone, String password, String role, String status) {
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", role);
        cv.put("status", status);
        return db.insert("users", null, cv);
    }

    private void setWard(SQLiteDatabase db, long userId, String ward) {
        ContentValues cv = new ContentValues();
        cv.put("ward", ward);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    private long insertVehicle(SQLiteDatabase db, String plate, String model, String capacity, String status,
                                long operatorId, String ward, String rejectionReason) {
        ContentValues cv = new ContentValues();
        cv.put("plate", plate);
        cv.put("model", model);
        cv.put("capacity", capacity);
        cv.put("status", status);
        cv.put("operator_id", operatorId);
        cv.put("ward", ward);
        cv.put("rejection_reason", rejectionReason);
        return db.insert("vehicles", null, cv);
    }

    private void insertComplaint(SQLiteDatabase db, String subject, String reporter, String dateText,
                                 String status, String ward) {
        ContentValues cv = new ContentValues();
        cv.put("subject", subject);
        cv.put("reporter", reporter);
        cv.put("date_text", dateText);
        cv.put("status", status);
        cv.put("ward", ward);
        db.insert("complaints", null, cv);
    }


    private static String daysAgo(int days) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.DAY_OF_YEAR, -days);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd", Locale.US);
        return fmt.format(cal.getTime());
    }

    /**
     * Safety net: guarantees the 5 canonical demo accounts (one per role) exist, without ever
     * duplicating or overwriting one that's already there. Safe to call every app start.
     */
    public void ensureDemoAccountsSeeded() {
        SQLiteDatabase db = getWritableDatabase();
        ensureLocationRoutingSchema(db);
        if (scalarInt("SELECT COUNT(*) FROM municipalities", null) == 0) {
            resetOfficialDarDemoData(db);
        }
        seedTestUsersOnly(db);
    }

    private void insertIfEmailMissing(SQLiteDatabase db, String name, String email, String phone,
                                       String password, String role, String ward) {
        Cursor c = db.rawQuery("SELECT id FROM users WHERE email = ?", new String[]{email});
        boolean exists = c.getCount() > 0;
        c.close();
        if (exists) {
            return;
        }

        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", role);
        if (ward != null) {
            cv.put("ward", ward);
        }
        db.insert("users", null, cv);
    }

    // ---------- Login / Register ----------

    private static String hashPassword(String password) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] bytes = digest.digest((password == null ? "" : password).getBytes());
            StringBuilder builder = new StringBuilder();
            for (byte b : bytes) {
                builder.append(String.format(Locale.US, "%02x", b));
            }
            return builder.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException("SHA-256 unavailable", e);
        }
    }

    public UserAccount checkLogin(String emailOrPhone, String password) {
        SQLiteDatabase db = getReadableDatabase();
        String passwordHash = hashPassword(password);
        Cursor c = db.rawQuery(
                "SELECT id, name, email, phone, role, password FROM users WHERE (email = ? OR phone = ?) " +
                        "AND (password = ? OR password = ?)",
                new String[]{emailOrPhone, emailOrPhone, passwordHash, password});
        UserAccount account = null;
        boolean upgradePlaintextPassword = false;
        if (c.moveToFirst()) {
            account = new UserAccount();
            account.id = c.getInt(0);
            account.name = c.getString(1);
            account.email = c.getString(2);
            account.phone = c.getString(3);
            account.role = c.getString(4);
            if (password.equals(c.getString(5))) {
                upgradePlaintextPassword = true;
            }
        }
        c.close();
        if (account != null && upgradePlaintextPassword) {
            ContentValues cv = new ContentValues();
            cv.put("password", passwordHash);
            getWritableDatabase().update("users", cv, "id = ?",
                    new String[]{String.valueOf(account.id)});
        }
        return account;
    }

    public UserAccount getUserByEmail(String email) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, name, email, phone, role FROM users WHERE email = ?",
                new String[]{email});
        UserAccount account = null;
        if (c.moveToFirst()) {
            account = new UserAccount();
            account.id = c.getInt(0);
            account.name = c.getString(1);
            account.email = c.getString(2);
            account.phone = c.getString(3);
            account.role = c.getString(4);
        }
        c.close();
        return account;
    }

    public long registerResident(String username, String email, String phone, String password) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", username);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", ROLE_RESIDENT);
        return db.insert("users", null, cv);
    }

    /** A Waste Operator registers one of their own drivers, tied to their operator_id and a single ward. */
    public long registerDriver(String name, String email, String phone, String password, String ward, int operatorId,
                                double wardLat, double wardLng, double wardRadiusKm) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", ROLE_DRIVER);
        cv.put("ward", ward);
        int wardId = findWardIdByName(ward);
        if (wardId > 0) {
            cv.put("ward_id", wardId);
            cv.put("municipality_id", getMunicipalityIdForWard(wardId));
        }
        cv.put("operator_id", operatorId);
        cv.put("availability_status", "Available");
        cv.put("ward_lat", wardLat);
        cv.put("ward_lng", wardLng);
        cv.put("ward_radius_km", wardRadiusKm);
        long userId = db.insert("users", null, cv);
        insertWelcomeNotification(db, userId, email, password);
        return userId;
    }

    /** Municipal Admin registers a new Waste Operator. */
    public long registerWasteOperator(String name, String email, String phone, String password, String ward,
                                       double wardLat, double wardLng, double wardRadiusKm) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", ROLE_TRUCK_OWNER);
        cv.put("ward", ward);
        int wardId = findWardIdByName(ward);
        if (wardId > 0) {
            cv.put("ward_id", wardId);
            cv.put("municipality_id", getMunicipalityIdForWard(wardId));
        }
        cv.put("ward_lat", wardLat);
        cv.put("ward_lng", wardLng);
        cv.put("ward_radius_km", wardRadiusKm);
        long userId = db.insert("users", null, cv);
        insertWelcomeNotification(db, userId, email, password);
        return userId;
    }

    /** Municipal Admin registers a new Ward Admin. */
    public long registerWardAdmin(String name, String email, String phone, String password, String ward,
                                   double wardLat, double wardLng, double wardRadiusKm) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("password", hashPassword(password));
        cv.put("role", ROLE_WARD_ADMIN);
        cv.put("ward", ward);
        int wardId = findWardIdByName(ward);
        if (wardId > 0) {
            cv.put("ward_id", wardId);
            cv.put("municipality_id", getMunicipalityIdForWard(wardId));
        }
        cv.put("ward_lat", wardLat);
        cv.put("ward_lng", wardLng);
        cv.put("ward_radius_km", wardRadiusKm);
        long userId = db.insert("users", null, cv);
        insertWelcomeNotification(db, userId, email, password);
        return userId;
    }

    /** Notifies a newly registered account of its own login credentials so they can sign in and change them. */
    private void insertWelcomeNotification(SQLiteDatabase db, long userId, String email, String password) {
        insertNotification(db, userId, "Welcome to takaGo!",
                "Your account is ready. Sign in with email " + email + " and password " + password +
                        ". You can change your password any time from Edit Profile.",
                "account");
    }

    /** A Waste Operator submits a new vehicle for Municipal Admin approval. */
    public long submitVehicle(String plate, String model, String capacity, int operatorId, String ward) {
        long id = vehicleRepository.submitVehicle(plate, model, capacity, operatorId, ward);
        int wardId = findWardIdByName(ward);
        if (id > 0 && wardId > 0) {
            ContentValues cv = new ContentValues();
            cv.put("ward_id", wardId);
            cv.put("municipality_id", getMunicipalityIdForWard(wardId));
            getWritableDatabase().update("vehicles", cv, "id = ?",
                    new String[]{String.valueOf(id)});
        }
        return id;
    }

    /**
     * Updates a resident's editable profile fields. Pass null/empty for newPassword to leave
     * the existing password unchanged.
     */
    public void updateResidentProfile(int userId, String name, String phone, String email, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("phone", phone);
        cv.put("email", email);
        if (newPassword != null && !newPassword.isEmpty()) {
            cv.put("password", hashPassword(newPassword));
        }
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    // ---------- Municipal Admin dashboard (all live queries - no static stat table) ----------

    public int getTotalUsers() {
        return dashboardRepository.getTotalUsers();
    }

    public int getTotalUsersInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM users WHERE municipality_id = ?",
                new String[]{String.valueOf(municipalityId)});
    }

    public int getTotalOperators() {
        return dashboardRepository.getTotalOperators(ROLE_TRUCK_OWNER);
    }

    public int getTotalOperatorsInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM users WHERE role = ? AND municipality_id = ?",
                new String[]{ROLE_TRUCK_OWNER, String.valueOf(municipalityId)});
    }

    public int getTotalDrivers() {
        return dashboardRepository.getTotalDrivers(ROLE_DRIVER);
    }

    public int getTotalDriversInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM users WHERE role = ? AND municipality_id = ?",
                new String[]{ROLE_DRIVER, String.valueOf(municipalityId)});
    }

    public int getTotalTrucks() {
        return dashboardRepository.getTotalTrucks();
    }

    public int getTotalTrucksInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM vehicles WHERE status = 'Approved' AND municipality_id = ?",
                new String[]{String.valueOf(municipalityId)});
    }

    /** Percentage of drivers who are currently not Offline (i.e. Available or Busy). */
    public int getVehiclesActivePercent() {
        return dashboardRepository.getVehiclesActivePercent(ROLE_DRIVER);
    }

    /** Real weight recycled this calendar month, from completed pickups' measured (or estimated) weight. */
    public double getRecycledTonsMonth() {
        return dashboardRepository.getRecycledTonsMonth();
    }

    public int getTotalPickupsAllTime() {
        return dashboardRepository.getTotalPickupsAllTime();
    }

    public double getAvgRating() {
        return dashboardRepository.getAvgRating(ROLE_DRIVER);
    }

    /** Share of resolved pickups (Completed vs Completed+Cancelled+Expired) that finished successfully. */
    public int getSlaPercent() {
        return dashboardRepository.getSlaPercent();
    }

    private int scalarInt(String sql, String[] args) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql, args);
        int value = 0;
        if (c.moveToFirst()) {
            value = c.getInt(0);
        }
        c.close();
        return value;
    }

    private double scalarDouble(String sql, String[] args) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(sql, args);
        double value = 0;
        if (c.moveToFirst()) {
            value = c.getDouble(0);
        }
        c.close();
        return value;
    }

    public int getPickupCountInWard(String ward, String status) {
        return dashboardRepository.getPickupCountInWard(ward, status);
    }

    public int getUserCountInWard(String ward, String role) {
        int wardId = findWardIdByName(ward);
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = wardId > 0
                ? db.rawQuery("SELECT COUNT(*) FROM users WHERE ward_id = ? AND role = ?",
                new String[]{String.valueOf(wardId), role})
                : db.rawQuery("SELECT COUNT(*) FROM users WHERE ward = ? AND role = ?",
                new String[]{ward, role});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    public int getPendingVehicleApprovalsCount() {
        return dashboardRepository.getPendingVehicleApprovalsCount();
    }

    public int getPendingVehicleApprovalsCountInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM vehicles WHERE status = 'Pending' AND municipality_id = ?",
                new String[]{String.valueOf(municipalityId)});
    }

    public int getOpenComplaintsCount() {
        return dashboardRepository.getOpenComplaintsCount();
    }

    public int getOpenComplaintsCountInMunicipality(int municipalityId) {
        return scalarInt("SELECT COUNT(*) FROM complaints WHERE status = 'Open' AND municipality_id = ?",
                new String[]{String.valueOf(municipalityId)});
    }

    public int getOpenComplaintsCountInWard(String ward) {
        return complaintRepository.getOpenComplaintsCountInWard(findWardIdByName(ward), ward);
    }

    private int countWhere(String table, String where, String arg) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM " + table + " WHERE " + where, new String[]{arg});
        int count = 0;
        if (c.moveToFirst()) {
            count = c.getInt(0);
        }
        c.close();
        return count;
    }

    /** Pickup counts for the last 7 days, oldest first (index 0 = 6 days ago ... index 6 = today). */
    public int[] getPickupsLast7Days() {
        return dashboardRepository.getPickupsLast7Days();
    }

    public void updateVehicleStatus(int vehicleId, String status) {
        vehicleRepository.updateVehicleStatus(vehicleId, status);
    }

    /** Rejecting a vehicle must always be given a reason so the Waste Operator knows what to fix. */
    public void updateVehicleStatus(int vehicleId, String status, String rejectionReason) {
        vehicleRepository.updateVehicleStatus(vehicleId, status, rejectionReason);
    }

    public void resolveComplaint(int complaintId) {
        complaintRepository.resolveComplaint(complaintId);
    }

    /** Municipal Admin suspends or reactivates any user account. */
    public void updateUserStatus(int userId, String status) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", status);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    public void markPickupCompleted(int pickupId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Completed");
        cv.put("completed_at", nowTimestamp());
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
        notifyResidentForPickup(pickupId, "Pickup completed", "Your waste pickup has been collected. Thanks for recycling!", "pickup");
        com.takago.app.network.ServerSyncManager.pushPickupStatus(appContext, pickupId, "completed", null, null);
    }

    // ---------- Pickup Request ----------

    private static final String RESIDENT_PICKUP_COLUMNS =
            "id, code, ward, category, status, pickup_date, latitude, longitude, address, photo_path, created_at, completed_at, driver_id, distance_km, " +
                    "waste_type, estimated_price_min, estimated_price_max, measured_weight_kg, included_weight_kg, " +
                    "rate_per_kg, distance_fee, waste_type_multiplier, final_price, scale_photo_path, pricing_status, payment_status, booking_fee, " +
                    "place_id, ward_id, group_id, stop_order, encoded_polyline, route_distance_meters, route_duration_seconds, route_calculated_at, " +
                    "house_number, street_name, formatted_address, place_name, plus_code, proof_photo_path";

    private static double weightKgForWasteSize(String wasteSize) {
        if ("Medium".equals(wasteSize)) {
            return 15;
        } else if ("Large".equals(wasteSize)) {
            return 30;
        }
        return 5; // Small
    }

    private static String nowTimestamp() {
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return fmt.format(new Date());
    }

    /**
     * Creates a new pending pickup request for a resident and returns its new row id.
     * The request is tagged with the resident's ward so that only drivers in that same
     * ward (see {@link #getDriversInWard}) can ever be assigned to it.
     */
    public long createPickupRequest(int residentId, String wasteSize, String wasteType, double latitude, double longitude,
                                     String address, String ward, String photoPath) {
        int wardId = findWardIdByName(ward);
        return createPickupRequest(residentId, wasteSize, wasteType, latitude, longitude,
                address, null, wardId, ward, photoPath, null, null, address);
    }

    public long createPickupRequest(int residentId, String wasteSize, String wasteType,
                                    double latitude, double longitude, String address,
                                    String placeId, int wardId, String ward, String photoPath) {
        return createPickupRequest(residentId, wasteSize, wasteType, latitude, longitude,
                address, placeId, wardId, ward, photoPath, null, null, address);
    }

    public long createPickupRequest(int residentId, String wasteSize, String wasteType,
                                    double latitude, double longitude, String address,
                                    String placeId, int wardId, String ward, String photoPath,
                                    String houseNumber, String streetName, String formattedAddress) {
        SQLiteDatabase db = getWritableDatabase();
        WardRow detectedWard = findMappedWardContaining(latitude, longitude);
        if (detectedWard == null) {
            insertNotification(residentId, "Pickup outside service area",
                    "This pickup location is outside the currently supported service area.", "pickup");
            return -1;
        }
        UserAccount resident = getUserById(residentId);
        if (resident != null && resident.municipalityId > 0
                && resident.municipalityId != detectedWard.municipalityId) {
            insertNotification(residentId, "Pickup outside municipality",
                    "The selected pickup point is outside your municipality service area.", "pickup");
            return -1;
        }
        wardId = detectedWard.id;
        ward = detectedWard.name;
        int municipalityId = detectedWard.municipalityId;
        double requestWeightKg = weightKgForWasteSize(wasteSize);

        int duplicatePickupId = findActiveDuplicatePickup(residentId, wardId, latitude, longitude);
        if (duplicatePickupId > 0) {
            mergeDuplicatePickup(duplicatePickupId, requestWeightKg, photoPath);
            insertNotification(residentId, "Pickup updated",
                    "Your active pickup request for this location was updated instead of creating a duplicate trip.",
                    "pickup");
            return duplicatePickupId;
        }

        ContentValues cv = new ContentValues();
        cv.put("code", "#" + (4300 + residentId) + "-" + System.currentTimeMillis() % 1000);
        cv.put("ward", ward);
        cv.put("ward_id", wardId);
        cv.put("municipality_id", municipalityId);
        cv.put("place_id", placeId);
        cv.put("category", wasteSize);
        cv.put("waste_type", wasteType);
        cv.put("status", "Pending");
        cv.put("batching_status", BATCH_PENDING_ASSIGNMENT);
        cv.put("pickup_date", daysAgo(0));
        cv.put("resident_id", residentId);
        cv.put("weight_kg", requestWeightKg);
        cv.put("latitude", latitude);
        cv.put("longitude", longitude);
        cv.put("address", address);
        cv.put("house_number", houseNumber);
        cv.put("street_name", streetName);
        cv.put("formatted_address", formattedAddress);
        if ((streetName == null || streetName.trim().isEmpty()) && address != null) {
            cv.put("place_name", address);
        }
        cv.put("last_location_updated_at", nowTimestamp());
        cv.put("photo_path", photoPath);
        cv.put("created_at", nowTimestamp());
        cv.put("submitted_at", nowTimestamp());

        boolean hazardous = ROLE_HAZARDOUS_WASTE.equals(wasteType);
        String pricingStatus = hazardous ? "PendingApproval" : "Estimated";
        cv.put("pricing_status", pricingStatus);

        if (!hazardous) {
            double[] range = computeEstimatedPriceRange(wasteSize, wasteType);
            cv.put("estimated_price_min", range[0]);
            cv.put("estimated_price_max", range[1]);
        }

        long id = db.insert("pickups", null, cv);
        evaluateHotspotRecommendation(municipalityId, wardId, latitude, longitude);

        // Keep the resident's own ward in sync with their most recent request location.
        ContentValues wardUpdate = new ContentValues();
        wardUpdate.put("ward", ward);
        wardUpdate.put("ward_id", wardId);
        wardUpdate.put("municipality_id", municipalityId);
        db.update("users", wardUpdate, "id = ?", new String[]{String.valueOf(residentId)});

        if (hazardous) {
            insertNotification(residentId, "Pickup submitted",
                    "Your hazardous waste pickup request has been submitted. This waste type needs manual " +
                            "pricing approval from the Municipal Admin before a final price is set.", "pickup");
        } else {
            double[] range = computeEstimatedPriceRange(wasteSize, wasteType);
            insertNotification(residentId, "Pickup submitted - price estimate ready",
                    "Your " + wasteSize.toLowerCase(Locale.US) + " " + wasteType.toLowerCase(Locale.US) +
                            " waste pickup request has been submitted. Estimated cost: " +
                            formatTzs(range[0]) + " - " + formatTzs(range[1]) +
                            ". Final price will be based on measured weight at collection.", "pickup");
        }

        tryAutoAssignDriver((int) id, wardId, ward, latitude, longitude,
                requestWeightKg, wasteType);

        return id;
    }

    public int findWardIdByName(String wardName) {
        if (wardName == null || wardName.trim().isEmpty()) return -1;
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT id FROM wards WHERE name_normalized = ? LIMIT 1",
                new String[]{wardName.trim().toLowerCase(Locale.US)});
        try {
            return cursor.moveToFirst() ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private int findActiveDuplicatePickup(int residentId, int wardId, double latitude, double longitude) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, latitude, longitude FROM pickups WHERE resident_id = ? AND ward_id = ? " +
                        "AND status NOT IN ('Completed', 'Cancelled', 'Expired') ORDER BY id DESC",
                new String[]{String.valueOf(residentId), String.valueOf(wardId)});
        try {
            while (cursor.moveToNext()) {
                double distanceMeters = distanceKm(latitude, longitude,
                        cursor.getDouble(1), cursor.getDouble(2)) * 1000.0;
                if (distanceMeters <= 30.0) return cursor.getInt(0);
            }
            return -1;
        } finally {
            cursor.close();
        }
    }

    private void mergeDuplicatePickup(int pickupId, double additionalWeightKg, String photoPath) {
        ContentValues values = new ContentValues();
        values.put("weight_kg", scalarDouble(
                "SELECT COALESCE(weight_kg, 0) FROM pickups WHERE id = ?",
                new String[]{String.valueOf(pickupId)}) + additionalWeightKg);
        if (photoPath != null && !photoPath.trim().isEmpty()) values.put("photo_path", photoPath);
        getWritableDatabase().update("pickups", values, "id = ?",
                new String[]{String.valueOf(pickupId)});
    }

    public String getWardName(int wardId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor cursor = db.rawQuery("SELECT name FROM wards WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(wardId)});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public int getMunicipalityIdForWard(int wardId) {
        if (wardId <= 0) return -1;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT municipality_id FROM wards WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(wardId)});
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    public String getMunicipalityName(int municipalityId) {
        if (municipalityId <= 0) return null;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT name FROM municipalities WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(municipalityId)});
        try {
            return cursor.moveToFirst() ? cursor.getString(0) : null;
        } finally {
            cursor.close();
        }
    }

    public List<WardRow> getAllWards() {
        List<WardRow> wards = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, municipality, municipality_id, boundary_geojson, " +
                        "boundary_status, source_shape_id, is_active, assigned_operator_id " +
                        "FROM wards ORDER BY name COLLATE NOCASE", null);
        try {
            while (cursor.moveToNext()) wards.add(new WardRow(cursor.getInt(0), cursor.getString(1),
                    cursor.getString(2), cursor.isNull(3) ? -1 : cursor.getInt(3),
                    cursor.getString(4), cursor.getString(5), cursor.getString(6),
                    cursor.getInt(7) != 0, cursor.isNull(8) ? -1 : cursor.getInt(8)));
        } finally {
            cursor.close();
        }
        return wards;
    }

    public List<WardRow> getWardsInMunicipality(int municipalityId) {
        List<WardRow> wards = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, municipality, municipality_id, boundary_geojson, " +
                        "boundary_status, source_shape_id, is_active, assigned_operator_id " +
                        "FROM wards WHERE municipality_id = ? ORDER BY name COLLATE NOCASE",
                new String[]{String.valueOf(municipalityId)});
        try {
            while (cursor.moveToNext()) wards.add(new WardRow(cursor.getInt(0), cursor.getString(1),
                    cursor.getString(2), cursor.isNull(3) ? -1 : cursor.getInt(3),
                    cursor.getString(4), cursor.getString(5), cursor.getString(6),
                    cursor.getInt(7) != 0, cursor.isNull(8) ? -1 : cursor.getInt(8)));
        } finally {
            cursor.close();
        }
        return wards;
    }

    private static String normalizeWardName(String name) {
        return name == null ? "" : name.trim().replaceAll("\\s+", " ").toLowerCase(Locale.US);
    }

    public long addWard(String name, String municipality) {
        String normalized = normalizeWardName(name);
        if (normalized.isEmpty() || findWardIdByName(name) > 0) return -1;
        ContentValues values = new ContentValues();
        values.put("name", name.trim().replaceAll("\\s+", " "));
        values.put("name_normalized", normalized);
        values.put("normalized_name", normalized);
        values.put("municipality", municipality);
        values.put("boundary_status", "NOT_MAPPED");
        values.put("is_active", 1);
        values.put("created_at", nowTimestamp());
        values.put("updated_at", nowTimestamp());
        return getWritableDatabase().insert("wards", null, values);
    }

    public List<String> getActiveMunicipalityNames() {
        List<String> names = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT name FROM municipalities WHERE is_active = 1 ORDER BY name COLLATE NOCASE", null);
        try {
            while (cursor.moveToNext()) names.add(cursor.getString(0));
        } finally {
            cursor.close();
        }
        return names;
    }

    public List<String> getWardNamesInMunicipality(String municipality) {
        List<String> names = new ArrayList<>();
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT name FROM wards WHERE municipality = ? AND is_active = 1 ORDER BY name COLLATE NOCASE",
                new String[]{municipality});
        try {
            while (cursor.moveToNext()) names.add(cursor.getString(0));
        } finally {
            cursor.close();
        }
        return names;
    }

    public boolean updateWard(int wardId, String name, boolean active, int operatorId,
                              String boundaryGeoJson) {
        String normalized = normalizeWardName(name);
        if (wardId <= 0 || normalized.isEmpty()) return false;
        Cursor duplicate = getReadableDatabase().rawQuery(
                "SELECT id FROM wards WHERE name_normalized=? AND id<>? LIMIT 1",
                new String[]{normalized, String.valueOf(wardId)});
        try { if (duplicate.moveToFirst()) return false; } finally { duplicate.close(); }
        ContentValues values = new ContentValues();
        values.put("name", name.trim().replaceAll("\\s+", " "));
        values.put("name_normalized", normalized);
        values.put("normalized_name", normalized);
        values.put("is_active", active ? 1 : 0);
        if (operatorId > 0) values.put("assigned_operator_id", operatorId);
        else values.putNull("assigned_operator_id");
        if (boundaryGeoJson != null) {
            values.put("boundary_geojson", boundaryGeoJson);
            values.put("boundary_status", "MAPPED");
        }
        values.put("updated_at", nowTimestamp());
        return getWritableDatabase().update("wards", values, "id=?",
                new String[]{String.valueOf(wardId)}) == 1;
    }

    public boolean assignWardAdmin(int wardId, int adminId) {
        if (wardId <= 0) return false;
        String wardName = getWardName(wardId);
        int municipalityId = getMunicipalityIdForWard(wardId);
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            ContentValues clear = new ContentValues();
            clear.putNull("ward_id");
            clear.putNull("municipality_id");
            clear.put("ward", "Unassigned");
            db.update("users", clear, "role = ? AND ward_id = ?",
                    new String[]{ROLE_WARD_ADMIN, String.valueOf(wardId)});

            if (adminId > 0) {
                ContentValues assign = new ContentValues();
                assign.put("ward_id", wardId);
                assign.put("municipality_id", municipalityId);
                assign.put("ward", wardName);
                int updated = db.update("users", assign, "id = ? AND role = ?",
                        new String[]{String.valueOf(adminId), ROLE_WARD_ADMIN});
                if (updated != 1) return false;
            }
            db.setTransactionSuccessful();
            return true;
        } finally {
            db.endTransaction();
        }
    }

    public int findWardIdContaining(double latitude, double longitude) {
        WardRow ward = findMappedWardContaining(latitude, longitude);
        return ward != null ? ward.id : -1;
    }

    public WardRow findMappedWardContaining(double latitude, double longitude) {
        if (!com.takago.app.location.RoutingService.isValidCoordinate(latitude, longitude)) return null;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, name, municipality, municipality_id, boundary_geojson, " +
                        "boundary_status, source_shape_id, is_active, assigned_operator_id " +
                        "FROM wards WHERE is_active = 1 " +
                        "AND boundary_geojson IS NOT NULL AND TRIM(boundary_geojson) <> '' " +
                        "AND COALESCE(boundary_status, 'MAPPED') = 'MAPPED'",
                null);
        try {
            while (cursor.moveToNext()) {
                WardRow ward = new WardRow(cursor.getInt(0), cursor.getString(1),
                        cursor.getString(2), cursor.isNull(3) ? -1 : cursor.getInt(3),
                        cursor.getString(4), cursor.getString(5), cursor.getString(6),
                        cursor.getInt(7) != 0, cursor.isNull(8) ? -1 : cursor.getInt(8));
                if (com.takago.app.location.WardBoundaryUtils.containsPoint(
                        ward.boundaryGeoJson, latitude, longitude)) return ward;
            }
        } finally {
            cursor.close();
        }
        return null;
    }

    public void savePickupRoute(int pickupId, String encodedPolyline,
                                int distanceMeters, int durationSeconds) {
        ContentValues values = new ContentValues();
        values.put("encoded_polyline", encodedPolyline);
        values.put("route_distance_meters", distanceMeters);
        values.put("route_duration_seconds", durationSeconds);
        values.put("distance_km", distanceMeters / 1000.0);
        values.put("eta_min", Math.max(1, (int) Math.ceil(durationSeconds / 60.0)));
        values.put("route_calculated_at", nowTimestamp());
        getWritableDatabase().update("pickups", values, "id = ?",
                new String[]{String.valueOf(pickupId)});
    }

    public static final String ROLE_HAZARDOUS_WASTE = "Hazardous";

    private static String formatTzs(double amount) {
        return String.format(Locale.US, "TZS %,.0f", amount);
    }

    /** Waste-type rate multiplier from the admin-configurable pricing settings. Hazardous waste has no multiplier - it's always manual. */
    private double wasteTypeMultiplier(String wasteType, PricingSettings s) {
        if (wasteType == null) {
            return s.multHousehold;
        }
        switch (wasteType) {
            case "Garden":
                return s.multGarden;
            case "Recyclables":
                return s.multRecyclables;
            case "Construction":
                return s.multConstruction;
            case "Electronic":
                return s.multElectronic;
            default: // Household
                return s.multHousehold;
        }
    }

    /**
     * A simple, honest estimate shown to the resident before collection: runs the same weight-based
     * formula as the final price, using the nominal weight for the chosen size stretched +/-30% to
     * reflect that the true weight won't be known until the truck scale measures it. Distance is not
     * charged at estimate time since the assigned driver/route isn't known yet.
     */
    public double[] computeEstimatedPriceRange(String wasteSize, String wasteType) {
        PricingSettings s = getPricingSettings();
        double nominalWeight = weightKgForWasteSize(wasteSize);
        double minWeight = Math.max(0, nominalWeight * 0.7);
        double maxWeight = nominalWeight * 1.3;
        double multiplier = wasteTypeMultiplier(wasteType, s);

        double minPrice = s.bookingFee + Math.max(0, minWeight - s.includedWeightKg) * s.ratePerKg * multiplier;
        double maxPrice = s.bookingFee + Math.max(0, maxWeight - s.includedWeightKg) * s.ratePerKg * multiplier;
        return new double[]{minPrice, maxPrice};
    }

    // ---------- Pricing settings ----------

    public PricingSettings getPricingSettings() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT booking_fee, included_weight_kg, rate_per_kg, distance_free_km, " +
                "distance_fee_per_km, mult_household, mult_garden, mult_recyclables, mult_construction, " +
                "mult_electronic FROM pricing_settings WHERE id = 1", null);
        PricingSettings s = new PricingSettings();
        if (c.moveToFirst()) {
            s.bookingFee = c.getDouble(0);
            s.includedWeightKg = c.getDouble(1);
            s.ratePerKg = c.getDouble(2);
            s.distanceFreeKm = c.getDouble(3);
            s.distanceFeePerKm = c.getDouble(4);
            s.multHousehold = c.getDouble(5);
            s.multGarden = c.getDouble(6);
            s.multRecyclables = c.getDouble(7);
            s.multConstruction = c.getDouble(8);
            s.multElectronic = c.getDouble(9);
        } else {
            // Defensive fallback - pricing_settings should always have its singleton row seeded.
            s.bookingFee = 2000;
            s.includedWeightKg = 20;
            s.ratePerKg = 100;
            s.distanceFreeKm = 3;
            s.distanceFeePerKm = 300;
            s.multHousehold = 1.0;
            s.multGarden = 0.8;
            s.multRecyclables = 0.7;
            s.multConstruction = 1.5;
            s.multElectronic = 1.3;
        }
        c.close();
        return s;
    }

    /** Municipal Admin updates the pricing configuration used for every future estimate/final price. */
    public void updatePricingSettings(PricingSettings s) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("booking_fee", s.bookingFee);
        cv.put("included_weight_kg", s.includedWeightKg);
        cv.put("rate_per_kg", s.ratePerKg);
        cv.put("distance_free_km", s.distanceFreeKm);
        cv.put("distance_fee_per_km", s.distanceFeePerKm);
        cv.put("mult_household", s.multHousehold);
        cv.put("mult_garden", s.multGarden);
        cv.put("mult_recyclables", s.multRecyclables);
        cv.put("mult_construction", s.multConstruction);
        cv.put("mult_electronic", s.multElectronic);
        db.update("pricing_settings", cv, "id = 1", null);
    }

    // ---------- Final pricing at collection ----------

    /**
     * Called when a driver enters the truck-scale weight at collection. Validates the weight,
     * computes the final price from the admin-configured settings + this pickup's waste type and
     * distance, persists everything, and notifies the resident at each step. Hazardous waste never
     * gets an automatic price - it's flagged for the Municipal Admin to set manually instead.
     */
    public PriceResult computeFinalPrice(int pickupId, double measuredWeightKg, String scalePhotoPath) {
        PriceResult result = new PriceResult();

        if (measuredWeightKg <= 0) {
            result.success = false;
            result.errorMessage = "Measured weight must be greater than 0 kg.";
            return result;
        }

        PickupRow pickup = getTripById(pickupId);
        if (pickup == null) {
            result.success = false;
            result.errorMessage = "Pickup not found.";
            return result;
        }

        int residentId = pickup.residentId;
        insertNotification(residentId, "Waste weighed",
                "Your waste has been weighed at collection: " + formatWeight(measuredWeightKg) + ".", "pickup");

        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("measured_weight_kg", measuredWeightKg);
        cv.put("scale_photo_path", scalePhotoPath);

        if (ROLE_HAZARDOUS_WASTE.equals(pickup.wasteType)) {
            cv.put("pricing_status", "PendingApproval");
            db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});

            result.success = true;
            result.requiresManualApproval = true;
            result.measuredWeightKg = measuredWeightKg;

            insertNotification(residentId, "Pricing pending approval",
                    "Your hazardous waste has been collected and weighed. The Municipal Admin will set a " +
                            "final price for this pickup shortly.", "pickup");
            return result;
        }

        PricingSettings s = getPricingSettings();
        double distanceKm = Math.max(0, pickup.distanceKm);
        double extraWeight = Math.max(0, measuredWeightKg - s.includedWeightKg);
        double multiplier = wasteTypeMultiplier(pickup.wasteType, s);
        double weightFee = extraWeight * s.ratePerKg * multiplier;
        double distanceFee = distanceKm <= s.distanceFreeKm ? 0 : (distanceKm - s.distanceFreeKm) * s.distanceFeePerKm;
        double finalPrice = Math.max(0, s.bookingFee + weightFee + distanceFee);

        cv.put("included_weight_kg", s.includedWeightKg);
        cv.put("rate_per_kg", s.ratePerKg);
        cv.put("booking_fee", s.bookingFee);
        cv.put("waste_type_multiplier", multiplier);
        cv.put("distance_fee", distanceFee);
        cv.put("final_price", finalPrice);
        cv.put("pricing_status", "Finalized");
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});

        result.success = true;
        result.measuredWeightKg = measuredWeightKg;
        result.includedWeightKg = s.includedWeightKg;
        result.extraWeightKg = extraWeight;
        result.bookingFee = s.bookingFee;
        result.weightFee = weightFee;
        result.distanceFee = distanceFee;
        result.finalPrice = finalPrice;

        insertNotification(residentId, "Final price ready",
                "Your final pickup price is " + formatTzs(finalPrice) + ".", "pickup");
        insertNotification(residentId, "Receipt generated",
                "Your receipt for pickup " + pickup.code + " is ready to view.", "pickup");

        return result;
    }

    private static String formatWeight(double kg) {
        return String.format(Locale.US, "%.1f kg", kg);
    }

    private static double distanceKm(double lat1, double lon1, double lat2, double lon2) {
        double earthRadiusKm = 6371;
        double dLat = Math.toRadians(lat2 - lat1);
        double dLon = Math.toRadians(lon2 - lon1);
        double a = Math.sin(dLat / 2) * Math.sin(dLat / 2)
                + Math.cos(Math.toRadians(lat1)) * Math.cos(Math.toRadians(lat2))
                * Math.sin(dLon / 2) * Math.sin(dLon / 2);
        double c = 2 * Math.atan2(Math.sqrt(a), Math.sqrt(1 - a));
        return earthRadiusKm * c;
    }

    private static String timestampPlusMinutes(int minutes) {
        Calendar cal = Calendar.getInstance();
        cal.add(Calendar.MINUTE, minutes);
        SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
        return fmt.format(cal.getTime());
    }

    private static String timestampPlusMinutes(String baseTimestamp, int minutes) {
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            Date base = fmt.parse(baseTimestamp);
            Calendar cal = Calendar.getInstance();
            cal.setTime(base != null ? base : new Date());
            cal.add(Calendar.MINUTE, minutes);
            return fmt.format(cal.getTime());
        } catch (Exception e) {
            return timestampPlusMinutes(minutes);
        }
    }

    /**
     * Drivers who can actually be auto-assigned right now: same ward, marked Available, and
     * driving a vehicle that's been Approved. Drivers outside the ward are never even considered.
     */
    public List<UserAccount> getAssignableDriversInWard(String ward) {
        return getAssignableDriversInWard(findWardIdByName(ward), ward, 0);
    }

    public List<UserAccount> getAssignableDriversInWard(int wardId, String wardFallback) {
        return getAssignableDriversInWard(wardId, wardFallback, 0);
    }

    private List<UserAccount> getAssignableDriversInWard(int wardId, String wardFallback,
                                                         double minimumWeightKg) {
        List<UserAccount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c;
        if (wardId > 0) {
            int municipalityId = getMunicipalityIdForWard(wardId);
            c = db.rawQuery("SELECT " + USER_ACCOUNT_COLUMNS + " FROM users " +
                            "WHERE role = ? AND municipality_id = ? AND ward_id = ? " +
                            "AND availability_status = 'Available' " +
                            "AND vehicle_id IN (SELECT id FROM vehicles " +
                            "WHERE status = 'Approved' AND municipality_id = ? AND ward_id = ? " +
                            "AND (capacity_weight_kg IS NULL OR capacity_weight_kg <= 0 OR capacity_weight_kg >= ?)) " +
                            "ORDER BY id ASC",
                    new String[]{ROLE_DRIVER, String.valueOf(municipalityId), String.valueOf(wardId),
                            String.valueOf(municipalityId), String.valueOf(wardId),
                            String.valueOf(minimumWeightKg)});
        } else {
            c = db.rawQuery("SELECT " + USER_ACCOUNT_COLUMNS + " FROM users " +
                        "WHERE role = ? AND LOWER(TRIM(ward)) = LOWER(TRIM(?)) AND availability_status = 'Available' " +
                        "AND vehicle_id IN (SELECT id FROM vehicles WHERE status = 'Approved') " +
                        "ORDER BY id ASC",
                new String[]{ROLE_DRIVER, wardFallback});
        }
        while (c.moveToNext()) {
            list.add(readUserAccountRow(c));
        }
        c.close();
        return list;
    }

    /**
     * Finds the nearest eligible driver in the request's ward and assigns them automatically.
     * If nobody is available, the request is left Pending and Ward Admins / Waste Operators for
     * that ward are notified instead.
     */
    private void tryAutoAssignDriver(int pickupId, int wardId, String ward,
                                     double latitude, double longitude,
                                     double minimumWeightKg, String wasteType) {
        if (ROLE_HAZARDOUS_WASTE.equals(wasteType)) {
            markPendingNextBatch(pickupId);
            notifyWardStaff(ward, "Manual assignment needed",
                    "Hazardous waste requests require manual review before assignment.", "assignment");
            return;
        }
        int municipalityId = getMunicipalityIdForWard(wardId);
        int operatorId = getAssignedOperatorIdForWard(wardId);
        int openGroupId = findJoinableRouteGroup(municipalityId, wardId, operatorId,
                minimumWeightKg, wasteType, latitude, longitude);
        if (openGroupId > 0) {
            addPickupToRouteGroup(openGroupId, pickupId, latitude, longitude, minimumWeightKg);
            return;
        }

        List<UserAccount> candidates = getAssignableDriversInWard(wardId, ward, minimumWeightKg);

        UserAccount nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UserAccount driver : candidates) {
            if (driver.latitude == 0 && driver.longitude == 0) {
                continue; // no known location yet - skip rather than guess
            }
            double distance = distanceKm(latitude, longitude, driver.latitude, driver.longitude);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = driver;
            }
        }

        SQLiteDatabase db = getWritableDatabase();
        if (nearest != null) {
            if (operatorId <= 0) operatorId = nearest.operatorId;
            int groupId = createOpenRouteGroup(municipalityId, wardId, operatorId,
                    nearest.id, nearest.vehicleId);
            ContentValues cv = new ContentValues();
            cv.put("driver_id", nearest.id);
            cv.put("assigned_vehicle_id", nearest.vehicleId);
            cv.put("status", "Assigned");
            cv.put("assignment_type", "AUTO");
            cv.put("distance_km", nearestDistance);
            cv.put("timeout_at", timestampPlusMinutes(10));
            cv.put("group_id", groupId);
            cv.put("stop_order", 1);
            cv.put("batching_status", BATCH_ASSIGNED_TO_ROUTE);
            db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
            addRouteStop(groupId, pickupId, 1, latitude, longitude);
            notifyNearbyResidentsForRoute(groupId, pickupId, municipalityId, wardId,
                    latitude, longitude, wasteType);

            insertNotification(nearest.id, "New pickup assigned",
                    "You've been automatically assigned a nearby pickup request.", "assignment");
        } else {
            markPendingNextBatch(pickupId);
            notifyWardStaff(ward, "No driver available",
                    "No available approved driver in this ward. The request was moved to the next batch.", "assignment");
        }
    }

    private int createOpenRouteGroup(int municipalityId, int wardId, int operatorId,
                                     int driverId, int vehicleId) {
        SQLiteDatabase db = getWritableDatabase();
        String createdAt = nowTimestamp();
        int joinWindow = getIntSetting("batch_join_window_minutes", DEFAULT_JOIN_WINDOW_MINUTES);
        ContentValues values = new ContentValues();
        values.put("municipality_id", municipalityId);
        values.put("ward_id", wardId);
        values.put("operator_id", operatorId > 0 ? operatorId : null);
        values.put("driver_id", driverId);
        values.put("vehicle_id", vehicleId);
        values.put("status", GROUP_OPEN_FOR_STOPS);
        values.put("join_deadline", timestampPlusMinutes(createdAt, joinWindow));
        values.put("maximum_stops", getIntSetting("batch_maximum_stops", DEFAULT_MAXIMUM_STOPS));
        values.put("created_at", createdAt);
        return (int) db.insert("request_groups", null, values);
    }

    private int findJoinableRouteGroup(int municipalityId, int wardId, int operatorId,
                                       double additionalWeightKg, String wasteType,
                                       double latitude, double longitude) {
        if (municipalityId <= 0 || wardId <= 0) return -1;
        lockExpiredOpenRouteGroups();
        List<String> args = new ArrayList<>();
        String where = "municipality_id = ? AND ward_id = ? AND status = ? " +
                "AND join_deadline >= ?";
        args.add(String.valueOf(municipalityId));
        args.add(String.valueOf(wardId));
        args.add(GROUP_OPEN_FOR_STOPS);
        args.add(nowTimestamp());
        if (operatorId > 0) {
            where += " AND operator_id = ?";
            args.add(String.valueOf(operatorId));
        }
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, vehicle_id, maximum_stops FROM request_groups WHERE " + where +
                        " ORDER BY created_at ASC",
                args.toArray(new String[0]));
        try {
            while (cursor.moveToNext()) {
                int groupId = cursor.getInt(0);
                int vehicleId = cursor.isNull(1) ? -1 : cursor.getInt(1);
                int maxStops = cursor.isNull(2)
                        ? getIntSetting("batch_maximum_stops", DEFAULT_MAXIMUM_STOPS) : cursor.getInt(2);
                if (routeStopCount(groupId) >= maxStops) {
                    lockRouteGroup(groupId, "Stop limit reached");
                    continue;
                }
                if ((routeStopCount(groupId) + 1) * 10
                        > getIntSetting("batch_route_duration_limit_minutes",
                        DEFAULT_ROUTE_DURATION_LIMIT_MINUTES)) {
                    lockRouteGroup(groupId, "Route duration limit reached");
                    continue;
                }
                if (!vehicleHasRemainingCapacity(groupId, vehicleId, additionalWeightKg)) {
                    lockRouteGroup(groupId, "Vehicle capacity reached");
                    notifyGroupDriver(groupId, "Vehicle capacity reached",
                            "This route is locked because the configured capacity threshold was reached.");
                    continue;
                }
                if (!routeHasNearbyStop(groupId, latitude, longitude)) continue;
                if (!isAddedDelayWithinLimit(groupId, latitude, longitude)) continue;
                if (!groupWasteCompatible(groupId, wasteType)) continue;
                return groupId;
            }
            return -1;
        } finally {
            cursor.close();
        }
    }

    private void addPickupToRouteGroup(int groupId, int pickupId, double latitude,
                                       double longitude, double requestWeightKg) {
        SQLiteDatabase db = getWritableDatabase();
        int nextOrder = routeStopCount(groupId) + 1;
        Cursor group = db.rawQuery(
                "SELECT driver_id, vehicle_id FROM request_groups WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(groupId)});
        int driverId = -1;
        int vehicleId = -1;
        try {
            if (group.moveToFirst()) {
                driverId = group.isNull(0) ? -1 : group.getInt(0);
                vehicleId = group.isNull(1) ? -1 : group.getInt(1);
            }
        } finally {
            group.close();
        }
        ContentValues pickupValues = new ContentValues();
        pickupValues.put("group_id", groupId);
        pickupValues.put("stop_order", nextOrder);
        pickupValues.put("driver_id", driverId);
        pickupValues.put("assigned_vehicle_id", vehicleId);
        pickupValues.put("status", "Assigned");
        pickupValues.put("assignment_type", "BATCHED");
        pickupValues.put("batching_status", BATCH_ASSIGNED_TO_ROUTE);
        pickupValues.putNull("encoded_polyline");
        pickupValues.putNull("route_calculated_at");
        db.update("pickups", pickupValues, "id = ?", new String[]{String.valueOf(pickupId)});
        addRouteStop(groupId, pickupId, nextOrder, latitude, longitude);
        optimizeRouteStopOrder(groupId);
        notifyGroupResidents(groupId, "Route updated",
                "A nearby stop was added to your collection route. ETA may change.");
        notifyGroupDriver(groupId, "Stop added",
                "A nearby confirmed pickup was added to your route. Recalculate the street route before continuing.");
    }

    private void addRouteStop(int groupId, int pickupId, int stopOrder, double latitude, double longitude) {
        ContentValues member = new ContentValues();
        member.put("group_id", groupId);
        member.put("pickup_id", pickupId);
        member.put("joined_at", nowTimestamp());
        getWritableDatabase().insertWithOnConflict("group_members", null, member,
                SQLiteDatabase.CONFLICT_IGNORE);

        ContentValues stop = new ContentValues();
        stop.put("group_id", groupId);
        stop.put("pickup_id", pickupId);
        stop.put("request_id", pickupId);
        stop.put("stop_order", stopOrder);
        stop.put("latitude", latitude);
        stop.put("longitude", longitude);
        stop.put("status", "Pending");
        stop.put("added_at", nowTimestamp());
        getWritableDatabase().insertWithOnConflict("route_stops", null, stop,
                SQLiteDatabase.CONFLICT_IGNORE);
    }

    private void markPendingNextBatch(int pickupId) {
        ContentValues values = new ContentValues();
        values.put("batching_status", BATCH_PENDING_NEXT);
        values.put("status", BATCH_PENDING_NEXT);
        getWritableDatabase().update("pickups", values, "id = ?",
                new String[]{String.valueOf(pickupId)});
        notifyResidentForPickup(pickupId, "Moved to next batch",
                "This request will be included in the next ward collection batch.", "batching");
    }

    private int getAssignedOperatorIdForWard(int wardId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT assigned_operator_id FROM wards WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(wardId)});
        try {
            return cursor.moveToFirst() && !cursor.isNull(0) ? cursor.getInt(0) : -1;
        } finally {
            cursor.close();
        }
    }

    private int routeStopCount(int groupId) {
        return scalarInt("SELECT COUNT(*) FROM route_stops WHERE group_id = ?",
                new String[]{String.valueOf(groupId)});
    }

    private boolean routeHasNearbyStop(int groupId, double latitude, double longitude) {
        double radiusMeters = getDoubleSetting("batch_nearby_radius_meters", DEFAULT_NEARBY_RADIUS_METERS);
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude, longitude FROM route_stops WHERE group_id = ?",
                new String[]{String.valueOf(groupId)});
        try {
            while (cursor.moveToNext()) {
                double distanceMeters = distanceKm(latitude, longitude,
                        cursor.getDouble(0), cursor.getDouble(1)) * 1000.0;
                if (distanceMeters <= radiusMeters) return true;
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private boolean isAddedDelayWithinLimit(int groupId, double latitude, double longitude) {
        int limitMinutes = getIntSetting("batch_max_added_delay_minutes", DEFAULT_MAX_ADDED_DELAY_MINUTES);
        double nearestKm = Double.MAX_VALUE;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude, longitude FROM route_stops WHERE group_id = ?",
                new String[]{String.valueOf(groupId)});
        try {
            while (cursor.moveToNext()) {
                nearestKm = Math.min(nearestKm, distanceKm(latitude, longitude,
                        cursor.getDouble(0), cursor.getDouble(1)));
            }
        } finally {
            cursor.close();
        }
        if (nearestKm == Double.MAX_VALUE) return true;
        double estimatedDelayMinutes = (nearestKm / 25.0) * 60.0;
        return estimatedDelayMinutes <= limitMinutes;
    }

    private boolean groupWasteCompatible(int groupId, String wasteType) {
        if (ROLE_HAZARDOUS_WASTE.equals(wasteType)) return false;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT waste_type FROM pickups WHERE group_id = ?",
                new String[]{String.valueOf(groupId)});
        try {
            while (cursor.moveToNext()) {
                if (ROLE_HAZARDOUS_WASTE.equals(cursor.getString(0))) return false;
            }
            return true;
        } finally {
            cursor.close();
        }
    }

    private boolean vehicleHasRemainingCapacity(int groupId, int vehicleId, double additionalWeightKg) {
        if (vehicleId <= 0) return true;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT capacity_weight_kg FROM vehicles WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(vehicleId)});
        double capacityKg = 0;
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) capacityKg = cursor.getDouble(0);
        } finally {
            cursor.close();
        }
        if (capacityKg <= 0) return true;
        double threshold = getDoubleSetting("batch_capacity_threshold_percent",
                DEFAULT_CAPACITY_THRESHOLD_PERCENT) / 100.0;
        double currentLoad = scalarDouble(
                "SELECT COALESCE(SUM(weight_kg), 0) FROM pickups WHERE group_id = ? " +
                        "AND status NOT IN ('Cancelled', 'Expired')",
                new String[]{String.valueOf(groupId)});
        return currentLoad + additionalWeightKg <= capacityKg * threshold;
    }

    private boolean vehicleHasRemainingCapacityForSingle(int vehicleId, double weightKg) {
        if (vehicleId <= 0) return true;
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT capacity_weight_kg FROM vehicles WHERE id = ? AND status = 'Approved' LIMIT 1",
                new String[]{String.valueOf(vehicleId)});
        try {
            if (!cursor.moveToFirst()) return false;
            if (cursor.isNull(0) || cursor.getDouble(0) <= 0) return true;
            double threshold = getDoubleSetting("batch_capacity_threshold_percent",
                    DEFAULT_CAPACITY_THRESHOLD_PERCENT) / 100.0;
            return weightKg <= cursor.getDouble(0) * threshold;
        } finally {
            cursor.close();
        }
    }

    private void lockRouteGroup(int groupId, String reason) {
        ContentValues values = new ContentValues();
        values.put("status", GROUP_LOCKED);
        values.put("route_locked_at", nowTimestamp());
        getWritableDatabase().update("request_groups", values,
                "id = ? AND status = ?",
                new String[]{String.valueOf(groupId), GROUP_OPEN_FOR_STOPS});
        notifyGroupResidents(groupId, "Route locked",
                reason + ". New requests will move to the next ward collection batch.");
    }

    private void lockExpiredOpenRouteGroups() {
        ContentValues values = new ContentValues();
        values.put("status", GROUP_LOCKED);
        values.put("route_locked_at", nowTimestamp());
        getWritableDatabase().update("request_groups", values,
                "status = ? AND join_deadline < ?",
                new String[]{GROUP_OPEN_FOR_STOPS, nowTimestamp()});
    }

    private void optimizeRouteStopOrder(int groupId) {
        List<RouteStopRow> stops = getRouteStops(groupId);
        if (stops.size() <= 1) return;
        List<RouteStopRow> ordered = new ArrayList<>();
        ordered.add(stops.remove(0));
        while (!stops.isEmpty()) {
            RouteStopRow last = ordered.get(ordered.size() - 1);
            int nearestIndex = 0;
            double nearestDistance = Double.MAX_VALUE;
            for (int i = 0; i < stops.size(); i++) {
                RouteStopRow candidate = stops.get(i);
                double distance = distanceKm(last.latitude, last.longitude,
                        candidate.latitude, candidate.longitude);
                if (distance < nearestDistance) {
                    nearestDistance = distance;
                    nearestIndex = i;
                }
            }
            ordered.add(stops.remove(nearestIndex));
        }
        SQLiteDatabase db = getWritableDatabase();
        for (int i = 0; i < ordered.size(); i++) {
            int order = i + 1;
            ContentValues stopValues = new ContentValues();
            stopValues.put("stop_order", order);
            stopValues.put("eta_seconds", order * 600);
            stopValues.put("eta", timestampPlusMinutes(order * 10));
            db.update("route_stops", stopValues, "group_id = ? AND pickup_id = ?",
                    new String[]{String.valueOf(groupId), String.valueOf(ordered.get(i).pickupId)});
            ContentValues pickupValues = new ContentValues();
            pickupValues.put("stop_order", order);
            pickupValues.put("eta_min", order * 10);
            db.update("pickups", pickupValues, "id = ?",
                    new String[]{String.valueOf(ordered.get(i).pickupId)});
        }
    }

    private void notifyNearbyResidentsForRoute(int groupId, int pickupId, int municipalityId,
                                               int wardId, double latitude, double longitude,
                                               String wasteType) {
        double radiusMeters = getDoubleSetting("batch_nearby_radius_meters", DEFAULT_NEARBY_RADIUS_METERS);
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id, latitude, longitude FROM users WHERE role = ? " +
                        "AND municipality_id = ? AND ward_id = ? " +
                        "AND latitude IS NOT NULL AND longitude IS NOT NULL",
                new String[]{ROLE_RESIDENT, String.valueOf(municipalityId), String.valueOf(wardId)});
        try {
            while (cursor.moveToNext()) {
                int residentId = cursor.getInt(0);
                if (hasActivePickupInWard(residentId, wardId)) continue;
                double distanceMeters = distanceKm(latitude, longitude,
                        cursor.getDouble(1), cursor.getDouble(2)) * 1000.0;
                if (distanceMeters <= radiusMeters) {
                    insertNotification(residentId, "Nearby collection available",
                            "A collection vehicle will be near your street within 30 minutes. Do you have waste to add? Add My Pickup or Not Now.",
                            "batching");
                }
            }
        } finally {
            cursor.close();
        }
    }

    private void evaluateHotspotRecommendation(int municipalityId, int wardId,
                                               double latitude, double longitude) {
        double radiusMeters = getDoubleSetting("batch_nearby_radius_meters", DEFAULT_NEARBY_RADIUS_METERS);
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude, longitude FROM pickups WHERE municipality_id = ? AND ward_id = ? " +
                        "AND submitted_at >= datetime('now', '-14 days')",
                new String[]{String.valueOf(municipalityId), String.valueOf(wardId)});
        int nearbyCount = 0;
        try {
            while (cursor.moveToNext()) {
                double distanceMeters = distanceKm(latitude, longitude,
                        cursor.getDouble(0), cursor.getDouble(1)) * 1000.0;
                if (distanceMeters <= radiusMeters) nearbyCount++;
            }
        } finally {
            cursor.close();
        }
        if (nearbyCount < 3 || existingOpenHotspot(municipalityId, wardId, latitude, longitude, radiusMeters)) {
            return;
        }
        int operatorId = getAssignedOperatorIdForWard(wardId);
        ContentValues values = new ContentValues();
        values.put("municipality_id", municipalityId);
        values.put("ward_id", wardId);
        values.put("operator_id", operatorId > 0 ? operatorId : null);
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("request_count", nearbyCount);
        values.put("status", "Recommended");
        values.put("created_at", nowTimestamp());
        values.put("updated_at", nowTimestamp());
        getWritableDatabase().insert("hotspot_recommendations", null, values);
        notifyMunicipalAdmins(municipalityId, "Scheduled collection recommended",
                "Repeated requests in this ward indicate a collection hotspot. Review recurring collection scheduling.");
        if (operatorId > 0) {
            insertNotification(operatorId, "Scheduled collection recommended",
                    "Repeated requests near one street indicate a collection hotspot.", "hotspot");
        }
    }

    private boolean existingOpenHotspot(int municipalityId, int wardId, double latitude,
                                        double longitude, double radiusMeters) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT latitude, longitude FROM hotspot_recommendations " +
                        "WHERE municipality_id = ? AND ward_id = ? AND status = 'Recommended'",
                new String[]{String.valueOf(municipalityId), String.valueOf(wardId)});
        try {
            while (cursor.moveToNext()) {
                double distanceMeters = distanceKm(latitude, longitude,
                        cursor.getDouble(0), cursor.getDouble(1)) * 1000.0;
                if (distanceMeters <= radiusMeters) return true;
            }
            return false;
        } finally {
            cursor.close();
        }
    }

    private void notifyMunicipalAdmins(int municipalityId, String title, String message) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT id FROM users WHERE role = ? AND municipality_id = ?",
                new String[]{ROLE_MUNICIPAL_ADMIN, String.valueOf(municipalityId)});
        try {
            while (cursor.moveToNext()) insertNotification(cursor.getInt(0), title, message, "hotspot");
        } finally {
            cursor.close();
        }
    }

    private boolean hasActivePickupInWard(int residentId, int wardId) {
        return scalarInt("SELECT COUNT(*) FROM pickups WHERE resident_id = ? AND ward_id = ? " +
                        "AND status NOT IN ('Completed', 'Cancelled', 'Expired')",
                new String[]{String.valueOf(residentId), String.valueOf(wardId)}) > 0;
    }

    private void notifyGroupResidents(int groupId, String title, String message) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT DISTINCT resident_id FROM pickups WHERE group_id = ? AND resident_id IS NOT NULL",
                new String[]{String.valueOf(groupId)});
        try {
            while (cursor.moveToNext()) insertNotification(cursor.getInt(0), title, message, "batching");
        } finally {
            cursor.close();
        }
    }

    private void notifyGroupDriver(int groupId, String title, String message) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT driver_id FROM request_groups WHERE id = ? LIMIT 1",
                new String[]{String.valueOf(groupId)});
        try {
            if (cursor.moveToFirst() && !cursor.isNull(0)) {
                insertNotification(cursor.getInt(0), title, message, "batching");
            }
        } finally {
            cursor.close();
        }
    }

    private void completeGroupIfDone(int groupId) {
        int remaining = scalarInt(
                "SELECT COUNT(*) FROM pickups WHERE group_id = ? " +
                        "AND status NOT IN ('Completed', 'Cancelled', 'Expired')",
                new String[]{String.valueOf(groupId)});
        if (remaining > 0) return;
        ContentValues values = new ContentValues();
        values.put("status", GROUP_COMPLETED);
        getWritableDatabase().update("request_groups", values, "id = ?",
                new String[]{String.valueOf(groupId)});
    }

    private int getIntSetting(String key, int fallback) {
        String value = getSetting(getReadableDatabase(), key);
        if (value == null) return fallback;
        try {
            return Integer.parseInt(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    private double getDoubleSetting(String key, double fallback) {
        String value = getSetting(getReadableDatabase(), key);
        if (value == null) return fallback;
        try {
            return Double.parseDouble(value);
        } catch (NumberFormatException e) {
            return fallback;
        }
    }

    /** Notifies every Ward Admin for this ward, plus every Waste Operator (any ward). */
    private void notifyWardStaff(String ward, String title, String message, String type) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id FROM users WHERE (role = ? AND ward = ?) OR role = ?",
                new String[]{ROLE_WARD_ADMIN, ward, ROLE_TRUCK_OWNER});
        while (c.moveToNext()) {
            insertNotification(c.getInt(0), title, message, type);
        }
        c.close();
    }

    /**
     * Driver taps Accept on an assigned request: status becomes On the Way.
     */
    public void acceptPickup(int pickupId, int driverId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Accepted");
        cv.put("driver_response_status", "Accepted");
        cv.put("accepted_at", nowTimestamp());
        db.update("pickups", cv, "id = ? AND driver_id = ?", new String[]{String.valueOf(pickupId), String.valueOf(driverId)});
        PickupRow pickup = getTripById(pickupId);
        if (pickup != null && pickup.groupId > 0) {
            ContentValues groupValues = new ContentValues();
            groupValues.put("status", GROUP_IN_PROGRESS);
            groupValues.put("route_locked_at", nowTimestamp());
            db.update("request_groups", groupValues, "id = ?",
                    new String[]{String.valueOf(pickup.groupId)});
        }

        notifyResidentForPickup(pickupId, "Driver accepted", "Your driver accepted the pickup request.", "assignment");
    }

    public void markPickupOnTheWayLocal(int pickupId, int driverId) {
        ContentValues values = new ContentValues();
        values.put("status", "On the way");
        getWritableDatabase().update("pickups", values, "id = ? AND driver_id = ?",
                new String[]{String.valueOf(pickupId), String.valueOf(driverId)});
    }

    public boolean isDriverVehicleApproved(int driverId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT 1 FROM users u JOIN vehicles v ON v.id=u.vehicle_id " +
                        "WHERE u.id=? AND v.status='Approved' LIMIT 1",
                new String[]{String.valueOf(driverId)});
        try {
            return cursor.moveToFirst();
        } finally {
            cursor.close();
        }
    }

    /**
     * Driver taps Reject: request goes back to Pending and the system immediately tries the
     * next-nearest available driver in the same ward (excluding the one who just rejected).
     */
    public void rejectPickup(int pickupId, int driverId) {
        SQLiteDatabase db = getWritableDatabase();
        PickupRow pickup = getTripById(pickupId);
        if (pickup == null) {
            return;
        }

        ContentValues cv = new ContentValues();
        cv.put("status", "Pending");
        cv.put("driver_id", (Integer) null);
        cv.put("assigned_vehicle_id", (Integer) null);
        cv.put("driver_response_status", "Rejected");
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});

        UserAccount driver = getUserById(driverId);
        if (driver != null) {
            insertNotification(driverId, "Request rejected", "You rejected pickup " + pickup.code + ".", "assignment");
        }

        com.takago.app.network.ServerSyncManager.pushPickupStatus(appContext, pickupId, "rejected", "rejected", null);

        // Try the next-nearest driver, ignoring the one who just rejected it.
        if (pickup.latitude != 0 || pickup.longitude != 0) {
            reassignExcluding(pickupId, pickup.wardId, pickup.ward, pickup.latitude,
                    pickup.longitude, pickup.weightKg, driverId);
        }
    }

    private void reassignExcluding(int pickupId, int wardId, String ward, double latitude,
                                   double longitude, double minimumWeightKg, int excludeDriverId) {
        List<UserAccount> candidates = getAssignableDriversInWard(wardId, ward, minimumWeightKg);
        UserAccount nearest = null;
        double nearestDistance = Double.MAX_VALUE;
        for (UserAccount driver : candidates) {
            if (driver.id == excludeDriverId || (driver.latitude == 0 && driver.longitude == 0)) {
                continue;
            }
            double distance = distanceKm(latitude, longitude, driver.latitude, driver.longitude);
            if (distance < nearestDistance) {
                nearestDistance = distance;
                nearest = driver;
            }
        }

        SQLiteDatabase db = getWritableDatabase();
        if (nearest != null) {
            ContentValues cv = new ContentValues();
            cv.put("driver_id", nearest.id);
            cv.put("assigned_vehicle_id", nearest.vehicleId);
            cv.put("status", "Assigned");
            cv.put("assignment_type", "AUTO");
            cv.put("distance_km", nearestDistance);
            cv.put("timeout_at", timestampPlusMinutes(10));
            db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
            insertNotification(nearest.id, "New pickup assigned",
                    "You've been automatically assigned a nearby pickup request.", "assignment");
        } else {
            notifyWardStaff(ward, "No driver available", "No available approved driver in this ward.", "assignment");
        }
    }

    /** Manual assignment by a Ward Admin or Waste Operator, e.g. for an overdue request. */
    public void assignDriverManually(int pickupId, int driverId) {
        UserAccount driver = getUserById(driverId);
        PickupRow pickup = getTripById(pickupId);
        if (driver == null || driver.vehicleId == 0) {
            return;
        }
        if (pickup == null || pickup.wardId <= 0 || driver.wardId != pickup.wardId
                || driver.municipalityId != getMunicipalityIdForWard(pickup.wardId)
                || !vehicleHasRemainingCapacityForSingle(driver.vehicleId, pickup.weightKg)) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        int operatorId = getAssignedOperatorIdForWard(pickup.wardId);
        if (operatorId <= 0) operatorId = driver.operatorId;
        int groupId = pickup.groupId > 0 ? pickup.groupId
                : createOpenRouteGroup(driver.municipalityId, pickup.wardId, operatorId,
                driverId, driver.vehicleId);
        ContentValues cv = new ContentValues();
        cv.put("driver_id", driverId);
        cv.put("assigned_vehicle_id", driver.vehicleId);
        cv.put("status", "Assigned");
        cv.put("assignment_type", "MANUAL");
        cv.put("timeout_at", timestampPlusMinutes(10));
        cv.put("group_id", groupId);
        cv.put("batching_status", BATCH_ASSIGNED_TO_ROUTE);
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
        if (pickup.groupId <= 0) addRouteStop(groupId, pickupId,
                routeStopCount(groupId) + 1, pickup.latitude, pickup.longitude);

        insertNotification(driverId, "New pickup assigned", "You've been manually assigned a pickup request.", "assignment");
    }

    /** Driver has physically picked up the waste, but the trip isn't finished yet. */
    public void markPickupCollected(int pickupId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Collected");
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
        PickupRow pickup = getTripById(pickupId);
        if (pickup != null && pickup.groupId > 0) {
            ContentValues stopValues = new ContentValues();
            stopValues.put("status", "Collected");
            db.update("route_stops", stopValues, "group_id = ? AND pickup_id = ?",
                    new String[]{String.valueOf(pickup.groupId), String.valueOf(pickupId)});
        }
        notifyResidentForPickup(pickupId, "Pickup collected", "Your waste has been collected by the driver.", "pickup");
        // Collection is an internal driver step; shared request status remains On the way until completion.
    }

    /** Driver cancels a trip they were assigned - a reason is required so it's always recorded. */
    public void cancelPickup(int pickupId, String reason) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("status", "Cancelled");
        cv.put("cancel_reason", reason);
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});
        notifyResidentForPickup(pickupId, "Pickup cancelled", "Your pickup was cancelled: " + reason, "pickup");
        com.takago.app.network.ServerSyncManager.pushPickupStatus(appContext, pickupId, "cancelled", null, reason);
    }

    /** A request is overdue once it's been Pending, or Assigned-but-unaccepted, too long. */
    public boolean isOverdue(PickupRow pickup) {
        if (pickup.timeoutAt == null || pickup.timeoutAt.isEmpty()) {
            return false;
        }
        if (!"Pending".equals(pickup.status) && !"Assigned".equals(pickup.status)) {
            return false;
        }
        try {
            SimpleDateFormat fmt = new SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US);
            return fmt.parse(pickup.timeoutAt).before(new Date());
        } catch (Exception e) {
            return false;
        }
    }

    /** All pickups still Pending or Assigned in a ward, for Ward Admin/Waste Operator overdue lists. */
    public List<PickupRow> getActionablePickupsInWard(String ward) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        int wardId = findWardIdByName(ward);
        Cursor c = wardId > 0
                ? db.rawQuery("SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE ward_id = ? AND status IN ('Pending', 'Assigned') ORDER BY id ASC",
                new String[]{String.valueOf(wardId)})
                : db.rawQuery("SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE ward = ? AND status IN ('Pending', 'Assigned') ORDER BY id ASC",
                new String[]{ward});
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    /** All pickups still Pending or Assigned, across every ward (used for Waste Operator view). */
    public List<PickupRow> getAllActionablePickups() {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE status IN ('Pending', 'Assigned') ORDER BY id ASC",
                null);
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    private void notifyResidentForPickup(int pickupId, String title, String message, String type) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT resident_id FROM pickups WHERE id = ?", new String[]{String.valueOf(pickupId)});
        int residentId = 0;
        if (c.moveToFirst()) {
            residentId = c.getInt(0);
        }
        c.close();
        if (residentId > 0) {
            insertNotification(residentId, title, message, type);
        }
    }

    // ---------- Resident Home ----------

    /** The resident's current in-progress pickup (if any), or null if nothing is active. */
    public PickupRow getActivePickupForResident(int residentId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + RESIDENT_PICKUP_COLUMNS + " FROM pickups " +
                        "WHERE resident_id = ? AND status NOT IN ('Completed', 'Cancelled') " +
                        "ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(residentId)});
        PickupRow row = null;
        if (c.moveToFirst()) {
            row = readPickupRow(c);
        }
        c.close();
        return row;
    }

    public List<PickupRow> getRecentPickupsForResident(int residentId, int limit) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + RESIDENT_PICKUP_COLUMNS + " FROM pickups " +
                        "WHERE resident_id = ? AND status IN ('Completed', 'Cancelled') " +
                        "ORDER BY pickup_date DESC LIMIT ?",
                new String[]{String.valueOf(residentId), String.valueOf(limit)});
        while (c.moveToNext()) {
            list.add(readPickupRow(c));
        }
        c.close();
        return list;
    }

    /** Every pickup request this resident has ever made, newest first (used by History). */
    public List<PickupRow> getAllPickupsForResident(int residentId) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + RESIDENT_PICKUP_COLUMNS + " FROM pickups " +
                        "WHERE resident_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(residentId)});
        while (c.moveToNext()) {
            list.add(readPickupRow(c));
        }
        c.close();
        return list;
    }

    private PickupRow readPickupRow(Cursor c) {
        PickupRow row = new PickupRow();
        row.id = c.getInt(0);
        row.code = c.getString(1);
        row.ward = c.getString(2);
        row.category = c.getString(3);
        row.status = c.getString(4);
        row.pickupDate = c.getString(5);
        row.latitude = c.getDouble(6);
        row.longitude = c.getDouble(7);
        row.address = c.getString(8);
        row.photoPath = c.getString(9);
        row.createdAt = c.getString(10);
        row.completedAt = c.getString(11);
        row.driverId = c.getInt(12);
        row.distanceKm = c.getDouble(13);
        row.wasteType = c.getString(14);
        row.estimatedPriceMin = c.getDouble(15);
        row.estimatedPriceMax = c.getDouble(16);
        row.measuredWeightKg = c.getDouble(17);
        row.includedWeightKg = c.getDouble(18);
        row.ratePerKg = c.getDouble(19);
        row.distanceFee = c.getDouble(20);
        row.wasteTypeMultiplier = c.getDouble(21);
        row.finalPrice = c.getDouble(22);
        row.scalePhotoPath = c.getString(23);
        row.pricingStatus = c.getString(24);
        row.paymentStatus = c.getString(25);
        row.bookingFee = c.getDouble(26);
        row.placeId = c.getString(27);
        row.wardId = c.isNull(28) ? -1 : c.getInt(28);
        row.groupId = c.isNull(29) ? -1 : c.getInt(29);
        row.stopOrder = c.isNull(30) ? -1 : c.getInt(30);
        row.encodedPolyline = c.getString(31);
        row.routeDistanceMeters = c.isNull(32) ? 0 : c.getInt(32);
        row.routeDurationSeconds = c.isNull(33) ? 0 : c.getInt(33);
        row.routeCalculatedAt = c.getString(34);
        row.houseNumber = c.getString(35);
        row.streetName = c.getString(36);
        row.formattedAddress = c.getString(37);
        row.placeName = c.getString(38);
        row.plusCode = c.getString(39);
        return row;
    }

    // ---------- Driver flow ----------

    private static final String USER_ACCOUNT_COLUMNS =
            "id, name, email, phone, role, status, rating, total_distance_km, license_info, vehicle_info, " +
                    "trips_count, driver_plate, availability_status, fleet_trucks, fleet_drivers, " +
                    "fleet_earnings_week, fleet_earnings_change, ward, profile_image_path, " +
                    "operator_id, vehicle_id, latitude, longitude, last_location_update, " +
                    "ward_lat, ward_lng, ward_radius_km, ward_id, municipality_id, " +
                    "house_number, street_name, place_name, formatted_address, plus_code, " +
                    "location_ward_name, last_location_updated_at";

    public UserAccount getUserById(int userId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + USER_ACCOUNT_COLUMNS + " FROM users WHERE id = ?",
                new String[]{String.valueOf(userId)});
        UserAccount account = null;
        if (c.moveToFirst()) {
            account = readUserAccountRow(c);
        }
        c.close();
        return account;
    }

    private UserAccount readUserAccountRow(Cursor c) {
        UserAccount account = new UserAccount();
        account.id = c.getInt(0);
        account.name = c.getString(1);
        account.email = c.getString(2);
        account.phone = c.getString(3);
        account.role = c.getString(4);
        account.status = c.getString(5);
        account.rating = c.getDouble(6);
        account.totalDistanceKm = c.getDouble(7);
        account.licenseInfo = c.getString(8);
        account.vehicleInfo = c.getString(9);
        account.tripsCount = c.getInt(10);
        account.driverPlate = c.getString(11);
        account.availabilityStatus = c.getString(12);
        account.fleetTrucks = c.getInt(13);
        account.fleetDrivers = c.getInt(14);
        account.fleetEarningsWeek = c.getString(15);
        account.fleetEarningsChange = c.getString(16);
        account.ward = c.getString(17);
        account.profileImagePath = c.getString(18);
        account.operatorId = c.getInt(19);
        account.vehicleId = c.getInt(20);
        account.latitude = c.getDouble(21);
        account.longitude = c.getDouble(22);
        account.lastLocationUpdate = c.getString(23);
        account.wardLat = c.getDouble(24);
        account.wardLng = c.getDouble(25);
        account.wardRadiusKm = c.getDouble(26);
        account.wardId = c.isNull(27) ? -1 : c.getInt(27);
        account.municipalityId = c.isNull(28) ? -1 : c.getInt(28);
        account.houseNumber = c.getString(29);
        account.streetName = c.getString(30);
        account.placeName = c.getString(31);
        account.formattedAddress = c.getString(32);
        account.plusCode = c.getString(33);
        account.locationWardName = c.getString(34);
        account.lastLocationUpdatedAt = c.getString(35);
        return account;
    }

    /** Saves only the readable-location cache. Ward/municipality assignment remains unchanged. */
    public void saveReadableUserLocation(int userId, double latitude, double longitude,
                                         String houseNumber, String streetName, String placeName,
                                         String formattedAddress, String plusCode,
                                         String locationWardName) {
        ContentValues values = new ContentValues();
        values.put("latitude", latitude);
        values.put("longitude", longitude);
        values.put("house_number", houseNumber);
        values.put("street_name", streetName);
        values.put("place_name", placeName);
        values.put("formatted_address", formattedAddress);
        values.put("plus_code", plusCode);
        values.put("location_ward_name", locationWardName);
        String updatedAt = nowTimestamp();
        values.put("last_location_updated_at", updatedAt);
        values.put("last_location_update", updatedAt);
        getWritableDatabase().update("users", values, "id = ?",
                new String[]{String.valueOf(userId)});
    }

    public void savePickupReadableAddress(long pickupId, String placeName, String plusCode) {
        ContentValues values = new ContentValues();
        values.put("place_name", placeName);
        values.put("plus_code", plusCode);
        values.put("last_location_updated_at", nowTimestamp());
        getWritableDatabase().update("pickups", values, "id = ?",
                new String[]{String.valueOf(pickupId)});
    }

    public int getDriverTodayCount(int driverId) {
        SQLiteDatabase db = getReadableDatabase();
        String today = daysAgo(0);
        Cursor c = db.rawQuery("SELECT COUNT(*) FROM pickups WHERE driver_id = ? AND " +
                        "(date(created_at) = date(?) OR date(completed_at) = date(?) OR date(pickup_date) = date(?))",
                new String[]{String.valueOf(driverId), today, today, today});
        int count = c.moveToFirst() ? c.getInt(0) : 0;
        c.close();
        return count;
    }

    public double getDriverCompletedRouteDistanceKm(int driverId) {
        Cursor cursor = getReadableDatabase().rawQuery(
                "SELECT COALESCE(SUM(CASE WHEN distance_km > 0 THEN distance_km ELSE 0 END),0) " +
                        "FROM pickups WHERE driver_id=? AND LOWER(status)='completed'",
                new String[]{String.valueOf(driverId)});
        try { return cursor.moveToFirst() ? cursor.getDouble(0) : 0d; }
        finally { cursor.close(); }
    }

    /** The driver's earliest not-yet-finished trip today, or null if none. */
    public PickupRow getNextPickupForDriver(int driverId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE driver_id = ? AND LOWER(REPLACE(status,' ','_')) IN " +
                        "('assigned','accepted','on_the_way','arrived','collecting','weight_recorded','resident_confirmation','price_confirmed','payment_pending','paid') " +
                        "ORDER BY CASE LOWER(REPLACE(status,' ','_')) WHEN 'on_the_way' THEN 1 WHEN 'accepted' THEN 2 WHEN 'arrived' THEN 3 WHEN 'collecting' THEN 4 ELSE 5 END, id DESC LIMIT 1",
                new String[]{String.valueOf(driverId)});
        PickupRow row = c.moveToFirst() ? readDriverPickupRow(c) : null;
        c.close();
        return row;
    }

    public PickupRow getTripById(int tripId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE id = ?",
                new String[]{String.valueOf(tripId)});
        PickupRow row = null;
        if (c.moveToFirst()) {
            row = readDriverPickupRow(c);
        }
        c.close();
        return row;
    }

    /** filter: "All", "Today" or "This week". */
    public List<PickupRow> getDriverTrips(int driverId, String filter) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String where = "driver_id = ?";
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(driverId));

        if ("Today".equals(filter)) {
            String today = daysAgo(0);
            where += " AND (date(created_at) = date(?) OR date(completed_at) = date(?) OR date(pickup_date) = date(?))";
            args.add(today); args.add(today); args.add(today);
        } else if ("This week".equals(filter)) {
            where += " AND pickup_date >= ?";
            args.add(daysAgo(6));
        }

        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE " + where + " ORDER BY pickup_date DESC, time_text ASC",
                args.toArray(new String[0]));
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    /** status == "All" returns every trip; otherwise only trips matching that exact status. */
    /**
     * Assigned/ongoing trips only (never Completed/Cancelled - those belong on Driver History).
     * status == "All" returns every ongoing trip regardless of which ongoing status it's in.
     */
    public List<PickupRow> getDriverTripsByStatus(int driverId, String status) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();

        String where = "driver_id = ?";
        List<String> args = new ArrayList<>();
        args.add(String.valueOf(driverId));

        if ("All".equals(status)) {
            where += " AND LOWER(REPLACE(status,' ','_')) IN ('assigned','accepted','on_the_way','arrived','collecting','weight_recorded','resident_confirmation','price_confirmed','payment_pending','paid','completed','cancelled')";
        } else if ("Assigned".equalsIgnoreCase(status)) {
            where += " AND LOWER(REPLACE(status,' ','_')) IN ('assigned','accepted')";
        } else if ("On the way".equalsIgnoreCase(status)) {
            where += " AND LOWER(REPLACE(status,' ','_')) IN ('on_the_way','arrived','collecting','weight_recorded','resident_confirmation','price_confirmed','payment_pending','paid')";
        } else {
            where += " AND LOWER(REPLACE(status,' ','_')) = ?";
            args.add(status.toLowerCase(Locale.US).replace(' ', '_'));
        }

        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE " + where + " ORDER BY pickup_date DESC, time_text ASC",
                args.toArray(new String[0]));
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    /** A driver's finished trips (Completed or Cancelled), newest first - used by Driver History. */
    public List<PickupRow> getDriverHistory(int driverId) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE driver_id = ? AND status IN ('Completed', 'Cancelled') " +
                        "ORDER BY pickup_date DESC, time_text ASC",
                new String[]{String.valueOf(driverId)});
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    /**
     * Updates a driver's editable profile fields - phone, email, optional password, and photo
     * (photo is saved separately via updateProfileImage). Ward is deliberately not editable here;
     * only a Waste Operator/Admin can reassign a driver's ward.
     */
    public void updateDriverProfile(int driverId, String phone, String email, String newPassword) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("phone", phone);
        cv.put("email", email);
        if (newPassword != null && !newPassword.isEmpty()) {
            cv.put("password", hashPassword(newPassword));
        }
        db.update("users", cv, "id = ?", new String[]{String.valueOf(driverId)});
    }

    public void markTripCollected(int tripId) {
        SQLiteDatabase db = getWritableDatabase();
        PickupRow pickup = getTripById(tripId);
        ContentValues cv = new ContentValues();
        cv.put("status", "Completed");
        cv.put("completed_at", nowTimestamp());
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(tripId)});
        if (pickup != null && pickup.groupId > 0) completeGroupIfDone(pickup.groupId);
        notifyResidentForPickup(tripId, "Pickup completed", "Your waste pickup has been collected. Thanks for recycling!", "pickup");
        com.takago.app.network.ServerSyncManager.pushPickupStatus(appContext, tripId, "completed", null, null);
    }

    private static final String DRIVER_PICKUP_COLUMNS =
            "id, code, ward, status, pickup_date, weight_kg, time_text, distance_km, eta_min, resident_display_name, " +
                    "latitude, longitude, driver_id, timeout_at, assigned_vehicle_id, driver_response_status, accepted_at, " +
                    "category, address, created_at, resident_id, completed_at, waste_type, estimated_price_min, " +
                    "estimated_price_max, measured_weight_kg, included_weight_kg, rate_per_kg, distance_fee, " +
                    "waste_type_multiplier, final_price, scale_photo_path, pricing_status, payment_status, booking_fee, " +
                    "place_id, ward_id, group_id, stop_order, encoded_polyline, route_distance_meters, route_duration_seconds, route_calculated_at, " +
                    "house_number, street_name, formatted_address, place_name, plus_code";

    private PickupRow readDriverPickupRow(Cursor c) {
        PickupRow row = new PickupRow();
        row.id = c.getInt(0);
        row.code = c.getString(1);
        row.ward = c.getString(2);
        row.status = c.getString(3);
        row.pickupDate = c.getString(4);
        row.weightKg = c.getDouble(5);
        row.timeText = c.getString(6);
        row.distanceKm = c.getDouble(7);
        row.etaMin = c.getInt(8);
        row.residentDisplayName = c.getString(9);
        row.latitude = c.getDouble(10);
        row.longitude = c.getDouble(11);
        row.driverId = c.getInt(12);
        row.timeoutAt = c.getString(13);
        row.assignedVehicleId = c.getInt(14);
        row.driverResponseStatus = c.getString(15);
        row.acceptedAt = c.getString(16);
        row.category = c.getString(17);
        row.residentId = c.getInt(20);
        row.address = c.getString(18);
        row.createdAt = c.getString(19);
        row.completedAt = c.getString(21);
        row.wasteType = c.getString(22);
        row.estimatedPriceMin = c.getDouble(23);
        row.estimatedPriceMax = c.getDouble(24);
        row.measuredWeightKg = c.getDouble(25);
        row.includedWeightKg = c.getDouble(26);
        row.ratePerKg = c.getDouble(27);
        row.distanceFee = c.getDouble(28);
        row.wasteTypeMultiplier = c.getDouble(29);
        row.finalPrice = c.getDouble(30);
        row.scalePhotoPath = c.getString(31);
        row.pricingStatus = c.getString(32);
        row.paymentStatus = c.getString(33);
        row.bookingFee = c.getDouble(34);
        row.placeId = c.getString(35);
        row.wardId = c.isNull(36) ? -1 : c.getInt(36);
        row.groupId = c.isNull(37) ? -1 : c.getInt(37);
        row.stopOrder = c.isNull(38) ? -1 : c.getInt(38);
        row.encodedPolyline = c.getString(39);
        row.routeDistanceMeters = c.isNull(40) ? 0 : c.getInt(40);
        row.routeDurationSeconds = c.isNull(41) ? 0 : c.getInt(41);
        row.routeCalculatedAt = c.getString(42);
        row.houseNumber = c.getString(43);
        row.streetName = c.getString(44);
        row.formattedAddress = c.getString(45);
        row.placeName = c.getString(46);
        row.plusCode = c.getString(47);
        row.proofPhotoPath = c.getString(48);
        return row;
    }

    // ---------- Waste Operator flow ----------

    public List<UserAccount> getAllDrivers() {
        List<UserAccount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + USER_ACCOUNT_COLUMNS + " FROM users WHERE role = ? ORDER BY id ASC",
                new String[]{ROLE_DRIVER});
        while (c.moveToNext()) {
            list.add(readUserAccountRow(c));
        }
        c.close();
        return list;
    }

    /** Only the drivers registered under one Waste Operator - used for "own drivers only" rules. */
    public List<UserAccount> getDriversForOperator(int operatorId) {
        List<UserAccount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + USER_ACCOUNT_COLUMNS + " FROM users WHERE role = ? AND operator_id = ? ORDER BY id ASC",
                new String[]{ROLE_DRIVER, String.valueOf(operatorId)});
        while (c.moveToNext()) {
            list.add(readUserAccountRow(c));
        }
        c.close();
        return list;
    }

    /** Completed pickups collected by any of this Waste Operator's own drivers, newest first. */
    public List<PickupRow> getCompletedPickupsForOperator(int operatorId) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " FROM pickups " +
                        "WHERE status = 'Completed' AND driver_id IN " +
                        "(SELECT id FROM users WHERE role = ? AND operator_id = ?) " +
                        "ORDER BY completed_at DESC",
                new String[]{ROLE_DRIVER, String.valueOf(operatorId)});
        while (c.moveToNext()) {
            list.add(readDriverPickupRow(c));
        }
        c.close();
        return list;
    }

    /**
     * Drivers available to collect requests in a given ward. A pickup request must only be
     * offered to drivers whose own ward matches the request's ward — drivers outside that
     * ward should never see or be assigned it.
     */
    public List<UserAccount> getDriversInWard(String ward) {
        List<UserAccount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        int wardId = findWardIdByName(ward);
        Cursor c = wardId > 0
                ? db.rawQuery("SELECT " + USER_ACCOUNT_COLUMNS + " FROM users WHERE role = ? AND ward_id = ? ORDER BY id ASC",
                new String[]{ROLE_DRIVER, String.valueOf(wardId)})
                : db.rawQuery("SELECT " + USER_ACCOUNT_COLUMNS + " FROM users WHERE role = ? AND ward = ? ORDER BY id ASC",
                new String[]{ROLE_DRIVER, ward});
        while (c.moveToNext()) {
            list.add(readUserAccountRow(c));
        }
        c.close();
        return list;
    }

    /** The most recently assigned pickup across the whole fleet (used on Waste Operator Home). */
    public PickupRow getLatestAssignedPickup() {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT " + DRIVER_PICKUP_COLUMNS + " " +
                        "FROM pickups WHERE status = 'Assigned' ORDER BY id DESC LIMIT 1",
                null);
        PickupRow row = null;
        if (c.moveToFirst()) {
            row = readDriverPickupRow(c);
        }
        c.close();
        return row;
    }

    // ---------- Resident profile stats ----------

    public int getTotalPickupsForResident(int residentId) {
        return countWhere("pickups", "resident_id = ?", String.valueOf(residentId));
    }

    public double getRecycledKgForResident(int residentId) {
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT COALESCE(SUM(COALESCE(measured_weight_kg, weight_kg, 0)), 0) FROM pickups " +
                        "WHERE resident_id = ? AND measured_weight_kg > 0 " +
                        "AND LOWER(REPLACE(status,' ','_')) NOT IN ('cancelled','rejected')",
                new String[]{String.valueOf(residentId)});
        double total = 0;
        if (c.moveToFirst()) {
            total = c.getDouble(0);
        }
        c.close();
        return total;
    }

    public String getActivePickupSummaryForResident(int residentId) {
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT code, status FROM pickups WHERE resident_id = ? AND LOWER(status) IN " +
                        "('pending','assigned','accepted','on_the_way','arrived','collecting','weight_recorded'," +
                        "'resident_confirmation','price_confirmed','payment_pending','paid') ORDER BY id DESC LIMIT 1",
                new String[]{String.valueOf(residentId)});
        String result = null;
        if (c.moveToFirst()) {
            String code = c.isNull(0) ? "Current pickup" : c.getString(0);
            String status = c.isNull(1) ? "Pending" : c.getString(1).replace('_', ' ');
            result = code + " is " + status;
        }
        c.close();
        return result;
    }

    public void updateProfileImage(int userId, String profileImagePath) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("profile_image_path", profileImagePath);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    public void updateSharedProfile(int userId, String name, String email,
                                    String phone, String profileImageUrl) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("profile_image_path", profileImageUrl);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(userId)});
    }

    public void upsertApiProfile(int userId, String name, String email, String phone,
                                 String profileImageUrl, String apiRole, String wardName,
                                 int operatorId) {
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("id", userId);
        cv.put("name", name);
        cv.put("email", email);
        cv.put("phone", phone);
        cv.put("profile_image_path", profileImageUrl);
        cv.put("role", localRole(apiRole));
        cv.put("status", "Active");
        int localWardId = findWardIdByName(wardName);
        if (localWardId > 0) {
            cv.put("ward_id", localWardId);
            cv.put("ward", wardName);
            cv.put("location_ward_name", wardName);
            cv.put("municipality_id", getMunicipalityIdForWard(localWardId));
        }
        if (operatorId > 0) cv.put("operator_id", operatorId);
        db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
    }

    /** Called while a driver's app is open so residents can see their live position. */
    public void updateDriverLocation(int driverId, double lat, double lng) {
        updateDriverLocation(driverId, lat, lng, 0f, 0f, 0f);
    }

    public void updateDriverLocation(int driverId, double lat, double lng,
                                     float bearing, float speed, float accuracy) {
        SQLiteDatabase db = getWritableDatabase();
        double previousLat = Double.NaN, previousLng = Double.NaN, totalKm = 0;
        Cursor previous = db.rawQuery("SELECT latitude, longitude, total_distance_km FROM users WHERE id=?",
                new String[]{String.valueOf(driverId)});
        if (previous.moveToFirst()) {
            if (!previous.isNull(0) && !previous.isNull(1)) { previousLat = previous.getDouble(0); previousLng = previous.getDouble(1); }
            totalKm = previous.getDouble(2);
        }
        previous.close();
        if (Double.isFinite(previousLat) && Double.isFinite(previousLng)) {
            double segment = distanceKm(previousLat, previousLng, lat, lng);
            if (segment >= 0.003 && segment <= 1.0) totalKm += segment;
        }
        ContentValues cv = new ContentValues();
        cv.put("latitude", lat);
        cv.put("longitude", lng);
        cv.put("last_location_update", nowTimestamp());
        cv.put("bearing", bearing);
        cv.put("speed", speed);
        cv.put("accuracy", accuracy);
        cv.put("total_distance_km", totalKm);
        db.update("users", cv, "id = ?", new String[]{String.valueOf(driverId)});
    }

    // ---------- Notifications ----------

    public void insertNotification(int userId, String title, String message) {
        notificationRepository.insertNotification(userId, title, message);
    }

    public void insertNotification(int userId, String title, String message, String type) {
        notificationRepository.insertNotification(userId, title, message, type);
    }

    private void insertNotification(SQLiteDatabase db, long userId, String title, String message) {
        notificationRepository.insertNotification(db, userId, title, message);
    }

    private void insertNotification(SQLiteDatabase db, long userId, String title, String message, String type) {
        notificationRepository.insertNotification(db, userId, title, message, type);
    }

    public List<NotificationRow> getNotificationsForUser(int userId) {
        return notificationRepository.getNotificationsForUser(userId);
    }

    public void clearNotificationsForUser(int userId) {
        getWritableDatabase().delete("notifications", "user_id = ?", new String[]{String.valueOf(userId)});
    }

    public int getUnreadNotificationCount(int userId) {
        return notificationRepository.getUnreadNotificationCount(userId);
    }

    public void markAllNotificationsRead(int userId) {
        notificationRepository.markAllNotificationsRead(userId);
    }

    // ---------- List screens ----------

    public List<PickupRow> getAllPickups() {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, code, ward, category, status, address, pickup_date, weight_kg, " +
                        "resident_display_name, driver_id, waste_type, final_price, pricing_status " +
                        "FROM pickups ORDER BY id DESC", null);
        while (c.moveToNext()) {
            PickupRow row = new PickupRow();
            row.id = c.getInt(0);
            row.code = c.getString(1);
            row.ward = c.getString(2);
            row.category = c.getString(3);
            row.status = c.getString(4);
            row.address = c.getString(5);
            row.pickupDate = c.getString(6);
            row.weightKg = c.getDouble(7);
            row.residentDisplayName = c.getString(8);
            row.driverId = c.getInt(9);
            row.wasteType = c.getString(10);
            row.finalPrice = c.getDouble(11);
            row.pricingStatus = c.getString(12);
            list.add(row);
        }
        c.close();
        return list;
    }

    public List<PickupRow> getPickupsInWard(String ward) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        int wardId = findWardIdByName(ward);
        Cursor c = wardId > 0
                ? db.rawQuery("SELECT id, code, ward, category, status, address, pickup_date, weight_kg, " +
                        "resident_display_name, driver_id, waste_type, final_price, pricing_status " +
                        "FROM pickups WHERE ward_id = ? ORDER BY id DESC", new String[]{String.valueOf(wardId)})
                : db.rawQuery("SELECT id, code, ward, category, status, address, pickup_date, weight_kg, " +
                        "resident_display_name, driver_id, waste_type, final_price, pricing_status " +
                        "FROM pickups WHERE ward = ? ORDER BY id DESC", new String[]{ward});
        while (c.moveToNext()) {
            PickupRow row = new PickupRow();
            row.id = c.getInt(0);
            row.code = c.getString(1);
            row.ward = c.getString(2);
            row.category = c.getString(3);
            row.status = c.getString(4);
            row.address = c.getString(5);
            row.pickupDate = c.getString(6);
            row.weightKg = c.getDouble(7);
            row.residentDisplayName = c.getString(8);
            row.driverId = c.getInt(9);
            row.wasteType = c.getString(10);
            row.finalPrice = c.getDouble(11);
            row.pricingStatus = c.getString(12);
            list.add(row);
        }
        c.close();
        return list;
    }

    public List<PickupRow> getPickupsInMunicipality(int municipalityId) {
        List<PickupRow> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery(
                "SELECT id, code, ward, category, status, address, pickup_date, weight_kg, " +
                        "resident_display_name, driver_id, waste_type, final_price, pricing_status " +
                        "FROM pickups WHERE municipality_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(municipalityId)});
        while (c.moveToNext()) {
            PickupRow row = new PickupRow();
            row.id = c.getInt(0);
            row.code = c.getString(1);
            row.ward = c.getString(2);
            row.category = c.getString(3);
            row.status = c.getString(4);
            row.address = c.getString(5);
            row.pickupDate = c.getString(6);
            row.weightKg = c.getDouble(7);
            row.residentDisplayName = c.getString(8);
            row.driverId = c.getInt(9);
            row.wasteType = c.getString(10);
            row.finalPrice = c.getDouble(11);
            row.pricingStatus = c.getString(12);
            list.add(row);
        }
        c.close();
        return list;
    }

    /** Municipal Admin manually sets the final price for a pickup that needed special approval (e.g. hazardous waste). */
    public void setManualFinalPrice(int pickupId, double finalPrice) {
        PickupRow pickup = getTripById(pickupId);
        if (pickup == null || finalPrice < 0) {
            return;
        }
        SQLiteDatabase db = getWritableDatabase();
        ContentValues cv = new ContentValues();
        cv.put("final_price", finalPrice);
        cv.put("pricing_status", "Finalized");
        db.update("pickups", cv, "id = ?", new String[]{String.valueOf(pickupId)});

        insertNotification(pickup.residentId, "Final price ready",
                "Your final pickup price is " + formatTzs(finalPrice) + ".", "pickup");
        insertNotification(pickup.residentId, "Receipt generated",
                "Your receipt for pickup " + pickup.code + " is ready to view.", "pickup");
    }

    public List<VehicleRow> getAllVehicles() {
        return vehicleRepository.getAllVehicles();
    }

    public List<VehicleRow> getVehiclesInMunicipality(int municipalityId) {
        List<VehicleRow> vehicles = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, plate, model, status, operator_id, ward, rejection_reason " +
                        "FROM vehicles WHERE municipality_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(municipalityId)});
        try {
            while (c.moveToNext()) {
                VehicleRow row = new VehicleRow();
                row.id = c.getInt(0);
                row.plate = c.getString(1);
                row.model = c.getString(2);
                row.status = c.getString(3);
                row.operatorId = c.getInt(4);
                row.ward = c.getString(5);
                row.rejectionReason = c.getString(6);
                vehicles.add(row);
            }
        } finally {
            c.close();
        }
        return vehicles;
    }

    /** Only the vehicles belonging to one Waste Operator's own fleet. */
    public List<VehicleRow> getVehiclesForOperator(int operatorId) {
        return vehicleRepository.getVehiclesForOperator(operatorId);
    }

    public VehicleRow getVehicleById(int vehicleId) {
        if (vehicleId <= 0) return null;
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, plate, model, status, operator_id, ward, rejection_reason " +
                        "FROM vehicles WHERE id=? LIMIT 1", new String[]{String.valueOf(vehicleId)});
        try {
            if (!c.moveToFirst()) return null;
            VehicleRow row = new VehicleRow();
            row.id = c.getInt(0); row.plate = c.getString(1); row.model = c.getString(2);
            row.status = c.getString(3); row.operatorId = c.getInt(4);
            row.ward = c.getString(5); row.rejectionReason = c.getString(6);
            return row;
        } finally { c.close(); }
    }

    /** current stop, total stops, resident stop, remaining stops before resident. */
    public int[] getRouteStopSummary(int groupId, int pickupId) {
        if (groupId <= 0) return new int[]{0, 0, 0, 0};
        SQLiteDatabase db = getReadableDatabase();
        int current = 0, total = 0, residentStop = 0;
        Cursor c = db.rawQuery("SELECT stop_order, status, pickup_id FROM route_stops " +
                "WHERE group_id=? ORDER BY stop_order", new String[]{String.valueOf(groupId)});
        try {
            while (c.moveToNext()) {
                total++;
                int order = c.getInt(0);
                String status = c.getString(1);
                if (c.getInt(2) == pickupId) residentStop = order;
                if (current == 0 && !"Completed".equalsIgnoreCase(status)) current = order;
            }
        } finally { c.close(); }
        int before = residentStop > 0 && current > 0 ? Math.max(0, residentStop - current) : 0;
        return new int[]{current, total, residentStop, before};
    }

    public List<RouteStopRow> getRouteStops(int groupId) {
        List<RouteStopRow> stops = new ArrayList<>();
        if (groupId <= 0) return stops;
        Cursor c = getReadableDatabase().rawQuery("SELECT pickup_id, stop_order, status, " +
                "latitude, longitude FROM route_stops WHERE group_id=? ORDER BY stop_order",
                new String[]{String.valueOf(groupId)});
        try { while (c.moveToNext()) { RouteStopRow row = new RouteStopRow();
            row.pickupId = c.getInt(0); row.stopOrder = c.getInt(1); row.status = c.getString(2);
            row.latitude = c.getDouble(3); row.longitude = c.getDouble(4); stops.add(row); }
        } finally { c.close(); }
        return stops;
    }

    public List<UserAccount> getWasteOperatorsForWardManagement() {
        return getWasteOperatorsForWardManagement(-1);
    }

    public List<UserAccount> getWasteOperatorsForWardManagement(int municipalityId) {
        List<UserAccount> operators = new ArrayList<>();
        Cursor c = municipalityId > 0
                ? getReadableDatabase().rawQuery(
                "SELECT id, name FROM users WHERE role=? AND municipality_id=? ORDER BY name COLLATE NOCASE",
                new String[]{ROLE_TRUCK_OWNER, String.valueOf(municipalityId)})
                : getReadableDatabase().rawQuery(
                "SELECT id, name FROM users WHERE role=? ORDER BY name COLLATE NOCASE",
                new String[]{ROLE_TRUCK_OWNER});
        try {
            while (c.moveToNext()) {
                UserAccount user = new UserAccount();
                user.id = c.getInt(0); user.name = c.getString(1); operators.add(user);
            }
        } finally { c.close(); }
        return operators;
    }

    public List<UserAccount> getWardAdminsForWardManagement(int municipalityId) {
        List<UserAccount> admins = new ArrayList<>();
        Cursor c = municipalityId > 0
                ? getReadableDatabase().rawQuery(
                "SELECT id, name, ward_id FROM users WHERE role=? AND (municipality_id=? OR municipality_id IS NULL) ORDER BY name COLLATE NOCASE",
                new String[]{ROLE_WARD_ADMIN, String.valueOf(municipalityId)})
                : getReadableDatabase().rawQuery(
                "SELECT id, name, ward_id FROM users WHERE role=? ORDER BY name COLLATE NOCASE",
                new String[]{ROLE_WARD_ADMIN});
        try {
            while (c.moveToNext()) {
                UserAccount user = new UserAccount();
                user.id = c.getInt(0);
                user.name = c.getString(1);
                user.wardId = c.isNull(2) ? -1 : c.getInt(2);
                admins.add(user);
            }
        } finally { c.close(); }
        return admins;
    }

    public List<ComplaintRow> getAllComplaints() {
        return complaintRepository.getAllComplaints();
    }

    public List<ComplaintRow> getComplaintsInWard(String ward) {
        return complaintRepository.getComplaintsInWard(ward);
    }

    public List<ComplaintRow> getComplaintsInMunicipality(int municipalityId) {
        List<ComplaintRow> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, subject, reporter, date_text, status, ward FROM complaints WHERE municipality_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(municipalityId)});
        try {
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
        } finally {
            c.close();
        }
        return list;
    }

    public List<UserAccount> getAllUsers() {
        List<UserAccount> list = new ArrayList<>();
        SQLiteDatabase db = getReadableDatabase();
        Cursor c = db.rawQuery("SELECT id, name, email, phone, role, status FROM users ORDER BY id DESC", null);
        while (c.moveToNext()) {
            UserAccount u = new UserAccount();
            u.id = c.getInt(0);
            u.name = c.getString(1);
            u.email = c.getString(2);
            u.phone = c.getString(3);
            u.role = c.getString(4);
            u.status = c.getString(5);
            list.add(u);
        }
        c.close();
        return list;
    }

    public List<UserAccount> getUsersInMunicipality(int municipalityId) {
        List<UserAccount> list = new ArrayList<>();
        Cursor c = getReadableDatabase().rawQuery(
                "SELECT id, name, email, phone, role, status, ward, ward_id, municipality_id " +
                        "FROM users WHERE municipality_id = ? ORDER BY id DESC",
                new String[]{String.valueOf(municipalityId)});
        try {
            while (c.moveToNext()) {
                UserAccount u = new UserAccount();
                u.id = c.getInt(0);
                u.name = c.getString(1);
                u.email = c.getString(2);
                u.phone = c.getString(3);
                u.role = c.getString(4);
                u.status = c.getString(5);
                u.ward = c.getString(6);
                u.wardId = c.isNull(7) ? -1 : c.getInt(7);
                u.municipalityId = c.isNull(8) ? -1 : c.getInt(8);
                list.add(u);
            }
        } finally {
            c.close();
        }
        return list;
    }


    private static void preserveLocalRoute(SQLiteDatabase db, int pickupId, ContentValues values) {
        Cursor cursor = db.rawQuery("SELECT encoded_polyline,route_distance_meters,route_duration_seconds,route_calculated_at FROM pickups WHERE id=?",
                new String[]{String.valueOf(pickupId)});
        try {
            if (!cursor.moveToFirst() || cursor.isNull(1) || cursor.getInt(1) <= 0) return;
            values.put("encoded_polyline", cursor.getString(0));
            values.put("route_distance_meters", cursor.getInt(1));
            values.put("route_duration_seconds", cursor.getInt(2));
            values.put("route_calculated_at", cursor.getString(3));
            values.put("distance_km", cursor.getInt(1) / 1000d);
            values.put("eta_min", Math.max(1, (int) Math.ceil(cursor.getInt(2) / 60d)));
        } finally { cursor.close(); }
    }

    /** Replaces the local cache with authoritative rows returned by Laravel/MySQL. */
    public synchronized void syncServerResource(String resource, JSONArray rows) {
        SQLiteDatabase db = getWritableDatabase();
        db.beginTransaction();
        try {
            // GeoJSON is authoritative for geometry; restore any polygons removed by older builds.
            if ("wards".equals(resource)) importWardBoundariesFromAssets(appContext, db);
            for (int i = 0; i < rows.length(); i++) {
                JSONObject row = rows.optJSONObject(i); if (row == null) continue;
                ContentValues cv = new ContentValues(); int id = row.optInt("id", 0); if (id <= 0) continue; cv.put("id", id);
                if ("users".equals(resource)) {
                    cv.put("name", row.optString("name", "")); cv.put("email", row.optString("email", "")); cv.put("phone", row.optString("phone", ""));
                    cv.put("role", localRole(row.optString("role", "resident"))); cv.put("status", title(row.optString("status", "active"))); cv.put("availability_status", title(row.optString("availability_status", "active")));
                    String userWardName = row.optString("ward_name", "");
                    int userLocalWardId = findWardIdByName(userWardName);
                    if (userLocalWardId > 0) {
                        cv.put("ward_id", userLocalWardId);
                        cv.put("ward", userWardName);
                        cv.put("location_ward_name", userWardName);
                        cv.put("municipality_id", getMunicipalityIdForWard(userLocalWardId));
                    }
                    putInt(cv,"operator_id",row); putInt(cv,"vehicle_id",row); putDouble(cv,"latitude",row); putDouble(cv,"longitude",row); putDouble(cv,"total_distance_km",row); String serverPhoto=row.optString("profile_image_url","");String storedPhoto=row.optString("profile_image_path","");cv.put("profile_image_path",!serverPhoto.isEmpty()?serverPhoto:!storedPhoto.isEmpty()?com.takago.app.network.ApiClient.profileImageUrl(id,storedPhoto):"");
                    db.insertWithOnConflict("users", null, cv, SQLiteDatabase.CONFLICT_REPLACE);
                } else if ("wards".equals(resource)) {
                    String name = row.optString("name", "").trim();
                    int localWardId = findWardIdByName(name);
                    if (localWardId > 0) {
                        ContentValues wardValues = new ContentValues();
                        wardValues.put("is_active", "active".equalsIgnoreCase(
                                row.optString("status", "active")) ? 1 : 0);
                        if (row.has("operator_id") && !row.isNull("operator_id")) {
                            wardValues.put("assigned_operator_id", row.optInt("operator_id"));
                        }
                        wardValues.put("updated_at", nowTimestamp());
                        db.update("wards", wardValues, "id = ?",
                                new String[]{String.valueOf(localWardId)});
                    }
                } else if ("vehicles".equals(resource)) {
                    cv.put("plate",row.optString("registration_number",""));cv.put("model",row.optString("type",""));cv.put("capacity",row.optString("capacity_kg",""));cv.put("status",title(row.optString("status","pending")));putInt(cv,"operator_id",row);putInt(cv,"ward_id",row);putInt(cv,"municipality_id",row);cv.put("rejection_reason",row.optString("rejection_reason",""));db.insertWithOnConflict("vehicles",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                } else if ("pickups".equals(resource)) {
                    cv.put("code",row.optString("code",""));cv.put("category",row.optString("waste_type","Household"));cv.put("waste_type",row.optString("waste_type","Household"));cv.put("status",title(row.optString("status","pending")).replace("_"," "));cv.put("address",row.optString("address",""));cv.put("created_at",row.optString("created_at",""));cv.put("completed_at",row.optString("completed_at",""));String serverPickupDate=row.optString("scheduled_at",row.optString("created_at",""));cv.put("pickup_date",serverPickupDate.length()>=10?serverPickupDate.substring(0,10):serverPickupDate);putInt(cv,"resident_id",row);putInt(cv,"driver_id",row);putIntAs(cv,"assigned_vehicle_id","vehicle_id",row);String pickupWardName=row.optString("ward_name","");int pickupLocalWardId=findWardIdByName(pickupWardName);if(pickupLocalWardId>0){cv.put("ward_id",pickupLocalWardId);cv.put("ward",pickupWardName);}putDouble(cv,"latitude",row);putDouble(cv,"longitude",row);putDouble(cv,"distance_km",row);putInt(cv,"eta_min",row);putDoubleAs(cv,"measured_weight_kg","weight_kg",row);putDouble(cv,"included_weight_kg",row);putDouble(cv,"rate_per_kg",row);putDouble(cv,"booking_fee",row);putDouble(cv,"distance_fee",row);putDouble(cv,"waste_type_multiplier",row);putDouble(cv,"final_price",row);cv.put("pricing_status",title(row.optString("pricing_status","estimated")));cv.put("payment_status",title(row.optString("payment_status","unpaid")));cv.put("driver_response_status",title(row.optString("driver_response_status","")));cv.put("timeout_at",row.optString("manual_assignment_available_at",""));cv.put("cancel_reason",row.optString("cancel_reason",""));String requestPhoto=row.optString("photo_path","");String proofPhoto=row.optString("proof_photo_path","");cv.put("photo_path",requestPhoto.isEmpty()?"":com.takago.app.network.ApiClient.pickupImageUrl(id,"request",requestPhoto));cv.put("proof_photo_path",proofPhoto.isEmpty()?"":com.takago.app.network.ApiClient.pickupImageUrl(id,"proof",proofPhoto));if(pickupLocalWardId<=0)cv.put("ward",pickupWardName);cv.put("resident_display_name",lookupName(db,"users",row.optInt("resident_id")));preserveLocalRoute(db,id,cv);db.insertWithOnConflict("pickups",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                } else if ("notifications".equals(resource)) {
                    putInt(cv,"user_id",row);cv.put("title",row.optString("title",""));cv.put("message",row.optString("message",""));cv.put("type",row.optString("type","info"));cv.put("created_at",row.optString("created_at",""));cv.put("is_read",row.isNull("read_at")?0:1);db.insertWithOnConflict("notifications",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                } else if ("pricing_settings".equals(resource)) {
                    cv.put("id",1);String[] keys={"booking_fee","included_weight_kg","rate_per_kg","distance_free_km","distance_fee_per_km","mult_household","mult_garden","mult_recyclables","mult_construction","mult_electronic"};for(String key:keys)if(row.has(key)&&!row.isNull(key))cv.put(key,row.optDouble(key));db.insertWithOnConflict("pricing_settings",null,cv,SQLiteDatabase.CONFLICT_REPLACE);
                }
            }
            db.setTransactionSuccessful();
        } finally { db.endTransaction(); }
    }
    private static String localRole(String role){if("driver".equals(role))return ROLE_DRIVER;if("truck_owner".equals(role)||"operator".equals(role))return ROLE_TRUCK_OWNER;if("ward_admin".equals(role))return ROLE_WARD_ADMIN;if("municipal_admin".equals(role))return ROLE_MUNICIPAL_ADMIN;return ROLE_RESIDENT;}
    private static String title(String text){if(text==null||text.isEmpty())return "";String value=text.replace('_',' ');return value.substring(0,1).toUpperCase(Locale.US)+value.substring(1).toLowerCase(Locale.US);}
    private static void putInt(ContentValues cv,String key,JSONObject row){if(row.has(key)&&!row.isNull(key))cv.put(key,row.optInt(key));}private static void putIntAs(ContentValues cv,String target,String source,JSONObject row){if(row.has(source)&&!row.isNull(source))cv.put(target,row.optInt(source));}private static void putDouble(ContentValues cv,String key,JSONObject row){if(row.has(key)&&!row.isNull(key))cv.put(key,row.optDouble(key));}private static void putDoubleAs(ContentValues cv,String target,String source,JSONObject row){if(row.has(source)&&!row.isNull(source))cv.put(target,row.optDouble(source));}
    private static String lookupName(SQLiteDatabase db,String table,int id){if(id<=0)return "";Cursor c=db.rawQuery("SELECT name FROM "+table+" WHERE id=?",new String[]{String.valueOf(id)});try{return c.moveToFirst()?c.getString(0):"";}finally{c.close();}}}
