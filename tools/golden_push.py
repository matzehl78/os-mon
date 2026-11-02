#!/usr/bin/env python3
"""
golden_push.py: Referenzimplementierung der PushPolicy + Generator für golden_push.json

Zweck: plattformneutrale "Wahrheit" für PushPolicy.kt und PushPolicy.swift, inklusive der Texte
(Zahlenformat, Dauer, Uhrzeit). Die Vektoren sind Abläufe aus Schritten:
  evaluate  -> erwartete Push (oder keine) samt Titel/Text/Aktionen
  sent      -> Zustand nach onSent
  ignored   -> Zustand nach onIgnored
  engaged   -> Zustand nach onEngaged
Nach jedem Schritt wird der vollständige Zustand plus Tageslimit verglichen.

Aufruf: python3 golden_push.py  -> schreibt golden_push.json
Bricht ab, wenn die Referenz von den Erwartungen aus PushPolicyTest.kt abweicht oder ein
Float-Wert zu nah an einer Rundungs- oder Auslöseschwelle liegt (Float32 gegen Double).
"""
import json
import math
import random
import struct

HOUR = 3_600_000
DAY = 24 * HOUR
TYPES = ["BATTERY_LOW", "NIGHT_USE", "STORAGE_FULL", "SCREEN_GOAL", "STEPS_PRAISE"]   # = Priorität
COOLDOWN = {"BATTERY_LOW": 6 * HOUR, "NIGHT_USE": 20 * HOUR, "STORAGE_FULL": 7 * DAY,
            "SCREEN_GOAL": 20 * HOUR, "STEPS_PRAISE": 20 * HOUR}
BASE_CAP = 3
MIN_GAP = 2 * HOUR
ACTIONS = ["Fokus 15 Min", "Später"]
PROBLEMS = []


# ---------------------------------------------------------------- Referenz

def signals(**kw):
    base = dict(batteryPct=None, charging=False, freeStorageRatio=None, screenMinutes=None,
                screenGoalMin=180.0, nightActiveMinutes=None, steps=None, stepGoal=7000)
    base.update(kw)
    return base


def state(**kw):
    base = dict(dayKey="", sentToday=0, lastSentMs={}, lastAnyMs=None, ignoredStreak=0)
    base.update(kw)
    return base


