# URL Shortener with Click Analytics

Spring Boot + PostgreSQL + Redis + Docker + JWT auth. Base62 short codes,
custom aliases, link expiry, and per-link analytics (total clicks, clicks/day, top referrers).

---

## 0. Install prerequisites (one-time, on your own machine)

You need these installed locally. Install whichever you're missing:

1. **Docker Desktop** — https://www.docker.com/products/docker-desktop/ (includes Docker Compose)
2. **Java 17** (only needed if you want to run without Docker, or use an IDE) — https://adoptium.net/
3. A REST client: **Postman** (https://www.postman.com/downloads/) or just `curl` in your terminal.
4. **k6** for load testing — https://k6.io/docs/get-started/installation/ (`brew install k6` on Mac, or download the binary for Windows/Linux).

You do NOT need to install Postgres, Redis, or Maven yourself — Docker handles all of that.

---

## 1. Run it locally

From the project root (where `docker-compose.yml` is):

```bash
docker compose up --build
```

First run will take a few minutes (downloading base images, building the jar). When it's ready you'll see
Spring Boot's startup banner and `Started UrlShortenerApplication`. Leave this terminal running.

The API is now live at `http://localhost:8080`.

---

## 2. Try the API

Open a **new** terminal (leave `docker compose up` running in the first one).

**Register a user:**
```bash
curl -X POST http://localhost:8080/api/auth/register \
  -H "Content-Type: application/json" \
  -d '{"email":"you@example.com","password":"password123"}'
```
You'll get back a JWT token. Copy it — you'll use it for anything that needs auth (analytics is currently open,
but keep this handy in case you lock it down further).

**Create a short link:**
```bash
curl -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.google.com"}'
```
Response includes `"shortCode"` (e.g. `"1"` or `"B"`) and `"shortUrl"`.

**Create one with a custom alias and an expiry:**
```bash
curl -X POST http://localhost:8080/api/links \
  -H "Content-Type: application/json" \
  -d '{"longUrl":"https://www.github.com","customAlias":"my-repo","expiresAt":"2026-12-31T00:00:00Z"}'
```

**Use the short link** (paste in browser, or):
```bash
curl -i http://localhost:8080/r/1
```
You should see `HTTP/1.1 302` with a `Location` header pointing at the long URL.

**Check analytics after a few clicks:**
```bash
curl http://localhost:8080/api/links/1/analytics
```
Returns total clicks, clicks per day, and top referrers.

---

## 3. How the pieces fit together (read this before your interview)

- **Base62 encoding**: short codes for auto-generated links come from base62-encoding the row's
  auto-increment DB id. This means no collision checks are needed — the id is unique by definition.
  Custom aliases go through a uniqueness check against the DB instead.
- **Redis caching**: `GET /r/{code}` checks Redis first (`link:{code}` → `id::longUrl`). Only on a
  cache miss does it hit Postgres, and it then re-populates Redis with a TTL (see `app.link-cache-ttl-seconds`
  in `application.yml`). This is what you'll benchmark with k6.
- **Async click writes**: recording a click (for analytics) happens on a separate thread
  (`@Async` in `ClickService`) so it never adds latency to the redirect response itself. This is a
  deliberate design decision worth mentioning in interviews — decoupling the hot path from analytics writes.
- **JWT auth**: stateless, `Authorization: Bearer <token>` header. See `JwtAuthFilter` / `SecurityConfig`.

---

## 4. Load testing (the numbers recruiters care about)

You need at least one short link created first (see step 2). Note its short code.

**Test WITH cache (warm):**
```bash
# hit it once first to warm the cache
curl http://localhost:8080/r/1

k6 run -e SHORT_CODE=1 k6/load-test.js
```

**Test WITHOUT cache (every request forced to miss Redis):**
```bash
# find your redis container name
docker ps

# flush the cache right before the run
docker exec -it <redis-container-name> redis-cli FLUSHALL

# re-run immediately — first request will be a miss, and since TTL hasn't
# re-populated cache for this specific run you're measuring closer to a DB-only path.
# For a stricter "always-miss" test, see the note below.
k6 run -e SHORT_CODE=1 k6/load-test.js
```

> Note: after the *first* request in a run, Redis gets re-populated, so the rest of that run is
> actually cached again. For a true "no cache" comparison, the cleanest approach is to temporarily
> comment out the Redis read/write lines in `LinkService.resolve()`, rebuild, and re-run k6 against
> that version. Report both configurations clearly in your results (e.g. "cache disabled" vs "cache enabled")
> rather than relying on FLUSHALL mid-test.

k6 prints a summary with `http_req_duration` (look for `p(95)`) and `http_reqs` (total, and you can compute
requests/sec from the test duration). Take screenshots of both runs for your portfolio/resume.

---

## 5. Deploy to AWS EC2 (free tier)

1. Launch a `t2.micro` or `t3.micro` EC2 instance, Ubuntu 22.04, in the AWS free tier.
2. Open inbound ports in its Security Group: 22 (SSH), 8080 (API) — or put it behind nginx on 80 later.
3. SSH in, then install Docker:
   ```bash
   sudo apt update && sudo apt install -y docker.io docker-compose-v2
   sudo usermod -aG docker $USER
   # log out and back in for the group change to apply
   ```
4. Copy your project to the instance (from your local machine):
   ```bash
   scp -i your-key.pem -r ./urlshortener ubuntu@<ec2-public-ip>:~/
   ```
5. On the instance:
   ```bash
   cd urlshortener
   docker compose up --build -d
   ```
6. Update `BASE_URL` in `docker-compose.yml` (the `app` service's environment) to
   `http://<ec2-public-ip>:8080` and restart (`docker compose up -d --build`) so generated short URLs
   point at the public address instead of `localhost`.
7. Re-run your k6 load test against `-e BASE_URL=http://<ec2-public-ip>:8080` for your final numbers —
   note in your writeup that a t2/t3.micro (1 vCPU, burstable) will cap your throughput regardless of
   app efficiency; that's expected and worth mentioning rather than hiding.

---

## 6. What's deliberately left out (mention these as "next steps" in interviews)

- Rate limiting on link creation (would normally add this with Redis + a token bucket).
- Flyway/Liquibase migrations (currently using `ddl-auto: update`, fine for a portfolio project, not for prod).
- Refresh tokens (JWT here is access-token-only, 24h expiry).
- HTTPS/TLS termination (would sit behind nginx or an AWS ALB in a real deployment).
