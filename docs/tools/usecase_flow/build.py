#!/usr/bin/env python3
"""List, validate, and generate SkyGo Use Case diagrams and DOCX files."""

from __future__ import annotations

import argparse
import importlib.util
import os
import re
import subprocess
import sys
import tempfile
from collections import defaultdict
from dataclasses import dataclass, field
from pathlib import Path

try:
    sys.stdout.reconfigure(encoding="utf-8", errors="replace")
    sys.stderr.reconfigure(encoding="utf-8", errors="replace")
except AttributeError:
    pass

ROOT = Path(__file__).resolve().parents[3]
SPEC_ROOT = ROOT / "docs" / "usecase_spec"
OUTPUT_ROOT = ROOT / "docs" / "build" / "usecase-flow"
TOOLS = Path(__file__).resolve().parent
ACTGEN = TOOLS / "diagram_gen" / "actgen.py"
SEQGEN = TOOLS / "diagram_gen" / "seqgen.py"
MD2DOCX = TOOLS / "doc_gen" / "md2docx.py"

PREFIX_TO_MODULE = {
    "AUTH": "auth",
    "FLT": "flight",
    "BKG": "booking",
    "PAY": "payment",
}
ID_PATTERN = r"(?P<code>[A-Z][A-Z0-9]*-UC-\d{3})"
MD_RE = re.compile(rf"^{ID_PATTERN}_.+\.md$")
ACT_RE = re.compile(rf"^{ID_PATTERN}_activity\.act$")
SEQ_RE = re.compile(rf"^{ID_PATTERN}_sequence\.seq$")
CODE_RE = re.compile(r"^[A-Z][A-Z0-9]*-UC-\d{3}$")
BR_RE = re.compile(r"\*\*(BR-[A-Z][A-Z0-9]*-\d{3}):\*\*")
REF_RE = re.compile(r"\{\{ref:([^}]*)\}\}")
STEP_RE = re.compile(r"^(\d+(?:\.\d+)*)\.\s")
HEADER_LABELS = (
    "Use case:",
    "Mục đích:",
    "Mô tả:",
    "Actor:",
    "Tiền điều kiện:",
    "Hậu điều kiện:",
)


@dataclass
class Unit:
    module: str
    code: str
    md: list[Path] = field(default_factory=list)
    act: list[Path] = field(default_factory=list)
    seq: list[Path] = field(default_factory=list)

    @property
    def out_dir(self) -> Path:
        return OUTPUT_ROOT / self.module / self.code


def discover() -> tuple[dict[tuple[str, str], Unit], list[str]]:
    units: dict[tuple[str, str], Unit] = {}
    problems: list[str] = []
    if not SPEC_ROOT.is_dir():
        return units, [f"Không thấy thư mục nguồn: {SPEC_ROOT}"]

    patterns = ((MD_RE, "md"), (ACT_RE, "act"), (SEQ_RE, "seq"))
    for path in sorted(SPEC_ROOT.rglob("*")):
        if not path.is_file():
            continue
        rel = path.relative_to(SPEC_ROOT)
        if len(rel.parts) < 2:
            if path.suffix.lower() in (".act", ".seq"):
                problems.append(f"{rel}: sơ đồ phải nằm trong thư mục module")
            continue

        matched = False
        for pattern, attr in patterns:
            match = pattern.fullmatch(path.name)
            if not match:
                continue
            code = match.group("code")
            module = rel.parts[0]
            unit = units.setdefault((module, code), Unit(module, code))
            getattr(unit, attr).append(path)
            matched = True
            prefix = code.split("-", 1)[0]
            expected_module = PREFIX_TO_MODULE.get(prefix)
            if expected_module and module != expected_module:
                problems.append(
                    f"{rel}: prefix {prefix} thuộc module '{expected_module}', không phải '{module}'"
                )
            break

        if not matched and path.suffix.lower() in (".act", ".seq"):
            problems.append(
                f"{rel}: tên sơ đồ phải theo dạng <MODULE>-UC-NNN_activity.act "
                "hoặc <MODULE>-UC-NNN_sequence.seq"
            )

    by_code: dict[str, set[str]] = defaultdict(set)
    for module, code in units:
        by_code[code].add(module)
    for code, modules in sorted(by_code.items()):
        if len(modules) > 1:
            problems.append(f"{code} xuất hiện ở nhiều module: {', '.join(sorted(modules))}")

    for unit in units.values():
        for attr in ("md", "act", "seq"):
            files = getattr(unit, attr)
            if len(files) > 1:
                names = ", ".join(str(p.relative_to(SPEC_ROOT)) for p in files)
                problems.append(f"{unit.code}: có nhiều file .{attr}: {names}")
    return units, problems


