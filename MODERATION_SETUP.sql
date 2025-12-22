-- MODERATION FEATURE SETUP
-- Jalankan di Supabase SQL Editor

-- 1. Update table profiles untuk support role admin
-- (Sudah ada dari awal, tapi pastikan)
ALTER TABLE public.profiles
  ALTER COLUMN role SET DEFAULT 'member';

-- Verify role constraint
ALTER TABLE public.profiles
  DROP CONSTRAINT IF EXISTS profiles_role_check;

ALTER TABLE public.profiles
  ADD CONSTRAINT profiles_role_check
  CHECK (role IN ('member', 'admin', 'moderator'));

-- 2. Buat tabel untuk admin accounts (hardcoded)
CREATE TABLE IF NOT EXISTS public.admin_accounts (
  id UUID PRIMARY KEY DEFAULT gen_random_uuid(),
  profile_id UUID REFERENCES public.profiles(id) ON DELETE CASCADE NOT NULL,
  email TEXT NOT NULL UNIQUE,
  granted_at TIMESTAMP WITH TIME ZONE DEFAULT timezone('utc'::text, now()) NOT NULL,
  granted_by TEXT, -- Email admin yang memberikan akses
  notes TEXT
);

-- 3. Enable RLS untuk admin_accounts
ALTER TABLE public.admin_accounts ENABLE ROW LEVEL SECURITY;

-- 4. Policies untuk admin_accounts
-- Hanya admin yang bisa lihat daftar admin
CREATE POLICY "Admin can view admin list"
ON public.admin_accounts FOR SELECT
USING (
  EXISTS (
    SELECT 1 FROM public.profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  )
);

-- Hanya super admin yang bisa insert admin baru (nanti via API)
CREATE POLICY "Super admin can create admin"
ON public.admin_accounts FOR INSERT
WITH CHECK (
  EXISTS (
    SELECT 1 FROM public.profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  )
);

-- 5. Update policies untuk comments - tambah delete permission
-- Drop policy lama
DROP POLICY IF EXISTS "Users can delete own comments" ON public.comments;

-- Buat policy baru: User bisa delete own comment ATAU thread owner bisa delete any comment
CREATE POLICY "Users can delete own comments OR thread owner can delete"
ON public.comments FOR DELETE
USING (
  -- User bisa hapus comment sendiri
  auth.uid() = user_id
  OR
  -- ATAU thread owner bisa hapus comment di thread nya
  EXISTS (
    SELECT 1 FROM public.threads
    WHERE threads.id = comments.thread_id
    AND threads.user_id = auth.uid()
  )
  OR
  -- ATAU admin bisa hapus any comment
  EXISTS (
    SELECT 1 FROM public.profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  )
);

-- 6. Update policies untuk threads - admin bisa delete any thread
DROP POLICY IF EXISTS "Users can delete own threads" ON public.threads;

CREATE POLICY "Users can delete own threads OR admin can delete"
ON public.threads FOR DELETE
USING (
  auth.uid() = user_id
  OR
  EXISTS (
    SELECT 1 FROM public.profiles
    WHERE profiles.id = auth.uid()
    AND profiles.role = 'admin'
  )
);

-- 7. Function untuk promote user jadi admin (manual via SQL)
CREATE OR REPLACE FUNCTION promote_to_admin(user_email TEXT)
RETURNS VOID AS $$
DECLARE
  target_user_id UUID;
BEGIN
  -- Cari user berdasarkan email
  SELECT id INTO target_user_id
  FROM auth.users
  WHERE email = user_email;

  IF target_user_id IS NULL THEN
    RAISE EXCEPTION 'User with email % not found', user_email;
  END IF;

  -- Update role di profiles
  UPDATE public.profiles
  SET role = 'admin'
  WHERE id = target_user_id;

  -- Insert ke admin_accounts
  INSERT INTO public.admin_accounts (profile_id, email, notes)
  VALUES (target_user_id, user_email, 'Promoted via SQL function')
  ON CONFLICT (email) DO NOTHING;

  RAISE NOTICE 'User % promoted to admin', user_email;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- 8. Function untuk demote admin
CREATE OR REPLACE FUNCTION demote_from_admin(user_email TEXT)
RETURNS VOID AS $$
DECLARE
  target_user_id UUID;
BEGIN
  SELECT id INTO target_user_id
  FROM auth.users
  WHERE email = user_email;

  IF target_user_id IS NULL THEN
    RAISE EXCEPTION 'User with email % not found', user_email;
  END IF;

  -- Update role ke member
  UPDATE public.profiles
  SET role = 'member'
  WHERE id = target_user_id;

  -- Remove dari admin_accounts
  DELETE FROM public.admin_accounts
  WHERE profile_id = target_user_id;

  RAISE NOTICE 'User % demoted to member', user_email;
END;
$$ LANGUAGE plpgsql SECURITY DEFINER;

-- ========================================
-- CARA PAKAI:
-- ========================================

-- Promote user jadi admin (jalankan sebagai admin di SQL Editor):
-- SELECT promote_to_admin('admin@example.com');

-- Demote admin jadi member:
-- SELECT demote_from_admin('admin@example.com');

-- Cek siapa saja yang admin:
-- SELECT p.username, p.email, aa.granted_at, aa.notes
-- FROM admin_accounts aa
-- JOIN profiles p ON p.id = aa.profile_id;

-- ========================================
-- NOTES:
-- ========================================
-- 1. Thread owner BISA hapus comment di thread mereka (moderasi)
-- 2. User BISA hapus comment sendiri
-- 3. Admin BISA hapus any comment dan any thread
-- 4. Untuk promote admin: jalankan SELECT promote_to_admin('email@example.com');

