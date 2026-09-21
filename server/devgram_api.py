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
                name TEXT NOT NULL DEFAULT '',
                reason TEXT NOT NULL DEFAULT '',
                blocked_by INTEGER NOT NULL,
                blocked_at INTEGER NOT NULL
            )
        """)
        db.execute("CREATE INDEX IF NOT EXISTS idx_plugin_blocks_id ON plugin_blocks(plugin_id)")
        db.execute("CREATE INDEX IF NOT EXISTS idx_plugin_blocks_sha ON plugin_blocks(sha256)")


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
    if not plugin_id and not sha256:
        abort(400, "plugin id or sha256 is required")
    return plugin_id, sha256


def _find_plugin_block(plugin_id, sha256):
    clauses, args = [], []
    if plugin_id:
        clauses.append("plugin_id = ?")
        args.append(plugin_id)
    if sha256:
        clauses.append("sha256 = ?")
        args.append(sha256)
    if not clauses:
        return None
    with _blocks_db() as db:
        return db.execute(
            "SELECT plugin_id, sha256, name, reason, blocked_by, blocked_at "
            "FROM plugin_blocks WHERE " + " OR ".join(clauses) + " ORDER BY blocked_at DESC LIMIT 1",
            args,
        ).fetchone()


def _block_json(row):
    if row is None:
        return {"blocked": False}
    return {
        "blocked": True,
        "pluginId": row["plugin_id"],
        "sha256": row["sha256"],
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
    return jsonify({
        "available": True,
        "versionCode": m.get("versionCode", 0),
        "versionName": m.get("versionName", ""),
        "changelog": m.get("changelog", ""),
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
    plugin_id, sha256 = _plugin_identity(request.args)
    response = jsonify(_block_json(_find_plugin_block(plugin_id, sha256)))
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
    plugin_id, sha256 = _plugin_identity(data)
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
            "INSERT INTO plugin_blocks(block_key, plugin_id, sha256, name, reason, blocked_by, blocked_at) "
            "VALUES(?,?,?,?,?,?,?) ON CONFLICT(block_key) DO UPDATE SET "
            "sha256=excluded.sha256, name=excluded.name, reason=excluded.reason, "
            "blocked_by=excluded.blocked_by, blocked_at=excluded.blocked_at",
            (key, plugin_id, sha256, name, reason, uid, now),
        )
    return jsonify({"ok": True, **_block_json(_find_plugin_block(plugin_id, sha256))})


@app.route("/api/beta/health")
def health():
    with _blocks_db() as db:
        block_count = db.execute("SELECT COUNT(*) FROM plugin_blocks").fetchone()[0]
    return jsonify({"ok": True, "hasApk": os.path.exists(APK_PATH),
                    "hasManifest": _manifest() is not None, "pluginBlocks": block_count})


if __name__ == "__main__":
    app.run(host="127.0.0.1", port=int(os.environ.get("BETA_PORT", "8020")))
