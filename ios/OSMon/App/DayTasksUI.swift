// DayTasksUI.swift: Aufgabenliste mit Motivation und „Miteinander“ (Verhaltenstipps). Gegenstück: DayTasksUI.kt.
//
// Startseite: Karte „Meine Aufgaben“ (Fortschritt, Satz vom Wesen, bis zu 4 Aufgaben zum Abhaken) und Karte
// „Miteinander“ (Tipp des Tages, „Hab ich gemacht“, als Aufgabe übernehmen). Sheets zum Verwalten und für alle
// Tipps. Regeln: Shared/Logic/DayTasks.swift, Texte: tasks.json (gleiche Datei wie Android). Nur auf dem Gerät.

import SwiftUI

// MARK: - Inhalte

struct TaskContent: Decodable {
    struct Idea: Decodable, Hashable { let emoji, title: String }
    struct Group: Decodable, Hashable { let group: String; let items: [Idea] }
    struct Motivation: Decodable { let empty, start, going, half, all: [String] }
    struct Tip: Decodable, Hashable { let tip, task: String }
    struct Category: Decodable, Identifiable, Hashable { let id, emoji, title: String; let tips: [Tip] }

    let suggestions: [Group]
    let motivation: Motivation
    let done: [String]
    let kindness: [Category]

    static let shared: TaskContent = {
        guard let url = Bundle.main.url(forResource: "tasks", withExtension: "json"),
              let data = try? Data(contentsOf: url),
              let c = try? JSONDecoder().decode(TaskContent.self, from: data) else {
            return TaskContent(suggestions: [], motivation: Motivation(empty: [], start: [], going: [], half: [], all: []),
                               done: ["Super!"], kindness: [])
        }
        return c
    }()

    var allTips: [(Category, Tip)] { kindness.flatMap { c in c.tips.map { (c, $0) } } }
    func tipOfTheDay(_ day: Int) -> (Category, Tip)? { DayTasks.pick(allTips, day: day, salt: 5) }

    func lines(_ s: TaskStage) -> [String] {
        switch s {
        case .empty: return motivation.empty
        case .start: return motivation.start
        case .going: return motivation.going
        case .half: return motivation.half
        case .all: return motivation.all
        }
    }

    func motivation(_ p: TaskProgress, day: Int, pet: String) -> String {
        DayTasks.pick(lines(p.stage), day: day, salt: p.done).map { DayTasks.fill($0, pet: pet) } ?? ""
    }
}

// MARK: - Speicher

enum TaskStore {
    static let key = "tasks.list"
    private static var d: UserDefaults { STShared.defaults }
    static var today: Int { EpochDay.of(.now) }

    static func load() -> [DayTask] {
        guard let data = d.data(forKey: key), let l = try? JSONDecoder().decode([DayTask].self, from: data) else { return [] }
        return DayTasks.cleanup(l, today: today)
    }

    static func save(_ l: [DayTask]) {
        if let data = try? JSONEncoder().encode(l) { d.set(data, forKey: key) }
    }

    static func newTask(_ title: String, emoji: String, rule: TaskRepeat) -> DayTask {
        DayTask(id: UUID().uuidString, title: String(title.trimmingCharacters(in: .whitespacesAndNewlines).prefix(40)),
                emoji: emoji, repeatRule: rule, createdDay: today)
    }

    // Miteinander: Tipp des Tages ausprobiert (pro Tag) und Anzahl insgesamt
    static func kindDone(_ day: Int) -> Bool { d.bool(forKey: "kind.done.\(day)") }
    static var kindCount: Int { d.integer(forKey: "kind.count") }
    static func markKind(_ day: Int) {
        guard !kindDone(day) else { return }
        d.set(true, forKey: "kind.done.\(day)")
        d.set(kindCount + 1, forKey: "kind.count")
    }
}

@Observable
final class TaskModel {
    private(set) var tasks: [DayTask] = TaskStore.load()
    var cheer: String? = nil
    var day: Int { TaskStore.today }

