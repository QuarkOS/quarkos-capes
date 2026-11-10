#!/usr/bin/env bash
# 1920x1080 store screenshots from the Fabric 1.21.11 release jar, plus a two-client server.
# Does not publish. Requires the local site on 127.0.0.1:8787 with dev auth.
set -u
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
export PATH="${HOME}/.local/bin:${PATH}"
export JAVA_HOME="${JAVA_HOME_GAME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="${JAVA_HOME}/bin:${PATH}"

MC="1.21.11"
JAR="${ROOT}/dist/quarkos-capes-1.0.0+fabric-${MC}.jar"
MAIN="${ROOT}/build/prod-smoke/main"
SHOTS="${ROOT}/docs/store/shots"
SERVER="${ROOT}/build/store-server"
SHARE="${ROOT}/build/store-share"
SECRET="${CAPES_DEV_AUTH_SECRET:-local-demo-secret-change-me}"
PORT=25599
mkdir -p "${SHOTS}" "${MAIN}" "${SHARE}" "${SERVER}"

if [[ ! -f "${JAR}" ]]; then
  echo "missing ${JAR}. Build it with -Prelease=true -PallowLocalService=true first." >&2
  exit 1
fi
if ! curl -fsS "http://127.0.0.1:8787/v1/health" | grep -q '"ok":true'; then
  echo "site is not healthy on 127.0.0.1:8787" >&2
  exit 1
fi
if ! command -v portablemc >/dev/null 2>&1; then
  python3 -m pip install --user portablemc
fi

fetch_fabric_api() {
  local dest="$1"
  python3 - "${MC}" "${dest}" << 'PY'
import json, sys, urllib.request
mc, dest = sys.argv[1], sys.argv[2]
preferred = "0.141.6+1.21.11"
url = "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=" + urllib.parse.quote('["%s"]' % mc) + "&loaders=" + urllib.parse.quote('["fabric"]')
req = urllib.request.Request(url, headers={"User-Agent": "QuarkOS-Capes/1.0.0 (store-shots)"})
versions = json.load(urllib.request.urlopen(req, timeout=60))
chosen = next((v for v in versions if v.get("version_number") == preferred), versions[0])
file = next(f for f in chosen["files"] if f["filename"].endswith(".jar") and f.get("primary", True))
urllib.request.urlretrieve(file["url"], dest)
print(chosen["version_number"])
PY
}

modrinth_jar() {
  local project="$1" dest="$2"
  python3 - "${project}" "${MC}" "${dest}" << 'PY'
import json, sys, urllib.request
project, mc, dest = sys.argv[1], sys.argv[2], sys.argv[3]
url = "https://api.modrinth.com/v2/project/%s/version?game_versions=%s&loaders=%s" % (
    project, urllib.parse.quote('["%s"]' % mc), urllib.parse.quote('["fabric"]'))
req = urllib.request.Request(url, headers={"User-Agent": "QuarkOS-Capes/1.0.0 (store-shots)"})
versions = json.load(urllib.request.urlopen(req, timeout=60))
if not versions:
    raise SystemExit("no %s build for %s" % (project, mc))
chosen = versions[0]
file = next(f for f in chosen["files"] if f["filename"].endswith(".jar"))
urllib.request.urlretrieve(file["url"], dest)
print(chosen["version_number"], file["filename"])
PY
}

seed_wearing() {
  python3 - "${SECRET}" << 'PY'
import hashlib, hmac, json, uuid, urllib.request, sys
secret = sys.argv[1].encode()

def offline(name):
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))

def bearer(uid):
    mac = hmac.new(secret, uid.encode(), hashlib.sha256).hexdigest()
    return "dev1." + uid + "." + mac

def call(method, path, token, body=None):
    data = None if body is None else json.dumps(body).encode()
    req = urllib.request.Request("http://127.0.0.1:8787" + path, data=data, method=method)
    req.add_header("Authorization", "Bearer " + token)
    if data is not None:
        req.add_header("Content-Type", "application/json")
    with urllib.request.urlopen(req, timeout=20) as response:
        raw = response.read()
        return response.status, json.loads(raw) if raw else {}

