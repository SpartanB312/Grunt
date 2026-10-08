# Performance fixes and validation

Baseline: `830295b04c5f1565159fba33062fdc842d921e08` on `grunt3`. This change implements the [initial audit](<PERFORMANCE_AUDIT.md>). Regression tests cover algorithmic scaling, allocation shape, concurrency contracts and executable bytecode; no workload-independent speedup is claimed.

## Coverage

| Audit findings | Implementation | Deliberate limits |
| --- | --- | --- |
| 01 | Dynamic ASM frames and computed maxs in Flow/native, restoring original MethodNode state. | Verification is retained; stale declared maxs are not trusted. |
| 02, 09 | Early unique hierarchy closure, primitive field membership, shared components and sparse synthetic-method unions. | Complete transitive closure still has quadratic worst-case output size. Legacy order/RNG is tested. |
| 03–05, 07 | Fixed-count resource workers, byte-budgeted dump, streaming resources, lazy full library bodies, structural hierarchy snapshots and owned ZIPFS lifetime. | Input ASTs remain resident. The default 64 MiB budget excludes at-most-P ASM working arrays; an oversized class is admitted exclusively. |
| 06 | Cost-aware batches and stealing small remaining tails. | Executing classes remain indivisible to preserve fused passes and worker-local state. |
| 08 | Stream resources through a reusable ZIP writer; nested backend JARs use DEFLATED level 0. | Existing compressionLevel default 9 is preserved. Compression/size choices remain configurable. |
| 10–11, 13 | Import-local instruction/block indices and operation-local CFG/port/region indices. | No invalidation-blind global mutable-graph cache. |
| 12 | Dense indices, BitSets and RPO/worklist dominance. | Exception edges and the existing unreachable-SCC policy are preserved. |
| 14–16 | Sparse regional use repair, batched layout, reusable parallel-copy scratch locals and carrier maps. | Truly global live-outs still require global repair; swap/cycle copy semantics are retained. |
| 17 | Prepared native lowering/emission reused for single/split output; lazy diagnostic monolith. | Config/commit-kind mismatch uses the validated fallback. |
| 18 | Bounded JNI batching for a proved fresh, unescaped int-array fill pattern. | Shared arrays, general sum/copy loops, handlers and callbacks retain the original per-element fallback. Not universal loop batching. |
| 19 | Handle-identity reference sets and pruning cleared weak member-cache entries. | JNI ownership checks and pending-exception handling remain. |
| 20–21 | GLSL token/call/patch indices, source-version parse reuse and accepted-patch expansion accounting. | Cross-file decisions are recomputed; deleting a helper no longer bypasses the expansion budget. |
| 22–23 | Bounded SPECK companions, opt-in literal helper reuse, lazy ABE setup and bounded immutable public-parameter/coefficient caches. | Per-occurrence protection remains default. CP-ABE policies, curves, pairings and AES-GCM are not weakened; mutable Pairing/Element instances are not shared. |
| 24–26 | Bounded batched UI log tail with full file sink, schema/catalog caches, detached snapshots, FIFO IO and save-intent/content guards. | UI mutation stays on its dispatcher. Unsupported atomic-file replacement fails rather than silently overwriting. |
| 27–30 | Nonterminal single-flight polling, atomic Redis claim/lease/ack, isolated attempts, supervised workers, admission budgets/quotas and opt-in retention. | Process isolation remains. Budgets are not OS resource limits or Redis Cluster/multi-host guarantees. |
| 31 | Buffered thread-safe logging, periodic/error flush, explicit close before packaging. | Old Kotlin constructor ABI is retained. Forced process termination can lose an unflushed tail. |
| 32 | Central-directory plugin indexing, closed streams, bounded index parsing and streaming index JSON. | Index format/version and public JSON helper remain; main does not gain automatic .gi loading. |

Additional fixes cover indexed ReferenceObfuscate anchors without changing RNG draws, immutable inliner metadata, SSA exception-range lookup, atomic Redis updates, streaming config BOM handling, eager no-op ABE setup and optional native object caching.

## Compatibility and operational notes

