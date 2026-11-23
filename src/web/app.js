class ApiClient {
    async request(url, options) {
        let response;

        try {
            response = await fetch(url, options);
        } catch (error) {
            throw new Error("Cannot reach the planner. Is the Java program still running?");
        }

        let data = null;
        try {
            data = await response.json();
        } catch (error) {
            data = null;
        }

        if (!response.ok) {
            const message = data && data.error ? data.error : "Something went wrong.";
            throw new Error(message);
        }

        return data;
    }

    getTasks() {
        return this.request("/api/tasks");
    }

    addTask(input) {
        const body = new URLSearchParams(input);
        return this.request("/api/tasks", {
            method: "POST",
            headers: { "Content-Type": "application/x-www-form-urlencoded" },
            body: body.toString()
        });
    }

    deleteTask(id) {
        return this.request("/api/tasks/" + encodeURIComponent(id), { method: "DELETE" });
    }

    quit() {
        return this.request("/api/quit", { method: "POST" });
    }
}

class TaskView {
    constructor() {
        this.form = document.getElementById("task-form");
        this.nameInput = document.getElementById("task-name");
        this.subjectInput = document.getElementById("task-subject");
        this.dateInput = document.getElementById("task-date");
        this.priorityInput = document.getElementById("task-priority");
        this.formMessage = document.getElementById("form-message");
        this.exportMessage = document.getElementById("export-message");
        this.tableBody = document.getElementById("task-body");
        this.table = document.getElementById("task-table");
        this.emptyState = document.getElementById("empty-state");
        this.pdfButton = document.getElementById("export-pdf");
        this.imageButton = document.getElementById("export-image");
        this.quitButton = document.getElementById("quit-button");
    }

    showMessage(element, text, isGood) {
        element.textContent = text;
        if (isGood) {
            element.classList.add("ok");
        } else {
            element.classList.remove("ok");
        }
    }

    showFormMessage(text, isGood) {
        this.showMessage(this.formMessage, text, isGood);
    }

    showExportMessage(text, isGood) {
        this.showMessage(this.exportMessage, text, isGood);
    }

    readForm() {
        return {
            name: this.nameInput.value.trim(),
            subject: this.subjectInput.value.trim(),
            date: this.dateInput.value,
            priority: this.priorityInput.value
        };
    }

    resetForm() {
        this.form.reset();
        this.priorityInput.value = "medium";
        this.nameInput.focus();
    }

    getInputByField(field) {
        if (field === "name") {
            return this.nameInput;
        }
        if (field === "subject") {
            return this.subjectInput;
        }
        return this.dateInput;
    }

    clearInvalid() {
        this.nameInput.classList.remove("invalid");
        this.subjectInput.classList.remove("invalid");
        this.dateInput.classList.remove("invalid");
    }

    markInvalid(fields) {
        for (let i = 0; i < fields.length; i++) {
            this.getInputByField(fields[i]).classList.add("invalid");
        }
        this.getInputByField(fields[0]).focus();
    }

    createCell(text) {
        const cell = document.createElement("td");
        cell.textContent = text;
        return cell;
    }

    createDeleteCell(task, onDelete) {
        const cell = document.createElement("td");
        cell.className = "col-action";

        const button = document.createElement("button");
        button.type = "button";
        button.className = "delete-btn";
        button.textContent = "Delete";
        button.setAttribute("aria-label", "Delete task " + task.name);
        button.addEventListener("click", function () {
            onDelete(task.id);
        });

        cell.appendChild(button);
        return cell;
    }

    renderTasks(tasks, onDelete) {
        this.tableBody.textContent = "";

        for (let i = 0; i < tasks.length; i++) {
            const task = tasks[i];
            const row = document.createElement("tr");
            row.className = task.priority;

            row.appendChild(this.createCell(task.name));
            row.appendChild(this.createCell(task.subject));
            row.appendChild(this.createCell(task.dateText));

            const priorityCell = this.createCell(task.priorityLabel);
            priorityCell.className = "priority-tag";
            row.appendChild(priorityCell);

            row.appendChild(this.createDeleteCell(task, onDelete));
            this.tableBody.appendChild(row);
        }

        if (tasks.length > 0) {
            this.table.classList.remove("hidden");
            this.emptyState.classList.add("hidden");
        } else {
            this.table.classList.add("hidden");
            this.emptyState.classList.remove("hidden");
        }
    }

    showStopped() {
        document.body.textContent = "";
        const box = document.createElement("div");
        box.className = "panel stopped";
        box.textContent = "The planner has stopped. You can close this tab.";
        document.body.appendChild(box);
    }
}

class App {
    constructor() {
        this.api = new ApiClient();
        this.view = new TaskView();
        this.taskCount = 0;
    }

    start() {
        this.view.form.addEventListener("submit", (event) => this.handleSubmit(event));
        this.view.pdfButton.addEventListener("click", () => this.handleExport("pdf"));
        this.view.imageButton.addEventListener("click", () => this.handleExport("png"));
        this.view.quitButton.addEventListener("click", () => this.handleQuit());
        this.refresh();
    }

    async refresh() {
        try {
            const tasks = await this.api.getTasks();
            this.taskCount = tasks.length;
            this.view.renderTasks(tasks, (id) => this.handleDelete(id));
        } catch (error) {
            this.view.showFormMessage(error.message, false);
        }
    }

    findMissingFields(input) {
        const missing = [];

        if (input.name === "") {
            missing.push({ field: "name", label: "task name" });
        }
        if (input.subject === "") {
            missing.push({ field: "subject", label: "subject" });
        }
        if (input.date === "") {
            missing.push({ field: "date", label: "due date" });
        }

        return missing;
    }

    async handleSubmit(event) {
        event.preventDefault();
        this.view.clearInvalid();

        const input = this.view.readForm();
        const missing = this.findMissingFields(input);

        if (missing.length > 0) {
            const labels = [];
            const fields = [];
            for (let i = 0; i < missing.length; i++) {
                labels.push(missing[i].label);
                fields.push(missing[i].field);
            }
            this.view.showFormMessage("Please fill in: " + labels.join(", ") + ".", false);
            this.view.markInvalid(fields);
            return;
        }

        try {
            const result = await this.api.addTask(input);
            await this.refresh();
            this.view.resetForm();

            if (result.saved) {
                this.view.showFormMessage("Task added.", true);
            } else {
                this.view.showFormMessage("Task added, but it could not be saved to disk.", false);
            }
        } catch (error) {
            this.view.showFormMessage(error.message, false);
        }
    }

    async handleDelete(id) {
        try {
            await this.api.deleteTask(id);
            await this.refresh();
            this.view.showFormMessage("", false);
        } catch (error) {
            this.view.showFormMessage(error.message, false);
        }
    }

    handleExport(kind) {
        if (this.taskCount === 0) {
            this.view.showExportMessage("Add at least one task before exporting.", false);
            return;
        }

        window.location.href = "/api/export/" + kind;
        const name = kind === "pdf" ? "PDF" : "Image";
        this.view.showExportMessage(name + " download started.", true);
    }

    async handleQuit() {
        const sure = window.confirm("Stop the planner? Your tasks stay saved.");
        if (!sure) {
            return;
        }

        try {
            await this.api.quit();
        } catch (error) {
            this.view.showFormMessage(error.message, false);
            return;
        }
        this.view.showStopped();
    }
}

const app = new App();
app.start();
