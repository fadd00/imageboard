package com.sample.image_board.viewmodel

import android.content.Context
import android.net.Uri
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.sample.image_board.data.repository.ThreadRepository
import com.sample.image_board.utils.ImageCompressor
import com.sample.image_board.data.model.Result
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch

sealed interface CreateThreadState {
    data object Idle : CreateThreadState
    data object Loading : CreateThreadState
    data object Success : CreateThreadState
    data class Error(val message: String) : CreateThreadState
}

// Data class untuk validasi hasil
data class ImageValidationResult(
    val isValid: Boolean,
    val errorMessage: String? = null,
    val fileSizeKB: Long? = null,
    val format: String? = null
)

class CreateThreadViewModel : ViewModel() {

    private val repository = ThreadRepository()

    private val _createState = MutableStateFlow<CreateThreadState>(CreateThreadState.Idle)
    val createState = _createState.asStateFlow()

    // State untuk image yang dipilih
    private val _selectedImageUri = MutableStateFlow<Uri?>(null)
    val selectedImageUri = _selectedImageUri.asStateFlow()

    // State untuk validation info
    private val _imageInfo = MutableStateFlow<ImageValidationResult?>(null)
    val imageInfo = _imageInfo.asStateFlow()

    // Constants untuk validasi
    companion object {
        const val MAX_FILE_SIZE_MB = 2
        const val MAX_FILE_SIZE_BYTES = MAX_FILE_SIZE_MB * 1024 * 1024 // 2MB
        const val MAX_CAPTION_LENGTH = 500

        // Compression settings
        const val COMPRESSION_TARGET_KB = 500L // Target 500KB setelah kompres
        const val COMPRESSION_QUALITY = 80
        const val MAX_IMAGE_WIDTH = 1024
        const val MAX_IMAGE_HEIGHT = 1024
    }

    /**
     * Set image URI yang dipilih dari kamera/galeri
     */
    fun setSelectedImage(uri: Uri?, context: Context) {
        _selectedImageUri.value = uri

        if (uri != null) {
            // Validasi image
            viewModelScope.launch {
                val validation = validateImage(context, uri)
                _imageInfo.value = validation
            }
        } else {
            _imageInfo.value = null
        }
    }

    /**
     * Validasi format dan ukuran gambar
     */
    private fun validateImage(context: Context, uri: Uri): ImageValidationResult {
        try {
            val contentResolver = context.contentResolver

            // 1. Validasi MIME Type (format)
            val mimeType = contentResolver.getType(uri)
            val format = when (mimeType) {
                "image/jpeg", "image/jpg" -> "JPG"
                "image/png" -> "PNG"
                else -> return ImageValidationResult(
                    isValid = false,
                    errorMessage = "Format tidak didukung. Hanya JPG/PNG yang diperbolehkan."
                )
            }

            // 2. Validasi ukuran file
            val inputStream = contentResolver.openInputStream(uri)
            val fileSize = inputStream?.available()?.toLong() ?: 0L
            inputStream?.close()

            val fileSizeKB = fileSize / 1024

            // Jika lebih dari 2MB, akan dikompres otomatis saat upload
            if (fileSize > MAX_FILE_SIZE_BYTES) {
                return ImageValidationResult(
                    isValid = true,
                    errorMessage = "Ukuran ${fileSizeKB}KB akan dikompres menjadi ~${COMPRESSION_TARGET_KB}KB",
                    fileSizeKB = fileSizeKB,
                    format = format
                )
            }

            return ImageValidationResult(
                isValid = true,
                errorMessage = null,
                fileSizeKB = fileSizeKB,
                format = format
            )

        } catch (e: Exception) {
            return ImageValidationResult(
                isValid = false,
                errorMessage = "Gagal membaca file: ${e.message}"
            )
        }
    }

    /**
     * Create thread dengan validasi lengkap
     */
    fun createThread(
        context: Context,
        title: String,
        caption: String,
        imageUri: Uri?
    ) {
        viewModelScope.launch {
            _createState.value = CreateThreadState.Loading

            // 1. Validasi Title
            if (title.isBlank()) {
                _createState.value = CreateThreadState.Error("Judul tidak boleh kosong")
                return@launch
            }

            if (title.length < 3) {
                _createState.value = CreateThreadState.Error("Judul minimal 3 karakter")
                return@launch
            }

            // 2. Validasi Caption (Opsional tapi ada batas)
            if (caption.length > MAX_CAPTION_LENGTH) {
                _createState.value = CreateThreadState.Error(
                    "Caption maksimal $MAX_CAPTION_LENGTH karakter"
                )
                return@launch
            }

            // 3. Validasi Image (WAJIB)
            if (imageUri == null) {
                _createState.value = CreateThreadState.Error("Gambar wajib dipilih")
                return@launch
            }

            // 4. Validasi format image
            val validation = validateImage(context, imageUri)
            if (!validation.isValid) {
                _createState.value = CreateThreadState.Error(
                    validation.errorMessage ?: "Gambar tidak valid"
                )
                return@launch
            }

            try {
                // 5. Kompres image jika perlu
                val imageByteArray = ImageCompressor.compressImage(
                    context = context,
                    uri = imageUri,
                    maxSizeKB = COMPRESSION_TARGET_KB,
                    quality = COMPRESSION_QUALITY,
                    maxWidth = MAX_IMAGE_WIDTH,
                    maxHeight = MAX_IMAGE_HEIGHT
                )

                // Log ukuran setelah kompres
                val compressedSizeKB = ImageCompressor.getFileSizeKB(imageByteArray)
                println("📦 Image compressed: ${validation.fileSizeKB}KB → ${compressedSizeKB}KB")

                // 6. Upload ke repository
                when (val result = repository.createThread(
                    title = title.trim(),
                    caption = caption.trim().takeIf { it.isNotEmpty() },
                    imageData = imageByteArray
                )) {
                    is Result.Success -> {
                        _createState.value = CreateThreadState.Success
                    }
                    is Result.Error -> {
                        _createState.value = CreateThreadState.Error(
                            result.exception.message ?: "Gagal membuat thread"
                        )
                    }
                }
            } catch (e: Exception) {
                _createState.value = CreateThreadState.Error(
                    e.message ?: "Gagal memproses gambar"
                )
            }
        }
    }

    /**
     * Reset state
     */
    fun resetState() {
        _createState.value = CreateThreadState.Idle
        _selectedImageUri.value = null
        _imageInfo.value = null
    }
}

