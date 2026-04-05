// FileSystemHelper: Cross-platform file system utilities

package org.example.project.util

/**
 * Cross-platform interface for file system operations.
 * Implemented on Android and iOS to handle platform-specific file access.
 */
expect object FileSystemHelper {
    /**
     * Check if a file exists at the given path.
     *
     * @param filePath Absolute path to the file
     * @return True if the file exists, false otherwise
     */
    fun fileExists(filePath: String): Boolean
}
