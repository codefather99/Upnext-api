package com.upnext.domain;

/** Another admin moved the card first. Carries the card as it truly is now so the screen can correct itself. */
public class StaleVersionException extends RuntimeException {
    private final Views.RequestView current;

    public StaleVersionException(Views.RequestView current) {
        super("Another admin already moved this card. It is shown where it is now.");
        this.current = current;
    }

    public Views.RequestView current() { return current; }
}
