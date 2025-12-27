# Imgr - Mobile Imageboard Application

Android imageboard application built with Jetpack Compose and Supabase backend.

## 📱 Tech Stack

- **Language**: Kotlin
- **UI Framework**: Jetpack Compose (Material 3)
- **Architecture**: MVVM (Model-View-ViewModel)
- **Backend**: Supabase
  - Auth (GoTrue) - User authentication
  - Postgrest - Database queries
  - Storage - Image uploads
- **Image Loading**: Coil
- **Image Compression**: Zelory Compressor
- **Navigation**: Jetpack Navigation Compose
- **HTTP Client**: Ktor

## ✨ Features

### Authentication
- ✅ Sign up with auto-generated username (`anon-XXXXXXXX`)
- ✅ Sign in with email/password
- ✅ Forgot password (email reset link)
- ✅ Auto-login (session persistence)
- ✅ **"Tetap Masuk" toggle** - Enable/disable auto-login
- ✅ Logout with confirmation

### Thread Management
- ✅ View thread feed with pagination (20 items per page)
- ✅ Infinite scroll
- ✅ Pull-to-refresh
- ✅ Search threads (by title, caption, username)
- ✅ Create thread with image (camera/gallery)
- ✅ Image validation (JPG/PNG, max 2MB)
- ✅ Auto image compression (target: 500KB, quality 80%, max 1024x1024px)
- ✅ Delete thread (owner/admin only)

### Comments
- ✅ View comments (newest first - descending order)
- ✅ Post comment (max 500 characters)
- ✅ Delete comment (owner/thread owner/admin)

### UI/UX
- ✅ **Splash Screen** - Animated "IMGR" branding on app launch
- ✅ Dark theme (Electric Violet color scheme)
- ✅ Bottom navigation (Feed, Search, Create)
- ✅ **Settings Dialog** - Tetap Masuk toggle + Logout button
- ✅ Skeleton loading states
- ✅ Responsive layouts
- ✅ Confirmation dialogs for destructive actions

### Permissions System
- ✅ Row Level Security (RLS) via Supabase
- ✅ Thread owner can delete own thread
- ✅ Thread owner can moderate comments in their thread
- ✅ Admin can delete any thread/comment

## 🎨 Design

### Theme
- Primary Color: `#D0BCFF` (Electric Violet)
- Background: `#141218` (Dark)
- Surface: `#2B2930` (Dark Gray)
- **Consistent dark theme** throughout the app

### Screens
1. **Splash Screen** - Fade in animation with IMGR branding
2. **Auth Screen** - Login/Register with email validation
3. **Forgot Password** - Password reset via email
4. **Home Screen** - Thread feed with search and username display
5. **Detail Screen** - Thread detail with comments
6. **Create Screen** - Create thread with image picker
7. **Search Screen** - Filter threads by keywords
8. **Settings Dialog** - Stay logged in toggle and logout

## 🔧 Recent Updates (December 2025)

### Bug Fixes
- ✅ Fixed TopAppBar spacing (removed excess padding)
- ✅ Fixed username not displaying in TopAppBar
- ✅ Fixed comment sorting (changed to descending - newest first)
- ✅ Fixed "Tetap Masuk" preference not persisting on app restart

### New Features
- ✅ Added Settings Dialog with:
  - "Tetap Masuk" toggle (auto-login control)
  - Logout button (red color)
- ✅ Added Splash Screen with fade animation
- ✅ Implemented preference persistence with SharedPreferences

### Technical Improvements
- ✅ Changed `AuthViewModel` to `AndroidViewModel` for Application context access
- ✅ Load preferences in ViewModel init for proper auto-login behavior
- ✅ Clear session when "Tetap Masuk" is OFF

## 📁 Project Structure

