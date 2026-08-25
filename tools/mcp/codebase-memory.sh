#!/usr/bin/env bash
set -euo pipefail

altimeter_repository_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/../.." && pwd)"
altimeter_memory_binary="${CODEBASE_MEMORY_MCP_BIN:-}"

if [[ -z "${altimeter_memory_binary}" ]]; then
    altimeter_memory_binary="$(command -v codebase-memory-mcp || true)"
fi

if [[ -z "${altimeter_memory_binary}" || ! -x "${altimeter_memory_binary}" ]]; then
    printf '%s\n' \
        'codebase-memory-mcp is not installed. Put it on PATH or set CODEBASE_MEMORY_MCP_BIN.' >&2
    exit 1
fi

export CBM_CACHE_DIR="${altimeter_repository_root}/.codebase-memory"
mkdir -p "${CBM_CACHE_DIR}"

exec "${altimeter_memory_binary}" --ui=true --port=9750
