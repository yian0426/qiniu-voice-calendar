# CLAUDE.md

This file provides guidance to Claude Code (claude.ai/code) when working with code in this repository.

## Project Overview

A voice-powered calendar application — "七牛语音日历" (Qiniu Voice Calendar). Two modules: a Vue 3 frontend and a Spring Boot backend (REST API with JWT auth, MyBatis-Plus, MySQL). Frontend and backend are fully integrated — frontend stores call real API endpoints, and the chat panel streams AI responses via SSE.

## Commands

### Frontend (`fronted/`)
```bash
cd fronted
npm install                     # Install dependencies
npm run dev                     # Start Vite dev server (port 5173, proxies /api → localhost:8080)
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

Stack: **Vue 3** (Composition API, `<script setup>`), **TypeScript**, **Vite**, **Pinia**, **Vue Router**, **Element Plus** (dark theme), **Lucide Vue** (icons), **Axios**, **SCSS**.

- `main.ts` — Bootstrap: Pinia → Router → Element Plus → mount `#app`
- `App.vue` — Root: just `<RouterView />`, imports global `layout.scss`
- `router/index.ts` — Single route: `/` → `HomeView`
- `assets/layout.scss` — Global dark-theme styles: starfield animation, glassmorphism panels, layout grid, scrollbar styles
- Path alias `@/` → `src/`
- `vite.config.ts` — Dev server on port 5173, `/api` proxy to `http://localhost:8080`, includes `vite-plugin-vue-devtools`

**Stores (Pinia):**

| Store | Purpose |
|-------|---------|
| `stores/auth.ts` | Auth state: token (localStorage), userId, username, profile. Methods: `login()`, `register()`, `fetchProfile()`, `logout()`, `clearAuth()`. Used by HomeView, SidebarAvatar, ChatPanel, AgendaPanel. |
| `stores/events.ts` | Event state: `Map<number, CalendarEvent>` for O(1) lookup. Supports optimistic updates (`addOptimistic`, `confirmOptimistic`, `rollbackOptimistic`), AI-tool integration (`applyFromAI`), debounced fetching, per-date querying. Defines `CalendarEvent` interface and `fromEventVO()` converter. |
| `stores/counter.ts` | Boilerplate Pinia store, **not used** by any component. |

**`utils/request.ts`** — Axios instance with full API integration. Base URL from `VITE_API_BASE_URL` (default `/api`). Request interceptor attaches `Bearer` token from `localStorage`. Response interceptor expects `code` 200 or 0, fires `auth:expired` custom event on 401/403. Exports `request<T>(config)`, typed `ApiResponse<T>`, and an `api` object with **20 endpoint methods** across modules: Chat (`streamChat` SSE async generator — AbortController cancellation, 30s idle timeout, stream ID debug tracking, done-signal recovery), Auth (`register`, `login`, `getProfile`, `updateProfile`), Events (`listEvents`, `getEvent`, `createEvent`, `updateEvent`, `patchEvent`, `deleteEvent`, `toggleEventStatus`), Tags (`listTags`, `createTag`, `updateTag`, `deleteTag`), Conversations (`getConversations`, `getMessages`, `deleteConversation`). Also exports `abortCurrentStream()` to cancel ongoing SSE streams. Includes TypeScript interfaces: `StreamEvent`, `ConversationVO`, `MessageVO`, `EventVO`, `CreateEventRequest`, `UpdateEventRequest`, `PatchEventRequest`, `TagVO`, `PageData<T>`, `LoginResponse`, `ProfileResponse`.

**Component tree:**
```
HomeView
├── LoginDialog (v-if="showLogin", triggered by auth:expired event)
├── SidebarAvatar (bottom-left, uses authStore for name/login state, emits showLogin)
├── main-content (flex row)
│   ├── features/calendar/AgendaPanel (left, flex: 1.5) — uses eventStore + authStore, real API data
│   │   ├── features/calendar/CalendarDayCell (receives tasks via props, emits task-click)
│   │   └── components/ThemeTag (presentational, type/color props + slot)
│   └── features/chat/ChatPanel (right, flex: 1) — SSE streaming, real AI chat, image upload
│       └── features/chat/ChatMessageItem (renders user/assistant/status messages)
```

**Key component details:**

- **AgendaPanel** (`features/calendar/`) — Three view modes: agenda (list grouped by day), week (24h × 7d grid), month (calendar grid). Fetches events from real API via `eventStore.fetchEvents()`. Different fetch ranges per view mode (week vs. month). Task detail editing via Element Plus `ElDialog` with inline tag add/remove. Handles 401/403 responses.

- **CalendarDayCell** (`features/calendar/`) — Reusable day cell. Props: `mode` (week|month), `dayNumber`, `isToday`, `tasks`, `maxDisplay`. Emits: `task-click`.

- **ThemeTag** (`components/`) — Colored tag chip. Props: `type` (primary|success|warning|danger|info), `color` (hex string). Renders slot content.

