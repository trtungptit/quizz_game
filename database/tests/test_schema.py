"""Integration tests against a disposable local MySQL instance, never the real DB.
Run: python database/tests/test_schema.py --mysql-bin "C:/Program Files/MySQL/MySQL Server 8.0/bin"
Requires MySQL 8.0.16+. Uses no saved credentials and creates no runtime seed data.
"""
from pathlib import Path
import argparse
import socket
import subprocess
import tempfile
import time
import unittest

SCHEMA = Path(__file__).resolve().parents[1] / 'schema.sql'
MYSQL = []


class RetryTemporaryDirectory(tempfile.TemporaryDirectory):
    """Windows antivirus/file indexing may briefly hold files after mysqld exits."""
    def cleanup(self):
        for attempt in range(30):
            try:
                return super().cleanup()
            except PermissionError:
                if attempt == 29:
                    raise
                time.sleep(0.5)


def sql(statement, success=True):
    result = subprocess.run(MYSQL, input=statement, encoding='utf-8', capture_output=True, timeout=15)
    if success and result.returncode:
        raise AssertionError(result.stderr)
    if not success and not result.returncode:
        raise AssertionError('Expected SQL rejection, but statement succeeded: ' + statement)
    if not success and not any(('ERROR ' + code + ' ') in result.stderr for code in ('1062', '1452', '3819', '1265')):
        raise AssertionError('Expected integrity rejection, not a syntax/missing-column error: ' + result.stderr)
    return result.stdout.strip()


class SchemaTests(unittest.TestCase):
    def setUp(self):
        sql('DROP DATABASE IF EXISTS quiz_game;\n' + SCHEMA.read_text(encoding='utf-8'))
        sql("USE quiz_game; INSERT INTO users(username,password_hash,display_name) VALUES "
            "('tester_a','TEST_ONLY_NOT_A_PASSWORD_HASH','A'),"
            "('tester_b','TEST_ONLY_NOT_A_PASSWORD_HASH','B'),"
            "('tester_c','TEST_ONLY_NOT_A_PASSWORD_HASH','C');"
            "INSERT INTO rooms(room_code,name,host_id) VALUES ('ABC234','Test room',1),('DEF567','Other room',3);")

    def query(self, command, success=True):
        return sql('USE quiz_game; ' + command, success)

    def question(self):
        self.query("INSERT INTO question_sets(owner_id,title,description) VALUES(1,'Set','');"
                   "INSERT INTO questions(set_id,type,prompt,position) VALUES(1,'MULTI','Question',0);"
                   "INSERT INTO question_options(question_id,option_index,option_text,is_correct) VALUES"
                   "(1,0,'A',TRUE),(1,1,'B',TRUE);")

    def match(self):
        self.question()
        self.query("INSERT INTO matches(room_id) VALUES(1); INSERT INTO match_players(match_id,user_id) VALUES(1,1);"
                   "INSERT INTO rounds(match_id,set_id,round_number) VALUES(1,1,1);"
                   "INSERT INTO round_questions(round_id,question_id,position) VALUES(1,1,0);")

    def test_multiple_matches_per_room(self):
        self.query('INSERT INTO matches(room_id) VALUES(1),(1);')

    def test_multi_answer_storage(self):
        self.match()
        self.query('INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(1,1,1,1,1);'
                   'INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(1,1,1),(1,1,2);')
        self.assertEqual(self.query('SELECT COUNT(*) FROM submission_options;'), '2')

    def test_one_active_room_per_user(self):
        self.query('INSERT INTO room_members(room_id,user_id) VALUES(1,2);')
        self.query('INSERT INTO room_members(room_id,user_id) VALUES(2,2);', False)
        self.query('UPDATE room_members SET left_at=CURRENT_TIMESTAMP WHERE room_id=1 AND user_id=2;'
                   'INSERT INTO room_members(room_id,user_id) VALUES(2,2);')

    def test_only_one_pending_invite_per_room_recipient(self):
        self.query('INSERT INTO invitations(room_id,sender_id,receiver_id) VALUES(1,1,2);')
        self.query('INSERT INTO invitations(room_id,sender_id,receiver_id) VALUES(1,1,2);', False)
        self.query("UPDATE invitations SET status='rejected',responded_at=CURRENT_TIMESTAMP(3) WHERE invitation_id=1;"
                   'INSERT INTO invitations(room_id,sender_id,receiver_id) VALUES(1,1,2);')

    def test_different_rooms_can_queue_invites(self):
        self.query('INSERT INTO invitations(room_id,sender_id,receiver_id) VALUES(1,1,2),(2,3,2);')

    def test_self_invite_rejected(self):
        self.query('INSERT INTO invitations(room_id,sender_id,receiver_id) VALUES(1,1,1);', False)

    def test_soft_delete_set(self):
        self.question()
        self.query('UPDATE question_sets SET deleted_at=CURRENT_TIMESTAMP WHERE set_id=1;')
        self.assertEqual(self.query('SELECT COUNT(*) FROM questions WHERE set_id=1;'), '1')

    def test_individual_question_deadlines(self):
        self.match()
        self.query('INSERT INTO match_players(match_id,user_id) VALUES(1,2);'
                   'INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id,question_sent_at,deadline_at) VALUES'
                   "(1,1,1,1,1,'2026-01-01 00:00:00','2026-01-01 00:00:20'),"
                   "(1,2,1,1,1,'2026-01-01 00:00:07','2026-01-01 00:00:27');")
        self.assertEqual(self.query('SELECT COUNT(DISTINCT deadline_at) FROM submissions;'), '2')

    def test_one_round_per_match_contract(self):
        self.match()
        self.query('INSERT INTO rounds(match_id,set_id,round_number) VALUES(1,1,2);', False)

    def test_username_case_collision(self):
        self.query("INSERT INTO users(username,password_hash,display_name) VALUES('TESTER_A','TEST_ONLY','A');", False)

    def test_invalid_room_code(self):
        self.query("INSERT INTO rooms(room_code,name,host_id) VALUES('ABO234','Bad',1);", False)

    def test_wrong_question_option_rejected(self):
        self.match()
        self.query("INSERT INTO questions(set_id,type,prompt,position) VALUES(1,'SINGLE','Other',1);"
                   "INSERT INTO question_options(question_id,option_index,option_text) VALUES(2,0,'Other');"
                   'INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(1,1,1,1,1);')
        self.query('INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(1,1,3);', False)

    def test_nonparticipant_submission_rejected(self):
        self.match()
        self.query('INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(1,2,1,1,1);', False)

    def test_room_configuration(self):
        self.question()
        self.query('UPDATE rooms SET set_id=1,num_questions=1,time_override_seconds=20 WHERE room_id=1;')
        self.query('UPDATE rooms SET time_override_seconds=100 WHERE room_id=1;', False)

    def test_duplicate_selected_option_rejected(self):
        self.match()
        self.query('INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(1,1,1,1,1);'
                   'INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(1,1,1);')
        self.query('INSERT INTO submission_options(submission_id,question_id,option_id) VALUES(1,1,1);', False)

    def test_submission_cannot_mix_matches(self):
        self.match()
        self.query('INSERT INTO matches(room_id) VALUES(1); INSERT INTO match_players(match_id,user_id) VALUES(2,1);')
        self.query('INSERT INTO submissions(round_question_id,user_id,question_id,match_id,round_id) VALUES(1,1,1,2,1);', False)

    def test_accented_answers_remain_distinct(self):
        self.question()
        self.query("INSERT INTO accepted_answers(question_id,answer_text,normalized_text) VALUES(1,'ma','ma'),(1,'má','má');")
        self.assertEqual(self.query('SELECT COUNT(*) FROM accepted_answers;'), '2')

    def test_pending_invites_cannot_expire_before_delivery(self):
        self.query("INSERT INTO invitations(room_id,sender_id,receiver_id,delivered_at,expires_at) VALUES"
                   "(1,1,2,'2026-01-01 00:00:05','2026-01-01 00:00:04');", False)


