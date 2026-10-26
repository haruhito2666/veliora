# 33 — Build Status & Remaining Work

> Consolidated status of the Veliora / SocietyOS repository as of 2026-10-03.
> Baseline commit: `9bd56b6` (`main`) **plus the uncommitted investigation
> pipeline work described below**. Everything marked ✅ was verified in this
> working tree; everything marked ⏳ is not implemented or not wired yet.
> Local-only notes that are excluded from the public release live in
> `docs/31-implementation-notes.md` (git-ignored).

## At a Glance

| Area | Done ✅ | Left ⏳ |
|------|---------|---------|
| Investigation state machine | `planning → researching → analyzing → debating → synthesizing → vote → confidence_chk → reporting → completed`, with a bounded confidence loop back to `researching` | — |
| Worker pipeline | All six task types end to end (`collect_evidence`, `challenge`, `debate`, `synthesize`, `vote`, `board_report`); idempotent ingestion; timeout sweep; phase-aware failure policy | Dead-letter table, worker heartbeats/leases |
| Persistence | Migrations 001–004; `timeout` task state; debate transcript, per-cycle votes, report rows | Event-table partitioning |
| API | Organization, investigation, evidence, **challenges**, **debate transcript**, **votes**, report, meetings, workers, constitution, evolution, comms, WebSocket | Report `format=pdf/markdown/docx` exports |
| Android | Org selection → home → investigation plan/evidence → report; real API data | Analysis, challenge, debate, voting screens; push notifications |
| Operations | Native run script with readiness gate; E2E golden path through `completed`; per-service logs | Metrics/tracing, DLQ, worker scaling, auth/rate limits |
| Sofuu In-Process Migration | Architecture blueprint & 1-to-1 substrate mapping specified (`docs/34-sofuu-native-migration-plan.md`) | Phase 1–5 implementation (`backend/sofuu/`) |
| Repository | Release docs, this status document, passing Go/Python/Android/E2E checks | Commit the pending change set |

## 1. Verification Snapshot

| Check | Command | Result |
|-------|---------|--------|
| Go unit + integration tests | `cd backend/go && go test ./...` | ✅ all packages pass |
| Go static analysis | `cd backend/go && go vet ./...` | ✅ clean |
| Python worker tests | `cd backend/workers && uv run pytest -q` | ✅ 16 passed |
| Database migrations | `make migrate` (001 + 002 + 003 + 004) | ✅ applied |
| Full golden path E2E | `EXPECT_WORKER_PIPELINE=1 bash scripts/e2e_golden_path.sh` | ✅ PASSED against a live backend |
| Cold-start readiness | `VELIORA_START_WORKERS=1 VELIORA_SCRIPTED_LLM=1 bash scripts/run_backend.sh` | ✅ blocks until NATS subscriptions are live |
| Android debug build | `cd veliora && ./gradlew :app:assembleDebug` | ✅ passes on current tree |

The E2E run verified the whole pipeline end to end: health → organization
(8 workers) → constitution mission → investigation → 5 plan tasks → NATS
worker dispatch → 5 evidence records → 5 peer challenges from outside each
submitting department → debate (10 persisted turns across 2 rounds) →
synthesis → 5 weighted ballots → confidence check → board report with a
recommendation → state `completed`, cycle 1, confidence 1.0, with 126 events in
the store and the WebSocket stream delivering `investigation.started`.

## 2. Completed Milestones

### M0 — Open-source release baseline ✅

- Authless local mode: no sign-in/sign-up/session/profile; gateway serves
  health, organization, investigation, evidence, report, meeting, worker,
  constitution, evolution, communication, and WebSocket routes.
- MIT license, third-party notices, README, `.env.example`, editor config,
  repo hygiene.
- Android debug build has no mock data layer; screens use real API data with
  loading / error / empty states.
- Migration `002_remove_auth.sql` drops account/session/ownership tables for
  databases created before the release.

