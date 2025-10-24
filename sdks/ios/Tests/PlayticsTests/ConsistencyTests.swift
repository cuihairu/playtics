import XCTest
@testable import Playtics

final class ConsistencyTests: XCTestCase {
    func testAssignVariantDeterministic() {
        let variants: [(name:String, weight:Int)] = [("A",50),("B",50)]
        let keys = ["u1","u2","u3","device123","device456"]
        let r1 = keys.map { Playtics.assignVariant(expId: "exp_consistency", salt: "s", variants: variants, key: $0) }
        let r2 = keys.map { Playtics.assignVariant(expId: "exp_consistency", salt: "s", variants: variants, key: $0) }
        XCTAssertEqual(r1, r2)
    }
}
