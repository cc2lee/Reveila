package com.reveila.service;

import java.util.EventObject;

import com.reveila.system.SystemComponent;

/**
 * A simple echo service that can reverse, repeat, and delay responses.
 * Its main purpose is to demonstrate service functionality and for testing.
 */
public class EchoService extends SystemComponent {

    private boolean reverse = false;
    private int repeat = 0;

    public EchoService() {
        super();
    }

    public void setReverse(boolean reverse) {
        this.reverse = reverse;
    }

    public void setRepeat(int repeat) {
        this.repeat = repeat;
    }

    public String echo(String name) {
        String textToEcho = name;
        if (this.reverse) {
            // Reverse the string using StringBuilder
            textToEcho = new StringBuilder(name).reverse().toString();
        }
        if (this.repeat > 0) {
            StringBuilder repeated = new StringBuilder();
            for (int i = 0; i < this.repeat; i++) {
                repeated.append(textToEcho);
                if (i < this.repeat - 1) {
                    repeated.append(", ");
                }
            }
            textToEcho = repeated.toString();
        }
        return textToEcho;
    }

    @Override
    public void onStart() throws Exception {
        // No initialization needed for this simple service
    }

    @Override
    public void onStop() throws Exception {
        // No long-running resources to clean up
    }

    @Override
    public void notifyEvent(EventObject evtObj) throws Exception {
        // No event handling for this simple service
    }
}