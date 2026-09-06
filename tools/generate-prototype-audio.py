#!/usr/bin/env python3
"""產生 Sango Prototype 使用的原創程序合成 BGM 與音效。"""

from __future__ import annotations

import argparse
import math
import random
import shutil
import subprocess
import tempfile
import wave
from pathlib import Path
from typing import Callable

SAMPLE_RATE = 44_100
MAX_INT16 = 32_767


def clamp(value: float, minimum: float = -1.0, maximum: float = 1.0) -> float:
    return max(minimum, min(maximum, value))


def smooth_envelope(time_seconds: float, duration_seconds: float, attack: float, release: float) -> float:
    attack_gain = min(1.0, time_seconds / max(0.001, attack))
    release_gain = min(1.0, (duration_seconds - time_seconds) / max(0.001, release))
    return max(0.0, min(attack_gain, release_gain))


def midi_frequency(midi_note: int) -> float:
    return 440.0 * (2.0 ** ((midi_note - 69) / 12.0))


def write_wave(path: Path, duration_seconds: float, generator: Callable[[float], float]) -> None:
    path.parent.mkdir(parents=True, exist_ok=True)
    frame_count = int(duration_seconds * SAMPLE_RATE)
    with wave.open(str(path), "wb") as wave_file:
        wave_file.setnchannels(1)
        wave_file.setsampwidth(2)
        wave_file.setframerate(SAMPLE_RATE)
        frames = bytearray()
        for frame_index in range(frame_count):
            time_seconds = frame_index / SAMPLE_RATE
            sample_value = clamp(generator(time_seconds))
            integer_value = int(sample_value * MAX_INT16)
            frames.extend(integer_value.to_bytes(2, byteorder="little", signed=True))
        wave_file.writeframes(frames)


def convert_to_ogg(wave_path: Path, ogg_path: Path) -> None:
    ffmpeg_path = shutil.which("ffmpeg")
    if ffmpeg_path is None:
        raise RuntimeError("找不到 ffmpeg，無法輸出 OGG。")
    ogg_path.parent.mkdir(parents=True, exist_ok=True)
    subprocess.run(
        [
            ffmpeg_path,
            "-hide_banner",
            "-loglevel",
            "error",
            "-y",
            "-i",
            str(wave_path),
            "-c:a",
            "libvorbis",
            "-q:a",
            "4",
            str(ogg_path),
        ],
        check=True,
    )


def plucked_tone(time_seconds: float, start_seconds: float, duration_seconds: float, frequency: float) -> float:
    local_time = time_seconds - start_seconds
    if local_time < 0.0 or local_time >= duration_seconds:
        return 0.0
    envelope = math.exp(-3.2 * local_time / duration_seconds)
    body = math.sin(2.0 * math.pi * frequency * local_time)
    harmonic = 0.35 * math.sin(2.0 * math.pi * frequency * 2.01 * local_time)
    overtone = 0.12 * math.sin(2.0 * math.pi * frequency * 3.0 * local_time)
    return (body + harmonic + overtone) * envelope


def flute_tone(time_seconds: float, start_seconds: float, duration_seconds: float, frequency: float) -> float:
    local_time = time_seconds - start_seconds
    if local_time < 0.0 or local_time >= duration_seconds:
        return 0.0
    envelope = smooth_envelope(local_time, duration_seconds, 0.12, 0.30)
    vibrato = 1.0 + 0.003 * math.sin(2.0 * math.pi * 5.2 * local_time)
    phase = 2.0 * math.pi * frequency * vibrato * local_time
    return envelope * (math.sin(phase) + 0.18 * math.sin(phase * 2.0))


def low_drum(time_seconds: float, start_seconds: float, strength: float = 1.0) -> float:
    local_time = time_seconds - start_seconds
    if local_time < 0.0 or local_time >= 0.42:
        return 0.0
    frequency = 92.0 - 48.0 * local_time
    envelope = math.exp(-9.0 * local_time)
    return math.sin(2.0 * math.pi * frequency * local_time) * envelope * strength


