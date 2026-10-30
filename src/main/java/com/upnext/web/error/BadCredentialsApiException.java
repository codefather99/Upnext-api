package com.upnext.web.error;

public class BadCredentialsApiException extends RuntimeException {
    public BadCredentialsApiException() { super("Email or password is wrong"); }
}
