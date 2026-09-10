#!/usr/bin/env python3
"""Build a deterministic, auditable Sango strategy module package."""

from __future__ import annotations

import argparse
import fnmatch
import hashlib
import json
import re
import subprocess
import sys
import zipfile
from dataclasses import dataclass
from pathlib import Path, PurePosixPath
from typing import Any


FIXED_ZIP_TIMESTAMP = (1980, 1, 1, 0, 0, 0)
MANIFEST_NAME = "manifest.json"
CONTRACT_ARCHIVE_NAME = "package-contract.json"
PAYLOAD_PREFIX = "payload"


class ExportError(RuntimeError):
    pass


@dataclass(frozen=True)
class PackageFile:
    source_path: str
    archive_path: str
    role: str
    target_hint: str
    transform: str
    data: bytes


def canonical_json(value: Any) -> bytes:
    return (json.dumps(value, ensure_ascii=False, indent=2, sort_keys=True) + "\n").encode("utf-8")


def sha256_bytes(data: bytes) -> str:
    return hashlib.sha256(data).hexdigest()


def safe_relative_path(value: str, field: str) -> str:
    if not isinstance(value, str) or not value or "\\" in value or "\x00" in value:
        raise ExportError(f"{field} 必須是非空白 POSIX 相對路徑：{value!r}")
    path = PurePosixPath(value)
    if path.is_absolute() or any(part in ("", ".", "..") for part in path.parts):
        raise ExportError(f"{field} 不得為絕對路徑或含路徑跳脫：{value!r}")
    normalized = path.as_posix()
    if normalized != value:
        raise ExportError(f"{field} 必須先正規化：{value!r}")
    return normalized


def run_git(repo: Path, *args: str, check: bool = True) -> subprocess.CompletedProcess[bytes]:
    try:
        return subprocess.run(
            ["git", "-C", str(repo), *args],
            check=check,
            stdout=subprocess.PIPE,
            stderr=subprocess.PIPE,
        )
    except FileNotFoundError as exc:
        raise ExportError("找不到 git；無法驗證來源提交與追蹤範圍") from exc
    except subprocess.CalledProcessError as exc:
        detail = exc.stderr.decode("utf-8", errors="replace").strip()
        raise ExportError(f"git 指令失敗：{detail or 'unknown error'}") from exc


def load_contract(path: Path) -> dict[str, Any]:
    try:
        contract = json.loads(path.read_text(encoding="utf-8"))
    except (OSError, json.JSONDecodeError) as exc:
        raise ExportError(f"無法讀取契約 {path}: {exc}") from exc

    required = {
        "contractVersion",
        "moduleId",
        "requiredInstallerVersion",
        "installationMode",
        "sourcePackage",
        "targetPackage",
        "versionSource",
        "sourceSets",
        "managedPaths",
        "protectedPaths",
    }
    missing = sorted(required - contract.keys())
    if missing:
        raise ExportError(f"契約缺少欄位：{', '.join(missing)}")
    if contract["installationMode"] != "preflight-only":
        raise ExportError("第一階段契約的 installationMode 必須是 preflight-only")
    safe_relative_path(contract["versionSource"], "versionSource")
    for index, item in enumerate(contract["managedPaths"]):
        if not isinstance(item, dict) or item.get("state") != "candidate-only":
            raise ExportError(f"managedPaths[{index}] 必須標示 state=candidate-only")
        safe_relative_path(item.get("path", ""), f"managedPaths[{index}].path")
    for index, item in enumerate(contract["protectedPaths"]):
        safe_relative_path(item, f"protectedPaths[{index}]")
    if not isinstance(contract["sourceSets"], list) or not contract["sourceSets"]:
        raise ExportError("sourceSets 必須是非空陣列")
    seen_ids: set[str] = set()
    for index, source_set in enumerate(contract["sourceSets"]):
        for key in ("id", "root", "includes", "excludes", "role", "targetHint", "transform"):
            if key not in source_set:
                raise ExportError(f"sourceSets[{index}] 缺少 {key}")
        if source_set["id"] in seen_ids:
            raise ExportError(f"重複的 source set id：{source_set['id']}")
        seen_ids.add(source_set["id"])
        safe_relative_path(source_set["root"], f"sourceSets[{index}].root")
        safe_relative_path(source_set["targetHint"], f"sourceSets[{index}].targetHint")
        for key in ("includes", "excludes"):
            if not isinstance(source_set[key], list):
                raise ExportError(f"sourceSets[{index}].{key} 必須是陣列")
            for pattern in source_set[key]:
                if not isinstance(pattern, str) or not pattern or "\\" in pattern or ".." in PurePosixPath(pattern).parts:
                    raise ExportError(f"sourceSets[{index}].{key} 含不安全 pattern：{pattern!r}")
    return contract


def is_selected(relative: str, includes: list[str], excludes: list[str]) -> bool:
    return any(fnmatch.fnmatchcase(relative, pattern) for pattern in includes) and not any(
        fnmatch.fnmatchcase(relative, pattern) for pattern in excludes
    )


