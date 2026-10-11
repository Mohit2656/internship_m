"""Run opt-in integration tests in a new local PostgreSQL schema, never public.

Reads DB_URL/DB_USERNAME/DB_PASSWORD from the environment, falling back to the
ignored backend/application-local-secrets.properties. Never prints credentials.
Requires Python 3, psql and Maven. No production endpoints are accepted.
"""
from pathlib import Path
from urllib.parse import urlparse, parse_qs, urlencode
import os
import re
import shutil
import subprocess
import sys
import uuid


def main():
    backend = Path(__file__).resolve().parents[1]
    config = {}
    local = backend / "application-local-secrets.properties"
    if local.exists():
        for line in local.read_text(encoding="utf-8").splitlines():
            if line.strip() and not line.lstrip().startswith("#") and "=" in line:
                key, value = line.split("=", 1)
                config[key.strip()] = value.strip()
    for key in ("DB_URL", "DB_USERNAME", "DB_PASSWORD"):
        if key in os.environ:
            config[key] = os.environ[key]
        if not config.get(key):
            raise SystemExit("Configure " + key + " before running PostgreSQL tests.")
    url = config["DB_URL"]
    if not url.startswith("jdbc:postgresql://"):
        raise SystemExit("Only a local PostgreSQL JDBC URL is permitted.")
    parsed = urlparse(url[5:])
    if parsed.hostname not in ("localhost", "127.0.0.1") or parsed.username or parsed.password:
        raise SystemExit("Only localhost PostgreSQL with separate credentials is permitted.")
    query = parse_qs(parsed.query, keep_blank_values=True)
    if any(key.lower() in ("currentschema", "options") for key in query):
        raise SystemExit("The base URL must not configure a schema or search-path options.")
    schema = "ims_deletion_test_" + uuid.uuid4().hex
    assert re.fullmatch(r"ims_deletion_test_[0-9a-f]{32}", schema)
    psql = shutil.which("psql")
    if not psql and os.name == "nt":
        candidates = sorted(Path(os.environ.get("ProgramFiles", "C:/Program Files")).glob("PostgreSQL/*/bin/psql.exe"))
        if candidates:
            psql = str(candidates[-1])
    maven = shutil.which("mvn.cmd" if os.name == "nt" else "mvn")
    if not psql or not maven:
        raise SystemExit("Install psql and Maven before running these tests.")
    env = os.environ.copy()
    env["PGPASSWORD"] = config["DB_PASSWORD"]
    command = [psql, "-h", parsed.hostname, "-p", str(parsed.port or 5432),
               "-U", config["DB_USERNAME"], "-d", parsed.path.lstrip("/"), "-v", "ON_ERROR_STOP=1", "-c"]
    created = subprocess.run(command + ["CREATE SCHEMA " + schema], env=env, capture_output=True)
    if created.returncode:
        raise SystemExit("Could not create the disposable local test schema.")
    query["currentSchema"] = [schema]
    isolated = "jdbc:" + parsed._replace(query=urlencode(query, doseq=True)).geturl()
    env.update(EXIF_TEST_DB_URL=isolated, EXIF_TEST_DB_SCHEMA=schema,
               EXIF_TEST_DB_USERNAME=config["DB_USERNAME"], EXIF_TEST_DB_PASSWORD=config["DB_PASSWORD"])
    result_code = 1
    try:
        with (backend / "audit-postgres.log").open("w", encoding="utf-8") as output:
            result = subprocess.run([maven, "-q", "-Dtest=*PostgresIntegrationTest,WorkplaceApprovalMigrationPostgresTest",
                                     "-DreuseForks=false", "test"], cwd=backend, env=env,
                                    stdout=output, stderr=subprocess.STDOUT)
        result_code = result.returncode
        print("PostgreSQL integration test exit code:", result_code)
        print("Test log: backend/audit-postgres.log")
    finally:
        # Only this exact schema, successfully created above, is removed.
        removed = subprocess.run(command + ["DROP SCHEMA " + schema + " CASCADE"], env=env, capture_output=True)
        print("Disposable schema cleanup:", "passed" if removed.returncode == 0 else "failed")
        if removed.returncode:
            result_code = 1
    return result_code


if __name__ == "__main__":
    sys.exit(main())
