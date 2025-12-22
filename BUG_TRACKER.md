# 🐛 BUG TRACKER - IMAGEBOARD APP

**Last Updated:** 11 Desember 2025

---

## 🔴 CRITICAL BUGS (BLOCKER)

### BUG-001: Foreign Key Constraint Violation on Comments
**Status:** 🔴 OPEN  
**Priority:** P0 (Critical)  
**Severity:** Blocker - Users cannot post comments

#### Description:
Saat user mencoba post comment, muncul error:
```
insert or update on table "comments" violates foreign key constraint
```

#### Steps to Reproduce:
1. Login dengan user yang sudah terdaftar
2. Buka feed, klik salah satu thread
3. Scroll ke bawah ke comment section
4. Ketik comment di input box
5. Klik tombol Send
6. Error muncul di Toast

#### Expected Behavior:
- Comment berhasil terpost
- Muncul di list comments
- Counter bertambah

#### Actual Behavior:
- Toast error muncul
- Comment tidak masuk database
- App tidak crash

#### Technical Details:
**File:** `ThreadRepository.kt` line ~145
```kotlin
suspend fun postComment(threadId: String, content: String) {
    val userId = supabase.auth.currentSessionOrNull()?.user?.id
        ?: throw Exception("User belum login")
    
    supabase.from("comments").insert(
        mapOf(
            "thread_id" to threadId,
            "user_id" to userId,  // ❌ userId not in profiles table
            "content" to content
        )
    )
}
```

#### Root Cause Analysis:
**Hypothesis 1:** Trigger SQL `handle_new_user()` gagal execute
- User register berhasil di `auth.users`
- Trigger seharusnya auto-create row di `profiles`
- Trigger mungkin fail silently
- User ID tidak ada di `profiles` table

**Hypothesis 2:** UUID mismatch
- `auth.users.id` menggunakan UUID format tertentu
- `profiles.id` expect format yang berbeda
- Insert comment pakai `auth.users.id` yang tidak match

#### Verification Query:
```sql
-- Cek user di auth.users
SELECT id, email FROM auth.users WHERE email = 'test@example.com';

-- Cek apakah user ada di profiles
SELECT id, username FROM profiles WHERE id = 'UUID_FROM_ABOVE';

-- Cek foreign key constraint
SELECT
  tc.constraint_name,
  kcu.column_name,
  ccu.table_name AS foreign_table_name,
  ccu.column_name AS foreign_column_name
FROM information_schema.table_constraints AS tc
JOIN information_schema.key_column_usage AS kcu
  ON tc.constraint_name = kcu.constraint_name
JOIN information_schema.constraint_column_usage AS ccu
  ON ccu.constraint_name = tc.constraint_name
WHERE tc.table_name = 'comments' AND tc.constraint_type = 'FOREIGN KEY';
```

#### Proposed Fix:

**Option A: Add Fallback Profile Creation**
```kotlin
suspend fun postComment(threadId: String, content: String) {
    val userId = supabase.auth.currentSessionOrNull()?.user?.id
        ?: throw Exception("User belum login")
    
    // ✅ Ensure profile exists
    ensureProfileExists(userId)
    
    supabase.from("comments").insert(...)
}

private suspend fun ensureProfileExists(userId: String) {
    val exists = supabase.from("profiles")
        .select { filter { eq("id", userId) } }
        .decodeSingleOrNull<Profile>() != null
    
    if (!exists) {
        // Create missing profile
        val user = supabase.auth.currentUserOrNull()
        supabase.from("profiles").insert(
            mapOf(
                "id" to userId,
                "username" to generateAnonUsername(),
                "full_name" to generateAnonUsername(),
                "role" to "member"
            )
        )
    }
}
```

**Option B: Fix Trigger SQL**
```sql
-- Recreate trigger dengan error logging
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER
LANGUAGE plpgsql
SECURITY DEFINER
AS $$
BEGIN
  -- Log untuk debug
  RAISE NOTICE 'Creating profile for user: %', new.id;
  
  INSERT INTO public.profiles (id, username, full_name, role)
  VALUES (
    new.id,
    COALESCE(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)),
    new.raw_user_meta_data->>'full_name',
    'member'
  );
  
  RAISE NOTICE 'Profile created successfully for user: %', new.id;
  RETURN new;
EXCEPTION
  WHEN OTHERS THEN
    RAISE EXCEPTION 'Failed to create profile: %', SQLERRM;
    RETURN NULL;
END;
$$;
```

