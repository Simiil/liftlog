# 09 — German On-Device Spot-Check (M6)

> **Status:** Done · 2026-06-11
> Covers the M6 PR3 exit step "manual on-device German pass" from
> [08-i18n-spec](08-i18n-spec.md) / plan Task 21. Performed on `emulator-5554`
> (Pixel 9 Pro XL AVD, Android 16) with the per-app language set to German
> (`cmd locale set-app-locales de.simiil.liftlog --locales de`). Walked: Home →
> start session → exercise picker (German search) → numpad entry → log set →
> finish → Statistik → Verlauf, plus an en↔de switch comparison.

## 1. Verified

| # | Check | Result | Evidence |
|---|-------|--------|----------|
| 1 | Home fully German, du-form copy, tabs Start/Pläne/Statistik/Verlauf | ✅ Pass | No English leaks, no clipping |
| 2 | Picker: list **sorted by German names** (Collator: "Aufrechtes Rudern" first), chips German | ✅ Pass | PR2 sort on localized names |
| 3 | Picker search: "Kreuz" matches Kreuzheben / Rumänisches K. / Steifbeiniges K. | ✅ Pass | Localized-name matching live |
| 4 | Numpad: decimal key renders `,`; chips `+2,5`/`−2,5`; "Abbrechen"/"Fertig" | ✅ Pass | PR1 locale entry |
| 5 | Entry `102,5` → buffer `102,5 kg` → Fertig → SATZ LOGGEN → set row `102,5 kg × 10` | ✅ Pass | Comma value parses & persists, no silent revert |
| 6 | `SATZ LOGGEN` fits full-width button, no wrap (flagged length risk) | ✅ Pass | |
| 7 | Statistik: `1,0t` weekly volume, `e1RM 136,67 kg`, German labels/trend line | ✅ Pass | German decimal commas in analytics |
| 8 | Verlauf: "Vor 5 Minuten · 1 Satz" — correct singular plural, PR chip | ✅ Pass | |
| 9 | TalkBack proxy: content descriptions German ("Satz 1 geloggt: 102,5 Kilogramm, 10 Wdh.", "Einheit abschließen", …) | ✅ Pass | Verified via a11y tree dump |
| 10 | en↔de switch: set rows re-render with the right separator (102.5 ↔ 102,5) | ✅ Pass | Composable-level formatting is locale-reactive |

## 2. Findings

| # | Finding | Severity | Scope |
|---|---------|----------|-------|
| F-01 | Stepper a11y label said "2.5 Kilogramm" under de (`WeightStepper.kt` used raw `Double.toString()`). | Minor | **Fixed in PR3** (`Decimals.format`) |
| F-02 | After a **mid-session** per-app language switch, exercise names resolved in ViewModels stay in the previous language until the next data emission or screen re-entry (ViewModels survive configuration changes; their `StateFlow` isn't locale-keyed). Self-heals on any DB write / navigation. Composable-resolved surfaces (picker rows, analytics browser rows, set-row numbers) update immediately. | Minor, edge case | Known limitation — accept for v1 or add an `ACTION_LOCALE_CHANGED` signal later |
| F-03 | Weight stepper wraps ≥5-char values onto two lines and clips "kg" (`102,5` → "102," / "5"). **Identical under English** (`102.5`) — pre-existing layout issue, not i18n. | Minor | Out of M6 scope — backlog |
| F-04 | Finish snackbar `session_finish_summary` is a plain string, so "1 Sätze" / "1 sets" for a single set. Pre-existing English plural gap mirrored by the translation. | Minor | Out of M6 scope — convert to `<plurals>` later |

## 3. Translation review notes

A fluent-German terminology review of all 265 keys ran before this pass
(register: consistent du-form; terminology: Satz/Sätze, Wdh., Einheit(en);
loanwords kept where German lifters use them). Five exercise names were
corrected as a result (upright row, machine chest press, rear delt fly,
preacher curl, glute kickback). The translation remains a **first pass —
native-speaker review requested** before release (PR3).
