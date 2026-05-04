#!/usr/bin/env bash
set -e

RUNTIME_HOME="/app/runtime"
APP_USER="solonclaw"
APP_GROUP="solonclaw"
APP_UID="${SOLONCLAW_UID:-10000}"
APP_GID="${SOLONCLAW_GID:-10000}"

is_positive_int() {
    case "$1" in
        ''|*[!0-9]*)
            return 1
            ;;
    esac

    [ "$1" -gt 0 ]
}

configure_runtime_user() {
    if ! is_positive_int "$APP_UID" || ! is_positive_int "$APP_GID"; then
        echo "Error: SOLONCLAW_UID and SOLONCLAW_GID must be positive numeric IDs."
        exit 1
    fi

    current_gid="$(id -g "$APP_USER")"
    if [ "$current_gid" != "$APP_GID" ]; then
        existing_group="$(getent group "$APP_GID" | cut -d: -f1 || true)"
        if [ -n "$existing_group" ]; then
            usermod -g "$existing_group" "$APP_USER"
            APP_GROUP="$existing_group"
        else
            groupmod -g "$APP_GID" "$APP_GROUP"
        fi
    fi

    current_uid="$(id -u "$APP_USER")"
    if [ "$current_uid" != "$APP_UID" ]; then
        existing_user="$(getent passwd "$APP_UID" | cut -d: -f1 || true)"
        if [ -n "$existing_user" ] && [ "$existing_user" != "$APP_USER" ]; then
            echo "Error: SOLONCLAW_UID=$APP_UID is already used by image user $existing_user."
            exit 1
        fi
        usermod -u "$APP_UID" "$APP_USER"
    fi

    chown -R "$APP_UID:$APP_GID" "/home/$APP_USER" 2>/dev/null || true
}

runtime_is_writable() {
    write_probe="$RUNTIME_HOME/.solonclaw-write-test"
    gosu "$APP_USER" sh -c 'touch "$1" && rm -f "$1"' sh "$write_probe" 2>/dev/null
}

if [ "$(id -u)" = "0" ]; then
    configure_runtime_user
    mkdir -p "$RUNTIME_HOME"

    if ! runtime_is_writable; then
        chown -R "$APP_UID:$APP_GID" "$RUNTIME_HOME" 2>/dev/null || \
            echo "Warning: failed to chown $RUNTIME_HOME; continuing"
        chmod -R u+rwX "$RUNTIME_HOME" 2>/dev/null || true
    fi

    if [ -f "$RUNTIME_HOME/config.yml" ]; then
        chown "$APP_UID:$APP_GID" "$RUNTIME_HOME/config.yml" 2>/dev/null || true
        chmod 640 "$RUNTIME_HOME/config.yml" 2>/dev/null || true
    fi

    if ! runtime_is_writable; then
        echo "Error: $RUNTIME_HOME is not writable by user $APP_USER (uid=$APP_UID, gid=$APP_GID)."
        echo "Fix host permissions for the bind-mounted runtime directory, for example:"
        echo "  sudo mkdir -p runtime"
        echo "  sudo chown -R $APP_UID:$APP_GID runtime"
        echo "  sudo chmod -R u+rwX runtime"
        echo "Or set SOLONCLAW_UID/SOLONCLAW_GID to match the host directory owner before docker compose up."
        echo "Container view of runtime directory:"
        ls -ldn "$RUNTIME_HOME" || true
        exit 1
    fi

    exec gosu "$APP_USER" "$0" "$@"
fi

exec java -jar /app/solon-claw.jar "$@"
