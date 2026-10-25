package com.example.screentosheets

object Config {
    // Replace with your computer's LAN IPv4 address, not localhost.
    const val BRIDGE_BASE_URL = "http://192.168.1.25:8765"
    // Must match the BRIDGE_TOKEN environment variable used to start bridge/server.py.
    const val BRIDGE_TOKEN = "change-this-dev-token"
}
