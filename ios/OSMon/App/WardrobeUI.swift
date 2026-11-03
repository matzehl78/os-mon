// WardrobeUI.swift: Kleiderschrank – Ansicht. Gegenstück: WardrobeUI.kt. Speicher: Shared/Sync/ExtrasStore.swift,
// Regeln: Shared/Logic/Wardrobe.swift. Getragenes gilt überall: App (2D und 3D), Widget, Uhr.

import SwiftUI
import WidgetKit

/// Knopf auf der Startseite: „👕 Kleiderschrank“ (mit „neu“, wenn etwas dazugekommen ist).
struct WardrobeButton: View {
    let version: Int
    let action: () -> Void
    var body: some View {
        let fresh = WardrobeStore.fresh.count
        Button(action: action) {
            Text("👕 Kleiderschrank" + (fresh > 0 ? " · \(fresh) neu" : "")).font(.subheadline.weight(fresh > 0 ? .bold : .regular))
        }
        .accessibilityLabel(fresh > 0 ? "Kleiderschrank, \(fresh) neue Teile" : "Kleiderschrank")
        .id(version)
    }
}

struct WardrobeView: View {
    let figureName: String
    let stage: GrowthStage?
    @Environment(\.dismiss) private var dismiss
    @State private var version = 0
    private let good = WardrobeStore.maxGoodDays
    private let premium = PremiumFlag.isPremium
    @State private var fresh = Set(WardrobeStore.fresh.map(\.id))

    var body: some View {
        let equipped = WardrobeStore.equipped
        let outfit = WardrobeStore.outfit
        NavigationStack {
            ScrollView {
                VStack(alignment: .leading, spacing: 14) {
                    PetFigureView(pose: preview(outfit)).frame(width: 200, height: 200).frame(maxWidth: .infinity)
                        .accessibilityLabel("\(figureName) mit deiner Auswahl")
                    Group {
                        if let n = Wardrobe.next(maxGoodDays: good) {
                            let m = n.goodDays - good
                            Text("Noch \(m) gute \(m == 1 ? "Tag" : "Tage") bis: \(n.emoji) \(n.title)")
                        } else { Text("Du hast alles mit guten Tagen freigeschaltet! 🎉") }
                    }
                    .font(.callout).frame(maxWidth: .infinity)
                    Text("Gute Tage sind Tage, an denen es \(figureName) gut ging. Antippen = anziehen, nochmal antippen = ausziehen. An Festtagen trägt \(figureName) das Fest-Zubehör.")
                        .font(.footnote).foregroundStyle(.secondary)
                    ForEach(WearSlot.allCases, id: \.self) { slot in
                        Text(slot.title).font(.headline)
                        LazyVGrid(columns: [GridItem(.adaptive(minimum: 96), spacing: 10)], spacing: 10) {
                            ForEach(Wardrobe.all.filter { $0.slot == slot }, id: \.id) { item in tile(item, equipped: equipped) }
                        }
                    }
                    if !premium {
                        Text("💎 Premium-Teile gibt es im Eltern-Bereich (Einstellungen).").font(.caption).foregroundStyle(.secondary)
                    }
                }
                .padding()
                .id(version)
            }
            .navigationTitle("👕 Kleiderschrank")
            .navigationBarTitleDisplayMode(.inline)
            .toolbar { ToolbarItem(placement: .confirmationAction) { Button("Fertig") { dismiss() } } }
        }
        .onDisappear { WardrobeStore.markSeen() }
    }

    private func preview(_ o: Outfit) -> PetPose {
        var p = PetPose(mood: .radiant, energy: 90, rest: 90, accessory: o.hat)
        p.glasses = o.glasses; p.stage = stage
        return p
    }

    private func tile(_ item: WearItem, equipped: [String]) -> some View {
        let open = Wardrobe.isUnlocked(item, maxGoodDays: good, premium: premium)
        let on = open && equipped.contains(item.id)
        let miss = Wardrobe.missing(item, maxGoodDays: good, premium: premium)
        let note = on ? "trägt es" : fresh.contains(item.id) ? "neu!" : open ? " " : miss == nil ? "💎 Premium" : "noch \(miss!) Tage"
        return Button {
            WardrobeStore.toggle(item.id)
            SoundPlayer.shared.play(.wardrobe)
            WidgetCenter.shared.reloadAllTimelines()
            PhoneWatchBridge.shared.push()
            version += 1
        } label: {
            VStack(spacing: 2) {
                Text(open ? item.emoji : "🔒").font(.system(size: 30))
                Text(item.title).font(.caption.weight(.medium)).multilineTextAlignment(.center).lineLimit(2)
                Text(note).font(.caption2).foregroundStyle(fresh.contains(item.id) ? Color.pink : .secondary)
            }
            .padding(8).frame(maxWidth: .infinity, minHeight: 96)
            .background(on ? Color(red: 0.85, green: 0.96, blue: 0.93) : Color(.secondarySystemBackground), in: RoundedRectangle(cornerRadius: 14))
            .overlay(RoundedRectangle(cornerRadius: 14).stroke(on ? Color(red: 0.11, green: 0.6, blue: 0.6) : .clear, lineWidth: 2))
            .opacity(open ? 1 : 0.5)
        }
        .buttonStyle(.plain)
        .disabled(!open)
        .accessibilityLabel(item.title + (on ? ", angezogen" : open ? "" : miss == nil ? ", Premium" : ", noch \(miss!) gute Tage"))
    }
}
