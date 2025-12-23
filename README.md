# 📱 Imgr - Android Imageboard App

**Imgr** adalah aplikasi Android forum berbasis gambar (seperti 4chan) yang memungkinkan user untuk posting thread dengan gambar wajib dan memberikan komentar.

## 🚀 Tech Stack

| Layer | Teknologi |
|-------|-----------|
| **Language** | Kotlin |
| **UI Framework** | Jetpack Compose + Material Design 3 |
| **Architecture** | MVVM + Clean Architecture |
| **Backend** | Supabase (PostgreSQL + GoTrue Auth + Storage) |
| **Network** | Ktor Client |
| **Image Loading** | Coil |
| **Image Compression** | Zelory Compressor |

## ✨ Fitur

- **Authentication** - Register & login dengan email/password, username auto-generate (`anon-XXXXXXXX`)
- **Feed** - List thread dengan thumbnail kecil (style 4chan), infinite scroll, pull-to-refresh
- **Create Thread** - Upload gambar dari kamera/galeri, auto-compress, title + caption
- **Thread Detail** - Full image, list comments, post comment
- **Moderation** - Thread owner bisa delete comment di thread-nya, admin bisa delete semua

## 📁 Struktur Project

```
app/src/main/java/com/sample/image_board/
├── data/
│   ├── model/          # Data models (Thread, Comment, Profile)
│   └── repository/     # AuthRepository, ThreadRepository
├── ui/
│   ├── auth/           # Login/Register screen
│   ├── home/           # Feed screen (HomeScreen)
│   ├── create/         # Create thread screen
│   ├── detail/         # Thread detail screen
│   ├── navigation/     # Navigation setup
│   └── theme/          # Material theme
├── utils/
│   ├── ImageCompressor.kt
│   └── SupabaseClient.kt
└── viewmodel/          # ViewModels (MVVM)
```

## ⚙️ Setup

### 1. Clone Repository
```bash
git clone <repo-url>
cd imageboard
```

