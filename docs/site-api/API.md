# QuarkOS Capes HTTP API v1

The same origin serves the website and `/v1`. JSON responses and errors include `api: 1`. Unknown JSON fields and any `accessToken` field are rejected with 400. Mojang access tokens never pass through this service. UTF-8 JSON request bodies are limited to 8 KiB. Uploads use multipart, with a 1 MiB image limit and a bounded envelope.

Breaking changes require `/v2`; new optional fields may be added to v1. Clients must ignore unknown response fields. All identifiers and URLs are case sensitive, except upload slugs are canonicalized to lowercase. Always URL-encode path identifiers and cursor values.

## Authentication

### Existing Minecraft bearer flow

1. `POST /v1/auth/challenge` with `{}` → `{api, serverId, expiresAt}` (two minutes, single use).
2. The mod sends its access token **directly to Mojang join**, with the challenge serverId.
3. `POST /v1/auth/complete` with `{username, serverId}` → `{api, token, uuid, name, expiresAt}`. The server verifies Mojang hasJoined. The token is a 12-hour bearer. It is stored only as SHA-256 in the data directory.
4. Use `Authorization: Bearer <token>` on mod writes. `DELETE /v1/auth/session` revokes it.

Signed dev bearers remain supported only with `CAPES_DEV_AUTH=true`, a secret of at least 16 characters, and an exact `127.0.0.1` bind. Their existing `dev1` HMAC format is unchanged; see README and DevAuth.java. Do not use dev authentication on public hosting.

### Local web accounts

- `POST /v1/account/register` or `/v1/account/login`: `{username,password}`. Usernames match `[A-Za-z0-9_]{3,24}`, passwords have 10–128 characters. Returns `{api,user,csrf}` and sets `capes_session` with HttpOnly, SameSite=Lax, Path=/, Max-Age=604800; Secure when CAPES_PUBLIC_URL uses HTTPS. Registration returns 409 for an existing name. Login failures do not reveal whether a username exists. Passwords use a random 24-byte salt and 210,000-iteration PBKDF2-HMAC-SHA256, with constant-time hash comparison.
- `GET /v1/account` → `{api,user,csrf}`, both null when signed out. User: `{id,username,minecraftUuid,minecraftName,admin,banned}`. An unlinked UUID/name is null.
- Cookie-authenticated writes require `X-CSRF-Token` from that response. Invalid/missing tokens return 403. Sign-in/registration check any supplied Origin against CAPES_PUBLIC_URL. Session tokens are random and stored only as SHA-256. Sessions expire after seven days.
- `DELETE /v1/account`: revoke this session and expire its cookie, 204. This does not delete the account.

### Linking

`POST /v1/link/code` with `{}` and web session + CSRF returns `{api,code,expiresAt}`. The code is eight hexadecimal characters, valid for ten minutes. Issuing a new code invalidates that account’s old codes. At most five issuances per account per ten minutes.

`POST /v1/link/claim` with `{code}` and a **Minecraft bearer** returns exactly:

```json
{"api":1,"linked":true,"uuid":"853c80ef-3c37-49fd-aa65-12b9b86ad9e6","name":"Emilio"}
```

A code is single use. Expired, invalid, or consumed codes return 400. A Minecraft UUID already linked to another web account returns 409. A web cookie alone cannot claim a code. The website can poll GET /v1/account or explicitly check link status; no Minecraft credentials are entered on the website.

## Catalog for the mod and browser

`GET /v1/catalog?source=&kind=&q=&sort=&style=&tag=&cursor=&limit=`:

| Parameter | Values / behavior |
| --- | --- |
| source | empty for merged uploads and LabyMod, `ours`, `labymod`, `featured` |
| kind | empty, `cape`, `cloak`; Minecraft capes are `labymod` + `cape`, LabyMod user cloaks are `labymod` + `cloak` |
| q | title, author, or tags substring for uploads, title or author for featured, upstream `input` search for LabyMod. Maximum 48 characters. |
| sort | `popular` default, `newest`, `name` |
| style | empty, `flat`, `frames`, `wave`, `model`; LabyMod has `flat` |
| tag | case-insensitive tag text, maximum 48 characters |
| limit | 1–50, default 20 |
| cursor | opaque continuation from the preceding response, bound to the filters and sort; change filters by dropping the cursor |

```json
{
  "api":1,
  "items":[{
    "id":"verdant-signal","source":"ours","kind":"cape",
    "title":"Verdant Signal","author":"Emilio","tags":["green"],"uses":12,
    "style":"flat","animateFrames":false,
    "frameCount":1,"frameWidth":64,"frameHeight":32,"frameDelayMs":[100],
    "textureUrl":"https://capes.example.org/v1/gallery/verdant-signal/asset",
    "sha256":"<cleaned PNG SHA-256>","model":null
  }],
  "nextCursor":null,
  "sources":{"ours":"available","labymod":"available"}
}
```

