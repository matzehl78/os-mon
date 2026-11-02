#!/usr/bin/env python3
"""
golden_reference.py: Referenzimplementierung der OS-Mon Pet Engine + Generator für golden_vectors.json

Zweck: eine plattformneutrale "Wahrheit" für Kotlin (PetCore.kt) und Swift (PetCore.swift).
Die Vektoren enthalten (a) die Werte der Kotlin-Unit-Tests, (b) Zufallsfälle mit festem Seed,
(c) Tagesverläufe (Sequenzen) für tick().

Aufruf:  python3 golden_reference.py            -> schreibt golden_vectors.json
Das Skript bricht ab, wenn die Referenz von den handgerechneten Kotlin-Testwerten abweicht
oder wenn ein Vektor zu nah an einer Schwelle liegt (Float32 vs. Double könnte sonst kippen).
"""
import json
import math
import random

# ---------------------------------------------------------------- Referenz

DEF_CFG = {"screenGoalMin": 180.0, "stepGoal": 7000, "pickupRef": 50.0}
MAX_PATS = 4
HYST = 5.0
MOODS = ["hibernation", "sick", "tired", "content", "radiant"]
MOOD_MIN = {"hibernation": 0.0, "sick": 20.0, "tired": 40.0, "content": 65.0, "radiant": 85.0}


def clamp(x, lo=0.0, hi=1.0):
    return max(lo, min(hi, x))


def inputs(**kw):
    base = dict(batteryPct=None, charging=False, lowPower=False, freeStorageRatio=None,
                screenMinutes=None, nightMinutes=None, pickups=None, steps=None,
                pats=0, focusSessions=0)
    base.update(kw)
    return base


def energy(i):
    b = i["batteryPct"]
    if b is None:
        return None
    return clamp(b * (0.85 if i["lowPower"] else 1.0) + (10.0 if i["charging"] else 0.0), 0.0, 100.0)


def fitness(i):
    f = i["freeStorageRatio"]
    if f is None:
        return None
    return 100.0 * clamp((f - 0.05) / 0.25) ** 0.8


def stress(i, c):
    t = i["screenMinutes"]
    if t is None:
        return None
    n = i["nightMinutes"] or 0.0
    if i["pickups"] is not None:
        x = 0.65 * t / (2 * c["screenGoalMin"]) + 0.25 * n / 45.0 + 0.10 * i["pickups"] / (2 * c["pickupRef"])
    else:
        x = 0.72 * t / (2 * c["screenGoalMin"]) + 0.28 * n / 45.0
    return clamp(100.0 * x, 0.0, 100.0)


def vitality(i, c):
    s = i["steps"]
    if s is None:
        return None
    return 100.0 * clamp(s / c["stepGoal"]) ** 0.7


def affection(r, i):
    inter = min(100.0, 25.0 * min(i["pats"], MAX_PATS) + 50.0 * i["focusSessions"])
    return 0.7 * r + 0.3 * inter if r is not None else inter


def targets(i, c):
    s = stress(i, c)
    r = None if s is None else 100.0 - s
    return {"e": energy(i), "r": r, "f": fitness(i), "v": vitality(i, c), "a": affection(r, i)}


def step(prev, target, dt):
    dt = max(dt, 0.0)

    def ease(p, t, tau):
        if t is None:
            return None
        if p is None:
            return t
        return p + (t - p) * (1.0 - math.exp(-dt / tau))

    p = prev or {}
    return {"e": ease(p.get("e"), target["e"], 0.5), "r": ease(p.get("r"), target["r"], 1.5),
            "f": target["f"], "v": target["v"], "a": ease(p.get("a"), target["a"], 6.0)}


def wellbeing(s):
    parts = [(w, s[k]) for k, w in (("e", 0.30), ("r", 0.25), ("f", 0.20), ("v", 0.15), ("a", 0.10))
             if s.get(k) is not None]
    if not parts:
        return 50.0
    wsum = sum(w for w, _ in parts)
    raw = sum(w * v for w, v in parts) / wsum
    vals = [s[k] for k in ("e", "r", "f", "v") if s.get(k) is not None]
    weakest = min(vals) if vals else 100.0
    return clamp(raw * (0.6 + 0.4 * weakest / 100.0), 0.0, 100.0)


