// ParentLock.swift: Eltern-PIN. Gegenstück: ParentLock.kt (gleiche Regeln, gleiche Tests).
// PIN = 4 Ziffern. Gespeichert wird nur SHA-256(salt + ":" + pin) als Hex, nie die PIN selbst.

import CryptoKit
import Foundation

enum ParentLock {
    static let maxTries = 5
    static let sessionSeconds: TimeInterval = 5 * 60
    private static let baseWait: TimeInterval = 60
    private static let maxWait: TimeInterval = 15 * 60

    static func isValidPin(_ pin: String) -> Bool { pin.count == 4 && pin.allSatisfy { ("0"..."9").contains($0) } }

    static func hash(_ pin: String, salt: String) -> String {
        SHA256.hash(data: Data("\(salt):\(pin)".utf8)).map { String(format: "%02x", $0) }.joined()
    }

    static func verify(_ pin: String, salt: String, stored: String) -> Bool { isValidPin(pin) && hash(pin, salt: salt) == stored }

    /// Wartezeit nach failures Fehlversuchen in Folge (0 = sofort).
    static func wait(_ failures: Int) -> TimeInterval {
        guard failures >= maxTries else { return 0 }
        return min(maxWait, baseWait * pow(2, Double(min(10, failures - maxTries))))
    }

    static func isTooSimple(_ pin: String) -> Bool {
        guard isValidPin(pin) else { return true }
        let d = pin.compactMap { $0.wholeNumberValue }
        let same = d.allSatisfy { $0 == d[0] }
        let up = (1...3).allSatisfy { d[$0] == d[$0 - 1] + 1 }
        let down = (1...3).allSatisfy { d[$0] == d[$0 - 1] - 1 }
        return same || up || down
    }
}
