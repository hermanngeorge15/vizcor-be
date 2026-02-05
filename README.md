# Kotlin Coroutines Visualizer - Backend

A powerful **real-time debugging and visualization backend** for Kotlin coroutines. This Ktor-based server tracks, stores, and streams coroutine lifecycle events, job states, thread activity, and suspension points to help developers understand and debug complex concurrent Kotlin applications.

## 🎯 What Does It Do?

This backend provides a complete observability solution for Kotlin coroutines:

- **📊 Real-Time Event Tracking**: Captures all coroutine lifecycle events (created, started, suspended, resumed, completed, cancelled)
- **🔄 Job State Monitoring**: Tracks job states including active, waiting for children, completing, cancelled, and completed states
- **🧵 Thread & Dispatcher Tracking**: Monitors which threads and dispatchers execute your coroutines
- **⏱️ Timeline Generation**: Builds detailed timelines showing coroutine progression and state changes
- **🌊 Server-Sent Events (SSE)**: Streams events in real-time to connected clients (e.g., frontend visualizer)
- **💾 Session Management**: Maintains isolated sessions for different debugging scenarios
- **🎬 Scenario Library**: Pre-built test scenarios demonstrating various coroutine patterns (nested launches, async/await, cancellation, etc.)

## 🏗️ Architecture Overview

The application follows an **event-sourced architecture**:

1. **VizSession**: Central container managing event tracking for a visualization session
2. **EventBus**: Real-time event distribution to subscribers via Kotlin Flows
3. **EventStore**: Persistent storage of all events with sequence ordering
4. **RuntimeSnapshot**: Current state projection of all tracked coroutines
5. **ProjectionService**: Computes derived views (timelines, hierarchies, relationships)
6. **Instrumentation Wrappers**: `VizScope`, `VizDispatcher`, etc. that wrap standard coroutine APIs to emit events

### Key Components

- **Events**: Strongly-typed event model covering coroutine lifecycle, job states, suspensions, deferred values, and flows
- **Session API**: REST endpoints for creating/managing visualization sessions
- **Scenario API**: Endpoints to run pre-defined coroutine test scenarios
- **SSE Streaming**: Real-time event streaming with history replay support
- **Metrics**: Prometheus-compatible metrics via Micrometer

## 🚀 Use Cases

- **Debugging Structured Concurrency**: Visualize parent-child relationships and cancellation propagation
- **Performance Analysis**: Identify blocking operations, thread starvation, or excessive suspensions
- **Learning Tool**: Understand how coroutines, jobs, and structured concurrency actually work
- **Testing**: Validate coroutine behavior in complex concurrent scenarios
- **Production Monitoring**: Track coroutine health and performance in live systems

## 🚀 Quick Start

### Prerequisites
- JDK 11 or higher
- Gradle (wrapper included)

### Run the Server

```bash
./gradlew run
```

The server will start at `http://localhost:8080`
## 🛠️ Technology Stack

