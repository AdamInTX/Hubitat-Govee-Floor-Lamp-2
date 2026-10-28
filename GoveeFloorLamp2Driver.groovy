/**
 * Govee Floor Lamp 2 (Cloud On/Off + Polling)
 * Author: Adam Sedgass (AdamInTX)
 *
 * Features:
 * - On/Off control via Govee Cloud API
 * - Polls current device state once per minute
 * - Manual refresh command
 * - Native Hubitat switch capability
 * - Optional debug logging
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
        capability "Refresh"

        attribute "switch", "string"

        command "on"
        command "off"
    }

    preferences {
        input name: "apiKey",
            type: "string",
            title: "Govee API Key",
            required: true

        input name: "sku",
            type: "string",
            title: "Device SKU (e.g. H607C)",
            required: true

        input name: "deviceId",
            type: "string",
            title: "Device ID",
            required: true

        input name: "debugLogging",
            type: "bool",
            title: "Enable debug logging?",
            defaultValue: false
    }
}

def installed() {
    logInfo("Driver installed")
    runEvery1Minute("refresh")
    refresh()
}

def updated() {
    logInfo("Preferences updated")
    unschedule()
    runEvery1Minute("refresh")
    refresh()
}

def on() {
    sendCommand(1)
}

def off() {
    sendCommand(0)
}

def refresh() {
    getDeviceState()
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
                log.debug "Govee control HTTP status: ${resp.status}"
                log.debug "Govee control response: ${resp.data}"
            }

            if (resp.status == 200) {
                // Reflect the requested state immediately.
                sendEvent(
                    name: "switch",
                    value: value == 1 ? "on" : "off",
                    displayed: true
                )

                // Verify actual state with Govee.
                refresh()
            } else {
                log.error "Govee control API returned HTTP ${resp.status}"
            }
        }
    } catch (Exception e) {
        log.error "HTTP error sending Govee command: ${e.message}"
    }
}

private getDeviceState() {
    def bodyMap = [
        requestId: "hubitat-${now()}",
        payload: [
            sku: settings.sku,
            device: settings.deviceId
        ]
    ]

    def params = [
        uri: "https://openapi.api.govee.com",
        path: "/router/api/v1/device/state",
        requestContentType: "application/json",
        contentType: "application/json",
        headers: [
            "Govee-API-Key": settings.apiKey
        ],
        body: JsonOutput.toJson(bodyMap)
    ]

    try {
        httpPost(params) { resp ->
            def data = resp.data

            if (settings.debugLogging) {
                log.debug "Govee state HTTP status: ${resp.status}"
                log.debug "Govee state response: ${data}"
            }

            if (resp.status != 200 || data?.code != 200) {
                log.warn "Govee state query failed: HTTP ${resp.status}, API code ${data?.code}"
                return
            }

            def capabilities = data?.payload?.capabilities

            def online = capabilities?.find {
                it.type == "devices.capabilities.online" &&
                it.instance == "online"
            }?.state?.value

            def power = capabilities?.find {
                it.type == "devices.capabilities.on_off" &&
                it.instance == "powerSwitch"
            }?.state?.value

            if (online == false || online?.toString() == "false") {
                log.warn "Govee reports the device offline"
                return
            }

            if (power == null ||
                !(power.toString() in ["0", "1"])) {
                log.warn "Govee response did not contain a usable power state"
                return
            }

            sendEvent(
                name: "switch",
                value: power.toString() == "1" ? "on" : "off",
                displayed: true
            )

            if (settings.debugLogging) {
                log.debug "Govee power state updated: ${power}"
            }
        }
    } catch (Exception e) {
        log.error "Error querying Govee state: ${e.message}"
    }
}

// Logging helper
private logInfo(msg) {
    log.info "Govee Floor Lamp: ${msg}"
}