pairs = [("CapeStoreA", "verdant-signal"), ("CapeStoreB", "violet-rift")]
for name, cape in pairs:
    uid = offline(name)
    status, body = call("POST", "/v1/auth/complete", bearer(uid), {"username": name, "serverId": "ignored"})
    if status != 200:
        raise SystemExit("auth %s %s" % (name, status))
    token = body["token"]
    status, worn = call("PUT", "/v1/me/wearing", token, {"source": "ours", "id": cape})
    print(name, uid, status, worn.get("wearing", worn).get("id") if isinstance(worn.get("wearing"), dict) else worn)
PY
}

prepare_server() {
  if [[ -f "${SERVER}/server.pid" ]] && kill -0 "$(cat "${SERVER}/server.pid")" 2>/dev/null; then
    echo "server already running"
    return
  fi
  if [[ ! -f "${SERVER}/server.jar" ]]; then
    python3 - "${MC}" "${SERVER}/server.jar" << 'PY'
import json, sys, urllib.request
mc, dest = sys.argv[1], sys.argv[2]
manifest = json.load(urllib.request.urlopen("https://piston-meta.mojang.com/mc/game/version_manifest_v2.json", timeout=60))
version = next(v for v in manifest["versions"] if v["id"] == mc)
meta = json.load(urllib.request.urlopen(version["url"], timeout=60))
urllib.request.urlretrieve(meta["downloads"]["server"]["url"], dest)
print("server", meta["downloads"]["server"]["sha1"])
PY
  fi
  printf 'eula=true\n' > "${SERVER}/eula.txt"
  cat > "${SERVER}/server.properties" << EOF
server-port=${PORT}
server-ip=127.0.0.1
online-mode=false
enforce-secure-profile=false
prevent-proxy-connections=false
level-type=minecraft:flat
gamemode=creative
force-gamemode=true
difficulty=peaceful
spawn-monsters=false
spawn-protection=0
enable-command-block=true
white-list=false
motd=QuarkOS Capes
view-distance=8
simulation-distance=6
max-players=8
level-name=world
EOF
  python3 - "${SERVER}/ops.json" << 'PY'
import hashlib, json, sys, uuid
def offline(name):
    digest = bytearray(hashlib.md5(("OfflinePlayer:" + name).encode()).digest())
    digest[6] = (digest[6] & 0x0F) | 0x30
    digest[8] = (digest[8] & 0x3F) | 0x80
    return str(uuid.UUID(bytes=bytes(digest)))
ops = [{"uuid": offline(name), "name": name, "level": 4, "bypassesPlayerLimit": False}
       for name in ("CapeStoreA", "CapeStoreB")]
open(sys.argv[1], "w").write(json.dumps(ops, indent=2) + "\n")
for row in ops:
    print(row["name"], row["uuid"])
PY
  (
    cd "${SERVER}"
    "${JAVA_HOME}/bin/java" -Xms512m -Xmx1G -jar server.jar nogui > server.log 2>&1
  ) &
  echo $! > "${SERVER}/server.pid"
  for _ in $(seq 1 240); do
    if grep -q "Done (" "${SERVER}/server.log" 2>/dev/null; then
      echo "server ready"
      return
    fi
    if ! kill -0 "$(cat "${SERVER}/server.pid")" 2>/dev/null; then
      echo "server exited" >&2
      tail -40 "${SERVER}/server.log" >&2
      exit 1
    fi
    sleep 2
  done
  echo "server did not finish starting" >&2
  tail -40 "${SERVER}/server.log" >&2
  exit 1
}

