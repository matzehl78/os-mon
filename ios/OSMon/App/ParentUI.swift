// ParentUI.swift: Eltern-Bereich mit PIN. Gegenstück: ParentUI.kt.
// PIN (nur als Hash, Shared/Logic/ParentLock.swift), Wochenübersicht, Aufgaben fürs Kind (kann das Kind nicht löschen),
// Einstellungen sperren (Ziele, Abend und Morgen, Daten löschen). Nach dem Entsperren 5 Minuten offen.

import SwiftUI

enum ParentStore {
    private static var d: UserDefaults { STShared.defaults }
    private static var unlockedUntil = Date.distantPast        // nur im Speicher: nach Neustart wieder gesperrt

    static var hasPin: Bool { d.string(forKey: "parent.hash") != nil }
    static var isUnlocked: Bool { Date() < unlockedUntil }
    static func lockNow() { unlockedUntil = .distantPast }

    static var lockSettings: Bool {
        get { d.object(forKey: "parent.lockSettings") as? Bool ?? true }
        set { d.set(newValue, forKey: "parent.lockSettings") }
    }
    static var settingsLocked: Bool { hasPin && lockSettings && !isUnlocked }

    static func setPin(_ pin: String) {
        var bytes = [UInt8](repeating: 0, count: 16)
        _ = SecRandomCopyBytes(kSecRandomDefault, bytes.count, &bytes)
        let salt = bytes.map { String(format: "%02x", $0) }.joined()
        d.set(salt, forKey: "parent.salt"); d.set(ParentLock.hash(pin, salt: salt), forKey: "parent.hash")
        d.set(0, forKey: "parent.failures"); d.removeObject(forKey: "parent.waitUntil")
        unlockedUntil = Date().addingTimeInterval(ParentLock.sessionSeconds)
    }

    static func removePin() {
        ["parent.salt", "parent.hash", "parent.failures", "parent.waitUntil"].forEach(d.removeObject(forKey:))
        lockNow()
    }

    enum Result: Equatable { case ok, wrong(left: Int), wait(seconds: Int) }

    static func tryUnlock(_ pin: String) -> Result {
        let now = Date()
        if let until = d.object(forKey: "parent.waitUntil") as? Date, now < until { return .wait(seconds: Int(until.timeIntervalSince(now).rounded(.up))) }
        if ParentLock.verify(pin, salt: d.string(forKey: "parent.salt") ?? "", stored: d.string(forKey: "parent.hash") ?? "") {
            d.set(0, forKey: "parent.failures"); unlockedUntil = now.addingTimeInterval(ParentLock.sessionSeconds)
            return .ok
        }
        let f = d.integer(forKey: "parent.failures") + 1
        let w = ParentLock.wait(f)
        d.set(f, forKey: "parent.failures")
        if w > 0 { d.set(now.addingTimeInterval(w), forKey: "parent.waitUntil"); return .wait(seconds: Int(w)) }
        return .wrong(left: ParentLock.maxTries - f)
    }

    @discardableResult
    static func addTask(_ title: String, rule: TaskRepeat) -> Bool {
        let list = TaskStore.load()
        guard DayTasks.canAdd(list, TaskStore.today, title: title) else { return false }
        var t = TaskStore.newTask(title, emoji: "👪", rule: rule); t.fromParent = true
        TaskStore.save(list + [t])
        NotificationCenter.default.post(name: .osmonTasksChanged, object: nil)
        PhoneWatchBridge.shared.push()
        return true
    }

    static func removeTask(_ t: DayTask) {
        TaskStore.save(TaskStore.load().filter { $0.id != t.id })
        NotificationCenter.default.post(name: .osmonTasksChanged, object: nil)
        PhoneWatchBridge.shared.push()
    }

    struct DayRow: Identifiable { let day: Int; let wellbeing: Float?; let done: Int; let due: Int; let goodnight: Bool; let rough: Int; var id: Int { day } }