### M1 — Organization lifecycle ✅

- `org-manager` materializes templates, departments, and workers from a
  template; organization state machine reaches `active`.
- Constitution engine persists mission overrides; DNA profiles, trust ledger,
  memory, knowledge graph, communication, Caspian bridge, meetings, evolution,
  and report engines all run as services and expose their projections.
- Per-organization JetStream streams (`ORG_<org_id>`) with monotonic sequence
  numbers in the `events` table as the source of truth.

### M2 — Durable research pipeline ✅

- `investigation-engine` creates a 5-task plan, moves `planning → researching`,
  and publishes each task to `workers.task.collect_evidence`.
- Python `worker-runtime` consumes the durable `VELIORA_WORKERS` JetStream
  stream, runs the `collect_evidence` handler (prompt composition + LLM +
  optional web search), and publishes results to
  `workers.result.collect_evidence`.
- `HandleTaskResult` validates the database-authoritative task identity,
  ignores duplicate deliveries idempotently, inserts evidence, and advances to
  `analyzing` when every research task in the cycle is `done`, `failed`, or
  `timeout`.
- Plan JSON in the investigation row is mirrored by task-status updates.

### M3 — Peer challenge phase ✅

- `createChallengeTasksTx` assigns one `challenge` task per submitted evidence
  item to the first active worker in a **different department**
  (`d.id <> $2 ORDER BY w.hired_at, w.id LIMIT 1`), so no department reviews
  its own evidence.
- `completeChallengeTaskTx` persists the verdict: a raised challenge inserts an
  `evidence_challenges` row (`resolved_qualified`) and increments
  `challenge_count`; a clean verdict accepts the evidence outright.
- `advanceAfterChallengesTx` moves `analyzing → debating` once every challenge
  task for the cycle is settled.

### M4 — Debate, synthesis, voting, report ✅ (this change set)

- **Debate orchestration.** `createDebateAndTasksTx` opens a `debates` row,
  schedules the observable `meetings` row with an agenda, seats one
  `meeting_participants` row per speaker, and dispatches round 1. A rebuttal
  round follows automatically (`maxDebateRounds = 2`), and each round's task
  payload carries the prior rounds' transcript (`other_turns`) so the second
  round actually answers the first.
- **Round accounting bug fixed.** The open-turn query originally derived
  `max(round_no)` from *unfinished* rows only, so a concluded round looked like
  round zero and the phase re-dispatched opening statements forever. The query
  now separates `count(*) FILTER (WHERE status NOT IN (...))` from
  `max(round_no)` over the whole cycle.
- **Transcript contains only spoken turns.** The placeholder `debate_arguments`
  rows written at debate open (empty `argument_text`) were removed; the
  transcript projection therefore never shows empty entries for speakers who
  never got a turn.
- **Synthesis.** `synthesize` is dispatched to the CEO Office worker with
  `stances` (full debate transcript), `evidence` (accepted claims), and
  `dissent` (opposing/qualified stances plus qualified peer challenges). The
  result is merged into `final_recommendation` and the phase advances to `vote`.
- **Voting.** Every debate speaker gets a `vote` task with the rendered
  recommendation, summary, and dissent. Ballots are weighted by the trust
  ledger and stored with their `cycle_no`; the partial unique index makes a
  re-delivered or re-voted ballot idempotent within a cycle.
- **Confidence check.** Confidence is the *decisive weight share*
  (`yes / (yes + no)`), not the raw sum, so a board with many abstentions
  cannot look more confident than one that split. `investigation.confidence`
  is emitted with the charter threshold, and the debate row records the verdict
  and tally.
- **Confidence loop.** Below the threshold the investigation re-enters
  `researching` for a new cycle (new cycle-scoped research tasks, the previous
  debate and meeting concluded, a fresh debate opened). Capped at
  `maxResearchCycles = 3`; on the final cycle the investigation reports its
  best result with an explicit "max research cycles reached" caveat rather than
  spinning forever.