#### Assigned To: -
#### ETA: -

---

### BUG-002: Auto-Login After Logout
**Status:** 🔴 OPEN  
**Priority:** P1 (High)  
**Severity:** Major - UX breaking

#### Description:
Setelah user logout, aplikasi otomatis login lagi dan kembali ke home screen.

#### Steps to Reproduce:
1. Login dengan user valid
2. Masuk ke home screen
3. Klik icon logout di top bar
4. Dialog konfirmasi muncul
5. Klik "Keluar"
6. Expected: Redirect ke login screen
7. Actual: Kembali ke home screen (auto-login)

#### Expected Behavior:
- User logout
- Session cleared
- Navigate ke login screen
- Tidak auto-login

#### Actual Behavior:
- User klik logout
- Dialog muncul ✅
- User confirm logout
- Session cleared (maybe) ✅
- Navigate ke login... ❌
- Auto-login happens
- Back to home screen

#### Technical Details:
**File:** `AuthViewModel.kt`

**Flow:**
```
confirmLogout()
    ↓
skipAutoCheck = true
    ↓
repository.signOut()
    ↓
_authState.value = AuthState.Idle
    ↓
--- Navigation happens ---
    ↓
AuthViewModel re-initialized (?)
    ↓
checkSession() called in init
    ↓
skipAutoCheck = false already (not persistent)
    ↓
session exists (not cleared properly?)
    ↓
_authState.value = AuthState.Success
    ↓
Navigate to Home again
```

#### Root Cause:
1. `skipAutoCheck` tidak persistent
2. ViewModel mungkin di-recreate saat navigation
3. Session token tidak cleared dari persistent storage
4. Navigation logic di `AppNavigation` observe `AuthState.Success`

#### Proposed Fix:

**Option A: Use Shared ViewModel Scope**
```kotlin
// Di AppNavigation.kt
@Composable
fun AppNavigation() {
    val navController = rememberNavController()
    // ✅ Share ViewModel across navigation
    val authViewModel: AuthViewModel = viewModel(
        viewModelStoreOwner = LocalContext.current as ComponentActivity
    )
    
    val authState by authViewModel.authState.collectAsState()
    
    // ✅ Navigate based on auth state
    LaunchedEffect(authState) {
        when (authState) {
            is AuthState.Idle -> {
                navController.navigate(Screen.Login.route) {
                    popUpTo(0) { inclusive = true }
                    launchSingleTop = true
                }
            }
            is AuthState.Success -> {
                if (navController.currentDestination?.route != Screen.Home.route) {
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
        startDestination = Screen.Login.route
    ) { ... }
}
```

**Option B: Clear Session More Aggressively**
```kotlin
fun confirmLogout() {
    _showLogoutDialog.value = false
    viewModelScope.launch {
        try {
            // ✅ Clear session dari Supabase
            repository.signOut()
            
            // ✅ Clear local storage manually
            // (jika Supabase tidak clear otomatis)
            
            // ✅ Set state AFTER clearing session
            delay(100)  // Wait for async clear
            _authState.value = AuthState.Idle
            
        } catch (e: Exception) {
            _authState.value = AuthState.Error("Logout gagal: ${e.message}")
        }
    }
}
```

**Option C: Add Logout Flag in Repository**
```kotlin
// AuthRepository.kt
object AuthRepository {
    private var isLoggedOut = false
    
    fun getCurrentSession(): UserSession? {
        if (isLoggedOut) return null  // ✅ Override session check
        return authClient.currentSessionOrNull()
    }
    
    suspend fun signOut() {
        isLoggedOut = true
        authClient.signOut()
    }
    
    suspend fun signIn(...) {
        isLoggedOut = false  // Reset flag
        authClient.signInWith(...)
    }
}
```

#### Test Case:
```kotlin
@Test
fun `logout should clear session and navigate to login`() {
    // Given: User is logged in
    viewModel.signIn("test@example.com", "password")
    assertEquals(AuthState.Success, viewModel.authState.value)
    
    // When: User logout
    viewModel.confirmLogout()
    
    // Then: Session cleared
    delay(500)
    assertEquals(AuthState.Idle, viewModel.authState.value)
    assertNull(repository.getCurrentSession())
}
```

#### Assigned To: -
#### ETA: -

---

