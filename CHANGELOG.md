# Changelog

All notable changes to this project will be documented in this file. Please note that this changelog was added in version 4.0 so documentation on versions prior to that are incomplete.

## [5.1.0] - 2026-07-23

### Added

- `'query timeout'` config option (seconds). Applies `Statement.setQueryTimeout` to every statement and defaults the `'query timeout mechanism'` JDBC property to `'cancel'`, so slow or lock-blocked statements are cancelled server-side instead of hanging indefinitely. Pair with the `'socket timeout'` JDBC property (milliseconds) as an unreachable-host backstop.
- `connectionLimit` config option is now honored via `setMaxConnections` (previously accepted but silently ignored, leaving the pool unbounded). When the limit is reached, acquiring a connection throws rather than queuing.
- `java/build-jar.sh` so the committed `jt400wrap.jar` is reproducible from source.

### Fixed

- Pooled connections are no longer leaked ("zombies") when cleanup fails on a broken connection — e.g. after a socket timeout — and cleanup failures no longer mask the original error: statement close failures in `finally` blocks, `Transaction.end()` autocommit-reset failures, and pool-return failures are now logged and contained.
- `queryAsStream` no longer leaks its connection when the query fails to execute.
- `StatementWrap`/`ResultStream` close is now idempotent (previously `asArray()` could return the same connection to the pool twice).

## [5.0.2] - 2024-05-30

We had to revert form java-bridge back to the java dependency because of deadlock issues.

## [5.0.0] - 2024-04-18

### Changed

- The java dependency was replaced with java-bridge.
- main was moved from dist/lib/jt400.js to dist/index.js
- Q promises replaced with native promises
- The full java object is no longer exposed when reading a message file

## [4.3.0] - 2021-08-01

### Added

- Options parameter added to the query function. Trimming values is now configurable, but still defaults to true. Issue [#22](https://github.com/tryggingamidstodin/node-jt400/issues/22).

## [4.2.0] - 2021-07-30

### Added

- Support for BLOB column (not only clob). See [#66](https://github.com/tryggingamidstodin/node-jt400/pull/66)

## [4.1.0] - 2021-03-12

### Added

- asIterable function added for async iterable support. See [#60](https://github.com/tryggingamidstodin/node-jt400/pull/60).
- new types and interfaces added.

## [4.0.0] - 2019-10-15

### Added

- All errors wrapped in [oops-error](https://github.com/tryggingamidstodin/oops-error).
- Created CHANGELOG.md.

### Changed

- Removed System.err logs in Java code.
- deprecated message for .pgm changed

## [3.1.3] - 2019-07-10

### Added

- ccsid option for program calls. See [#39](https://github.com/tryggingamidstodin/node-jt400/pull/39).

### Changed

- ccsid defaults to ccsid from as400 system.

## [3.0.0] - 2018-11-30

### Added

- defineProgram function.
- Default timeout on program calls set to 3 sec to avoid programs halting.
- Optional timeout parameter set to program calls.

### Changed

- Function .pgm was deprecated in favour of defineProgram.

## [2.0.0] - 2018-10-8

### Added

### Changed

- Only supports node verison 8 and higher.

## [1.5.4] - 2017-10-24

### Added

- Support for CLOB data type
