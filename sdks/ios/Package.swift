// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "Playtics",
    platforms: [ .iOS(.v13), .macOS(.v12) ],
    products: [ .library(name: "Playtics", targets: ["Playtics"]) ],
    targets: [
        .target(name: "Playtics", path: "Sources/Playtics")
    ]
)
