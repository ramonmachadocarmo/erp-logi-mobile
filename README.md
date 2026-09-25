# Manaus Route Planner (v0.2.0)

[![Sponsor](https://img.shields.io/static/v1?label=Sponsor&message=%E2%9D%A4&logo=GitHub&color=%23fe8e86)](https://github.com/sponsors/ramonmachadocarmo)

**Manaus Route Planner** is the driver-facing app for the ERP's delivery routes — built for
**Android Automotive OS**, **Android Auto**, and **Android smartphones**. It is a separate app
from the ERP's main `mobile/` (office/warehouse) Flutter app, sharing nothing but the same
backend and the same login.

It uses **Clean Architecture** and **SOLID** design principles, backed by the ERP's own
`identity-service` (login/permissions), `config-service` (vehicles/customers) and `sales-service`
(delivery plans, route alternatives, arrival confirmation) — the same endpoints
`apps/web/mfes/sales`'s route-planning screens already use.

---

## 🚀 Version 0.2.0 Overview

* **Version:** `0.2.0`
* **Target SDK:** 37 · **Minimum SDK:** 29 (Android 10+)
* **Architecture:** Clean Architecture + SOLID (Multi-module: `:shared`, `:mobile`)

---

## ✨ What it does

### 🔐 1. Same login as the rest of the ERP
* `POST /api/identity/auth/login` (identity-service) — the driver's usual ERP credentials.
* Access is gated on the role having **view permission on both `/logistica/rotas` and
  `/logistica/entrega`** (the same `menu_permissions` map the web/mobile role matrix use) — a
  role without both is shown "sem acesso", not a route.
* Login happens on the **phone** screen only. Android Auto never asks for a password — typing
  credentials on the car template widgets is both awkward and a driver-safety concern — so the
  car screen just reads the session the phone already established (shared `TokenStore`,
  AES-GCM/Android-Keystore-encrypted, no plaintext token on disk).
* **Biometric sign-in** (fingerprint/face, `androidx.biometric`, BIOMETRIC_STRONG only — a
  screen-lock PIN doesn't count): after a password login, the driver can save email+password
  encrypted on-device; next launch offers the fingerprint automatically (same UX as
  `mobile/lib/core/biometric/biometric_service.dart`). The prompt never talks to the backend by
  itself — success just unlocks the stored password, which still goes through the real
  `/auth/login` call. A rejected password (changed/revoked) clears the stored credentials
  automatically; logging out does not.

### 🚚 2. Real delivery plans, not a local JSON file
* Driver picks their vehicle (from `config-service`'s `/vehicles`); the app loads that vehicle's
  plan for **today** from `sales-service`'s `/delivery-plans`.
* Already-delivered stops (checked against `/sales-orders`' own status) are filtered out on every
  load — reopen the app mid-route and it picks up where it left off.

### 🔄 3. Real route alternatives — no simulated traffic engine
* sales-service computes up to 3 real OSRM-routed alternatives per plan at creation time
  ("Melhor tempo", "Menor distância", "Alternativa"). "Recalcular" is picking one of those and
  confirming it (`POST /delivery-plans/:id/confirm`) — not an on-device heuristic.

### 🎨 4. Custom vector map, real coordinates
* `CustomMapSurfaceRenderer` draws the plan's actual OSRM route geometry and stop coordinates,
  projected onto the car surface — not fixed decorative positions.

### ✅ 5. Arrival confirmation hits the real order
* "Confirmar Chegada" calls `POST /sales-orders/:id/deliver`; a failed attempt calls
  `POST /sales-orders/:id/fail` with a note — both persist server-side, visible from the web's own
  Entrega screen too.

### 📱 6. Phone app (`:mobile`)
* Jetpack Compose, reuses 100% of `:shared`'s Clean Architecture (domain, use cases,
  repositories, `RouteFlowController`) — the car screen and the phone screen run the exact same
  flow logic, just two different renderers.

### 🧪 7. Unit test suite
* `Session` permission logic, `Address` formatting, `RoutePlan` computed fields — pure, no Android
  dependency.
* `ApiClient` and `RouteRepositoryImpl` against a `MockWebServer` fake backend (bearer header,
  401 handling, error parsing, delivered-stop filtering, options round-tripping).
* Offline cache: `FileOfflineCache` (round-trip, per-vehicle, day expiry, corrupt files, path-safe
  names), `CachedRouteRepository`/`CachedConfigRepository` (network-only fallback, server errors
  never masked, stop removal on delivery) and `LogoutUseCase` cache wipe.
* `TokenStore` (Android Keystore) isn't covered by local JVM unit tests — Keystore only exists on
  a real device/emulator, so that needs an instrumented test instead.

---

## 🏗️ Project Architecture

```text
logi-mobile/
├── :shared/         Domain entities, use cases, repositories, networking, RouteFlowController,
│                    MyCarAppService (Android Auto — projected onto the car screen from the phone)
└── :mobile/         Android smartphone app (Jetpack Compose UI) — login + vehicle + route;
                     Android Auto comes bundled automatically, no separate install
```

No standalone Android Automotive OS app (a car with Android as its own dashboard OS, no phone
involved) — only Android Auto, which projects from this same phone app.

---

## 🔮 Next steps

- [ ] **Live GPS tracking:** `FusedLocationProviderClient` for automatic arrival detection by
  geofence, instead of a manual tap.
- [ ] **Push-based plan updates:** today the driver has to tap "Atualizar" to see a dispatcher's
  change; a queue/websocket push would remove that.
- [x] **Offline caching (read):** the vehicle list and each vehicle's plan for today are kept in
  app-private JSON files (`FileOfflineCache`); when the server is unreachable the last saved plan is
  shown with a "Sem conexão — exibindo rota salva às HH:mm" banner. Cleared on logout/401 and
  ignored the next day.
- [x] **Offline write queue:** "Confirmar chegada"/"Falha" made without connectivity go into a
  durable queue (`FilePendingActionQueue`, kept across restarts and logout) and the driver sees the
  stop as done. A WorkManager job (`SyncPendingActionsWorker`, constraint: network connected,
  exponential backoff) replays them oldest-first via `PendingActionSyncer`: acknowledged -> removed;
  offline/5xx/408/429 -> kept and retried; 401 -> kept until next login; other 4xx (e.g. order
  already delivered) -> marked rejected, not retried, and reported in the UI banner.
- [ ] Show the offline banner on the Android Auto screen too (only the phone UI has it today).

---

## 🚢 Deploy

Release e publicação na Google Play: `make logi-release patch|minor|major` e `make logi-publish` (na raiz do
monorepo). CI/CD em `.github/workflows/android.yml`; configuração única e pendências em
[DEPLOY_ANDROID.md](DEPLOY_ANDROID.md).
