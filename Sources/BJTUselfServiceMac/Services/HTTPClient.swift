import Foundation

private final class TrustAllDelegate: NSObject, URLSessionDelegate {
    func urlSession(_ session: URLSession, didReceive challenge: URLAuthenticationChallenge) async -> (URLSession.AuthChallengeDisposition, URLCredential?) {
        if challenge.protectionSpace.authenticationMethod == NSURLAuthenticationMethodServerTrust,
           let trust = challenge.protectionSpace.serverTrust {
            return (.useCredential, URLCredential(trust: trust))
        }
        return (.performDefaultHandling, nil)
    }
}

actor HTTPClient {
    let cookieStorage = HTTPCookieStorage.shared
    private var manualCookies: [String: [HTTPCookie]] = [:]
    private let delegate = TrustAllDelegate()

    private let session: URLSession

    init() {
        let configuration = URLSessionConfiguration.default
        configuration.httpCookieStorage = cookieStorage
        configuration.httpCookieAcceptPolicy = .always
        configuration.httpShouldSetCookies = true
        configuration.requestCachePolicy = .reloadIgnoringLocalCacheData
        configuration.timeoutIntervalForRequest = 35
        configuration.timeoutIntervalForResource = 120
        session = URLSession(configuration: configuration, delegate: delegate, delegateQueue: nil)
    }

    func data(for request: URLRequest) async throws -> (Data, HTTPURLResponse) {
        let (data, response) = try await session.data(for: request)
        guard let http = response as? HTTPURLResponse else {
            throw AppError.badResponse("不是 HTTP 响应")
        }
        if let url = http.url {
            storeCookies(from: http, for: url)
        }
        return (data, http)
    }

    func get(_ urlString: String, headers: [String: String] = [:]) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: urlString) else { throw AppError.invalidURL(urlString) }
        var request = URLRequest(url: url)
        request.httpMethod = "GET"
        add(headers: headers, to: &request)
        return try await data(for: request)
    }

    func postForm(_ urlString: String, headers: [String: String] = [:], form: [String: String]) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: urlString) else { throw AppError.invalidURL(urlString) }
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("application/x-www-form-urlencoded", forHTTPHeaderField: "Content-Type")
        request.httpBody = form.percentEncodedFormData()
        add(headers: headers, to: &request)
        return try await data(for: request)
    }

    func postMultipart(_ urlString: String, headers: [String: String] = [:], parts: [MultipartPart]) async throws -> (Data, HTTPURLResponse) {
        guard let url = URL(string: urlString) else { throw AppError.invalidURL(urlString) }
        let boundary = "Boundary-\(UUID().uuidString)"
        var request = URLRequest(url: url)
        request.httpMethod = "POST"
        request.setValue("multipart/form-data; boundary=\(boundary)", forHTTPHeaderField: "Content-Type")
        request.httpBody = MultipartPart.body(parts: parts, boundary: boundary)
        add(headers: headers, to: &request)
        return try await data(for: request)
    }

    func cookies(for url: URL) -> [HTTPCookie] {
        var result = cookieStorage.cookies(for: url) ?? []
        let domain = url.host ?? ""
        for manual in manualCookies[domain] ?? [] {
            if !result.contains(where: { $0.name == manual.name }) {
                result.append(manual)
            }
        }
        return result
    }

    func clearCookies() {
        cookieStorage.cookies?.forEach { cookieStorage.deleteCookie($0) }
        manualCookies.removeAll()
    }

    func clearCookies(forDomain domain: String) {
        cookieStorage.cookies?.filter { $0.domain.contains(domain) }.forEach { cookieStorage.deleteCookie($0) }
        manualCookies.removeValue(forKey: domain)
    }

    private func add(headers: [String: String], to request: inout URLRequest) {
        for (key, value) in headers {
            request.setValue(value, forHTTPHeaderField: key)
        }
        if request.value(forHTTPHeaderField: "User-Agent") == nil {
            request.setValue(desktopUserAgent, forHTTPHeaderField: "User-Agent")
        }
    }

    private func storeCookies(from response: HTTPURLResponse, for url: URL) {
        let allHeaders = response.allHeaderFields as? [String: String] ?? [:]
        guard !allHeaders.isEmpty else { return }
        let cookies = HTTPCookie.cookies(withResponseHeaderFields: allHeaders, for: url)
        for cookie in cookies {
            cookieStorage.setCookie(cookie)
            let domain = cookie.domain
            manualCookies[domain, default: []].removeAll { $0.name == cookie.name }
            manualCookies[domain, default: []].append(cookie)
        }
    }
}

struct MultipartPart {
    var name: String
    var filename: String?
    var contentType: String
    var data: Data

    static func file(name: String, filename: String, data: Data, contentType: String = "application/octet-stream") -> MultipartPart {
        MultipartPart(name: name, filename: filename, contentType: contentType, data: data)
    }

    static func body(parts: [MultipartPart], boundary: String) -> Data {
        var body = Data()
        for part in parts {
            body.append("--\(boundary)\r\n")
            if let filename = part.filename {
                body.append("Content-Disposition: form-data; name=\"\(part.name)\"; filename=\"\(filename)\"\r\n")
            } else {
                body.append("Content-Disposition: form-data; name=\"\(part.name)\"\r\n")
            }
            body.append("Content-Type: \(part.contentType)\r\n\r\n")
            body.append(part.data)
            body.append("\r\n")
        }
        body.append("--\(boundary)--\r\n")
        return body
    }
}

private extension Dictionary where Key == String, Value == String {
    func percentEncodedFormData() -> Data {
        let body = map { key, value in
            "\(key.urlFormEncoded)=\(value.urlFormEncoded)"
        }
        .joined(separator: "&")
        return Data(body.utf8)
    }
}

private extension String {
    var urlFormEncoded: String {
        var allowed = CharacterSet.urlQueryAllowed
        allowed.remove(charactersIn: ":#[]@!$&'()*+,;=")
        return addingPercentEncoding(withAllowedCharacters: allowed) ?? self
    }
}

private extension Data {
    mutating func append(_ string: String) {
        append(Data(string.utf8))
    }
}