Uploads also include `owner`, `status`, `revision`, and `updatedAt`. LabyMod items have id `cloak:<hash>` or `cape:<hash>`, `textureUrl: https://texture.laby.net/<hash>.png`, and a `creditUrl` pointing to `https://laby.net/cloaks/<hash>` or `https://laby.net/capes`. Optional `previewUrl` is reserved; the server does not relay upstream renders. LabyMod author values identify the community/source rather than claiming an individual creator. The site always credits LabyMod and shows its non-affiliation notice.

`GET /v1/catalog/{source}/{id}` returns a single uniform item plus `api`. Hidden, unpublished, pending, removed, or deleted uploads return 404. LabyMod hashes are lowercase hexadecimal, 32–64 characters; references can resolve without downloading an image. A detail cached from browsing includes live title/tags/uses; an uncached direct hash reference returns a generic source label and zero uses. Listed LabyMod frame dimensions are the standard 64×32 descriptor; clients should honor actual texture dimensions when decoding. Neither API nor server fetches upstream image bytes.

LabyMod `popular`/`newest` map to `most_used`/`latest`. The upstream has no global alphabetical sort or creation timestamps: `name` orders each fixed 50-item discovery window alphabetically, and newest preserves upstream order within each LabyMod collection, before dated local uploads. Local uploads have full-catalog name/newest/popular ordering. Cursors are a live feed, not a frozen snapshot; upstream changes or cache refreshes can move records. Filtering may produce an empty page with a non-null nextCursor; continue until null. Source states are `available`, `unavailable`, or `disabled`. An unavailable source never turns a merged response into a 500; community results remain available. A disabled direct LabyMod detail returns 503 `source_disabled`.

### Featured capes

| Method and path | Response |
| --- | --- |
| `GET /v1/catalog?source=featured` | Standard v1 catalog page. `popular` and `newest` use manifest order. `name` sorts by title, then ID. |
| `GET /v1/catalog/featured/{id}` | Standard v1 item with `api: 1`. Missing IDs return 404. |
| `GET /v1/catalog/featured/{id}/asset` | PNG with a SHA-256 ETag. Send `If-None-Match` for 304. Missing IDs return 404. |

Featured items use `kind: cape`. They include `title`, `author`, `sourceUrl` (an attribution URL or null), `license`, and `sha256` for the returned PNG. A static item has `style: flat`, `animateFrames: false`, and one frame. An animated item has `style: frames`, `animateFrames: true`, and `stripLayout: vertical`. Its PNG is `frameWidth` pixels wide and `frameHeight * frameCount` pixels tall. `frameDelayMs` gives one duration per frame, starting at the top. Featured uses `tags: []` and `uses: 0`. Select `source=featured` explicitly for list requests. Existing merged cursors keep their format.

Set `CAPES_FEATURED_DIR` to an external folder. The server reads it once at startup. An unset variable or an empty `items` array gives an empty page with `sources.featured: disabled`. A configured folder with a missing or invalid manifest or PNG fails startup.

```text
featured/
  manifest.json
  textures/
    bands.png
    slide.png
```

`manifest.json` has an `items` array. Each row has an `id` and a `texture` filename under `textures/`. An ID is a lowercase slug of 1–64 letters, digits, underscores, or hyphens and starts with a letter or digit. A row also accepts the importer sidecar fields below. A source path beginning with `/` resolves against `CAPES_PUBLIC_URL`.

#### Import a folder

Run the command with the built server jar and a folder of PNGs. Use each filename stem as its ID. The importer lowercases stems and checks for duplicates. It cleans the PNGs, names them by content hash, and replaces the manifest after it validates the whole folder. Point `CAPES_FEATURED_DIR` at the output folder and restart the server.

```powershell
java -jar .\server\build\libs\quarkos-capes-server-0.1.0.jar import-featured .\cape-input .\featured
```

An optional JSON sidecar shares the PNG's stem, for example `slide.json` for `slide.png`.

```json
{
  "title": "Slide",
  "author": "QuarkOS",
  "sourceUrl": "/legal#featured-capes",
  "license": "MIT",
  "frameCount": 4,
  "frameWidth": 64,
  "frameHeight": 32,
  "frameDelayMs": [100, 100, 100, 100]
}
```

Without a sidecar, the importer uses a single frame and the lowercase filename stem for the title. Author defaults to `Unknown`, `sourceUrl` to null, and license to `unverified`. For animations, supply the frame count and dimensions. Delays default to 100 ms and each delay must be 50–1000 ms. The PNG must use 64×32 cape atlases or integer HD multiples. Limits are 1 MiB, 32 frames, 512×256 pixels per frame, and 8192 pixels for the width of all frames laid side by side. Give each cape its source URL and license before public use.