    static func week() -> [DayRow] {
        let today = TaskStore.today, tasks = TaskStore.load()
        return (0...6).reversed().map { today - $0 }.map { d in
            let p = DayTasks.progress(tasks, d)
            return DayRow(day: d, wellbeing: DayLog.average(day: d), done: p.done, due: p.total,
                          goodnight: SleepStore.saidGoodnight(night: d), rough: GrowthStore.rough(d))
        }
    }
}

/// Hinweis statt gesperrter Einstellungen.
struct LockedByParentRow: View {
    let title: String
    let onUnlock: () -> Void
    var body: some View {
        HStack {
            Label("\(title) ist durch den Eltern-PIN geschützt.", systemImage: "lock.fill").font(.subheadline)
            Spacer()
            Button("Entsperren", action: onUnlock).buttonStyle(.borderless)
        }
    }
}

/// PIN festlegen / eingeben, danach der Eltern-Bereich.
struct ParentView: View {
    @Environment(\.dismiss) private var dismiss
    @State private var version = 0

    var body: some View {
        NavigationStack {
            Group {
                if !ParentStore.hasPin { PinSetupView { version += 1 } }
                else if !ParentStore.isUnlocked { PinEntryView { version += 1 } }
                else { ParentContentView { version += 1 } }
            }
            .id(version)
            .navigationTitle("Eltern-Bereich")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        }
    }
}

private struct PinSetupView: View {
    let onDone: () -> Void
    @State private var a = ""
    @State private var b = ""
    @State private var err: String?

    var body: some View {
        Form {
            Section {
                SecureField("PIN (4 Ziffern)", text: $a).keyboardType(.numberPad).onChange(of: a) { a = String(a.filter(\.isNumber).prefix(4)); err = nil }
                SecureField("PIN wiederholen", text: $b).keyboardType(.numberPad).onChange(of: b) { b = String(b.filter(\.isNumber).prefix(4)); err = nil }
                if let err { Text(err).font(.footnote).foregroundStyle(.red) }
                Button("PIN speichern") {
                    if !ParentLock.isValidPin(a) { err = "Bitte genau 4 Ziffern." }
                    else if ParentLock.isTooSimple(a) { err = "Zu einfach (z. B. 1111 oder 1234). Bitte einen anderen PIN." }
                    else if a != b { err = "Die beiden PINs sind verschieden." }
                    else { ParentStore.setPin(a); onDone() }
                }
                .disabled(a.count < 4 || b.count < 4)
            } footer: {
                Text("Mit dem PIN schützt du Ziele, Schlafenszeit und das Löschen der Daten. Den PIN kennt nur ihr Eltern.")
            }
        }
    }
}

private struct PinEntryView: View {
    let onDone: () -> Void
    @State private var pin = ""
    @State private var msg: String?

    var body: some View {
        Form {
            Section {
                SecureField("PIN", text: $pin).keyboardType(.numberPad).onChange(of: pin) { pin = String(pin.filter(\.isNumber).prefix(4)); msg = nil }
                if let msg { Text(msg).font(.footnote).foregroundStyle(.red) }
                Button("Entsperren") {
                    switch ParentStore.tryUnlock(pin) {
                    case .ok: onDone()
                    case .wrong(let left): msg = "Falscher PIN. Noch \(left) Versuche."
                    case .wait(let s): msg = "Zu viele Versuche. Bitte \(s) Sekunden warten."
                    }
                    pin = ""
                }
                .disabled(pin.count < 4)
            } footer: {
                Text("PIN vergessen? App löschen und neu installieren setzt alles zurück.")
            }
        }
    }
}

private struct ParentContentView: View {
    let onChanged: () -> Void
    @State private var tasks = TaskStore.load().filter(\.isFromParent)
    @State private var title = ""
    @State private var rule: TaskRepeat = .daily
    @State private var lock = ParentStore.lockSettings
    @State private var gameLimit = GameStore.limit
    private let week = ParentStore.week()
    private let rules: [(TaskRepeat, String)] = [(.once, "Einmal"), (.daily, "Täglich"), (.weekdays, "Mo–Fr"), (.weekend, "Wochenende")]