def ensure_inside_repo(repo: Path, relative: str) -> Path:
    candidate = (repo / Path(*PurePosixPath(relative).parts)).resolve()
    try:
        candidate.relative_to(repo.resolve())
    except ValueError as exc:
        raise ExportError(f"來源路徑跳出 repository：{relative}") from exc
    return candidate


def git_tracked_and_clean(repo: Path, path: Path) -> tuple[str, bool]:
    try:
        relative = path.resolve().relative_to(repo.resolve()).as_posix()
    except ValueError:
        return f"<outside-repo>/{path.name}", False
    tracked = run_git(repo, "ls-files", "--error-unmatch", "--", relative, check=False)
    if tracked.returncode != 0:
        return relative, False
    dirty = run_git(repo, "diff", "--name-only", "HEAD", "--", relative).stdout.strip()
    return relative, not dirty


def validate_release_inputs(
    repo: Path, contract_path: Path, allow_uncommitted: bool
) -> dict[str, Any]:
    inputs: list[dict[str, Any]] = []
    for label, path in (("contract", contract_path), ("exporter", Path(__file__))):
        relative, tracked_and_clean = git_tracked_and_clean(repo, path)
        inputs.append({"label": label, "path": relative, "trackedAndClean": tracked_and_clean})
    invalid = [item for item in inputs if not item["trackedAndClean"]]
    if invalid and not allow_uncommitted:
        paths = ", ".join(item["path"] for item in invalid)
        raise ExportError(
            "發布輸入尚未由 sourceCommit 完整追蹤或已有修改："
            f"{paths}；開發中驗證可明確加 --allow-uncommitted-tooling"
        )
    return {
        "releaseInputsTrackedAtSourceCommit": not invalid,
        "allowUncommittedTooling": allow_uncommitted,
        "inputs": inputs,
    }


def collect_files(repo: Path, contract: dict[str, Any]) -> list[PackageFile]:
    package_files: list[PackageFile] = []
    seen_sources: set[str] = set()
    tracked_scope: list[str] = []

    for source_set in contract["sourceSets"]:
        root = source_set["root"]
        tracked_scope.append(root)
        result = run_git(repo, "ls-files", "-z", "--", root)
        tracked = [part.decode("utf-8") for part in result.stdout.split(b"\0") if part]
        root_prefix = root + "/"
        for source_path in tracked:
            normalized_source = safe_relative_path(source_path.replace("\\", "/"), "git tracked path")
            if not normalized_source.startswith(root_prefix):
                continue
            relative = normalized_source[len(root_prefix) :]
            if not is_selected(relative, source_set["includes"], source_set["excludes"]):
                continue
            if normalized_source in seen_sources:
                raise ExportError(f"來源檔案被多個 source set 選取：{normalized_source}")
            seen_sources.add(normalized_source)
            source_file = ensure_inside_repo(repo, normalized_source)
            if not source_file.is_file():
                raise ExportError(f"Git 追蹤檔案不存在：{normalized_source}")
            target_hint = PurePosixPath(source_set["targetHint"], relative).as_posix()
            safe_relative_path(target_hint, "target hint")
            archive_path = PurePosixPath(PAYLOAD_PREFIX, normalized_source).as_posix()
            safe_relative_path(archive_path, "archive path")
            package_files.append(
                PackageFile(
                    source_path=normalized_source,
                    archive_path=archive_path,
                    role=source_set["role"],
                    target_hint=target_hint,
                    transform=source_set["transform"],
                    data=source_file.read_bytes(),
                )
            )

    dirty = run_git(repo, "diff", "--name-only", "HEAD", "--", *tracked_scope).stdout.decode("utf-8").strip()
    if dirty:
        paths = ", ".join(line for line in dirty.splitlines() if line)
        raise ExportError(f"模組來源範圍有尚未提交的 tracked 變更：{paths}")
    package_files.sort(key=lambda item: item.archive_path)
    if not package_files:
        raise ExportError("契約沒有選到任何 Git 追蹤檔案")
    return package_files


def parse_versions(version_file: Path) -> tuple[str, int, int]:
    try:
        text = version_file.read_text(encoding="utf-8")
    except OSError as exc:
        raise ExportError(f"無法讀取版本檔：{version_file}") from exc

    def find(pattern: str, label: str) -> str:
        match = re.search(pattern, text)
        if not match:
            raise ExportError(f"版本檔缺少 {label}")
        return match.group(1)

    game = find(r'GAME_VERSION\s*=\s*"([^"]+)"', "GAME_VERSION")
    save = int(find(r"SAVE_DOCUMENT_SCHEMA_VERSION\s*=\s*(\d+)", "SAVE_DOCUMENT_SCHEMA_VERSION"))
    state = int(find(r"GAME_STATE_SCHEMA_VERSION\s*=\s*(\d+)", "GAME_STATE_SCHEMA_VERSION"))
    return game, save, state


