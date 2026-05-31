# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A voice-powered calendar application — "七牛语音日历" (Qiniu Voice Calendar). Two modules: a Vue 3 frontend (UI complete with mock data) and a Spring Boot backend (REST API with JWT auth, MyBatis-Plus, MySQL). Frontend-to-backend integration is not yet wired up — all frontend data is still mock.

## Commands

### Frontend (`fronted/`)
```bash
cd fronted
npm install                     # Install dependencies
npm run dev                     # Start Vite dev server
npm run build                   # Type-check + production build
npm run type-check              # Run vue-tsc type checking only
```

### Backend (`voice_calendar-backend/`)
```bash
cd voice_calendar-backend
./mvnw spring-boot:run          # Start Spring Boot (Windows: mvnw.cmd)
./mvnw test                     # Run all tests
./mvnw test -Dtest=AuthControllerTest        # Run a single test class
```

## Architecture

### Frontend (`fronted/src/`)

Stack: **Vue 3** (Composition API, `<script setup>`), **TypeScript**, **Vite 8**, **Pinia**, **Vue Router**, **Element Plus** (dark theme), **Lucide Vue** (icons), **Axios**, **SCSS**.

- `main.ts` — Bootstrap: Pinia → Router → Element Plus → mount `#app`
- `App.vue` — Root: just `<RouterView />`, imports global `layout.scss`
- `router/index.ts` — Single route: `/` → `HomeView`
- `stores/counter.ts` — Boilerplate Pinia store, **not used** by any component
- `utils/request.ts` — Axios instance. Base URL from `VITE_API_BASE_URL` (default `/api`). Request interceptor attaches `Bearer` token from `localStorage`. Response interceptor expects `code` 200 or 0. Exports `request<T>(config)` and typed `ApiResponse<T>`. The `api` object is an empty placeholder — **no real API calls exist yet**.
- `assets/layout.scss` — Global dark-theme styles: starfield animation, glassmorphism panels, layout grid, scrollbar styles
- Path alias `@/` → `src/`

**Component tree:**
```
HomeView
├── SidebarAvatar (bottom-left, user menu popup — no data flow)
├── main-content (flex row)
│   ├── AgendaPanel (left, flex: 1.5) — owns all Task[] mock data
│   │   ├── CalendarDayCell (receives tasks via props, emits task-click)
│   │   └── ThemeTag (presentational, type/color props + slot)
│   └── ChatPanel (right, flex: 1) — self-contained, mock image upload
├── right-floating-actions (static icon buttons)
└── notification-fab (static badge)
```

**Key component details:**

- **AgendaPanel** — Most complex component. Three view modes: agenda (list grouped by day), week (24h × 7d grid), month (calendar grid). All data is a hardcoded `Task[]` array (12 mock tasks). Task detail editing via Element Plus `ElDialog` with inline tag add/remove. No props, no emits — self-contained.
- **CalendarDayCell** — Reusable day cell. Props: `mode` (week|month), `dayNumber`, `isToday`, `tasks`, `maxDisplay`. Emits: `task-click`.
- **ThemeTag** — Colored tag chip. Props: `type` (primary|success|warning|danger|info), `color` (hex string). Renders slot content.
- **ChatPanel** — Welcome screen with suggestion chips, textarea, simulated image upload (progress bar animation). No actual message sending.
- **SidebarAvatar** — Avatar button with popup menu (profile/theme/logout). Actions only `console.log`.

### Backend (`voice_calendar-backend/`)

Stack: **Spring Boot 4.0.6**, **Java 21**, **Maven**, **MyBatis-Plus 3.5.10.1**, **MySQL**, **Spring Security + JWT** (jjwt 0.12.7), **Lombok**.

**Package structure:**
```
com.qiniu.voice_calendar
├── common/          Result<T> — unified response wrapper { code, message, data }
├── config/          SecurityConfig, JwtAuthenticationFilter, JwtProperties
├── controller/      AuthController, EventController, TagController
├── dto/             13 request/response DTOs
├── entity/          User, Event, Tag, EventTag
├── exception/       BusinessException (runtime, carries HTTP code), GlobalExceptionHandler
├── mapper/          UserMapper, EventMapper, TagMapper, EventTagMapper (all extend BaseMapper<T>)
├── service/         UserService, EventService, TagService + impl/
└── util/            JwtUtil (HMAC-SHA token gen/validate), SecurityContextUtil (getCurrentUserId/Username)
```

**API endpoints (15 total):**

| Method | Path | Auth | Description |
|--------|------|------|-------------|
| POST | `/api/auth/register` | No | Register, returns JWT |
| POST | `/api/auth/login` | No | Login, returns JWT |
| GET | `/api/auth/profile` | Yes | Get current user profile |
| PUT | `/api/auth/profile` | Yes | Update email/avatar |
| GET | `/api/events` | Yes | Paginated list (filters: date range, status, tag, keyword) |
| GET | `/api/events/{id}` | Yes | Get single event (ownership check) |
| POST | `/api/events` | Yes | Create event (auto-creates unknown tags, JSON-serializes participants, auto-calculates duration) |
| PUT | `/api/events/{id}` | Yes | Full update |
| PATCH | `/api/events/{id}` | Yes | Partial update (null-safe) |
| DELETE | `/api/events/{id}` | Yes | Delete (ownership check) |
| PATCH | `/api/events/{id}/status` | Yes | Toggle status (0=未完成, 1=已完成) |
| GET | `/api/tags` | Yes | List user's tags with eventCount |
| POST | `/api/tags` | Yes | Create tag |
| PUT | `/api/tags/{id}` | Yes | Update tag (ownership check) |
| DELETE | `/api/tags/{id}` | Yes | Delete tag (cascades via FK) |

**Security:** Stateless JWT. Filter extracts `Bearer <token>`, sets `SecurityContextHolder` with `principal=userId` (Long). No roles — authorization is ownership-based in service layer. BCrypt for passwords.

**Database:** 4 tables — `users`, `events`, `tags`, `event_tags` (many-to-many). Participants stored as JSON string in `events.participants`. No Flyway/Liquibase — schema managed externally. MyBatis-Plus logical delete configured but not used (no `deleted` field on entities). Test profile uses H2 in-memory with `schema-h2.sql`.

**Key design decisions:**
- Tags auto-created when assigned to events (deterministic color from `name.hashCode()`)
- Duration stored as human-readable string ("1h 30m"), auto-calculated from start/end times
- All queries use MyBatis-Plus `LambdaQueryWrapper` — no custom SQL
- Global exception handler maps `BusinessException` → response code, `MethodArgumentNotValidException` → 400 with field errors

**Tests:** 31 tests across 4 classes (AuthControllerTest, EventControllerTest, TagControllerTest, context load). Controllers tested with standalone MockMvc + `@Mock` services + `MockitoExtension`. Test helper provides fake JWT filter accepting `"Bearer valid-token"` → userId=1.

### Frontend → Backend Integration

The frontend's `request.ts` is configured to call `/api` and expects `{ code, message, data }` responses — this matches the backend's `Result<T>` wrapper exactly. The backend returns HTTP 200 for all responses (errors distinguished by `code` field), which the frontend interceptor already handles. When wiring up, import `request` from `@/utils/request` and call endpoints directly — no adapter layer needed.
