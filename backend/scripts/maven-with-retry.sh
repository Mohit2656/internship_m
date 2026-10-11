#!/bin/sh
# Retry the complete Maven invocation, retaining downloaded artifacts in .m2.
# Four attempts with exponential backoff: 30, 60, then 120 seconds.
attempt=1
max_attempts=4
delay=30

while :; do
    printf 'Maven attempt %s/%s\n' "$attempt" "$max_attempts"
    # -U retries missing releases even after a cached failed repository request.
    if mvn -B --no-transfer-progress -U "$@"; then
        printf 'Maven succeeded on attempt %s/%s\n' "$attempt" "$max_attempts"
        exit 0
    else
        result=$?
    fi

    if [ "$attempt" -ge "$max_attempts" ]; then
        printf 'Maven failed after %s attempts (exit %s); stopping.\n' "$max_attempts" "$result" >&2
        exit "$result"
    fi

    printf 'Maven attempt %s failed (exit %s); retrying in %s seconds.\n' "$attempt" "$result" "$delay" >&2
    sleep "$delay" || exit 1
    attempt=$((attempt + 1))
    delay=$((delay * 2))
done
