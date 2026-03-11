#!/usr/bin/env python3
"""CLI entry point for TDMS derived payload generation."""

from __future__ import annotations

import argparse
from pathlib import Path

from tdms_derive.pipeline import run_preprocessor


def build_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Generate derived payloads from real TDMS files.")
    parser.add_argument("--shot", type=int, required=True, help="Shot number to process")
    parser.add_argument("--input-root", default="data/TUBE", help="Root directory containing TDMS shot folders")
    parser.add_argument("--output-root", default="data/derived", help="Root directory for derived output")
    parser.add_argument("--stability-checks", type=int, default=2, help="Number of unchanged size/mtime checks")
    parser.add_argument("--stability-wait-ms", type=int, default=100, help="Wait between stability checks in milliseconds")
    return parser


def main() -> None:
    parser = build_parser()
    args = parser.parse_args()
    bundle = run_preprocessor(
        shot_no=args.shot,
        input_root=Path(args.input_root),
        output_root=Path(args.output_root),
        stable_checks=args.stability_checks,
        stable_wait_ms=args.stability_wait_ms,
    )
    print(f"generated {bundle.output_dir}")


if __name__ == "__main__":
    main()
