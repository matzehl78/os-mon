#!/usr/bin/env python3
"""
golden_evolution.py: Referenzimplementierung des Evolutions-Trackers + Generator für golden_evolution.json

Der Tracker entscheidet täglich (beim Abschluss eines Tages), ob das Wesen
  - zum ersten Mal evolviert (ab Tag 11),
  - eine Metamorphose durchläuft (andere Kategorie führt 14 Tage stabil, mind. 21 Tage seit der
    letzten Evolution, Abstand mind. 10 Prozentpunkte),
  - oder nur seinen Gesundheits-Zweig (Branch) bzw. das Hybrid-Merkmal aktualisiert.

Nutzt die Engine-Referenz aus golden_reference.py (gleicher Ordner).
Aufruf: python3 golden_evolution.py  -> schreibt golden_evolution.json
Das Skript bricht ab, wenn ein Szenario nicht das erwartete Verhalten zeigt oder eine Entscheidung zu
nah an einer Schwelle liegt (Float32 in Kotlin/Swift gegen Double hier).
"""
import copy
import json

import golden_reference as g

GROUPS = ["social", "entertainment", "games", "productivity", "other"]
FORM_GROUP = {"chatterling": "social", "bingeon": "entertainment", "pixelo": "games",
              "zenfuchs": "productivity", "wandero": None}

EVOLVE_AGE = 11          # frühestens ab Tag 11
MIN_USABLE = 5           # ... mit mind. 5 Tagen Nutzungsdaten im Fenster
FALLBACK_AGE = 21        # ... oder spätestens ab Tag 21 (auch ohne Daten)
WINDOW = 14              # Fenster für Durchschnitte (Datensätze)
HIST_MAX = 28
META_INTERVAL = 21       # Mindestabstand zwischen zwei Evolutionen
CHALLENGER_DAYS = 14     # so lange muss eine neue Form führen
MARGIN = 0.10            # Vorsprung der neuen Kategorie
KEEP = 0.5 ** (1.0 / 5.0)   # Halbwertszeit 5 Tage

PROBLEMS = []
S2_SWITCH = 16           # S2: ab diesem Tag Produktivität
S7_START, S7_END = 30, 40  # S7: Produktivität von..bis (ohne Ende)
S8_SWITCH = 14          # S8: ab diesem Tag Spiele 0,36 gegen Social 0,30  # S7: Produktivität von..bis (ohne Ende)


# ---------------------------------------------------------------- Tracker (Referenz)

def new_state(hatch_day):
    return {"hatchDay": hatch_day, "history": [], "blended": None, "current": None,
            "lastEvolutionDay": None, "challenger": None, "challengerSince": None, "pendingEvent": None}


def blend(prev, today):
    if prev is None:
        return {k: today.get(k, 0.0) for k in GROUPS}
    return {k: KEEP * prev.get(k, 0.0) + (1.0 - KEEP) * today.get(k, 0.0) for k in GROUPS}


def usable(r):
    return r["shares"] is not None and (r["totalMinutes"] or 0.0) > 0.0


def group_margin(blended, frm, to):
    fg, tg = FORM_GROUP[frm], FORM_GROUP[to]
    if fg is None or tg is None or blended is None:
        return None
    return blended.get(tg, 0.0) - blended.get(fg, 0.0)