- **Report.** `board_report` is dispatched to the CEO with the decision,
  evidence, and dissent; `reports` is upserted and the investigation moves to
  `completed`, emitting `investigation.completed` and `investigation.report_ready`.
- **Per-cycle scoping.** Every phase-completion count is scoped to
  `cycle_no`, and evidence/debate/vote lookups are scoped to the tasks of that
  cycle, so a second cycle never re-seats or re-counts the first cycle's work.

### M5 — Operational hardening ✅ (this change set)

- **Durable result consumers.** `SubscribeWorkerResults` now attaches
  `nats.Durable(...)`, so an engine restart resumes from its last acknowledged
  message instead of creating an ephemeral consumer that replays the whole
  retention window.
- **No queue group on results.** Worker results must be applied exactly once
  and in order. A queue group let the durable name stay bound to the first
  registered consumer while a restarted replica's fresh queue consumer replayed
  the same results (observed live as duplicate `debate` redeliveries). Result
  consumers are now single, non-queue push consumers per task type.
- **Timeout sweeper.** The engine runs a 15-second background sweep
  (`SweepTimedOutTasks`) that resolves elapsed `timeout_after` deadlines,
  applies the phase failure/advance policy, and dispatches the follow-up work
  the timeout unblocks. `BackfillTaskTimeouts` gives tasks persisted before
  deadline tracking a deadline on engine start so an upgraded database cannot
  freeze a phase on an un-timed task.
- **Phase-aware failure policy.** A phase that loses *all* of its tasks fails
  the investigation; `challenge` and `debate` fail on majority loss because
  their successors need the qualification and the second voice. A minority loss
  does not sink the phase. Terminal failure releases sibling pending/in-progress
  tasks and cancels the open meeting so nothing is left held by a dead
  investigation.
- **Stream provisioning race.** The Python runtime's `AddStream` raced the Go
  engine's provisioning and crashed the worker on the losing attempt; it now
  retries and treats provisioning as eventually consistent.
- **Report projection.** `report.Service.Get` accepts both the synthesis and
  board-report payload shapes (`summary`/`executive_summary`,
  `dissent`/`dissenting_opinions`) and derives dissent from opposing/qualified
  debate turns only — a "support" turn is not a dissent.

## 3. Architecture As Built

```text
api-gateway ──rpc.investigation.create──▶ investigation-engine
                                              │
                     planning → researching  │ 5 tasks (cycle 1)
                                              ▼
                             workers.task.collect_evidence
                                              │
                                     VELIORA_WORKERS (JetStream, file, 7d)
                                              ▼
                                    Python worker-runtime
                                              ▼
                            workers.result.collect_evidence
                                              ▼
                              investigation-engine HandleTaskResult
                                              ▼
                        last result → analyzing + challenge tasks
                                              ▼
                              workers.task.challenge (different dept)
                                              ▼
                    workers.result.challenge → accepted evidence + challenge rows
                                              ▼
                                         debating
                                              │  debate + meeting + round-1 turns
                                              ▼
                        round-2 turns (payload carries round-1 transcript)
                                              ▼
                                         synthesizing
                                              ▼
                              workers.task.synthesize (stances/evidence/dissent)
                                              ▼
                                         vote (weighted ballots)
                                              ▼
                                  confidence_chk (yes / (yes+no))
                                       │                    │
                       confidence ≥ threshold      confidence < threshold
                       and cycle < 3              → researching (next cycle)
                                              ▼
                                      workers.task.board_report
                                              ▼
                                     completed + report_ready
```

A background sweeper runs every 15 seconds alongside the result consumers:
it resolves elapsed task deadlines, applies the phase failure/advance policy,
and dispatches the follow-up work each timeout unblocks.

Key technical decisions:

- **Subjects are flat and type-scoped** (`workers.task.<type>`,
  `workers.result.<type>`), not org-scoped. Organization and investigation IDs
  travel inside `TaskMessage` / `TaskResult` envelopes.
- **Durable stream:** `VELIORA_WORKERS`, subjects `workers.task.*` and
  `workers.result.*`, file storage, 7-day `MaxAge`, 100k `MaxMsgs`, explicit
  PubAck with `nats.MsgId` deduplication.
- **Separate task/result subject trees** prevent a runtime from consuming its
  own result messages.
- **Result consumers** are single durable push consumers (no queue group) with
  manual ack, `MaxDeliver(10)`, `AckWait(30s)`; invalid envelopes are acked and
  discarded, processing errors NAK for redelivery.
- **Task payloads** are rebuildable: `description`, `deliverables`,
  `evidence_id` / `other_claim`, and the debate transcript are read back from
  the task, evidence, and argument rows.
- **Event sourcing:** each state change appends an `events` row and publishes
  the envelope to the org stream; domain tables (investigations, tasks,
  evidence, evidence_challenges, debate_arguments, votes, reports) are
  projections.

### Database changes

`003_worker_pipeline.sql` (earlier in this change set):

| Statement | Purpose |
|-----------|---------|
| `tasks.task_type TEXT DEFAULT 'collect_evidence'` | Distinguishes research from challenge (and later) work. |
| `evidence.task_id UUID REFERENCES tasks(id)` + unique partial index | Idempotent research result ingestion, one evidence row per task. |
| `task_inv_status_idx (investigation_id, status)` | Phase-completion counting. |
| `tasks.evidence_id UUID REFERENCES evidence(id)` + index | Links follow-up tasks to the evidence they operate on. |

`004_investigation_pipeline.sql` (new in this change set):

| Statement | Purpose |
|-----------|---------|
| `tasks.status` check extended with `timeout` | The sweeper marks stalled work `timeout` instead of leaving it `in_progress` forever. |
| `debate_id`, `round_no`, `cycle_no`, `deliverables` on `tasks` | Round/cycle identity for debate turns and per-cycle phase counting. |
| `debates`, `debate_arguments` | Persisted debate and ordered transcript. |
| `votes.cycle_no` + `one_vote_per_worker_cycle_idx` | One ballot per worker per confidence cycle, idempotent on re-delivery. |
| `reports` upsert key on `investigation_id` | The board report is rewritten, not duplicated, if regenerated. |

> **Upgrade note:** if migration `003` was applied before `tasks.evidence_id`
> existed, the local database is missing that column even though the migration
> is recorded. The fix is idempotent:
> `ALTER TABLE tasks ADD COLUMN IF NOT EXISTS evidence_id UUID REFERENCES evidence(id);`
> Fresh databases get it from `003_worker_pipeline.sql`.

## 4. What Is Left

### 4.1 Next milestone — Dead-letter handling and worker leases

- ⏳ **Dead-letter handling.** Result consumers give up after `MaxDeliver(10)`
  and the message disappears. Add a dead-letter table or stream so an
  unprocessable result is inspectable and replayable.
- ⏳ **Worker heartbeat / lease renewal.** A crashed worker leaves its task
  `in_progress` until the sweeper's deadline elapses; a heartbeat would let the
  engine reclaim capacity sooner and distinguish "slow" from "dead".
- ⏳ **Concurrency and back-pressure.** No per-worker concurrency cap,
  per-department rate limit, or LLM token/cost accounting. The worker runtime
  currently processes one task per delivered message with no throttle.

### 4.2 Following milestones

- ⏳ **Contested evidence loop.** A raised challenge qualifies evidence
  (`resolved_qualified`) and is surfaced in synthesis/report dissent, but there
  is no re-work loop that re-opens research for a disputed claim.
- ⏳ **User interaction route.** `POST /investigations/{id}/interact` is
  documented but not implemented; the user can currently only create and
  cancel investigations.