def mood(h, prev):
    idx = MOODS.index(prev) if prev else max(k for k, m in enumerate(MOODS) if h >= MOOD_MIN[m])
    while idx < len(MOODS) - 1 and h >= MOOD_MIN[MOODS[idx + 1]] + HYST:
        idx += 1
    while idx > 0 and h < MOOD_MIN[MOODS[idx]] - HYST:
        idx -= 1
    return MOODS[idx]


def overlays(s):
    out = []
    if (s["e"] if s.get("e") is not None else 100.0) < 15.0:
        out.append("yawning")
    if (s["f"] if s.get("f") is not None else 100.0) < 20.0:
        out.append("chubby")
    if (s["r"] if s.get("r") is not None else 100.0) < 30.0:
        out.append("stressed")
    if (s["v"] if s.get("v") is not None else 100.0) < 20.0:
        out.append("sluggish")
    return out


def tick(prev, i, c, dt):
    stats = step(prev["stats"] if prev else None, targets(i, c), dt)
    h = wellbeing(stats)
    return {"stats": stats, "wellbeing": h, "mood": mood(h, prev["mood"] if prev else None),
            "overlays": overlays(stats)}


THRESH = [("social", "chatterling", 0.35), ("entertainment", "bingeon", 0.35),
          ("games", "pixelo", 0.30), ("productivity", "zenfuchs", 0.30)]


def evolve(shares, avg_daily, avg_w, c, outdoor=0.0):
    cands = [(form, shares.get(g, 0.0), k) for k, (g, form, mn) in enumerate(THRESH) if shares.get(g, 0.0) >= mn]
    if outdoor >= 0.25:
        cands.append(("wandero", outdoor, len(THRESH)))
    cands.sort(key=lambda t: (-t[1], t[2]))
    top = max([v for g, v in shares.items() if g != "other"], default=0.0)
    balanced = top <= 0.30 and avg_daily <= c["screenGoalMin"]
    if cands:
        form = cands[0][0]
    elif balanced:
        form = "wandero"
    else:
        best, best_s = THRESH[0], shares.get(THRESH[0][0], 0.0)
        for t in THRESH[1:]:
            if shares.get(t[0], 0.0) > best_s:
                best, best_s = t, shares.get(t[0], 0.0)
        form = best[1]
    hybrid = cands[1][0] if len(cands) > 1 and cands[0][1] - cands[1][1] < 0.05 else None
    branch = "radiant" if avg_w >= 65 else ("wild" if avg_w < 40 else "standard")
    return {"form": form, "hybrid": hybrid, "branch": branch}


# ---------------------------------------------------------------- Selbsttest gegen die Kotlin-Testwerte

def near(a, b, eps=0.1):
    return abs(a - b) <= eps


