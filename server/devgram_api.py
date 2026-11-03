#!/usr/bin/env python3
"""
DevGram Beta API — api.devgram.space

Раздаёт бета-APK ТОЛЬКО поддержавшим (значок ACCESS_SUPPORTER) по персональному токену.
Токен клиент получает автоматически (в фоне) через @officialdevgram_bot — бот видит реальный
telegram-id, кладёт hash(token) в Firebase /beta_tokens/{uid}. Здесь мы этот hash сверяем и
дополнительно перепроверяем значок в /badges/{uid} (на случай отзыва).

Эндпоинты:
  GET /api/beta/latest                      — манифест беты {versionCode, versionName, changelog, size, sha256} (публично)
  GET /api/beta/download?uid=..&token=..     — APK, если токен валиден и значок supporter; иначе 403
  GET /api/plugins/block-status?id=..&sha256=.. — серверный запрет установки плагина
  POST /api/plugins/block                   — блокировка/разблокировка для ACCESS_DEVELOPER

Файлы (кладёт бот при публикации):
  /home/beta/manifest.json                  — {versionCode, versionName, changelog, sha256, size, publishedAt}
  /home/beta/DevGram-beta.apk               — сам APK (приватно, напрямую не раздаётся)

Firebase-доступ — сервис-аккаунт (тот же, что у modbot), OAuth JWT.
Блок-лист плагинов хранится отдельно в серверной SQLite-БД и в Firebase не зеркалится.
"""
import hashlib
import json
import os
import re
import sqlite3
import time
import urllib.parse
import urllib.request

import jwt
from flask import Flask, jsonify, request, send_file, abort

RTDB = "https://devgram-d03e4-default-rtdb.europe-west1.firebasedatabase.app"
SA_PATH = os.environ.get("BETA_SA", "/home/devgram-d03e4-firebase-adminsdk-fbsvc-c736e82d67.json")
BETA_DIR = os.environ.get("BETA_DIR", "/home/beta")
APK_PATH = os.path.join(BETA_DIR, "DevGram-beta.apk")
MANIFEST_PATH = os.path.join(BETA_DIR, "manifest.json")
HISTORY_PATH = os.path.join(BETA_DIR, "history.json")
PLUGIN_BLOCKS_DB = os.environ.get("PLUGIN_BLOCKS_DB", "/home/devgram/api/plugin_blocks.db")
BADGES_PATH = os.environ.get("BADGES_PATH", "/home/devgram/badges/badges.json")

ACCESS_SUPPORTER = 1
ACCESS_DEVELOPER = 1 << 1
EMOJI_TEAM = 5413368748289598440
TEAM_HARDCODED = {7101191373}
PLUGIN_ID_RE = re.compile(r"^[A-Za-z0-9][A-Za-z0-9._-]{0,63}$")
SHA256_RE = re.compile(r"^[0-9a-f]{64}$")

app = Flask(__name__)


def _blocks_db():
    db = sqlite3.connect(PLUGIN_BLOCKS_DB, timeout=15)
    db.row_factory = sqlite3.Row
    db.execute("PRAGMA busy_timeout=15000")
    return db


