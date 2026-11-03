// OSMonApp.swift: Einstieg der App. Bei jedem Start Logo-Film, beim ersten Start dann Vorspann-Film und Onboarding, sonst der Home-Screen.
// Meldungen und Hintergrund-Aktualisierung richtet PushResponder ein (siehe PushResponder.swift).

import SwiftUI

@main
struct OSMonApp: App {
    @AppStorage("onboarding.done", store: STShared.defaults) private var done = false
    @AppStorage("intro.seen", store: STShared.defaults) private var introSeen = false
    @State private var logoDone = false                     // Logo-Film bei jedem Kaltstart
    @State private var logoFull = !STShared.defaults.bool(forKey: "onboarding.done") && !STShared.defaults.bool(forKey: "intro.seen")
    @Environment(\.scenePhase) private var phase

    init() {
        #if DEBUG
        DemoMode.prepare()
        #endif
        PushResponder.shared.setUp()
        PhoneWatchBridge.shared.start()                 // Apple Watch (falls gekoppelt)
        Task { @MainActor in PremiumStore.shared.start() }   // Einmalkauf mit dem App Store abgleichen
    }

    var body: some Scene {
        WindowGroup {
            #if DEBUG
            if DemoMode.screen != nil { DemoRoot() } else { startRoot }
            #else
            startRoot
            #endif
        }
        .onChange(of: phase) { _, p in
            if p == .background { PetBackgroundRefresh.schedule() }
        }
    }

    @ViewBuilder private var startRoot: some View {
        // Erster Start: ganze Fassung mit Ton vor dem Vorspann; danach kurz (ca. 3 s) und stumm
        if logoDone { root.transition(.opacity) } else { LogoSplashView(full: logoFull) { logoDone = true } }
    }

    @ViewBuilder private var root: some View {
            if done {
                HomeScreen()
            } else if !introSeen {
                IntroVideoView { introSeen = true }      // Vorspann einmal vor dem Onboarding
            } else {
                OnboardingView { done = true }
            }
    }
}
