// OSMonApp.swift: Einstieg der App. Onboarding beim ersten Start, danach der Home-Screen.
// Meldungen und Hintergrund-Aktualisierung richtet PushResponder ein (siehe PushResponder.swift).

import SwiftUI

@main
struct OSMonApp: App {
    @AppStorage("onboarding.done", store: STShared.defaults) private var done = false
    @Environment(\.scenePhase) private var phase

    init() { PushResponder.shared.setUp() }

    var body: some Scene {
        WindowGroup {
            if done {
                HomeScreen()
            } else {
                OnboardingView { done = true }
            }
        }
        .onChange(of: phase) { _, p in
            if p == .background { PetBackgroundRefresh.schedule() }
        }
    }
}
