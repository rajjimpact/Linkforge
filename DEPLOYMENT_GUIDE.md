# 🚀 LinkForge Deployment & Environment Variables Guide

This guide contains all environment variables, deployment steps, and configuration details for **LinkForge**.

---

## 📋 Deployment Sequence

Always deploy in this order:
1. **Neon PostgreSQL Database**
2. **Upstash Redis Cache**
3. **Render (Spring Boot Backend API)**
4. **Vercel (Frontend App)**

---

## 🔑 Environment Variables Reference

### Render Web Service (`linkforge`)

Copy & paste this bulk text block directly into Render Dashboard (**Environment -> Edit Raw Text**):

```env
SPRING_PROFILES_ACTIVE=prod
DB_HOST=<Your_Neon_Host_Here>
DB_PORT=5432
DB_NAME=neondb
DB_USERNAME=<Your_Neon_User_Here>
DB_PASSWORD=<Your_Neon_Password_Here>
REDIS_HOST=<Your_Upstash_Host_Here>
REDIS_PORT=6379
REDIS_PASSWORD=<Your_Upstash_Password_Here>
JWT_SECRET=linkforge_super_secret_jwt_signing_key_2026_prod_v1_secure!
APP_BASE_URL=https://linkforge.onrender.com
APP_FRONTEND_URL=https://linkforge.vercel.app
CORS_ALLOWED_ORIGINS=https://linkforge.vercel.app,http://localhost:3000,http://localhost:5173
```

---

### Vercel (Frontend App)

Add these in Vercel **Settings -> Environment Variables**:

```env
VITE_API_BASE_URL=https://linkforge.onrender.com
```

---

## 📍 How to Find Your Credentials

| Variable | Source | Where to Find |
|---|---|---|
| `DB_HOST` | [Neon Console](https://console.neon.tech) | Dashboard -> Parameters -> Host *(e.g. `ep-cool-12345.us-east-2.aws.neon.tech` - do NOT include `@`)* |
| `DB_NAME` | [Neon Console](https://console.neon.tech) | Default is `neondb` |
| `DB_USERNAME` | [Neon Console](https://console.neon.tech) | Dashboard -> Parameters -> User |
| `DB_PASSWORD` | [Neon Console](https://console.neon.tech) | Dashboard -> Parameters -> Password *(click 👁️ to reveal)* |
| `REDIS_HOST` | [Upstash Console](https://console.upstash.com) | Redis DB -> Details -> Endpoint *(e.g. `glowing-cat-12345.upstash.io`)* |
| `REDIS_PORT` | [Upstash Console](https://console.upstash.com) | `6379` |
| `REDIS_PASSWORD` | [Upstash Console](https://console.upstash.com) | Redis DB -> Details -> Password |
| `JWT_SECRET` | Self-created | Any secure text with >= 32 characters |
| `APP_BASE_URL` | Render Dashboard | `https://<service-name>.onrender.com` |
| `APP_FRONTEND_URL` | Vercel Dashboard | `https://<your-project>.vercel.app` |

---

## 🛠️ Build Fixes Applied to Codebase

1. **Testcontainers Redis Fix**: Removed invalid `org.testcontainers:redis` dependency from `pom.xml`.
2. **UA-Parser Fix**: Removed dead `ua_parser` dependency and `raw.github.com/before/uadetector` repository link. User-Agent parsing now relies on native String matching in `UserAgentParserService.java`.