- **ChatPanel** (`features/chat/`, 783 lines) — Fully functional streaming chat client. SSE streaming via `streamChat()` async generator. Handles stream event types: `content`, `status`, `tool_result`, `event_data`, `done`, `error`. On `event_data`, calls `eventStore.applyFromAI()` for instant calendar UI updates. Parses ```calendar-json``` code blocks from AI responses as fallback via `extractCalendarJson()`. Real image upload to `/api/upload` via FormData. Smart scrolling: detects when user scrolls up (80px threshold), shows "new message" badge instead of auto-scrolling. Stop button via `abortCurrentStream()`. 5-second thinking timeout with forced fallback text. Suggestion chips for quick prompts. Stream ID tracking for debug logging.

- **ChatMessageItem** (`features/chat/`) — Renders `user`, `assistant`, and `status` role messages. Shows avatars, timestamps, "thinking..." animation dots, streaming cursor blink effect, "generating reply..." progress indicator. Exports `ChatMessage` interface.

- **LoginDialog** (`features/auth/`) — Login/Register modal via Element Plus `ElDialog`. Toggles between login and register forms. Validates inputs (username 3-50 chars, password min 6 chars). Calls `authStore.login()` / `authStore.register()`. Prevents closing when not logged in (no close button, no click-outside dismiss).

- **SidebarAvatar** — Uses `authStore` for display name and login state. Emits `showLogin` event when clicked while not logged in. Performs real `authStore.logout()` with ElMessage toast confirmation.

### Backend (`voice_calendar-backend/`)

Stack: **Spring Boot 3.3.4**, **Java 21**, **Maven**, **MyBatis-Plus 3.5.10.1**, **MySQL**, **Spring Security + JWT** (jjwt 0.12.7), **Lombok**, **java.net.http.HttpClient** (for AI streaming).

**Package structure:**
```
com.qiniu.voice_calendar
├── common/          Result<T> — unified response wrapper { code, message, data }
├── config/          SecurityConfig, JwtAuthenticationFilter, JwtProperties, AiProperties
├── controller/      AuthController, EventController, TagController, ChatController
├── dto/             16 request/response DTOs (see below)
├── entity/          User, Event, Tag, EventTag, Conversation, Message
├── exception/       BusinessException (runtime, carries HTTP code), GlobalExceptionHandler
├── mapper/          UserMapper, EventMapper, TagMapper, EventTagMapper, ConversationMapper, MessageMapper (all extend BaseMapper<T>)
├── service/
│   ├── UserService, EventService, TagService (existing)
│   ├── AiService (interface — abstract AI provider)
│   ├── ChatService (interface — conversation management)
│   ├── StreamEvent (model — AiService callback event types: CONTENT, TOOL_CALL, DONE, ERROR; ChatServiceImpl additionally sends status/tool_result/event_data types via sendSse() for richer frontend UX)
│   └── impl/        UserServiceImpl, EventServiceImpl, TagServiceImpl,
│                    OpenAiCompatibleService (AI provider), ChatServiceImpl (conversation + tool loop)
└── util/            JwtUtil (HMAC-SHA token gen/validate), SecurityContextUtil (getCurrentUserId/Username)
```

**API endpoints (19 total):**

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
| PATCH | `/api/events/{id}` | Yes | Partial update (null-safe field updating: title, description, startTime, endTime, location, participants, reminderBefore, tags) |
| DELETE | `/api/events/{id}` | Yes | Delete (ownership check) |
| PATCH | `/api/events/{id}/status` | Yes | Toggle status (0=未完成, 1=已完成) |
| GET | `/api/tags` | Yes | List user's tags with eventCount |
| POST | `/api/tags` | Yes | Create tag |
| PUT | `/api/tags/{id}` | Yes | Update tag (ownership check) |
| DELETE | `/api/tags/{id}` | Yes | Delete tag (cascades via FK) |
| **POST** | **`/api/chat/stream`** | **Yes** | **SSE streaming chat (5 min timeout, virtual threads, sends `event_data` SSE for instant calendar UI updates)** |
| **GET** | **`/api/conversations`** | **Yes** | **List user's conversations (ordered by updatedAt desc)** |
| **GET** | **`/api/conversations/{id}/messages`** | **Yes** | **Get messages for a conversation (ownership check)** |
| **DELETE** | **`/api/conversations/{id}`** | **Yes** | **Delete conversation + cascade delete messages** |

**New DTOs (added beyond original 13):**
- `ChatRequest` — Chat input: conversationId (nullable) + content (@NotBlank)
- `ConversationVO` — Conversation list item: id, title, createdAt, updatedAt
- `MessageVO` — Message item: id, role, createdAt, plus computed `content` from metadata
- `StatusRequest` — Event status toggle: status (0 or 1, @NotNull @Min @Max)

**AI Integration (new):**