def daily_cap(s):
    return BASE_CAP - min(2, s["ignoredStreak"] // 3)


def is_quiet(m):
    return m >= 22 * 60 or m < 8 * 60


def rollover(s, day_key):
    return s if s["dayKey"] == day_key else dict(s, dayKey=day_key, sentToday=0)


def eligible(t, sig, clk):
    m = clk["minuteOfDay"]
    if t == "BATTERY_LOW":
        return (sig["batteryPct"] if sig["batteryPct"] is not None else 100.0) <= 15.0 and not sig["charging"]
    if t == "NIGHT_USE":
        return (m >= 23 * 60 + 30 or m < 3 * 60) and (sig["nightActiveMinutes"] or 0.0) >= 10.0
    if t == "STORAGE_FULL":
        return (sig["freeStorageRatio"] if sig["freeStorageRatio"] is not None else 1.0) < 0.08
    if t == "SCREEN_GOAL":
        return (sig["screenMinutes"] or 0.0) >= sig["screenGoalMin"]
    if t == "STEPS_PRAISE":
        return sig["steps"] is not None and sig["steps"] * 5 >= sig["stepGoal"] * 4
    raise ValueError(t)


def round_half_up(x):          # Kotlin roundToInt / Swift .rounded() für positive Werte
    return int(math.floor(x + 0.5))


def thousands(n):
    s = str(n)[::-1]
    return ".".join(s[i:i + 3] for i in range(0, len(s), 3))[::-1]


def duration(minutes):
    h, m = divmod(minutes, 60)
    if h == 0:
        return f"{m} Min."
    if m == 0:
        return f"{h} Std."
    return f"{h} Std. {m} Min."


def message(t, sig, clk):
    if t == "BATTERY_LOW":
        b = round_half_up(sig["batteryPct"] or 0.0)
        return t, "Gähn … 🪫", f"Ich hab nur noch {b} % Energie. Gibst du mir Strom, bevor ich einschlafe?"
    if t == "STORAGE_FULL":
        p = round_half_up((sig["freeStorageRatio"] or 0.0) * 100.0)
        return t, "Die Haut spannt! 🍔", (f"Nur noch {p} % Platz, ich passe kaum noch in mich rein. "
                                         "Misten wir 1 GB alte Videos aus?")
    if t == "SCREEN_GOAL":
        d = duration(round_half_up(sig["screenMinutes"] or 0.0))
        return t, "Meine Augen werden eckig 👀", f"{d} Bildschirm heute. Eine 10-Minuten-Pause für uns beide?"
    if t == "NIGHT_USE":
        m = clk["minuteOfDay"]
        return t, "Psst … 🌙", f"Es ist {m // 60:02d}:{m % 60:02d} und ich kann nicht schlafen. Legst du uns schlafen?"
    if t == "STEPS_PRAISE":
        n = sig["steps"] or 0
        if n >= sig["stepGoal"]:
            body = f"{thousands(n)} Schritte, Ziel geschafft! Ich bin ganz stolz auf uns."
        else:
            body = f"{thousands(n)} Schritte, wow. Noch eine Runde frische Luft, dann sind wir strahlend?"
        return t, "Ich hab Muckis! 💪", body
    raise ValueError(t)


def evaluate(st, sig, clk):
    s = rollover(st, clk["dayKey"])
    if s["sentToday"] >= daily_cap(s):
        return None
    if s["lastAnyMs"] is not None and clk["nowMs"] - s["lastAnyMs"] < MIN_GAP:
        return None
    quiet = is_quiet(clk["minuteOfDay"])
    for t in TYPES:
        if quiet and t != "NIGHT_USE":
            continue
        last = s["lastSentMs"].get(t)
        if last is not None and clk["nowMs"] - last < COOLDOWN[t]:
            continue
        if eligible(t, sig, clk):
            typ, title, body = message(t, sig, clk)
            return {"type": typ, "title": title, "body": body, "actions": ACTIONS}
    return None


def on_sent(st, t, clk):
    s = rollover(st, clk["dayKey"])
    ls = dict(s["lastSentMs"])
    ls[t] = clk["nowMs"]
    return dict(s, sentToday=s["sentToday"] + 1, lastSentMs=ls, lastAnyMs=clk["nowMs"])


def on_ignored(st):
    return dict(st, ignoredStreak=st["ignoredStreak"] + 1)


def on_engaged(st):
    return dict(st, ignoredStreak=0)


# ---------------------------------------------------------------- Uhr wie in PushPolicyTest.kt

def clk(day, hour, minute=0):
    m = hour * 60 + minute
    return {"nowMs": day * DAY + m * 60_000, "minuteOfDay": m, "dayKey": f"d{day}"}


def typ(msg):
    return msg["type"] if msg else None


# ---------------------------------------------------------------- Selbsttest gegen PushPolicyTest.kt

def selftest():
    S = state()
    c = clk(0, 14)
    assert typ(evaluate(S, signals(batteryPct=14.0), c)) == "BATTERY_LOW"
    assert evaluate(S, signals(batteryPct=14.0, charging=True), c) is None
    assert evaluate(S, signals(batteryPct=16.0), c) is None
    assert evaluate(S, signals(), c) is None
    m = evaluate(S, signals(freeStorageRatio=0.06), clk(0, 12))
    assert typ(m) == "STORAGE_FULL" and "6 %" in m["body"]
    assert evaluate(S, signals(freeStorageRatio=0.09), clk(0, 12)) is None
    m = evaluate(S, signals(screenMinutes=180.0), clk(0, 18))
    assert typ(m) == "SCREEN_GOAL" and m["body"].startswith("3 Std.")
    c = clk(0, 17)
    assert evaluate(S, signals(steps=5599), c) is None
    m = evaluate(S, signals(steps=5600), c)
    assert typ(m) == "STEPS_PRAISE" and m["body"].startswith("5.600 Schritte")
    assert "Ziel geschafft" in evaluate(S, signals(steps=7200), c)["body"]
    low = signals(batteryPct=5.0)
    assert evaluate(S, low, clk(0, 23)) is None and evaluate(S, low, clk(1, 7, 30)) is None
    assert evaluate(S, low, clk(1, 8)) is not None
    active = signals(nightActiveMinutes=12.0)
    m = evaluate(S, active, clk(0, 23, 47))
    assert typ(m) == "NIGHT_USE" and "23:47" in m["body"]
    assert evaluate(S, signals(nightActiveMinutes=5.0), clk(0, 23, 47)) is None
    assert evaluate(S, active, clk(0, 22, 45)) is None and evaluate(S, active, clk(0, 14)) is None
    assert typ(evaluate(S, active, clk(1, 0, 20))) == "NIGHT_USE"
    sig = signals(batteryPct=10.0, screenMinutes=400.0)
    assert typ(evaluate(S, sig, clk(0, 14))) == "BATTERY_LOW"
    s = on_sent(S, "BATTERY_LOW", clk(0, 14))
    assert typ(evaluate(s, sig, clk(0, 16, 30))) == "SCREEN_GOAL"
    assert evaluate(s, sig, clk(0, 15, 30)) is None and evaluate(s, sig, clk(0, 16)) is not None
    s = on_sent(S, "BATTERY_LOW", clk(0, 9))
    assert evaluate(s, signals(batteryPct=10.0), clk(0, 14)) is None
    assert typ(evaluate(s, signals(batteryPct=10.0), clk(0, 15))) == "BATTERY_LOW"
    t = on_sent(S, "STORAGE_FULL", clk(0, 12))
    assert evaluate(t, signals(freeStorageRatio=0.05), clk(3, 12)) is None
    assert typ(evaluate(t, signals(freeStorageRatio=0.05), clk(7, 12))) == "STORAGE_FULL"
    s = S
    for tt, cc in (("SCREEN_GOAL", clk(0, 9)), ("STEPS_PRAISE", clk(0, 12)), ("STORAGE_FULL", clk(0, 15))):
        s = on_sent(s, tt, cc)
    assert evaluate(s, low, clk(0, 18)) is None and evaluate(s, low, clk(1, 10)) is not None
    s = S
    assert daily_cap(s) == 3
    for _ in range(3):
        s = on_ignored(s)
    assert daily_cap(s) == 2
    for _ in range(3):
        s = on_ignored(s)
    assert daily_cap(s) == 1
    for _ in range(9):
        s = on_ignored(s)
    assert daily_cap(s) == 1 and daily_cap(on_engaged(s)) == 3
    s = on_sent(state(ignoredStreak=6), "SCREEN_GOAL", clk(0, 9))
    assert evaluate(s, low, clk(0, 14)) is None


# ---------------------------------------------------------------- Schwellen-Abstände (Float32-Sicherheit)

def f32(x):
    return struct.unpack("f", struct.pack("f", x))[0]


def risky(x, edge, eps=1e-3):
    """Nur Werte, die in Float32 nicht exakt darstellbar sind UND nahe an einer Kante liegen, sind riskant.
    Ganze Zahlen und x,5 sind exakt; dort rechnen Kotlin, Swift und Python identisch."""
    return abs(x - edge) < eps and f32(x) != x


def check_signals(label, sig):
    b = sig["batteryPct"]
    if b is not None and (risky(b, 15.0) or risky(b, math.floor(b) + 0.5)):
        PROBLEMS.append(f"{label}: batteryPct {b} nahe Schwelle/Rundung")
    f = sig["freeStorageRatio"]
    if f is not None:
        p = f * 100.0
        if abs(f - 0.08) < 1e-5 or risky(p, math.floor(p) + 0.5) or abs(f32(f32(f) * f32(100.0)) - p) > 1e-4 and abs((p % 1.0) - 0.5) < 1e-3:
            PROBLEMS.append(f"{label}: freeStorageRatio {f} nahe Schwelle/Rundung")
    sm = sig["screenMinutes"]
    if sm is not None and (risky(sm, sig["screenGoalMin"]) or risky(sm, math.floor(sm) + 0.5)):
        PROBLEMS.append(f"{label}: screenMinutes {sm} nahe Schwelle/Rundung")
    n = sig["nightActiveMinutes"]
    if n is not None and risky(n, 10.0):
        PROBLEMS.append(f"{label}: nightActiveMinutes {n} nahe 10")


# ---------------------------------------------------------------- Abläufe

class Run:
    def __init__(self, name, start=None):
        self.name = name
        self.s = start or state()
        self.steps = []
        self.initial = dict(self.s)

    def _push(self, step):
        step["expectState"] = self.s
        step["expectCap"] = daily_cap(self.s)
        self.steps.append(step)

    def ev(self, sig, c):
        check_signals(f"{self.name}[{len(self.steps)}]", sig)
        m = evaluate(self.s, sig, c)
        self._push({"op": "evaluate", "signals": sig, "clock": c, "expectMessage": m})
        return m

    def sent(self, t, c):
        self.s = on_sent(self.s, t, c)
        self._push({"op": "sent", "type": t, "clock": c})

    def ign(self):
        self.s = on_ignored(self.s)
        self._push({"op": "ignored"})

    def eng(self):
        self.s = on_engaged(self.s)
        self._push({"op": "engaged"})

    def out(self):
        return {"name": self.name, "initial": self.initial, "steps": self.steps}


def scenarios():
    out = []

    r = Run("Kotlin-Tests: Auslöser und Texte")
    for sig, c in [(signals(batteryPct=14.0), clk(0, 14)), (signals(batteryPct=14.0, charging=True), clk(0, 14)),
                   (signals(batteryPct=16.0), clk(0, 14)), (signals(), clk(0, 14)),
                   (signals(freeStorageRatio=0.06), clk(0, 12)), (signals(freeStorageRatio=0.09), clk(0, 12)),
                   (signals(screenMinutes=180.0), clk(0, 18)), (signals(steps=5599), clk(0, 17)),
                   (signals(steps=5600), clk(0, 17)), (signals(steps=7200), clk(0, 17)),
                   (signals(batteryPct=5.0), clk(0, 23)), (signals(batteryPct=5.0), clk(1, 7, 30)),
                   (signals(batteryPct=5.0), clk(1, 8)), (signals(nightActiveMinutes=12.0), clk(0, 23, 47)),
                   (signals(nightActiveMinutes=5.0), clk(0, 23, 47)), (signals(nightActiveMinutes=12.0), clk(0, 22, 45)),
                   (signals(nightActiveMinutes=12.0), clk(0, 14)), (signals(nightActiveMinutes=12.0), clk(1, 0, 20)),
                   (signals(screenMinutes=59.0), clk(0, 12)), (signals(screenMinutes=61.0, screenGoalMin=60.0), clk(0, 12)),
                   (signals(screenMinutes=245.0), clk(0, 12)), (signals(steps=1234567, stepGoal=10), clk(0, 12)),
                   (signals(batteryPct=7.4), clk(0, 12)), (signals(freeStorageRatio=0.0123), clk(0, 12))]:
        r.ev(sig, c)
    out.append(r.out())

    r = Run("Priorität, Abstand, Cooldown")
    sig = signals(batteryPct=10.0, screenMinutes=400.0)
    r.ev(sig, clk(0, 14)); r.sent("BATTERY_LOW", clk(0, 14))
    r.ev(sig, clk(0, 15, 30)); r.ev(sig, clk(0, 16)); r.ev(sig, clk(0, 16, 30))
    r.sent("SCREEN_GOAL", clk(0, 16, 30))
    r.ev(sig, clk(0, 19)); r.ev(sig, clk(0, 20, 30))
    r.ev(signals(freeStorageRatio=0.05), clk(1, 12)); r.sent("STORAGE_FULL", clk(1, 12))
    r.ev(signals(freeStorageRatio=0.05), clk(4, 12)); r.ev(signals(freeStorageRatio=0.05), clk(8, 12))
    out.append(r.out())

    r = Run("Tageslimit, Ignorieren, Engagement")
    low = signals(batteryPct=5.0)
    r.sent("SCREEN_GOAL", clk(0, 9)); r.sent("STEPS_PRAISE", clk(0, 12)); r.sent("STORAGE_FULL", clk(0, 15))
    r.ev(low, clk(0, 18)); r.ev(low, clk(1, 10))
    for _ in range(3):
        r.ign()
    r.sent("BATTERY_LOW", clk(1, 10)); r.sent("SCREEN_GOAL", clk(1, 13))
    r.ev(signals(steps=6000), clk(1, 16))
    for _ in range(3):
        r.ign()
    r.ev(signals(steps=6000), clk(2, 9)); r.sent("STEPS_PRAISE", clk(2, 9)); r.ev(low, clk(2, 12))
    r.eng()
    r.ev(low, clk(2, 12))
    out.append(r.out())

    r = Run("Nacht: Ruhezeit und Ausnahme", state(ignoredStreak=4))
    act = signals(nightActiveMinutes=25.0, batteryPct=9.0)
    r.ev(act, clk(0, 23, 10)); r.ev(act, clk(0, 23, 40)); r.sent("NIGHT_USE", clk(0, 23, 40))
    r.ev(act, clk(1, 2, 0)); r.ev(act, clk(1, 8, 5)); r.sent("BATTERY_LOW", clk(1, 8, 5))
    r.ev(act, clk(1, 23, 50)); r.ev(act, clk(2, 0, 30))
    out.append(r.out())

    # Zufallsabläufe mit festem Seed: decken Kombinationen ab, die keiner von Hand schreibt.
    rng = random.Random(7)
    for k in range(6):
        r = Run(f"Zufall {k}", state(ignoredStreak=rng.choice([0, 0, 2, 5])))
        now = rng.randint(0, 2) * DAY + rng.randint(0, 1439) * 60_000
        for _ in range(40):
            now += rng.choice([15, 30, 45, 60, 90, 120, 180, 300]) * 60_000
            day, rem = divmod(now, DAY)
            c = {"nowMs": now, "minuteOfDay": rem // 60_000, "dayKey": f"d{day}"}
            sig = signals(
                batteryPct=None if rng.random() < 0.15 else round(rng.uniform(1, 100), 1) + 0.2,
                charging=rng.random() < 0.25,
                freeStorageRatio=None if rng.random() < 0.2 else round(rng.uniform(0.01, 0.4), 3) + 0.0011,
                screenMinutes=None if rng.random() < 0.2 else float(rng.randint(0, 420)),
                screenGoalMin=rng.choice([120.0, 180.0, 240.0]),
                nightActiveMinutes=None if rng.random() < 0.5 else float(rng.choice([0, 5, 9, 11, 30])),
                steps=None if rng.random() < 0.2 else rng.randint(0, 12000),
                stepGoal=rng.choice([5000, 7000, 10000]))
            m = r.ev(sig, c)
            if m and rng.random() < 0.8:
                r.sent(m["type"], c)
                x = rng.random()
                if x < 0.4:
                    r.ign()
                elif x < 0.6:
                    r.eng()
        out.append(r.out())
    return out


def main():
    selftest()
    scs = scenarios()
    if PROBLEMS:
        raise SystemExit("Werte zu nah an Schwellen:\n" + "\n".join(PROBLEMS[:30]))
    doc = {"version": 1, "scenarios": scs}
    with open("golden_push.json", "w", encoding="utf-8") as f:
        json.dump(doc, f, ensure_ascii=False, separators=(",", ":"))
    sent = sum(1 for sc in scs for st in sc["steps"] if st["op"] == "evaluate" and st["expectMessage"])
    print("OK:", len(scs), "Abläufe,", sum(len(sc["steps"]) for sc in scs), "Schritte,", sent, "ausgelöste Pushes")


if __name__ == "__main__":
    main()
