package studyplanner;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;

public class StorageService {
    private final Path file;

    public StorageService(Path file) {
        this.file = file;
    }

    public List<Task> load() {
        List<Task> tasks = new ArrayList<>();

        if (!Files.exists(file)) {
            return tasks;
        }

        try {
            List<String> lines = Files.readAllLines(file, StandardCharsets.UTF_8);
            for (String line : lines) {
                Task task = parseLine(line);
                if (task != null) {
                    tasks.add(task);
                }
            }
        } catch (IOException e) {
            return new ArrayList<>();
        }

        return tasks;
    }

    public boolean save(List<Task> tasks) {
        List<String> lines = new ArrayList<>();

        for (Task task : tasks) {
            lines.add(toLine(task));
        }

        try {
            Path folder = file.getParent();
            if (folder != null) {
                Files.createDirectories(folder);
            }
            Files.write(file, lines, StandardCharsets.UTF_8);
            return true;
        } catch (IOException e) {
            return false;
        }
    }

    private String toLine(Task task) {
        return task.getId() + "\t"
                + escape(task.getName()) + "\t"
                + escape(task.getSubject()) + "\t"
                + task.getDate() + "\t"
                + task.getPriority().name();
    }

    private Task parseLine(String line) {
        String[] parts = line.split("\t", -1);
        if (parts.length != 5) {
            return null;
        }

        try {
            String id = parts[0];
            String name = unescape(parts[1]);
            String subject = unescape(parts[2]);
            LocalDate date = LocalDate.parse(parts[3]);
            Priority priority = Priority.valueOf(parts[4]);
            return new Task(id, name, subject, date, priority);
        } catch (RuntimeException e) {
            return null;
        }
    }

    private String escape(String text) {
        return text.replace("\\", "\\\\").replace("\t", "\\t").replace("\n", "\\n").replace("\r", "\\r");
    }

    private String unescape(String text) {
        StringBuilder result = new StringBuilder();

        for (int i = 0; i < text.length(); i++) {
            char c = text.charAt(i);
            if (c == '\\' && i + 1 < text.length()) {
                i++;
                char next = text.charAt(i);
                switch (next) {
                    case 't' -> result.append('\t');
                    case 'n' -> result.append('\n');
                    case 'r' -> result.append('\r');
                    default -> result.append(next);
                }
            } else {
                result.append(c);
            }
        }

        return result.toString();
    }
}