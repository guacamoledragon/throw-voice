# CI Speed Optimization Log

## Baseline Analysis (2026-03-12)

### Pipeline #970 (deprecate-prefix/phase-0) - 13m 46s total
| Job          | Stage  | Duration | Queued |
|--------------|--------|----------|--------|
| dependencies | .pre   | 746.8s   | 2.9s   |
| test         | test   | 57.7s    | 3.2s   |
| coverage     | deploy | 10.5s    | 2.1s   |

### Pipeline #971 (ci-speed) - 2m 24s total (cache hit from prior build on same runner)
| Job          | Stage  | Duration | Queued |
|--------------|--------|----------|--------|
| dependencies | .pre   | 81.8s    | 1.0s   |
| test         | test   | 52.4s    | 2.0s   |
| coverage     | deploy | 10.4s    | 1.1s   |

### Root Causes Identified

1. **Cache key is branch-scoped (`maven-$CI_COMMIT_REF_SLUG`)** - Every new branch
   starts with a completely cold Maven cache. Since the dependencies don't change
   between branches (only `pom.xml` matters), this forces a full 12-minute download
   on every new branch's first pipeline.

2. **`dependencies` job is a separate stage** - Even with a cache hit, the deps job
   downloads everything, uploads to B2 (~5s zip + ~8s upload), then `test` must
   download it (~10-15s). This adds ~25s of pure cache I/O overhead.

3. **No Maven parallelism** - Maven runs single-threaded by default. Adding `-T1C`
   (1 thread per core) can speed up multi-module builds (though this is single-module,
   the plugin resolution can still benefit).

4. **`go-offline-maven-plugin:resolve-dependencies` downloads plugin deps too** - 
   This plugin resolves ALL transitive deps including build plugins, which is why it
   takes so long on a cold cache. The kotlin-compiler-embeddable alone is 58MB.

---

## Optimization Plan

### Phase 1: Cache Key Strategy (biggest impact)
- Change cache key from `maven-$CI_COMMIT_REF_SLUG` to hash of `pom.xml`
- Use `cache:key:files` with `pom.xml` so all branches share the same cache
- Add fallback key so branches can reuse the default/master cache
- This alone should eliminate the 12-minute cold-start on new branches

### Phase 2: Merge deps + test into single job
- The separate `dependencies` job exists solely to warm the cache
- If we have a good cache key strategy, we can merge deps resolution into
  the test job itself, eliminating one full job + cache upload/download cycle
- Keep `policy: pull-push` on test so it both uses and updates the cache

### Phase 3: Maven Tuning
- Add `-T1C` for thread-per-core parallelism
- Add `--fail-at-end` for better error reporting in parallel builds

---

## Changes Log

### Iteration 1 - Initial optimization (pending)
- TODO: Apply changes and push
