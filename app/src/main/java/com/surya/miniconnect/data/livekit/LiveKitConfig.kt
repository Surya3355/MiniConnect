package com.surya.miniconnect.data.livekit

object LiveKitConfig {
    // ==========================================================
    //  Fill these with your droplet IP after running
    //  livekit-server/setup-livekit.sh (see its README).
    //  No domain/TLS yet, so plain ws:// and http:// (cleartext
    //  is allowed via network_security_config).
    // ==========================================================
    private const val HOST = "168.144.21.58"

    /** LiveKit SFU signaling URL. */
    const val URL = "ws://$HOST:7880"

    /** Token endpoint that mints LiveKit JWTs (keeps the API secret off-device). */
    const val TOKEN_URL = "http://$HOST:8080/token"

    /**
     * E2EE (SFrame) toggle. The WebRTC native lib is force-loaded before the
     * key provider is created (see LiveKitManager.connect).
     */
    const val E2EE_ENABLED = true

    // Unambiguous characters (no 0/O, 1/I) so codes are easy to read and type
    private const val CODE_CHARS = "ABCDEFGHJKLMNPQRSTUVWXYZ23456789"

    /** Generates a short shareable room code like "RIDE-7K2P". */
    fun generateCode(): String {
        val suffix = (1..4).map { CODE_CHARS.random() }.joinToString("")
        return "RIDE-$suffix"
    }

    /** Normalizes user-entered codes (trim, uppercase). */
    fun normalizeCode(input: String): String = input.trim().uppercase()

    /**
     * The E2EE shared key for a room. Everyone with the code derives the same
     * key, so the SFU forwards encrypted audio it can't read. For production
     * (RiderConnect) derive this from ride/group membership instead.
     */
    fun e2eeKeyFor(roomCode: String): String = "mc-e2ee-$roomCode"
}