def close_day(s, r, cfg, check=True):
    last = s["history"][-1]["epochDay"] if s["history"] else None
    if last is not None and r["epochDay"] <= last:
        return s                                          # Tag schon abgeschlossen: ignorieren
    s = copy.deepcopy(s)
    s["history"] = (s["history"] + [r])[-HIST_MAX:]
    if usable(r):
        s["blended"] = blend(s["blended"], r["shares"])

    window = s["history"][-WINDOW:]
    uw = [x for x in window if usable(x)]
    avg_daily = sum(x["totalMinutes"] for x in uw) / len(uw) if uw else 0.0
    ws = [x["avgWellbeing"] for x in window if x["avgWellbeing"] is not None]
    avg_w = sum(ws) / len(ws) if ws else 50.0
    cand = g.evolve(s["blended"] or {}, avg_daily, avg_w, cfg)

    if check:
        check_edges(s, cand, avg_daily, avg_w, cfg, r["epochDay"])

    age = r["epochDay"] - s["hatchDay"]
    cur = s["current"]
    if cur is None:
        if age >= EVOLVE_AGE and (len(uw) >= MIN_USABLE or age >= FALLBACK_AGE):
            s["current"] = cand
            s["lastEvolutionDay"] = r["epochDay"]
            s["pendingEvent"] = {"kind": "evolved", "from": None, "to": cand["form"], "epochDay": r["epochDay"]}
    else:
        if cand["form"] == cur["form"]:
            s["current"] = cand                           # Hybrid und Branch nachziehen
            s["challenger"], s["challengerSince"] = None, None
        else:
            cur = dict(cur, branch=cand["branch"])        # nur den Gesundheits-Zweig aktualisieren
            s["current"] = cur
            if s["challenger"] != cand["form"]:
                s["challenger"], s["challengerSince"] = cand["form"], r["epochDay"]
            stable = r["epochDay"] - s["challengerSince"] >= CHALLENGER_DAYS - 1
            interval_ok = s["lastEvolutionDay"] is None or r["epochDay"] - s["lastEvolutionDay"] >= META_INTERVAL
            margin = group_margin(s["blended"], cur["form"], cand["form"])
            if stable and interval_ok and (margin is None or margin >= MARGIN):
                s["pendingEvent"] = {"kind": "metamorphosis", "from": cur["form"], "to": cand["form"],
                                     "epochDay": r["epochDay"]}
                s["current"] = cand
                s["lastEvolutionDay"] = r["epochDay"]
                s["challenger"], s["challengerSince"] = None, None
    return s


def acknowledge(s):
    s = copy.deepcopy(s)
    s["pendingEvent"] = None
    return s


# ---------------------------------------------------------------- Schwellen-Abstände (nur Generator)

def check_edges(s, cand, avg_daily, avg_w, cfg, day):
    b = s["blended"] or {}
    tol = 0.0005   # Float32-Fehler liegen bei ca. 1e-6, 5e-4 ist reichlich Sicherheitsabstand
    for grp, mn in (("social", 0.35), ("entertainment", 0.35), ("games", 0.30), ("productivity", 0.30)):
        if abs(b.get(grp, 0.0) - mn) < tol:
            PROBLEMS.append(f"Tag {day}: Anteil {grp}={b.get(grp, 0.0):.4f} nahe Schwelle {mn}")
    top = max([v for k, v in b.items() if k != "other"], default=0.0)
    if abs(top - 0.30) < tol:
        PROBLEMS.append(f"Tag {day}: Top-Anteil {top:.4f} nahe 0,30")
    shares = sorted([b.get(k, 0.0) for k in ("social", "entertainment", "games", "productivity")], reverse=True)
    if abs((shares[0] - shares[1]) - 0.05) < tol:
        PROBLEMS.append(f"Tag {day}: Abstand Platz 1/2 nahe 0,05")
    cur = s["current"]
    if cur and cand["form"] != cur["form"]:
        m = group_margin(b, cur["form"], cand["form"])
        if m is not None and abs(m - MARGIN) < tol:
            PROBLEMS.append(f"Tag {day}: Vorsprung {m:.4f} nahe 0,10")
    if abs(avg_daily - cfg["screenGoalMin"]) < 0.5:
        PROBLEMS.append(f"Tag {day}: Ø Minuten {avg_daily:.2f} nahe Ziel")
    for edge in (65.0, 40.0):
        if abs(avg_w - edge) < 0.05:
            PROBLEMS.append(f"Tag {day}: Ø Wohlbefinden {avg_w:.3f} nahe {edge}")


# ---------------------------------------------------------------- Szenarien

