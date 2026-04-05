// FileSystemHelper: Android implementation

package org.example.project.util

import java.io.File

actual object FileSystemHelper {
    actual fun fileExists(filePath: String): Boolean {
        return try {
            File(filePath).exists()
        } catch (e: Exception) {
            false
        }
    }
}
