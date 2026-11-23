package studyplanner;

import java.awt.Desktop;
import java.io.IOException;
import java.net.URI;
import java.nio.file.Path;
import java.nio.file.Paths;

public class Main {
    public static void main(String[] args) {
        Path dataFile = Paths.get(System.getProperty("user.home"), ".study-planner", "tasks.txt");

        StorageService storageService = new StorageService(dataFile);
        TaskManager taskManager = new TaskManager(storageService);
        ExportService exportService = new ExportService();
        WebServer webServer = new WebServer(taskManager, exportService);

        try {
            int port = webServer.start();
            String url = "http://localhost:" + port + "/";
            System.out.println("Study Planner is running at " + url);
            System.out.println("Use the Stop button in the page, or stop the program in NetBeans, to quit.");
            openBrowser(url);
        } catch (IOException e) {
            System.out.println("Could not start the planner: " + e.getMessage());
        }
    }

    private static void openBrowser(String url) {
        try {
            if (Desktop.isDesktopSupported() && Desktop.getDesktop().isSupported(Desktop.Action.BROWSE)) {
                Desktop.getDesktop().browse(new URI(url));
            } else {
                System.out.println("Open this link in your browser: " + url);
            }
        } catch (Exception e) {
            System.out.println("Open this link in your browser: " + url);
        }
    }
}