run_client() {
  local id="$1" scene="$2" role="$3" username="$4" uuid="$5" timeout_s="$6"
  shift 6
  local run="${ROOT}/build/store/runs/${id}"
  rm -rf "${run}"
  mkdir -p "${run}/mods" "${run}/logs"
  cp "${JAR}" "${run}/mods/"
  fetch_fabric_api "${run}/mods/fabric-api.jar" >/dev/null
  local log="${run}/client.log"
  local jvm="-Dquarkos_capes.store=true,-Dquarkos_capes.storeScene=${scene},-Dquarkos_capes.storeDir=${SHOTS},-Dquarkos_capes.storeRole=${role},-Dquarkos_capes.storeShare=${SHARE},-Dquarkos_capes.server=127.0.0.1:${PORT},-Dquarkos_capes.width=1920,-Dquarkos_capes.height=1080,-Dquarkos_capes.guiScale=2,-Xms512m,-Xmx2G"
  set +e
  xvfb-run -a --server-args="-screen 0 1920x1080x24" \
    env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460 \
    timeout "${timeout_s}" portablemc \
      --main-dir "${MAIN}" \
      start "fabric:${MC}:0.19.5" \
      --mc-dir "${run}" \
      --jvm "${JAVA_HOME}/bin/java" \
      --username "${username}" \
      --uuid "${uuid}" \
      --resolution 1920x1080 \
      --disable-chat \
      "$@" \
      --jvm-arg="${jvm}" \
      > "${log}" 2>&1
  local code=$?
  set -e
  echo "${code}" > "${run}/exit.code"
  echo "client ${id} exit ${code}"
}

check_png() {
  local file="$1"
  python3 - "${file}" << 'PY'
import sys
from PIL import Image
path = sys.argv[1]
im = Image.open(path)
if im.size != (1920, 1080):
    raise SystemExit("%s is %s" % (path, im.size))
colors = im.convert("RGB").resize((32, 18)).getcolors(32 * 18)
if colors and len(colors) < 4:
    raise SystemExit("%s looks blank" % path)
print(path, im.size, "ok")
PY
}

echo "seeding wearing"
seed_wearing

if [[ "${STORE_SKIP_GALLERY:-}" == "1" ]]; then
  echo "gallery skipped"
else
rm -f "${SHARE}/capture.trace" "${SHOTS}/wardrobe.png" "${SHOTS}/browse.png" "${SHOTS}/wearing.png" "${SHOTS}/elytra.png"
echo "gallery"
run_client gallery gallery a CapeStoreA "$(python3 - << 'PY'
import hashlib, uuid
d = bytearray(hashlib.md5(b"OfflinePlayer:CapeStoreA").digest())
d[6] = (d[6] & 0x0F) | 0x30
d[8] = (d[8] & 0x3F) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)" 900 --disable-multiplayer
for name in wardrobe browse wearing elytra; do
  check_png "${SHOTS}/${name}.png"
done
if ! grep -q "quarkos_capes store gallery ok" "${ROOT}/build/store/runs/gallery/logs/latest.log" \
    "${ROOT}/build/store/runs/gallery/client.log"; then
  echo "gallery did not log ok" >&2
  exit 1
fi
fi

if [[ "${STORE_SKIP_SHADER:-}" == "1" ]]; then
  echo "shader skipped"
else
echo "shader"
SHADER_RUN="${ROOT}/build/store/runs/shader"
rm -rf "${SHADER_RUN}"
mkdir -p "${SHADER_RUN}/mods" "${SHADER_RUN}/shaderpacks" "${SHADER_RUN}/config" "${SHADER_RUN}/logs"
cp "${JAR}" "${SHADER_RUN}/mods/"
fetch_fabric_api "${SHADER_RUN}/mods/fabric-api.jar" >/dev/null
modrinth_jar iris "${SHADER_RUN}/mods/iris.jar"
modrinth_jar sodium "${SHADER_RUN}/mods/sodium.jar"
python3 - "${SHADER_RUN}/shaderpacks" << 'PY'
import json, sys, urllib.request
dest = sys.argv[1]
url = "https://api.modrinth.com/v2/project/complementary-reimagined/version"
req = urllib.request.Request(url, headers={"User-Agent": "QuarkOS-Capes/1.0.0 (store-shots)"})
versions = json.load(urllib.request.urlopen(req, timeout=60))
file = next(f for f in versions[0]["files"] if f["filename"].endswith(".zip"))
urllib.request.urlretrieve(file["url"], dest + "/" + file["filename"])
open("/tmp/store-shader-name.txt", "w").write(file["filename"])
print(file["filename"])
PY
PACK="$(cat /tmp/store-shader-name.txt)"
printf 'enableShaders=true\nshaderPack=%s\n' "${PACK}" > "${SHADER_RUN}/config/iris.properties"
# run_client wipes the directory, so install shader files after a dedicated launch.
rm -f "${SHOTS}/shader.png"
set +e
xvfb-run -a --server-args="-screen 0 1920x1080x24" \
  env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=4.6 MESA_GLSL_VERSION_OVERRIDE=460 \
  timeout 1200 portablemc \
    --main-dir "${MAIN}" \
    start "fabric:${MC}:0.19.5" \
    --mc-dir "${SHADER_RUN}" \
    --jvm "${JAVA_HOME}/bin/java" \
    --username CapeStoreA \
    --uuid "$(python3 - << 'PY'