    var body: some View {
        Form {
            Section {
                ForEach(week) { r in row(r) }
            } header: { Text("Diese Woche") } footer: {
                Text("Balken = Wohlbefinden im Tagesmittel · ✅ Aufgaben · 🌙 Gute Nacht gesagt · ✋ grob behandelt")
            }
            Section {
                TextField("z. B. Zähne putzen", text: $title).onChange(of: title) { if title.count > 40 { title = String(title.prefix(40)) } }
                Picker("Wiederholen", selection: $rule) { ForEach(rules.indices, id: \.self) { i in Text(rules[i].1).tag(rules[i].0) } }
                Button("Aufgabe stellen") {
                    if ParentStore.addTask(title, rule: rule) { title = ""; tasks = TaskStore.load().filter(\.isFromParent) }
                }
                .disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                ForEach(tasks) { t in Text("👪  \(t.title)") }
                    .onDelete { idx in idx.map { tasks[$0] }.forEach(ParentStore.removeTask); tasks = TaskStore.load().filter(\.isFromParent) }
            } header: { Text("Aufgaben fürs Kind") } footer: {
                Text("Das Kind sieht diese Aufgaben mit „von den Eltern“, kann sie abhaken, aber nicht löschen. Löschen: nach links wischen.")
            }
            Section {
                Picker("Runden pro Tag", selection: $gameLimit) {
                    Text("Aus").tag(0)
                    ForEach(1...MiniGames.maxLimit, id: \.self) { n in Text("\(n)×").tag(n) }
                }
                .pickerStyle(.segmented)
                .onChange(of: gameLimit) { _, n in GameStore.setLimit(n) }
            } header: { Text("Mini-Spiele") } footer: { Text("Jede Runde dauert 30 Sekunden. Standard: 2 Runden am Tag.") }
            Section("Premium") { PremiumCard().listRowInsets(EdgeInsets()) }
            Section("Schutz") {
                Toggle("Einstellungen sperren (Ziele, Schlafenszeit, Daten löschen)", isOn: $lock)
                    .onChange(of: lock) { ParentStore.lockSettings = lock; onChanged() }
                Button("Jetzt sperren") { ParentStore.lockNow(); onChanged() }
                Button("PIN entfernen", role: .destructive) { ParentStore.removePin(); onChanged() }
            }
        }
    }

    private func row(_ r: ParentStore.DayRow) -> some View {
        let name = EpochDay.date(r.day).formatted(.dateTime.weekday(.abbreviated))
        let w = r.wellbeing
        let col: Color = w == nil ? .gray : w! >= 65 ? .green : w! >= 40 ? .orange : .red
        return HStack(spacing: 8) {
            Text(name).font(.subheadline.weight(.semibold)).frame(width: 34, alignment: .leading)
            ProgressView(value: Double(w ?? 0), total: 100).tint(col)
            Text(w.map { "\(Int($0))" } ?? "–").font(.caption).monospacedDigit().frame(width: 26)
            Text(r.due > 0 ? "✅ \(r.done)/\(r.due)" : "").font(.caption).frame(width: 56, alignment: .leading)
            Text((r.goodnight ? "🌙" : "") + (r.rough > 0 ? " ✋\(r.rough)" : "")).font(.caption)
        }
        .accessibilityElement(children: .ignore)
        .accessibilityLabel("\(name): Wohlbefinden \(w.map { "\(Int($0))" } ?? "keine Messung"), Aufgaben \(r.done) von \(r.due)\(r.goodnight ? ", Gute Nacht gesagt" : "")\(r.rough > 0 ? ", \(r.rough)× grob" : "")")
    }
}
