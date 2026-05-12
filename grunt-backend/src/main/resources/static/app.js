const form = document.querySelector("#uploadForm");
const configFile = document.querySelector("#configFile");
const inputFile = document.querySelector("#inputFile");
const libsFile = document.querySelector("#libsFile");
const configName = document.querySelector("#configName");
const inputName = document.querySelector("#inputName");
const libsName = document.querySelector("#libsName");
const submitButton = document.querySelector("#submitButton");
const message = document.querySelector("#formMessage");
const progress = document.querySelector("#uploadProgress");
const jobsBody = document.querySelector("#jobsBody");
const refreshButton = document.querySelector("#refreshButton");
const apiStatus = document.querySelector("#apiStatus");

const storageKey = "grunteon.jobs";
const jobs = new Map(loadJobs().map((job) => [job.jobId, job]));
let pollTimer = 0;

bindFileName(configFile, configName, "No file selected");
bindFileName(inputFile, inputName, "No file selected");
bindFileName(libsFile, libsName, "No files selected");

form.addEventListener("submit", (event) => {
    event.preventDefault();
    submitJob();
});

refreshButton.addEventListener("click", () => {
    refreshJobs();
});

renderJobs();
refreshJobs();
pollTimer = window.setInterval(refreshJobs, 2000);

function bindFileName(input, target, fallback) {
    input.addEventListener("change", () => {
        const files = Array.from(input.files || []);
        if (files.length === 0) {
            target.textContent = fallback;
        } else if (files.length === 1) {
            target.textContent = files[0].name;
        } else {
            target.textContent = `${files.length} files selected`;
        }
    });
}

function submitJob() {
    clearMessage();
    setProgress(0);

    const config = configFile.files[0];
    const input = inputFile.files[0];
    if (!config || !input) {
        showMessage("Config JSON and input JAR are required.", true);
        return;
    }

    const formData = new FormData();
    formData.append("config", config);
    formData.append("input", input);
    Array.from(libsFile.files || []).forEach((file) => {
        formData.append("libs", file);
    });

    submitButton.disabled = true;
    showMessage("Uploading...");

    const request = new XMLHttpRequest();
    request.open("POST", "/api/jobs");
    request.upload.addEventListener("progress", (event) => {
        if (event.lengthComputable) {
            setProgress(Math.round((event.loaded / event.total) * 100));
        }
    });
    request.addEventListener("load", () => {
        submitButton.disabled = false;
        if (request.status >= 200 && request.status < 300) {
            const response = JSON.parse(request.responseText);
            jobs.set(response.jobId, {
                jobId: response.jobId,
                status: response.status,
                updatedAt: new Date().toISOString(),
                resultAvailable: false,
                error: null,
            });
            persistJobs();
            renderJobs();
            showMessage(`Job queued: ${shortId(response.jobId)}`);
            form.reset();
            configName.textContent = "No file selected";
            inputName.textContent = "No file selected";
            libsName.textContent = "No files selected";
            setProgress(100);
            refreshJobs();
        } else {
            showMessage(readError(request.responseText, request.status), true);
            setProgress(0);
        }
    });
    request.addEventListener("error", () => {
        submitButton.disabled = false;
        showMessage("Upload failed.", true);
        setProgress(0);
    });
    request.send(formData);
}

async function refreshJobs() {
    if (jobs.size === 0) {
        setApiStatus("Ready", "ok");
        return;
    }

    const entries = Array.from(jobs.values());
    let ok = true;
    for (const job of entries) {
        try {
            const response = await fetch(`/api/jobs/${encodeURIComponent(job.jobId)}`, {
                cache: "no-store",
            });
            if (!response.ok) {
                ok = false;
                continue;
            }
            const data = await response.json();
            jobs.set(data.jobId, data);
        } catch (error) {
            ok = false;
        }
    }
    persistJobs();
    renderJobs();
    setApiStatus(ok ? "Ready" : "Offline", ok ? "ok" : "danger");
}

function renderJobs() {
    const entries = Array.from(jobs.values())
        .sort((a, b) => new Date(b.updatedAt || 0) - new Date(a.updatedAt || 0));

    if (entries.length === 0) {
        jobsBody.innerHTML = '<tr><td colspan="4" class="empty">No jobs yet</td></tr>';
        return;
    }

    jobsBody.innerHTML = "";
    entries.forEach((job) => {
        const row = document.createElement("tr");
        row.innerHTML = `
            <td>
                <div class="job-id" title="${escapeHtml(job.jobId)}">${escapeHtml(job.jobId)}</div>
                ${job.error ? `<span class="error-line" title="${escapeHtml(job.error)}">${escapeHtml(job.error)}</span>` : ""}
            </td>
            <td>${statusMarkup(job.status)}</td>
            <td>${formatDate(job.updatedAt)}</td>
            <td>${resultMarkup(job)}</td>
        `;
        jobsBody.appendChild(row);
    });
}

function statusMarkup(status) {
    const value = String(status || "QUEUED");
    return `<span class="status-text ${value.toLowerCase()}">${escapeHtml(value)}</span>`;
}

function resultMarkup(job) {
    if (job.resultAvailable && job.status === "SUCCESS") {
        const url = `/api/jobs/${encodeURIComponent(job.jobId)}/result`;
        return `<a class="download-button" href="${url}">Download</a>`;
    }
    return '<span class="muted">Pending</span>';
}

function loadJobs() {
    try {
        const parsed = JSON.parse(window.localStorage.getItem(storageKey) || "[]");
        return Array.isArray(parsed) ? parsed : [];
    } catch (error) {
        return [];
    }
}

function persistJobs() {
    const entries = Array.from(jobs.values()).slice(-30);
    window.localStorage.setItem(storageKey, JSON.stringify(entries));
}

function readError(body, status) {
    try {
        const parsed = JSON.parse(body);
        return parsed.message || `Request failed with ${status}.`;
    } catch (error) {
        return `Request failed with ${status}.`;
    }
}

function showMessage(text, isError = false) {
    message.textContent = text;
    message.classList.toggle("error", isError);
}

function clearMessage() {
    showMessage("");
}

function setProgress(value) {
    progress.style.width = `${Math.max(0, Math.min(100, value))}%`;
}

function setApiStatus(text, kind) {
    apiStatus.textContent = text;
    apiStatus.className = `status-pill ${kind}`;
}

function formatDate(value) {
    if (!value) return "-";
    const date = new Date(value);
    if (Number.isNaN(date.getTime())) return "-";
    return date.toLocaleString();
}

function shortId(value) {
    return String(value || "").slice(0, 8);
}

function escapeHtml(value) {
    return String(value || "")
        .replaceAll("&", "&amp;")
        .replaceAll("<", "&lt;")
        .replaceAll(">", "&gt;")
        .replaceAll('"', "&quot;")
        .replaceAll("'", "&#039;");
}

window.addEventListener("beforeunload", () => {
    window.clearInterval(pollTimer);
});
