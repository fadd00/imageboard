# 🗂️ SETUP SUPABASE STORAGE BUCKET

## ❌ Error: Bucket Not Found

Ketika upload gambar, muncul error:
```
Bucket 'images' not found
```

**Penyebab**: Bucket storage belum dibuat di Supabase.

---

## ✅ Solusi: Buat Bucket di Supabase

### Option A: Via Supabase Dashboard (Recommended - Paling Mudah)

#### 1. Login ke Supabase Dashboard
```
https://supabase.com/dashboard
```

#### 2. Pilih Project Anda
Klik project imageboard

#### 3. Buka Storage
- Sidebar kiri → **Storage**
- Klik **"Create a new bucket"**

#### 4. Konfigurasi Bucket
```
Bucket name: images
Public bucket: ✅ ON (centang ini)
File size limit: 5 MB
Allowed MIME types: image/jpeg, image/png
```

#### 5. Klik **"Create bucket"**

#### 6. Setup Policies (Penting!)
Setelah bucket dibuat, klik bucket **"images"** → tab **"Policies"**

**Policy 1: Public Read**
```
Policy Name: Public Access
Allowed operation: SELECT
Target roles: public
Using expression: true
```

**Policy 2: Authenticated Upload**
```
Policy Name: Authenticated Upload
Allowed operation: INSERT
Target roles: authenticated
WITH CHECK expression: bucket_id = 'images'
```

**Policy 3: Users can delete own images**
```
Policy Name: Users Delete Own
Allowed operation: DELETE
Target roles: authenticated
USING expression: auth.uid()::text = (storage.foldername(name))[1]
```

---

### Option B: Via SQL Editor (Advanced)

#### 1. Buka SQL Editor
Sidebar kiri → **SQL Editor** → **New query**

#### 2. Copy-Paste SQL Script
Buka file: `SUPABASE_STORAGE_BUCKET.sql`

Copy semua isi file dan paste ke SQL Editor.

#### 3. Run Query
Klik **"Run"** atau tekan Ctrl+Enter

#### 4. Verify
Cek di **Storage** → Bucket **"images"** sudah muncul

---

## 🔍 Verifikasi Bucket

### Cek via Dashboard:
1. Go to **Storage**
2. Should see bucket: **images**
3. Click bucket → should see:
   - ✅ Public: **ON**
   - ✅ File size limit: **5 MB**
   - ✅ Allowed MIME types: **image/jpeg, image/png**

### Cek via SQL:
```sql
-- Cek bucket ada
SELECT * FROM storage.buckets WHERE id = 'images';

-- Should return:
-- id: images
-- name: images
-- public: true
-- file_size_limit: 5242880
-- allowed_mime_types: {image/jpeg, image/jpg, image/png}
```

---

## 📊 Bucket Configuration Details

### Bucket Settings:
| Setting | Value | Purpose |
|---------|-------|---------|
| **Bucket ID** | `images` | Identifier unik |
| **Public** | `true` | Gambar bisa diakses publik via URL |
| **File Size Limit** | `5 MB` | Max ukuran per file |
| **Allowed MIME Types** | `image/jpeg, image/png` | Hanya JPG & PNG |

### Policies:
| Policy | Action | Who | Purpose |
|--------|--------|-----|---------|
| **Public Read** | SELECT | Anyone | Semua orang bisa lihat gambar |
| **Auth Upload** | INSERT | Logged in users | User login bisa upload |
| **Delete Own** | DELETE | File owner | User bisa hapus file sendiri |
| **Update Own** | UPDATE | File owner | User bisa update file sendiri |

---

## 🔐 Security

### Public Bucket - Aman?
✅ **Aman**, karena:
- Upload tetap butuh authentication
- Hanya read yang public (lihat gambar)
- User cuma bisa delete/update file sendiri
- Rate limiting by Supabase
- File size limit (5 MB)
- MIME type validation (JPG/PNG only)

### File URL Format:
```
https://[PROJECT_REF].supabase.co/storage/v1/object/public/images/[filename]

Contoh:
https://swytclwaagjfpbnyyiqr.supabase.co/storage/v1/object/public/images/img_user123_1234567890.jpg
```

---

## 🧪 Test Upload

Setelah bucket dibuat, test di aplikasi:

### 1. Login ke App
```
Email: test@example.com
Password: password123
```

### 2. Klik FAB (+)
Navigate ke Create Thread Screen

### 3. Pilih Gambar
- Ambil Foto (Kamera)
- atau Pilih dari Galeri

### 4. Isi Form
```
Judul: Test Upload Image
Caption: Testing image upload to Supabase Storage
```

### 5. Klik "Post Thread"
Loading... → Success!

### 6. Verify di Supabase
- Go to **Storage** → **images** bucket
- Should see file: `img_[userid]_[timestamp].jpg`
- Click file → Preview image
- Copy public URL → Paste di browser → Image muncul ✅

---

## 🐛 Troubleshooting

### Error: "Bucket not found"
**Solution**: Bucket belum dibuat. Follow Option A atau B di atas.

### Error: "Policy violation"
**Solution**: Policies belum di-setup. Follow step 6 di Option A.

### Error: "File too large"
**Solution**: File > 5MB. App sudah auto compress ke 500KB, tapi jika masih error, naikkan limit:
```sql
UPDATE storage.buckets 
SET file_size_limit = 10485760  -- 10MB
WHERE id = 'images';
```

### Error: "Invalid MIME type"
**Solution**: Format file bukan JPG/PNG. App sudah validasi di client, tapi jika masih error:
```sql
UPDATE storage.buckets 
SET allowed_mime_types = ARRAY['image/jpeg', 'image/jpg', 'image/png', 'image/gif']
WHERE id = 'images';
```

### Error: "Upload timeout"
**Solution**: 
- File terlalu besar → App sudah compress ke 500KB
- Internet lambat → Coba lagi
- Server issue → Check Supabase status

---

## ✅ Checklist

Sebelum test upload, pastikan:
- [x] Bucket **"images"** sudah dibuat
- [x] Bucket di-set **public**
- [x] File size limit: **5 MB**
- [x] Allowed MIME: **JPG, PNG**
- [x] Policy **Public Read** aktif
- [x] Policy **Auth Upload** aktif
- [x] Policy **Delete Own** aktif

---

## 📝 Summary

### Setup Steps:
1. ✅ Login Supabase Dashboard
2. ✅ Storage → Create bucket "images"
3. ✅ Set public: ON
4. ✅ Set file limit: 5 MB
5. ✅ Set MIME types: JPG, PNG
6. ✅ Setup policies (read, upload, delete)
7. ✅ Test upload di app

### Expected Result:
- ✅ Upload gambar berhasil
- ✅ Thread muncul di feed dengan gambar
- ✅ Gambar bisa diakses via public URL
- ✅ No errors!

---

## 🎉 DONE!

Setelah bucket dibuat, aplikasi sudah bisa upload gambar! 🚀

**Next**: Test create thread dengan gambar dari kamera/galeri!

