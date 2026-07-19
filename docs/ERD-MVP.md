# ERD-MVP.md

# MiniConnect MVP Engineering Requirements Document

## Vision

Build a production-quality Android application enabling two Android
devices to communicate over Wi-Fi Direct without Internet.

## Product Goals

-   Peer discovery
-   Device connection
-   TCP socket communication
-   Real-time chat
-   Single file transfer
-   Disconnect and reconnect
-   Material 3 UI

Out of Scope: - Group chat - Voice/video - Cloud sync - Authentication -
Database - Encryption

## Tech Stack

-   Kotlin
-   Jetpack Compose
-   Material 3
-   MVVM
-   StateFlow
-   Coroutines
-   Android Wi-Fi Direct
-   TCP Sockets

## Architecture

Compose UI → ViewModel → Managers → Android Framework

Rules: - UI never calls Android Wi-Fi APIs. - Android callbacks stay
inside managers. - Mutable StateFlow is private. - No business logic in
Activities.

## Folder Structure

data/ - wifi/ - socket/

domain/model/

presentation/ - discovery/ - chat/

ui/ - screens/ - components/ - theme/

util/

## Managers

### WifiDirectManager

Responsibilities: - Initialize Wi-Fi Direct - Discover peers - Connect -
Disconnect - Register/unregister receiver - Expose peers and connection
state

API: - initialize(context) - discoverPeers() - connect(peer) -
disconnect() - cleanup()

### SocketManager

Responsibilities: - Start server - Connect client - Send message -
Receive messages - Send file - Disconnect

API: - startServer() - connect() - sendMessage() - sendFile() -
disconnect()

### PermissionManager

-   Check permissions
-   Request permissions
-   Handle Android version differences

## ViewModels

DiscoveryViewModel - Discover - Connect - UI state

ChatViewModel - Send/receive messages - Send files - Disconnect

## Domain Models

Peer ConnectionState DiscoveryState ChatMessage TransferState

## UI

Screen 1: Discovery - Refresh - Device list - Loading - Empty - Error

Screen 2: Chat - Header - Connection state - Messages - Text input -
Send - Attachment - Transfer progress

## Engineering Rules

-   SOLID
-   DRY
-   KISS
-   KDoc on public APIs
-   No TODOs
-   No placeholder implementations
-   No Activity context retention

## Android

Implement: - Manifest permissions - Runtime permissions -
BroadcastReceiver - PeerListListener - ConnectionInfoListener

## Error Handling

-   Result for commands
-   StateFlow for state
-   Meaningful exceptions

## Acceptance

-   Two devices discover each other
-   Connect successfully
-   Exchange text messages
-   Transfer one file
-   Disconnect/reconnect
-   No crashes on rotation
-   Build succeeds

## Deliverable

Generate the complete MVP in one implementation. No placeholders.
