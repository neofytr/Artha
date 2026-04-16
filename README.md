# Artha

Artha is an event-sourced payment engine written in Java 21 and Spring Boot. I built it to understand how a real fintech backend holds together — not just "CRUD with Spring Boot", but the messier parts: consistency across services, idempotent retries, rebuilding state from an event stream, transactions that can't fit in one database row.

The name is Sanskrit for wealth or purpose. It seemed to fit.

## What it actually does

You can open accounts, deposit and withdraw money, put funds on hold, and move money between accounts. Nothing here is new — banks have done it for a century. What's interesting is *how*.

Every state change is stored as an immutable event in Postgres. The current balance of an account isn't a column somewhere; it's the sum of every deposit, withdrawal, reservation and commit that ever happened on that account, replayed in order. If you think the balance looks wrong, you can read the event stream and see exactly why it is what it is — which is the thing accountants have always wanted from software.

Money transfers are the part I spent the most time on. Moving 100 rupees from Alice to Bob touches two different aggregates, which means two different optimistic locks, two different event streams, two different places something can fail. You can't wrap that in a database transaction without giving up a lot. So transfers run as a saga: reserve the amount on Alice's account, credit Bob's account, commit Alice's reservation. If any step fails, the steps that already ran get compensated in reverse. If a compensation itself fails, the saga lands in a `STUCK` state that yells for human attention instead of silently losing money.

## How it's structured

The layout is boring on purpose:

```
core/        — framework code with no business knowledge
  event/     — event store interface, domain event base class, concurrency exceptions
  aggregate/ — aggregate root, repository, snapshot strategy
  cqrs/      — command bus, query bus, handler registry
  saga/      — saga orchestrator, step interface, state machine
  resilience/— circuit breaker, token-bucket rate limiter, retry w/ jitter
  idempotency/

domain/      — business rules, no frameworks leak in here
  account/   — Account aggregate, Money value object, events, exceptions
  transfer/  — TransferContext (saga payload)

application/ — glues commands to aggregates and queries to read models
  command/
  query/
  saga/      — TransferMoneySaga step definitions

infrastructure/ — the ugly but necessary part
  persistence/   — JPA event store, outbox table, read-model projections, JPA saga store
  messaging/     — Kafka producer/consumer, outbox relay, DLQ publisher
  security/      — JWT filter, token issuer, Spring Security config
  observability/ — custom health indicators, traced saga orchestrator

api/
  rest/      — controllers, error handler, idempotency & correlation filters
  config/    — Spring config, Kafka config
  dto/
```

The rule I tried to hold to: dependencies point inward. `domain` doesn't know Spring exists. `core` doesn't know about Postgres. `infrastructure` and `api` can look at everything. When you read a file, you should know roughly what layer you're in without having to scroll up.

## The pieces worth looking at

**Event store** (`infrastructure/persistence/JpaEventStore.java`) — appends events with optimistic concurrency enforced by a unique constraint on `(aggregate_id, sequence_number)`. If two writes race, the second one's INSERT fails and the caller gets a `ConcurrencyException`. The command layer retries those with exponential backoff; every other exception propagates.

**Outbox relay** (`infrastructure/messaging/OutboxRelay.java`) — the piece that solves the "dual write" problem. Events are written to an `event_outbox` table in the same database transaction as the event store append. A separate poller ships them to Kafka using `SELECT ... FOR UPDATE SKIP LOCKED` so multiple relay workers can run in parallel without stepping on each other. Delivery is at-least-once; consumers dedupe by `event_id`.

**Saga orchestrator** (`core/saga/SagaOrchestrator.java`) — runs steps in order, keeps track of the last completed step, rewinds on failure. Persists state after every transition (to Postgres via `JpaSagaStateStore`) so a process crash doesn't leave an orphaned saga. If compensation fails, you get `STUCK` instead of `FAILED` because those really are different things — one recovered cleanly, the other needs a human.

**Circuit breaker** (`core/resilience/CircuitBreaker.java`) — the three-state machine (`CLOSED` → `OPEN` → `HALF_OPEN`). State transitions use CAS so concurrent callers don't need to take a mutex on the hot path. I wrote this from scratch instead of pulling in Resilience4j because the point was to understand the state machine, not to configure one.

**Token bucket rate limiter** (`core/resilience/TokenBucketRateLimiter.java`) — per-key buckets with continuous refill, all math in integer scaled-fixed-point to avoid floating point drift. There's one CAS loop per `tryAcquire`; no locks.

**Idempotency filter** (`api/rest/IdempotencyFilter.java`) — servlet filter that intercepts mutating requests with an `Idempotency-Key` header. First request processes normally and caches the response; duplicate requests with the same key get the cached response back. Concurrent duplicates get a 409. The store is Postgres-backed with a unique primary key, so the "who gets there first" race is resolved by the database instead of application code.

**Account aggregate** (`domain/account/Account.java`) — enforces the invariants you'd want from a bank account: no overdraft, no mixing currencies, no debiting a frozen account, no closing an account with outstanding reservations. All state changes go through `raise(event)` which applies the event and queues it for persistence; replay on load reconstructs state by feeding each historical event back to the same `apply()` method. Same code path, same results.

**DLQ routing** (`infrastructure/messaging/DeadLetterPublisher.java`) — when a consumer can't project an event (bad schema, projection bug) after 3 retries, the message is routed to `<topic>.dlq` with the original topic, offset, and exception attached as headers. The consumer group keeps moving instead of blocking on poison messages.