```
app/src/main/java/com/sample/image_board/
├── data/
│   ├── model/
│   │   ├── Models.kt - Data classes (Thread, Comment, Profile)
│   │   └── Result.kt - Result wrapper (Success/Error)
│   └── repository/
│       ├── AuthRepository.kt - Authentication logic
│       └── ThreadRepository.kt - Thread & comment operations
├── viewmodel/
│   ├── AuthViewModel.kt - Auth state management
│   ├── HomeViewModel.kt - Feed state management
│   ├── DetailViewModel.kt - Thread detail state
│   └── CreateThreadViewModel.kt - Create thread flow
├── ui/
│   ├── splash/
│   │   └── SplashScreen.kt - Animated splash screen
│   ├── auth/
│   │   ├── AuthScreen.kt - Login/Register UI
│   │   └── ForgotPasswordScreen.kt - Password reset UI
│   ├── home/
│   │   └── HomeScreen.kt - Feed with search & settings
│   ├── detail/
│   │   └── DetailScreen.kt - Thread & comments
│   ├── create/
│   │   └── CreateThreadScreen.kt - Create thread UI
│   ├── search/
│   │   └── SearchScreen.kt - Search functionality
│   ├── main/
│   │   └── MainScreen.kt - Bottom navigation
│   ├── navigation/
│   │   └── AppNavigation.kt - Navigation graph
│   └── theme/
│       ├── Theme.kt - Material 3 theme
│       ├── Color.kt - Color definitions
│       └── Type.kt - Typography
└── utils/
    ├── SupabaseClient.kt - Supabase configuration
    ├── ImageCompressor.kt - Image compression utility
    └── PreferenceManager.kt - SharedPreferences wrapper
```

## 🚀 Setup

### Prerequisites
- Android Studio (latest version)
- Kotlin 1.9+
- Android SDK 24+ (Nougat)
- Supabase account

### Configuration

1. Clone the repository
2. Create `local.properties` in project root:
```properties
SUPABASE_URL=your_supabase_url_here
SUPABASE_KEY=your_supabase_anon_key_here
```

3. Set up Supabase:
   - Create a project in Supabase
   - Create tables: `threads`, `comments`, `profiles`
   - Enable Row Level Security (RLS)
   - Create storage bucket for images
   - Enable email authentication

4. Build and run the app

## 🔐 Environment Variables

Required in `local.properties`:
- `SUPABASE_URL` - Your Supabase project URL
- `SUPABASE_KEY` - Your Supabase anon/public key

These are injected into `BuildConfig` at compile time.

## 📸 Screenshots

### Splash Screen
- Animated "IMGR" branding with fade in effect
- Automatically routes to Login or Main based on auth state

### Home Screen
- Compact header with app title and current username
- Search functionality
- Settings icon for preferences
- Thread cards with image thumbnails
- Pull-to-refresh and infinite scroll

### Settings Dialog
- "Tetap Masuk" toggle switch
- Red logout button
- Clean Material 3 design

### Thread Detail
- Full image display
- Comments section (newest first)
- Add comment input with character counter
- Delete options for authorized users

## 🎯 Permissions

### App Permissions
- `INTERNET` - Network access
- `ACCESS_NETWORK_STATE` - Network state checking
- `CAMERA` - Take photos
- `READ_MEDIA_IMAGES` - Access gallery

### User Roles
- **User** - Can create threads, post comments, delete own content
- **Thread Owner** - Can delete own thread and moderate comments
- **Admin** - Full access to delete any content

## 🔄 Data Flow

1. **Authentication**
   - User signs up → Auto-generated username → Profile created via SQL trigger
   - Session saved to secure storage (if "Tetap Masuk" ON)
   - Auto-login on app restart

2. **Thread Feed**
   - Paginated loading (20 threads per page)
   - Infinite scroll triggers next page
   - Batched comment counts for performance
   - RLS enforces permissions

3. **Image Upload**
   - Validate format and size (client-side)
   - Compress image (target 500KB)
   - Upload to Supabase Storage
   - Store URL in database

4. **Preferences**
   - "Tetap Masuk" saved to SharedPreferences
   - Loaded in ViewModel init
   - Controls auto-login behavior

## 🐛 Known Issues

None currently. All reported bugs have been fixed.

## 📝 License

This project is for educational/portfolio purposes.

## 👨‍💻 Development Notes

### Code Style
- Kotlin conventions
- Jetpack Compose best practices
- MVVM architecture pattern
- Repository pattern for data layer

### State Management
- StateFlow for reactive state
- ViewModelScope for coroutines
- Single source of truth

### Performance
- Image compression before upload
- Pagination for large datasets
- Efficient RLS queries
- Coil for async image loading

## 🔮 Future Enhancements

Potential features for future development:
- User profiles with avatars
- Notifications for new comments
- Thread categories/tags
- Vote system (upvote/downvote)
- Bookmarks/favorites
- Dark/Light theme toggle
- Reply to specific comments
- Image gallery support (multiple images)
- Report/flag content
- User blocking

---

**Last Updated**: December 27, 2025
