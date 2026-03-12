# CI Speed Optimization Log

## Baseline Analysis (2026-03-12)

### Pipeline #970 (deprecate-prefix/phase-0) - 13m 46s total
| Job          | Stage  | Duration | Queued |
|--------------|--------|----------|--------|
| dependencies | .pre   | 746.8s   | 2.9s   |
| test         | test   | 57.7s    | 3.2s   |
| coverage     | deploy | 10.5s    | 2.1s   |

**Effective wall-clock: ~13m 46s** (deps runs first, test blocked until deps finishes)

### Pipeline #971 (ci-speed, initial push) - 2m 24s total
| Job          | Stage  | Duration | Queued |
|--------------|--------|----------|--------|
| dependencies | .pre   | 81.8s    | 1.0s   |
| test         | test   | 52.4s    | 2.0s   |
| coverage     | deploy | 10.4s    | 1.1s   |

This was fast because the runner's Docker layer cache had the Maven image warm,
and the Maven deps were already in the runner's local volume from recent builds.
But the B2 cache was per-branch, so a fresh branch would always get a cold start.

### Root Causes Identified

1. **Cache key is branch-scoped (`maven-$CI_COMMIT_REF_SLUG`)** - Every new branch
   starts with a completely cold Maven cache. Since the dependencies don't change
   between branches (only `pom.xml` matters), this forces a full 12-minute download
   on every new branch's first pipeline.

2. **`dependencies` job is a separate stage** - Even with a cache hit, the deps job
   downloads everything, uploads to B2 (~5s zip + ~8s upload), then `test` must
   download it (~10-15s). This adds ~25s of pure cache I/O overhead per pipeline.

3. **No Maven parallelism** - Maven runs single-threaded by default. Adding `-T1C`
   (1 thread per core) can speed up plugin resolution and compilation.

4. **`go-offline-maven-plugin:resolve-dependencies` downloads plugin deps too** -
   This plugin resolves ALL transitive deps including build plugins, which is why it
   takes so long on a cold cache. The kotlin-compiler-embeddable alone is 58MB.

---

## Changes Log

### Iteration 1 - Commit c78c0c3 (2026-03-12)

**Changes applied:**
1. Changed cache key from `maven-$CI_COMMIT_REF_SLUG` to `cache:key:files: [pom.xml]`
   with prefix `maven`. All branches now share the same Maven cache as long as pom.xml
   hasn't changed.
2. Eliminated the separate `dependencies` job entirely. The `test` job now uses
   default `pull-push` cache policy -- it restores the cache, runs deps + compile +
   test in one shot, then saves the cache back.
3. Added `-T1C` (1 thread per core) to the Maven invocation for parallelism.

**Pipeline #972 (cold cache, first run with new key) - ~1m 51s**
| Job      | Stage  | Duration | Notes |
|----------|--------|----------|-------|
| test     | test   | 100.9s   | Cold cache (new pom.xml hash key), downloaded all deps from Maven Central |
| coverage | deploy | 10.4s    | Same as baseline |

Maven time: 1m 16s (Wall Clock). Cache uploaded to B2 after success.

**Pipeline #973 (warm cache) - ~1m 3s**
| Job      | Stage  | Duration | Notes |
|----------|--------|----------|-------|
| test     | test   | 52.1s    | Warm cache, "cache.zip is up to date" |
| coverage | deploy | 10.9s    | Same as baseline |

Maven time: 36.9s (Wall Clock). Cache detected as up-to-date, skipped re-upload.

### Results Summary

| Scenario               | Before         | After    | Improvement |
|------------------------|----------------|----------|-------------|
| Cold cache (new branch)| 13m 46s        | 1m 51s   | **7.4x faster** |
| Warm cache             | 2m 24s         | 1m 3s    | **2.3x faster** |
| Deps job overhead      | 81-747s        | 0s       | **Eliminated** |

The cold-cache improvement is the most impactful: any new branch now takes ~2 minutes
instead of ~14 minutes, because all branches share the same pom.xml-keyed cache.

### Architecture Change

**Before:**
```
.pre stage:  dependencies (resolve-deps, push cache to B2)  ~81-747s
test stage:  test (pull cache from B2, compile+test)         ~52-58s
deploy:      coverage                                        ~10s
```

**After:**
```
test stage:  test (pull-push cache, compile+test in one)     ~52-101s
deploy:      coverage                                        ~10s
```

The separate `dependencies` job existed to front-load dependency resolution.
But with content-addressed caching (pom.xml hash), this is unnecessary -- the
test job itself maintains the cache, and it's shared across all branches.
