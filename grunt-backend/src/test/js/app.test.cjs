const test = require("node:test");
const assert = require("node:assert/strict");
const fs = require("node:fs");
const vm = require("node:vm");
const path = require("node:path");
const source = fs.readFileSync(path.join(__dirname, "../../main/resources/static/app.js"), "utf8");
const flush = () => new Promise(setImmediate);

function harness(initial) {
    const timers = new Map(), pending = [], elements = new Map(), listeners = new Map();
    let timerId = 0, calls = 0, active = 0, maxActive = 0, renders = 0, stored = "";
    const element = () => ({
        addEventListener() {}, classList: { toggle() {} }, style: {}, appendChild() {},
        set innerHTML(value) { renders++; }, set textContent(value) {}, set className(value) {},
    });
    const document = { hidden: false, addEventListener: (name, fn) => listeners.set(name, fn),
        querySelector: (selector) => { if (!elements.has(selector)) elements.set(selector, element()); return elements.get(selector); },
        createElement: element };
    const context = vm.createContext({ document, AbortController, console, Date, Math, Promise,
        window: { addEventListener: (name, fn) => listeners.set(name, fn), setTimeout: (fn, delay) => { timers.set(++timerId, { fn, delay }); return timerId; },
            clearTimeout: (id) => timers.delete(id),
            localStorage: { getItem: () => JSON.stringify(initial), setItem: (_, value) => { stored = value; } } },
        fetch: (url, options) => {
            calls++; active++; maxActive = Math.max(maxActive, active);
            return new Promise((resolve, reject) => {
                let done = false;
                const finish = (action) => { if (!done) { done = true; active--; action(); } };
                const request = { url, respond: (status = 200, jobStatus = "SUCCESS") => finish(() => resolve({
                    status, ok: status === 200, json: async () => ({ jobId: url.split("/").pop(), status: jobStatus }),
                })) };
                options.signal.addEventListener("abort", () => finish(() => reject(new Error("aborted"))));
                pending.push(request);
            });
        },
    });
    vm.runInContext(source, context);
    return { context, pending, timers, document, listeners, eval: (s) => vm.runInContext(s, context),
        get calls() { return calls; }, get active() { return active; }, get maxActive() { return maxActive; },
        get renders() { return renders; }, get stored() { return stored; },
        async drain(status = 200, jobStatus = "SUCCESS") {
            while (pending.length) { pending.splice(0).forEach((r) => r.respond(status, jobStatus)); await flush(); }
        },
        async tick() { const [id, timer] = timers.entries().next().value; timers.delete(id); timer.fn(); await flush(); },
    };
}
const jobs = (count, status = "QUEUED") => Array.from({ length: count }, (_, i) => ({ jobId: "job-" + i, status }));

for (const count of [1, 30, 100]) test("single flight, capped requests and stop at terminal: " + count, async () => {
    const h = harness(jobs(count));
    const first = h.eval("refreshJobs()");
    assert.equal(first, h.eval("refreshJobs()"));
    assert.equal(h.calls, Math.min(count, 4));
    await h.drain(); await first;
    assert.equal(h.calls, count);
    assert.ok(h.maxActive <= 4);
    assert.equal(h.timers.size, 0);
    await h.eval("refreshJobs()");
    assert.equal(h.calls, count);
});

test("manual terminal refresh handles deleted result and 404", async () => {
    const h = harness(jobs(1, "SUCCESS")); await flush();
    assert.equal(h.calls, 0);
    const refresh = h.eval("refreshJobs(true)"); await h.drain(404); await refresh;
    assert.equal(h.eval("jobs.size"), 0);
    assert.equal(h.timers.size, 0);
});

test("503 backoff is bounded; unchanged response does not rebuild DOM", async () => {
    const h = harness(jobs(1));
    await h.drain(200, "QUEUED");
    const before = h.renders;
    await h.tick(); await h.drain(200, "QUEUED");
    assert.equal(h.renders, before);
    for (let i = 0; i < 8; i++) { await h.tick(); await h.drain(503); }
    const delay = [...h.timers.values()][0].delay;
    assert.ok(delay >= 24000 && delay <= 30000);
});

test("hidden document aborts requests and resumes without overlapping cycles", async () => {
    const h = harness(jobs(30));
    h.document.hidden = true; h.listeners.get("visibilitychange")(); await flush();
    assert.equal(h.active, 0); assert.equal(h.timers.size, 0);
    h.document.hidden = false; h.listeners.get("visibilitychange")();
    await h.drain();
    assert.ok(h.maxActive <= 4); assert.equal(h.timers.size, 0);
});

test("live map and persisted history are bounded, terminal entries evicted first", async () => {
    const h = harness(jobs(1, "SUCCESS")); await flush();
    h.eval('for(let i=0;i<130;i++) jobs.set("new-"+i, {jobId:"new-"+i,status:"QUEUED"}); persistJobs()');
    assert.equal(h.eval("jobs.size"), 100);
    assert.equal(h.eval('jobs.has("job-0")'), false);
    assert.equal(JSON.parse(h.stored).length, 100);
});

test("request timeout recovers and cannot leave a permanent in-flight cycle", async () => {
    const h = harness(jobs(1));
    await h.tick();
    assert.equal(h.active, 0);
    assert.equal(h.eval("pollInFlight"), null);
    assert.equal(h.timers.size, 1);
});