def selftest():
    c = DEF_CFG
    assert near(energy(inputs(batteryPct=50)), 50)
    assert near(energy(inputs(batteryPct=50, lowPower=True)), 42.5)
    assert near(energy(inputs(batteryPct=50, charging=True)), 60)
    assert near(energy(inputs(batteryPct=95, charging=True)), 100)
    assert energy(inputs()) is None
    for r, exp in ((0.30, 100), (0.60, 100), (0.05, 0), (0.175, 57.4)):
        assert near(fitness(inputs(freeStorageRatio=r)), exp), r
    assert near(stress(inputs(screenMinutes=360, nightMinutes=0), c), 72)
    assert near(stress(inputs(screenMinutes=180, nightMinutes=45), c), 64)
    assert near(stress(inputs(screenMinutes=360, nightMinutes=0, pickups=0), c), 65)
    assert near(stress(inputs(screenMinutes=2000, nightMinutes=500, pickups=1000), c), 100)
    for s, exp in ((7000, 100), (20000, 100), (3500, 61.6), (0, 0)):
        assert near(vitality(inputs(steps=s), c), exp), s
    assert near(affection(100, inputs(pats=2, focusSessions=1)), 100)
    assert near(affection(100, inputs()), 70)
    assert near(affection(None, inputs(pats=1)), 25)
    assert near(wellbeing({"e": 100, "r": 100, "f": 100, "v": 100, "a": 100}), 100)
    assert near(wellbeing({"e": 0, "r": 100, "f": 100, "v": 100, "a": 100}), 42)
    assert near(wellbeing({"e": 50}), 40)
    assert near(wellbeing({}), 50)
    assert near(step({"e": 100}, {"e": 0, "r": None, "f": None, "v": None, "a": None}, 0.25)["e"], 60.7)
    assert step({"e": 50}, {"e": None, "r": None, "f": None, "v": None, "a": None}, 1)["e"] is None
    assert near(step({"e": 50}, {"e": 0, "r": None, "f": None, "v": None, "a": None}, -1)["e"], 50)
    for h, prev, exp in ((90, None, "radiant"), (70, None, "content"), (50, None, "tired"),
                         (25, None, "sick"), (5, None, "hibernation"), (86, "content", "content"),
                         (90, "content", "radiant"), (82, "radiant", "radiant"), (79, "radiant", "content")):
        assert mood(h, prev) == exp, (h, prev)
    assert set(overlays({"e": 10, "r": 20, "f": 10, "v": 10})) == {"yawning", "chubby", "stressed", "sluggish"}
    assert overlays({"e": 80, "r": 80, "f": 80, "v": 80}) == [] and overlays({}) == []
    i = inputs(batteryPct=100, freeStorageRatio=0.4, screenMinutes=60, steps=7000)
    first = tick(None, i, c, 0.25)
    assert first["mood"] == "radiant" and near(first["wellbeing"], 88.7)
    second = tick(first, dict(i, batteryPct=10), c, 0.25)
    assert 10 < second["stats"]["e"] < first["stats"]["e"] and near(second["stats"]["e"], 64.6)
    e1 = evolve({"social": 0.5, "entertainment": 0.1, "other": 0.4}, 200, 70, c)
    assert e1 == {"form": "chatterling", "hybrid": None, "branch": "radiant"}
    e2 = evolve({"social": 0.40, "entertainment": 0.37, "other": 0.23}, 200, 50, c)
    assert e2 == {"form": "chatterling", "hybrid": "bingeon", "branch": "standard"}
    bal = {"social": 0.25, "entertainment": 0.25, "games": 0.2, "productivity": 0.2, "other": 0.1}
    assert evolve(bal, 150, 30, c)["form"] == "wandero" and evolve(bal, 150, 30, c)["branch"] == "wild"
    assert evolve(bal, 400, 50, c)["form"] == "chatterling"


# ---------------------------------------------------------------- Vektoren

MOOD_EDGES = [15, 20, 25, 35, 40, 45, 60, 65, 70, 80, 85, 90]
MARGIN = 0.05
problems = []


def check_margin(label, value, edges):
    for e in edges:
        if abs(value - e) < MARGIN:
            problems.append(f"{label}: Wert {value:.4f} nahe Schwelle {e}")


def check_snapshot(label, snap):
    check_margin(label + " H", snap["wellbeing"], MOOD_EDGES)
    st = snap["stats"]
    for k, edge in (("e", 15), ("f", 20), ("r", 30), ("v", 20)):
        if st.get(k) is not None:
            check_margin(f"{label} {k}", st[k], [edge])


def rnd(x, n=5):
    if isinstance(x, dict):
        return {k: rnd(v, n) for k, v in x.items()}
    if isinstance(x, list):
        return [rnd(v, n) for v in x]
    if isinstance(x, float):
        return round(x, n)
    return x


