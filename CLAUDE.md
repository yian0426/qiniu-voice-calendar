# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A voice-powered calendar application — "七牛语音日历" (Qiniu Voice Calendar). Two modules: a Vue 3 frontend and a Spring Boot backend. Currently early-stage: frontend has UI scaffolding with mock data, backend is a skeleton with no endpoints yet.

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
./mvnw test                     # Run tests
```

## Architecture

### Frontend (`fronted/src/`)

Stack: **Vue 3** (Composition API, `<script setup>`), **TypeScript**, **Vite 8**, **Pinia** (state), **Vue Router**, **Element Plus** (UI library, dark theme CSS vars loaded), **Lucide Vue** (icons), **Axios** (HTTP), **SCSS**.

- `main.ts` — App bootstrap: creates Pinia → Vue Router → Element Plus → mounts to `#app`
- `App.vue` — Root component, just `<RouterView />`, imports global `layout.scss`
- `router/index.ts` — Single route: `/` → `HomeView`
- `stores/` — Pinia stores (currently only a `counter` example store)
- `utils/request.ts` — Axios instance with interceptors. Base URL from `VITE_API_BASE_URL` env var (defaults to `/api`). Request interceptor attaches `Bearer` token from `localStorage`. Response interceptor checks `code` field (expects 200 or 0). Standard API response shape: `{ code: number, message: string, data: T }`. The `request<T>()` function is the single exported entry point for API calls.
- `assets/layout.scss` — Global styles, starry animated background, glassmorphism panels, layout grid
- Path alias `@/` → `src/` (configured in both `tsconfig.app.json` and `vite.config.ts`)

**Component tree (HomeView layout):**
```
HomeView
├── SidebarAvatar (bottom-left, user menu popup)
├── main-content (flex row)
│   ├── AgendaPanel (left, flex: 1.5) — calendar/task list
│   │   └── CalendarDayCell (reusable day cell for week/month grids)
│   │   └── ThemeTag (colored tag chip)
│   └── ChatPanel (right, flex: 1) — chat interface with mock suggestions
├── right-floating-actions (vertical button group)
└── notification-fab (bottom-right badge)
```

**AgendaPanel** is the most complex component. Three view modes: agenda (list), week (time grid), month (calendar grid). All data is hardcoded mock `Task[]` — no real API calls yet. Task detail editing via Element Plus Dialog.

### Backend (`voice_calendar-backend/`)

Stack: **Spring Boot 4.0.6**, **Java 21**, **Maven**, **MySQL** (connector present but not yet configured), **Spring WebMVC**.

- `VoiceCalendarBackendApplication.java` — Standard `@SpringBootApplication` entry point
- `application.yaml` — Only sets `spring.application.name`, no datasource config yet
- No controllers, services, or repositories exist yet — pure skeleton
- Single test: `contextLoads()` verifies the ApplicationContext starts

### Frontend → Backend Integration

Not wired up yet. The frontend's `utils/request.ts` is set up to call `/api` (configurable via `VITE_API_BASE_URL`) and expects `{ code, message, data }` responses. When adding backend endpoints, match this response format.
