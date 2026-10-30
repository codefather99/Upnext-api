package com.upnext.domain;

public class NotFoundException extends RuntimeException {
    public NotFoundException(String what) { super(what + " not found"); }
}