**Custom health indicators** (`infrastructure/observability/`) — `/actuator/health` reports DOWN when the outbox backlog exceeds a threshold (Kafka down or relay wedged) or when any saga is in `STUCK` state (failed compensation, ledger may be inconsistent). Different signals, different on-call actions.

## Consistency model

- The **write side** is strongly consistent. A successful command's effects are durable before the HTTP response returns. Optimistic locking means concurrent writes on the same aggregate serialize (losers retry).
- The **read side** is eventually consistent. The projection lags the event store by however long it takes a Kafka consumer to catch up — usually tens of milliseconds, occasionally more if a consumer is rebalancing. Clients that need read-your-writes should use the write-side load (replay events) rather than querying the read model.
- Across aggregates, we get **saga-level consistency**. The system converges — either the transfer completes or is rolled back — but there's a window where Alice has been debited and Bob hasn't been credited yet. Observers need to tolerate that window or look at the saga state to know it's in flight.

## Tests

`mvn test` runs 36 tests across four layers.

**Unit tests (22):**
- Account aggregate invariants — no overdraft, frozen rejects operations, replay fidelity, snapshot round-trip
- Money value object — scaling, currency mismatch, arithmetic
- Circuit breaker state transitions — open after N failures, half-open probe success/failure
- Token bucket refill, key isolation
- Saga orchestrator happy path, compensation order, `STUCK` on compensation failure

**Property-based tests (4):** using jqwik. Each property runs 50–500 randomly generated sequences.
- `balanceNeverGoesNegative` — no sequence of legal operations can produce a negative balance
- `replayReproducesState` — replaying the event stream deterministically produces the same state as live execution
- `snapshotRoundTripPreservesState` — snapshot serialize/deserialize loses no information
- `ledgerConservation` — across many accounts and many random transfers, total money is conserved

Property tests are where I get the most confidence. Example-based tests confirm the cases I thought of; these find the cases I didn't.

**Integration tests (6):** Spring Boot context with H2 (PostgreSQL mode) + EmbeddedKafka. Full command dispatch → event store → outbox → read model.
- Open account persists events and rebuilds on load
- Deposit/withdraw reflected in balance
- Overdraft rejected, state unchanged
- Transfer saga happy path atomically moves funds
- Transfer saga with insufficient funds leaves total conserved
- Transfer saga to a frozen destination rolls back source reservation

A Testcontainers variant with real Postgres + real Kafka is stubbed out but requires Docker; pending.

## Running it

```bash
docker compose up -d   # postgres, kafka, zookeeper, redis, prometheus
mvn spring-boot:run    # app on :8080
```

That'll bring up Postgres, Kafka, Zookeeper, Redis and Prometheus, then start the app on port 8080. Flyway runs the migrations on startup.

For tests, there's no external setup. Everything is self-contained:

```bash
mvn test
```

A few things you can poke at once it's up:

```bash
# Get a token
curl -X POST localhost:8080/api/v1/auth/login \
  -H 'Content-Type: application/json' \
  -d '{"userId":"alice","password":"secret"}'

# Open an account
curl -X POST localhost:8080/api/v1/accounts \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"ownerId":"alice","currency":"INR"}'

# Deposit
curl -X POST localhost:8080/api/v1/accounts/$ACC/deposits \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -H "Idempotency-Key: $(uuidgen)" \
  -d '{"amount":"1000.00","currency":"INR","reference":"seed"}'

# Transfer
curl -X POST localhost:8080/api/v1/transfers \
  -H "Authorization: Bearer $TOKEN" \
  -H "Content-Type: application/json" \
  -d '{"sourceAccountId":"...","destinationAccountId":"...","amount":"100","currency":"INR"}'
```

Prometheus scrapes `/actuator/prometheus`; custom counters like `artha.outbox.published` and `artha.outbox.failed` are exposed there. Traces export to OTLP at `$OTEL_ENDPOINT` (defaults to `localhost:4318`).

## Choices I'd probably reconsider

A few places where I made a call and wasn't 100% sure it was right:

- **BigDecimal for Money**. Storing as integer minor units (long cents) would be faster and less error-prone for arithmetic. I went with BigDecimal + `Currency` so the currency-precision logic was explicit, but JPY has 0 fraction digits and BHD has 3 and my migrations assume 8-digit scale everywhere — that's more about database portability than domain correctness. If this were going to production I'd use a custom `Amount` type that's always integer minor units plus a separate `Currency` column.
- **Snapshot strategy** is naive — snapshot every N versions regardless of aggregate size. A smarter strategy would look at event payload size and snapshot more aggressively for chatty aggregates.
- **Event schema evolution**. Right now, renaming or removing an event field is a breaking change. A real system needs upcasters that read old versions and produce new ones on replay. I left hooks in `EventSerializer` for that but didn't implement it.
- **TEXT instead of JSONB** for event payloads. I initially used JSONB but backed off to TEXT for H2 test compatibility. In a real deployment I'd put JSONB back and add GIN indexes for payload queries.

## What's missing on purpose

- No multi-currency conversion. If Alice's account is in INR and Bob's is in USD, the transfer fails with a currency mismatch. Adding FX would mean another bounded context (pricing/FX) and another saga step; I didn't want to grow the surface area.
- No Redis usage yet. It's in the compose file and the dependency is in pom.xml but I didn't get to the caching layer I had planned for read-model hot paths.

## License

MIT. Do whatever you want with it.
