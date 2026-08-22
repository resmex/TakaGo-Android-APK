package com.takago.app.data.local;

public final class TakaGoDatabaseContract {

    public static final String DB_NAME = "takago.db";
    public static final int DB_VERSION = 21;

    private TakaGoDatabaseContract() {
    }

    public static final class Roles {
        public static final String RESIDENT = "Resident";
        public static final String DRIVER = "Driver";
        public static final String TRUCK_OWNER = "Waste Operator";
        public static final String WARD_ADMIN = "Ward Admin";
        public static final String MUNICIPAL_ADMIN = "Municipal Admin";

        private Roles() {
        }
    }

    public static final class Tables {
        public static final String USERS = "users";
        public static final String VEHICLES = "vehicles";
        public static final String COMPLAINTS = "complaints";
        public static final String PICKUPS = "pickups";
        public static final String NOTIFICATIONS = "notifications";
        public static final String PRICING_SETTINGS = "pricing_settings";

        private Tables() {
        }
    }

    public static final class PickupStatus {
        public static final String PENDING = "Pending";
        public static final String ASSIGNED = "Assigned";
        public static final String ON_THE_WAY = "On the way";
        public static final String COLLECTED = "Collected";
        public static final String COMPLETED = "Completed";
        public static final String CANCELLED = "Cancelled";
        public static final String EXPIRED = "Expired";

        private PickupStatus() {
        }
    }

    public static final class VehicleStatus {
        public static final String PENDING = "Pending";
        public static final String APPROVED = "Approved";
        public static final String REJECTED = "Rejected";

        private VehicleStatus() {
        }
    }
}
