import Foundation
import Security

final class LocalStore {
    static let shared = LocalStore()

    private let fileManager = FileManager.default
    private let encoder = JSONEncoder()
    private let decoder = JSONDecoder()
    private let defaults = UserDefaults.standard

    private var appSupportDirectory: URL {
        let base = fileManager.urls(for: .applicationSupportDirectory, in: .userDomainMask).first!
        return base.appendingPathComponent("BJTUselfServiceMac", isDirectory: true)
    }

    private var cacheURL: URL {
        appSupportDirectory.appendingPathComponent("cache.json")
    }

    private init() {
        encoder.outputFormatting = [.prettyPrinted, .sortedKeys]
        encoder.dateEncodingStrategy = .iso8601
        decoder.dateDecodingStrategy = .iso8601
    }

    func loadCache() -> AppCache {
        guard let data = try? Data(contentsOf: cacheURL) else { return AppCache() }
        return (try? decoder.decode(AppCache.self, from: data)) ?? AppCache()
    }

    func saveCache(_ cache: AppCache) {
        try? fileManager.createDirectory(at: appSupportDirectory, withIntermediateDirectories: true)
        guard let data = try? encoder.encode(cache) else { return }
        try? data.write(to: cacheURL, options: [.atomic])
    }

    func clearCache() {
        try? fileManager.removeItem(at: cacheURL)
    }

    var autoSyncGrades: Bool {
        get { defaults.object(forKey: "autoSyncGrades") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "autoSyncGrades") }
    }

    var autoSyncHomework: Bool {
        get { defaults.object(forKey: "autoSyncHomework") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "autoSyncHomework") }
    }

    var autoSyncSchedule: Bool {
        get { defaults.object(forKey: "autoSyncSchedule") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "autoSyncSchedule") }
    }

    var autoSyncExams: Bool {
        get { defaults.object(forKey: "autoSyncExams") as? Bool ?? false }
        set { defaults.set(newValue, forKey: "autoSyncExams") }
    }

    var checkUpdate: Bool {
        get { defaults.object(forKey: "checkUpdate") as? Bool ?? true }
        set { defaults.set(newValue, forKey: "checkUpdate") }
    }

    var appearance: String {
        get { defaults.string(forKey: "appearance") ?? "system" }
        set { defaults.set(newValue, forKey: "appearance") }
    }
}

enum KeychainStore {
    private static let service = "team.bjtuss.bjtuselfservice.mac"
    private static let account = "student-credentials"

    static func save(_ credentials: Credentials) {
        guard let data = try? JSONEncoder().encode(credentials) else { return }
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)

        var item = query
        item[kSecValueData as String] = data
        item[kSecAttrAccessible as String] = kSecAttrAccessibleAfterFirstUnlockThisDeviceOnly
        let status = SecItemAdd(item as CFDictionary, nil)
        if status != errSecSuccess {
            print("Keychain save failed: \(status)")
        }
    }

    static func load() -> Credentials? {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account,
            kSecReturnData as String: true,
            kSecMatchLimit as String: kSecMatchLimitOne
        ]

        var result: CFTypeRef?
        let status = SecItemCopyMatching(query as CFDictionary, &result)
        guard status == errSecSuccess, let data = result as? Data else { return nil }
        return try? JSONDecoder().decode(Credentials.self, from: data)
    }

    static func clear() {
        let query: [String: Any] = [
            kSecClass as String: kSecClassGenericPassword,
            kSecAttrService as String: service,
            kSecAttrAccount as String: account
        ]
        SecItemDelete(query as CFDictionary)
    }
}
