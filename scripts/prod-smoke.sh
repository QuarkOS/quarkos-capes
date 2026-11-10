#!/usr/bin/env bash
# Launch every dist jar in a production client and record a third-person cape shot.
# Uses portablemc with the Fabric, Forge, and NeoForge installers. Fabric API comes from Modrinth.
# Does not publish.
set -u
cd "$(dirname "$0")/.."
ROOT="$(pwd)"
export PATH="${HOME}/.local/bin:${PATH}"
export JAVA_HOME="${JAVA_HOME_GAME:-/usr/lib/jvm/java-21-openjdk-amd64}"
export PATH="${JAVA_HOME}/bin:${PATH}"

DIST="${ROOT}/dist"
MAIN="${ROOT}/build/prod-smoke/main"
SHOTS="${ROOT}/docs/prod-smoke"
REPORT="${ROOT}/docs/PROD-SMOKE.md"
mkdir -p "${MAIN}" "${SHOTS}" "${ROOT}/build/prod-smoke/runs"

if ! command -v portablemc >/dev/null 2>&1; then
  python3 -m pip install --user portablemc
fi

# Loader versions this tree builds against. fabric:<mc>:<loader>, forge::<full>, neoforge::<full>.
installer_for() {
  local loader="$1" mc="$2"
  case "${loader}:${mc}" in
    fabric:*) echo "fabric:${mc}:0.19.5" ;;
    forge:1.20.1) echo "forge::1.20.1-47.4.29" ;;
    forge:1.21.1) echo "forge::1.21.1-52.1.20" ;;
    forge:1.21.4) echo "forge::1.21.4-54.1.22" ;;
    forge:1.21.8) echo "forge::1.21.8-58.1.26" ;;
    forge:1.21.11) echo "forge::1.21.11-61.2.4" ;;
    neoforge:1.21.1) echo "neoforge::21.1.265" ;;
    neoforge:1.21.4) echo "neoforge::21.4.161" ;;
    neoforge:1.21.8) echo "neoforge::21.8.57" ;;
    neoforge:1.21.11) echo "neoforge::21.11.48" ;;
    *) echo "" ;;
  esac
}

# Exact Fabric API builds this tree compiles against. Newer matching builds are a fallback.
preferred_api() {
  case "$1" in
    1.20.1) echo "0.92.12+1.20.1" ;;
    1.21.1) echo "0.116.17+1.21.1" ;;
    1.21.4) echo "0.119.4+1.21.4" ;;
    1.21.8) echo "0.136.1+1.21.8" ;;
    1.21.11) echo "0.141.6+1.21.11" ;;
    *) echo "" ;;
  esac
}

fetch_fabric_api() {
  local mc="$1" dest="$2"
  local query preferred
  preferred="$(preferred_api "${mc}")"
  query="$(python3 -c 'import urllib.parse,sys; print(urllib.parse.quote(sys.argv[1]))' "[\"${mc}\"]")"
  local body
  body="$(curl -fsSL -A "QuarkOS-Capes/1.0.0 (prod-smoke)" \
    "https://api.modrinth.com/v2/project/fabric-api/version?game_versions=${query}&loaders=%5B%22fabric%22%5D")" \
    || return 1
  python3 -c '
import json, sys, urllib.request
versions = json.loads(sys.argv[1])
preferred = sys.argv[2]
dest = sys.argv[3]
def primary(version):
    for f in version.get("files", []):
        if f.get("primary") and f.get("filename", "").endswith(".jar"):
            return f
    for f in version.get("files", []):
        if f.get("filename", "").endswith(".jar"):
            return f
    return None
chosen = None
for version in versions:
    if version.get("version_number") == preferred:
        chosen = version
        break
if chosen is None and versions:
    chosen = versions[0]
if chosen is None:
    sys.exit(2)
file = primary(chosen)
if file is None:
    sys.exit(3)
opener = urllib.request.build_opener()
opener.addheaders = [("User-Agent", "QuarkOS-Capes/1.0.0 (prod-smoke)")]
urllib.request.install_opener(opener)
urllib.request.urlretrieve(file["url"], dest)
print(chosen.get("version_number", ""))
' "${body}" "${preferred}" "${dest}"
}

