# 📱 DOKUMENTASI LENGKAP - IMAGEBOARD APP

**Tanggal Dokumentasi:** 11 Desember 2025  
**Platform:** Android (Kotlin + Jetpack Compose)  
**Backend:** Supabase (PostgreSQL + Storage + Auth)

---

## 📋 DAFTAR ISI

1. [Ringkasan Aplikasi](#ringkasan-aplikasi)
2. [Fitur yang Sudah Diimplementasikan](#fitur-yang-sudah-diimplementasikan)
3. [Arsitektur Aplikasi](#arsitektur-aplikasi)
4. [Struktur Database](#struktur-database)
5. [Bug dan Masalah yang Diketahui](#bug-dan-masalah-yang-diketahui)
6. [Konfigurasi dan Setup](#konfigurasi-dan-setup)
7. [Dependencies](#dependencies)
8. [Flow Aplikasi](#flow-aplikasi)
9. [API dan Integrasi](#api-dan-integrasi)
10. [Rekomendasi Perbaikan](#rekomendasi-perbaikan)

---

## 🎯 RINGKASAN APLIKASI

ImageBoard adalah aplikasi Android mirip forum/imageboard (seperti 4chan) di mana user dapat:
- Membuat akun dengan email & password
- Posting thread dengan gambar wajib
- Memberikan komentar pada thread
- Melihat feed postingan terbaru

### Teknologi Stack:
- **Frontend:** Kotlin, Jetpack Compose, Material Design 3
- **Backend:** Supabase (PostgreSQL + GoTrue Auth + Storage)
- **Image Processing:** Zelory Compressor
- **Image Loading:** Coil
- **Network:** Ktor Client

---

## ✅ FITUR YANG SUDAH DIIMPLEMENTASIKAN

### 1. **Autentikasi & Akun** (HIGH PRIORITY) ✅

#### ✅ Implementasi:
- **Register:** Email + Password (min 6 karakter)
- **Auto-Generate Username:** Format `anon-XXXXXXXX` (8 karakter random)
- **Login:** Email + Password
- **Session Management:** 
  - Token JWT disimpan terenkripsi (`autoSaveToStorage = true`)
  - Auto-refresh token (handled by Supabase SDK)
  - Auto-login saat buka app (check current session di `AuthViewModel.init`)
- **Logout:** 
  - ✅ Ada dialog konfirmasi sebelum logout
  - ✅ Hapus session dari server & lokal
  - ⚠️ **BUG:** Auto-login setelah logout (lihat bagian Bug)

#### 📁 File Terkait:
```
- ui/auth/AuthScreen.kt
- viewmodel/AuthViewModel.kt
- data/repository/AuthRepository.kt
- ui/navigation/AppNavigation.kt
```

#### 🔑 Fitur Khusus:
- Email confirmation DISABLED (langsung bisa login setelah register)
- Username auto-generate (user tidak input manual)
- Error handling yang user-friendly:
  - "Email sudah terdaftar"
  - "Password minimal 6 karakter"
  - "Email atau password salah"

---

### 2. **Feed / Beranda** (HIGH PRIORITY) ✅

#### ✅ Implementasi:
- **Reverse-Chronological:** Postingan terbaru di atas (ORDER BY created_at DESC)
- **Pagination:** 
  - Load 20 items per page
  - Infinite scroll (auto load saat scroll 5 item dari bawah)
  - Loading state untuk first load dan load more
- **Pull-to-Refresh:** ✅ Swipe down untuk refresh
- **Loading State:**
  - Skeleton loading untuk first load
  - Circular progress untuk load more
  - Pull refresh indicator
- **Comment Counter:** Menampilkan jumlah komentar per thread

#### 📁 File Terkait:
```
- ui/home/HomeScreen.kt
- viewmodel/HomeViewModel.kt
- data/repository/ThreadRepository.kt
```

#### 🎨 UI Components:
- `ThreadCard` - Card untuk setiap thread di feed
- `ThreadCardSkeleton` - Skeleton loading dengan shimmer effect
- `PullRefreshIndicator` - Indicator untuk pull-to-refresh

#### ⚙️ State Management:
```kotlin
sealed interface HomeState {
    data object Loading        // First load
    data object LoadingMore    // Infinite scroll
    data object Refreshing     // Pull-to-refresh
    data class Success(threads, hasMore)
    data class Error(message)
}
```

---

### 3. **Posting Konten** (HIGH PRIORITY) ✅

#### ✅ Implementasi:
- **Sumber Gambar:**
  - ✅ Kamera (dengan permission request)
  - ✅ Galeri
  - Dialog pemilihan sumber
- **Validasi File:**
  - Format: JPG/PNG only (validated via MIME type)
  - Ukuran: Max 2MB (jika lebih, auto-compress)
  - Display info: Format, ukuran, status validasi
- **Kompresi Otomatis:**
  - Target: 500KB
  - Quality: 80%
  - Max resolution: 1024x1024
  - Library: Zelory Compressor
  - Fallback: Manual compression jika library gagal
- **Caption:** 
  - Optional
  - Max 500 karakter
  - Character counter
- **Title:**
  - Wajib
  - Min 3 karakter
  - Real-time validation

#### 📁 File Terkait:
```
- ui/create/CreateThreadScreen.kt
- viewmodel/CreateThreadViewModel.kt
- utils/ImageCompressor.kt
- data/repository/ThreadRepository.kt
```

#### 🖼️ Image Processing Flow:
```
URI → Validate → Compress → ByteArray → Upload to Storage → Get URL → Save to DB
```

#### 🔧 Compression Settings:
```kotlin
MAX_FILE_SIZE_MB = 2
COMPRESSION_TARGET_KB = 500
COMPRESSION_QUALITY = 80
MAX_IMAGE_WIDTH = 1024
MAX_IMAGE_HEIGHT = 1024
```

---

### 4. **Interaksi & Komentar** (MEDIUM PRIORITY) ✅

#### ✅ Implementasi:
- **List Komentar:**
  - Urutan: Kronologis (terlama di atas) - ORDER BY created_at ASC
  - Display: Username + content
  - Avatar placeholder (circular box)
- **Input Komentar:**
  - Teks only (no image)
  - Max 500 karakter
  - Character counter
  - Real-time validation
- **Counter:**
  - Jumlah komentar di Feed Card
  - Jumlah komentar di Detail Screen header
  - Counter update otomatis setelah post comment

#### 📁 File Terkait:
```
- ui/detail/DetailScreen.kt
- viewmodel/DetailViewModel.kt
- data/repository/ThreadRepository.kt
- data/model/Models.kt (Comment, CommentResponse)
```

#### 🎨 UI Components:
- `ThreadDetailCard` - Detail thread dengan image full
- `CommentItem` - Individual comment item
- `CommentInputBar` - Fixed input bar di bawah dengan send button

#### ⚠️ **BUG KRITIS:**
- Foreign key constraint violation saat insert comment
- Error: `violates foreign key constraint`
- Kemungkinan penyebab: Mismatch antara user_id dari auth.users vs profiles table

---

### 5. **Detail Thread** ✅

#### ✅ Implementasi:
- Full image preview (responsive height)
- Thread metadata: Title, username, date
- Caption (jika ada)
- List comments
- Comment count header
- Fixed comment input bar di bawah
- Back navigation

#### 📁 File Terkait:
```
- ui/detail/DetailScreen.kt
- viewmodel/DetailViewModel.kt
```

---

## 🏗️ ARSITEKTUR APLIKASI

### Layer Architecture (Clean Architecture-ish):

```
┌─────────────────────────────────────┐
│         UI Layer (Compose)          │
│   - Screens (Auth, Home, Create,    │
│     Detail)                          │
│   - Navigation (AppNavigation)      │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      ViewModel Layer (MVVM)         │
│   - AuthViewModel                   │
│   - HomeViewModel                   │
│   - CreateThreadViewModel           │
│   - DetailViewModel                 │
│   - State management (StateFlow)    │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Repository Layer               │
│   - AuthRepository                  │
│   - ThreadRepository                │
│   - Data mapping & validation       │
└──────────────┬──────────────────────┘
               │
┌──────────────▼──────────────────────┐
│      Data Layer                     │
│   - SupabaseClient (singleton)      │
│   - Models (serialization)          │
│   - Utils (ImageCompressor)         │
└─────────────────────────────────────┘
```

### State Management Pattern:

Menggunakan **MVI-like pattern** dengan StateFlow:

```kotlin
sealed interface ScreenState {
    data object Idle
    data object Loading
    data class Success(...)
    data class Error(message)
}

// ViewModel
private val _state = MutableStateFlow<ScreenState>(Idle)
val state = _state.asStateFlow()

// UI
val state by viewModel.state.collectAsState()
when (state) {
    is Loading -> ShowLoading()
    is Success -> ShowContent()
    is Error -> ShowError()
}
```

---

## 🗄️ STRUKTUR DATABASE

### Supabase PostgreSQL Schema:

#### 1. **Table: `profiles`**
```sql
CREATE TABLE public.profiles (
  id UUID PRIMARY KEY,              -- FK to auth.users(id)
  username TEXT UNIQUE,             -- Auto-generated: anon-XXXXXXXX
  full_name TEXT,
  avatar_url TEXT,
  role TEXT DEFAULT 'member',       -- 'member' | 'admin'
  created_at TIMESTAMP WITH TIME ZONE,
  updated_at TIMESTAMP WITH TIME ZONE
);
```

**Trigger:** Auto-create profile saat user register di `auth.users`

#### 2. **Table: `threads`**
```sql
CREATE TABLE public.threads (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  user_id UUID NOT NULL,            -- FK to profiles(id)
  title TEXT NOT NULL,
  caption TEXT,
  image_url TEXT NOT NULL,          -- Wajib (image board)
  created_at TIMESTAMP WITH TIME ZONE
);
```

#### 3. **Table: `comments`**
```sql
CREATE TABLE public.comments (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  thread_id UUID NOT NULL,          -- FK to threads(id)
  user_id UUID NOT NULL,            -- FK to profiles(id)
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE
);
```

### Row Level Security (RLS):

#### Profiles:
- ✅ Everyone can SELECT (view)
- ✅ User can INSERT own profile
- ✅ User can UPDATE own profile

#### Threads:
- ✅ Everyone can SELECT (view)
- ✅ Authenticated users can INSERT
- ✅ User can UPDATE own threads
- ✅ User can DELETE own threads

#### Comments:
- ✅ Everyone can SELECT (view)
- ✅ Authenticated users can INSERT
- ✅ User can DELETE own comments

### Storage Bucket:

**Bucket Name:** `images`

**Policies:**
1. **Public Access:** Everyone can SELECT (view images)
2. **Authenticated Upload:** Logged-in users can INSERT

**Settings:**
- Public: Yes
- File size limit: 2MB (enforced by app, not bucket)
- Allowed MIME types: image/jpeg, image/png

---

## 🐛 BUG DAN MASALAH YANG DIKETAHUI

### 🔴 CRITICAL BUGS:

#### 1. **Foreign Key Constraint Error pada Comments** 
**Status:** ❌ BELUM DIPERBAIKI

**Error Message:**
```
insert or update on table "comments" violates foreign key constraint
```

**Lokasi:** 
- File: `ThreadRepository.kt` - method `postComment()`
- File: `DetailViewModel.kt` - method `postComment()`

**Penyebab Kemungkinan:**
- User ID dari `auth.currentSession.user.id` tidak match dengan `profiles.id`
- Trigger SQL `handle_new_user()` gagal create profile saat register
- Mismatch UUID format

**Dampak:** 
- User tidak bisa posting comment
- App tidak crash, tapi muncul toast error

**Cara Reproduce:**
1. Login
2. Buka thread detail
3. Ketik comment
4. Klik send
5. Error muncul

**Solusi Temporary:**
Cek manual di Supabase:
```sql
-- Cek apakah user ada di profiles
SELECT * FROM profiles WHERE id = 'USER_UUID_DARI_AUTH';

-- Jika tidak ada, insert manual
INSERT INTO profiles (id, username, full_name, role)
VALUES ('USER_UUID', 'anon-xxxxxxxx', 'anon-xxxxxxxx', 'member');
```

**Solusi Permanen (Rekomendasi):**
- Debug trigger SQL `handle_new_user()`
- Pastikan trigger ALWAYS execute after user register
- Add error handling di `AuthRepository.signUp()`
- Add retry mechanism untuk create profile

---

#### 2. **Auto-Login Setelah Logout**
**Status:** ❌ BELUM DIPERBAIKI

**Behavior:**
1. User klik logout
2. Dialog konfirmasi muncul ✅
3. User klik "Keluar"
4. Session cleared ✅
5. Redirect ke login screen... ❌ GAGAL
6. Auto-login terjadi lagi (kembali ke home)

**Lokasi:**
- File: `AuthViewModel.kt` - methods `confirmLogout()` dan `checkSession()`
- File: `AppNavigation.kt` - navigation logic

**Penyebab:**
- `checkSession()` di `AuthViewModel.init` dipanggil ulang saat navigasi
- Flag `skipAutoCheck` tidak persistent
- Navigation state tidak clear session token dengan benar

**Dampak:**
- User tidak bisa logout dengan benar
- Pengalaman user buruk

**Solusi Temporary:**
```kotlin
// Di AuthViewModel
private var skipAutoCheck = false

fun confirmLogout() {
    skipAutoCheck = true  // Set flag
    repository.signOut()
    _authState.value = AuthState.Idle
}

private fun checkSession() {
    if (skipAutoCheck) {
        skipAutoCheck = false
        return  // Skip check
    }
    // ... check session normal
}
```

**Solusi Permanen (Rekomendasi):**
- Gunakan single source of truth untuk auth state
- Pindahkan auth check ke level aplikasi (MainActivity)
- Gunakan Navigation backstack clearing yang lebih agresif:
```kotlin
navController.navigate(Screen.Login.route) {
    popUpTo(0) { 
        inclusive = true
        saveState = false  // Jangan save state
    }
    launchSingleTop = true
    restoreState = false  // Jangan restore state
}
```

---

### 🟡 MINOR BUGS:

#### 3. **Skeleton Loading Tidak Ada shimmerEffect Implementation**
**Status:** ⚠️ INCOMPLETE

**Lokasi:** `HomeScreen.kt` - function `ThreadCardSkeleton()`

**Issue:**
```kotlin
Box(
    modifier = Modifier.shimmerEffect()  // ❌ Function defined tapi tidak implemented
)

fun Modifier.shimmerEffect(): Modifier {
    // TODO: Implementation
    return this  // Cuma return modifier kosong
}
```

**Dampak:**
- Skeleton loading tampil tapi tidak animated
- UX kurang smooth

**Solusi:**
Implement shimmer effect menggunakan Compose Animation:
```kotlin
fun Modifier.shimmerEffect(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition()
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(1000),
            repeatMode = RepeatMode.Reverse
        )
    )
    background(
        color = MaterialTheme.colorScheme.onSurface.copy(alpha = alpha)
    )
}
```

---

#### 4. **Tidak Ada Error Handling untuk Network Timeout**
**Status:** ⚠️ MISSING FEATURE

**Lokasi:** Semua Repository methods

**Issue:**
- Upload gambar besar bisa timeout
- Tidak ada retry mechanism
- Error message generic

**Dampak:**
- User bingung kenapa gagal upload
- Data loss (gambar yang sudah dikompres hilang)

**Solusi:**
- Add timeout configuration di SupabaseClient
- Add retry with exponential backoff
- Show progress indicator saat upload
- Save draft locally jika gagal

---

### 🟢 WARNINGS (Non-Breaking):

#### 5. **Deprecated Method Usage**
```kotlin
@Deprecated("Use getThreadsPaginated() instead")
suspend fun getAllThreads(): List<ThreadWithUser>
```
Status: ✅ OK (masih berfungsi, tapi tidak dipakai)

#### 6. **BuildConfig Hardcoded**
**File:** `SupabaseClient.kt`

```kotlin
private object Config {
    const val SUPABASE_URL = "https://swytclwaagjfpbnyyiqr.supabase.co"
    const val SUPABASE_KEY = "eyJhbGc..." // Hardcoded
}
```

**Rekomendasi:**
Gunakan BuildConfig (sudah disiapkan di build.gradle.kts):
```kotlin
BuildConfig.SUPABASE_URL
BuildConfig.SUPABASE_KEY
```

---

## ⚙️ KONFIGURASI DAN SETUP

### 1. **local.properties**
```properties
SUPABASE_URL=https://swytclwaagjfpbnyyiqr.supabase.co
SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 2. **build.gradle.kts (app level)**

**SDK Versions:**
- compileSdk: 36
- minSdk: 31
- targetSdk: 35

**Build Features:**
- Compose: ✅ Enabled
- BuildConfig: ✅ Enabled

**ProGuard:**
- Release: ❌ Disabled (isMinifyEnabled = false)

### 3. **Permissions (AndroidManifest.xml)**
```xml
<uses-permission android:name="android.permission.INTERNET" />
<uses-permission android:name="android.permission.CAMERA" />
<uses-permission android:name="android.permission.READ_EXTERNAL_STORAGE" />
<uses-permission android:name="android.permission.WRITE_EXTERNAL_STORAGE" />
```

### 4. **FileProvider Configuration**
Untuk kamera capture, perlu FileProvider config di AndroidManifest.xml:
```xml
<provider
    android:name="androidx.core.content.FileProvider"
    android:authorities="${applicationId}.fileprovider"
    android:exported="false"
    android:grantUriPermissions="true">
    <meta-data
        android:name="android.support.FILE_PROVIDER_PATHS"
        android:resource="@xml/file_paths" />
</provider>
```

---

## 📦 DEPENDENCIES

### Core Android:
```kotlin
androidx.core:core-ktx
androidx.lifecycle:lifecycle-runtime-ktx
androidx.lifecycle:lifecycle-viewmodel-compose
androidx.activity:activity-compose
```

### Compose:
```kotlin
androidx.compose.bom
androidx.compose.ui
androidx.compose.material3
androidx.compose.material (untuk PullRefresh)
androidx.compose.material-icons-extended
androidx.navigation:navigation-compose
```

### Supabase:
```kotlin
io.github.jan-tennert.supabase:postgrest-kt
io.github.jan-tennert.supabase:storage-kt
io.github.jan-tennert.supabase:gotrue-kt
```

### Ktor (HTTP Client):
```kotlin
io.ktor:ktor-client-cio
io.ktor:ktor-client-core
```

### Serialization:
```kotlin
org.jetbrains.kotlinx:kotlinx-serialization-json
```

### Image Processing:
```kotlin
io.coil-kt:coil-compose          // Image loading
id.zelory:compressor:3.0.1        // Image compression
```

---

## 🔄 FLOW APLIKASI

### 1. **Authentication Flow:**

```
App Launch
    ↓
AuthViewModel.init()
    ↓
checkSession()
    ↓
┌─ Session exists? ─┐
│  YES         │ NO │
↓              ↓
Home Screen    Login Screen
               ↓
        ┌──────┴──────┐
        │             │
    Sign In      Sign Up
        │             │
        └──── → ──────┘
               ↓
        Auto-generate username
               ↓
        Create profile (SQL trigger)
               ↓
        Session saved
               ↓
        Navigate to Home
```

### 2. **Create Thread Flow:**

```
Home Screen
    ↓
Klik FAB (+)
    ↓
CreateThreadScreen
    ↓
┌──── Choose Image Source ────┐
│   Camera     │    Gallery   │
└──────┬───────┴──────┬───────┘
       │              │
    Take Photo    Select Image
       │              │
       └──────┬───────┘
              ↓
       Validate Image
       (Format, Size)
              ↓
       Compress Image
       (Target: 500KB)
              ↓
       Show Preview
              ↓
    Enter Title + Caption
              ↓
       Click Post
              ↓
    Upload to Storage
    (Get public URL)
              ↓
    Save to Database
              ↓
    Navigate back to Home
    (Auto refresh feed)
```

### 3. **Comment Flow:**

```
Feed Screen
    ↓
Klik Thread Card
    ↓
DetailScreen
    ↓
Load thread detail + comments
    ↓
┌── Display ──┐
│ - Image     │
│ - Title     │
│ - Caption   │
│ - Comments  │
└─────────────┘
    ↓
Enter comment text
(Max 500 char)
    ↓
Click Send
    ↓
Validate input
    ↓
Post to database
    ↓
⚠️ BUG: Foreign key error
    ↓
Reload comments
```

### 4. **Feed Pagination Flow:**

```
HomeScreen Launch
    ↓
Load first 20 threads
    ↓
Display in LazyColumn
    ↓
User scrolls down
    ↓
Detect: 5 items before end?
    ↓ YES
Load next 20 threads
    ↓
Append to current list
    ↓
Update hasMore flag
    ↓
Continue until no more data
    ↓
Show "Sudah semua! 🎉"
```

---

## 🌐 API DAN INTEGRASI

### Supabase Endpoints:

#### 1. **Auth (GoTrue)**
```
POST /auth/v1/signup         - Register
POST /auth/v1/token          - Login
POST /auth/v1/logout         - Logout
GET  /auth/v1/user           - Get current user
POST /auth/v1/token?grant_type=refresh_token  - Refresh token
```

#### 2. **Database (Postgrest)**
```
GET  /rest/v1/threads        - List threads (with pagination)
  ?select=*,profiles!inner(username)
  &order=created_at.desc
  &offset=0&limit=20

GET  /rest/v1/threads?id=eq.UUID  - Get thread detail

GET  /rest/v1/comments       - List comments
  ?thread_id=eq.UUID
  &select=*,profiles!inner(username)
  &order=created_at.asc

POST /rest/v1/threads        - Create thread
POST /rest/v1/comments       - Create comment
```

#### 3. **Storage**
```
POST /storage/v1/object/images/{filename}  - Upload image
GET  /storage/v1/object/public/images/{filename}  - Get image URL
```

### Data Models:

#### Thread Model:
```kotlin
@Serializable
data class ThreadResponse(
    val id: String,
    val title: String,
    val caption: String?,
    @SerialName("image_url") val imageUrl: String,
    @SerialName("user_id") val userId: String,
    @SerialName("created_at") val createdAt: String,
    val profiles: ProfileUsername?
)

// Converted to:
data class ThreadWithUser(
    val id: String,
    val title: String,
    val content: String,        // caption
    val imageUrl: String,
    val userId: String,
    val createdAt: String,
    val commentCount: Int,      // loaded separately
    val userName: String        // from profiles
)
```

#### Comment Model:
```kotlin
@Serializable
data class CommentResponse(
    val id: String,
    @SerialName("thread_id") val threadId: String,
    @SerialName("user_id") val userId: String,
    val content: String,
    @SerialName("created_at") val createdAt: String,
    val profiles: ProfileUsername?
)

// Converted to:
data class Comment(
    val id: String,
    val threadId: String,
    val userId: String,
    val content: String,
    val createdAt: String,
    val userName: String        // from profiles
)
```

---

## 🔧 REKOMENDASI PERBAIKAN

### Priority 1 (URGENT):

#### 1. **Fix Foreign Key Constraint Error**
**File:** `data/repository/ThreadRepository.kt`

**Problem:**
```kotlin
suspend fun postComment(threadId: String, content: String) {
    val userId = supabase.auth.currentSessionOrNull()?.user?.id
        ?: throw Exception("User belum login")
    
    // ❌ userId mungkin tidak ada di profiles table
    supabase.from("comments").insert(...)
}
```

**Solution:**
```kotlin
suspend fun postComment(threadId: String, content: String) {
    val userId = supabase.auth.currentSessionOrNull()?.user?.id
        ?: throw Exception("User belum login")
    
    // ✅ Verify user exists in profiles first
    try {
        val profile = supabase.from("profiles")
            .select { filter { eq("id", userId) } }
            .decodeSingleOrNull<Profile>()
        
        if (profile == null) {
            // Auto-create profile if missing
            createProfileForUser(userId)
        }
        
        supabase.from("comments").insert(...)
    } catch (e: Exception) {
        throw Exception("Gagal posting comment: ${e.message}")
    }
}
```

---

#### 2. **Fix Auto-Login After Logout**
**File:** `ui/navigation/AppNavigation.kt`

**Solution:**
```kotlin
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    val authViewModel: AuthViewModel = viewModel()
    val authState by authViewModel.authState.collectAsState()
    
    // ✅ Observe auth state and navigate accordingly
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Idle -> {
                // User logged out, navigate to login
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthState.Success -> {
                // User logged in, navigate to home
                if (navController.currentDestination?.route == Screen.Login.route) {
                    navController.navigate(Screen.Home.route) {
                        popUpTo(Screen.Login.route) { inclusive = true }
                    }
                }
            }
            else -> {}
        }
    }
    
    NavHost(
        navController = navController,
        startDestination = Screen.Login.route  // ✅ Always start at login
    ) {
        // ... composable screens
    }
}
```

---

### Priority 2 (IMPORTANT):

#### 3. **Add Shimmer Effect Implementation**
**File:** `ui/home/HomeScreen.kt`

```kotlin
@Composable
fun Modifier.shimmerEffect(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "shimmer alpha"
    )
    background(color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
}
```

---

#### 4. **Add Network Error Handling**
**File:** `utils/SupabaseClient.kt`

```kotlin
object SupabaseClient {
    val client by lazy {
        createSupabaseClient(
            supabaseUrl = BuildConfig.SUPABASE_URL,
            supabaseKey = BuildConfig.SUPABASE_KEY
        ) {
            install(Auth) {
                autoSaveToStorage = true
            }
            install(Postgrest)
            install(Storage)
            
            // ✅ Add HTTP client configuration
            httpEngine {
                requestTimeout = 60_000  // 60 seconds
            }
        }
    }
}
```

**Add Retry Logic:**
```kotlin
suspend fun <T> retryIO(
    times: Int = 3,
    initialDelay: Long = 1000,
    factor: Double = 2.0,
    block: suspend () -> T
): T {
    var currentDelay = initialDelay
    repeat(times - 1) {
        try {
            return block()
        } catch (e: Exception) {
            delay(currentDelay)
            currentDelay = (currentDelay * factor).toLong()
        }
    }
    return block() // Last attempt
}

// Usage:
suspend fun uploadImage(...): String {
    return retryIO(times = 3) {
        // Upload logic
    }
}
```

---

#### 5. **Add Upload Progress Indicator**
**File:** `viewmodel/CreateThreadViewModel.kt`

```kotlin
// Add progress state
private val _uploadProgress = MutableStateFlow(0f)
val uploadProgress = _uploadProgress.asStateFlow()

fun createThread(...) {
    viewModelScope.launch {
        _createState.value = CreateThreadState.Loading
        
        // Show progress
        _uploadProgress.value = 0.3f  // Compression done
        
        val imageByteArray = ImageCompressor.compressImage(...)
        
        _uploadProgress.value = 0.6f  // Upload starting
        
        repository.createThread(...)
        
        _uploadProgress.value = 1.0f  // Complete
        
        _createState.value = CreateThreadState.Success
    }
}
```

---

### Priority 3 (NICE TO HAVE):

#### 6. **Add Image Caching**
Coil sudah support caching by default, tapi bisa di-configure:

```kotlin
// Di MainActivity onCreate()
val imageLoader = ImageLoader.Builder(this)
    .memoryCache {
        MemoryCache.Builder(this)
            .maxSizePercent(0.25)  // Use 25% of app memory
            .build()
    }
    .diskCache {
        DiskCache.Builder()
            .directory(cacheDir.resolve("image_cache"))
            .maxSizeBytes(50 * 1024 * 1024)  // 50MB
            .build()
    }
    .build()
```

---

#### 7. **Add Pull-to-Refresh Animation**
Sudah ada, tapi bisa ditingkatkan:

```kotlin
val pullRefreshState = rememberPullRefreshState(
    refreshing = isRefreshing,
    onRefresh = { 
        viewModel.refresh() 
        // ✅ Add haptic feedback
        view.performHapticFeedback(HapticFeedbackConstants.CONFIRM)
    },
    refreshThreshold = 80.dp,  // Custom threshold
    refreshingOffset = 100.dp  // Custom offset
)
```

---

#### 8. **Add Dark Mode Support**
Theme sudah ada, tinggal configure:

**File:** `ui/theme/Theme.kt`

```kotlin
@Composable
fun ImageboardTheme(
    darkTheme: Boolean = isSystemInDarkTheme(),  // ✅ Detect system
    content: @Composable () -> Unit
) {
    val colorScheme = when {
        darkTheme -> darkColorScheme(...)
        else -> lightColorScheme(...)
    }
    
    MaterialTheme(
        colorScheme = colorScheme,
        typography = Typography,
        content = content
    )
}
```

---

#### 9. **Add Timestamp Formatting**
Sekarang cuma raw timestamp string, lebih baik formatted:

```kotlin
// Add dependency
implementation("org.ocpsoft.prettytime:prettytime:5.0.7.Final")

// Usage
fun formatTimeAgo(timestamp: String): String {
    val instant = Instant.parse(timestamp)
    val date = Date.from(instant)
    val prettyTime = PrettyTime()
    return prettyTime.format(date)  // "2 hours ago"
}
```

---

#### 10. **Add Error Logging**
Untuk debug production issues:

```kotlin
// Add dependency
implementation("com.jakewharton.timber:timber:5.0.1")

// Di MainActivity onCreate()
if (BuildConfig.DEBUG) {
    Timber.plant(Timber.DebugTree())
}

// Usage di Repository
catch (e: Exception) {
    Timber.e(e, "Failed to post comment")
    throw Exception("Gagal posting comment: ${e.message}")
}
```

---

## 📊 STATISTIK KODE

### Total Files: 21 Kotlin files

#### By Category:
- **UI Screens:** 5 files (Auth, Home, Create, Detail, Upload)
- **ViewModels:** 4 files
- **Repositories:** 2 files
- **Models:** 1 file
- **Utils:** 3 files
- **Navigation:** 1 file
- **Theme:** 3 files
- **Tests:** 2 files

#### Lines of Code (estimasi):
- UI Layer: ~1,200 lines
- ViewModel Layer: ~600 lines
- Repository Layer: ~400 lines
- Utils: ~300 lines
- Total: ~2,500 lines

---

## 🎓 KESIMPULAN

### ✅ Yang Sudah Bagus:
1. Arsitektur Clean & MVVM pattern
2. State management dengan StateFlow
3. Image compression otomatis
4. Pagination & infinite scroll
5. Pull-to-refresh
6. Form validation
7. Error handling user-friendly
8. Material Design 3

### ❌ Yang Harus Diperbaiki URGENT:
1. **Foreign key error pada comments** (BLOCKER)
2. **Auto-login after logout** (UX Critical)

### ⚠️ Yang Perlu Ditingkatkan:
1. Shimmer effect implementation
2. Network timeout handling
3. Upload progress indicator
4. Image caching optimization
5. Dark mode support
6. Timestamp formatting
7. Error logging

### 📈 Next Steps:
1. Fix bug kritis (#1 dan #2)
2. Test thoroughly di real device
3. Add analytics/monitoring
4. Prepare for Play Store release
5. Write user documentation

---

**Dibuat oleh:** GitHub Copilot  
**Tanggal:** 11 Desember 2025  
**Versi Dokumentasi:** 1.0.0

