// swift-tools-version: 5.9

import PackageDescription

let package = Package(
    name: "BJTUselfServiceMac",
    platforms: [
        .macOS(.v14)
    ],
    products: [
        .executable(name: "BJTUselfServiceMac", targets: ["BJTUselfServiceMac"])
    ],
    dependencies: [
        .package(url: "https://github.com/scinfu/SwiftSoup.git", from: "2.8.0")
    ],
    targets: [
        .executableTarget(
            name: "BJTUselfServiceMac",
            dependencies: ["SwiftSoup"],
            resources: [
                .copy("Resources/CaptchaCRNN.mlpackage")
            ],
            linkerSettings: [
                .linkedFramework("AppKit"),
                .linkedFramework("CoreML"),
                .linkedFramework("Security"),
                .linkedFramework("WebKit")
            ]
        )
    ]
)
