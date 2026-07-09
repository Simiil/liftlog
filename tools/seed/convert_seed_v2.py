#!/usr/bin/env python3
"""Build the next seed JSON from the free-exercise-db dump + relevance judgments.

- relevance-1 entries only (every already-shipped built-in is relevance 1 by contract)
- already-shipped built-ins keep their UUID, curated name, muscleGroup, equipment
  (modulo EXISTING_EQUIPMENT_FIX) and take force + secondaryMuscleGroups from fedb
- new entries get deterministic UUIDv5 ids and fully mapped classification

Writes ~/Code/exercises.v2.json (review artifact; copy into assets/seed/ to ship).
Validates the SeedAssetTest contracts; prints ONLY summary stats.
"""
import json
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from seed_common import (EQUIPMENT, EXISTING_EQUIPMENT_FIX, FORCE, MUSCLE, NAME_FIX,
                         load_all, map_secondaries, seed_uuid)

OUT = os.path.expanduser("~/Code/exercises.v2.json")
SEED_VERSION = 2
MUSCLE_ORDER = ["CHEST", "BACK", "SHOULDERS", "BICEPS", "TRICEPS", "QUADS",
                "HAMSTRINGS", "GLUTES", "CALVES", "ABS", "FOREARMS", "OTHER"]

fedb, judged, id_map, curated = load_all()
curated_by_uuid = {e["id"]: e for e in curated}

entries = []
existing_count = 0
for e in fedb:
    if judged[e["id"]] != 1:
        continue
    primary = MUSCLE[e["primaryMuscles"][0]]
    force = FORCE[e["force"]]
    if e["id"] in id_map:
        uuid_ = id_map[e["id"]]["uuid"]
        if uuid_ in curated_by_uuid:
            existing_count += 1
            cur = curated_by_uuid[uuid_]
            name = cur["name"]
            muscle = cur["muscleGroup"]
            equipment = EXISTING_EQUIPMENT_FIX.get(name, cur["equipment"])
        else:  # mapped but not yet shipped (shouldn't happen after v2)
            name = NAME_FIX.get(e["name"], e["name"])
            muscle = primary
            equipment = EQUIPMENT[e["equipment"]]
        secondaries = map_secondaries(e, muscle)
    else:
        uuid_ = seed_uuid(e["id"])
        name = NAME_FIX.get(e["name"], e["name"])
        muscle = primary
        equipment = EQUIPMENT[e["equipment"]]
        secondaries = map_secondaries(e, muscle)
    entry = {"id": uuid_, "name": name, "muscleGroup": muscle, "equipment": equipment}
    if force is not None:
        entry["force"] = force
    if secondaries:
        entry["secondaryMuscleGroups"] = secondaries
    entries.append(entry)

entries.sort(key=lambda x: (MUSCLE_ORDER.index(x["muscleGroup"]), x["name"].lower()))

# --- validation (mirrors SeedAssetTest contracts) ---
errors = []
ids = [e["id"] for e in entries]
if len(ids) != len(set(ids)):
    errors.append("duplicate ids")
names = [e["name"].lower() for e in entries]
dupes = {n for n in names if names.count(n) > 1}
if dupes:
    errors.append(f"duplicate names (case-insensitive): {sorted(dupes)}")
missing_existing = {m["uuid"] for m in id_map.values()} - set(ids)
if missing_existing:
    errors.append(f"already-shipped built-ins missing: {missing_existing}")
for e in entries:
    assert e["muscleGroup"] in MUSCLE_ORDER
    assert e["equipment"] in set(EQUIPMENT.values())
    for s in e.get("secondaryMuscleGroups", []):
        assert s in MUSCLE_ORDER and s != e["muscleGroup"]
    assert len(e.get("secondaryMuscleGroups", [])) == len(set(e.get("secondaryMuscleGroups", [])))
if errors:
    print("VALIDATION FAILED:")
    for err in errors:
        print(" -", err)
    sys.exit(1)

with open(OUT, "w", encoding="utf-8") as f:
    json.dump({"seedVersion": SEED_VERSION, "exercises": entries}, f, indent=2, ensure_ascii=False)
    f.write("\n")

print(f"wrote {OUT}: {len(entries)} exercises ({existing_count} existing, {len(entries) - existing_count} new)")
print("per muscleGroup:", {m: sum(1 for e in entries if e['muscleGroup'] == m) for m in MUSCLE_ORDER})
eq_counts = {}
for e in entries:
    eq_counts[e["equipment"]] = eq_counts.get(e["equipment"], 0) + 1
print("per equipment:", dict(sorted(eq_counts.items(), key=lambda kv: -kv[1])))
force_counts = {"PUSH": 0, "PULL": 0, "STATIC": 0, "null": 0}
for e in entries:
    force_counts[e.get("force", "null")] += 1
print("force:", force_counts)
print("with secondaries:", sum(1 for e in entries if e.get("secondaryMuscleGroups")))
