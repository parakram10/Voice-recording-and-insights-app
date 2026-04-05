// FileSystemHelper: iOS implementation

package org.example.project.util

import platform.Foundation.NSFileManager

actual object FileSystemHelper {
    actual fun fileExists(filePath: String): Boolean {
        return try {
            NSFileManager.defaultManager.fileExistsAtPath(filePath)
        } catch (e: Exception) {
            false
        }
    }
}
