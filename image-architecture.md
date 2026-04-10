# Image Architecture
**Project:** Quick Commerce Platform — Zambia
**Status:** Implemented
**Last Updated:** April 2026

---

## 1. Overview

Product and category images are stored on **Cloudflare R2** (object storage) and served through **Cloudflare's CDN** with on-the-fly resizing. The backend never serves image bytes — it only stores a short key per image and returns it in API responses. The frontend constructs delivery URLs from those keys.

**Local development** uses **MinIO** — a self-hosted, S3-compatible replacement for R2. All code works identically against MinIO with zero Cloudflare account needed.

---

## 2. Why Cloudflare R2

| Concern | Why it matters for Zambia |
|---------|--------------------------|
| **Zero egress fees** | AWS S3 + CloudFront charges $0.09/GB outbound. R2 is always $0 |
| **African CDN edge** | Cloudflare has PoPs in Johannesburg, Nairobi, Lagos, Cape Town — fastest edge for Zambian users |
| **Free tier at launch** | 10GB storage, 10M reads/month, 5,000 image transforms/month — all free |
| **On-the-fly transforms** | One original image stored, resized/converted to WebP automatically per device |
| **No server bandwidth** | Images go CDN → user directly, backend EC2 is not involved at serve time |

---

## 3. How It Works — High Level

```
Admin uploads image
        │
        ▼
   Backend (product-service)
   validates + stores in R2
   returns r2Key (short path)
        │
        ▼
   Admin saves product
   r2Key stored in DB alongside product
        │
        ▼
   Customer opens app
        │
        ▼
   Frontend fetches product from API
   gets r2Key from images[]
   constructs CDN URL
        │
        ▼
   Cloudflare CDN edge (Johannesburg)
   resizes + converts to WebP on first request
   caches and serves in ~20ms on repeat
```

**Key principle:** The backend is completely out of the image delivery path. It only handles upload (once) and stores the key. Everything else is CDN.

---

## 4. Image Storage

### Products
- Column: `images` (JSON array)
- Stores: ordered list of r2Keys
- First element = primary image (shown in grids/listings)
- Array order = display order

```json
["products/abc123/original.jpg", "products/def456/original.jpg"]
```

### Categories
- Column: `image_url` (VARCHAR)
- Stores: single r2Key string

```
categories/xyz789/original.jpg
```

### r2Key structure
```
products/{uuid}/original.jpg
```
- UUID is generated per upload — not tied to product ID
- Extension reflects actual file type (`.jpg` or `.png`)
- Same key works for both dev (MinIO) and prod (R2) — only the base URL changes

---

## 5. Admin UI Integration

### Upload flow (products — up to 3 images)

The admin UI orchestrates uploads behind a single **"Save"** button:

```
1. Admin selects images (up to 3)
         │
         ▼
2. Client-side validation (instant, no network)
   - JPEG or PNG only
   - Max 5MB per file
   - Min 800×800px recommended
         │
         ▼
3. Upload each image in parallel
   POST /api/v1/images/upload   (one request per image)
   → returns { "r2Key": "products/abc/original.jpg" }
         │
         ▼
4. Sync product with collected r2Keys
   POST /api/v1/catalog/products/sync
   body includes: "images": ["key1", "key2", "key3"]
         │
         ▼
5. Show success. Product is live with images.
```

If any upload fails in step 3 — stop, show error. Nothing is saved. Admin can retry cleanly.

### Upload flow (categories — single image)

Same upload API, same validation. Admin uploads one image, gets an r2Key, then passes it as `imageUrl` in the create/update category request.

### Image upload API reference

```
POST /api/v1/images/upload
Content-Type: multipart/form-data

Field name: image
Accepted: JPEG, PNG
Max size: 5MB
Validated by: magic bytes (not file extension — rename attacks blocked)

Response 200:
{
  "r2Key": "products/d6393cef-2310-4035-872a-6b5ab05444d6/original.jpg"
}

Response 400:
{
  "code": "INVALID_ARGUMENT",
  "message": "Only JPEG or PNG allowed",   // or "File size exceeds 5MB limit" / "Image file is empty"
  "status": 400
}
```

---

## 6. Frontend Integration Guide

### What changed

The `images` field on product responses is now a **`List<String>`** (array of r2Keys), not a single URL string.

**Before:**
```json
{
  "images": "https://images.unsplash.com/photo-abc"
}
```

**After:**
```json
{
  "images": [
    "products/d6393cef-2310-4035-872a-6b5ab05444d6/original.jpg",
    "products/8681dd50-1185-47c6-9613-cc7e4fb40b6f/original.jpg"
  ]
}
```

- `images[0]` = primary image (use this for grids, listings, thumbnails)
- `images` may be an empty array `[]` — handle this with a placeholder
- Categories still use a single `imageUrl` string field (not an array)