    init() {
        // Auf der Uhr abgehakt → neu laden
        NotificationCenter.default.addObserver(forName: .osmonTasksChanged, object: nil, queue: .main) { [weak self] _ in
            self?.tasks = TaskStore.load()
        }
    }

    func update(_ l: [DayTask]) { tasks = l; TaskStore.save(l); PhoneWatchBridge.shared.push() }

    @discardableResult
    func add(_ title: String, emoji: String, rule: TaskRepeat) -> Bool {
        guard DayTasks.canAdd(tasks, day, title: title) else { return false }
        update(tasks + [TaskStore.newTask(title, emoji: emoji, rule: rule)])
        return true
    }

    func remove(_ t: DayTask) { update(tasks.filter { $0.id != t.id }) }

    /// true, wenn damit alles erledigt ist.
    func toggle(_ t: DayTask) -> Bool {
        let wasDone = DayTasks.isDone(t, day)
        if !wasDone { AchievementStore.addTask() }            // Sammelalbum
        update(tasks.map { $0.id == t.id ? DayTasks.toggle($0, day) : $0 })
        return !wasDone && DayTasks.progress(tasks, day).stage == .all
    }

    func has(_ title: String) -> Bool { tasks.contains { $0.title == title && DayTasks.isDue($0, day) } }
}

// MARK: - Startseite

/// Beide Karten der Startseite. onCheer(alles erledigt, Satz) lässt das Wesen reagieren.
struct TasksSection: View {
    let onCheer: (Bool, String) -> Void
    @State private var model = TaskModel()
    @State private var sheet: Sheet? = nil
    private let content = TaskContent.shared
    private var pet: String { PetFigureStore.current.name }

    enum Sheet: String, Identifiable { case manage, add, kindness; var id: String { rawValue } }

    var body: some View {
        VStack(spacing: 10) {
            TasksCard(model: model, content: content, pet: pet, onToggle: toggle,
                      onManage: { sheet = .manage }, onAdd: { sheet = .add })
            KindnessCard(model: model, content: content, pet: pet, onCheer: { onCheer(false, $0) },
                         onShowAll: { sheet = .kindness })
        }
        .sheet(item: $sheet) { s in
            switch s {
            case .manage, .add: TasksManageView(model: model, content: content, adding: s == .add, onToggle: toggle)
            case .kindness: KindnessListView(model: model, content: content)
            }
        }
        .sensoryFeedback(.success, trigger: model.tasks.map(\.doneDays.count).reduce(0, +))
    }

    private func toggle(_ t: DayTask) {
        let nowDone = !DayTasks.isDone(t, model.day)
        let all = model.toggle(t)
        guard nowDone else { model.cheer = nil; return }
        let line: String
        if all {
            line = content.motivation(DayTasks.progress(model.tasks, model.day), day: model.day, pet: pet)
        } else {
            let n = model.tasks.map(\.doneDays.count).reduce(0, +)
            line = DayTasks.pick(content.done, day: model.day, salt: n).map { DayTasks.fill($0, pet: pet, task: t.title) } ?? ""
        }
        model.cheer = line
        onCheer(all, line)
    }
}

private struct TasksCard: View {
    let model: TaskModel
    let content: TaskContent
    let pet: String
    let onToggle: (DayTask) -> Void
    let onManage: () -> Void
    let onAdd: () -> Void

