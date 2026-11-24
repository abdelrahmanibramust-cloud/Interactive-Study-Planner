# Interactive Study Planner

A study planner for university students. Add tasks with a subject, due date and priority, see them in a color-coded schedule sorted by deadline, and export the schedule as a PDF or a PNG image.

The app is written in Java. A small built-in web server runs on your computer and shows the planner as a web page in your browser. Everything stays on your machine, with no account, no database and no internet needed.

Course project for Software Implementation Project, Near East University.

## Features

- Add tasks with a name, subject, due date and priority
- Colors show urgency at a glance: red for high, yellow for medium, green for low
- Tasks are sorted automatically by due date, then by priority
- Delete tasks you no longer need
- Tasks are saved automatically and are still there the next time you open the app
- Export the schedule as a PDF or a PNG image
- Clear messages when a required field is empty or there is nothing to export

## Run it

You need Java 8 or newer installed.

1. Download `StudyPlanner.jar` from the [Releases](../../releases) page.
2. Double-click it, or run this in a terminal:

   ```
   java -jar StudyPlanner.jar
   ```

3. Your browser opens on `http://localhost:8080`. If it does not, open that link yourself. If port 8080 is busy, the app uses the next free port up to 8090 and prints the address.
4. When you are done, click **Stop planner** in the page.

Your tasks are saved in a text file at `.study-planner/tasks.txt` inside your user folder.

## Build from source

1. Clone this repository.
2. Open the folder as a project in NetBeans (File > Open Project).
3. Choose Clean and Build, then run `Main.java`.

The web page files (`index.html`, `style.css`, `app.js`) live in `src/web` and are packaged into the jar during the build.

## How it works

- `Main` starts the server and opens the browser
- `WebServer` serves the page and answers its requests
- `TaskManager` holds the task list: add, remove, sort, validate
- `StorageService` saves and loads tasks from the text file
- `ExportService` draws the schedule and creates the PDF and PNG
- `Task` and `Priority` are one task, and a priority level with its color
- `src/web` is the page the browser shows (HTML, CSS and JavaScript)

The page sends requests to the Java server, which does all the real work: validating input, saving, sorting and exporting.

## Limitations

- It runs only on the computer where the jar is started. It is not hosted online, and there is no sharing between devices.
- The exported PDF is a picture of the schedule on one page, so its text cannot be selected.
- Data is stored in one file on one computer. Deleting that file removes your tasks.

## Technologies

- Java (built-in `HttpServer`, `java.time`, `javax.imageio`), no external libraries
- HTML, CSS and JavaScript for the page
- NetBeans and Apache Ant for building
