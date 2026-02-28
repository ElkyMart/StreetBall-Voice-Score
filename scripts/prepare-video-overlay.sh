#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
APP_ID="com.streetball.voicescore"
REMOTE_EXPORT_DIR="/sdcard/Android/data/${APP_ID}/files/Documents/exports"

ADB_BIN="${ROOT_DIR}/.android-sdk/platform-tools/adb"
if [[ ! -x "${ADB_BIN}" ]]; then
  ADB_BIN="adb"
fi

video_input=""
run_ffmpeg=0

for arg in "$@"; do
  case "$arg" in
    --run)
      run_ffmpeg=1
      ;;
    --help|-h)
      cat <<'USAGE'
Usage:
  scripts/prepare-video-overlay.sh [video_file] [--run]

What it does:
  1) Pulls latest score_timeline export files (CSV/SRT/ASS/notes) from connected device.
  2) Prints an ffmpeg command to burn ASS (or SRT fallback) onto your video.
  3) If --run is provided, executes ffmpeg command.
USAGE
      exit 0
      ;;
    *)
      if [[ -z "${video_input}" ]]; then
        video_input="$arg"
      else
        echo "Unexpected argument: $arg" >&2
        exit 1
      fi
      ;;
  esac
done

if ! "${ADB_BIN}" get-state >/dev/null 2>&1; then
  echo "No adb device detected. Connect and authorize phone first." >&2
  exit 1
fi

latest_remote_srt="$("${ADB_BIN}" shell "ls -1t ${REMOTE_EXPORT_DIR}/score_timeline_*.srt 2>/dev/null | head -n 1" | tr -d '\r' | tail -n 1)"
if [[ -z "${latest_remote_srt}" ]]; then
  echo "No SRT exports found on device at ${REMOTE_EXPORT_DIR}" >&2
  exit 1
fi

base_name="${latest_remote_srt##*/}"              # score_timeline_YYYYMMDD_HHMMSS.srt
stem="${base_name%.srt}"                          # score_timeline_YYYYMMDD_HHMMSS
remote_csv="${REMOTE_EXPORT_DIR}/${stem}.csv"
remote_srt="${REMOTE_EXPORT_DIR}/${stem}.srt"
remote_ass="${REMOTE_EXPORT_DIR}/${stem}.ass"
remote_notes="${REMOTE_EXPORT_DIR}/${stem}_video_notes.txt"

local_dir="${ROOT_DIR}/video_exports/${stem}"
mkdir -p "${local_dir}"

echo "Pulling export files to: ${local_dir}"
"${ADB_BIN}" pull "${remote_csv}" "${local_dir}/" >/dev/null
"${ADB_BIN}" pull "${remote_srt}" "${local_dir}/" >/dev/null
"${ADB_BIN}" pull "${remote_ass}" "${local_dir}/" >/dev/null || true
"${ADB_BIN}" pull "${remote_notes}" "${local_dir}/" >/dev/null || true

local_srt="${local_dir}/${stem}.srt"
local_ass="${local_dir}/${stem}.ass"
local_csv="${local_dir}/${stem}.csv"
local_notes="${local_dir}/${stem}_video_notes.txt"

echo "Pulled:"
echo "  ${local_csv}"
echo "  ${local_srt}"
if [[ -f "${local_ass}" ]]; then
  echo "  ${local_ass}"
fi
if [[ -f "${local_notes}" ]]; then
  echo "  ${local_notes}"
fi

overlay_filter="subtitles='${local_srt}'"
overlay_type="SRT subtitles"
if [[ -f "${local_ass}" ]]; then
  overlay_filter="ass='${local_ass}'"
  overlay_type="ASS styled subtitles"
fi

if [[ -z "${video_input}" ]]; then
  cat <<EOF

To burn score overlay onto a video (${overlay_type}):
  ffmpeg -i /path/to/input.mp4 -vf "${overlay_filter}" -c:a copy /path/to/output_with_score.mp4
EOF
  exit 0
fi

if [[ ! -f "${video_input}" ]]; then
  echo "Video file not found: ${video_input}" >&2
  exit 1
fi

video_abs="$(cd "$(dirname "${video_input}")" && pwd)/$(basename "${video_input}")"
video_stem="$(basename "${video_input}")"
video_stem="${video_stem%.*}"
output_video="${local_dir}/${video_stem}_with_score.mp4"

echo
echo "ffmpeg command (${overlay_type}):"
echo "  ffmpeg -y -i \"${video_abs}\" -vf \"${overlay_filter}\" -c:a copy \"${output_video}\""

if [[ "${run_ffmpeg}" -eq 1 ]]; then
  if ! command -v ffmpeg >/dev/null 2>&1; then
    echo "ffmpeg is not installed." >&2
    exit 1
  fi

  ffmpeg -y -i "${video_abs}" -vf "${overlay_filter}" -c:a copy "${output_video}"
  echo "Created: ${output_video}"
fi