def rec(day, shares, total, w):
    return {"epochDay": day, "shares": shares, "totalMinutes": total, "avgWellbeing": w}


SOCIAL = {"social": 0.55, "entertainment": 0.15, "games": 0.05, "productivity": 0.05, "other": 0.20}
PRODUCT = {"social": 0.10, "entertainment": 0.10, "games": 0.05, "productivity": 0.60, "other": 0.15}
BALANCED = {"social": 0.25, "entertainment": 0.25, "games": 0.20, "productivity": 0.20, "other": 0.10}
CLOSE = {"social": 0.40, "entertainment": 0.37, "games": 0.05, "productivity": 0.05, "other": 0.13}


def summarize(s):
    return {
        "current": s["current"],
        "lastEvolutionDay": s["lastEvolutionDay"],
        "challenger": s["challenger"],
        "challengerSince": s["challengerSince"],
        "pendingEvent": s["pendingEvent"],
        "blended": s["blended"],
        "historySize": len(s["history"]),
    }


def run(name, hatch, records, cfg=None, acks=()):
    cfg = cfg or g.DEF_CFG
    s = new_state(hatch)
    steps = []
    for r in records:
        s = close_day(s, r, cfg)
        steps.append({"record": r, "expect": summarize(s), "ack": r["epochDay"] in acks})
        if r["epochDay"] in acks:
            s = acknowledge(s)
    return {"name": name, "hatchDay": hatch, "config": cfg, "steps": steps}, s


