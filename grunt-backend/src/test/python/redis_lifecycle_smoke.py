"""Run the production lifecycle Lua against a private Redis UNIX socket (no third-party dependencies)."""
import concurrent.futures
import pathlib
import socket
import sys
import uuid


class Redis:
    def __init__(self, path):
        self.socket = socket.socket(socket.AF_UNIX)
        self.socket.settimeout(5)
        self.socket.connect(path)
        self.input = self.socket.makefile("rb")

    def call(self, *args):
        encoded = [str(arg).encode() for arg in args]
        self.socket.sendall(b"*%d\r\n" % len(encoded) + b"".join(
            b"$%d\r\n" % len(value) + value + b"\r\n" for value in encoded))
        return self.read()

    def read(self):
        line = self.input.readline()
        if not line:
            raise EOFError("Redis closed the connection")
        kind, value = line[:1], line[1:-2]
        if kind == b"-":
            raise RuntimeError(value.decode())
        if kind == b"+":
            return value.decode()
        if kind == b":":
            return int(value)
        if kind == b"$":
            length = int(value)
            if length < 0:
                return None
            data = self.input.read(length)
            if len(data) != length or self.input.read(2) != b"\r\n":
                raise EOFError("Incomplete Redis bulk reply")
            return data.decode()
        if kind == b"*":
            return [self.read() for _ in range(int(value))]
        raise ValueError("Unexpected Redis reply")

    def close(self):
        self.input.close()
        self.socket.close()


def main():
    if len(sys.argv) != 2:
        raise SystemExit("Usage: python3 redis_lifecycle_smoke.py /absolute/path/to/private/redis.sock")
    path = sys.argv[1]
    script = (pathlib.Path(__file__).resolve().parents[2] / "main/resources/redis/jobs.lua").read_text()
    namespace = "grunteon-perf-smoke:" + uuid.uuid4().hex
    base, prefix = namespace + ":queue", namespace + ":job:"
    fixed = [base, base + ":leases", base + ":active", base + ":upload-bytes", base + ":total-bytes",
             base + ":staged", base + ":terminal", "", base + ":cancelled-uploads", base + ":wakeups"]
    jobs = ["a", "b", "c", "d"] + ["race-" + str(i) for i in range(8)]
    client = Redis(path)
    checks = 0

    def expect(actual, wanted):
        nonlocal checks
        assert actual == wanted, (actual, wanted)
        checks += 1

    def execute(connection, op, job="", *args):
        keys = list(fixed)
        keys[7] = prefix + job
        return connection.call("EVAL", script, len(keys), *keys, op, prefix, *args)

    def op(operation, job="", *args):
        return execute(client, operation, job, *args)

    try:
        expect(client.call("PING"), "PONG")
        expect(op("reserve", "a", "a", 10, 2, 100), "1")
        expect(op("reserve", "a", "a", 10, 2, 100), "1")
        expect(op("create", "a", "a", "/unused/a", "stamp", 2), "1")
        expect(op("create", "a", "a", "/unused/a", "stamp", 2), "1")
        expect(client.call("LLEN", base), 1)
        expect(op("reserve", "b", "b", 20, 2, 100), "1")
        expect(op("create", "b", "b", "/unused/b", "stamp", 2), "1")
        expect(op("reserve", "c", "c", 1, 2, 100), "0")
        expect(op("claim", "", "owner-a", 60000, "stamp", 16), "a")
        expect(op("claim", "", "owner-b", 60000, "stamp", 16), "b")
        expect(op("renew", "a", "a", "stale", 60000), "0")
        expect(op("renew", "a", "a", "owner-a", 60000), "1")
        expect(op("complete", "a", "a", "owner-a", "SUCCESS", "stamp", "", "attempts/a/result.zip"), "1")
        expect(op("complete", "a", "a", "owner-a", "SUCCESS", "stamp", "", "attempts/a/result.zip"), "1")
        expect(client.call("HGET", prefix + "a", "resultAvailable"), "1")
        client.call("ZADD", base + ":leases", 0, "b")
        expect(op("claim", "", "new-owner-b", 60000, "stamp", 16), "b")
        expect(op("complete", "b", "b", "owner-b", "SUCCESS", "stamp", "", "stale.zip"), "0")
        expect(op("complete", "b", "b", "new-owner-b", "FAILED", "stamp", "failed", ""), "1")
        expect(client.call("SCARD", base + ":active"), 0)
        expect(client.call("GET", base + ":total-bytes"), "30")
        expect(op("reserve", "c", "c", 80, 2, 100), "0")
        expect(op("beginDelete", "a", "a", 0), "1")
        expect(op("finishDelete", "a", "a"), "1")
        expect(client.call("GET", base + ":total-bytes"), "20")
        expect(op("reserve", "c", "c", 80, 2, 100), "1")
        expect(op("cancelUpload", "c", "c", 0), "1")
        expect(op("create", "c", "c", "/unused/c", "stamp", 2), "0")
        expect(op("releaseUpload", "c", "c"), "1")
        expect(op("reserve", "d", "d", 10, 2, 100), "1")
        expect(op("create", "d", "d", "/unused/d", "stamp", 2), "1")
        expect(op("claim", "", "owner-d", 60000, "stamp", 16), "d")
        expect(op("beginDelete", "d", "d", 0), "0")

        def reserve(job):
            connection = Redis(path)
            try:
                return execute(connection, "reserve", job, job, 1, 2, 100)
            finally:
                connection.close()

        with concurrent.futures.ThreadPoolExecutor(max_workers=8) as executor:
            results = list(executor.map(reserve, jobs[4:]))
        expect(results.count("1"), 1)
        expect(client.call("SCARD", base + ":active"), 2)
        print("Redis lifecycle smoke: %d checks passed" % checks)
    finally:
        # Only this random test namespace is removed; never FLUSHDB/FLUSHALL.
        keys = [key for key in fixed if key] + [prefix + job for job in jobs]
        assert all(key.startswith(namespace + ":") for key in keys)
        client.call("UNLINK", *keys)
        client.close()


if __name__ == "__main__":
    main()
