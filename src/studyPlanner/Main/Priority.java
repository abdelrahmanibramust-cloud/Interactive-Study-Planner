package studyplanner;

import java.awt.Color;

public enum Priority {
    HIGH("High", new Color(246, 185, 179)),
    MEDIUM("Medium", new Color(251, 224, 138)),
    LOW("Low", new Color(191, 227, 184));

    private final String label;
    private final Color color;

    Priority(String label, Color color) {
        this.label = label;
        this.color = color;
    }

    public String getLabel() {
        return label;
    }

    public Color getColor() {
        return color;
    }

    @Override
    public String toString() {
        return label;
    }
}