def scenarios():
    out = []
    # S1: Social-Kind evolviert an Tag 11 (strahlend), danach keine Änderung
    recs = [rec(d, SOCIAL, 240.0, 70.0) for d in range(0, 41)]
    sc, s = run("S1 Social-Kind: Evolution an Tag 11", 0, recs, acks={11})
    assert s["current"] == {"form": "chatterling", "hybrid": None, "branch": "radiant"}, s["current"]
    assert s["lastEvolutionDay"] == 11 and s["pendingEvent"] is None
    assert sc["steps"][10]["expect"]["current"] is None and sc["steps"][11]["expect"]["current"] is not None
    out.append(sc)

    # S2: erst Social, ab Tag 15 Produktivität: Metamorphose nach 14 Tagen Führung und 21 Tagen Abstand
    recs = [rec(d, SOCIAL if d < S2_SWITCH else PRODUCT, 240.0, 70.0) for d in range(0, 60)]
    sc, s = run("S2 Metamorphose Social zu Zenfuchs", 0, recs)
    meta = [i for i, st in enumerate(sc["steps"]) if st["expect"]["pendingEvent"]
            and st["expect"]["pendingEvent"]["kind"] == "metamorphosis"]
    assert meta, "keine Metamorphose"
    d = sc["steps"][meta[0]]["record"]["epochDay"]
    assert d >= 11 + META_INTERVAL, d
    assert s["current"]["form"] == "zenfuchs"
    out.append(sc)

    # S3: keine Bildschirmzeit-Daten: Fallback ab Tag 21 zu Wandero (Balance)
    recs = [rec(d, None, None, 60.0) for d in range(0, 30)]
    sc, s = run("S3 Ohne Nutzungsdaten: Fallback ab Tag 21", 0, recs)
    assert sc["steps"][20]["expect"]["current"] is None
    assert sc["steps"][21]["expect"]["current"] == {"form": "wandero", "hybrid": None, "branch": "standard"}
    out.append(sc)

    # S4: doppeltes/zurückliegendes Schließen wird ignoriert, Lücken sind erlaubt
    recs = [rec(d, SOCIAL, 240.0, 70.0) for d in (0, 1, 2, 3, 4, 5)]
    recs += [rec(5, PRODUCT, 100.0, 10.0), rec(3, PRODUCT, 100.0, 10.0)]            # werden ignoriert
    recs += [rec(d, SOCIAL, 240.0, 70.0) for d in (9, 10, 11, 12)]                  # Lücke 6..8
    sc, s = run("S4 Ignorieren und Lücken", 0, recs)
    assert sc["steps"][6]["expect"] == sc["steps"][5]["expect"] and sc["steps"][7]["expect"] == sc["steps"][5]["expect"]
    assert s["current"]["form"] == "chatterling"
    out.append(sc)

    # S5: Balance -> Wandero im wilden Zweig, danach Zweig-Wechsel zu strahlend ohne Metamorphose
    recs = [rec(d, BALANCED, 150.0, 30.0) for d in range(0, 20)]
    recs += [rec(d, BALANCED, 150.0, 80.0) for d in range(20, 50)]
    sc, s = run("S5 Wandero: Branch wechselt von wild zu strahlend", 0, recs)
    assert sc["steps"][11]["expect"]["current"] == {"form": "wandero", "hybrid": None, "branch": "wild"}
    assert s["current"] == {"form": "wandero", "hybrid": None, "branch": "radiant"}
    assert s["lastEvolutionDay"] == 11
    out.append(sc)

    # S6: knappes Rennen: Hybrid-Merkmal
    recs = [rec(d, CLOSE, 200.0, 50.0) for d in range(0, 20)]
    sc, s = run("S6 Hybrid Chatterling/Bingeon", 0, recs)
    assert s["current"] == {"form": "chatterling", "hybrid": "bingeon", "branch": "standard"}, s["current"]
    out.append(sc)

    # S7: Herausforderer flackert: Wechsel nach Produktivität, nach <14 Tagen zurück zu Social: keine Metamorphose
    recs = [rec(d, SOCIAL, 240.0, 70.0) for d in range(0, S7_START)]
    recs += [rec(d, PRODUCT, 240.0, 70.0) for d in range(S7_START, S7_END)]
    recs += [rec(d, SOCIAL, 240.0, 70.0) for d in range(S7_END, 70)]
    sc, s = run("S7 Flackernder Herausforderer", 0, recs)
    chal = [st["expect"]["challenger"] for st in sc["steps"]]
    assert any(c == "zenfuchs" for c in chal), "Herausforderer nie gesetzt"
    assert all(not (st["expect"]["pendingEvent"] and st["expect"]["pendingEvent"]["kind"] == "metamorphosis")
               for st in sc["steps"]), "unerwartete Metamorphose"
    assert s["current"]["form"] == "chatterling" and s["challenger"] is None
    out.append(sc)

    # S8: Metamorphose wird durch zu kleinen Vorsprung blockiert (Spiele 0,36 gegen Social 0,30)
    near = {"social": 0.30, "entertainment": 0.05, "games": 0.36, "productivity": 0.05, "other": 0.24}
    base = {"social": 0.55, "entertainment": 0.10, "games": 0.10, "productivity": 0.05, "other": 0.20}
    recs = [rec(d, base, 240.0, 70.0) for d in range(0, S8_SWITCH)]
    recs += [rec(d, near, 240.0, 70.0) for d in range(S8_SWITCH, 80)]
    sc, s = run("S8 Vorsprung unter 10 Prozentpunkten blockiert", 0, recs)
    assert s["current"]["form"] == "chatterling", s["current"]
    out.append(sc)

    return out


def main():
    scs = scenarios()
    if PROBLEMS:
        uniq = sorted(set(PROBLEMS))
        raise SystemExit("Szenarien zu nah an Schwellen:\n" + "\n".join(uniq[:30]))

    def rnd(x):
        if isinstance(x, dict):
            return {k: rnd(v) for k, v in x.items()}
        if isinstance(x, list):
            return [rnd(v) for v in x]
        if isinstance(x, float):
            return round(x, 6)
        return x

    doc = {"version": 1, "tolerance": 0.002, "scenarios": rnd(scs)}
    with open("golden_evolution.json", "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    print("OK:", [(s["name"], len(s["steps"])) for s in scs])


if __name__ == "__main__":
    main()