- **AiService** — Abstract interface for AI providers. Method: `streamChat(List<ChatMessage>, List<Map<String,Object>> tools, Consumer<StreamEvent> callback)`. Defines inner `ChatMessage` record (role + content).
- **OpenAiCompatibleService** (391 lines) — Implements `AiService`. Supports two AI provider formats: OpenAI-compatible (`/chat/completions`) and Anthropic-compatible (`/v1/messages`). Uses `java.net.http.HttpClient` for non-blocking streaming. Parses SSE streams for both formats. Handles tool call accumulation and structured output parsing. Auth style configurable via `AiProperties` (api-key header vs. Bearer).
- **ChatServiceImpl** (442 lines) — Full conversation management with AI tool calling loop:
  1. Gets or creates conversation (auto-titles from first user message)
  2. Saves user message
  3. Builds LLM context with system prompt (current Beijing time, explicit today/tomorrow/day-after-tomorrow date mapping, calendar-json output instructions)
  4. First LLM call with 5 tool definitions: `create_event`, `list_events`, `update_event`, `delete_event`, `get_event`
  5. Executes tool calls against EventService (ownership-scoped to current user). Each tool sends real-time feedback: `status` events with human-readable Chinese descriptions via `describeToolCall()` (e.g. "创建日程"), `tool_result` events with operation summaries, and `event_data` SSE events with full event details via `sendEventData()` for immediate frontend calendar rendering.
  6. Second LLM call with tool results for natural language summary
  7. Saves assistant message. System prompt instructs AI to output ```calendar-json``` blocks for frontend rendering as a fallback.
- **StreamEvent** — AiService callback event model. Types: `CONTENT`, `TOOL_CALL`, `DONE`, `ERROR`. Factory methods: `content()`, `toolCall()`, `done()`, `error()`. ChatServiceImpl's `sendSse()` helper additionally emits `status` (progress indicators like "正在创建日程..."), `tool_result` (operation result text), and `event_data` (structured event data for frontend `applyFromAI()`) types — these bypass StreamEvent and are built as JSON maps directly.
- **AiProperties** — `@ConfigurationProperties(prefix = "ai")`. Configures: `apiKey`, `baseUrl` (default OpenAI), `model` (default gpt-4o), `format` ("openai" or "anthropic"), `authStyle` ("api-key" or "bearer"). Values injected from `AI_*` environment variables with defaults in `application.yaml`.

**Security:** Stateless JWT. Filter extracts `Bearer <token>`, sets `SecurityContextHolder` with `principal=userId` (Long). No roles — authorization is ownership-based in service layer. BCrypt for passwords. OPTIONS requests permitted globally for CORS preflight. Explicit `AuthenticationManager` bean in SecurityConfig.

**Database:** 8 tables — `users`, `events`, `tags`, `event_tags`, `reminders` (event reminders with remind_at/sent fields), `conversations` (AI chat conversations), `messages` (chat messages with role/content — entity fields; DB also has intent, audio_url, metadata columns for planned voice features), `attachments` (file attachments per event). Participants stored as JSON string in `events.participants`. No Flyway/Liquibase — schema managed externally via `sql/create_tables.sql`. MyBatis-Plus logical delete configured but not used (no `deleted` field on entities). Test profile uses H2 in-memory with `schema-h2.sql`.

**Key design decisions:**
- Tags auto-created when assigned to events (deterministic color from `name.hashCode()`)
- Duration stored as human-readable string ("1h 30m"), auto-calculated from start/end times
- All queries use MyBatis-Plus `LambdaQueryWrapper` — no custom SQL
- Global exception handler maps `BusinessException` → response code, `MethodArgumentNotValidException` → 400 with field errors
- AI chat uses virtual threads for non-blocking SSE streaming with 5-minute timeout
- AI tool calling loop: LLM → execute tools → LLM summary, with `event_data` SSE events pushing calendar updates to frontend mid-stream
- API style and auth method for AI provider are configurable via `AiProperties`, supporting both OpenAI and Anthropic formats

**Tests:** 38+ tests across 5 classes (AuthControllerTest, EventControllerTest, TagControllerTest, ChatControllerTest, context load). Controllers tested with standalone MockMvc + `@Mock` services + `MockitoExtension`. Test helpers: `FakeJwtFilter` (accepts `"Bearer valid-token"` → userId=1), `TestSecurityConfig` (minimal security for tests), `TestHelper` (generates real JWT tokens for integration tests).

### Frontend → Backend Integration

Fully wired up. The frontend's `request.ts` calls `/api` endpoints and expects `{ code, message, data }` responses — matching the backend's `Result<T>` wrapper exactly. Frontend stores (`auth.ts`, `events.ts`) manage state and call `api.*` methods. SSE streaming chat uses an async generator over Fetch + ReadableStream. The backend returns HTTP 200 for all responses (errors distinguished by `code` field), which the frontend interceptor handles. `auth:expired` custom event on 401/403 triggers the login dialog automatically.
