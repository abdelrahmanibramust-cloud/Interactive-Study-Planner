package studyplanner;

import java.awt.Color;
import java.awt.Font;
import java.awt.FontMetrics;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Locale;
import javax.imageio.ImageIO;

public class ExportService {
    private static final int IMAGE_WIDTH = 1000;
    private static final int MARGIN = 30;
    private static final int ROW_HEIGHT = 40;
    private static final int TITLE_AREA = 80;
    private static final int[] COLUMN_WIDTHS = {360, 240, 170, 170};
    private static final String[] COLUMN_TITLES = {"Task", "Subject", "Due", "Priority"};
    private static final Color INK = new Color(26, 26, 26);

    public BufferedImage renderSchedule(List<Task> tasks) {
        int height = MARGIN + TITLE_AREA + ROW_HEIGHT + tasks.size() * ROW_HEIGHT + MARGIN;
        BufferedImage image = new BufferedImage(IMAGE_WIDTH, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();

        g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
        g.setRenderingHint(RenderingHints.KEY_TEXT_ANTIALIASING, RenderingHints.VALUE_TEXT_ANTIALIAS_ON);

        g.setColor(Color.WHITE);
        g.fillRect(0, 0, IMAGE_WIDTH, height);

        g.setColor(INK);
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 28));
        g.drawString("My study schedule", MARGIN, MARGIN + 34);

        int y = MARGIN + TITLE_AREA;
        drawHeader(g, y);
        y += ROW_HEIGHT;

        for (Task task : tasks) {
            drawRow(g, task, y);
            y += ROW_HEIGHT;
        }

        g.dispose();
        return image;
    }

    private void drawHeader(Graphics2D g, int y) {
        g.setFont(new Font(Font.SANS_SERIF, Font.BOLD, 15));
        g.setColor(new Color(91, 107, 133));

        int x = MARGIN + 16;
        for (int i = 0; i < COLUMN_TITLES.length; i++) {
            g.drawString(COLUMN_TITLES[i], x, y + 26);
            x += COLUMN_WIDTHS[i];
        }

        g.setColor(INK);
        g.fillRect(MARGIN, y + ROW_HEIGHT - 2, IMAGE_WIDTH - 2 * MARGIN, 2);
    }

    private void drawRow(Graphics2D g, Task task, int y) {
        Color color = task.getPriority().getColor();
        int rowWidth = IMAGE_WIDTH - 2 * MARGIN;

        g.setColor(color);
        g.fillRect(MARGIN, y, rowWidth, ROW_HEIGHT - 2);
        g.setColor(color.darker().darker());
        g.fillRect(MARGIN, y, 6, ROW_HEIGHT - 2);

        String[] values = {task.getName(), task.getSubject(), task.getDateText(), task.getPriority().getLabel()};
        int x = MARGIN + 16;

        g.setColor(INK);
        for (int i = 0; i < values.length; i++) {
            int style = (i == 0 || i == 3) ? Font.BOLD : Font.PLAIN;
            g.setFont(new Font(Font.SANS_SERIF, style, 16));
            String shown = fit(values[i], g.getFontMetrics(), COLUMN_WIDTHS[i] - 24);
            g.drawString(shown, x, y + 25);
            x += COLUMN_WIDTHS[i];
        }
    }

    private String fit(String text, FontMetrics metrics, int maxWidth) {
        String clean = text.replace('\n', ' ').replace('\r', ' ');
        if (metrics.stringWidth(clean) <= maxWidth) {
            return clean;
        }

        String cut = clean;
        while (cut.length() > 1 && metrics.stringWidth(cut + "...") > maxWidth) {
            cut = cut.substring(0, cut.length() - 1);
        }
        return cut + "...";
    }

    public byte[] createPng(List<Task> tasks) throws IOException {
        BufferedImage image = renderSchedule(tasks);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "png", out)) {
            throw new IOException("PNG writer not available");
        }
        return out.toByteArray();
    }

    public byte[] createPdf(List<Task> tasks) throws IOException {
        BufferedImage image = renderSchedule(tasks);

        ByteArrayOutputStream jpegStream = new ByteArrayOutputStream();
        if (!ImageIO.write(image, "jpg", jpegStream)) {
            throw new IOException("JPEG writer not available");
        }
        byte[] jpegBytes = jpegStream.toByteArray();

        double pageWidth = 595.0;
        double pageHeight = image.getHeight() * pageWidth / image.getWidth();
        String pw = String.format(Locale.US, "%.2f", pageWidth);
        String ph = String.format(Locale.US, "%.2f", pageHeight);

        String content = "q " + pw + " 0 0 " + ph + " 0 0 cm /Im0 Do Q";

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        int[] offsets = new int[6];

        writeText(out, "%PDF-1.4\n");

        offsets[1] = out.size();
        writeText(out, "1 0 obj\n<< /Type /Catalog /Pages 2 0 R >>\nendobj\n");

        offsets[2] = out.size();
        writeText(out, "2 0 obj\n<< /Type /Pages /Kids [3 0 R] /Count 1 >>\nendobj\n");

        offsets[3] = out.size();
        writeText(out, "3 0 obj\n<< /Type /Page /Parent 2 0 R /MediaBox [0 0 " + pw + " " + ph + "] "
                + "/Resources << /XObject << /Im0 4 0 R >> >> /Contents 5 0 R >>\nendobj\n");

        offsets[4] = out.size();
        writeText(out, "4 0 obj\n<< /Type /XObject /Subtype /Image /Width " + image.getWidth()
                + " /Height " + image.getHeight()
                + " /ColorSpace /DeviceRGB /BitsPerComponent 8 /Filter /DCTDecode /Length "
                + jpegBytes.length + " >>\nstream\n");
        out.write(jpegBytes, 0, jpegBytes.length);
        writeText(out, "\nendstream\nendobj\n");

        offsets[5] = out.size();
        writeText(out, "5 0 obj\n<< /Length " + content.length() + " >>\nstream\n" + content
                + "\nendstream\nendobj\n");

        int xrefStart = out.size();
        writeText(out, "xref\n0 6\n0000000000 65535 f \n");
        for (int i = 1; i <= 5; i++) {
            writeText(out, String.format(Locale.US, "%010d 00000 n \n", offsets[i]));
        }
        writeText(out, "trailer\n<< /Size 6 /Root 1 0 R >>\nstartxref\n" + xrefStart + "\n%%EOF\n");

        return out.toByteArray();
    }

    private void writeText(ByteArrayOutputStream out, String text) {
        byte[] bytes = text.getBytes(StandardCharsets.ISO_8859_1);
        out.write(bytes, 0, bytes.length);
    }
}