    var body: some View {
        let day = model.day
        let list = DayTasks.forDay(model.tasks, day)
        let p = DayTasks.progress(model.tasks, day)
        let streak = DayTasks.streak(model.tasks, today: day)
        VStack(alignment: .leading, spacing: 8) {
            HStack {
                Label("Meine Aufgaben", systemImage: "checklist").font(.headline)
                Spacer()
                if streak >= 2 {
                    Text("🔥 \(streak) Tage").font(.subheadline.weight(.semibold)).foregroundStyle(.orange)
                        .accessibilityLabel("\(streak) Tage am Stück alles geschafft")
                }
                if p.total > 0 { Text("\(p.done)/\(p.total)").font(.subheadline.bold()).monospacedDigit() }
            }
            Text("\(pet): \(model.cheer ?? content.motivation(p, day: day, pet: pet))")
                .font(.subheadline).foregroundStyle(.secondary)
                .fixedSize(horizontal: false, vertical: true)
            if p.total > 0 {
                ProgressView(value: p.fraction).tint(p.stage == .all ? .green : .teal)
            }
            ForEach(list.prefix(4)) { TaskRow(task: $0, day: day, onToggle: onToggle) }
            if list.count > 4 {
                Text("+ \(list.count - 4) weitere").font(.caption).foregroundStyle(.secondary)
            }
            HStack {
                Button(action: onAdd) { Label("Aufgabe", systemImage: "plus") }.buttonStyle(.bordered)
                if !model.tasks.isEmpty { Button("Alle verwalten", action: onManage).buttonStyle(.borderless) }
            }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
    }
}

private struct TaskRow: View {
    let task: DayTask
    let day: Int
    let onToggle: (DayTask) -> Void

    private var subtitle: String? {
        if DayTasks.isCarried(task, day) { return "von gestern" }
        if task.isFromParent { return "👪 von den Eltern" }
        switch task.repeatRule {
        case .daily: return "täglich"
        case .weekdays: return "Mo–Fr"
        case .weekend: return "am Wochenende"
        case .once: return nil
        }
    }

    var body: some View {
        let done = DayTasks.isDone(task, day)
        Button { onToggle(task) } label: {
            HStack(spacing: 10) {
                Image(systemName: done ? "checkmark.circle.fill" : "circle")
                    .font(.title3).foregroundStyle(done ? Color.green : Color.secondary)
                Text(task.emoji)
                VStack(alignment: .leading, spacing: 1) {
                    Text(task.title).strikethrough(done).foregroundStyle(done ? .secondary : .primary)
                    if let s = subtitle {
                        Text(s).font(.caption2).foregroundStyle(s == "von gestern" ? Color.orange : Color.secondary)
                    }
                }
                Spacer(minLength: 0)
            }
            .contentShape(Rectangle())
        }
        .buttonStyle(.plain)
        .accessibilityValue(done ? "erledigt" : "offen")
        .accessibilityAddTraits(done ? .isSelected : [])
    }
}

private struct KindnessCard: View {
    let model: TaskModel
    let content: TaskContent
    let pet: String
    let onCheer: (String) -> Void
    let onShowAll: () -> Void
    @State private var done = TaskStore.kindDone(TaskStore.today)
    @State private var count = TaskStore.kindCount

