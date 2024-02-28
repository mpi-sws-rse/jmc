package org.example.checker;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ModelChecker {
    public static final Logger logger = LogManager.getLogger(ModelChecker.class);

    public boolean check() {
        logger.info("Starting checker");
        return true;
    }
}