- **[Ktor](https://ktor.io/)** - Asynchronous web framework for Kotlin
- **[Kotlin Coroutines](https://kotlinlang.org/docs/coroutines-overview.html)** - Structured concurrency for Kotlin
- **[kotlinx.serialization](https://github.com/Kotlin/kotlinx.serialization)** - JSON serialization
- **[Micrometer](https://micrometer.io/)** - Application metrics (Prometheus compatible)
- **[Logback](https://logback.qos.ch/)** - Logging framework
- **[Gradle](https://gradle.org/)** - Build tool
- **Server-Sent Events (SSE)** - Real-time event streaming
- **OpenAPI/Swagger** - API documentation

## Contributing
PRs are welcome 🙌  
Please read [CONTRIBUTING.md](./CONTRIBUTING.md) before opening a Pull Request.

## Features

Here's a list of features included in this project:

| Name                                                                   | Description                                                                        |
|------------------------------------------------------------------------|------------------------------------------------------------------------------------|
| [AsyncAPI](https://start.ktor.io/p/asyncapi)                           | Generates and serves AsyncAPI documentation                                        |
| [Routing](https://start.ktor.io/p/routing)                             | Provides a structured routing DSL                                                  |
| [Swagger](https://start.ktor.io/p/swagger)                             | Serves Swagger UI for your project                                                 |
| [OpenAPI](https://start.ktor.io/p/openapi)                             | Serves OpenAPI documentation                                                       |
| [Server-Sent Events (SSE)](https://start.ktor.io/p/sse)                | Support for server push events                                                     |
| [Micrometer Metrics](https://start.ktor.io/p/metrics-micrometer)       | Enables Micrometer metrics in your Ktor server application.                        |
| [Content Negotiation](https://start.ktor.io/p/content-negotiation)     | Provides automatic content conversion according to Content-Type and Accept headers |
| [kotlinx.serialization](https://start.ktor.io/p/kotlinx-serialization) | Handles JSON serialization using kotlinx.serialization library                     |

## 📡 API Endpoints

### Scenario Execution
- `GET /api/viz/one-launch-scenario` - Simple single coroutine launch
- `GET /api/viz/two-launch-scenario` - Two parallel coroutines
- `GET /api/viz/nested-launch-scenario` - Nested coroutine hierarchies
- `GET /api/viz/run-scenario-with-data` - Full scenario with captured data
- `POST /api/scenarios/run` - Run custom scenario from DSL definition

### Test Scenarios (Job State Tracking)
- `GET /api/tests/waiting-for-children` - Parent waiting for child coroutines
- `GET /api/tests/nested-waiting` - Multi-level waiting hierarchies
- `GET /api/tests/cancel-waiting` - Cancellation propagation tests
- `GET /api/tests/progress-tracking` - Monitor child completion progress
- `GET /api/tests/job-status-all` - Run all job status test scenarios


## 📂 Project Structure

```
src/main/kotlin/com/jh/proj/coroutineviz/
├── Application.kt              # Main entry point
├── HTTP.kt                     # CORS and HTTP configuration
├── Routing.kt                  # Route registration
├── Serialization.kt            # JSON serialization setup
├── events/                     # Event model definitions
│   ├── VizEvent.kt            # Base event interfaces
│   ├── coroutine/             # Coroutine lifecycle events
│   ├── job/                   # Job state events
│   ├── dispatcher/            # Thread/dispatcher events
│   ├── flow/                  # Kotlin Flow events
│   └── deferred/              # Async/Deferred events
├── session/                    # Session management
│   ├── VizSession.kt          # Core session container
│   ├── SessionManager.kt      # Global session registry
│   ├── EventBus.kt            # Real-time event distribution
│   ├── EventStore.kt          # Event persistence
│   └── ProjectionService.kt   # Timeline/hierarchy projections
├── wrappers/                   # Instrumentation wrappers
│   ├── VizScope.kt            # CoroutineScope wrapper
│   ├── VizDispatchers.kt      # Dispatcher tracking
│   └── VizDeferred.kt         # Async/await tracking
├── routes/                     # HTTP route handlers
│   ├── SessionRoutes.kt       # Session CRUD + SSE
│   ├── VizScenarioRoutes.kt   # Pre-built scenarios
│   └── TestRoutes.kt          # Test scenario endpoints
├── scenarios/                  # Scenario DSL and runner
└── models/                     # Data models and DTOs
```

## 🔧 How It Works

The visualizer uses **instrumentation wrappers** around standard coroutine APIs to capture events without modifying application code:

```kotlin
// Create a visualization session
val session = VizSession("my-debug-session")

// Use VizScope instead of CoroutineScope
val scope = VizScope(session)

// Your coroutine code remains mostly unchanged
scope.vizLaunch("my-coroutine") {
    println("This coroutine is being tracked!")
    vizDelay(1000)  // Tracked delay
    vizLaunch("child") {
        // Nested coroutines are tracked too
    }
}

// Events are automatically captured and streamed
session.eventBus.events.collect { event ->
    println("Event: ${event.kind}")
}
```

### Event Flow

1. **Instrumented Code** → Coroutine wrappers emit events (e.g., `CoroutineCreated`, `CoroutineStarted`)
2. **VizSession** → Receives events and processes them through:
   - **EventStore**: Appends to persistent log
   - **RuntimeSnapshot**: Updates current state
   - **EventBus**: Broadcasts to subscribers
3. **Clients** → Consume events via:
   - **REST API**: Query snapshots and timelines
   - **SSE Stream**: Real-time event feed

### Supported Event Types

- **Coroutine Lifecycle**: Created, Started, Resumed, Suspended, Completed, Cancelled
- **Job States**: Active, WaitingForChildren, Completing, Completed, Cancelled
- **Suspensions**: Delay, Join, Await, AwaitAll, WithContext
- **Deferred Values**: DeferredCreated, DeferredCompleted, AwaitStarted, AwaitCompleted

## 💡 Usage Examples

### Basic Session Creation

```bash
# Create a new session
curl -X POST "http://localhost:8080/api/sessions?name=my-session"

# Response: {"sessionId": "my-session-123", "message": "Session created successfully"}
```

### Stream Events in Real-Time

```bash
# Connect to SSE stream (history + live events)
curl -N "http://localhost:8080/api/sessions/my-session-123/events"
```

### Run a Test Scenario

```bash
# Run nested launch scenario
curl "http://localhost:8080/api/viz/nested-launch-scenario"

# Run job state tracking test
curl "http://localhost:8080/api/tests/waiting-for-children"
```

## 📚 Additional Documentation
- **[CONTRIBUTING.md](./CONTRIBUTING.md)** - Contribution guidelines

## 🧪 Testing

Run all tests:
```bash
./gradlew test
```

View test reports:
```bash
open build/reports/tests/test/index.html
```

### Available Test Suites

- **Unit Tests**: Core logic and event processing
- **Integration Tests**: Full scenario execution with VizSession
- **API Tests**: REST endpoint validation
- **Job State Tests**: Coroutine lifecycle and state tracking

## 🔧 Development

### Code Formatting

The project uses ktlint for code style:

```bash
# Check formatting
./gradlew ktlintCheck

# Auto-format code
./gradlew ktlintFormat
```

### HTTP Request Testing

Use the included HTTP request files in `http-requests/` directory:
- `test.http` - Example API requests (can be run in IntelliJ IDEA)

### Watch Mode

For development with auto-reload:
```bash
./gradlew run --continuous
```


## Building & Running

To build or run the project, use one of the following tasks:

| Task                                    | Description                                                          |
|-----------------------------------------|----------------------------------------------------------------------|
| `./gradlew test`                        | Run the tests                                                        |
| `./gradlew build`                       | Build everything                                                     |
| `./gradlew run`                         | Run the server                                                       |

If the server starts successfully, you'll see the following output:

```
2024-12-04 14:32:45.584 [main] INFO  Application - Application started in 0.303 seconds.
2024-12-04 14:32:45.682 [main] INFO  Application - Responding at http://0.0.0.0:8080
```

### Environment Configuration

Configure the application via `application.yaml` or environment variables:

```yaml
ktor:
  deployment:
    port: 8080
    host: 0.0.0.0
  application:
    modules:
      - com.jh.proj.coroutineviz.ApplicationKt.module
```

### Monitoring

The application exposes Prometheus metrics at `/metrics-micrometer`:
- HTTP request metrics (duration, count, status codes)
- JVM metrics (memory, threads, GC)
- Custom coroutine visualization metrics (sessions, events)


## 📄 License

MIT

## 🤝 Support

- Open an issue for bug reports or feature requests
- Review test scenarios for usage examples
---

**Made with ❤️ using Kotlin and Ktor**

