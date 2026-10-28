/**
 *  Govee Floor Lamp 2 (Cloud On/Off) — No Polling
 *  Author: Adam Sedgass (clean, dashboard-ready)
 *
 *  Features:
 *   - On/Off control via Govee Cloud API
 *   - Appears as a native switch in Hubitat dashboards
 *   - Debug logging optional
 */

import groovy.json.JsonOutput

metadata {
    definition(
        name: "Govee Floor Lamp (Cloud On/Off)",
        namespace: "AdamInTX",
        author: "AdamInTX"
    ) {
        capability "Switch"
        capability "Actuator"

        attribute "switch", "string"

        command "on"
        command "off"
    }

    preferences {
        input name: "apiKey", type: "string", title: "Govee API Key", required: true
        input name: "sku", type: "string", title: "Device SKU (e.g. H607C)", required: true
        input name: "deviceId", type: "string", title: "Device ID", required: true
        input name: "debugLogging", type: "bool", title: "Enable debug logging?", defaultValue: false
    }
}

def installed() {
    logInfo("Driver installed")
}

def updated() {
    logInfo("Preferences updated")
}

def on() {
    sendCommand(1)
}

def off() {
    sendCommand(0)
}

private sendCommand(value) {
    def bodyMap = [
        requestId: "hubitat-${now()}",
        payload: [
            sku: settings.sku,
            device: settings.deviceId,
            capability: [
                type: "devices.capabilities.on_off",
                instance: "powerSwitch",
                value: value
            ]
        ]
    ]

    def params = [
        uri: "https://openapi.api.govee.com",
        path: "/router/api/v1/device/control",
        requestContentType: "application/json",
        contentType: "application/json",
        headers: [
            "Govee-API-Key": settings.apiKey
        ],
        body: JsonOutput.toJson(bodyMap)
    ]

    try {
        httpPost(params) { resp ->
            if (settings.debugLogging) {
                log.debug "Govee API status: ${resp.status}"
                log.debug "Govee API response: ${resp.data}"
            }
            if (resp.status == 200) {
                sendEvent(name: "switch", value: value == 1 ? "on" : "off", displayed: true)
            } else {
                log.error "Govee API returned status ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "HTTP error sending command to Govee: ${e.message}"
    }
}

// Logging helper
private logInfo(msg) {
    log.info "Govee Floor Lamp: ${msg}"
}