### 2. Setup Supabase
1. Buat project di [supabase.com](https://supabase.com)
2. Copy SQL dari bagian [Database Setup](#database-setup) dan jalankan di SQL Editor
3. Copy URL dan Anon Key dari Settings > API

### 3. Configure Local Properties
Buat file `local.properties` di root project:
```properties
sdk.dir=/path/to/android/sdk
SUPABASE_URL=https://YOUR-PROJECT.supabase.co
SUPABASE_KEY=eyJhbGciOiJIUzI1NiIsInR5cCI6IkpXVCJ9...
```

### 4. Build & Run
```bash
./gradlew assembleDebug
./gradlew installDebug
```

---

## 🗄️ Database Setup

Jalankan SQL berikut di Supabase SQL Editor:

### 1. Core Tables
```sql
-- Bersihkan jika ada
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();
DROP TABLE IF EXISTS public.comments;
DROP TABLE IF EXISTS public.threads;
DROP TABLE IF EXISTS public.profiles;

-- Table Profiles
CREATE TABLE public.profiles (
  id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL PRIMARY KEY,
  username TEXT UNIQUE,
  full_name TEXT,
  avatar_url TEXT,
  role TEXT DEFAULT 'member' CHECK (role IN ('member', 'admin', 'moderator')),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Table Threads
CREATE TABLE public.threads (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  title TEXT NOT NULL,
  caption TEXT,
  image_url TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Table Comments
CREATE TABLE public.comments (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  thread_id UUID REFERENCES public.threads(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- Enable RLS
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;
```

### 2. RLS Policies
```sql
-- PROFILES
CREATE POLICY "Public profiles are viewable by everyone"
  ON public.profiles FOR SELECT USING (true);
CREATE POLICY "Users can insert their own profile"
  ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);
CREATE POLICY "Users can update own profile"
  ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- THREADS
CREATE POLICY "Threads are viewable by everyone"
  ON public.threads FOR SELECT USING (true);
CREATE POLICY "Authenticated users can create threads"
  ON public.threads FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Users can delete own threads OR admin can delete"
  ON public.threads FOR DELETE USING (
    auth.uid() = user_id OR
    EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role = 'admin')
  );

-- COMMENTS
CREATE POLICY "Comments are viewable by everyone"
  ON public.comments FOR SELECT USING (true);
CREATE POLICY "Authenticated users can create comments"
  ON public.comments FOR INSERT WITH CHECK (auth.role() = 'authenticated');
CREATE POLICY "Users can delete own comments OR thread owner can delete"
  ON public.comments FOR DELETE USING (
    auth.uid() = user_id OR
    EXISTS (SELECT 1 FROM public.threads WHERE threads.id = comments.thread_id AND threads.user_id = auth.uid()) OR
    EXISTS (SELECT 1 FROM public.profiles WHERE profiles.id = auth.uid() AND profiles.role = 'admin')
  );
```

### 3. Auto-Create Profile Trigger
```sql
CREATE OR REPLACE FUNCTION public.handle_new_user()
RETURNS TRIGGER AS $$
BEGIN
  INSERT INTO public.profiles (id, username, full_name, role)
  VALUES (
    new.id,
    COALESCE(new.raw_user_meta_data->>'username', split_part(new.email, '@', 1)),
    new.raw_user_meta_data->>'full_name',
    'member'
  );
  RETURN new;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();
```

### 4. Comment Count Function
```sql
CREATE OR REPLACE FUNCTION get_comment_counts(thread_ids_array uuid[])
RETURNS JSON
LANGUAGE plpgsql AS $$
DECLARE
    counts_json JSON;
BEGIN
    SELECT json_object_agg(thread_id, comment_count)
    INTO counts_json
    FROM (
        SELECT thread_id, COUNT(*) as comment_count
        FROM comments WHERE thread_id = ANY(thread_ids_array)
        GROUP BY thread_id
    ) AS comment_counts;
    RETURN COALESCE(counts_json, '{}'::json);
END;
$$;
```

### 5. Indexes
```sql
CREATE INDEX IF NOT EXISTS idx_threads_created_at ON public.threads(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_user_id ON public.threads(user_id);
CREATE INDEX IF NOT EXISTS idx_comments_thread_id ON public.comments(thread_id);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON public.comments(created_at);
```

---

## 📦 Storage Setup

### 1. Create Bucket
```sql
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'images', 'images', true, 5242880,
    ARRAY['image/jpeg', 'image/jpg', 'image/png']
) ON CONFLICT (id) DO NOTHING;
```

### 2. Storage Policies
```sql
-- Public read
CREATE POLICY "Public Read Access"
  ON storage.objects FOR SELECT TO public
  USING (bucket_id = 'images');

-- Authenticated upload
CREATE POLICY "Authenticated Upload"
  ON storage.objects FOR INSERT TO authenticated
  WITH CHECK (bucket_id = 'images');

-- Delete own images
CREATE POLICY "Users Delete Own Images"
  ON storage.objects FOR DELETE TO authenticated
  USING (bucket_id = 'images' AND auth.uid()::text = (storage.foldername(name))[1]);
```

---

## 👑 Admin Management

### Promote User to Admin
```sql
SELECT promote_to_admin('email@example.com');
```

### Demote Admin to Member
```sql
SELECT demote_from_admin('email@example.com');
```

### Promote/Demote Functions
```sql
CREATE OR REPLACE FUNCTION promote_to_admin(user_email TEXT)
RETURNS VOID AS $$
DECLARE target_user_id UUID;
BEGIN
  SELECT id INTO target_user_id FROM auth.users WHERE email = user_email;
  IF target_user_id IS NULL THEN RAISE EXCEPTION 'User not found'; END IF;
  UPDATE public.profiles SET role = 'admin' WHERE id = target_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

CREATE OR REPLACE FUNCTION demote_from_admin(user_email TEXT)
RETURNS VOID AS $$
DECLARE target_user_id UUID;
BEGIN
  SELECT id INTO target_user_id FROM auth.users WHERE email = user_email;
  IF target_user_id IS NULL THEN RAISE EXCEPTION 'User not found'; END IF;
  UPDATE public.profiles SET role = 'member' WHERE id = target_user_id;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;
```

---

## 🐛 Known Issues & TODO (Next Patch)

### BUGS - Priority 0 (Critical)

| ID | Issue | Location | Description |
|----|-------|----------|-------------|
| BUG-001 | Comment FK Constraint | `ThreadRepository.postComment()` | Comment posting may fail due to foreign key constraint violation on some edge cases |
| BUG-002 | Auto-Login After Logout | `AuthViewModel.checkSession()` | Session may persist after logout - `skipAutoCheck` flag exists but may not cover all cases |

### BUGS - Priority 1 (High)

| ID | Issue | Location | Description |
|----|-------|----------|-------------|
| BUG-003 | Title Field Still Queried | `ThreadRepository.getThreadsPaginated()` | Still selecting `title` column even though SRS removed it |
| BUG-004 | Search Filters on Title | `HomeViewModel.filterThreads()` | `thread.title` still used in search filter (line 285) |
| BUG-005 | CreateThread Sends Title | `ThreadRepository.createThread()` | Still accepts and sends `title` param to DB (line 248-266) |

### CODE INCONSISTENCIES

| Issue | Location | Fix |
|-------|----------|-----|
| Deprecated API Warning | `ThreadRepository.getAllThreads()` | Remove or update deprecated method |
| Unused Title Parameter | `CreateThreadViewModel.createThread()` | Remove `title` param completely |
| Mixed Language | Various UI files | Standardize to English or Indonesian |
| Search Bar in Feed | `HomeScreen.kt` | Search is in separate screen but search bar logic still exists in Feed |

### SECURITY NOTES

> ⚠️ **Email Auth Disabled for Testing**
> Supabase email confirmation is currently disabled. Enable before production:
> - Supabase Dashboard → Authentication → Email → Enable "Confirm email"

| Category | Status | Notes |
|----------|--------|-------|
| RLS Policies | ✅ Active | Row Level Security enabled on all tables |
| Image Upload | ✅ Validated | MIME type check + size limit in app + storage |
| Input Validation | ⚠️ Partial | Client-side only, add server-side validation |
| API Keys | ⚠️ Review | Check if anon key exposure is acceptable |
| Session Handling | ⚠️ Review | Test token refresh and expiry handling |

### TODO - Features

- [ ] Remove `title` column usage from all code
- [ ] Update database schema if `title` column exists
- [ ] Add timestamp display to posts
- [ ] Implement profile page (future enhancement)
- [ ] Add image zoom in detail view
- [ ] Dark/Light theme toggle
- [ ] Push notifications for comments
- [ ] Rate limiting for comment posting
- [ ] Report/flag content feature

### TODO - Technical Debt

- [ ] Remove all deprecated methods in `ThreadRepository`
- [ ] Standardize error messages (English/Indonesian)
- [ ] Add unit tests for ViewModels
- [ ] Add instrumented tests for UI
- [ ] Implement proper error handling with retry logic
- [ ] Add offline mode support (Room + WorkManager)

---

## 📝 License

MIT License

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open a Pull Request