def main():
    global MYSQL
    parser = argparse.ArgumentParser()
    parser.add_argument('--mysql-bin', required=True)
    args = parser.parse_args()
    mysql_bin = Path(args.mysql_bin)
    scratch = Path.home() / 'AppData/Local/hermes/cache/scratch'
    scratch.mkdir(parents=True, exist_ok=True)
    with RetryTemporaryDirectory(prefix='quiz-schema-test-', dir=scratch) as work:
        data = Path(work) / 'data'
        subprocess.run([str(mysql_bin / 'mysqld.exe'), '--no-defaults', '--initialize-insecure',
                        '--basedir=' + str(mysql_bin.parent), '--datadir=' + str(data)],
                       capture_output=True, check=True, timeout=120)
        with socket.socket() as sock:
            sock.bind(('127.0.0.1', 0))
            port = sock.getsockname()[1]
        MYSQL = [str(mysql_bin / 'mysql.exe'), '--no-defaults', '--protocol=TCP', '-h127.0.0.1',
                 '-P' + str(port), '-uroot', '--connect-timeout=2', '--default-character-set=utf8mb4',
                 '--batch', '--skip-column-names']
        server = subprocess.Popen([str(mysql_bin / 'mysqld.exe'), '--no-defaults',
                                   '--basedir=' + str(mysql_bin.parent), '--datadir=' + str(data),
                                   '--bind-address=127.0.0.1', '--port=' + str(port),
                                   '--mysqlx=OFF', '--skip-log-bin'], stdout=subprocess.DEVNULL,
                                  stderr=subprocess.DEVNULL)
        try:
            for _ in range(60):
                try:
                    version = sql('SELECT VERSION();')
                    break
                except (AssertionError, subprocess.TimeoutExpired):
                    if server.poll() is not None:
                        raise RuntimeError('Disposable MySQL failed to start')
                    time.sleep(0.5)
            else:
                raise RuntimeError('Disposable MySQL startup timed out')
            print('Disposable MySQL:', version, flush=True)
            result = unittest.TextTestRunner(verbosity=2).run(unittest.defaultTestLoader.loadTestsFromTestCase(SchemaTests))
        finally:
            subprocess.run([str(mysql_bin / 'mysqladmin.exe'), '--no-defaults', '--protocol=TCP',
                            '-h127.0.0.1', '-P' + str(port), '-uroot', 'shutdown'],
                           capture_output=True, timeout=20)
            try:
                server.wait(timeout=20)
            except subprocess.TimeoutExpired:
                server.terminate()
                server.wait(timeout=10)
        raise SystemExit(0 if result.wasSuccessful() else 1)


if __name__ == '__main__':
    main()