def resolve(units: dict[tuple[str, str], Unit], targets: list[str]) -> list[Unit]:
    if not targets or targets == ["all"]:
        return sorted(units.values(), key=lambda unit: (unit.module, unit.code))

    chosen: list[Unit] = []
    for target in targets:
        if CODE_RE.fullmatch(target):
            found = [u for u in units.values() if u.code == target]
            if not found:
                raise SystemExit(f"Không thấy {target}. Dùng 'list' để xem nguồn đã có.")
            chosen.extend(found)
        else:
            found = [u for u in units.values() if u.module == target]
            if not found:
                valid = ", ".join(sorted({u.module for u in units.values()})) or "chưa có module nào"
                raise SystemExit(f"Không thấy module '{target}'. Module có nguồn: {valid}.")
            chosen.extend(found)

    unique = {(unit.module, unit.code): unit for unit in chosen}
    return sorted(unique.values(), key=lambda unit: (unit.module, unit.code))


def split_table_row(line: str) -> list[str]:
    value = line.strip()
    if value.startswith("|"):
        value = value[1:]
    if value.endswith("|"):
        value = value[:-1]
    return [cell.strip() for cell in re.split(r"(?<!\\)\|", value)]


def is_separator(cells: list[str]) -> bool:
    return bool(cells) and all(re.fullmatch(r":?-{3,}:?", cell) for cell in cells)


def parse_sections(text: str) -> dict[str, list[str]]:
    sections: dict[str, list[str]] = {}
    current: str | None = None
    for line in text.splitlines():
        if line.startswith("### "):
            current = line[4:].strip()
            sections[current] = []
        elif current is not None:
            sections[current].append(line)
    return sections


def table_rows(lines: list[str]) -> list[list[str]]:
    rows = []
    for line in lines:
        if line.strip().startswith("|"):
            cells = split_table_row(line)
            if not is_separator(cells):
                rows.append(cells)
    return rows


def get_defined_ids(units: dict[tuple[str, str], Unit]) -> tuple[set[str], set[str]]:
    business_rules: set[str] = set()
    use_cases = {unit.code for unit in units.values() if unit.md}
    files = [SPEC_ROOT / "00-module.md", *SPEC_ROOT.rglob("rules.md")]
    for path in files:
        if path.is_file():
            content = path.read_text(encoding="utf-8")
            business_rules.update(BR_RE.findall(content))
            if path.name == "00-module.md":
                use_cases.update(re.findall(r"\b[A-Z][A-Z0-9]*-UC-\d{3}\b", content))
    return business_rules, use_cases