def build_loop(duration_seconds: float, tempo_bpm: float, melody_notes: list[int], chord_roots: list[int]) -> Callable[[float], float]:
    beat_seconds = 60.0 / tempo_bpm
    melody_step = beat_seconds
    chord_length = beat_seconds * 4.0

    def generator(time_seconds: float) -> float:
        sample = 0.0
        chord_index = int(time_seconds / chord_length) % len(chord_roots)
        chord_start = math.floor(time_seconds / chord_length) * chord_length
        root_note = chord_roots[chord_index]
        for interval in (0, 7, 12):
            sample += 0.07 * flute_tone(
                time_seconds,
                chord_start,
                chord_length,
                midi_frequency(root_note + interval),
            )

        melody_index = int(time_seconds / melody_step) % len(melody_notes)
        melody_start = math.floor(time_seconds / melody_step) * melody_step
        sample += 0.19 * plucked_tone(
            time_seconds,
            melody_start,
            melody_step * 0.92,
            midi_frequency(melody_notes[melody_index]),
        )

        beat_index = int(time_seconds / beat_seconds)
        beat_start = beat_index * beat_seconds
        drum_strength = 1.0 if beat_index % 4 == 0 else 0.45
        sample += 0.16 * low_drum(time_seconds, beat_start, drum_strength)
        return sample * 0.82

    return generator


def sine_sweep(
    time_seconds: float,
    duration_seconds: float,
    start_frequency: float,
    end_frequency: float,
) -> float:
    progress = min(1.0, max(0.0, time_seconds / duration_seconds))
    frequency = start_frequency + (end_frequency - start_frequency) * progress
    return math.sin(2.0 * math.pi * frequency * time_seconds)


def make_sfx_generators() -> dict[str, tuple[float, Callable[[float], float]]]:
    random_generator = random.Random(0x53414E47)
    noise_table = [random_generator.uniform(-1.0, 1.0) for _ in range(SAMPLE_RATE)]

    def click(time_seconds: float) -> float:
        return 0.33 * sine_sweep(time_seconds, 0.11, 880.0, 520.0) * math.exp(-28.0 * time_seconds)

    def confirm(time_seconds: float) -> float:
        return (
            0.24 * plucked_tone(time_seconds, 0.0, 0.25, 523.25)
            + 0.22 * plucked_tone(time_seconds, 0.11, 0.28, 659.25)
        )

    def cancel(time_seconds: float) -> float:
        return 0.24 * sine_sweep(time_seconds, 0.25, 440.0, 220.0) * math.exp(-7.0 * time_seconds)

    def command_success(time_seconds: float) -> float:
        notes = ((0.0, 392.0), (0.13, 493.88), (0.26, 587.33))
        return sum(0.17 * plucked_tone(time_seconds, start, 0.34, frequency) for start, frequency in notes)

    def command_error(time_seconds: float) -> float:
        return (
            0.19 * sine_sweep(time_seconds, 0.34, 220.0, 145.0)
            + 0.11 * math.sin(2.0 * math.pi * 73.0 * time_seconds)
        ) * math.exp(-5.2 * time_seconds)

    def end_month(time_seconds: float) -> float:
        sample = 0.25 * low_drum(time_seconds, 0.0, 1.0)
        sample += 0.16 * plucked_tone(time_seconds, 0.22, 0.65, 293.66)
        sample += 0.17 * plucked_tone(time_seconds, 0.43, 0.65, 392.0)
        return sample

    def save_complete(time_seconds: float) -> float:
        return (
            0.17 * plucked_tone(time_seconds, 0.0, 0.42, 659.25)
            + 0.19 * plucked_tone(time_seconds, 0.16, 0.44, 783.99)
        )

    def battle_alert(time_seconds: float) -> float:
        return (
            0.30 * low_drum(time_seconds, 0.0, 1.0)
            + 0.28 * low_drum(time_seconds, 0.38, 0.9)
            + 0.23 * low_drum(time_seconds, 0.76, 0.85)
        )

    def battle_impact(time_seconds: float) -> float:
        noise_index = min(len(noise_table) - 1, int(time_seconds * SAMPLE_RATE))
        metallic = math.sin(2.0 * math.pi * 940.0 * time_seconds)
        metallic += 0.55 * math.sin(2.0 * math.pi * 1340.0 * time_seconds)
        return (0.35 * metallic + 0.30 * noise_table[noise_index]) * math.exp(-12.0 * time_seconds)

    def city_captured(time_seconds: float) -> float:
        return (
            0.22 * low_drum(time_seconds, 0.0, 1.0)
            + 0.18 * plucked_tone(time_seconds, 0.18, 0.75, 261.63)
            + 0.20 * plucked_tone(time_seconds, 0.45, 0.75, 392.0)
            + 0.20 * plucked_tone(time_seconds, 0.72, 0.75, 523.25)
        )

    def objective_success(time_seconds: float) -> float:
        notes = ((0.0, 392.0), (0.18, 493.88), (0.36, 587.33), (0.62, 783.99))
        return sum(0.15 * plucked_tone(time_seconds, start, 0.58, frequency) for start, frequency in notes)

    def objective_failed(time_seconds: float) -> float:
        notes = ((0.0, 293.66), (0.24, 246.94), (0.50, 196.0))
        return sum(0.16 * plucked_tone(time_seconds, start, 0.68, frequency) for start, frequency in notes)

    return {
        "ui_click": (0.14, click),
        "confirm": (0.44, confirm),
        "cancel": (0.30, cancel),
        "command_success": (0.70, command_success),
        "command_error": (0.46, command_error),
        "end_month": (1.10, end_month),
        "save_complete": (0.72, save_complete),
        "battle_alert": (1.20, battle_alert),
        "battle_impact": (0.48, battle_impact),
        "city_captured": (1.55, city_captured),
        "objective_success": (1.55, objective_success),
        "objective_failed": (1.55, objective_failed),
    }


