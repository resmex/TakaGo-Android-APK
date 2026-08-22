# takaGo Test Users Seed Report

Password for every account: `Password123`

## Expected Counts

- Municipal Admins: 5
- Waste Operators: 5
- Ward Admins: 5
- Drivers: 10
- Vehicles: 10
- Residents: 30
- Total Accounts: 55

## Wards Used

- Ubungo MC / Goba
- Kinondoni MC / Magomeni
- Ilala MC / Tabata
- Temeke MC / Temeke
- Kigamboni MC / Kigamboni

All other wards remain without seeded test users.

## Emails Seeded

Municipal Admins:
- `ubungo@takago.com`
- `kinondoni@takago.com`
- `ilala@takago.com`
- `temeke@takago.com`
- `kigamboni@takago.com`

Waste Operators:
- `goba.operator@takago.com`
- `magomeni.operator@takago.com`
- `tabata.operator@takago.com`
- `temeke.operator@takago.com`
- `kigamboni.operator@takago.com`

Ward Admins:
- `goba.admin@takago.com`
- `magomeni.admin@takago.com`
- `tabata.admin@takago.com`
- `temeke.admin@takago.com`
- `kigamboni.admin@takago.com`

Drivers:
- `goba.driver1@takago.com`
- `goba.driver2@takago.com`
- `magomeni.driver1@takago.com`
- `magomeni.driver2@takago.com`
- `tabata.driver1@takago.com`
- `tabata.driver2@takago.com`
- `temeke.driver1@takago.com`
- `temeke.driver2@takago.com`
- `kigamboni.driver1@takago.com`
- `kigamboni.driver2@takago.com`

Residents:
- `goba.resident1@takago.com`
- `goba.resident2@takago.com`
- `goba.resident3@takago.com`
- `goba.resident4@takago.com`
- `goba.resident5@takago.com`
- `goba.resident6@takago.com`
- `magomeni.resident1@takago.com`
- `magomeni.resident2@takago.com`
- `magomeni.resident3@takago.com`
- `magomeni.resident4@takago.com`
- `magomeni.resident5@takago.com`
- `magomeni.resident6@takago.com`
- `tabata.resident1@takago.com`
- `tabata.resident2@takago.com`
- `tabata.resident3@takago.com`
- `tabata.resident4@takago.com`
- `tabata.resident5@takago.com`
- `tabata.resident6@takago.com`
- `temeke.resident1@takago.com`
- `temeke.resident2@takago.com`
- `temeke.resident3@takago.com`
- `temeke.resident4@takago.com`
- `temeke.resident5@takago.com`
- `temeke.resident6@takago.com`
- `kigamboni.resident1@takago.com`
- `kigamboni.resident2@takago.com`
- `kigamboni.resident3@takago.com`
- `kigamboni.resident4@takago.com`
- `kigamboni.resident5@takago.com`
- `kigamboni.resident6@takago.com`

## Seed Behavior

- Does not delete municipalities or wards.
- Does not modify GeoJSON boundaries.
- Does not modify assignment or routing logic.
- Uses existing SHA-256 password hashing.
- Updates existing target email accounts instead of duplicating them.
- Removes older demo `@takago.com` users outside this test set so only these five wards have seeded users.
- Creates one approved `TEST-*` vehicle per driver.

## Build

`.\gradlew.bat assembleDebug --console=plain` passed.
