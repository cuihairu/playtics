// swift-tools-version:5.7
import PackageDescription

let package = Package(
    name: "Pit",
    platforms: [ .iOS(.v13), .macOS(.v12) ],
    products: [ .library(name: "Pit", targets: ["Pit"]) ],
    targets: [
        .target(name: "Pit", path: "Sources/Pit"),
        .testTarget(name: "PitTests", dependencies: ["Pit"], path: "Tests/PitTests")
    ]
)
