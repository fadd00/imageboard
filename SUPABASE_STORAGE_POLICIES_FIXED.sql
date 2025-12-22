-- ============================================
-- SUPABASE STORAGE POLICIES - FIXED VERSION
-- Copy-paste seluruh file ini ke SQL Editor
-- ============================================

-- Hapus policies lama (jika ada error)
DROP POLICY IF EXISTS "Public Read Access" ON storage.objects;
DROP POLICY IF EXISTS "Authenticated Upload" ON storage.objects;
DROP POLICY IF EXISTS "Users Delete Own Images" ON storage.objects;
DROP POLICY IF EXISTS "Users Update Own Images" ON storage.objects;

-- ============================================
-- POLICY 1: Public Read Access
-- Allow SEMUA ORANG untuk lihat gambar
-- ============================================
CREATE POLICY "Public Read Access"
ON storage.objects FOR SELECT
TO public
USING (bucket_id = 'images');

-- ============================================
-- POLICY 2: Authenticated Upload (PENTING!)
-- Allow USER LOGIN untuk upload gambar
-- ============================================
CREATE POLICY "Authenticated Upload"
ON storage.objects FOR INSERT
TO authenticated
WITH CHECK (bucket_id = 'images');

-- ============================================
-- POLICY 3: Delete Own Images (Optional)
-- Allow USER untuk hapus file SENDIRI
-- ============================================
CREATE POLICY "Users Delete Own Images"
ON storage.objects FOR DELETE
TO authenticated
USING (
    bucket_id = 'images'
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- ============================================
-- POLICY 4: Update Own Images (Optional)
-- Allow USER untuk update file SENDIRI
-- ============================================
CREATE POLICY "Users Update Own Images"
ON storage.objects FOR UPDATE
TO authenticated
USING (
    bucket_id = 'images'
    AND auth.uid()::text = (storage.foldername(name))[1]
);

-- ============================================
-- VERIFIKASI
-- ============================================
SELECT
    policyname,
    cmd as operation,
    roles,
    qual as using_expression
FROM pg_policies
WHERE tablename = 'objects'
AND schemaname = 'storage'
ORDER BY policyname;

-- Expected output:
-- ✅ Authenticated Upload     | INSERT | {authenticated}
-- ✅ Public Read Access       | SELECT | {public}
-- ✅ Users Delete Own Images  | DELETE | {authenticated}
-- ✅ Users Update Own Images  | UPDATE | {authenticated}

