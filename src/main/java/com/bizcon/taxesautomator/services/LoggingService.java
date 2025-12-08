package com.bizcon.taxesautomator.services;

import com.bizcon.taxesautomator.utils.MessageType;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class LoggingService {

    private static final Logger logger = LogManager.getLogger(LoggingService.class);

    public static void logData(String message, MessageType logType) {
        switch (logType){
            case WARN -> logger.warn(message);
            case ERROR -> logger.error(message);
            case INFO -> logger.info(message);
        }
    }
}
