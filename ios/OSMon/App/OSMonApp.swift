// OSMonApp.swift: Einstieg der App. Onboarding beim ersten Start, danach der Home-Screen.
// Meldungen und Hintergrund-Aktualisierung richtet PushResponder ein (siehe PushResponder.swift).

import SwiftUI

@main
struct OSMonApp: App {
    @AppStorage("onboarding.done", store: STShared.defaults) private var done = false
    @Environment(\.scenePhase) private var phase

    init() {
        #if DEBUG
        DemoMode.prepare()
        #endif
        PushResponder.shared.setUp()
    }

    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if DemoMode.screen != nil { DemoRoot() } else { root }
            #else
            root
            #endif
        }
        .onChange(of: phase) { _, p in
            if p == .background { PetBackgroundRefresh.schedule() }
        }
    }

    @ViewBuilder private var root: some View {
            if done {
                HomeScreen()
            } else {
                OnboardingView { done = true }
            }
    }
}
