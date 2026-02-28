# Audio Regression Testing

This folder stores expected score timelines for real audio recordings.

## Run with a transcript file

```bash
scripts/run-audio-regression.sh \
  --transcript /path/to/transcript.srt \
  --expected testdata/audio/recording1.expected.txt
```

## Run from raw audio

```bash
scripts/run-audio-regression.sh \
  --audio "/home/emart/Projects/Score/sounds files/Recording 1.flac" \
  --expected testdata/audio/recording1.expected.txt
```

Requirements for raw audio mode:

- `ffmpeg`
- `whisper` CLI (`pip install -U openai-whisper`)

The script transcribes audio to SRT, then runs `AudioTranscriptRegressionTest`
to verify the accepted score sequence matches the expected timeline exactly.
