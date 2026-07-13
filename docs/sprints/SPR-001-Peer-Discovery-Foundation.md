# SPR-001 -- Peer Discovery Foundation

**Project:** MiniConnect\
**Sprint:** 1\
**Status:** Planned

------------------------------------------------------------------------

# 1. Sprint Goal

Deliver a production-quality Wi-Fi Direct peer discovery feature using
modern Android architecture.

By the end of this sprint, a user should be able to:

-   Launch the application.
-   Grant required permissions.
-   Start peer discovery.
-   View nearby Wi-Fi Direct devices.
-   Refresh discovery.
-   Observe loading, empty and error states.

Out of scope:

-   Device connection
-   Messaging
-   File transfer
-   Voice communication

------------------------------------------------------------------------

# 2. Architecture

    Compose UI
          │
          ▼
    DiscoveryViewModel
          │
          ▼
    WifiDirectManager
          │
          ▼
    Android WifiP2pManager

Rules:

-   UI never accesses Android Wi-Fi APIs.
-   Android callbacks never leave the data layer.
-   ViewModel owns UI state.
-   Manager owns Android framework interactions.

------------------------------------------------------------------------

# 3. Folder Structure

``` text
app/src/main/java/com/surya/miniconnect/

data/
  wifi/
    WifiDirectManager.kt
    WifiDirectBroadcastReceiver.kt

domain/
  model/
    Peer.kt
    DiscoveryState.kt

presentation/
  discovery/
    DiscoveryViewModel.kt
    DiscoveryUiState.kt

ui/
  screens/
    DiscoveryScreen.kt

  components/
    PeerCard.kt
    LoadingView.kt
    EmptyState.kt

util/
  PermissionManager.kt
```

------------------------------------------------------------------------

# 4. Android Manifest

Required permissions (appropriate for supported Android versions):

-   ACCESS_FINE_LOCATION
-   ACCESS_WIFI_STATE
-   CHANGE_WIFI_STATE
-   NEARBY_WIFI_DEVICES (Android 13+)

Register any required receiver only if appropriate for the chosen
implementation.

------------------------------------------------------------------------

# 5. Gradle

Use:

-   Kotlin Coroutines
-   AndroidX Lifecycle ViewModel
-   StateFlow
-   Material 3

Do not introduce unnecessary libraries.

------------------------------------------------------------------------

# 6. Classes

## WifiDirectManager

Responsibilities:

-   Initialize WifiP2pManager
-   Register/unregister receiver
-   Start peer discovery
-   Report discovery result
-   Expose immutable StateFlow

Must NOT:

-   Reference Compose
-   Reference ViewModel
-   Hold Activity context

Public API:

``` kotlin
fun initialize(context: Context): Result<Unit>

fun registerReceiver(context: Context)

fun unregisterReceiver(context: Context)

suspend fun discoverPeers(): Result<Unit>

val peers: StateFlow<List<Peer>>

val discoveryState: StateFlow<DiscoveryState>
```

------------------------------------------------------------------------

## WifiDirectBroadcastReceiver

Responsibilities:

-   Receive Wi-Fi Direct broadcasts
-   Notify WifiDirectManager
-   No UI logic

------------------------------------------------------------------------

## DiscoveryViewModel

Responsibilities:

-   Call WifiDirectManager
-   Expose DiscoveryUiState
-   Trigger refresh

Public API:

``` kotlin
fun discoverPeers()

fun refresh()
```

------------------------------------------------------------------------

## DiscoveryScreen

Responsibilities:

-   Display peer list
-   Show loading
-   Show empty state
-   Show error state
-   Trigger discovery

------------------------------------------------------------------------

# 7. Domain Models

## Peer

``` kotlin
data class Peer(
    val name: String,
    val address: String,
    val status: Int
)
```

## DiscoveryState

``` kotlin
sealed interface DiscoveryState
```

Suggested states:

-   Idle
-   Discovering
-   Success
-   Empty
-   Error

------------------------------------------------------------------------

# 8. State Management

Use StateFlow.

Expose immutable StateFlow.

MutableStateFlow must remain private.

------------------------------------------------------------------------

# 9. Threading

-   Coroutines
-   Main dispatcher for Android callbacks
-   Never block UI thread

------------------------------------------------------------------------

# 10. Error Handling

Use Result for command operations.

Represent UI state separately.

Do not swallow exceptions.

------------------------------------------------------------------------

# 11. Coding Standards

-   Kotlin
-   SOLID
-   KDoc for public APIs
-   Small focused methods
-   No TODO comments
-   No placeholder implementations
-   No singleton unless justified

------------------------------------------------------------------------

# 12. Acceptance Criteria

-   Project builds.
-   Permissions requested.
-   Wi-Fi Direct initializes.
-   Discovery starts.
-   Nearby devices appear.
-   Loading state displayed.
-   Empty state displayed when appropriate.
-   Errors surfaced to UI.
-   No memory leaks.
-   Lifecycle-safe receiver registration.

------------------------------------------------------------------------

# 13. Manual Testing

1.  Install on two Android phones.
2.  Grant permissions.
3.  Enable Wi-Fi.
4.  Open MiniConnect on both.
5.  Tap Discover.
6.  Verify each phone appears on the other.
7.  Rotate device and verify no crash.
8.  Leave and return to app.
9.  Verify receiver lifecycle behaves correctly.

------------------------------------------------------------------------

# 14. Definition of Done

-   All acceptance criteria satisfied.
-   Code compiles without warnings.
-   Architecture respected.
-   No unused code.
-   No placeholder implementations.
-   Ready for Sprint 2 (Device Connection).

------------------------------------------------------------------------

# 15. Copilot Implementation Rules

Implement the entire sprint in one pass.

Do not implement Sprint 2 features.

Do not add sockets, messaging, or connection logic.

Generate production-ready code only.

The final implementation must compile successfully.