- [WorkResources](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/WorkResources.kt>) and [Grunteon](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/Grunteon.kt>) are closeable; CLI/UI/backend close their resources. Full-body lookup after close fails explicitly, while cached input/metadata collections remain inspectable.
- Library collections contain marked metadata-only nodes until requested. Plugins needing method bodies must use `getClassNode`; `getClassMetadata` avoids hydration. Indexed phantom classes without a byte source remain phantom. Concurrent hydration cannot return an obsolete header.
- File-backed [ResourceOutput](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/resource/ObfuscationIO.kt>) implementations should expose `targetPath()`. PathResourceOutput does so; directory-input streaming excludes the output and its same-file aliases. Self-copy tests use a capped writer, not an unbounded disk fill.
- Directory entries previously omitted by Path.walk defaults are now emitted. Duplicate resource names retain last-entry lookup semantics. Existing dirs-only `corruptCRC32` behavior/RNG consumption is intentionally preserved.
- The [JVM lookup helper](<grunt-main/src/main/java/net/spartanb312/grunteon/obfuscator/util/ImplLookupGetter.java>) locates the platform JVM library under java.home, fixing the existing Linux `Cannot open library: jvm` failure exposed by dump tests.
- [Universal JAR packaging](<grunt-ui/build.gradle.kts>) explicitly retains runtime configuration producer dependencies; correctness no longer depends on task order after conversion to zipTree files. See [Gradle implicit-dependency validation](<https://docs.gradle.org/9.5.0/userguide/validation_problems.html#implicit_dependency>).
- SPECK `reuseHelpers=false` retains per-occurrence diversity. Opt-in reuse keys include type and raw bits, including signed zero and NaN payloads.
- CLI elapsed time now includes initialization/resource loading, not only the engine run.

### Native compilation cache

```text
-Dgrunteon.native.cache.enabled=true
-Dgrunteon.native.cache.toolchainId=<immutable-full-toolchain-content-id>
```

[NativeCompileCache](<grunt-main/src/main/kotlin/net/spartanb312/grunteon/obfuscator/process/nativecode/NativeCompileCache.kt>) hashes exact fresh preprocessed input, source, driver bytes/version/target, effective flags, environment and working directory. Misses compile that exact snapshot. Entries are checksummed/atomic; linking always runs. Unsupported flags, external-input pragmas, PCH/modules and MSVC use an uncached fallback.

The supplied ID must cover toolchain helpers, assembler, libraries, specs, implicit configuration and wrapper state. This is **not** an automatically complete toolchain fingerprint. Inputs/toolchain must not change concurrently. Cache data is trusted local work-directory data with no automatic eviction; single-TU compile+link is not cached.

### Backend rollout

[Backend settings](<grunt-backend/src/main/resources/application.yml>) preserve the previous 4 GiB worker heap and two requested workers. Effective concurrency uses CPU sharing and a default 9216 MiB aggregate reservation including estimated native/other overhead. Legacy heap/processor options are accounted for, not silently overridden. Small hosts must configure smaller budgets.

New defaults: 64 admitted jobs, 1 MiB config upload, 8 GiB retained-upload accounting and a 1 GiB free-disk admission floor. They do not cap generated artifacts/native intermediates; use filesystem/cgroup limits for hard enforcement. Terminal retention is disabled by default. Unconfirmed process termination quarantines a slot instead of admitting an overlapping worker.

Stop old workers before rollout. Legacy RUNNING records without leases need deliberate reconciliation; historical terminal records are not automatically indexed/deleted. The Lua layout targets standalone Redis, not Redis Cluster. Multiple hosts need an identical shared job root with reliable locks and coordinated host budgets. Isolated checks do not replace production crash/load tests.

## Validation

All seven Gradle suites passed in the final run (main excludes the unavailable external acceptance fixture suite):

| Module | Tests | Failures/errors | Reported skips |
| --- | ---: | ---: | ---: |
| grunt-main | 283 | 0 | 1 |
| grunt-ir | 77 | 0 | 0 |
| grunt-glsl | 36 | 0 | 0 |
| grunt-index | 7 | 0 | 0 |
| grunt-backend | 20 | 0 | 0 |
| grunt-ui | 45 | 0 | 0 |
| grunt-yapyap | 16 | 0 | 1 |

Universal desktop JAR (all four native runtime targets), backend boot JAR, GLSL and Yapyap JAR tasks also succeeded. The universal JAR is approximately 112 MiB. Across the suites, 484 tests were discovered: 482 passed, 2 reported skips, no failures/errors.

### Limitations, not hidden as passes

- Gradle Wrapper 9.5.0, Kotlin 2.4.0, JDK 25 and separately installed JDK 8 were used; tracked dependency versions/toolchains did not change.
- Download of `net.spartanb312:genesis-kotlin:1.0.0` was blocked by maven.noblesix.net's expired TLS certificate. TLS verification was **not** disabled. Local checks used a temporary JAR compiled from the [official Genesis source](<https://github.com/SpartanB312/Genesis/tree/9f377a1>) via an ignored init script. This checks that source, not byte-for-byte identity with the unavailable published artifact.
- Kotlin 2.4.0 parallel JVM test codegen hit an internal ArrayList race. The local init script used `-Xbackend-threads=1` for test compilation. Production compiler/dependency settings remain unchanged.
- Main selection excludes only `ObftestAtConfigTest`, whose seven external acceptance configs are absent. No fake fixtures or source-level disabling were added. One existing ignored method-renamer test remains skipped.
- Native PBC is unavailable (one explicit assumption skip); the opt-in Zig smoke returns early without a configured Zig executable. GNU/JNI cache validation was enabled with `GRUNTEON_GNU_EXECUTABLE=/usr/bin/g++` and performed real compilation, cache-hit checks and JNI execution.
- Desktop startup was attempted with Xvfb and isolated settings, but the installed JDK 25 lacks libawt_xawt.so and raised HeadlessException. Packaging and 45 UI tests passed; actual desktop startup/visual behavior is not claimed as verified.
- No representative JFR/JMH throughput or end-to-end percentage speedup is claimed.

### Additional checks

- [Backend polling tests](<grunt-backend/src/test/js/app.test.cjs>): Node runner, 8 tests passed.
- [Lua model tests](<grunt-backend/src/test/lua/jobs_test.lua>): 49 assertions without Redis.
- [Real Redis lifecycle smoke](<grunt-backend/src/test/python/redis_lifecycle_smoke.py>): production Lua on private UNIX-socket Redis 8.0.2 with persistence/TCP disabled, 35 checks including concurrent admission, expired-claim fencing and retained-byte accounting. Only a random test namespace is removed; never FLUSHDB/FLUSHALL. The test server was stopped afterwards.
- [Bootstrap regression harness](<grunt-bootstrap/src/test/java/net/spartanb312/everett/bootstrap/ExternalClassLoaderRegressionTest.java>): 3 checks passed on JDK 25. This standalone main is not automatically discovered by Gradle test.

Regression coverage includes executable JVM/JNI checks, deterministic old/new graph/RNG comparisons, maxLocals/capacity bounds, IO cancellation, constructor ABI, resource ownership, lazy hydration races and delayed UI-save interleavings. Native/Yapyap license notices are preserved.
