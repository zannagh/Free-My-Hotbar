package com.example.examplemod;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class ExampleMod {

    public static final String MOD_ID = "example-mod";
    public static final Logger LOGGER = LoggerFactory.getLogger(MOD_ID);

    public static void init() {
        LOGGER.info("Initializing {}", MOD_ID);
    }
}
