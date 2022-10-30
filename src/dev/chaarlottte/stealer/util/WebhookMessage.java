package dev.chaarlottte.stealer.util;

import java.awt.*;
import java.util.ArrayList;
import java.util.List;

public class WebhookMessage {

    private String name, description;
    private final List<Field> fields;

    private String thumbnailUrl;

    private Color color;

    private WebhookMessage(String name, List<Field> fields, String thumbnailUrl, Color color, String description) {
        this.name = name;
        this.fields = fields;
        this.thumbnailUrl = thumbnailUrl;
        this.color = color;
        this.description = description;
    }

    public String getName() {
        return name;
    }

    public String getDescription() {
        return description;
    }

    public List<Field> getFields() {
        return fields;
    }

    public String getThumbnailUrl() {
        return thumbnailUrl;
    }

    public Color getColor() {
        return color;
    }

    public static class Builder {
        private String name, description = "blankdesc42";
        private List<Field> fields = new ArrayList<>();

        private String thumbnailUrl = "";

        private Color color = new Color(245, 169, 184);

        public Builder(String name) {
            this.name = name;
        }

        public Builder addField(String name, String value, boolean inline) {
            fields.add(new Field(name, value, inline));
            return this;
        }

        public void setThumbnailUrl(String thumbnailUrl) {
            this.thumbnailUrl = thumbnailUrl;
        }


        public void setDescription(String description) {
            this.description = description;
        }


        public WebhookMessage build() {
            return new WebhookMessage(name, fields, thumbnailUrl, color, description);
        }

        public void setColor(Color color) {
            this.color = color;
        }
    }

    public static class Field {
        private final String name;
        private final String value;
        private final boolean inline;

        public Field(String name, String value, boolean inline) {
            this.name = name;
            this.value = value;
            this.inline = inline;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public boolean isInline() {
            return inline;
        }
    }

}
