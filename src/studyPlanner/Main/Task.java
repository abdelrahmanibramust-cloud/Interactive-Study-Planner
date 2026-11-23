package studyplanner;

import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public class Task {
    private static final DateTimeFormatter DATE_FORMAT =
            DateTimeFormatter.ofPattern("d MMM yyyy", Locale.ENGLISH);

    private final String id;
    private final String name;
    private final String subject;
    private final LocalDate date;
    private final Priority priority;

    public Task(String id, String name, String subject, LocalDate date, Priority priority) {
        this.id = id;
        this.name = name;
        this.subject = subject;
        this.date = date;
        this.priority = priority;
    }

    public String getId() {
        return id;
    }

    public String getName() {
        return name;
    }

    public String getSubject() {
        return subject;
    }

    public LocalDate getDate() {
        return date;
    }

    public Priority getPriority() {
        return priority;
    }

    public String getDateText() {
        return date.format(DATE_FORMAT);
    }
}