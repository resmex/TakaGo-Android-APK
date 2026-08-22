# Ward GeoJSON Import Report

Asset: `app/src/main/assets/takago_dar_es_salaam_wards.geojson`

Import marker: `ward_geojson_import_version = 1`

## Asset Validation

- GeoJSON type expected by importer: `FeatureCollection`
- Required properties checked for every feature:
  - `ward_name`
  - `normalized_ward_name`
  - `municipality_name`
  - `municipality_code`
  - `source_shape_id`
  - `boundary_status`
- Supported geometry types: `Polygon`, `MultiPolygon`
- Geometry storage: only the geometry object is stored in `wards.boundary_geojson`

## Asset Counts

- Features: 106
- Polygon features: 95
- MultiPolygon features: 11
- Unique `municipality_code + normalized_ward_name` ward boundaries: 88
- Duplicate `source_shape_id` values detected in the asset: 0
- Split ward feature groups merged for storage: 13

## Municipality Feature Counts

- Ubungo MC (`UBUNGO_MC`): 15 features
- Kinondoni MC (`KINONDONI_MC`): 23 features
- Ilala MC (`ILALA_MC`): 31 features
- Temeke MC (`TEMEKE_MC`): 25 features
- Kigamboni MC (`KIGAMBONI_MC`): 12 features

## Final Unique Mapped Ward Counts

- Ubungo MC: 14 mapped ward rows
- Kinondoni MC: 19 mapped ward rows
- Ilala MC: 26 mapped ward rows
- Temeke MC: 20 mapped ward rows
- Kigamboni MC: 9 mapped ward rows
- Total mapped ward rows from asset: 88

The importer preserves existing ward IDs and assignments by matching on `municipality_id + normalized_ward_name`. Multiple source features for the same ward are merged into one `MultiPolygon` geometry for that ward row.

## Runtime Verification Targets

- Import runs in a SQLite transaction.
- Municipalities are inserted or updated by `code` or normalized name.
- Wards are inserted or updated by `municipality_id + normalized_name`.
- `app_settings.ward_geojson_import_version` prevents repeat imports on restart.
- Active mapped ward polygons are used for resident pickup point-in-polygon detection.
- Normal driver assignment requires matching `municipality_id` and `ward_id`, driver availability, approved same-ward vehicle, and sufficient vehicle capacity where capacity is set.
