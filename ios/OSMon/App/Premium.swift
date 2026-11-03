// Premium.swift: Premium als Einmalkauf (StoreKit 2). Gegenstück: Premium.kt (Google Play Billing).
//
// Ein Produkt „com.example.osmon.premium“ (nicht verbrauchbar), in App Store Connect anzulegen (tools/set_ids.sh
// ersetzt com.example). Kaufen und Wiederherstellen nur im Eltern-Bereich (hinter dem PIN, „Parental Gate“).
// Premium schaltet frei: Zauberhut, Piratenhut, Monokel und das Spiel „Glühwürmchen“. Kein Abo, keine Werbung.

import StoreKit
import SwiftUI

@MainActor
final class PremiumStore: ObservableObject {
    static let shared = PremiumStore()
    @Published private(set) var product: Product?
    @Published private(set) var owned = PremiumFlag.isPremium
    @Published var status: String?
    private var updates: Task<Void, Never>?

    /// Beim Start: Käufe abgleichen und auf neue Transaktionen (z. B. „Kauf genehmigen“ in der Familie) hören.
    func start() {
        guard updates == nil else { return }
        updates = Task { [weak self] in
            for await r in Transaction.updates { if case .verified(let t) = r { await t.finish(); await self?.refresh() } }
        }
        Task { await refresh() }
    }

    func load() async {
        if product == nil { product = try? await Product.products(for: [PremiumFlag.productID]).first }
    }

    /// Kaufstatus aus den aktuellen Berechtigungen (rückerstattet = aus).
    func refresh() async {
        var has = false
        for await r in Transaction.currentEntitlements {
            if case .verified(let t) = r, t.productID == PremiumFlag.productID, t.revocationDate == nil { has = true }
        }
        PremiumFlag.set(has)
        owned = PremiumFlag.isPremium
    }

    func buy() async {
        await load()
        guard let p = product else { status = "Produkt nicht verfügbar – bitte später noch einmal."; return }
        do {
            switch try await p.purchase() {
            case .success(let r):
                if case .verified(let t) = r { await t.finish(); await refresh(); status = "Danke! Premium ist freigeschaltet." }
                else { status = "Kauf konnte nicht bestätigt werden." }
            case .pending: status = "Kauf wartet noch auf Bestätigung (z. B. „Kaufen anfragen“)."
            case .userCancelled: break
            @unknown default: break
            }
        } catch { status = "Kauf nicht möglich." }
    }

    func restore() async {
        try? await AppStore.sync()
        await refresh()
        status = owned ? "Premium wiederhergestellt." : "Kein Kauf gefunden."
    }
}

/// Karte im Eltern-Bereich.
struct PremiumCard: View {
    @ObservedObject private var store = PremiumStore.shared

    var body: some View {
        VStack(alignment: .leading, spacing: 8) {
            Label(store.owned ? "Premium ist freigeschaltet" : "Premium – einmal kaufen, für immer", systemImage: "diamond.fill")
                .font(.headline).foregroundStyle(Color(red: 0.23, green: 0.12, blue: 0.43))
            Text("Enthält: 🧙 Zauberhut, 🏴‍☠️ Piratenhut, 🧐 Monokel und das Spiel ✨ Glühwürmchen. Kein Abo, keine Werbung. Alles andere in OS-Mon bleibt kostenlos.")
                .font(.footnote).foregroundStyle(.secondary)
            if !store.owned {
                Button { Task { await store.buy() } } label: {
                    Text(store.product.map { "Für \($0.displayPrice) kaufen" } ?? "Kaufen")
                }.buttonStyle(.borderedProminent)
            }
            Button("Kauf wiederherstellen") { Task { await store.restore() } }.buttonStyle(.bordered)
            if let s = store.status { Text(s).font(.caption) }
        }
        .padding(14)
        .frame(maxWidth: .infinity, alignment: .leading)
        .background(Color(red: 0.95, green: 0.91, blue: 1), in: RoundedRectangle(cornerRadius: 16))
        .task { await store.load() }
    }
}
