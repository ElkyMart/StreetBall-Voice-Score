#!/usr/bin/env bash
set -euo pipefail

ROOT_DIR="$(cd "$(dirname "${BASH_SOURCE[0]}")/.." && pwd)"
GRADLE_USER_HOME="${GRADLE_USER_HOME:-$ROOT_DIR/.gradle-home}"

audio_file=""
expected_file=""
transcript_file=""
model="${WHISPER_MODEL:-base}"

usage() {
  cat <<'USAGE'
Usage:
  scripts/run-audio-regression.sh \
    --audio "/path/to/Recording 1.flac" \
    --expected "/path/to/recording1.expected.txt"

Optional:
  --transcript "/path/to/transcript.srt"   # skip whisper, use existing transcript
  --model base|small|medium|large          # whisper model (default: base)

Environment:
  WHISPER_MODEL  Default model for whisper CLI.

Notes:
  - Requires ffmpeg.
  - Requires either:
      1) whisper CLI in PATH, or
      2) --transcript pointing to an existing transcript file.
USAGE
}

while [[ $# -gt 0 ]]; do
  case "$1" in
    --audio)
      audio_file="${2:-}"
      shift 2
      ;;
    --expected)
      expected_file="${2:-}"
      shift 2
      ;;
    --transcript)
      transcript_file="${2:-}"
      shift 2
      ;;
    --model)
      model="${2:-}"
      shift 2
      ;;
    --help|-h)
      usage
      exit 0
      ;;
    *)
      echo "Unknown argument: $1" >&2
      usage
      exit 1
      ;;
  esac
done

if [[ -z "$expected_file" ]]; then
  echo "--expected is required." >&2
  usage
  exit 1
fi

if [[ ! -f "$expected_file" ]]; then
  echo "Expected sequence file not found: $expected_file" >&2
  exit 1
fi

if [[ -z "$transcript_file" ]]; then
  if [[ -z "$audio_file" ]]; then
    echo "Provide --audio or --transcript." >&2
    usage
    exit 1
  fi
  if [[ ! -f "$audio_file" ]]; then
    echo "Audio file not found: $audio_file" >&2
    exit 1
  fi
fi

if ! command -v ffmpeg >/dev/null 2>&1; then
  echo "ffmpeg is required but not installed." >&2
  exit 1
fi

work_dir="$(mktemp -d -t score-audio-reg-XXXXXX)"
cleanup() {
  rm -rf "$work_dir"
}
trap cleanup EXIT

if [[ -z "$transcript_file" ]]; then
  if ! command -v whisper >/dev/null 2>&1; then
    cat <<'EOF' >&2
whisper CLI not found in PATH.
Install one of:
  pip install -U openai-whisper
or generate transcript elsewhere and run with:
  --transcript /path/to/transcript.srt
EOF
    exit 1
  fi

  wav_file="$work_dir/input.wav"
  ffmpeg -hide_banner -loglevel error -y \
    -i "$audio_file" \
    -ac 1 -ar 16000 \
    "$wav_file"

  whisper "$wav_file" \
    --language en \
    --task transcribe \
    --model "$model" \
    --output_dir "$work_dir" \
    --output_format srt \
    --fp16 False >/dev/null

  base_name="$(basename "$wav_file")"
  transcript_file="$work_dir/${base_name%.*}.srt"
fi

if [[ ! -f "$transcript_file" ]]; then
  echo "Transcript file not found: $transcript_file" >&2
  exit 1
fi

echo "Transcript: $transcript_file"
echo "Expected sequence: $expected_file"

(
  cd "$ROOT_DIR"
  AUDIO_TRANSCRIPT_PATH="$transcript_file" \
  AUDIO_EXPECTED_SEQUENCE_PATH="$expected_file" \
  GRADLE_USER_HOME="$GRADLE_USER_HOME" \
  ./gradlew :app:testDebugUnitTest \
    --tests '*AudioTranscriptRegressionTest' \
    --no-daemon
)

echo "Audio regression passed."
