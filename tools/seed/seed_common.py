"""Shared vocabulary mappings and paths for the seed pipeline (02-data-spec Appendix A,
design spec docs/superpowers/specs/2026-07-09-37-exercise-model-extension-design.md).

External inputs (not checked in; see README.md):
- ~/Code/exercises.json        free-exercise-db dump (873 entries)
- ~/Code/exercise-judged.json  relevance map, fedb id -> 1 (seed) / 2 (cut)

Checked-in inputs:
- existing-id-map.json  fedb id -> {uuid, appName} for exercises the app already ships
- names-de.json         English seed name -> German display name (all seeded entries)
"""
import json
import os
import re
import uuid

HERE = os.path.dirname(os.path.abspath(__file__))
ROOT = os.path.abspath(os.path.join(HERE, "..", ".."))
FEDB = os.path.expanduser("~/Code/exercises.json")
JUDGED = os.path.expanduser("~/Code/exercise-judged.json")
ID_MAP = os.path.join(HERE, "existing-id-map.json")

MUSCLE = {
    "abdominals": "ABS", "abductors": "OTHER", "adductors": "OTHER",
    "biceps": "BICEPS", "calves": "CALVES", "chest": "CHEST",
    "forearms": "FOREARMS", "glutes": "GLUTES", "hamstrings": "HAMSTRINGS",
    "lats": "BACK", "lower back": "BACK", "middle back": "BACK",
    "neck": "OTHER", "quadriceps": "QUADS", "shoulders": "SHOULDERS",
    "traps": "BACK", "triceps": "TRICEPS",
}
EQUIPMENT = {
    "barbell": "BARBELL", "dumbbell": "DUMBBELL", "machine": "MACHINE",
    "cable": "CABLE", "body only": "BODYWEIGHT", "kettlebells": "KETTLEBELL",
    "medicine ball": "MEDICINE_BALL", "foam roll": "FOAM_ROLLER",
    "bands": "BANDS", "exercise ball": "EXERCISE_BALL",
    "e-z curl bar": "BARBELL", "other": "OTHER", None: "BODYWEIGHT",
}
FORCE = {"push": "PUSH", "pull": "PULL", "static": "STATIC", None: None}

# Curated fixes for existing built-ins once the vocabulary allowed them
# (seed name -> equipment). Kettlebell Swing shipped as DUMBBELL before KETTLEBELL existed.
EXISTING_EQUIPMENT_FIX = {"Kettlebell Swing": "KETTLEBELL"}

# Curated display-name fixes for NEW entries (fedb name -> seed name).
NAME_FIX = {"Landmine 180's": "Landmine 180s"}


def current_seed_path():
    """The single packaged seed asset (exercises.v<N>.json)."""
    d = os.path.join(ROOT, "app/src/main/assets/seed")
    files = sorted(f for f in os.listdir(d) if re.fullmatch(r"exercises\.v\d+\.json", f))
    assert len(files) == 1, f"expected exactly one seed asset, found {files}"
    return os.path.join(d, files[0])


def seed_uuid(fedb_id):
    """Deterministic UUID for new seed entries — stable across pipeline reruns."""
    return str(uuid.uuid5(uuid.NAMESPACE_URL, "liftlog:seed:" + fedb_id))


def map_secondaries(entry, primary_mapped):
    out = []
    for m in entry["secondaryMuscles"]:
        mapped = MUSCLE[m]
        if mapped != primary_mapped and mapped not in out:
            out.append(mapped)
    return out


def load_all():
    """Returns (fedb, judged, id_map, curated) where `curated` is the CURRENT seed asset's
    exercises — the source of shipped names/classifications that reruns must preserve."""
    fedb = json.load(open(FEDB, encoding="utf-8"))
    judged = json.load(open(JUDGED, encoding="utf-8"))
    id_map = json.load(open(ID_MAP, encoding="utf-8"))
    curated = json.load(open(current_seed_path(), encoding="utf-8"))["exercises"]
    return fedb, judged, id_map, curated
