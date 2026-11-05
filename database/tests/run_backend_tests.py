"""Run backend Java tests against a fresh disposable local MySQL instance."""
from pathlib import Path
import os
import shutil
import socket
import subprocess
import tempfile
import time
import argparse

ROOT = Path(__file__).resolve().parents[2]
DEFAULT_MYSQL = Path(r"C:\Program Files\MySQL\MySQL Server 8.0\bin")
DEFAULT_MVN = Path.home() / ".m2/wrapper/dists/apache-maven-3.8.2-bin/1um3pu5poco57pibs3g92d6vrf/apache-maven-3.8.2/bin/mvn.cmd"


def main():
    parser = argparse.ArgumentParser()
    parser.add_argument("--mysql-bin", type=Path, default=DEFAULT_MYSQL)
    parser.add_argument("--mvn", type=Path, default=DEFAULT_MVN)
    parser.add_argument("--timeout", type=int, default=900,
                        help="Maximum seconds for Maven tests (default: 900)")
    parser.add_argument("--tests", help="Optional Maven test class selector")
    args = parser.parse_args()
    scratch = Path(os.environ.get("LOCALAPPDATA", str(Path.home()))) / "hermes/cache/scratch"
    scratch.mkdir(parents=True, exist_ok=True)
    work = Path(tempfile.mkdtemp(prefix="quiz-backend-test-", dir=scratch))
    data = work / "data"
    server = None
    port = None
    try:
        subprocess.run([str(args.mysql_bin / "mysqld.exe"), "--no-defaults", "--initialize-insecure",
                        "--basedir=" + str(args.mysql_bin.parent), "--datadir=" + str(data)],
                       capture_output=True, check=True, timeout=120)
        with socket.socket() as sock:
            sock.bind(("127.0.0.1", 0))
            port = sock.getsockname()[1]
        mysql = [str(args.mysql_bin / "mysql.exe"), "--no-defaults", "--protocol=TCP",
                 "-h127.0.0.1", "-P" + str(port), "-uroot", "--connect-timeout=2", "--batch"]
        server = subprocess.Popen([str(args.mysql_bin / "mysqld.exe"), "--no-defaults",
                                   "--basedir=" + str(args.mysql_bin.parent), "--datadir=" + str(data),
                                   "--bind-address=127.0.0.1", "--port=" + str(port),
                                   "--mysqlx=OFF", "--skip-log-bin"], stdout=subprocess.DEVNULL,
                                  stderr=subprocess.DEVNULL)
        for _ in range(60):
            try:
                version = subprocess.run(mysql + ["-Nse", "SELECT VERSION()"], check=True,
                                         capture_output=True, text=True, timeout=3).stdout.strip()
                break
            except (subprocess.SubprocessError, OSError):
                if server.poll() is not None:
                    raise RuntimeError("Disposable MySQL failed to start")
                time.sleep(0.5)
        else:
            raise RuntimeError("Disposable MySQL startup timed out")
        print("Disposable MySQL:", version, flush=True)
        schema = (ROOT / "database/schema.sql").read_text(encoding="utf-8")
        subprocess.run(mysql, input=schema + "\nCREATE USER 'quiz_test'@'%' IDENTIFIED BY 'ephemeral-test-only';\n"
                       "GRANT ALL PRIVILEGES ON quiz_game.* TO 'quiz_test'@'%';\n",
                       text=True, check=True, capture_output=True, timeout=60)
        env = os.environ.copy()
        env.update({"QUIZ_TEST_DB_URL": f"jdbc:mysql://127.0.0.1:{port}/quiz_game?useSSL=false&allowPublicKeyRetrieval=true&serverTimezone=UTC",
                    "QUIZ_TEST_DB_USER": "quiz_test", "QUIZ_TEST_DB_PASSWORD": "ephemeral-test-only"})
        command = ["cmd.exe", "/c", str(args.mvn), "-q", "-pl", "server", "-am", "test"]
        if args.tests:
            command += ["-Dtest=" + args.tests, "-Dsurefire.failIfNoSpecifiedTests=false"]
        subprocess.run(command,
                       cwd=ROOT, env=env, check=True, timeout=args.timeout)
        print("Backend unit + TCP/DB integration tests passed.", flush=True)
    finally:
        if server is not None and port is not None:
            subprocess.run([str(args.mysql_bin / "mysqladmin.exe"), "--no-defaults", "--protocol=TCP",
                            "-h127.0.0.1", "-P" + str(port), "-uroot", "shutdown"],
                           capture_output=True, timeout=20)
            try:
                server.wait(timeout=20)
            except subprocess.TimeoutExpired:
                server.terminate()
                server.wait(timeout=10)
        for _ in range(30):
            try:
                shutil.rmtree(work)
                break
            except PermissionError:
                time.sleep(0.5)
        else:
            print("WARNING: temporary test data directory could not be removed:", work)


if __name__ == "__main__":
    main()
