#!/usr/bin/env bash
set -euo pipefail

repo_root="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
cd "$repo_root"

mkdir -p run build/deployment
printf 'eula=true\n' > run/eula.txt
smoke_dir="$(mktemp -d)"
input_pipe="$smoke_dir/server-input"
server_log="$repo_root/build/deployment/server-smoke.log"
mkfifo "$input_pipe"
server_pid=""

cleanup() {
    exec 3>&- 2>/dev/null || true
    if [[ -n "$server_pid" ]] && kill -0 "$server_pid" 2>/dev/null; then
        kill "$server_pid" 2>/dev/null || true
    fi
    rm -rf "$smoke_dir"
}
trap cleanup EXIT

./gradlew runServer --no-daemon < "$input_pipe" > "$server_log" 2>&1 &
server_pid=$!
exec 3>"$input_pipe"

ready=false
for _ in {1..180}; do
    if grep -Fq 'Done (' "$server_log" \
            && grep -Fq 'migration marked complete' "$server_log" \
            && grep -Fq 'ICyou camera limits loaded' "$server_log" \
            && grep -Fq 'ICyou camera system is idle' "$server_log"; then
        ready=true
        break
    fi
    if ! kill -0 "$server_pid" 2>/dev/null; then
        break
    fi
    sleep 1
done

if [[ "$ready" != true ]]; then
    echo 'Dedicated-server smoke test did not reach a healthy ready state.'
    cat "$server_log"
    exit 1
fi

printf 'stop\n' >&3
exec 3>&-
for _ in {1..60}; do
    if ! kill -0 "$server_pid" 2>/dev/null; then
        wait "$server_pid"
        server_pid=""
        echo 'Dedicated-server smoke test passed.'
        exit 0
    fi
    sleep 1
done

echo 'Dedicated-server smoke test did not stop cleanly.'
cat "$server_log"
exit 1
