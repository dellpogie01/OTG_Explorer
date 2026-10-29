package com.juanmediahub.usbotgorganizer.domain.operations

import com.juanmediahub.usbotgorganizer.domain.model.StorageItem
import com.juanmediahub.usbotgorganizer.domain.model.StorageRef
import com.juanmediahub.usbotgorganizer.domain.storage.StorageAccess
import java.net.URLDecoder
import java.nio.charset.StandardCharsets

/**
 * Prevents recursive folder copies and moves. Document URIs are never compared
 * by string prefix. If two different tree grants use the same provider and
 * volume, their ancestry cannot be established reliably, so a folder transfer
 * is refused rather than risking a recursive copy.
 */
object AncestryGuard {
    suspend fun isUnsafeFolderTarget(
        storage: StorageAccess,
        source: StorageItem,
        destParent: StorageRef
    ): Boolean {
        if (!source.isDirectory) return false

        if (source.ref.documentId == destParent.documentId) return true

        val destinationAncestors = try {
            storage.ancestorDocumentIds(destParent)
        } catch (_: Exception) {
            return true
        }
        if (source.ref.documentId in destinationAncestors) return true

        if (storage.sameTree(source.ref, destParent)) return false

        val sourceTree = parseTree(source.ref.treeUri) ?: return true
        val destTree = parseTree(destParent.treeUri) ?: return true
        if (sourceTree.authority != destTree.authority) return false

        val sourceVolume = volumeOf(sourceTree.documentId)
        val destVolume = volumeOf(destTree.documentId)
        if (sourceVolume != null && destVolume != null && sourceVolume != destVolume) {
            return false
        }

        // Same provider and volume via two different tree grants: the true
        // relationship cannot be proven without traversing outside a grant.
        return true
    }

    private fun parseTree(treeUri: String): TreeIdentity? {
        if (!treeUri.startsWith("content://")) return null
        val rest = treeUri.removePrefix("content://")
        val authority = rest.substringBefore('/', missingDelimiterValue = "")
            .substringBefore('?')
        val marker = "/tree/"
        val markerIndex = rest.indexOf(marker)
        if (authority.isBlank() || markerIndex < 0) return null
        val encodedId = rest.substring(markerIndex + marker.length)
            .substringBefore('/')
            .substringBefore('?')
        if (encodedId.isBlank()) return null
        val documentId = runCatching {
            URLDecoder.decode(encodedId.replace("+", "%2B"), StandardCharsets.UTF_8.name())
        }.getOrNull() ?: return null
        return TreeIdentity(authority, documentId)
    }

    private fun volumeOf(treeDocumentId: String): String? {
        if (!treeDocumentId.contains(':')) return null
        val volume = treeDocumentId.substringBefore(':')
        return volume.takeIf { it.isNotBlank() }
    }

    private data class TreeIdentity(val authority: String, val documentId: String)
}
