// PetFigureTests.swift: prüft PetFigureData.swift und den SVG-Parser gegen figures.json
//
// Einrichtung (Unit-Test-Target):
//   1. PetFigure.swift, PetFigureData.swift, PetCore.swift, PetSync.swift, ScreenTimeShared.swift
//      als Sources ins Test-Target (oder @testable import der App).
//   2. figures.json als Resource ins Test-Target ("Copy Bundle Resources").
//   3. Daten neu erzeugen: node gen_figures.mjs (mit Playwright für die bbox-Referenzen).

import XCTest
import SwiftUI

final class PetFigureTests: XCTestCase {

    private struct Fig: Decodable {
        struct Colors: Decodable { let body, bodySick, ant, off, heart, iris, lid, line, cheek: String }
        struct Shape: Decodable {
            let path: String
            let bbox: [CGFloat]?
            let face: [String: CGFloat]
            let heart: [CGFloat]
        }
        let id: String
        let name: String
        let colors: Colors
        let shape: Shape
    }

    private func load() throws -> [Fig] {
        let url = try XCTUnwrap(Bundle(for: Self.self).url(forResource: "figures", withExtension: "json"),
                                "figures.json fehlt im Test-Target")
        return try JSONDecoder().decode([Fig].self, from: Data(contentsOf: url))
    }

    private func hex(_ s: String) -> UInt32 { UInt32(s.dropFirst(), radix: 16)! }

    func testCatalogMatchesReference() throws {
        let ref = try load()
        XCTAssertEqual(PetFigure.all.map(\.id), ref.map(\.id))
        XCTAssertEqual(Set(PetFigure.all.map(\.id)).count, PetFigure.all.count, "IDs müssen eindeutig sein")
        for (f, r) in zip(PetFigure.all, ref) {
            XCTAssertEqual(f.name, r.name)
            XCTAssertEqual(f.colors.body, hex(r.colors.body), f.id)
            XCTAssertEqual(f.colors.bodySick, hex(r.colors.bodySick), f.id)
            XCTAssertEqual(f.colors.ant, hex(r.colors.ant), f.id)
            XCTAssertEqual(f.colors.off, hex(r.colors.off), f.id)
            XCTAssertEqual(f.colors.heart, hex(r.colors.heart), f.id)
            XCTAssertEqual(f.colors.iris, hex(r.colors.iris), f.id)
            XCTAssertEqual(f.colors.lid, hex(r.colors.lid), f.id)
            XCTAssertEqual(f.colors.line, hex(r.colors.line), f.id)
            XCTAssertEqual(f.colors.cheek, hex(r.colors.cheek), f.id)
            XCTAssertEqual(f.shape.path, r.shape.path, f.id)
            XCTAssertEqual(f.shape.face.ey, r.shape.face["ey"], f.id)
            XCTAssertEqual(f.shape.face.my, r.shape.face["my"], f.id)
            XCTAssertEqual(f.shape.heart.size, r.shape.heart[2], f.id)
        }
    }

    /// Der eigene SVG-Parser muss denselben Umriss liefern wie der Browser (getBBox).
    func testBodyPathsParseLikeBrowser() throws {
        for r in try load() {
            guard let b = r.shape.bbox else { continue }
            let box = SVGPath.parse(r.shape.path).cgPath.boundingBoxOfPath
            XCTAssertEqual(box.minX, b[0], accuracy: 0.75, r.id)
            XCTAssertEqual(box.minY, b[1], accuracy: 0.75, r.id)
            XCTAssertEqual(box.width, b[2], accuracy: 0.75, r.id)
            XCTAssertEqual(box.height, b[3], accuracy: 0.75, r.id)
        }
    }

    func testParserHandlesArcsAndRelativeCommands() {
        // Vollkreis aus zwei Bögen (wie bei Minto) und relative Befehle
        let circle = SVGPath.parse("M37 120 A73 73 0 1 1 183 120 A73 73 0 1 1 37 120 Z").cgPath.boundingBoxOfPath
        XCTAssertEqual(circle.minX, 37, accuracy: 0.5)
        XCTAssertEqual(circle.width, 146, accuracy: 0.5)
        XCTAssertEqual(circle.height, 146, accuracy: 0.5)
        let rel = SVGPath.parse("M10 10 l20 0 v20 h-20 z").cgPath.boundingBoxOfPath
        XCTAssertEqual(rel, CGRect(x: 10, y: 10, width: 20, height: 20))
    }

    func testSelectionFallsBackToDefault() {
        XCTAssertEqual(PetFigure.byID("gibt-es-nicht").id, PetFigure.defaultID)
        XCTAssertEqual(PetFigure.byID(nil).id, PetFigure.defaultID)
        XCTAssertEqual(PetFigure.byID("glut").name, "Glut")
    }
}
