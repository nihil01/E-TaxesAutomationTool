package com.bizcon.taxesautomator.utils;

import java.util.HashSet;

public interface UiModifier {

    void markAsCompleted(String rowNumber);
    void markAsFailed(String rowNumber);
    void notifyCompletion(String message, HashSet<String> asanIDs);

}
