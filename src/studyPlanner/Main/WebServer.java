package studyplanner;

import com.sun.net.httpserver.Headers;
import com.sun.net.httpserver.HttpExchange;
import com.sun.net.httpserver.HttpServer;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.net.BindException;
import java.net.InetAddress;
import java.net.InetSocketAddress;
import java.net.URLDecoder;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class WebServer {
    private static final int FIRST_PORT = 8080;
    private static final int LAST_PORT = 8090;
    private static final int MAX_BODY_BYTES = 10000;
    private static final int MAX_NAME_LENGTH = 80;
    private static final int MAX_SUBJECT_LENGTH = 50;

    private final TaskManager taskManager;
    private final ExportService exportService;
    private HttpServer server;
    private int port;

    public WebServer(TaskManager taskManager, ExportService exportService) {
        this.taskManager = taskManager;
        this.exportService = exportService;
    }

    public int start() throws IOException {
        for (int candidate = FIRST_PORT; candidate <= LAST_PORT && server == null; candidate++) {
            try {
                InetSocketAddress address = new InetSocketAddress(InetAddress.getLoopbackAddress(), candidate);
                server = HttpServer.create(address, 0);
                port = candidate;
            } catch (BindException e) {
                server = null;
            }
        }

        if (server == null) {
            throw new IOException("No free port between " + FIRST_PORT + " and " + LAST_PORT);
        }

        server.createContext("/", exchange -> handleStatic(exchange));
        server.createContext("/api/", exchange -> handleApi(exchange));
        server.start();
        return port;
    }

    private void handleStatic(HttpExchange exchange) throws IOException {
        try {
            if (!isLocalRequest(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            String path = exchange.getRequestURI().getPath();
            Map<String, String> files = new HashMap<>();
            files.put("/", "index.html");
            files.put("/index.html", "index.html");
            files.put("/style.css", "style.css");
            files.put("/app.js", "app.js");

            String fileName = files.get(path);
            if (fileName == null || !exchange.getRequestMethod().equals("GET")) {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
                return;
            }

            byte[] content = readResource("/web/" + fileName);
            if (content == null) {
                sendJson(exchange, 404, "{\"error\":\"File missing from the project\"}");
                return;
            }

            sendBytes(exchange, 200, contentTypeFor(fileName), content, null);
        } finally {
            exchange.close();
        }
    }

    private void handleApi(HttpExchange exchange) throws IOException {
        try {
            if (!isLocalRequest(exchange)) {
                sendJson(exchange, 403, "{\"error\":\"Forbidden\"}");
                return;
            }

            String method = exchange.getRequestMethod();
            String path = exchange.getRequestURI().getPath();

            if (path.equals("/api/tasks") && method.equals("GET")) {
                sendJson(exchange, 200, tasksToJson(taskManager.getSortedTasks()));
            } else if (path.equals("/api/tasks") && method.equals("POST")) {
                handleAdd(exchange);
            } else if (path.startsWith("/api/tasks/") && method.equals("DELETE")) {
                String id = path.substring("/api/tasks/".length());
                boolean saved = taskManager.removeTask(id);
                sendJson(exchange, 200, "{\"saved\":" + saved + "}");
            } else if (path.equals("/api/export/pdf") && method.equals("GET")) {
                handleExport(exchange, true);
            } else if (path.equals("/api/export/png") && method.equals("GET")) {
                handleExport(exchange, false);
            } else if (path.equals("/api/quit") && method.equals("POST")) {
                handleQuit(exchange);
            } else {
                sendJson(exchange, 404, "{\"error\":\"Not found\"}");
            }
        } catch (Exception e) {
            sendJson(exchange, 500, "{\"error\":\"Server error\"}");
        } finally {
            exchange.close();
        }
    }

    private void handleAdd(HttpExchange exchange) throws IOException {
        Map<String, String> form = readForm(exchange);
        if (form == null) {
            sendJson(exchange, 400, "{\"error\":\"Request too large\"}");
            return;
        }

        String name = form.getOrDefault("name", "").trim();
        String subject = form.getOrDefault("subject", "").trim();
        String dateText = form.getOrDefault("date", "").trim();
        String priorityText = form.getOrDefault("priority", "").trim().toUpperCase();

        List<String> missing = taskManager.findMissingFields(name, subject);
        if (dateText.isEmpty()) {
            missing.add("due date");
        }
        if (!missing.isEmpty()) {
            sendJson(exchange, 400, "{\"error\":" + quote("Please fill in: " + String.join(", ", missing) + ".") + "}");
            return;
        }

        if (name.length() > MAX_NAME_LENGTH || subject.length() > MAX_SUBJECT_LENGTH) {
            sendJson(exchange, 400, "{\"error\":\"Task name or subject is too long.\"}");
            return;
        }

        LocalDate date;
        try {
            date = LocalDate.parse(dateText);
        } catch (RuntimeException e) {
            sendJson(exchange, 400, "{\"error\":\"The due date is not valid.\"}");
            return;
        }

        Priority priority;
        try {
            priority = Priority.valueOf(priorityText);
        } catch (RuntimeException e) {
            sendJson(exchange, 400, "{\"error\":\"Please choose a priority.\"}");
            return;
        }

        boolean saved = taskManager.addTask(name, subject, date, priority);
        sendJson(exchange, 201, "{\"saved\":" + saved + "}");
    }

    private void handleExport(HttpExchange exchange, boolean asPdf) throws IOException {
        if (taskManager.getCount() == 0) {
            sendJson(exchange, 400, "{\"error\":\"Add at least one task before exporting.\"}");
            return;
        }

        List<Task> tasks = taskManager.getSortedTasks();
        byte[] content = asPdf ? exportService.createPdf(tasks) : exportService.createPng(tasks);
        String extension = asPdf ? "pdf" : "png";
        String type = asPdf ? "application/pdf" : "image/png";

        Map<String, String> headers = new HashMap<>();
        headers.put("Content-Disposition", "attachment; filename=\"study-schedule-" + LocalDate.now() + "." + extension + "\"");
        sendBytes(exchange, 200, type, content, headers);
    }

    private void handleQuit(HttpExchange exchange) throws IOException {
        sendJson(exchange, 200, "{\"ok\":true}");

        Thread stopper = new Thread(new Runnable() {
            @Override
            public void run() {
                try {
                    Thread.sleep(400);
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                }
                server.stop(0);
                System.exit(0);
            }
        });
        stopper.start();
    }

    private boolean isLocalRequest(HttpExchange exchange) {
        Headers headers = exchange.getRequestHeaders();
        String host = headers.getFirst("Host");
        String origin = headers.getFirst("Origin");
        String localhost = "localhost:" + port;
        String loopback = "127.0.0.1:" + port;

        if (host == null || !(host.equals(localhost) || host.equals(loopback))) {
            return false;
        }
        if (origin != null && !(origin.equals("http://" + localhost) || origin.equals("http://" + loopback))) {
            return false;
        }
        return true;
    }

    private Map<String, String> readForm(HttpExchange exchange) throws IOException {
        InputStream in = exchange.getRequestBody();
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        byte[] chunk = new byte[1024];
        int read;

        while ((read = in.read(chunk)) != -1) {
            buffer.write(chunk, 0, read);
            if (buffer.size() > MAX_BODY_BYTES) {
                return null;
            }
        }

        Map<String, String> form = new HashMap<>();
        String body = new String(buffer.toByteArray(), StandardCharsets.UTF_8);

        for (String pair : body.split("&")) {
            int equals = pair.indexOf('=');
            if (equals > 0) {
                String key = URLDecoder.decode(pair.substring(0, equals), "UTF-8");
                String value = URLDecoder.decode(pair.substring(equals + 1), "UTF-8");
                form.put(key, value);
            }
        }

        return form;
    }

    private byte[] readResource(String path) throws IOException {
        InputStream in = WebServer.class.getResourceAsStream(path);
        if (in == null) {
            return null;
        }

        try {
            ByteArrayOutputStream buffer = new ByteArrayOutputStream();
            byte[] chunk = new byte[4096];
            int read;
            while ((read = in.read(chunk)) != -1) {
                buffer.write(chunk, 0, read);
            }
            return buffer.toByteArray();
        } finally {
            in.close();
        }
    }

    private String contentTypeFor(String fileName) {
        if (fileName.endsWith(".html")) {
            return "text/html; charset=utf-8";
        }
        if (fileName.endsWith(".css")) {
            return "text/css; charset=utf-8";
        }
        return "application/javascript; charset=utf-8";
    }

    private String tasksToJson(List<Task> tasks) {
        List<String> items = new ArrayList<>();

        for (Task task : tasks) {
            items.add("{\"id\":" + quote(task.getId())
                    + ",\"name\":" + quote(task.getName())
                    + ",\"subject\":" + quote(task.getSubject())
                    + ",\"date\":" + quote(task.getDate().toString())
                    + ",\"dateText\":" + quote(task.getDateText())
                    + ",\"priority\":" + quote(task.getPriority().name().toLowerCase())
                    + ",\"priorityLabel\":" + quote(task.getPriority().getLabel()) + "}");
        }

        return "[" + String.join(",", items) + "]";
    }

    private String quote(String text) {
        StringBuilder result = new StringBuilder("\"");

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '"') {
                result.append("\\\"");
            } else if (c == '\\') {
                result.append("\\\\");
            } else if (c < 0x20) {
                result.append(String.format("\\u%04x", (int) c));
            } else {
                result.append(c);
            }
        }

        return result.append("\"").toString();
    }

    private void sendJson(HttpExchange exchange, int status, String json) throws IOException {
        sendBytes(exchange, status, "application/json; charset=utf-8", json.getBytes(StandardCharsets.UTF_8), null);
    }

    private void sendBytes(HttpExchange exchange, int status, String contentType, byte[] content,
            Map<String, String> extraHeaders) throws IOException {
        Headers headers = exchange.getResponseHeaders();
        headers.set("Content-Type", contentType);
        headers.set("Cache-Control", "no-store");
        headers.set("X-Content-Type-Options", "nosniff");

        if (extraHeaders != null) {
            for (Map.Entry<String, String> entry : extraHeaders.entrySet()) {
                headers.set(entry.getKey(), entry.getValue());
            }
        }

        exchange.sendResponseHeaders(status, content.length);
        OutputStream out = exchange.getResponseBody();
        out.write(content);
        out.close();
    }
}