### Constructing image URLs

The r2Key alone is not a displayable URL. The frontend must prefix it with the CDN base URL + transform parameters.

```javascript
const CDN = "https://cdn.yourapp.zm/cdn-cgi/image";

const SIZES = {
  thumb:  "w=150,h=150,fit=cover,f=auto,q=80",   // category grid, search chips
  card:   "w=400,h=400,fit=cover,f=auto,q=80",   // product cards, home page
  detail: "w=800,h=800,fit=contain,f=auto,q=85", // product detail page
};

function imageUrl(r2Key, size = "card") {
  if (!r2Key) return "/assets/placeholder.png";

  // Local dev — MinIO serves raw, no transforms
  if (process.env.NODE_ENV === "development") {
    return `http://localhost:9000/product-images/${r2Key}`;
  }

  return `${CDN}/${SIZES[size]}/${r2Key}`;
}
```

**Usage examples:**
```javascript
// Product grid card
<img src={imageUrl(product.images[0], "card")} />

// Product detail — show all images
product.images.map(key => <img src={imageUrl(key, "detail")} />)

// Category icon
<img src={imageUrl(category.imageUrl, "thumb")} />

// Safe primary image access
const primaryImage = product.images?.[0] ?? null;
```

### Format conversion — nothing to do

`f=auto` in the transform URL tells Cloudflare to check what the device supports:
- Modern Android/iOS → WebP (~30% smaller than JPEG)
- Newer browsers → AVIF where supported
- Old devices → JPEG fallback

All from the same URL. No frontend logic needed.

### Environment config

```
# Production
CDN_BASE_URL=https://cdn.yourapp.zm/cdn-cgi/image

# Local dev
CDN_BASE_URL=http://localhost:9000/product-images
```

---

## 7. Cloudflare Setup Guide

One-time setup, ~45 minutes.

### Step 1 — Create Cloudflare account
Go to [cloudflare.com](https://cloudflare.com) → Sign Up

### Step 2 — Register or transfer domain
Dashboard → **Domain Registration** → Register Domains
*(or add an existing domain and point nameservers to Cloudflare)*

### Step 3 — Create R2 bucket
Dashboard → **R2 Object Storage** → Create bucket
- Name: `bunzo-images` (or whatever you chose)
- Region: automatic

### Step 4 — Connect CDN subdomain to bucket
Bucket → **Settings** → Public access → **Custom Domains**
- Add `cdn.yourapp.zm`
- Cloudflare will auto-provision SSL

### Step 5 — Enable Image Transformations
Dashboard → **Images** → Transformations
- Toggle **ON**
- Enable **"Resize from any origin"** → ON

This is what makes `cdn-cgi/image/w=400,...` URLs work.

### Step 6 — Generate R2 API credentials
Dashboard → **R2 Overview** → Manage R2 API Tokens → Create Token
- Permissions: **Object Read & Write** on your bucket
- Copy **Access Key ID** and **Secret Access Key** — secret is shown once

### Step 7 — Set environment variables on EC2
```bash
R2_ACCESS_KEY_ID=<your access key id>
R2_SECRET_ACCESS_KEY=<your secret access key>
R2_ENDPOINT=https://<account-id>.r2.cloudflarestorage.com
R2_BUCKET=bunzo-images
R2_REGION=auto
```

Account ID is visible in the R2 dashboard URL or under account settings.

### Step 8 — DNS for backend API
Domain → **DNS** → Records → Add:
```
Type: A
Name: api
Value: <EC2 public IP>
Proxy: ON (orange cloud)
```

---

## 8. Pre-Launch Cache Warming

Before going live, warm the CDN so first users don't wait for the initial transform:

```bash
#!/bin/bash
API="https://api.yourapp.zm/api/v1/catalog/products/all"
CDN="https://cdn.yourapp.zm/cdn-cgi/image"

curl -s "$API" | jq -r '.[].images[]' | while read key; do
  curl -s -o /dev/null "$CDN/w=150,h=150,fit=cover,f=auto,q=80/$key"
  curl -s -o /dev/null "$CDN/w=400,h=400,fit=cover,f=auto,q=80/$key"
  curl -s -o /dev/null "$CDN/w=800,h=800,fit=contain,f=auto,q=85/$key"
  echo "Warmed: $key"
done
```

---

## 9. Cost at Launch

| Service | Cost |
|---------|------|
| Cloudflare domain | ~$0.87/month |
| R2 storage (up to 10GB) | $0 |
| R2 reads (up to 10M/month) | $0 |
| Image transforms (up to 5,000/month) | $0 |
| CDN bandwidth | $0 (always free) |
| **Total** | **~$0.87/month** |

At 3,000 SKUs / 5,000 daily users: ~$2/month. At 10,000 SKUs / 30,000 users: ~$12.50/month.
