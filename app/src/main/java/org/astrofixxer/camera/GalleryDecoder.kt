package org.astrofixxer.camera

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.media.ExifInterface
import android.net.Uri
import org.astrofixxer.astro.GrayImage
import org.astrofixxer.astro.Luminance
import kotlin.math.max

/**
 * Reads a picture the person chose in the photo picker and turns it into brightness for the solver: decoded at reduced
 * size (never bigger than about twice what the solver uses), turned upright by its EXIF orientation, converted to luma and
 * shrunk to at most [Luminance.MAX_LONG_SIDE] pixels. The picture is only read, never copied or saved anywhere.
 */
internal object GalleryDecoder {
    /** The picture as a [GrayImage], or null when it cannot be read (not an image, damaged, too big for memory). */
    fun decode(resolver: ContentResolver, uri: Uri): GrayImage? = try {
        val bounds = BitmapFactory.Options().apply { inJustDecodeBounds = true }
        resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, bounds) }
        if (bounds.outWidth <= 0 || bounds.outHeight <= 0) null else {
            // A power-of-two reduction that keeps the long side at 1600 pixels or more.
            var sample = 1
            while (max(bounds.outWidth, bounds.outHeight) / (sample * 2) >= Luminance.MAX_LONG_SIDE) sample *= 2
            val opts = BitmapFactory.Options().apply { inSampleSize = sample; inPreferredConfig = Bitmap.Config.ARGB_8888 }
            val bmp = resolver.openInputStream(uri)?.use { BitmapFactory.decodeStream(it, null, opts) }
            if (bmp == null) null else {
                try {
                    val row = IntArray(bmp.width)
                    Luminance.fromRows(bmp.width, bmp.height, quarterTurns(resolver, uri)) { y, out ->
                        bmp.getPixels(row, 0, bmp.width, 0, y, bmp.width, 1)
                        Luminance.argbRow(row, out, bmp.width)
                    }
                } finally {
                    bmp.recycle()
                }
            }
        }
    } catch (e: Exception) {
        null
    } catch (e: OutOfMemoryError) {
        null
    }

    /** Clockwise quarter turns that make the picture upright, from its EXIF orientation (0 when there is none). Mirroring is ignored: the solver finds mirrored pictures itself. */
    private fun quarterTurns(resolver: ContentResolver, uri: Uri): Int = try {
        resolver.openInputStream(uri)?.use { stream ->
            when (ExifInterface(stream).getAttributeInt(ExifInterface.TAG_ORIENTATION, ExifInterface.ORIENTATION_NORMAL)) {
                ExifInterface.ORIENTATION_ROTATE_90, ExifInterface.ORIENTATION_TRANSPOSE -> 1
                ExifInterface.ORIENTATION_ROTATE_180, ExifInterface.ORIENTATION_FLIP_VERTICAL -> 2
                ExifInterface.ORIENTATION_ROTATE_270, ExifInterface.ORIENTATION_TRANSVERSE -> 3
                else -> 0
            }
        } ?: 0
    } catch (e: Exception) {
        0
    }
}
