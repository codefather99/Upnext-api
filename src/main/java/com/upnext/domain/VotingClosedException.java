package com.upnext.domain;

public class VotingClosedException extends RuntimeException {
    public VotingClosedException() { super("This request is completed, so votes can no longer change."); }
}