#### Scraper configuration

`java -jar <jar> scrape-featured <review-folder>` is disabled when `CAPES_FEATURED_SCRAPE_URL` is unset. Server startup reads the manifest independently.

After Emilio chooses the site, provide a `FeaturedScraper.Source` adapter. Register it with Java's `ServiceLoader` in `META-INF/services/dev.quarkos.capes.server.FeaturedScraper$Source`. Set `CAPES_FEATURED_SCRAPE_URL` to the approved HTTP(S) site. With separate server and adapter jars on Windows, run the scraper with both jars on the classpath.

```powershell
java -cp "server.jar;source-adapter.jar" dev.quarkos.capes.server.CapesServer scrape-featured .\review
```

The adapter collects PNGs and sidecars into the review folder for import. A configured site without an adapter returns an error.

## Wearing (stable mod contract)

`PUT /v1/me/wearing` with a Minecraft bearer **or** a linked web session + CSRF:

```json
{"source":"labymod","id":"cloak:a0c8e54d8f03aaa9f1ba58190b75ef4a"}
```

For an upload, send `{"source":"ours","id":"verdant-signal"}`. For a featured cape, send `{"source":"featured","id":"slide"}`. The server stores only `{source,id,updatedAt}`. Repeating the same equip does not increment its count; a transition to an upload increments that upload’s cumulative wears once. A web account without a Minecraft link gets 409 `minecraft_not_linked`.

PUT and `GET /v1/me/wearing` return:

```json
{
  "api":1,
  "uuid":"853c80ef-3c37-49fd-aa65-12b9b86ad9e6",
  "wearing":{
    "id":"cloak:a0c8e54d8f03aaa9f1ba58190b75ef4a","source":"labymod","kind":"cloak",
    "title":"LabyMod cloak","author":"LabyMod community","tags":[],"uses":0,
    "style":"flat","frameCount":1,"frameWidth":64,"frameHeight":32,
    "frameDelayMs":[100],"animateFrames":false,"model":null,
    "textureUrl":"https://texture.laby.net/a0c8e54d8f03aaa9f1ba58190b75ef4a.png",
    "creditUrl":"https://laby.net/cloaks/a0c8e54d8f03aaa9f1ba58190b75ef4a"
  }
}
```

`DELETE /v1/me/wearing` removes the reference, 204. It does not delete an upload or decrement cumulative wears.

`GET /v1/players/{uuid}/wearing` is public and returns exactly `{api,uuid,wearing}` as above; wearing is null when unset or the target is no longer publicly available. `GET /v1/players/wearing?uuids=<uuid>,<uuid>` returns exactly `{api,players:[{api,uuid,wearing},...]}` in request order. Up to 50 UUIDs are accepted; an empty list returns an empty array. Invalid UUIDs and more than 50 return 400. Responses use a SHA-256 JSON ETag and `Cache-Control: public, max-age=15`; send `If-None-Match` for 304. Resolve and load textureUrl directly; use SHA-256 for our assets when present. A later moderation removal makes the resolved reference null, including locally hidden LabyMod hashes.

## Uploads and ownership

`PUT /v1/uploads/{id}` is multipart, authenticated by web session + CSRF or Minecraft bearer. Slug: `[A-Za-z0-9][A-Za-z0-9_-]{0,63}`. Another owner’s slug returns 403. Parts:

- `file`: PNG or GIF, at most 1,048,576 bytes, validated by magic and ImageIO, re-encoded as a clean PNG.
- `title`: required 1–48 characters.
- `tags`: JSON string array, at most five tags, each 1–24 lowercase letters/numbers/spaces/hyphens. Tags are normalized to lowercase.
- `style`: `flat`, `frames`, `wave`, `model`; `animateFrames`: `true` or `false`.
- `frameCount`, `frameWidth`, `frameHeight`: optional integers. PNG strips with >1 frame require frame dimensions and exact horizontal-strip dimensions. GIF frames are detected/composited automatically.
- `frameDelayMs`: optional JSON integer array, one delay per PNG frame, 50–1000 ms. Default 100 ms. GIF delays use the original cleaner’s clamping rules.
- `model`: JSON, required only for style=model; at most 16 KiB and validated by ModelValidator. Schema example below.

Each frame is a 64×32 or 22×17 layout or an integer HD multiple, capped at 512×256. At most 32 frames and 4,194,304 total pixels. The moderation hook sees a CleanAsset. HOLD returns the item with status=pending, REJECT returns 422 without storing. Ownership for web uploads uses the local account UUID; linking Minecraft does not transfer legacy mod upload ownership.

