#!/usr/bin/env python3
"""Create a minimal Soluna UI autotest asset project from templates."""

from __future__ import annotations

import argparse
import re
from pathlib import Path


SCRIPT_DIR = Path(__file__).resolve().parent
SKILL_DIR = SCRIPT_DIR.parent
TEMPLATE_ROOT = SKILL_DIR / "assets" / "templates" / "asset-project"


def parse_args() -> argparse.Namespace:
    parser = argparse.ArgumentParser(description=__doc__)
    parser.add_argument("--output", required=True, help="Directory to create the asset project in.")
    parser.add_argument("--project-id", required=True, help="Stable asset project id.")
    parser.add_argument("--app-id", required=True, help="Target mobile app package or bundle id.")
    parser.add_argument("--app-name", required=True, help="Human-readable app name.")
    parser.add_argument(
        "--product-model",
        help="Product model or public app display name shown in reports and DingTalk notifications. Defaults to --app-name.",
    )
    parser.add_argument(
        "--platform",
        required=True,
        choices=("android", "ios"),
        help="Platform starter assets to generate.",
    )
    parser.add_argument("--udid", help="Device UDID for a single-platform project.")
    parser.add_argument("--runner-min-version", default="0.1.0", help="Minimum Soluna runner version.")
    parser.add_argument("--force", action="store_true", help="Overwrite files that already exist.")
    return parser.parse_args()


def validate_identifier(value: str, label: str) -> None:
    if not re.fullmatch(r"[A-Za-z0-9_.-]+", value):
        raise SystemExit(f"{label} may contain only letters, digits, dots, underscores, and hyphens: {value}")


def render(text: str, values: dict[str, str]) -> str:
    for key, value in values.items():
        text = text.replace("{{" + key + "}}", value)
        text = text.replace("__" + key + "__", value)
    return text


def write_rendered_files(output: Path, values: dict[str, str], force: bool) -> list[Path]:
    if not TEMPLATE_ROOT.is_dir():
        raise SystemExit(f"Template root not found: {TEMPLATE_ROOT}")

    written: list[Path] = []
    for template in sorted(TEMPLATE_ROOT.rglob("*.tpl")):
        relative = template.relative_to(TEMPLATE_ROOT)
        rendered_relative = Path(render(str(relative), values).removesuffix(".tpl"))
        target = output / rendered_relative
        if target.exists() and not force:
            raise SystemExit(f"Refusing to overwrite existing file without --force: {target}")
        target.parent.mkdir(parents=True, exist_ok=True)
        target.write_text(render(template.read_text(encoding="utf-8"), values), encoding="utf-8")
        written.append(target)
    return written


def main() -> int:
    args = parse_args()
    validate_identifier(args.project_id, "--project-id")
    validate_identifier(args.app_id, "--app-id")

    primary_platform = args.platform
    primary_udid = args.udid or f"CHANGE_ME_{primary_platform.upper()}_UDID"
    platforms_yaml = f"      - {primary_platform}"

    values = {
        "PROJECT_ID": args.project_id,
        "APP_ID": args.app_id,
        "APP_NAME": args.app_name,
        "PRODUCT_MODEL": args.product_model or args.app_name,
        "PLATFORM": primary_platform,
        "UDID": primary_udid,
        "RUNNER_MIN_VERSION": args.runner_min_version,
        "PLATFORMS_YAML": platforms_yaml,
    }

    output = Path(args.output).expanduser().resolve()
    written = write_rendered_files(output, values, args.force)
    plan = output / "apps" / args.app_id / "plans" / f"{primary_platform}-smoke.yaml"

    print(f"Created Soluna asset project at: {output}")
    print(f"Files written: {len(written)}")
    print()
    print("Next steps:")
    print(f"  1. Edit device config: {output / 'devices' / primary_platform / (primary_udid + '.yaml')}")
    print("  2. Copy artifact templates to *.local.yaml if uploads are needed.")
    print(f"  3. Validate: soluna validate {plan}")
    print(f"  4. Run: soluna run {plan} --run-id {args.project_id}-smoke-001")
    return 0


if __name__ == "__main__":
    raise SystemExit(main())