    var body: some View {
        if let pair = content.tipOfTheDay(model.day) {
            let cat = pair.0, tip = pair.1
            VStack(alignment: .leading, spacing: 6) {
                HStack {
                    Label("Miteinander", systemImage: "heart.fill").font(.caption.weight(.semibold)).foregroundStyle(.pink)
                    Spacer()
                    Text("\(cat.emoji) \(cat.title)").font(.caption2).foregroundStyle(.secondary)
                }
                Text(tip.tip).font(.body.weight(.medium)).fixedSize(horizontal: false, vertical: true)
                if count > 0 {
                    Text("\(count) gute Taten bisher – \(pet) ist stolz auf dich.").font(.caption2).foregroundStyle(.secondary)
                }
                HStack {
                    if done {
                        Label("Heute ausprobiert", systemImage: "checkmark.circle.fill").font(.subheadline.weight(.semibold)).foregroundStyle(.green)
                    } else {
                        Button("Hab ich gemacht") {
                            TaskStore.markKind(model.day); done = true; count = TaskStore.kindCount
                            onCheer("Danke! Die Welt ist ein bisschen netter geworden. 💛")
                        }
                        .buttonStyle(.borderedProminent).tint(.pink)
                        if !model.has(tip.task) {
                            Button("Als Aufgabe") { model.add(tip.task, emoji: "💛", rule: .once) }.buttonStyle(.borderless)
                        }
                    }
                    Spacer()
                    Button("Alle Tipps", action: onShowAll).buttonStyle(.borderless)
                }
            }
            .padding(14)
            .frame(maxWidth: .infinity, alignment: .leading)
            .background(Color.pink.opacity(0.12), in: RoundedRectangle(cornerRadius: 18))
            .background(.regularMaterial, in: RoundedRectangle(cornerRadius: 18))
        }
    }
}

// MARK: - Verwalten

private let taskEmojis = ["⭐", "📚", "🧹", "🪥", "💧", "🍎", "🌳", "🏃", "🎒", "🐾", "🎨", "🎵", "📞", "💛", "🧘", "📵"]

private struct ChipLabel: View {
    let text: String
    var selected = false
    var body: some View {
        Text(text).font(.subheadline)
            .padding(.horizontal, 12).padding(.vertical, 7)
            .background(selected ? Color.accentColor.opacity(0.18) : Color(.secondarySystemBackground), in: Capsule())
            .overlay(Capsule().stroke(selected ? Color.accentColor : .clear, lineWidth: 1))
    }
}

struct TasksManageView: View {
    let model: TaskModel
    let content: TaskContent
    var adding = false
    let onToggle: (DayTask) -> Void
    @Environment(\.dismiss) private var dismiss
    @State private var title = ""
    @State private var emoji = "⭐"
    @State private var rule: TaskRepeat = .once
    @State private var full = false
    @FocusState private var focused: Bool

    private let rules: [(TaskRepeat, String)] = [(.once, "Einmal"), (.daily, "Täglich"), (.weekdays, "Mo–Fr"), (.weekend, "Wochenende")]

    var body: some View {
        NavigationStack {
            Form {
                Section("Neue Aufgabe") {
                    TextField("Was möchtest du heute schaffen?", text: $title)
                        .focused($focused)
                        .onChange(of: title) { if title.count > 40 { title = String(title.prefix(40)) }; full = false }
                        .submitLabel(.done).onSubmit(add)
                    chips(taskEmojis.map { ($0, $0) }, selected: emoji) { emoji = $0 }
                    chips(rules.map { ($0.1, $0.0.rawValue) }, selected: rule.rawValue) { rule = TaskRepeat(rawValue: $0) ?? .once }
                    Button("Hinzufügen", action: add).disabled(title.trimmingCharacters(in: .whitespaces).isEmpty)
                    if full {
                        Text("Höchstens \(DayTasks.maxTasks) Aufgaben pro Tag – lieber weniger und dafür geschafft!")
                            .font(.footnote).foregroundStyle(.orange)
                    }
                }
                let day = model.day
                let list = DayTasks.forDay(model.tasks, day)
                if !list.isEmpty {
                    Section("Heute") {
                        ForEach(list) { TaskRow(task: $0, day: day, onToggle: onToggle) }
                            .onDelete { idx in idx.map { list[$0] }.filter { !$0.isFromParent }.forEach(model.remove) }
                    }
                }
                let later = model.tasks.filter { !DayTasks.isDue($0, day) && $0.repeatRule != .once }
                if !later.isEmpty {
                    Section("An anderen Tagen") {
                        ForEach(later) { TaskRow(task: $0, day: day, onToggle: { _ in }) }
                            .onDelete { idx in idx.map { later[$0] }.filter { !$0.isFromParent }.forEach(model.remove) }
                    }
                }
                Section {
                    ForEach(content.suggestions, id: \.self) { g in
                        VStack(alignment: .leading, spacing: 6) {
                            Text(g.group).font(.caption.weight(.semibold)).foregroundStyle(.secondary)
                            chips(g.items.map { ("\($0.emoji) \($0.title)", $0.title) }, selected: nil) { t in
                                if let i = g.items.first(where: { $0.title == t }) { title = i.title; emoji = i.emoji }
                            }
                        }
                    }
                } header: { Text("Ideen") } footer: { Text("Zum Löschen eine Aufgabe nach links wischen.") }
            }
            .navigationTitle(adding ? "Neue Aufgabe" : "Meine Aufgaben")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
            .onAppear { if adding { focused = true } }
        }
    }