import hashlib, uuid
d = bytearray(hashlib.md5(b"OfflinePlayer:CapeStoreA").digest())
d[6] = (d[6] & 0x0F) | 0x30
d[8] = (d[8] & 0x3F) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)" \
    --resolution 1920x1080 \
    --disable-chat \
    --disable-multiplayer \
    --jvm-arg="-Dquarkos_capes.store=true,-Dquarkos_capes.storeScene=shader,-Dquarkos_capes.storeDir=${SHOTS},-Dquarkos_capes.width=1920,-Dquarkos_capes.height=1080,-Dquarkos_capes.guiScale=2,-Xms512m,-Xmx2G" \
    > "${SHADER_RUN}/client.log" 2>&1
shader_code=$?
set -e
echo "shader client exit ${shader_code}"
# The launch above created the mc dir, but iris jars were copied before portablemc, and run did not wipe.
# iris.properties was written before launch. Good.
if [[ ! -f "${SHOTS}/shader.png" ]]; then
  echo "shader shot missing" >&2
  tail -30 "${SHADER_RUN}/client.log" >&2
  exit 1
fi
check_png "${SHOTS}/shader.png"
fi

echo "multiplayer"
prepare_server
rm -f "${SHARE}/a-ready" "${SHARE}/b-shot" "${SHARE}/b-turned" "${SHARE}/a-shot" "${SHARE}/a-ground" \
  "${SHOTS}/multiplayer-a.png" "${SHOTS}/multiplayer-b.png"
UUID_A="$(python3 - << 'PY'
import hashlib, uuid
d = bytearray(hashlib.md5(b"OfflinePlayer:CapeStoreA").digest())
d[6] = (d[6] & 0x0F) | 0x30
d[8] = (d[8] & 0x3F) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)"
UUID_B="$(python3 - << 'PY'
import hashlib, uuid
d = bytearray(hashlib.md5(b"OfflinePlayer:CapeStoreB").digest())
d[6] = (d[6] & 0x0F) | 0x30
d[8] = (d[8] & 0x3F) | 0x80
print(uuid.UUID(bytes=bytes(d)))
PY
)"
run_client peer-a peer a CapeStoreA "${UUID_A}" 700 &
pid_a=$!
sleep 8
run_client peer-b peer b CapeStoreB "${UUID_B}" 700 &
pid_b=$!
wait "${pid_a}" || true
wait "${pid_b}" || true
check_png "${SHOTS}/multiplayer-a.png"
check_png "${SHOTS}/multiplayer-b.png"
if ! grep -q "quarkos_capes store multiplayer ok" \
    "${ROOT}/build/store/runs/peer-a/logs/latest.log" \
    "${ROOT}/build/store/runs/peer-b/logs/latest.log" \
    "${ROOT}/build/store/runs/peer-a/client.log" \
    "${ROOT}/build/store/runs/peer-b/client.log"; then
  echo "multiplayer did not log ok" >&2
  exit 1
fi
echo "store shots ready in ${SHOTS}"
