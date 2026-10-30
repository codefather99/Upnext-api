package com.upnext;

import org.springframework.boot.SpringApplication;

public class TestUpnextApplication {

    public static void main(String[] args) {
        SpringApplication.from(UpnextApplication::main).with(TestcontainersConfiguration.class).run(args);
    }

}
