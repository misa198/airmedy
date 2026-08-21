package me.misa198.airmedy.sync

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import java.io.File
import java.security.MessageDigest
import me.misa198.airmedy.sync.StagedPlaylistArtwork

/** Converts picker content into the one format accepted by playlist reconciliation. */
internal fun stagePlaylistArtwork(contentResolver: ContentResolver, filesDir: File, uri: Uri): StagedPlaylistArtwork {
    val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
    val boundsStream = contentResolver.openInputStream(uri) ?: error("Unable to open playlist artwork")
    boundsStream.use { BitmapFactory.decodeStream(it, null, bounds) }
    require(bounds.outWidth > 0 && bounds.outHeight > 0) { "Selected file is not an image" }
    var sample = 1
    while (bounds.outWidth / sample > 2048 || bounds.outHeight / sample > 2048) sample *= 2
    val bitmap = contentResolver.openInputStream(uri)?.use {
        BitmapFactory.decodeStream(it, null, BitmapFactory.Options().apply { inSampleSize = sample })
    } ?: error("Unable to decode playlist artwork")
    val bytes = java.io.ByteArrayOutputStream().use { output ->
        check(bitmap.compress(Bitmap.CompressFormat.JPEG, 90, output)) { "Unable to encode playlist artwork" }
        output.toByteArray()
    }
    bitmap.recycle()
    val hash = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }
    val relativePath = "playlist-artwork/$hash.jpg"
    val target = File(filesDir, relativePath)
    if (!target.isFile) {
        target.parentFile?.mkdirs()
        val temporary = File(target.parentFile, "$hash.tmp")
        temporary.outputStream().use { it.write(bytes) }
        check(temporary.renameTo(target)) { "Unable to save playlist artwork" }
    }
    return StagedPlaylistArtwork(hash, "image/jpeg", bytes.size.toLong(), relativePath)
}
