package com.zbrowser.app

import android.Manifest
import android.app.Activity
import android.content.pm.PackageManager
import android.webkit.GeolocationPermissions
import android.webkit.PermissionRequest
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

/**
 * Manages runtime permissions for the browser.
 * Handles geolocation, camera, microphone, and other web content permissions.
 * Shows user-friendly prompts before granting permissions to web pages.
 *
 * NOTE: Not provided via Hilt @Singleton — instead created directly in
 * MainActivity because it needs an Activity reference. Hilt @Singleton
 * cannot inject Activity-scoped objects.
 *
 * v4.0 FIX: M3 — Supports multiple concurrent permission requests using a map
 * of requestCode → pending request, instead of a single pending field.
 */
class PermissionManager(private val activity: Activity) {

    companion object {
        const val RC_GEOLOCATION = 1001
        const val RC_CAMERA = 1002
        const val RC_MICROPHONE = 1003
        const val RC_FILE_ACCESS = 1004

        private val RESOURCE_TO_PERMISSION: Map<String, String> = mapOf(
            PermissionRequest.RESOURCE_VIDEO_CAPTURE to Manifest.permission.CAMERA,
            PermissionRequest.RESOURCE_AUDIO_CAPTURE to Manifest.permission.RECORD_AUDIO
            // NOTE: RESOURCE_GEOLOCATION was removed in API 35.
            // Geolocation is handled separately via onGeolocationPermissionsShowPrompt.
        )
    }

    // M3 FIX: Use maps to support multiple concurrent permission requests
    private val pendingGeoCallbacks = mutableMapOf<Int, Pair<GeolocationPermissions.Callback, String>>()
    private val pendingPermissionRequests = mutableMapOf<Int, PermissionRequest>()
    private var nextRequestCode = RC_FILE_ACCESS + 1

    fun onGeolocationPermissionsShowPrompt(
        origin: String,
        callback: GeolocationPermissions.Callback
    ) {
        if (hasPermission(Manifest.permission.ACCESS_FINE_LOCATION)) {
            callback.invoke(origin, true, false)
        } else {
            val requestCode = RC_GEOLOCATION
            pendingGeoCallbacks[requestCode] = Pair(callback, origin)
            ActivityCompat.requestPermissions(
                activity,
                arrayOf(Manifest.permission.ACCESS_FINE_LOCATION),
                requestCode
            )
        }
    }

    fun onPermissionRequest(request: PermissionRequest) {
        val requestedResources = request.resources
        val neededPermissions = mutableListOf<String>()
        var requestCode = RC_CAMERA  // Default

        for (resource in requestedResources) {
            val permission = RESOURCE_TO_PERMISSION[resource]
            if (permission != null && !hasPermission(permission)) {
                neededPermissions.add(permission)
                // Set the correct request code based on the resource type
                when (resource) {
                    PermissionRequest.RESOURCE_VIDEO_CAPTURE -> requestCode = RC_CAMERA
                    PermissionRequest.RESOURCE_AUDIO_CAPTURE -> requestCode = RC_MICROPHONE
                    // Geolocation handled via onGeolocationPermissionsShowPrompt
                }
            }
        }

        if (neededPermissions.isEmpty()) {
            request.grant(requestedResources)
        } else {
            // M3 FIX: Use unique request codes for concurrent requests
            val uniqueCode = if (pendingPermissionRequests.containsKey(requestCode)) {
                nextRequestCode++
            } else {
                requestCode
            }
            pendingPermissionRequests[uniqueCode] = request
            ActivityCompat.requestPermissions(
                activity,
                neededPermissions.toTypedArray(),
                uniqueCode
            )
        }
    }

    fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        val allGranted = grantResults.isNotEmpty() &&
                grantResults.all { it == PackageManager.PERMISSION_GRANTED }

        when (requestCode) {
            RC_GEOLOCATION -> {
                pendingGeoCallbacks.remove(requestCode)?.let { (callback, origin) ->
                    callback.invoke(origin, allGranted, false)
                }
            }
            RC_CAMERA, RC_MICROPHONE -> {
                pendingPermissionRequests.remove(requestCode)?.let { request ->
                    if (allGranted) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                }
            }
            else -> {
                // M3 FIX: Handle dynamically generated request codes
                pendingPermissionRequests.remove(requestCode)?.let { request ->
                    if (allGranted) {
                        request.grant(request.resources)
                    } else {
                        request.deny()
                    }
                }
                pendingGeoCallbacks.remove(requestCode)?.let { (callback, origin) ->
                    callback.invoke(origin, allGranted, false)
                }
            }
        }
    }

    private fun hasPermission(permission: String): Boolean {
        return ContextCompat.checkSelfPermission(activity, permission) ==
                PackageManager.PERMISSION_GRANTED
    }
}