- ⏳ **Report export.** `?format=pdf|markdown|docx` are documented; only the
  JSON report body is served.
- ⏳ **Android surfaces.** No analysis / challenge / debate / voting screens;
  `InvestigationScreen` renders plan and evidence but not challenge verdicts or
  the debate transcript, though the API now serves both.
- ⏳ **Push notifications** for phase changes are not implemented.

### 4.3 Platform, observability, security

- ⏳ No metrics, tracing, or structured logging pipeline; only per-service log
  files.
- ⏳ No rate limiting or authentication (intentional for authless local mode;
  required before any public deployment).
- ⏳ Knowledge graph remains Postgres adjacency tables; no deep traversal or
  Neo4j backend.
- ⏳ Evolution auto-proposals are not triggered on a cadence.
- ⏳ No event-table partitioning (deferred until >1M events per organization).
- ⏳ Multi-process worker scaling is untested; task concurrency and
  back-pressure controls are not tuned or benchmarked.

### 4.4 Repository / docs hygiene

- ⏳ This change set is **uncommitted**; commit the investigation-pipeline work
  together with migrations `003_worker_pipeline.sql` and
  `004_investigation_pipeline.sql` (both untracked).
- ✅ `docs/25-state-machine.md` transition triggers reconciled in §8 below.
- ✅ `docs/24-api-design.md` documents the challenge/debate/vote routes, the
  worker subjects, and the `TaskMessage` / `TaskResult` schemas.
- ⏳ `docs/19-investigation-engine.md` still describes the debate as owned by a
  standalone debate engine and the vote as a ballot record; the implementation
  dispatches every turn as a worker task. Not yet reconciled.

## 5. Known Issues / Limitations

1. **Local DB drift for migration 003** (see upgrade note in §3). Verified
   fixed in the working database; fresh databases are correct.
2. **Cold-start race (fixed):** `run_backend.sh` previously returned before
   `investigation-engine` finished subscribing, so an immediate E2E could 502.
   It now waits for the gateway, investigation engine, and (when enabled)
   worker-runtime readiness lines.
3. **Scripted mode is deterministic, not autonomous:** with
   `VELIORA_SCRIPTED_LLM=1` the evidence, challenge, debate, synthesis, and
   vote outputs come from fixed fixtures (every vote is `yes`, so scripted runs
   complete in cycle 1). Set `OPENAI_API_KEY` and drop the flag to exercise
   real generation; output will vary, cost tokens, and may legitimately fail the
   confidence threshold and loop.
4. **Challenges always qualify evidence:** a raised challenge increments
   `challenge_count` and stores `resolved_qualified`; there is no re-work loop
   that re-opens research for a disputed claim yet.
5. **No `debate-engine` command exists by design.** The debate engine is a Go
   library (`internal/debate`) plus Python handlers; orchestration comes from
   `investigation-engine`.
6. **A dead worker's result can still arrive after its task timed out.** The
   engine acks it and `HandleTaskResult` treats the already-settled task as a
   duplicate, so the work is lost for that cycle rather than corrupting state.
   This is the safe direction but it does mean a slow worker loses its turn.
7. **E2E polls rather than waits on events.** The golden path polls the
   investigation state every 2 seconds (up to 10 minutes) because the full run
   is ~27 worker tasks, several of which make real DuckDuckGo/Tavily requests.

## 6. Command Reference

```bash
# Infra + migrate
make up
make migrate

# Backend with deterministic workers
VELIORA_START_WORKERS=1 VELIORA_SCRIPTED_LLM=1 bash scripts/run_backend.sh

# Tests
make test                                   # Go + Python
cd backend/go && go test ./... && go vet ./...
cd backend/workers && uv run pytest -q

# Full golden path (research → challenge → debate → synthesis → vote → report)
EXPECT_WORKER_PIPELINE=1 bash scripts/e2e_golden_path.sh

# Android
cd veliora && ./gradlew :app:assembleDebug
```

