#!/usr/bin/env python3
"""Match LiftLog's shipped seed exercises against free-exercise-db entries by name.

HISTORICAL: the authoritative run happened 2026-07-09 against the v1 seed (69 exercises);
its output is the checked-in existing-id-map.json. Kept for provenance and reruns.

Writes ~/Code/existing-id-map.json: { fedb_id: {"uuid": <app uuid>, "appName": <v1 name>} }
Prints ONLY a succinct summary (counts + unmatched with fuzzy candidates).
"""
import difflib
import json
import os
import re
import sys

sys.path.insert(0, os.path.dirname(os.path.abspath(__file__)))
from seed_common import FEDB, current_seed_path

OUT = os.path.join(os.path.dirname(os.path.abspath(__file__)), "existing-id-map.json")

# Manual resolutions for names that don't match automatically: v1 name -> fedb id.
# A value of None means "confirmed absent from fedb; carry over from v1 unenriched".
# v1 name -> fedb NAME (resolved to id below), or None = confirmed absent (carry over unenriched)
OVERRIDES = {
    "Barbell Bench Press": "Barbell Bench Press - Medium Grip",
    "Incline Barbell Bench Press": "Barbell Incline Bench Press - Medium Grip",
    "Machine Chest Press": "Machine Bench Press",
    "Pec Deck": "Butterfly",
    "Cable Fly": "Cable Crossover",
    "Push-up": "Pushups",
    "Dips": "Dips - Chest Version",  # v1 classifies Dips under CHEST
    "Deadlift": "Barbell Deadlift",
    "Barbell Row": "Bent Over Barbell Row",
    "T-Bar Row": "T-Bar Row with Handle",
    "Pull-up": "Pullups",
    "Lat Pulldown": "Wide-Grip Lat Pulldown",
    "Seated Cable Row": "Seated Cable Rows",
    "Machine Row": "Leverage Iso Row",
    "Back Extension": "Hyperextensions (Back Extensions)",
    "Overhead Press": "Standing Military Press",
    "Seated Dumbbell Shoulder Press": "Dumbbell Shoulder Press",
    "Machine Shoulder Press": "Machine Shoulder (Military) Press",
    "Lateral Raise": "Side Lateral Raise",
    "Cable Lateral Raise": "Standing Low-Pulley Deltoid Raise",
    "Rear Delt Fly": "Reverse Machine Flyes",
    "Front Raise": "Front Dumbbell Raise",
    "Upright Row": "Upright Barbell Row",
    "Dumbbell Curl": "Dumbbell Bicep Curl",
    "Hammer Curl": "Hammer Curls",
    "Cable Curl": "Standing Biceps Cable Curl",
    "Preacher Curl Machine": "Machine Preacher Curls",
    "Close-Grip Bench Press": "Close-Grip Barbell Bench Press",
    "Skull Crusher": "EZ-Bar Skullcrusher",
    "Overhead Cable Extension": "Cable Rope Overhead Triceps Extension",
    "Dumbbell Overhead Extension": "Seated Triceps Press",
    "Back Squat": "Barbell Squat",
    "Front Squat": "Front Barbell Squat",
    "Bulgarian Split Squat": "Split Squat with Dumbbells",
    "Walking Lunge": "Barbell Walking Lunge",
    "Leg Extension": "Leg Extensions",
    "Stiff-Leg Deadlift": "Stiff-Legged Barbell Deadlift",
    "Lying Leg Curl": "Lying Leg Curls",
    "Hip Thrust": "Barbell Hip Thrust",
    "Cable Glute Kickback": "One-Legged Cable Kickback",
    "Hip Abduction Machine": "Thigh Abductor",
    "Standing Calf Raise": "Standing Calf Raises",
    "Calf Press on Leg Press": "Calf Press On The Leg Press Machine",
    "Crunch": "Crunches",
    "Ab Wheel Rollout": "Ab Roller",
    "Barbell Wrist Curl": "Seated Palm-Up Barbell Wrist Curl",
    "Kettlebell Swing": "One-Arm Kettlebell Swings",
}


def norm(name: str) -> str:
    n = name.lower()
    n = re.sub(r"[^a-z0-9]+", "", n)
    return n


v1 = json.load(open(current_seed_path(), encoding="utf-8"))["exercises"]
fedb = json.load(open(FEDB, encoding="utf-8"))

fedb_by_norm = {}
for e in fedb:
    fedb_by_norm.setdefault(norm(e["name"]), []).append(e)

mapping = {}
unmatched = []
carryover = []
for ex in v1:
    if ex["name"] in OVERRIDES:
        target_name = OVERRIDES[ex["name"]]
        if target_name is None:
            carryover.append(ex["name"])
            continue
        hits = [e for e in fedb if e["name"] == target_name]
        if len(hits) != 1:
            print(f"BAD OVERRIDE: {ex['name']!r} -> {target_name!r} ({len(hits)} hits)")
            unmatched.append(ex["name"])
            continue
        mapping[hits[0]["id"]] = {"uuid": ex["id"], "appName": ex["name"]}
        continue
    hits = fedb_by_norm.get(norm(ex["name"]), [])
    if len(hits) == 1:
        mapping[hits[0]["id"]] = {"uuid": ex["id"], "appName": ex["name"]}
    elif len(hits) > 1:
        print(f"AMBIGUOUS: {ex['name']} -> {[h['id'] for h in hits]}")
        unmatched.append(ex["name"])
    else:
        unmatched.append(ex["name"])

json.dump(mapping, open(OUT, "w", encoding="utf-8"), indent=1)

print(f"v1 exercises: {len(v1)}")
print(f"matched: {len(mapping)}  carryover(absent): {len(carryover)}  unmatched: {len(unmatched)}")
for name in carryover:
    print(f"CARRYOVER: {name}")
if unmatched:
    all_names = [e["name"] for e in fedb]
    for name in unmatched:
        cands = difflib.get_close_matches(name, all_names, n=3, cutoff=0.4)
        print(f"UNMATCHED: {name!r} candidates: {cands}")
    sys.exit(1)
