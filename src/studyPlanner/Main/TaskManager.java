package studyplanner;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;

public class TaskManager {
    private final StorageService storageService;
    private List<Task> tasks;

    public TaskManager(StorageService storageService) {
        this.storageService = storageService;
        this.tasks = storageService.load();
    }

    public int getCount() {
        return tasks.size();
    }

    public List<Task> getSortedTasks() {
        List<Task> copy = new ArrayList<>(tasks);
        copy.sort(Comparator.comparing(Task::getDate).thenComparing(Task::getPriority));
        return copy;
    }

    public List<String> findMissingFields(String name, String subject) {
        List<String> missing = new ArrayList<>();

        if (name.isEmpty()) {
            missing.add("task name");
        }
        if (subject.isEmpty()) {
            missing.add("subject");
        }

        return missing;
    }

    public boolean addTask(String name, String subject, LocalDate date, Priority priority) {
        Task task = new Task(UUID.randomUUID().toString(), name, subject, date, priority);
        tasks.add(task);
        return storageService.save(tasks);
    }

    public boolean removeTask(String id) {
        List<Task> remaining = new ArrayList<>();

        for (Task task : tasks) {
            if (!task.getId().equals(id)) {
                remaining.add(task);
            }
        }

        tasks = remaining;
        return storageService.save(tasks);
    }
}