`GET /v1/me/capes` → `{api,items}` including your accepted, pending, removed, and unpublished uploads. Their own asset endpoint is visible with owner credentials. `PATCH /v1/uploads/{id}` with `{title,tags}` preserves the cleaned image, style, model, frame timing and ownership, increments revision and re-enters moderation. `POST /v1/uploads/{id}` with `{action:"unpublish"}` hides it but retains files. PATCH can publish it again. `DELETE /v1/uploads/{id}` permanently removes the entry, image and reports, 204. Bans prevent publishing/editing; owners can still unpublish/delete.

Legacy `/v1/gallery` and `/v1/capes` APIs and hasJoined/dev auth retain their existing shapes and validation; see README. Legacy small arbitrary image dimensions are intentionally retained for compatibility. Their assets return image/png with a content-hash ETag and support 304.

## Reports and administration

`POST /v1/catalog/{source}/{id}/report` with `{reason}` (1–280 characters) → `{api,stored:true,id:<report UUID>}`. Anonymous reports are allowed. Valid web sessions also require CSRF. At most ten reports per peer address per minute, shared with legacy reports.

Admins authenticate with a local username in CAPES_ADMINS plus session/CSRF, or `Authorization: Bearer <CAPES_ADMIN_TOKEN>`. Admin tokens cannot act as player bearers or upload owners.

- `GET /v1/admin` → `{api,reports,items}`. Reports include `{id,source,itemId,reason,createdAt,resolved}`; legacy gallery reports use `galleryId`. Items include held, unpublished and rejected upload metadata.
- `POST /v1/admin/action` with `{action,source,id}` where action is `approve`, `restore`, `takedown`, or `hold`. Uploads change status; LabyMod hashes are hidden/restored in a local blocklist without upstream writes. `hold` has the same hide behavior for upstream items.
- `{action:"ban",username,banned:true|false}` prevents/restores publishing for both web identity and linked Minecraft identity.
- `{action:"resolve",reportId}` marks a report resolved.
- Successful new admin actions append timestamp, actor, action and fields to `audit.jsonl`.

## Errors, limits and caching

```json
{"api":1,"error":"minecraft_not_linked","message":"Link your Minecraft account before wearing a cape."}
```

| HTTP | error | Meaning |
| --- | --- | --- |
| 400 | bad_request | Invalid JSON/fields/identifier/filter/cursor/layout/code; includes access-token rejection |
| 401 | unauthorized | Missing/invalid/expired session or Minecraft bearer |
| 403 | forbidden / uuid_mismatch | CSRF, role, ban, ownership, or legacy path-UUID mismatch |
| 404 | not_found | Missing/non-public item or unknown API path |
| 405 | method_not_allowed | Unsupported method on new routes |
| 409 | conflict / minecraft_not_linked | Duplicate account/link or web wear before linking |
| 413 | too_large | Encoded upload or decoded pixel cap exceeded |
| 415 | unsupported_type | Image magic is neither PNG nor GIF |
| 422 | rejected | Moderation rejected an upload |
| 429 | rate_limited | Rate bucket exhausted; wait for its window |
| 503 | source_disabled | LabyMod detail disabled by operator |

Rate windows: sign-in + registration share 10/minute/address; Mojang auth completes 10/minute/address; cape and gallery publishes each 30/hour/owner; link codes 5/10 minutes/account; link claims 10/minute/Minecraft UUID; PUT wearing 60/minute/player; reports 10/minute/address. Buckets are in memory and reset on restart. Direct peer addresses are used, never untrusted forwarded headers. Clients should back off on 429.

LabyMod metadata cache: maximum 128 request keys, successful entries expire after 120 seconds, failures after 10 seconds; 3-second connect and 5-second request timeout; 1 MiB metadata response bound; redirects disabled. HTTP client and clock are injectable; all JUnit source tests use saved fixtures without network. No upstream image proxy route exists.

Account/API responses default to no-store except public wearing (15 seconds). HTML uses no-cache with ETag; static assets use one hour + ETag. All responses send nosniff, a same-origin CSP (with texture.laby.net for image/texture access), a restrictive referrer policy, and no camera/microphone/geolocation permission. Static GET/HEAD and conditional requests are supported. `GET /v1/health` → `{api:1,ok:true}`.

Model example (integer cube coordinates and atlas UV origin):

```json
{"cubes":[{"x":-5,"y":0,"z":0,"w":10,"h":16,"d":1,"u":0,"v":0}]}
```

At most 64 cubes; xyz are -32..32, whd are 1..32, UV origins must be within the frame. The model is attached to the back; the mod applies its geometry when resolving `style: model`. `GET /v1/admin/items/{id}/asset` is an admin-authenticated PNG preview of a held or removed upload, using the same cleaned bytes; this never makes the asset public.