def _init_blocks_db():
    os.makedirs(os.path.dirname(PLUGIN_BLOCKS_DB), exist_ok=True)
    with _blocks_db() as db:
        db.execute("""
            CREATE TABLE IF NOT EXISTS plugin_blocks (
                block_key TEXT PRIMARY KEY,
                plugin_id TEXT NOT NULL DEFAULT '',
                sha256 TEXT NOT NULL DEFAULT '',
                structure TEXT NOT NULL DEFAULT '',
                simhash TEXT NOT NULL DEFAULT '',
                feature_count INTEGER NOT NULL DEFAULT 0,
                name TEXT NOT NULL DEFAULT '',
                reason TEXT NOT NULL DEFAULT '',
                blocked_by INTEGER NOT NULL,
                blocked_at INTEGER NOT NULL
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS idx_plugin_blocks_id ON plugin_blocks(plugin_id)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_plugin_blocks_sha ON plugin_blocks(sha256)")
        columns = {row[1] for row in db.execute("PRAGMA table_info(plugin_blocks)")}
        for name, definition in (
            ("structure", "TEXT NOT NULL DEFAULT ''"),
            ("simhash", "TEXT NOT NULL DEFAULT ''"),
            ("feature_count", "INTEGER NOT NULL DEFAULT 0"),
        ):
            if name not in columns:
                try:
                    db.execute("ALTER TABLE plugin_blocks ADD COLUMN %s %s" % (name, definition))
                except sqlite3.OperationalError as error:
                    # Несколько gunicorn-worker'ов могут одновременно увидеть старую схему.
                    if "duplicate column name" not in str(error).lower():
                        raise
        db.execute("CREATE INDEX IF NOT EXISTS idx_plugin_blocks_structure ON plugin_blocks(structure)")


_init_blocks_db()

# ---------------------------------------------------------------- Firebase (SA OAuth)
_oauth = {"tok": None, "exp": 0}


def _oauth_token():
    if _oauth["tok"] and time.time() < _oauth["exp"] - 60:
        return _oauth["tok"]
    with open(SA_PATH, "r", encoding="utf-8") as f:
        sa = json.load(f)
    now = int(time.time())
    assertion = jwt.encode({
        "iss": sa["client_email"],
        "scope": "https://www.googleapis.com/auth/firebase.database "
                 "https://www.googleapis.com/auth/userinfo.email",
        "aud": "https://oauth2.googleapis.com/token",
        "iat": now, "exp": now + 3600,
    }, sa["private_key"], algorithm="RS256")
    data = urllib.parse.urlencode({
        "grant_type": "urn:ietf:params:oauth:grant-type:jwt-bearer",
        "assertion": assertion,
    }).encode()
    with urllib.request.urlopen("https://oauth2.googleapis.com/token", data=data, timeout=25) as r:
        tok = json.loads(r.read().decode("utf-8"))
    _oauth["tok"] = tok["access_token"]
    _oauth["exp"] = now + int(tok.get("expires_in", 3600))
    return _oauth["tok"]


def _rtdb_get(path):
    try:
        url = RTDB + path + "?access_token=" + _oauth_token()
        with urllib.request.urlopen(url, timeout=15) as r:
            raw = r.read().decode("utf-8").strip()
        if not raw or raw == "null":
            return None
        return json.loads(raw)
    except Exception:
        return None


# ---------------------------------------------------------------- helpers
def _manifest():
    try:
        with open(MANIFEST_PATH, "r", encoding="utf-8") as f:
            return json.load(f)
    except Exception:
        return None


def _history(manifest):
    try:
        with open(HISTORY_PATH, "r", encoding="utf-8") as f:
            history = json.load(f)
        if not isinstance(history, list):
            history = []
    except Exception:
        history = []
    by_version = {}
    for item in history + ([manifest] if isinstance(manifest, dict) else []):
        if not isinstance(item, dict):
            continue
        try:
            version_code = int(item.get("versionCode", 0))
        except Exception:
            continue
        if version_code > 0:
            by_version[version_code] = item
    return [by_version[key] for key in sorted(by_version)]


def _merged_changelog(items):
    groups = {"new": [], "fixed": [], "other": []}
    seen = set()
    for item in items:
        current = "other"
        for raw_line in str(item.get("changelog", "") or "").splitlines():
            line = raw_line.strip()
            if not line:
                continue
            lower = line.lower().rstrip(":")
            if lower in ("что нового", "новое", "добавлено"):
                current = "new"
                continue
            if lower in ("исправлено", "исправления"):
                current = "fixed"
                continue
            text = line.lstrip("–—-• ").strip()
            if not text:
                continue
            key = text.casefold()
            if key in seen:
                continue
            seen.add(key)
            groups[current].append("– " + text)
    parts = []
    if groups["new"]:
        parts.append("Что нового:\n" + "\n".join(groups["new"]))
    if groups["fixed"]:
        parts.append("Исправлено:\n" + "\n".join(groups["fixed"]))
    if groups["other"]:
        parts.append("\n".join(groups["other"]))
    return "\n\n".join(parts)


def _has_supporter(uid):
    """Значок ACCESS_SUPPORTER (или команда/разработчик) у пользователя uid?"""
    badge = _rtdb_get("/badges/%d.json" % uid)
    if not isinstance(badge, dict):
        # значок может быть записан просто числом-ролью (legacy) — тогда supporter по умолчанию
        return badge is not None
    access = badge.get("p")
    if access is None:
        return True  # legacy-запись без флага доступа — считаем supporter
    return (int(access) & (ACCESS_SUPPORTER | ACCESS_DEVELOPER)) != 0


def _has_developer(uid):
    """Проверить ACCESS_DEVELOPER по серверной копии значков, без доверия данным клиента."""
    if uid in TEAM_HARDCODED:
        return True
    try:
        with open(BADGES_PATH, "r", encoding="utf-8") as f:
            badge = (json.load(f) or {}).get(str(uid))
        if not isinstance(badge, dict):
            return badge == 0  # legacy ROLE_TEAM
        if int(badge.get("e") or 0) == EMOJI_TEAM:
            return True
        return (int(badge.get("p") or 0) & ACCESS_DEVELOPER) != 0
    except Exception:
        return False


def _token_valid(uid, token):
    if not token:
        return False
    rec = _rtdb_get("/beta_tokens/%d.json" % uid)
    if not isinstance(rec, dict):
        return False
    want = rec.get("hash")
    if not want:
        return False
    got = hashlib.sha256(token.encode("utf-8")).hexdigest()
    # constant-time compare
    if len(got) != len(want):
        return False
    diff = 0
    for a, b in zip(got, want):
        diff |= ord(a) ^ ord(b)
    return diff == 0


def _plugin_identity(data):
    plugin_id = str(data.get("pluginId") or data.get("id") or "").strip().lower()
    sha256 = str(data.get("sha256") or "").strip().lower()
    if plugin_id and not PLUGIN_ID_RE.fullmatch(plugin_id):
        plugin_id = ""  # старые плагины с необычным id всё ещё проверяются по SHA-256
    if sha256 and not SHA256_RE.fullmatch(sha256):
        abort(400, "invalid sha256")
    structure = str(data.get("structure") or "").strip().lower()
    simhash = str(data.get("simhash") or "").strip().lower()
    try:
        feature_count = max(0, int(data.get("featureCount") or 0))
    except Exception:
        feature_count = 0
    if structure and not SHA256_RE.fullmatch(structure):
        abort(400, "invalid structure fingerprint")
    if simhash and not re.fullmatch(r"[0-9a-f]{16}", simhash):
        abort(400, "invalid simhash")
    if not plugin_id and not sha256:
        abort(400, "plugin id or sha256 is required")
    return plugin_id, sha256, structure, simhash, feature_count


def _find_plugin_block(plugin_id, sha256, structure="", simhash="", feature_count=0):
    clauses, args = [], []
    if plugin_id:
        clauses.append("plugin_id = ?")
        args.append(plugin_id)
    if sha256:
        clauses.append("sha256 = ?")
        args.append(sha256)
    if structure:
        clauses.append("structure = ?")
        args.append(structure)
    if not clauses:
        return None
    with _blocks_db() as db:
        row = db.execute(
            "SELECT plugin_id, sha256, structure, simhash, feature_count, name, reason, blocked_by, blocked_at "
            "FROM plugin_blocks WHERE " + " OR ".join(clauses) + " ORDER BY blocked_at DESC LIMIT 1",
            args,
        ).fetchone()
        if row is not None or not simhash or feature_count < 12:
            return row
        candidates = db.execute(
            "SELECT plugin_id, sha256, structure, simhash, feature_count, name, reason, blocked_by, blocked_at "
            "FROM plugin_blocks WHERE simhash != '' AND feature_count >= 12"
        ).fetchall()
    current = int(simhash, 16)
    best = None
    best_distance = 65
    for candidate in candidates:
        count = int(candidate["feature_count"] or 0)
        ratio = count / float(feature_count)
        if ratio < 0.40 or ratio > 2.50:
            continue
        distance = (current ^ int(candidate["simhash"], 16)).bit_count()
        limit = 3 if min(count, feature_count) < 24 else 5
        if distance <= limit and distance < best_distance:
            best = candidate
            best_distance = distance
    return best


def _block_json(row):
    if row is None:
        return {"blocked": False}
    return {
        "blocked": True,
        "pluginId": row["plugin_id"],
        "sha256": row["sha256"],
        "structure": row["structure"],
        "simhash": row["simhash"],
        "name": row["name"],
        "reason": row["reason"],
        "blockedBy": row["blocked_by"],
        "blockedAt": row["blocked_at"],
    }


# ---------------------------------------------------------------- endpoints
@app.route("/api/beta/latest")
def latest():
    m = _manifest()
    if not m:
        return jsonify({"available": False}), 200
    try:
        current_version = max(0, int(request.args.get("versionCode", "0")))
    except Exception:
        current_version = 0
    history = _history(m)
    changelog = m.get("changelog", "")
    if current_version > 0:
        missed = [item for item in history
                  if current_version < int(item.get("versionCode", 0)) <= int(m.get("versionCode", 0))]
        if missed:
            changelog = _merged_changelog(missed)
    elif history:
        # Старые клиенты ещё не отправляют versionCode. Для них безопаснее показать
        # всю сохранённую историю, чем потерять изменения между пропущенными релизами.
        changelog = _merged_changelog(history)
    return jsonify({
        "available": True,
        "versionCode": m.get("versionCode", 0),
        "versionName": m.get("versionName", ""),
        "changelog": changelog,
        "size": m.get("size", 0),
        "sha256": m.get("sha256", ""),
        "publishedAt": m.get("publishedAt", 0),
    })


@app.route("/api/beta/download")
def download():
    try:
        uid = int(request.args.get("uid", "0"))
    except Exception:
        uid = 0
    token = request.args.get("token", "")
    if uid <= 0 or not token:
        abort(400)
    if not _token_valid(uid, token):
        abort(403)
    if not _has_supporter(uid):  # значок могли отозвать после выдачи токена
        abort(403)
    if not os.path.exists(APK_PATH):
        abort(404)
    return send_file(APK_PATH, mimetype="application/vnd.android.package-archive",
                     as_attachment=True, download_name="DevGram-beta.apk")


@app.route("/api/plugins/block-status")
def plugin_block_status():
    plugin_id, sha256, structure, simhash, feature_count = _plugin_identity(request.args)
    response = jsonify(_block_json(_find_plugin_block(
        plugin_id, sha256, structure, simhash, feature_count)))
    response.headers["Cache-Control"] = "no-store"
    return response


@app.route("/api/plugins/block", methods=["POST"])
def set_plugin_block():
    data = request.get_json(silent=True)
    if not isinstance(data, dict):
        abort(400)
    try:
        uid = int(data.get("uid") or 0)
    except Exception:
        uid = 0
    token = str(data.get("token") or "")
    if uid <= 0 or not _token_valid(uid, token):
        abort(403)
    if not _has_developer(uid):
        abort(403)
    plugin_id, sha256, structure, simhash, feature_count = _plugin_identity(data)
    blocked = bool(data.get("blocked", True))
    if not blocked:
        clauses, args = [], []
        if plugin_id:
            clauses.append("plugin_id = ?")
            args.append(plugin_id)
        if sha256:
            clauses.append("sha256 = ?")
            args.append(sha256)
        with _blocks_db() as db:
            cursor = db.execute("DELETE FROM plugin_blocks WHERE " + " OR ".join(clauses), args)
        return jsonify({"ok": True, "blocked": False, "removed": cursor.rowcount})

    key = "id:" + plugin_id if plugin_id else "sha256:" + sha256
    name = str(data.get("name") or "").strip()[:128]
    reason = str(data.get("reason") or "").strip()[:500]
    now = int(time.time() * 1000)
    with _blocks_db() as db:
        db.execute(
            "INSERT INTO plugin_blocks(block_key, plugin_id, sha256, structure, simhash, feature_count, "
            "name, reason, blocked_by, blocked_at) VALUES(?,?,?,?,?,?,?,?,?,?) "
            "ON CONFLICT(block_key) DO UPDATE SET sha256=excluded.sha256, structure=excluded.structure, "
            "simhash=excluded.simhash, feature_count=excluded.feature_count, "
            "name=excluded.name, reason=excluded.reason, "
            "blocked_by=excluded.blocked_by, blocked_at=excluded.blocked_at",
            (key, plugin_id, sha256, structure, simhash, feature_count, name, reason, uid, now),
        )
    return jsonify({"ok": True, **_block_json(_find_plugin_block(
        plugin_id, sha256, structure, simhash, feature_count))})


@app.route("/api/beta/health")
def health():
    with _blocks_db() as db:
        block_count = db.execute("SELECT COUNT(*) FROM plugin_blocks").fetchone()[0]
    return jsonify({"ok": True, "hasApk": os.path.exists(APK_PATH),
                    "hasManifest": _manifest() is not None, "pluginBlocks": block_count})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=int(os.environ.get("BETA_PORT", "8020")))