## 🟡 MAJOR BUGS

### BUG-003: Shimmer Effect Not Animated
**Status:** 🟡 OPEN  
**Priority:** P2 (Medium)  
**Severity:** Minor - UX polish

#### Description:
Skeleton loading di feed screen ada `shimmerEffect()` modifier tapi tidak animated.

#### Location:
**File:** `HomeScreen.kt` line ~374

```kotlin
fun Modifier.shimmerEffect(): Modifier {
    // TODO: Implementation
    return this  // ❌ No animation
}
```

#### Expected:
Shimmer effect dengan alpha animation (loading skeleton effect)

#### Actual:
Static gray boxes (no animation)

#### Proposed Fix:
```kotlin
@Composable
fun Modifier.shimmerEffect(): Modifier = composed {
    val infiniteTransition = rememberInfiniteTransition(label = "shimmer")
    val alpha by infiniteTransition.animateFloat(
        initialValue = 0.2f,
        targetValue = 0.9f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 1000),
            repeatMode = RepeatMode.Reverse
        ),
        label = "alpha"
    )
    background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = alpha))
}
```

#### Priority Justification:
- Tidak blocking functionality
- UX polish
- Nice to have

---

## 🟢 MINOR ISSUES

### ISSUE-001: No Network Timeout Handling
**Status:** 🟢 OPEN  
**Priority:** P3 (Low)  
**Severity:** Minor

#### Description:
Upload image besar bisa timeout tanpa proper error message.

#### Proposed Fix:
Add timeout config di SupabaseClient dan retry mechanism.

---

### ISSUE-002: BuildConfig Hardcoded
**Status:** 🟢 OPEN  
**Priority:** P3 (Low)  
**Severity:** Minor - Security concern

#### Location:
**File:** `SupabaseClient.kt`

#### Description:
API keys hardcoded di source code instead of using BuildConfig.

#### Current:
```kotlin
private object Config {
    const val SUPABASE_URL = "https://swytclwaagjfpbnyyiqr.supabase.co"
    const val SUPABASE_KEY = "eyJhbGc..." // ❌ Hardcoded
}
```

#### Proposed:
```kotlin
private object Config {
    val SUPABASE_URL = BuildConfig.SUPABASE_URL
    val SUPABASE_KEY = BuildConfig.SUPABASE_KEY
}
```

---

### ISSUE-003: No Upload Progress Indicator
**Status:** 🟢 OPEN  
**Priority:** P3 (Low)  
**Severity:** Minor - UX

#### Description:
Saat upload gambar besar, user tidak tahu progress (cuma loading spinner).

#### Proposed:
Add progress state dan linear progress indicator.

---

## 📊 BUG STATISTICS

### By Severity:
- 🔴 Critical: 2 bugs
- 🟡 Major: 1 bug
- 🟢 Minor: 3 issues

### By Status:
- ❌ Open: 6
- 🔄 In Progress: 0
- ✅ Fixed: 0
- 🚫 Won't Fix: 0

### By Priority:
- P0 (Blocker): 1
- P1 (High): 1
- P2 (Medium): 1
- P3 (Low): 3

---

## 🎯 NEXT ACTIONS

### Immediate (This Week):
1. [ ] Fix BUG-001 (Foreign Key Constraint) - BLOCKER
2. [ ] Fix BUG-002 (Auto-Login After Logout) - HIGH

### Short Term (This Month):
3. [ ] Implement BUG-003 (Shimmer Effect) - MEDIUM
4. [ ] Add network timeout handling - LOW
5. [ ] Add upload progress - LOW

### Long Term:
6. [ ] Security: Move to BuildConfig
7. [ ] Add error logging/monitoring
8. [ ] Add analytics

---

**Template for New Bug:**

```markdown
### BUG-XXX: [Title]
**Status:** 🔴/🟡/🟢 OPEN  
**Priority:** P0/P1/P2/P3  
**Severity:** Blocker/Major/Minor

#### Description:
[Explain the bug]

#### Steps to Reproduce:
1. Step 1
2. Step 2
3. Step 3

#### Expected Behavior:
[What should happen]

#### Actual Behavior:
[What actually happens]

#### Technical Details:
**File:** [Filename] line [number]
```
[Code snippet]
```

#### Root Cause:
[Analysis]

#### Proposed Fix:
[Solution]

#### Assigned To: -
#### ETA: -
```