def validate_markdown(
    unit: Unit, defined_rules: set[str], defined_ucs: set[str]
) -> list[str]:
    path = unit.md[0]
    text = path.read_text(encoding="utf-8")
    sections = parse_sections(text)
    errors: list[str] = []

    headings = list(sections)
    if len(headings) < 5:
        errors.append("thiếu mục heading Use Case, mô tả, luồng chính, luồng phụ hoặc biểu đồ")
        return errors
    if not headings[0].startswith(unit.code + "_"):
        errors.append(f"heading đầu tiên phải bắt đầu bằng '{unit.code}_'")
    if headings[1] != f"Mô tả use case {unit.code}":
        errors.append(f"heading thứ hai phải là 'Mô tả use case {unit.code}'")
    for required in ("Luồng sự kiện chính", "Luồng sự kiện phụ", "Biểu đồ"):
        if required not in sections:
            errors.append(f"thiếu mục '### {required}'")

    header_rows = table_rows(sections.get(f"Mô tả use case {unit.code}", []))
    labels = [
        row[0].replace("**", "")
        for row in header_rows
        if row and row[0].replace("**", "") not in ("Trường", "Field")
    ]
    if labels != list(HEADER_LABELS):
        errors.append(f"header phải có đúng 6 dòng theo STANDARD.md; hiện có: {labels}")
    for row in header_rows:
        if len(row) != 2:
            errors.append(f"hàng header cần đúng 2 cột: {row}")

    main_rows = table_rows(sections.get("Luồng sự kiện chính", []))
    alt_rows = table_rows(sections.get("Luồng sự kiện phụ", []))
    main_steps: list[str] = []
    alt_steps: list[str] = []
    for row in main_rows:
        if len(row) != 2:
            errors.append(f"hàng luồng chính cần đúng 2 cột: {row}")
            continue
        for cell in row:
            match = STEP_RE.match(REF_RE.sub("", cell))
            if match:
                main_steps.append(match.group(1))
    for row in alt_rows:
        if len(row) != 2:
            errors.append(f"hàng luồng phụ cần đúng 2 cột: {row}")
            continue
        for cell in row:
            match = STEP_RE.match(REF_RE.sub("", cell))
            if match:
                alt_steps.append(match.group(1))

    expected = [str(i) for i in range(1, len(main_steps) + 1)]
    if main_steps != expected:
        errors.append(f"số bước chính phải liên tục từ 1; hiện có: {main_steps}")
    seen_alt: set[str] = set()
    for step in alt_steps:
        parts = step.split(".")
        if (
            len(parts) not in (2, 3)
            or not parts[0].isdigit()
            or not 1 <= int(parts[0]) <= len(main_steps)
        ):
            errors.append(f"bước phụ '{step}' phải rẽ từ một bước chính hiện có")
            continue
        if step in seen_alt:
            errors.append(f"trùng số bước phụ '{step}'")
        if len(parts) == 3 and ".".join(parts[:2]) not in seen_alt:
            errors.append(f"bước phụ '{step}' đứng trước nhánh {'.'.join(parts[:2])}")
        seen_alt.add(step)

    missing_refs: set[str] = set()
    for block in REF_RE.findall(text):
        for ref in (value.strip() for value in block.split(",")):
            if ref.startswith("BR-") and ref not in defined_rules:
                missing_refs.add(ref)
            elif CODE_RE.fullmatch(ref) and ref not in defined_ucs:
                missing_refs.add(ref)
            elif not ref.startswith("BR-") and not CODE_RE.fullmatch(ref):
                missing_refs.add(ref)
    if missing_refs:
        errors.append("mã tham chiếu chưa được định nghĩa: " + ", ".join(sorted(missing_refs)))
    return errors


def run(command: list[object]) -> tuple[int, str]:
    env = dict(os.environ, PYTHONIOENCODING="utf-8", PYTHONUTF8="1")
    result = subprocess.run(
        [sys.executable, *map(str, command)],
        capture_output=True,
        text=True,
        encoding="utf-8",
        errors="replace",
        env=env,
    )
    return result.returncode, (result.stdout + result.stderr).strip()


def check_unit(
    unit: Unit, defined_rules: set[str], defined_ucs: set[str]
) -> tuple[bool, list[str]]:
    errors: list[str] = []
    if unit.md:
        errors.extend(validate_markdown(unit, defined_rules, defined_ucs))

    with tempfile.TemporaryDirectory(prefix="skygo-usecase-") as temp_dir:
        for tool, specs in ((ACTGEN, unit.act), (SEQGEN, unit.seq)):
            for spec in specs:
                rc, output = run([tool, spec, "-o", temp_dir, "--no-png"])
                if rc != 0:
                    errors.append(f"{spec.name}: {output}")
    return not errors, errors


def cmd_list(_args: argparse.Namespace) -> int:
    units, problems = discover()
    if not units:
        print(f"Chưa có Use Case nguồn dưới {SPEC_ROOT}")
    else:
        print(f"{'module':<18}{'Use Case':<20}{'md':<6}{'act':<6}{'seq':<6}")
        for unit in sorted(units.values(), key=lambda item: (item.module, item.code)):
            mark = lambda files: "có" if files else "-"
            print(
                f"{unit.module:<18}{unit.code:<20}"
                f"{mark(unit.md):<6}{mark(unit.act):<6}{mark(unit.seq):<6}"
            )
    for problem in problems:
        print("CẢNH BÁO:", problem)
    return 1 if problems else 0


