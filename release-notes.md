# Release Notes - Manaus Route Planner

## 📦 Version 0.2.0 (Real backend integration)
*Release Date: September 2026*

Replaces every mocked piece from 0.1.0 with the ERP's real backend.

### Changes from v0.1.0

* **Login:** same identity-service login as the web/mobile ERP apps, gated on
  `menu_permissions["/logistica/rotas"]` and `["/logistica/entrega"]` both being granted. Login is
  phone-only — the car screen reads the session the phone already established, never asks for a
  password itself.
* **Real delivery plans:** `FileSystemRouteDataSource`/`routes.json` removed. Routes now come from
  sales-service's `/delivery-plans` (filtered by the driver's chosen vehicle + today), the same
  data apps/web/mfes/sales's route report reads. Already-delivered stops are filtered out on every
  load by cross-checking `/sales-orders` status.
* **Real route alternatives:** `SimulatedTrafficOptimizerEngine` (fake reversed-stop
  "optimization") removed. "Recalcular" now shows the plan's real, server-computed alternatives
  (OSRM, up to 3 per plan) and confirms the chosen one via `POST /delivery-plans/:id/confirm`.
* **Real arrival confirmation:** `POST /sales-orders/:id/deliver` (and `/fail` for a failed
  attempt) — persists server-side, visible from the web's own Entrega screen.
* **Real map:** `CustomMapSurfaceRenderer` now projects the plan's actual OSRM geometry and stop
  coordinates instead of fixed decorative positions.
* **New vehicle picker:** the driver picks which vehicle they're on today (config-service's
  `/vehicles`) — there's no "assigned vehicle" concept in the backend yet.
* Tests: `Session`/`Address`/`RoutePlan` (pure) plus `ApiClient`/`RouteRepositoryImpl` against a
  `MockWebServer` fake backend, replacing the old mock-repository/engine tests.

---

## 📦 Version 0.1.0 (Initial Release)
*Release Date: September 2026*

Welcome to the initial release of **Manaus Route Planner**! Version 0.1.0 establishes the foundation for driver-optimized delivery and route management across Android Automotive OS, Android Auto, and Android Smartphones.

---

### Key Capabilities Included in v0.1.0

#### 1. Core Route Management & File System Data Source
* Loaded delivery route for **Manaus, AM, Brazil** with real addresses and GPS coordinates.
* Implemented `FileSystemRouteDataSource` to read and write `routes.json` from local storage.

#### 2. Traffic Optimization Engine
* Smart route recalculation engine that re-orders intermediate stops to bypass traffic delays.
* Side-by-side route comparison displaying total time (`min`), total distance (`km`), and ETA savings.

#### 3. Custom Map Surface Rendering & Theme
* Custom surface canvas renderer (`CustomMapSurfaceRenderer`) drawing vector map features directly on the car display.
* Styled with a custom **Light Green (`#81C784`)** and **Vivid Orange (`#FF9800`)** design palette.

#### 4. Driver In-App Navigation & Arrival Flow
* Auto-loads the first stop as `🎯 [Destino Atual]`.
* One-tap **`"✅ Confirmar Chegada"`** action that completes the stop, advances to the next destination, and updates remaining distance/time.

#### 5. Smartphone Mobile App
* Jetpack Compose phone interface (`:mobile`) sharing 100% of domain logic with the car app.

#### 6. Enterprise Quality Assurance
* 100% passing unit test suite (`12 passed, 0 failed`) covering Domain Use Cases, Data Repository, DTO Mapping, and Traffic Engines.

---

### Supported Devices & Platforms
* **Android Automotive OS:** Native head unit standalone app (`:automotive`).
* **Android Auto:** Projected car app (`:shared`).
* **Android Smartphones:** Companion phone app (`:mobile`).