rows="${ROOT}/build/prod-smoke/rows.tsv"
if [[ $# -eq 0 || ! -s "${rows}" ]]; then
  : > "${rows}"
fi

write_report() {
  {
    echo "# Production smoke"
    echo
    echo "Each \`dist/\` jar was launched with [portablemc](https://github.com/mindstorm38/portablemc) and the real Fabric, Forge, or NeoForge installer. Fabric API was downloaded from Modrinth. Forge and NeoForge have no extra required mod; Cloth Config is inside the jars that need it. The client loads a flat creative world, wears the bundled cloak, and saves a third-person shot. Nothing is published."
    echo
    echo "| Result | Loader | Minecraft | Installer | Modrinth deps | Screenshot |"
    echo "| --- | --- | --- | --- | --- | --- |"
    if [[ -s "${rows}" ]]; then
      sort -t $'\t' -k2,2 -k3,3V "${rows}" | while IFS=$'\t' read -r result loader mc installer deps shot; do
        echo "| ${result} | ${loader} | ${mc} | \`${installer}\` | ${deps} | ${shot} |"
      done
    fi
    echo
    echo "A pass needs the log line \`quarkos_capes prod smoke ok\` and a screenshot larger than 1000 bytes."
  } > "${REPORT}"
}

shopt -s nullglob
jars=("${DIST}"/quarkos-capes-*.jar)
if [[ ${#jars[@]} -eq 0 ]]; then
  echo "no jars in dist/. Run scripts/release.sh first." >&2
  exit 1
fi
if [[ $# -gt 0 ]]; then
  filtered=()
  for jar in "${jars[@]}"; do
    for want in "$@"; do
      if [[ "$(basename "${jar}")" == *"+${want}.jar" ]]; then
        filtered+=("${jar}")
      fi
    done
  done
  jars=("${filtered[@]}")
fi
if [[ ${#jars[@]} -eq 0 ]]; then
  echo "no dist jar matched: $*" >&2
  exit 1
fi

failures=0
if [[ $# -gt 0 && -s "${rows}" ]]; then
  kept="${rows}.kept"
  : > "${kept}"
  while IFS= read -r line; do
    [[ -z "${line}" ]] && continue
    loader="${line#*$'\t'}"
    loader="${loader%%$'\t'*}"
    mc="${line#*$'\t'}"
    mc="${mc#*$'\t'}"
    mc="${mc%%$'\t'*}"
    drop=0
    for want in "$@"; do
      if [[ "${loader}-${mc}" == "${want}" ]]; then
        drop=1
      fi
    done
    if [[ "${drop}" -eq 0 ]]; then
      printf '%s\n' "${line}" >> "${kept}"
    fi
  done < "${rows}"
  mv "${kept}" "${rows}"
fi
for jar in "${jars[@]}"; do
  base="$(basename "${jar}")"
  # quarkos-capes-1.0.0+fabric-1.21.11.jar
  rest="${base#quarkos-capes-}"
  rest="${rest%.jar}"
  coord="${rest#*+}"
  loader="${coord%%-*}"
  mc="${coord#*-}"
  id="${loader}-${mc}"
  installer="$(installer_for "${loader}" "${mc}")"
  run="${ROOT}/build/prod-smoke/runs/${id}"
  rm -rf "${run}"
  mkdir -p "${run}/mods" "${run}/logs"
  cp "${jar}" "${run}/mods/"
  deps="none"
  result="fail"
  shot_cell="—"
  reason=""
  if [[ -z "${installer}" ]]; then
    reason="no installer mapping"
  elif [[ "${loader}" == "fabric" ]]; then
    if api_version="$(fetch_fabric_api "${mc}" "${run}/mods/fabric-api.jar")"; then
      deps="fabric-api ${api_version}"
    else
      reason="Modrinth fabric-api download failed"
    fi
  fi
  png="${SHOTS}/${id}.png"
  rm -f "${png}" "${png}.trace"
  log="${run}/client.log"
  if [[ -z "${reason}" ]]; then
    set +e
    xvfb-run -a --server-args="-screen 0 1280x720x24" \
      env LIBGL_ALWAYS_SOFTWARE=1 MESA_GL_VERSION_OVERRIDE=3.3 \
      timeout 1200 portablemc \
        --main-dir "${MAIN}" \
        start "${installer}" \
        --mc-dir "${run}" \
        --jvm "${JAVA_HOME}/bin/java" \
        --username CapeSmoke \
        --resolution 1280x720 \
        --disable-chat \
        --disable-multiplayer \
        --jvm-arg="-Dquarkos_capes.prodSmoke=true,-Dquarkos_capes.prodSmokeShot=${png},-Dquarkos_capes.width=1280,-Dquarkos_capes.height=720,-Xms512m,-Xmx2G" \
        > "${log}" 2>&1
    code=$?
    set -e
    game_log="${run}/logs/latest.log"
    if { grep -q "quarkos_capes prod smoke ok" "${log}" || grep -q "quarkos_capes prod smoke ok" "${game_log}"; } \
        && [[ -f "${png}" ]] && [[ "$(stat -c%s "${png}")" -gt 1000 ]]; then
      result="pass"
      shot_cell="[docs/prod-smoke/${id}.png](prod-smoke/${id}.png)"
    else
      reason="exit ${code}"
      if grep -q "quarkos_capes prod smoke fail" "${log}" "${game_log}"; then
        reason="$(grep "quarkos_capes prod smoke fail" "${log}" "${game_log}" | tail -1)"
      elif [[ -f "${png}.trace" ]]; then
        reason="$(tail -1 "${png}.trace")"
      fi
      failures=$((failures + 1))
    fi
  else
    failures=$((failures + 1))
    echo "${reason}" > "${log}"
  fi
  printf '%s\t%s\t%s\t%s\t%s\t%s\n' "${result}" "${loader}" "${mc}" "${installer:-—}" "${deps}" "${shot_cell}" >> "${rows}"
  rm -f "${png}.trace"
  if [[ "${result}" != "pass" ]]; then
    echo "FAIL ${id}: ${reason}" >&2
  else
    echo "PASS ${id}"
  fi
  write_report
done

write_report
echo "wrote ${REPORT} (${failures} failed)"
exit "${failures}"