def cmd_check(args: argparse.Namespace) -> int:
    units, problems = discover()
    defined_rules, defined_ucs = get_defined_ids(units)
    for problem in problems:
        print("LỖI:", problem)
    failed = bool(problems)
    for unit in resolve(units, args.targets):
        print(f"{unit.module}/{unit.code}")
        ok, errors = check_unit(unit, defined_rules, defined_ucs)
        if errors:
            failed = True
            for error in errors:
                print("  LỖI:", error)
        else:
            print("  OK")
    print("\nCó lỗi." if failed else "\nNguồn Use Case hợp lệ theo các kiểm tra cấu trúc.")
    return 1 if failed else 0


def cmd_build(args: argparse.Namespace) -> int:
    units, problems = discover()
    if problems:
        for problem in problems:
            print("LỖI:", problem)
        print("Sửa tên file hoặc cấu trúc nguồn rồi build lại.")
        return 1

    selected = resolve(units, args.targets)
    if not selected:
        print("Chưa có nguồn Use Case để build.")
        return 0

    no_png = args.no_png
    wants_diagrams = args.only in (None, "diagrams")
    wants_docx = args.only in (None, "docx")
    if wants_docx and importlib.util.find_spec("docx") is None:
        print("Thiếu python-docx. Cài bằng: pip install python-docx")
        return 1
    if wants_diagrams and not no_png and importlib.util.find_spec("playwright") is None:
        no_png = True
        print("Chưa cài playwright; sẽ sinh draw.io/SVG và bỏ qua PNG. Dùng --no-png để ẩn thông báo này.\n")

    failed: list[str] = []
    for unit in selected:
        print(f"{unit.module}/{unit.code}")
        unit.out_dir.mkdir(parents=True, exist_ok=True)
        if wants_diagrams:
            if not unit.act and not unit.seq:
                print("  Chưa có nguồn Activity/Sequence.")
            for tool, specs in ((ACTGEN, unit.act), (SEQGEN, unit.seq)):
                for spec in specs:
                    command: list[object] = [tool, spec, "-o", unit.out_dir]
                    if no_png:
                        command.append("--no-png")
                    rc, output = run(command)
                    print(f"  {'OK' if rc == 0 else 'LỖI'} {spec.name}")
                    if output:
                        for line in output.splitlines():
                            print("    " + line)
                    if rc != 0:
                        failed.append(f"{unit.code}/{spec.name}")
        if wants_docx:
            if not unit.md:
                print("  Thiếu file Markdown Use Case; bỏ qua DOCX.")
            for md in unit.md:
                rc, output = run(
                    [MD2DOCX, md, "--out", unit.out_dir, "--assets", unit.out_dir]
                )
                print(f"  {'OK' if rc == 0 else 'LỖI'} {md.name}")
                if output:
                    for line in output.splitlines():
                        print("    " + line)
                if rc != 0:
                    failed.append(f"{unit.code}/{md.name}")
                for specs, kind in ((unit.act, "activity"), (unit.seq, "sequence")):
                    image_path = unit.out_dir / f"{unit.code}_{kind}.png"
                    if specs and not image_path.exists():
                        print(f"    ! DOCX chưa có ảnh {kind}: thiếu {image_path.name}")
    print(f"\nĐầu ra: {OUTPUT_ROOT}")
    if failed:
        print("Lỗi:", ", ".join(failed))
        return 1
    return 0


def make_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="Kiểm tra và sinh sơ đồ Use Case SkyGo.")
    subparsers = parser.add_subparsers(dest="command", required=True)
    subparsers.add_parser("list", help="liệt kê UC và các nguồn hiện có")
    for command, help_text in (
        ("check", "kiểm tra format UC, tham chiếu và cú pháp sơ đồ"),
        ("build", "sinh sơ đồ Activity/Sequence và xuất DOCX"),
    ):
        child = subparsers.add_parser(command, help=help_text)
        child.add_argument(
            "targets",
            nargs="*",
            help="all, một mã UC (ví dụ BKG-UC-001) hoặc tên module (ví dụ booking)",
        )
        if command == "build":
            child.add_argument(
                "--only",
                choices=("diagrams", "docx"),
                help="chỉ sinh sơ đồ hoặc chỉ xuất Word",
            )
            child.add_argument("--no-png", action="store_true", help="chỉ sinh draw.io và SVG")
    return parser


def main() -> int:
    args = make_parser().parse_args()
    if args.command == "list":
        return cmd_list(args)
    if args.command == "check":
        return cmd_check(args)
    if args.command == "build":
        return cmd_build(args)
    return 2


if __name__ == "__main__":
    raise SystemExit(main())