## 7. Definition of Done for the Next Milestone

A dead-letter/lease milestone is complete when:

1. A result that exhausts `MaxDeliver(10)` lands in a dead-letter table with
   the original envelope and the failure reason, and is inspectable through the
   API or a CLI helper.
2. A dead-lettered result can be replayed after the underlying fault is fixed.
3. Worker heartbeats let the engine distinguish a slow worker from a dead one
   and reclaim capacity without waiting for the full task deadline.
4. `go test ./...`, `uv run pytest -q`, and
   `EXPECT_WORKER_PIPELINE=1 bash scripts/e2e_golden_path.sh` still pass.
5. README, `docs/32-open-source-release.md`, and this document are updated.

---

## Appendix A — Change Inventory (current working tree)

Status: **uncommitted**, on top of `9bd56b6`.

| File | What changed |
|------|--------------|
| `backend/go/internal/investigation/service.go` | Debate orchestration (meeting + 2 rounds, prior-transcript payloads, round-count fix, placeholder removal); synthesis/vote/report payloads (stances, evidence, dissent); confidence check with decisive-weight confidence and the capped confidence loop; per-cycle scoping for every phase-completion count; phase-aware failure policy with sibling release; timeout sweep + deadline backfill; challenges/votes projections; `min(uuid)` fix in the report task's CEO lookup. |
| `backend/go/internal/investigation/service_test.go` | Added full-pipeline-to-completed, confidence-cycle-loop, max-cycle-cap, timeout-sweep, all-research-lost, and minority-challenge-loss tests, plus shared `dispatchAndRun`/`scriptedResult` helpers. |
| `backend/go/internal/report/service.go` | `Get` accepts synthesis and report payload shapes, derives dissent from opposing/qualified turns only, and never lets a worker-reported confidence overwrite the vote tally. |
| `backend/go/internal/report/service_test.go` | *(new)* Guards `409 investigation_not_complete` and asserts a rendered report with recommendation, dissent, evidence, and transcript. |
| `backend/go/internal/gateway/server.go` | New `GET .../investigations/{invID}/challenges` and `.../votes` routes. |
| `backend/go/internal/shared/bus/bus.go` | `SubscribeWorkerResults` attaches `nats.Durable(...)` and supports a single-consumer (non-queue) mode for exactly-once result application. |
| `backend/go/cmd/investigation-engine/main.go` | `rpc.investigation.challenges` / `rpc.investigation.votes` responders; result subscribers moved off queue groups; 15-second background timeout sweeper with deadline backfill. |
| `backend/go/migrations/postgres/004_investigation_pipeline.sql` *(untracked)* | `timeout` task state, debate/task round+cycle columns, `debates`/`debate_arguments`, `votes.cycle_no` with per-cycle unique index, `reports` upsert key. |
| `backend/workers/src/veliora_workers/runtime.py` | Stream provisioning retried instead of crashing on the provisioning race with the Go engine. |
| `backend/workers/src/veliora_workers/tasks.py` | `synthesize` takes evidence and dissent and is instructed to preserve dissent in the result. |
| `backend/workers/src/veliora_workers/llm.py` | Scripted synthesis fixture carries `dissent`; board-report fixture carries `dissenting_opinions`, `supporting_evidence`, `full_discussion_summary`. |
| `backend/workers/tests/test_workers.py` | Added rebuttal-transcript, synthesis-dissent, and runtime payload tests; worker suite is now 16 tests. |
| `scripts/e2e_golden_path.sh` | `EXPECT_WORKER_PIPELINE=1` now waits for `completed` and asserts 5 evidence, 5 challenges, a concluded 10-turn debate, 5 ballots, and a served report recommendation. |
| `README.md` | Status paragraph now describes the full investigation pipeline through `completed`. |
| `docs/32-open-source-release.md` | Release limitation updated. |
| `docs/INDEX.md` | Doc 33 registered. |
