package com.bizcon.taxesautomator.utils;

public interface UiModifier {

    void markAsCompleted(String rowNumber);
    void markAsFailed(String rowNumber);
    void notifyCompletion(String message);

}
