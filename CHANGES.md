# What changed in this version

## Bugs fixed in the backend

1. **Short codes `a` and `A` clashed in MySQL.** Base62 uses both cases, but MySQL compares text
   without caring about case by default. Link number 36 (code `A`) failed with a duplicate-key
   error because link number 10 already had `a`, and opening `/A` could redirect to the wrong
   link. `urls.short_code` is now case-sensitive (`utf8mb4_bin`).
   File: `backend/src/main/java/com/urlshortener/entity/Url.java`.
   Existing databases need the one-line `ALTER TABLE` in `README.md` (or a fresh database).
2. **Redirects stopped working when Redis was down.** The redirect rate limiter threw an error,
   so every short link returned HTTP 500. Your own design rule says redirects must still work
   through MySQL. The redirect limiter now fails open and logs a warning. The authenticated API
   still needs Redis.
   File: `backend/src/main/java/com/urlshortener/filter/RateLimitFilter.java`.
3. **Unknown `/api/keys/...` paths returned HTTP 500** (the rate limiter looked for an API key
   that was never checked). They are no longer rate limited, so they return a normal 404.
   Same file.

## Small additions to the backend

- Link responses now include `"active": true/false`, so the website can show deactivated links.
  The old five-argument constructor of `CreateUrlResponse` is kept, so existing tests still compile.
- `application.yml` can read passwords from a `.env` file in the project root, so one file works for
  both Docker Compose and `mvn spring-boot:run` on every operating system.

## New structure and files

- `backend/` holds the Spring Boot project; `Dockerfile` and `pom.xml` moved with it (unchanged).
- `frontend/` is a new React + Vite website (create links, copy, stats, daily chart, referrers,
  devices, live token-bucket meter, backend health).
- `docker-compose.yml` now builds the backend from `./backend`.
- `.env.example`, `RUN_LOCALLY.md`, a rewritten `README.md`, and `.gitignore` for the new layout.
- Removed from the project: the `target/` build folder and the `.git` folder were not copied.

## Not changed

All other Java code, the tests, `pom.xml`, the Dockerfile and the `performance/` scripts.
