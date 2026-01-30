#!/bin/bash
#
# agi-cli.sh
# Command-line interface for AGI-Android OS
#
# Provides shell access to the AgentSystemService for testing and scripting.
#
# Usage:
#   agi-cli session create [--width W] [--height H] [--headless]
#   agi-cli session list
#   agi-cli session destroy <id>
#   agi-cli session capture <id> [output.png]
#   agi-cli session click <id> <x> <y>
#   agi-cli session type <id> <text>
#   agi-cli session launch <id> <package>
#   agi-cli shell <command>
#   agi-cli packages

set -e

# Default device serial (empty = any connected device)
DEVICE="${ANDROID_SERIAL:-}"
ADB="adb"
[[ -n "$DEVICE" ]] && ADB="adb -s $DEVICE"

# Service name
SERVICE="agent"

usage() {
    echo "AGI-Android OS CLI"
    echo ""
    echo "Usage: agi-cli <command> [args...]"
    echo ""
    echo "Session commands:"
    echo "  session create [--width W] [--height H] [--headless]"
    echo "  session list"
    echo "  session destroy <session_id>"
    echo "  session info <session_id>"
    echo ""
    echo "Interaction commands:"
    echo "  capture <session_id> [output.png]  - Capture screenshot"
    echo "  click <session_id> <x> <y>         - Tap at coordinates"
    echo "  type <session_id> <text>           - Type text"
    echo "  key <session_id> <keycode>         - Press key"
    echo "  drag <session_id> <x1> <y1> <x2> <y2>"
    echo ""
    echo "App commands:"
    echo "  launch <session_id> <package>      - Launch app"
    echo "  kill <session_id> <package>        - Force stop app"
    echo "  current <session_id>               - Get foreground app"
    echo ""
    echo "System commands:"
    echo "  shell <command>                    - Execute shell command"
    echo "  packages                           - List installed packages"
    echo "  install <apk_path>                 - Install APK"
    echo "  uninstall <package>                - Uninstall app"
    echo ""
    echo "Environment:"
    echo "  ANDROID_SERIAL  - Target device serial"
    exit 1
}

# Check device connection
check_device() {
    if ! $ADB shell true 2>/dev/null; then
        echo "Error: No device connected or ADB not available"
        exit 1
    fi
}

# Check service availability
check_service() {
    if ! $ADB shell service check $SERVICE 2>/dev/null | grep -q "Service $SERVICE"; then
        echo "Error: AgentSystemService not found on device"
        echo "Is this device running AGI-Android OS?"
        exit 1
    fi
}

# Call service method via app_process (simplified approach)
# In a real implementation, you'd use a helper app or direct binder calls
call_service() {
    local method="$1"
    shift
    # This is a placeholder - actual implementation would need:
    # 1. A helper app that exposes CLI commands, OR
    # 2. Direct binder transaction via cmd, OR
    # 3. A proper native CLI binary
    echo "Calling service method: $method $*"
    echo "(Note: Full implementation requires helper binary on device)"
}

# Main command dispatch
case "${1:-}" in
    session)
        check_device
        check_service
        case "${2:-}" in
            create)
                echo "Creating session..."
                call_service createSession "${@:3}"
                ;;
            list)
                echo "Active sessions:"
                call_service listSessions
                ;;
            destroy)
                [[ -z "${3:-}" ]] && usage
                echo "Destroying session: $3"
                call_service destroySession "$3"
                ;;
            info)
                [[ -z "${3:-}" ]] && usage
                call_service getSession "$3"
                ;;
            *)
                usage
                ;;
        esac
        ;;

    capture)
        check_device
        check_service
        [[ -z "${2:-}" ]] && usage
        SESSION_ID="$2"
        OUTPUT="${3:-screenshot.png}"
        echo "Capturing screenshot from session $SESSION_ID..."
        # Pull screenshot via temp file
        $ADB shell "cmd $SERVICE capture $SESSION_ID /data/local/tmp/agi_capture.png"
        $ADB pull /data/local/tmp/agi_capture.png "$OUTPUT"
        $ADB shell rm /data/local/tmp/agi_capture.png
        echo "Saved to: $OUTPUT"
        ;;

    click)
        check_device
        check_service
        [[ -z "${4:-}" ]] && usage
        echo "Clicking at ($3, $4) in session $2"
        call_service click "$2" "$3" "$4"
        ;;

    type)
        check_device
        check_service
        [[ -z "${3:-}" ]] && usage
        echo "Typing in session $2: $3"
        call_service type "$2" "$3"
        ;;

    key)
        check_device
        check_service
        [[ -z "${3:-}" ]] && usage
        echo "Pressing key $3 in session $2"
        call_service pressKey "$2" "$3"
        ;;

    drag)
        check_device
        check_service
        [[ -z "${6:-}" ]] && usage
        echo "Dragging from ($3,$4) to ($5,$6) in session $2"
        call_service drag "$2" "$3" "$4" "$5" "$6"
        ;;

    launch)
        check_device
        check_service
        [[ -z "${3:-}" ]] && usage
        echo "Launching $3 in session $2"
        call_service launchApp "$2" "$3"
        ;;

    kill)
        check_device
        check_service
        [[ -z "${3:-}" ]] && usage
        echo "Killing $3 in session $2"
        call_service killApp "$2" "$3"
        ;;

    current)
        check_device
        check_service
        [[ -z "${2:-}" ]] && usage
        call_service getCurrentApp "$2"
        ;;

    shell)
        check_device
        check_service
        [[ -z "${2:-}" ]] && usage
        call_service executeShell "${*:2}"
        ;;

    packages)
        check_device
        check_service
        call_service getInstalledPackages
        ;;

    install)
        check_device
        check_service
        [[ -z "${2:-}" ]] && usage
        # Push APK to device first
        APK_NAME=$(basename "$2")
        $ADB push "$2" "/data/local/tmp/$APK_NAME"
        call_service installApk "/data/local/tmp/$APK_NAME"
        ;;

    uninstall)
        check_device
        check_service
        [[ -z "${2:-}" ]] && usage
        call_service uninstallApp "$2"
        ;;

    help|--help|-h|"")
        usage
        ;;

    *)
        echo "Unknown command: $1"
        usage
        ;;
esac