def main() -> None:
    parser = argparse.ArgumentParser()
    parser.add_argument(
        "--assets",
        type=Path,
        default=Path(__file__).resolve().parents[1] / "assets",
    )
    arguments = parser.parse_args()

    assets_directory = arguments.assets.resolve()
    music_directory = assets_directory / "audio" / "music"
    sound_directory = assets_directory / "audio" / "sfx"

    lobby_tempo_bpm = 72.0
    strategy_tempo_bpm = 88.0
    loop_beats = 48.0
    lobby_duration_seconds = loop_beats * 60.0 / lobby_tempo_bpm
    strategy_duration_seconds = loop_beats * 60.0 / strategy_tempo_bpm

    lobby_generator = build_loop(
        lobby_duration_seconds,
        lobby_tempo_bpm,
        [62, 65, 67, 69, 67, 65, 62, 60, 62, 67, 69, 72, 69, 67, 65, 62],
        [50, 48, 45, 43, 50, 45],
    )
    strategy_generator = build_loop(
        strategy_duration_seconds,
        strategy_tempo_bpm,
        [57, 60, 62, 64, 62, 60, 57, 55, 57, 62, 64, 67, 64, 62, 60, 57],
        [45, 43, 48, 40, 45, 48],
    )

    with tempfile.TemporaryDirectory(prefix="sango-audio-") as temporary_directory:
        temporary_path = Path(temporary_directory)
        music_definitions = {
            "lobby_theme": (lobby_duration_seconds, lobby_generator),
            "strategy_theme": (strategy_duration_seconds, strategy_generator),
        }
        for file_name, (duration_seconds, generator) in music_definitions.items():
            wave_path = temporary_path / f"{file_name}.wav"
            write_wave(wave_path, duration_seconds, generator)
            convert_to_ogg(wave_path, music_directory / f"{file_name}.ogg")

        for file_name, (duration_seconds, generator) in make_sfx_generators().items():
            wave_path = temporary_path / f"{file_name}.wav"
            write_wave(wave_path, duration_seconds, generator)
            convert_to_ogg(wave_path, sound_directory / f"{file_name}.ogg")

    print(f"已產生 Prototype Audio：{assets_directory / 'audio'}")


if __name__ == "__main__":
    main()
