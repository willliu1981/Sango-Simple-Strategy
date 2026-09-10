from __future__ import annotations

import hashlib
import json
import os
import subprocess
import sys
import tempfile
import unittest
import zipfile
from pathlib import Path


EXPORTER = Path(__file__).with_name("export-sango-module.py").resolve()


class ExporterTest(unittest.TestCase):
    def setUp(self) -> None:
        self.temporary = tempfile.TemporaryDirectory()
        self.external = tempfile.TemporaryDirectory()
        self.repo = Path(self.temporary.name)
        self._git("init", "-q")
        self._git("config", "user.email", "test@example.invalid")
        self._git("config", "user.name", "Exporter Test")
        self._write(
            "core/src/main/java/idv/kuan/studio/sango/SangoVersion.java",
            'class SangoVersion { static final String GAME_VERSION = "9.8.7"; '
            "static final int SAVE_DOCUMENT_SCHEMA_VERSION = 2; "
            "static final int GAME_STATE_SCHEMA_VERSION = 13; }\n",
        )
        self._write("core/src/main/java/idv/kuan/studio/sango/domain/Rule.java", "class Rule {}\n")
        self._write("core/src/main/java/idv/kuan/studio/sango/Main.java", "class Main {}\n")
        self._write("core/src/main/java/idv/kuan/studio/sango/provider/Provider.java", "class Provider {}\n")
        self._write("assets/ui/city.xml", "<ui/>\n")
        self._write("core/src/main/resources/META-INF/services/example", "Provider\n")
        self.exporter = self.repo / "tools/sango-module/export-sango-module.py"
        self._write(
            "tools/sango-module/export-sango-module.py",
            EXPORTER.read_text(encoding="utf-8"),
        )
        self.contract = self.repo / "contract.json"
        self.contract.write_text(json.dumps(self._contract(), ensure_ascii=False), encoding="utf-8")
        self._git("add", ".")
        self._git("commit", "-q", "-m", "fixture")

    def tearDown(self) -> None:
        self.temporary.cleanup()
        self.external.cleanup()

    def _git(self, *args: str) -> subprocess.CompletedProcess[str]:
        return subprocess.run(["git", "-C", str(self.repo), *args], check=True, text=True, capture_output=True)

    def _write(self, relative: str, content: str) -> None:
        path = self.repo / relative
        path.parent.mkdir(parents=True, exist_ok=True)
        path.write_text(content, encoding="utf-8", newline="\n")

    def _contract(self) -> dict:
        return {
            "contractVersion": 1,
            "moduleId": "fixture",
            "requiredInstallerVersion": "0.1.0",
            "installationMode": "preflight-only",
            "sourcePackage": "idv.kuan.studio.sango",
            "targetPackage": "example.target",
            "versionSource": "core/src/main/java/idv/kuan/studio/sango/SangoVersion.java",
            "sourceSets": [
                {
                    "id": "java",
                    "root": "core/src/main/java/idv/kuan/studio/sango",
                    "includes": ["*.java", "**/*.java"],
                    "excludes": ["Main.java", "provider/**"],
                    "role": "gameplay-source",
                    "targetHint": "target/java",
                    "transform": "java-package-rewrite",
                },
                {
                    "id": "ui",
                    "root": "assets/ui",
                    "includes": ["*.xml"],
                    "excludes": [],
                    "role": "ui-layout",
                    "targetHint": "target/ui",
                    "transform": "copy",
                },
            ],
            "managedPaths": [{"path": "target/java", "state": "candidate-only"}],
            "protectedPaths": ["host/Main.java"],
            "excludedContent": ["fixture"],
        }

    def _run(self, *args: str) -> subprocess.CompletedProcess[str]:
        environment = os.environ.copy()
        environment["PYTHONIOENCODING"] = "utf-8"
        return subprocess.run(
            [sys.executable, str(self.exporter), "--repo", str(self.repo), "--contract", str(self.contract), *args],
            text=True,
            encoding="utf-8",
            capture_output=True,
            env=environment,
        )

    def test_two_exports_are_byte_identical_and_auditable(self) -> None:
        first = self.repo / "out/first.zip"
        second = self.repo / "out/second.zip"
        self.assertEqual(0, self._run("--output", str(first)).returncode)
        self.assertEqual(0, self._run("--output", str(second)).returncode)
        self.assertEqual(first.read_bytes(), second.read_bytes())

        with zipfile.ZipFile(first) as archive:
            names = archive.namelist()
            self.assertEqual(sorted(names), names)
            self.assertNotIn("payload/core/src/main/java/idv/kuan/studio/sango/Main.java", names)
            self.assertFalse(any("/provider/" in name or "META-INF" in name for name in names))
            manifest = json.loads(archive.read("manifest.json"))
            self.assertEqual("9.8.7", manifest["gameVersion"])
            self.assertEqual(2, manifest["saveDocumentSchemaVersion"])
            self.assertEqual(13, manifest["gameStateSchemaVersion"])
            self.assertEqual("preflight-only", manifest["installationMode"])
            self.assertTrue(manifest["toolingProvenance"]["releaseInputsTrackedAtSourceCommit"])
            for item in manifest["files"]:
                payload = archive.read(item["archivePath"])
                self.assertEqual(len(payload), item["size"])
                self.assertEqual(hashlib.sha256(payload).hexdigest(), item["sha256"])

        digest = hashlib.sha256(first.read_bytes()).hexdigest()
        self.assertEqual(f"{digest}  first.zip\n", Path(str(first) + ".sha256").read_text(encoding="utf-8"))
        first_prompt = first.with_name("first-codex-prompt.md")
        prompt_text = first_prompt.read_text(encoding="utf-8")
        self.assertIn(str(first.resolve()), prompt_text)
        self.assertIn(str(Path(str(first) + ".sha256").resolve()), prompt_text)
        self.assertIn("!plan", prompt_text)
        self.assertIn("!exec", prompt_text)
        self.assertNotEqual(
            prompt_text,
            second.with_name("second-codex-prompt.md").read_text(encoding="utf-8"),
        )

    def test_list_and_dry_run_do_not_write_package(self) -> None:
        output = self.repo / "should-not-exist.zip"
        listed = self._run("--list", "--output", str(output))
        self.assertEqual(0, listed.returncode, listed.stderr)
        self.assertIn("domain/Rule.java", listed.stdout)
        self.assertFalse(output.exists())
        self.assertFalse(output.with_name("should-not-exist-codex-prompt.md").exists())
        dry_run = self._run("--dry-run", "--output", str(output))
        self.assertEqual(0, dry_run.returncode, dry_run.stderr)
        self.assertIn("preflight-only", dry_run.stdout)
        self.assertFalse(output.exists())
        self.assertFalse(output.with_name("should-not-exist-codex-prompt.md").exists())

    def test_unrelated_untracked_file_is_ignored_but_tracked_scope_change_fails(self) -> None:
        self._write("notes/untracked.txt", "not part of package\n")
        self.assertEqual(0, self._run("--dry-run").returncode)
        self._write("assets/ui/city.xml", "<changed/>\n")
        failed = self._run("--dry-run")
        self.assertEqual(2, failed.returncode)
        self.assertIn("tracked", failed.stderr)

    def test_path_traversal_in_contract_is_rejected(self) -> None:
        contract = self._contract()
        contract["sourceSets"][0]["targetHint"] = "../outside"
        self.contract.write_text(json.dumps(contract), encoding="utf-8")
        failed = self._run("--dry-run")
        self.assertEqual(2, failed.returncode)
        self.assertIn("路徑跳脫", failed.stderr)

    def test_untracked_contract_requires_explicit_development_override(self) -> None:
        self._write("untracked-contract.json", json.dumps(self._contract()))
        original_contract = self.contract
        self.contract = self.repo / "untracked-contract.json"
        failed = self._run("--dry-run")
        self.assertEqual(2, failed.returncode)
        self.assertIn("--allow-uncommitted-tooling", failed.stderr)
        allowed = self._run("--dry-run", "--allow-uncommitted-tooling")
        self.assertEqual(0, allowed.returncode, allowed.stderr)
        self.contract = original_contract

    def test_external_contract_requires_explicit_development_override(self) -> None:
        external_contract = Path(self.external.name) / "contract.json"
        external_contract.write_text(json.dumps(self._contract()), encoding="utf-8")
        original_contract = self.contract
        self.contract = external_contract
        failed = self._run("--dry-run")
        self.assertEqual(2, failed.returncode)
        self.assertIn("<outside-repo>/contract.json", failed.stderr)
        allowed = self._run("--dry-run", "--allow-uncommitted-tooling")
        self.assertEqual(0, allowed.returncode, allowed.stderr)
        self.contract = original_contract


if __name__ == "__main__":
    unittest.main()
