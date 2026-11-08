import Foundation
import Security

struct FamilyTask: Codable, Identifiable, Equatable {
    var id: String
    var title: String
    var `repeat`: String
}
struct FamilyConfig: Codable, Equatable { var limit: Int; var tasks: [FamilyTask] }
struct FamilyRequestResult: Decodable { var request: FamilyLocationRequest }
struct FamilyLocationRequest: Codable { var id: String; var created: Double; var expires: Double; var state: String }
struct FamilyLocation: Codable { var latitude: Double; var longitude: Double; var accuracy: Double; var measured: Double; var received: Double }
struct FamilyDevice: Codable, Identifiable {
    var id: String; var name: String; var paired: Bool; var config: FamilyConfig
    var version: Int; var applied: Int; var completed: [String]; var seen: Double?; var day: Int?; var location: FamilyLocation?; var locationSharing: Bool?; var locationRequest: FamilyLocationRequest?
    var measuring: FamilyMeasuring? = nil
}
/// Werden Bildschirmzeit und Schritte am Kindergerät gemessen? Nur an/aus. Gleiche Texte wie Webportal und Android.
struct FamilyMeasuring: Codable, Equatable {
    var screen: Bool; var steps: Bool; var updated: Double?
    var isOff: Bool { !screen || !steps }
    static func text(_ m: FamilyMeasuring?) -> String {
        guard let m else { return "Messung noch unbekannt (ältere OS-Mon-Version auf dem Kindergerät)" }
        let off = [m.screen ? nil : "Bildschirmzeit", m.steps ? nil : "Schritte"].compactMap { $0 }
        if off.isEmpty { return "Bildschirmzeit und Schritte werden gemessen" }
        return off.joined(separator: " und ") + (off == ["Bildschirmzeit"] ? " wird" : " werden")
            + " nicht gemessen – am Kindergerät in OS-Mon oder den Systemeinstellungen wieder erlauben"
    }
}
struct FamilyConnection: Codable { var server: String; var token: String }
struct FamilyPair: Decodable { let token: String; let code: String; let expires: Int }
struct FamilyWebCode: Decodable { let code: String; let expires: Int }
struct FamilyToken: Decodable { let token: String }
struct FamilyList: Decodable { let children: [FamilyDevice] }
struct FamilyOK: Decodable { let ok: Bool }
struct FamilyError: LocalizedError { let text: String; var errorDescription: String? { text } }

enum FamilyVault {
    private static var query: [String: Any] { [kSecClass as String: kSecClassGenericPassword, kSecAttrService as String: Bundle.main.bundleIdentifier ?? "OSMon", kSecAttrAccount as String: "family.connection"] }
    static func load() -> FamilyConnection? {
        var q = query; q[kSecReturnData as String] = true; q[kSecMatchLimit as String] = kSecMatchLimitOne
        var result: CFTypeRef?
        guard SecItemCopyMatching(q as CFDictionary, &result) == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(FamilyConnection.self, from: data)
    }
    static func save(_ value: FamilyConnection) throws {
        let data = try JSONEncoder().encode(value)
        let update = SecItemUpdate(query as CFDictionary, [kSecValueData as String: data] as CFDictionary)
        if update == errSecItemNotFound {
            var q = query; q[kSecValueData as String] = data; q[kSecAttrAccessible as String] = kSecAttrAccessibleWhenUnlockedThisDeviceOnly
            guard SecItemAdd(q as CFDictionary, nil) == errSecSuccess else { throw FamilyError(text: "Zugang konnte nicht sicher gespeichert werden.") }
        } else if update != errSecSuccess { throw FamilyError(text: "Zugang konnte nicht gespeichert werden.") }
    }
    static func clear() { SecItemDelete(query as CFDictionary) }
}

struct FamilyAPI {
    var connection: FamilyConnection
    static func server(_ text: String) throws -> String {
        guard let u = URLComponents(string: text.trimmingCharacters(in: .whitespacesAndNewlines)), u.scheme == "https", let host = u.host, !host.isEmpty,
              u.user == nil, u.password == nil, u.query == nil, u.fragment == nil, u.path.isEmpty || u.path == "/" else { throw FamilyError(text: "Bitte eine HTTPS-Serveradresse ohne Pfad angeben.") }
        return String(text.trimmingCharacters(in: .whitespacesAndNewlines).trimmingCharacters(in: CharacterSet(charactersIn: "/")))
    }
    func call<T: Decodable>(_ method: String, _ path: String, _ body: [String: Any]? = nil) async throws -> T {
        let base = try Self.server(connection.server)
        guard let url = URL(string: base + path) else { throw FamilyError(text: "Ungültige Adresse.") }
        var request = URLRequest(url: url); request.httpMethod = method; request.timeoutInterval = 15
        request.setValue("application/json", forHTTPHeaderField: "Content-Type")
        if !connection.token.isEmpty { request.setValue("Bearer \(connection.token)", forHTTPHeaderField: "Authorization") }
        if let body { request.httpBody = try JSONSerialization.data(withJSONObject: body) }
        // Ephemeral transport: no persistent cookie or response cache.
        let cfg = URLSessionConfiguration.ephemeral
        let session = URLSession(configuration: cfg, delegate: FamilyNoRedirect(), delegateQueue: nil)
        defer { session.finishTasksAndInvalidate() }
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else { throw FamilyError(text: "Keine Serverantwort.") }
        guard (200..<300).contains(http.statusCode) else {
            let message = (try? JSONSerialization.jsonObject(with: data) as? [String: String])?["error"]
            throw FamilyError(text: message ?? "Verbindung fehlgeschlagen (\(http.statusCode)).")
        }
        return try JSONDecoder().decode(T.self, from: data)
    }
    static func object<T: Encodable>(_ value: T) throws -> Any { try JSONSerialization.jsonObject(with: JSONEncoder().encode(value)) }
}
private final class FamilyNoRedirect: NSObject, URLSessionTaskDelegate {
    func urlSession(_ session: URLSession, task: URLSessionTask, willPerformHTTPRedirection response: HTTPURLResponse, newRequest request: URLRequest, completionHandler: @escaping (URLRequest?) -> Void) { completionHandler(nil) }
}
