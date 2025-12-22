-- SUPABASE DATABASE SETUP UNTUK IMAGEBOARD APP
g-- Schema yang benar sesuai dengan aplikasi
-- Jalankan di Supabase SQL Editor

-- 1. Bersihkan sisa-sisa kegagalan sebelumnya (Reset)
DROP TRIGGER IF EXISTS on_auth_user_created ON auth.users;
DROP FUNCTION IF EXISTS public.handle_new_user();
DROP TABLE IF EXISTS public.comments;
DROP TABLE IF EXISTS public.threads;
DROP TABLE IF EXISTS public.profiles;

-- 2. Buat Table Profiles (Linked ke auth.users)
CREATE TABLE public.profiles (
  id UUID REFERENCES auth.users(id) ON DELETE CASCADE NOT NULL PRIMARY KEY,
  username TEXT UNIQUE,
  full_name TEXT,
  avatar_url TEXT,
  role TEXT DEFAULT 'member' CHECK (role IN ('member', 'admin')),
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  updated_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 3. Buat Table Threads (Postingan)
CREATE TABLE public.threads (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  title TEXT NOT NULL,
  caption TEXT,
  image_url TEXT NOT NULL, -- Wajib ada karena ini Image Board
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 4. Buat Table Comments
CREATE TABLE public.comments (
  id UUID DEFAULT gen_random_uuid() PRIMARY KEY,
  thread_id UUID REFERENCES public.threads(id) ON DELETE CASCADE NOT NULL,
  user_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  content TEXT NOT NULL,
  created_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL
);

-- 5. Aktifkan Row Level Security (RLS) - Wajib biar gak jebol
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

-- 6. Setup Policies (Aturan Main)

-- PROFILES POLICIES
CREATE POLICY "Public profiles are viewable by everyone"
ON public.profiles FOR SELECT USING (true);

CREATE POLICY "Users can insert their own profile"
ON public.profiles FOR INSERT WITH CHECK (auth.uid() = id);

CREATE POLICY "Users can update own profile"
ON public.profiles FOR UPDATE USING (auth.uid() = id);

-- THREADS POLICIES
CREATE POLICY "Threads are viewable by everyone"
ON public.threads FOR SELECT USING (true);

CREATE POLICY "Authenticated users can create threads"
ON public.threads FOR INSERT WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "Users can update own threads"
ON public.threads FOR UPDATE USING (auth.uid() = user_id);

CREATE POLICY "Users can delete own threads"
ON public.threads FOR DELETE USING (auth.uid() = user_id);

-- COMMENTS POLICIES
CREATE POLICY "Comments are viewable by everyone"
ON public.comments FOR SELECT USING (true);

CREATE POLICY "Authenticated users can create comments"
ON public.comments FOR INSERT WITH CHECK (auth.role() = 'authenticated');

CREATE POLICY "Users can delete own comments"
ON public.comments FOR DELETE USING (auth.uid() = user_id);

-- 7. TRIGGER FUNCTION (Otomatis sync User Auth -> Profile)
-- Logic: Ambil username dari metadata, kalau kosong ambil dari email (sebelum @)
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

-- Pasang Trigger ke table auth.users
CREATE TRIGGER on_auth_user_created
  AFTER INSERT ON auth.users
  FOR EACH ROW EXECUTE PROCEDURE public.handle_new_user();

-- 8. Indexes untuk performance
CREATE INDEX IF NOT EXISTS idx_threads_created_at ON public.threads(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_user_id ON public.threads(user_id);
CREATE INDEX IF NOT EXISTS idx_comments_thread_id ON public.comments(thread_id);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON public.comments(created_at);

-- DONE! 🎉
-- Sekarang setup Supabase Storage:
-- 1. Buka Storage di dashboard Supabase
-- 2. Buat bucket baru nama "images"
-- 3. Set bucket jadi Public
-- 4. Storage policies:
--    - INSERT: authenticated users only
--    - SELECT: public (anyone can view)

-- Optional: Insert dummy data untuk testing
-- Uncomment jika diperlukan
/*
-- Contoh insert thread (harus ada user dulu dari signup)
INSERT INTO public.threads (user_id, title, caption, image_url)
VALUES (
  'YOUR-USER-UUID-HERE',
  'Welcome to ImageBoard!',
  'This is the first post',
  'https://via.placeholder.com/400'
);
*/
CREATE OR REPLACE FUNCTION decrement_comment_count()
RETURNS TRIGGER AS $$
BEGIN
    UPDATE public.threads
    SET comment_count = GREATEST(comment_count - 1, 0)
    WHERE id = OLD.thread_id;
    RETURN OLD;
END;
$$ LANGUAGE plpgsql;

-- 7. Trigger untuk auto decrement
DROP TRIGGER IF EXISTS on_comment_deleted ON public.comments;
CREATE TRIGGER on_comment_deleted
    AFTER DELETE ON public.comments
    FOR EACH ROW
    EXECUTE FUNCTION decrement_comment_count();

-- 8. Enable Row Level Security (RLS)
ALTER TABLE public.profiles ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.threads ENABLE ROW LEVEL SECURITY;
ALTER TABLE public.comments ENABLE ROW LEVEL SECURITY;

-- 9. RLS Policies untuk Profiles
-- Semua orang bisa read profiles
CREATE POLICY "Public profiles are viewable by everyone"
    ON public.profiles FOR SELECT
    USING (true);

-- User hanya bisa update profile sendiri
CREATE POLICY "Users can update own profile"
    ON public.profiles FOR UPDATE
    USING (auth.uid() = id);

-- 10. RLS Policies untuk Threads
-- Semua orang bisa read threads
CREATE POLICY "Threads are viewable by everyone"
    ON public.threads FOR SELECT
    USING (true);

-- Authenticated user bisa create thread
CREATE POLICY "Authenticated users can create threads"
    ON public.threads FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- User bisa delete thread sendiri
CREATE POLICY "Users can delete own threads"
    ON public.threads FOR DELETE
    USING (auth.uid() = user_id);

-- 11. RLS Policies untuk Comments
-- Semua orang bisa read comments
CREATE POLICY "Comments are viewable by everyone"
    ON public.comments FOR SELECT
    USING (true);

-- Authenticated user bisa create comment
CREATE POLICY "Authenticated users can create comments"
    ON public.comments FOR INSERT
    WITH CHECK (auth.uid() = user_id);

-- User bisa delete comment sendiri
CREATE POLICY "Users can delete own comments"
    ON public.comments FOR DELETE
    USING (auth.uid() = user_id);

-- 12. Indexes untuk performance
CREATE INDEX IF NOT EXISTS idx_threads_created_at ON public.threads(created_at DESC);
CREATE INDEX IF NOT EXISTS idx_threads_user_id ON public.threads(user_id);
CREATE INDEX IF NOT EXISTS idx_comments_thread_id ON public.comments(thread_id);
CREATE INDEX IF NOT EXISTS idx_comments_created_at ON public.comments(created_at);

-- 13. DUMMY DATA untuk testing (Optional)
-- Uncomment jika mau insert dummy data

-- INSERT INTO public.profiles (id, username, full_name) VALUES
-- ('00000000-0000-0000-0000-000000000001', 'testuser1', 'Test User 1'),
-- ('00000000-0000-0000-0000-000000000002', 'testuser2', 'Test User 2');

-- INSERT INTO public.threads (user_id, title, content, comment_count) VALUES
-- ('00000000-0000-0000-0000-000000000001', 'Welcome to ImageBoard!', 'This is the first thread. Enjoy!', 2),
-- ('00000000-0000-0000-0000-000000000002', 'How to use this app?', 'Can someone explain the features?', 1);

-- INSERT INTO public.comments (thread_id, user_id, content)
-- SELECT
--     (SELECT id FROM public.threads LIMIT 1),
--     '00000000-0000-0000-0000-000000000002',
--     'Welcome! This app is awesome!';

-- DONE! 🎉
-- Sekarang setup Supabase Storage:
-- 1. Buka Storage di dashboard Supabase
-- 2. Buat bucket baru nama "images"
-- 3. Set bucket jadi Public
-- 4. Storage policies:
--    - INSERT: authenticated users only
--    - SELECT: public (anyone can view)

