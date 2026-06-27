#!/usr/bin/env bash
# install-kubectl-plugin.sh — build (if needed) and install kubectl-microvm as a kubectl plugin
#
# Usage:
#   ./install-kubectl-plugin.sh              # JVM fat-jar wrapper (fast build)
#   ./install-kubectl-plugin.sh --native     # GraalVM native binary (slow first build)
#   ./install-kubectl-plugin.sh --skip-build # install from existing build output
#   ./install-kubectl-plugin.sh --prefix <dir>  # install to custom prefix (default: ~/.local/bin)
#   ./install-kubectl-plugin.sh --uninstall  # remove plugin
#
set -euo pipefail

NATIVE=false
SKIP_BUILD=false
PREFIX="${HOME}/.local/bin"
UNINSTALL=false

while [[ $# -gt 0 ]]; do
  case $1 in
    --help)
      sed -n '2,8p' "$0" | sed 's/^# //'
      exit 0 ;;
    --native)       NATIVE=true ;;
    --skip-build)   SKIP_BUILD=true ;;
    --prefix)       PREFIX="$2"; shift ;;
    --prefix=*)     PREFIX="${1#--prefix=}" ;;
    --uninstall)    UNINSTALL=true ;;
    *) echo "Unknown option: $1" >&2; exit 1 ;;
  esac
  shift
done

PLUGIN_NAME="kubectl-microvm"
INSTALL_PATH="${PREFIX}/${PLUGIN_NAME}"

# Uninstall
if $UNINSTALL; then
  rm -f "${INSTALL_PATH}"
  echo "==> Removed ${INSTALL_PATH}"
  exit 0
fi

# Build
if ! $SKIP_BUILD; then
  if $NATIVE; then
    ./build-local.sh --only cli --skip-tests --native
  else
    ./build-local.sh --only cli --skip-tests
  fi
fi

mkdir -p "${PREFIX}"

# Locate build output — prefer native binary when requested or when it's newer than the JVM jar
NATIVE_BIN=$(find operator-cli/target -maxdepth 1 -name "*-runner" -not -name "*.jar" 2>/dev/null | head -1)
QUARKUS_APP_DIR="operator-cli/target/quarkus-app"

if $NATIVE && [[ -n "$NATIVE_BIN" && -f "$NATIVE_BIN" ]]; then
  echo "==> Installing native binary → ${INSTALL_PATH}"
  cp "${NATIVE_BIN}" "${INSTALL_PATH}"
  chmod +x "${INSTALL_PATH}"
elif [[ -n "$NATIVE_BIN" && -f "$NATIVE_BIN" ]] && ! [[ -d "$QUARKUS_APP_DIR" ]]; then
  # native bin exists and no JVM build — install native
  echo "==> Installing native binary → ${INSTALL_PATH}"
  cp "${NATIVE_BIN}" "${INSTALL_PATH}"
  chmod +x "${INSTALL_PATH}"
elif [[ -d "$QUARKUS_APP_DIR" ]]; then
  # Install JVM wrapper script that launches the Quarkus fast-jar
  APP_DIR_ABS="$(cd "${QUARKUS_APP_DIR}" && pwd)"
  echo "==> Installing JVM wrapper → ${INSTALL_PATH}"
  cat > "${INSTALL_PATH}" <<WRAPPER
#!/usr/bin/env bash
exec java -jar "${APP_DIR_ABS}/quarkus-run.jar" "\$@"
WRAPPER
  chmod +x "${INSTALL_PATH}"

else
  echo "Error: no build output found. Run without --skip-build first." >&2
  exit 1
fi

# Verify PATH
if ! echo ":${PATH}:" | grep -q ":${PREFIX}:"; then
  echo ""
  echo "  ⚠  ${PREFIX} is not in your PATH."
  echo "  Add to your shell profile:"
  echo "    export PATH=\"${PREFIX}:\$PATH\""
fi

echo ""
echo "==> kubectl-microvm installed at ${INSTALL_PATH}"
echo "    Verify:  kubectl microvm --help"
"${INSTALL_PATH}" --help 2>/dev/null | head -6 || true