def build_manifest(
    repo: Path,
    contract: dict[str, Any],
    contract_bytes: bytes,
    files: list[PackageFile],
    tooling_provenance: dict[str, Any],
) -> dict[str, Any]:
    game_version, save_schema, state_schema = parse_versions(ensure_inside_repo(repo, contract["versionSource"]))
    commit = run_git(repo, "rev-parse", "HEAD").stdout.decode("ascii").strip()
    return {
        "manifestVersion": 1,
        "moduleId": contract["moduleId"],
        "contractVersion": contract["contractVersion"],
        "requiredInstallerVersion": contract["requiredInstallerVersion"],
        "installationMode": contract["installationMode"],
        "gameVersion": game_version,
        "saveDocumentSchemaVersion": save_schema,
        "gameStateSchemaVersion": state_schema,
        "sourceCommit": commit,
        "toolingProvenance": tooling_provenance,
        "contractSha256": sha256_bytes(contract_bytes),
        "managedPaths": contract["managedPaths"],
        "protectedPaths": contract["protectedPaths"],
        "files": [
            {
                "sourcePath": item.source_path,
                "archivePath": item.archive_path,
                "role": item.role,
                "targetHint": item.target_hint,
                "transform": item.transform,
                "size": len(item.data),
                "sha256": sha256_bytes(item.data),
            }
            for item in files
        ],
    }


def write_zip_entry(archive: zipfile.ZipFile, name: str, data: bytes) -> None:
    safe_relative_path(name, "ZIP entry")
    info = zipfile.ZipInfo(name, FIXED_ZIP_TIMESTAMP)
    info.compress_type = zipfile.ZIP_STORED
    info.create_system = 3
    info.external_attr = 0o100644 << 16
    info.flag_bits |= 0x800
    archive.writestr(info, data)


def export_package(output: Path, contract_bytes: bytes, manifest: dict[str, Any], files: list[PackageFile]) -> str:
    output.parent.mkdir(parents=True, exist_ok=True)
    with zipfile.ZipFile(output, "w", allowZip64=True) as archive:
        entries = [
            (CONTRACT_ARCHIVE_NAME, contract_bytes),
            (MANIFEST_NAME, canonical_json(manifest)),
            *((item.archive_path, item.data) for item in files),
        ]
        for name, data in sorted(entries, key=lambda entry: entry[0]):
            write_zip_entry(archive, name, data)
    digest = hashlib.sha256(output.read_bytes()).hexdigest()
    sidecar = output.with_name(output.name + ".sha256")
    sidecar.write_text(f"{digest}  {output.name}\n", encoding="utf-8", newline="\n")
    return digest


def create_parser() -> argparse.ArgumentParser:
    parser = argparse.ArgumentParser(description="匯出可稽核、可重現的 Sango 策略模組包")
    parser.add_argument("--repo", type=Path, default=Path(__file__).resolve().parents[2], help="Sango repository 根目錄")
    parser.add_argument("--contract", type=Path, help="契約 JSON；預設使用工具目錄下的 package-contract.json")
    parser.add_argument("--output", type=Path, help="ZIP 輸出位置；預設為 dist/sango-module-<version>.zip")
    mode = parser.add_mutually_exclusive_group()
    mode.add_argument("--dry-run", action="store_true", help="只驗證並顯示摘要，不寫檔")
    mode.add_argument("--list", action="store_true", help="只列出選取檔案，不寫檔")
    parser.add_argument(
        "--allow-uncommitted-tooling",
        action="store_true",
        help="只供開發驗證；允許 contract/exporter 尚未由 sourceCommit 追蹤，並記入 manifest",
    )
    return parser


def main(argv: list[str] | None = None) -> int:
    args = create_parser().parse_args(argv)
    try:
        repo = args.repo.resolve()
        if not (repo / ".git").exists():
            raise ExportError(f"不是 Git repository：{repo}")
        contract_path = (args.contract or repo / "tools/sango-module/package-contract.json").resolve()
        contract = load_contract(contract_path)
        contract_bytes = canonical_json(contract)
        tooling_provenance = validate_release_inputs(
            repo, contract_path, args.allow_uncommitted_tooling
        )
        files = collect_files(repo, contract)
        manifest = build_manifest(repo, contract, contract_bytes, files, tooling_provenance)
        if args.list:
            for item in files:
                print(f"{item.role}\t{item.source_path}\t->\t{item.target_hint}")
            print(f"共 {len(files)} 個檔案")
            return 0
        if args.dry_run:
            print(
                f"OK preflight-only: {manifest['moduleId']} {manifest['gameVersion']}, "
                f"commit {manifest['sourceCommit']}, {len(files)} files"
            )
            return 0
        output = (args.output or repo / f"dist/sango-module-{manifest['gameVersion']}.zip").resolve()
        digest = export_package(output, contract_bytes, manifest, files)
        print(f"已輸出：{output}")
        print(f"SHA-256：{digest}")
        print("安裝模式：preflight-only（Curated 不得 live overwrite）")
        return 0
    except ExportError as exc:
        print(f"ERROR: {exc}", file=sys.stderr)
        return 2


if __name__ == "__main__":
    raise SystemExit(main())
