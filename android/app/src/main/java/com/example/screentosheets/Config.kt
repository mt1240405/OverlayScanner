package com.example.screentosheets

/**
 * Names used to store bridge settings in app-private SharedPreferences.
 * No IP address or authentication token is compiled into the app.
 */
object Config {
    const val PREFS_NAME = "bridge_settings"
    const val KEY_BRIDGE_HOST = "bridge_host"
    const val KEY_BRIDGE_PORT = "bridge_port"
    const val KEY_BRIDGE_TOKEN = "bridge_token"
    const val DEFAULT_BRIDGE_PORT = "8765"
}
