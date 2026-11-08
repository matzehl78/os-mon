// OwnDataKeysTests.swift: „Alle Daten löschen“ und „Exportieren“ erfassen jeden OS-Mon-Schlüssel, aber keine Systemschlüssel.

import XCTest

final class OwnDataKeysTests: XCTestCase {
    /// Ein Schlüssel je Speicher der App (Stand: alle Literale in ios/OSMon). Früher fehlten u. a. Geburtstag, Aufgaben, PIN.
    let appKeys = [
        "pet.figure", "pet.scene", "pet.3d", "pet.evostate", "st.minutes", "steps.today", "daylog.n.20000", "push.state",
        "goal.stress", "feature.screentime", "onboarding.done", "sel.apps", "ach.unlocked", "ach.games", "festivals.enabled",
        "seasons.enabled", "user.birthday.d", "user.birthday.m", "games.limit", "games.day", "games.played", "growth.state",
        "growth.eggTaps", "growth.dropped", "kind.count", "parent.hash", "parent.salt", "parent.lockSettings", "premium.owned",
        "sleep.enabled", "sleep.bed", "sound.on", "tasks.list", "watch.payload", "wr.equipped", "wr.maxgood", "intro.seen",
    ]
    let systemKeys = ["AppleLanguages", "AppleLocale", "NSInterfaceStyle", "OSMonDemo", "OSMonFigure", "com.apple.something"]

    func testEveryAppKeyIsOwn() {
        for key in appKeys { XCTAssertTrue(OwnDataKeys.isOwn(key), key) }
    }

    func testSystemKeysStay() {
        for key in systemKeys { XCTAssertFalse(OwnDataKeys.isOwn(key), key) }
        XCTAssertEqual(OwnDataKeys.select(domainKeys: [], allKeys: systemKeys), [])
    }

    func testDomainKeysAreAlwaysIncluded() {
        // Ein künftiger Schlüssel ohne bekanntes Präfix wird über die App-Group-Domain trotzdem gelöscht
        XCTAssertEqual(OwnDataKeys.select(domainKeys: ["future.thing", "pet.figure"], allKeys: ["pet.figure", "tasks.list", "AppleLanguages"]),
                       ["future.thing", "pet.figure", "tasks.list"])
    }

    func testDeletingSelectedKeysEmptiesSuite() throws {
        let suite = "osmon.test.\(UUID().uuidString)"
        let d = try XCTUnwrap(UserDefaults(suiteName: suite))
        defer { d.removePersistentDomain(forName: suite) }
        for key in appKeys + ["future.thing"] { d.set(1, forKey: key) }
        for key in OwnDataKeys.keys(in: d, suite: suite) { d.removeObject(forKey: key) }
        for key in appKeys + ["future.thing"] { XCTAssertNil(d.object(forKey: key), key) }
    }
}
