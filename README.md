# 🔗 LinkForge

> **Secure, scalable, analytics-driven URL management platform**
> Built with Java 21 + Spring Boot 3.3 + PostgreSQL + Redis

[![CI/CD](https://github.com/yourusername/linkforge/actions/workflows/ci.yml/badge.svg)](https://github.com/yourusername/linkforge/actions)
[![Java 21](https://img.shields.io/badge/Java-21-orange.svg)](https://openjdk.org/projects/jdk/21/)
[![Spring Boot](https://img.shields.io/badge/Spring%20Boot-3.3.x-green.svg)](https://spring.io/projects/spring-boot)
[![PostgreSQL](https://img.shields.io/badge/PostgreSQL-16-blue.svg)](https://www.postgresql.org/)
[![Redis](https://img.shields.io/badge/Redis-7-red.svg)](https://redis.io/)
[![Docker](https://img.shields.io/badge/Docker-Ready-blue.svg)](https://www.docker.com/)

---

## 📋 Project Overview

LinkForge is a production-grade URL shortening platform implementing 10 engineering phases:

| Phase | Feature | Status |
|-------|---------|--------|
| 1 | Authentication + User Management + URL CRUD | ✅ |
| 2 | QR Code Generation (PNG/SVG/Logo/Colored) | ✅ |
| 3 | Click Analytics (Country, Device, Browser, Referrer) | ✅ |
| 4 | Redis Caching (sub-50ms redirects) | ✅ |
| 5 | Developer API with API Keys | ✅ |
| 6 | Admin Panel | ✅ |
| 7 | Email Notifications (Thymeleaf) | ✅ |
| 8 | Background Schedulers | ✅ |
| 9 | Docker + Nginx | ✅ |
| 10 | GitHub Actions CI/CD | ✅ |

---

## 🚀 Quick Start

### Prerequisites
- Java 21
- Docker & Docker Compose
- Maven 3.9+

### 1. Clone & Configure

```bash
git clone https://github.com/yourusername/linkforge.git
cd linkforge
cp .env.example .env
# Edit .env with your secrets
```

### 2. Start with Docker Compose

```bash
docker-compose -f docker/docker-compose.yml up -d
```

### 3. Access

| Service | URL |
|---------|-----|
| API | http://localhost:8080 |
| Swagger UI | http://localhost:8080/swagger-ui.html |
| Actuator Health | http://localhost:8080/actuator/health |
| Prometheus Metrics | http://localhost:8080/actuator/prometheus |

---

## 🏗️ Architecture

```
Request → Nginx (rate limit) → Spring Boot → Redis Cache (< 1ms)
                                           ↓ cache miss
                                         PostgreSQL
                                           ↓
                               Async Analytics (Virtual Threads)
```

### Tech Stack

| Component | Technology |
|-----------|-----------|
| Language | Java 21 (Virtual Threads) |
| Framework | Spring Boot 3.3.x |
| Database | PostgreSQL 16 (Flyway migrations) |
| Cache | Redis 7 (Lettuce client) |
| Auth | JWT (JJWT 0.12) + Refresh Token Rotation |
| Security | BCrypt (cost 12), RBAC, Rate Limiting |
| QR Codes | ZXing (Google) |
| Email | JavaMailSender + Thymeleaf |
| Docs | SpringDoc OpenAPI 3.0 |
| Metrics | Micrometer + Prometheus |
| Build | Maven + Docker multi-stage |
| CI/CD | GitHub Actions |

---

## 📡 API Endpoints

### Authentication
```
POST /api/v1/auth/register         # Register (sends verify email)
POST /api/v1/auth/login            # Login → JWT + Refresh Token
POST /api/v1/auth/refresh          # Refresh token rotation
POST /api/v1/auth/logout           # Revoke all tokens
GET  /api/v1/auth/verify-email     # Verify email
POST /api/v1/auth/forgot-password  # Send reset email
POST /api/v1/auth/reset-password   # Reset password
POST /api/v1/auth/change-password  # Change password
```

### URLs
```
POST   /api/v1/urls           # Create short URL
GET    /api/v1/urls           # List (paginated, filterable, sortable)
GET    /api/v1/urls/{id}      # Get by ID
PUT    /api/v1/urls/{id}      # Update
DELETE /api/v1/urls/{id}      # Delete
PUT    /api/v1/urls/{id}/toggle  # Toggle active
POST   /api/v1/urls/bulk      # Bulk create (max 100)
DELETE /api/v1/urls/bulk      # Bulk delete
GET    /{shortCode}           # Public redirect (< 50ms)
```

### QR Codes
```
GET  /api/v1/urls/{id}/qr            # PNG (size, fgColor, bgColor, download)
GET  /api/v1/urls/{id}/qr/svg        # SVG
POST /api/v1/urls/{id}/qr/with-logo  # PNG with center logo
```

### Analytics
```
GET /api/v1/analytics/{urlId}/summary    # Click summary
GET /api/v1/analytics/{urlId}/clicks    # Daily clicks
GET /api/v1/analytics/{urlId}/countries # By country
GET /api/v1/analytics/{urlId}/devices   # By device
GET /api/v1/analytics/{urlId}/browsers  # By browser
GET /api/v1/analytics/{urlId}/referrers # Top referrers
GET /api/v1/analytics/dashboard         # Global dashboard
```

### Admin (ADMIN role required)
```
GET    /api/v1/admin/users          # All users (paginated)
PUT    /api/v1/admin/users/{id}/disable
DELETE /api/v1/admin/users/{id}
GET    /api/v1/admin/urls           # All URLs
DELETE /api/v1/admin/urls/{id}
GET    /api/v1/admin/stats          # Platform stats
```

---

## 🔐 Security Features

- **JWT** with 15-min access token + 7-day refresh token rotation
- **BCrypt** (cost 12) password hashing
- **Brute Force Protection**: Lock after 5 failed attempts for 15 minutes
- **Rate Limiting**: Redis sliding window (60 req/min API, 300 req/min redirect, 10 req/min auth)
- **Email Enumeration Prevention**: All auth endpoints return consistent responses
- **URL Safety**: Blocks `javascript:`, `data:`, `file:` schemes + private IPs
- **Google Safe Browsing API**: Async malicious URL detection (optional)
- **Security Headers**: HSTS, CSP, X-Frame-Options, Referrer-Policy, Permissions-Policy
- **Audit Logging**: All security events logged with masked PII
- **API Keys**: SHA-256 hashed, shown only once, per-key rate limits

---

## ⚡ Performance

| Metric | Target | Achieved |
|--------|--------|---------|
| Redirect (cache hit) | < 50ms | ~2-5ms |
| Redirect (cache miss) | < 100ms | ~15-30ms |
| Analytics recording | Async | Non-blocking |
| Max concurrent | Horizontal | Stateless |

---

## 🧪 Testing

```bash
# Unit tests
mvn test

# Integration tests (requires Docker for Testcontainers)
mvn verify -P integration-tests

# Security dependency scan
mvn org.owasp:dependency-check-maven:check
```

---

## 📦 Project Structure

```
src/main/java/com/linkforge/
├── auth/           # JWT, refresh tokens, password reset
├── users/          # User entity, profiles, API keys
├── urls/           # Short URL CRUD + redirect
├── qr/             # QR code generation
├── analytics/      # Click tracking + dashboard
├── cache/          # Redis service
├── notification/   # Email service
├── scheduler/      # Background jobs
├── admin/          # Admin panel
├── security/       # JWT filter, rate limit, brute force
├── config/         # Spring configurations
├── exception/      # Global error handling
└── util/           # URL sanitizer, Safe Browsing, audit log
```

---

## 🌐 Deployment

Designed for deployment on:
- **Render** (Spring Boot app)
- **Neon** (PostgreSQL)
- **Upstash** (Redis)

GitHub Actions automatically builds, tests, and deploys on push to `main`.

---

## 📄 License

MIT License — see [LICENSE](LICENSE)

---

## 📝 Summary

LinkForge is an enterprise-ready, high-performance URL management and redirection platform built with Java 21 (leveraging Virtual Threads) and Spring Boot 3.3. It is designed to handle high-throughput link shortening, custom alias generation, dynamic QR code creation, and real-time click analytics with sub-50ms redirect latency.

### Key Capabilities
- **Sub-Millisecond Caching & High Performance**: Powered by Redis 7 caching and optimized database queries in PostgreSQL 16, delivering ultra-fast temporary (302) redirects while recording comprehensive click metadata asynchronously.
- **Enterprise Security**: Implements JWT authentication with refresh token rotation, BCrypt password hashing, brute-force protection, sliding-window rate limiting, and automated dangerous scheme/malicious URL filtering via Google Safe Browsing integration.
- **Rich Analytics & QR Tools**: Captures granular geolocation, device, browser, and referrer data for detailed performance dashboards, alongside customizable PNG/SVG QR code generation with embedded logos.
- **Production Infrastructure**: Built with Docker multi-stage builds, Nginx reverse proxy configuration, Flyway database migrations, Micrometer/Prometheus actuator metrics, and automated GitHub Actions CI/CD workflows.

LinkForge serves as a complete, scalable solution for developers and organizations seeking secure, reliable, and analytics-driven link management.