    private func add() {
        if model.add(title, emoji: emoji, rule: rule) { title = ""; emoji = "⭐"; rule = .once }
        else { full = !title.trimmingCharacters(in: .whitespaces).isEmpty }
    }

    /// Waagerecht scrollbare Auswahl: (Anzeige, Wert)
    private func chips(_ items: [(String, String)], selected: String?, onPick: @escaping (String) -> Void) -> some View {
        ScrollView(.horizontal, showsIndicators: false) {
            HStack(spacing: 6) {
                ForEach(items.indices, id: \.self) { i in
                    let item = items[i]
                    Button { onPick(item.1) } label: { ChipLabel(text: item.0, selected: item.1 == selected) }
                        .buttonStyle(.plain)
                        .accessibilityAddTraits(item.1 == selected ? .isSelected : [])
                }
            }
        }
    }
}

// MARK: - Alle Tipps

struct KindnessListView: View {
    let model: TaskModel
    let content: TaskContent
    @Environment(\.dismiss) private var dismiss
    @State private var open: String? = nil

    var body: some View {
        NavigationStack {
            List {
                Section {
                    Text("Kleine Regeln, die das Zusammenleben schöner machen. Such dir einen Tipp aus und probier ihn heute aus.")
                        .font(.subheadline).foregroundStyle(.secondary)
                }
                ForEach(content.kindness) { c in
                    Section {
                        DisclosureGroup(isExpanded: Binding(get: { open == c.id }, set: { open = $0 ? c.id : nil })) {
                            ForEach(c.tips, id: \.self) { t in tipRow(t) }
                        } label: {
                            Label { Text(c.title).font(.headline) } icon: { Text(c.emoji) }
                        }
                    }
                }
            }
            .navigationTitle("Miteinander")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
            .onAppear { if open == nil { open = content.kindness.first?.id } }
        }
    }

    private func tipRow(_ t: TaskContent.Tip) -> some View {
        VStack(alignment: .leading, spacing: 4) {
            Text(t.tip).font(.subheadline)
            HStack {
                Spacer()
                if model.has(t.task) {
                    Label("in deinen Aufgaben", systemImage: "checkmark").font(.caption).foregroundStyle(.green)
                } else {
                    Button("+ Als Aufgabe für heute") { model.add(t.task, emoji: "💛", rule: .once) }
                        .font(.caption).buttonStyle(.borderless)
                }
            }
        }
        .padding(.vertical, 2)
    }
}

// MARK: - Vorführmodus (Screenshots)

enum TaskDemo {
    static func seed() {
        let d = TaskStore.today
        func t(_ title: String, _ e: String, _ r: TaskRepeat, _ created: Int, done: Bool) -> DayTask {
            DayTask(id: UUID().uuidString, title: title, emoji: e, repeatRule: r, createdDay: created, doneDays: done ? [d] : [])
        }
        TaskStore.save([
            DayTask(id: UUID().uuidString, title: "Zähne putzen", emoji: "🪥", repeatRule: .daily, createdDay: d - 4,
                    doneDays: [d, d - 1, d - 2, d - 3, d - 4]),
            t("Hausaufgaben machen", "📚", .weekdays, d - 4, done: true),
            t("Zimmer aufräumen", "🧹", .once, d - 1, done: false),
            t("Eine Stunde nach draußen", "🌳", .once, d, done: false),
            t("Oma anrufen", "📞", .once, d, done: false)
        ])
    }
}
