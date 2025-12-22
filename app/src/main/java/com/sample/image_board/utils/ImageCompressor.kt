package com.sample.image_board.utils

import android.content.Context
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import id.zelory.compressor.Compressor
import id.zelory.compressor.constraint.destination
import id.zelory.compressor.constraint.quality
import id.zelory.compressor.constraint.resolution
import id.zelory.compressor.constraint.size
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileOutputStream

object ImageCompressor {

    /**
     * Kompresi gambar dari URI menjadi ByteArray
     *
     * @param context Context aplikasi
     * @param uri URI gambar yang akan dikompres
     * @param maxSizeKB Ukuran maksimal dalam KB (default 500KB)
     * @param quality Kualitas kompresi 0-100 (default 80)
     * @param maxWidth Lebar maksimal dalam pixel (default 1024)
     * @param maxHeight Tinggi maksimal dalam pixel (default 1024)
     * @return ByteArray hasil kompresi
     */
    suspend fun compressImage(
        context: Context,
        uri: Uri,
        maxSizeKB: Long = 500, // 500KB max
        quality: Int = 80,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): ByteArray = withContext(Dispatchers.IO) {
        try {
            // Convert URI to File
            val imageFile = uriToFile(context, uri)

            // Compress menggunakan Compressor library
            val compressedFile = Compressor.compress(context, imageFile) {
                resolution(maxWidth, maxHeight)
                quality(quality)
                size(maxSizeKB * 1024) // Convert KB to Bytes

                // Simpan di cache dir dengan nama unik
                val outputFile = File(context.cacheDir, "compressed_${System.currentTimeMillis()}.jpg")
                destination(outputFile)
            }

            // Convert File to ByteArray
            val byteArray = compressedFile.readBytes()

            // Cleanup
            compressedFile.delete()
            if (imageFile.path.startsWith(context.cacheDir.path)) {
                imageFile.delete()
            }

            byteArray
        } catch (e: Exception) {
            e.printStackTrace()
            // Fallback: kompresi manual jika library gagal
            compressImageManual(context, uri, quality, maxWidth, maxHeight)
        }
    }

    /**
     * Fallback manual compression menggunakan Bitmap
     */
    private fun compressImageManual(
        context: Context,
        uri: Uri,
        quality: Int = 80,
        maxWidth: Int = 1024,
        maxHeight: Int = 1024
    ): ByteArray {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val originalBitmap = BitmapFactory.decodeStream(inputStream)
        inputStream.close()

        // Resize bitmap
        val scaledBitmap = scaleBitmap(originalBitmap, maxWidth, maxHeight)

        // Compress to JPEG
        val outputStream = ByteArrayOutputStream()
        scaledBitmap.compress(Bitmap.CompressFormat.JPEG, quality, outputStream)
        val byteArray = outputStream.toByteArray()

        // Cleanup
        outputStream.close()
        if (scaledBitmap != originalBitmap) {
            scaledBitmap.recycle()
        }
        originalBitmap.recycle()

        return byteArray
    }

    /**
     * Scale bitmap dengan mempertahankan aspect ratio
     */
    private fun scaleBitmap(bitmap: Bitmap, maxWidth: Int, maxHeight: Int): Bitmap {
        val width = bitmap.width
        val height = bitmap.height

        // Jika ukuran sudah kecil, return as is
        if (width <= maxWidth && height <= maxHeight) {
            return bitmap
        }

        // Calculate scale ratio
        val ratio = minOf(
            maxWidth.toFloat() / width,
            maxHeight.toFloat() / height
        )

        val newWidth = (width * ratio).toInt()
        val newHeight = (height * ratio).toInt()

        return Bitmap.createScaledBitmap(bitmap, newWidth, newHeight, true)
    }

    /**
     * Convert URI ke File
     */
    private fun uriToFile(context: Context, uri: Uri): File {
        val inputStream = context.contentResolver.openInputStream(uri)
            ?: throw IllegalArgumentException("Cannot open URI: $uri")

        val tempFile = File(context.cacheDir, "temp_image_${System.currentTimeMillis()}.jpg")
        val outputStream = FileOutputStream(tempFile)

        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()

        return tempFile
    }

    /**
     * Get file size dalam KB
     */
    fun getFileSizeKB(byteArray: ByteArray): Long {
        return (byteArray.size / 1024).toLong()
    }

    /**
     * Format ukuran file untuk display
     */
    fun formatFileSize(bytes: Long): String {
        return when {
            bytes < 1024 -> "$bytes B"
            bytes < 1024 * 1024 -> "${bytes / 1024} KB"
            else -> "${bytes / (1024 * 1024)} MB"
        }
    }
}

