-- ============================================
-- SUPABASE STORAGE BUCKET SETUP
-- Untuk Upload Gambar Thread
-- ============================================

-- 1. Buat Storage Bucket untuk Images
INSERT INTO storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
VALUES (
    'images',
    'images',
    true,  -- Public bucket (bisa diakses tanpa auth untuk read)
    5242880,  -- 5MB limit (5 * 1024 * 1024 bytes)
    ARRAY['image/jpeg', 'image/jpg', 'image/png']  -- Hanya JPG & PNG
)
ON CONFLICT (id) DO NOTHING;

-- 2. Enable RLS (Row Level Security) untuk bucket
ALTER TABLE storage.objects ENABLE ROW LEVEL SECURITY;

-- 3. Policy: Siapa saja bisa READ (public bucket)
CREATE POLICY "Public Access untuk Read Images"
ON storage.objects FOR SELECT
USING (bucket_id = 'images');

-- 4. Policy: User yang login bisa UPLOAD
CREATE POLICY "Authenticated users can upload images"
ON storage.objects FOR INSERT
WITH CHECK (
    bucket_id = 'images'
    AND auth.role() = 'authenticated'
);

-- 5. Policy: User bisa UPDATE file miliknya sendiri
CREATE POLICY "Users can update their own images"
ON storage.objects FOR UPDATE
USING (
    bucket_id = 'images'
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- 6. Policy: User bisa DELETE file miliknya sendiri
CREATE POLICY "Users can delete their own images"
ON storage.objects FOR DELETE
USING (
    bucket_id = 'images'
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- ============================================
-- VERIFIKASI
-- ============================================

-- Cek bucket sudah dibuat
SELECT id, name, public, file_size_limit, allowed_mime_types
FROM storage.buckets
WHERE id = 'images';

-- Cek policies
SELECT schemaname, tablename, policyname, permissive, roles, cmd, qual
FROM pg_policies
WHERE tablename = 'objects'
AND schemaname = 'storage';