def build():
    c = DEF_CFG
    V = {"version": 1, "tolerance": 0.01}

    # --- targets: Kotlin-Testwerte + Zufall
    cases = [
        ("kotlin: Akku 50", inputs(batteryPct=50)),
        ("kotlin: Low Power", inputs(batteryPct=50, lowPower=True)),
        ("kotlin: Laden", inputs(batteryPct=50, charging=True)),
        ("kotlin: Laden geklemmt", inputs(batteryPct=95, charging=True)),
        ("kotlin: Speicher 30%", inputs(freeStorageRatio=0.30)),
        ("kotlin: Speicher 5%", inputs(freeStorageRatio=0.05)),
        ("kotlin: Speicher 17,5%", inputs(freeStorageRatio=0.175)),
        ("kotlin: Stress iOS 2Z", inputs(screenMinutes=360, nightMinutes=0)),
        ("kotlin: Stress iOS Z+Nacht", inputs(screenMinutes=180, nightMinutes=45)),
        ("kotlin: Stress Android 2Z", inputs(screenMinutes=360, nightMinutes=0, pickups=0)),
        ("kotlin: Stress max", inputs(screenMinutes=2000, nightMinutes=500, pickups=1000)),
        ("kotlin: Schritte 3500", inputs(steps=3500)),
        ("kotlin: Schritte 20000", inputs(steps=20000)),
        ("kotlin: Zuneigung voll", inputs(screenMinutes=0, nightMinutes=0, pats=2, focusSessions=1)),
        ("kotlin: Zuneigung nur Interaktion", inputs(pats=1)),
        ("kotlin: alles fehlt", inputs()),
        ("Streicheln gedeckelt", inputs(screenMinutes=0, pats=9)),
    ]
    rng = random.Random(42)
    for k in range(40):
        cases.append((f"zufall {k}", inputs(
            batteryPct=None if rng.random() < 0.1 else round(rng.uniform(0, 100), 1),
            charging=rng.random() < 0.3, lowPower=rng.random() < 0.3,
            freeStorageRatio=None if rng.random() < 0.1 else round(rng.uniform(0, 0.6), 3),
            screenMinutes=None if rng.random() < 0.1 else round(rng.uniform(0, 600), 1),
            nightMinutes=None if rng.random() < 0.3 else round(rng.uniform(0, 120), 1),
            pickups=None if rng.random() < 0.5 else rng.randint(0, 200),
            steps=None if rng.random() < 0.1 else rng.randint(0, 15000),
            pats=rng.randint(0, 6), focusSessions=rng.randint(0, 3))))
    cfg2 = {"screenGoalMin": 120.0, "stepGoal": 10000, "pickupRef": 80.0}
    V["targets"] = []
    for name, inp in cases:
        cfg = cfg2 if name.startswith("zufall") and int(name.split()[1]) % 4 == 0 else c
        V["targets"].append({"name": name, "config": cfg, "inputs": inp, "expect": targets(inp, cfg)})

    # --- step
    none = {"e": None, "r": None, "f": None, "v": None, "a": None}
    V["step"] = [
        {"name": "kotlin: 100 -> 0, 15 min", "prev": {**none, "e": 100.0}, "target": {**none, "e": 0.0}, "dtHours": 0.25},
        {"name": "kotlin: ohne Vorwert", "prev": None, "target": {**none, "e": 0.0}, "dtHours": 1.0},
        {"name": "kotlin: Sensor fällt weg", "prev": {**none, "e": 50.0}, "target": dict(none), "dtHours": 1.0},
        {"name": "kotlin: dt negativ", "prev": {**none, "e": 50.0}, "target": {**none, "e": 0.0}, "dtHours": -1.0},
        {"name": "alle Werte, 1 h", "prev": {"e": 80.0, "r": 40.0, "f": 90.0, "v": 10.0, "a": 55.0},
         "target": {"e": 20.0, "r": 90.0, "f": 30.0, "v": 70.0, "a": 85.0}, "dtHours": 1.0},
        {"name": "alle Werte, 6 h", "prev": {"e": 80.0, "r": 40.0, "f": 90.0, "v": 10.0, "a": 55.0},
         "target": {"e": 20.0, "r": 90.0, "f": 30.0, "v": 70.0, "a": 85.0}, "dtHours": 6.0},
        {"name": "teilweise Vorwerte", "prev": {**none, "r": 60.0}, "target": {"e": 70.0, "r": 20.0, "f": None, "v": 5.0, "a": 33.0}, "dtHours": 0.5},
    ]
    for s in V["step"]:
        s["expect"] = step(s["prev"], s["target"], s["dtHours"])

    # --- wellbeing
    stats_cases = [
        ("kotlin: alles 100", {"e": 100.0, "r": 100.0, "f": 100.0, "v": 100.0, "a": 100.0}),
        ("kotlin: schwächstes Glied", {"e": 0.0, "r": 100.0, "f": 100.0, "v": 100.0, "a": 100.0}),
        ("kotlin: nur E=50", {**none, "e": 50.0}),
        ("kotlin: leer", dict(none)),
        ("nur A", {**none, "a": 40.0}),
        ("gemischt", {"e": 70.0, "r": 55.0, "f": None, "v": 30.0, "a": 80.0}),
    ]
    V["wellbeing"] = [{"name": n, "stats": s, "expect": wellbeing(s)} for n, s in stats_cases]

    # --- mood
    mood_cases = [(90, None), (70, None), (50, None), (25, None), (5, None), (86, "content"), (90, "content"),
                  (82, "radiant"), (79, "radiant"), (60, "content"), (59, "content"), (26, "tired"),
                  (24, "tired"), (45, "tired"), (44, "tired"), (15, "sick"), (14, "sick"), (95, "hibernation")]
    V["mood"] = [{"name": f"h={h} prev={p}", "h": float(h), "prev": p, "expect": mood(float(h), p)} for h, p in mood_cases]

    # --- overlays
    ov_cases = [("kotlin: alle vier", {"e": 10.0, "r": 20.0, "f": 10.0, "v": 10.0, "a": None}),
                ("kotlin: keine", {"e": 80.0, "r": 80.0, "f": 80.0, "v": 80.0, "a": 80.0}),
                ("kotlin: fehlende Werte", dict(none)),
                ("nur gestresst", {"e": 50.0, "r": 29.0, "f": 50.0, "v": 50.0, "a": None}),
                ("knapp darüber", {"e": 16.0, "r": 31.0, "f": 21.0, "v": 21.0, "a": None})]
    V["overlays"] = [{"name": n, "stats": s, "expect": overlays(s)} for n, s in ov_cases]

    # --- Sequenzen
    def seq(name, cfg, steps):
        prev, out = None, []
        for k, (inp, dt) in enumerate(steps):
            snap = tick(prev, inp, cfg, dt)
            check_snapshot(f"{name}[{k}]", snap)
            out.append({"inputs": inp, "dtHours": dt, "expect": snap})
            prev = snap
        return {"name": name, "config": cfg, "steps": out}

    bat = [100, 90, 78, 65, 52, 40, 28, 15]
    scr = [0, 20, 55, 90, 140, 200, 260, 330]
    pk = [5, 15, 30, 45, 60, 80, 100, 120]
    st = [500, 1500, 3000, 3800, 5200, 6000, 6800, 7200]
    foc = [0, 0, 1, 1, 1, 1, 1, 1]
    A = [(inputs(batteryPct=b, freeStorageRatio=0.12, screenMinutes=s, nightMinutes=0, pickups=p, steps=t,
                 pats=min(k, 2), focusSessions=f), 0.25 if k == 0 else 1.0)
         for k, (b, s, p, t, f) in enumerate(zip(bat, scr, pk, st, foc))]
    B = [(inputs(batteryPct=b, charging=ch, freeStorageRatio=0.25, screenMinutes=s, nightMinutes=n),
          0.25 if k == 0 else 0.5)
         for k, (b, ch, s, n) in enumerate([(30, False, 40, 0), (24, False, 70, 0), (60, True, 70, 0),
                                            (85, True, 90, 0), (100, True, 120, 10), (95, False, 200, 30)])]
    C = [(inputs(batteryPct=b, lowPower=True, freeStorageRatio=0.04, screenMinutes=s, nightMinutes=n,
                 pickups=p, steps=t), 0.25 if k == 0 else 2.0)
         for k, (b, s, n, p, t) in enumerate([(20, 300, 60, 90, 400), (12, 420, 80, 140, 450),
                                              (8, 520, 100, 180, 500), (5, 600, 120, 220, 520)])]
    D = [(inputs(batteryPct=bb, freeStorageRatio=0.35, screenMinutes=s, steps=t), 0.25 if k == 0 else 0.5)
         for k, (bb, s, t) in enumerate([(90, 30, 2000), (80, 60, 3000), (None, 90, 4500), (None, 120, 6000),
                                         (60, 150, 7000)])]
    E = [(inputs(batteryPct=b, freeStorageRatio=0.4, screenMinutes=sm, nightMinutes=0, steps=7000, pats=2,
                 focusSessions=1), 0.25 if k == 0 else 1.0)
         for k, (b, sm) in enumerate([(100, 30), (85, 60), (60, 120), (35, 200), (15, 300), (8, 400), (8, 480)])]
    V["sequences"] = [seq("A Arbeitstag (Android)", c, A), seq("B Laden (iOS-Variante)", c, B),
                      seq("C Schlechter Tag", c, C), seq("D Sensor fällt aus", c, D),
                      seq("E Guter Tag kippt", c, E)]

    # --- evolve
    ev = [
        ("kotlin: dominant Social", {"social": 0.5, "entertainment": 0.1, "other": 0.4}, 200, 70, 0.0),
        ("kotlin: Hybrid", {"social": 0.40, "entertainment": 0.37, "other": 0.23}, 200, 50, 0.0),
        ("kotlin: Balance -> Wandero", {"social": 0.25, "entertainment": 0.25, "games": 0.2, "productivity": 0.2, "other": 0.1}, 150, 30, 0.0),
        ("kotlin: zu viel Zeit -> nächstliegend", {"social": 0.25, "entertainment": 0.25, "games": 0.2, "productivity": 0.2, "other": 0.1}, 400, 50, 0.0),
        ("Games", {"social": 0.1, "entertainment": 0.1, "games": 0.5, "productivity": 0.1, "other": 0.2}, 250, 66, 0.0),
        ("Produktivität", {"social": 0.1, "entertainment": 0.1, "games": 0.05, "productivity": 0.6, "other": 0.15}, 220, 45, 0.0),
        ("Schwelle genau 0,35", {"social": 0.35, "entertainment": 0.20, "games": 0.1, "productivity": 0.1, "other": 0.25}, 200, 65, 0.0),
        ("knapp unter Schwelle", {"social": 0.34, "entertainment": 0.30, "games": 0.1, "productivity": 0.1, "other": 0.16}, 300, 20, 0.0),
        ("Outdoor gewinnt", {"social": 0.36, "entertainment": 0.10, "games": 0.0, "productivity": 0.1, "other": 0.44}, 170, 80, 0.5),
        ("Outdoor Hybrid", {"social": 0.40, "entertainment": 0.10, "other": 0.5}, 170, 39, 0.38),
        ("Balance exakt 0,30", {"social": 0.30, "entertainment": 0.30, "games": 0.1, "productivity": 0.1, "other": 0.2}, 180, 50, 0.0),
        ("leere Anteile", {}, 100, 50, 0.0),
        ("Gleichstand Social/Entertainment", {"social": 0.4, "entertainment": 0.4, "other": 0.2}, 200, 50, 0.0),
    ]
    V["evolve"] = [{"name": n, "shares": sh, "avgDailyMinutes": float(ad), "avgWellbeing14": float(aw),
                    "outdoorShare": o, "config": c, "expect": evolve(sh, ad, aw, c, o)} for n, sh, ad, aw, o in ev]
    return V


if __name__ == "__main__":
    selftest()
    vectors = build()
    if problems:
        raise SystemExit("Vektoren zu nah an Schwellen:\n" + "\n".join(problems))
    with open("golden_vectors.json", "w", encoding="utf-8") as f:
        json.dump(rnd(vectors), f, ensure_ascii=False, indent=1)
    print("OK:", {k: len(v) for k, v in vectors.items() if isinstance(v, list)})
