#!/usr/bin/env python3
"""Debug CSV export: all 873 fedb exercises with raw + mapped info, relevance,
and the seeded UUID (existing app UUID or the deterministic new one). Excel-friendly."""
import csv
import os
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from seed_common import (EQUIPMENT, EXISTING_EQUIPMENT_FIX, FORCE, MUSCLE, NAME_FIX,
                         load_all, map_secondaries, seed_uuid)

OUT = os.path.expanduser("~/Code/exercise-export.csv")

fedb, judged, id_map, curated = load_all()
curated_by_uuid = {e["id"]: e for e in curated}

with open(OUT, "w", newline="", encoding="utf-8-sig") as f:  # BOM so Excel detects UTF-8
    w = csv.writer(f)
    w.writerow([
        "fedb_id", "name", "relevance", "seeded", "seed_uuid", "is_existing", "app_name",
        "seed_name", "category", "level", "mechanic",
        "force_raw", "force_mapped", "equipment_raw", "equipment_mapped",
        "primary_raw", "primary_mapped", "secondary_raw", "secondary_mapped",
        "instructions", "images",
    ])
    for e in fedb:
        relevance = judged[e["id"]]
        existing = id_map.get(e["id"])
        primary_mapped = MUSCLE[e["primaryMuscles"][0]]
        if existing:
            uuid_ = existing["uuid"]
            cur = curated_by_uuid[uuid_]
            seed_name = cur["name"]
            muscle_mapped = cur["muscleGroup"]
            equipment_mapped = EXISTING_EQUIPMENT_FIX.get(seed_name, cur["equipment"])
        else:
            uuid_ = seed_uuid(e["id"]) if relevance == 1 else ""
            seed_name = NAME_FIX.get(e["name"], e["name"]) if relevance == 1 else ""
            muscle_mapped = primary_mapped
            equipment_mapped = EQUIPMENT[e["equipment"]]
        w.writerow([
            e["id"], e["name"], relevance, "yes" if relevance == 1 else "no",
            uuid_, "yes" if existing else "no", existing["appName"] if existing else "",
            seed_name, e["category"], e["level"], e["mechanic"] or "",
            e["force"] or "", FORCE[e["force"]] or "",
            e["equipment"] or "", equipment_mapped,
            e["primaryMuscles"][0], muscle_mapped,
            " | ".join(e["secondaryMuscles"]),
            " | ".join(map_secondaries(e, muscle_mapped)),
            " ".join(e["instructions"]).replace("\n", " "),
            " | ".join(e["images"]),
        ])

rows = sum(1 for _ in open(OUT, encoding="utf-8-sig")) - 1
print(f"wrote {OUT}: {rows} data rows")
