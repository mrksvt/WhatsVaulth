package com.mrksvt.waen.update

import android.content.Context
import android.content.Intent
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.mrksvt.waen.BuildConfig
import java.io.File
import java.security.MessageDigest
import java.util.zip.ZipFile

/** Reason an in-app install could not be started. */
enum class InstallFailure {
    ASSET_MISSING,
    NOT_AN_APK,
    WRONG_PACKAGE,
    NOT_NEWER,
    SIGNATURE_MISMATCH,
    PERMISSION_DENIED,
    UNKNOWN
}

/**
 * Checks the package name and version code of a candidate APK against the installed app.
 * Returns null when the APK may be installed.
 */
fun verifyApkMetadata(
    apkPackageName: String?,
    apkVersionCode: Long,
    expectedPackage: String,
    installedVersionCode: Long
): InstallFailure? {
    if (apkPackageName.isNullOrEmpty()) return InstallFailure.NOT_AN_APK
    if (apkPackageName != expectedPackage) return InstallFailure.WRONG_PACKAGE
    if (apkVersionCode <= installedVersionCode) return InstallFailure.NOT_NEWER
    return null
}

/** True when [file] is a readable zip archive holding an Android manifest. */
fun isValidApkArchive(file: File): Boolean {
    if (!file.isFile || file.length() == 0L) return false
    return try {
        ZipFile(file).use { zip ->
            zip.getEntry("AndroidManifest.xml") != null
        }
    } catch (e: Exception) {
        false
    }
}

/**
 * Compares the signing certificates of the downloaded APK and the installed app.
 * Returns null when they are compatible or when one side cannot be read.
 */
fun verifySignature(archiveDigests: Set<String>, installedDigests: Set<String>): InstallFailure? {
    if (archiveDigests.isEmpty() || installedDigests.isEmpty()) return null
    return if (archiveDigests.any { installedDigests.contains(it) }) null else InstallFailure.SIGNATURE_MISMATCH
}

/**
 * Verifies a downloaded APK and builds the install intent when the APK is safe to install.
 */
object ApkInstaller {

    /** Verifies [file] against the installed app. Returns null when the APK is installable. */
    fun verifyApk(context: Context, file: File): InstallFailure? {
        if (!file.isFile || file.length() == 0L) return InstallFailure.ASSET_MISSING
        if (!isValidApkArchive(file)) return InstallFailure.NOT_AN_APK
        val pm = context.packageManager
        val archive = try {
            pm.getPackageArchiveInfo(file.absolutePath, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: Exception) {
            null
        } ?: return InstallFailure.NOT_AN_APK
        val failure = verifyApkMetadata(
            archive.packageName,
            archive.longVersionCode,
            BuildConfig.APPLICATION_ID,
            BuildConfig.VERSION_CODE.toLong()
        )
        if (failure != null) return failure
        val installed = try {
            pm.getPackageInfo(BuildConfig.APPLICATION_ID, PackageManager.GET_SIGNING_CERTIFICATES)
        } catch (e: Exception) {
            null
        }
        return verifySignature(signingDigests(archive), installed?.let { signingDigests(it) } ?: emptySet())
    }

    /** True when the user already allowed this app to install packages. */
    fun canInstall(context: Context): Boolean {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return true
        return context.packageManager.canRequestPackageInstalls()
    }

    /** Settings screen that grants the unknown sources permission to this app. */
    fun unknownSourcesIntent(context: Context): Intent {
        return Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES)
            .setData(Uri.parse("package:${context.packageName}"))
    }

    /**
     * Builds the install intent for [file] using a FileProvider uri. Returns null when the
     * file does not exist.
     */
    fun installIntent(context: Context, file: File): Intent? {
        if (!file.isFile) return null
        val uri = FileProvider.getUriForFile(
            context, "${context.packageName}.fileprovider", file
        )
        return Intent(Intent.ACTION_VIEW)
            .setDataAndType(uri, "application/vnd.android.package-archive")
            .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    }

    private fun signingDigests(info: PackageInfo): Set<String> {
        val signingInfo = info.signingInfo ?: return emptySet()
        val signatures = if (signingInfo.hasMultipleSigners()) {
            signingInfo.apkContentsSigners
        } else {
            signingInfo.signingCertificateHistory
        } ?: return emptySet()
        return signatures.mapNotNull { signature ->
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                digest.update(signature.toByteArray())
                digest.digest().joinToString("") { "%02x".format(it) }
            } catch (e: Exception) {
                null
            }
        }.toSet()
    }
}
