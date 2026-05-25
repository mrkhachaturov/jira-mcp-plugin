#!/usr/bin/env bash
#
# Sync curated subset of the MCP documentation repo
# (github.com/modelcontextprotocol/modelcontextprotocol) into wiki/mcp-docs/
# for local Miyo indexing.
#
# Layout: preserves upstream directory structure verbatim for the paths we
# care about; drops paths that aren't relevant to building MCP servers
# (community/governance, server registry submission docs, etc.). `.mdx` is
# renamed to `.md` so Miyo's markdown chunker picks them up — JSX tags inside
# survive as literal text (slightly noisy but harmless).
#
# Always sparse-clones into /tmp (never uses .upstream/ — that's reference-only
# and may be stale).
#
# Usage: just wiki-sync           (preferred)
#        bash scripts/sync-mcp-docs.sh
#
# Env vars (with defaults):
#   MCP_REPO_URL   — upstream git repo (default: official MCP repo)
#   MCP_REPO_REF   — branch/tag/sha to checkout (default: main)
#   MCP_TMP_DIR    — staging clone location (default: /tmp/mcp-docs-sync)
#   WIKI_TARGET    — destination dir relative to repo root (default: wiki/mcp-docs)

set -euo pipefail

MCP_REPO_URL="${MCP_REPO_URL:-https://github.com/modelcontextprotocol/modelcontextprotocol.git}"
MCP_REPO_REF="${MCP_REPO_REF:-main}"
MCP_TMP_DIR="${MCP_TMP_DIR:-/tmp/mcp-docs-sync}"

REPO_ROOT="$(cd "$(dirname "$0")/.." && pwd)"
WIKI_TARGET="${WIKI_TARGET:-wiki/mcp-docs}"
DEST="$REPO_ROOT/$WIKI_TARGET"

echo "[sync-mcp-docs] target: $DEST"
echo "[sync-mcp-docs] source: $MCP_REPO_URL @ $MCP_REPO_REF"

# Paths we want from upstream — only documentation directly useful for
# building MCP servers / Apps. Skipped (intentionally):
#   docs/community/     governance, charter, antitrust
#   docs/development/   roadmap only
#   docs/registry/      MCP server registry submission process
#   docs/seps/          duplicate of top-level seps/ (sans README/TEMPLATE)
#   docs/snippets/      Hugo include shard
#   blog/archetypes/    Hugo scaffold
#   blog/content/*.md   _index/search/archives (Hugo nav, no content)
SPARSE_PATHS=(
  docs/docs
  docs/specification
  docs/extensions
  seps
  schema
  blog/content/posts
)

# Directories we then rsync (with .md/.mdx filter), preserving structure.
# Each maps "<upstream-relpath>" → identical "<dest-relpath>" under $DEST.
SYNC_DIRS=(
  "docs/docs"
  "docs/specification"
  "docs/extensions"
  "seps"
  "schema"
  "blog/content/posts"
)

# Top-level docs/ files we want (not in any subdir we sync).
SYNC_FILES=(
  "docs/clients.mdx"
  "docs/examples.mdx"
)

# 1. Sparse clone / refresh — pulls only the paths above (~few MB vs full ~70 MB).
if [ -d "$MCP_TMP_DIR/.git" ]; then
  echo "[sync-mcp-docs] refreshing existing sparse clone at $MCP_TMP_DIR"
  git -C "$MCP_TMP_DIR" sparse-checkout set "${SPARSE_PATHS[@]}" >/dev/null
  git -C "$MCP_TMP_DIR" fetch --quiet --depth 1 origin "$MCP_REPO_REF"
  git -C "$MCP_TMP_DIR" reset --quiet --hard "origin/$MCP_REPO_REF"
else
  echo "[sync-mcp-docs] fresh sparse clone into $MCP_TMP_DIR (selected paths only)"
  rm -rf "$MCP_TMP_DIR"
  git clone --quiet \
    --no-checkout \
    --depth 1 \
    --filter=blob:none \
    --branch "$MCP_REPO_REF" \
    "$MCP_REPO_URL" "$MCP_TMP_DIR"
  git -C "$MCP_TMP_DIR" sparse-checkout init --cone >/dev/null
  git -C "$MCP_TMP_DIR" sparse-checkout set "${SPARSE_PATHS[@]}" >/dev/null
  git -C "$MCP_TMP_DIR" checkout --quiet "$MCP_REPO_REF"
fi

UPSTREAM_SHA="$(git -C "$MCP_TMP_DIR" rev-parse --short HEAD)"
UPSTREAM_DATE="$(git -C "$MCP_TMP_DIR" log -1 --format=%ci HEAD)"

# 2. Wipe old content. Safe — everything below DEST is gitignored.
mkdir -p "$DEST"
find "$DEST" -mindepth 1 -delete

# 3. Mirror each curated path preserving structure. rsync filter keeps only
#    .md/.mdx files.
SYNCED=0
for sub in "${SYNC_DIRS[@]}"; do
  src="$MCP_TMP_DIR/$sub"
  if [ ! -d "$src" ]; then
    echo "[sync-mcp-docs]   $sub/: missing in upstream, skip"
    continue
  fi
  dest_dir="$DEST/$sub"
  mkdir -p "$dest_dir"
  rsync -a \
    --include='*/' \
    --include='*.md' \
    --include='*.mdx' \
    --exclude='*' \
    "$src/" "$dest_dir/"
  count=$(find "$dest_dir" -type f \( -name '*.md' -o -name '*.mdx' \) | wc -l | tr -d ' ')
  if [ "$count" -eq 0 ]; then
    echo "[sync-mcp-docs]   $sub/: no markdown files, skip"
    rm -rf "$dest_dir"
  else
    echo "[sync-mcp-docs]   $sub/: $count files"
    SYNCED=$((SYNCED + count))
  fi
done

# 4. Top-level docs/ files (clients, examples).
for f in "${SYNC_FILES[@]}"; do
  src="$MCP_TMP_DIR/$f"
  if [ -f "$src" ]; then
    dest_file="$DEST/$f"
    mkdir -p "$(dirname "$dest_file")"
    cp "$src" "$dest_file"
    SYNCED=$((SYNCED + 1))
    echo "[sync-mcp-docs]   $f: copied"
  else
    echo "[sync-mcp-docs]   $f: missing in upstream, skip"
  fi
done

# 5. Rename .mdx → .md so Miyo indexes them. JSX tags inside remain literal.
RENAMED=0
while IFS= read -r -d '' f; do
  mv "$f" "${f%.mdx}.md"
  RENAMED=$((RENAMED + 1))
done < <(find "$DEST" -type f -name '*.mdx' -print0)
echo "[sync-mcp-docs] renamed .mdx → .md: $RENAMED files"

# 6. Drop any empty dirs left after filtering.
find "$DEST" -mindepth 1 -type d -empty -delete

echo "[sync-mcp-docs] DONE — $SYNCED files at $DEST from $UPSTREAM_SHA ($UPSTREAM_DATE)"
echo "[sync-mcp-docs] reindex via Miyo if your index is not auto-watching."
