package com.jsql.view.swing.bruteforce;

import java.util.ArrayList;
import java.util.List;

public class Bruter {

    List<String> characters = new ArrayList<>();
    
    boolean found = false;
    
    int maxLength;
    int minLength;
    
    int count;
    
    long starttime;
    long endtime;
    
    int minutes;
    int seconds;
    int hours;
    int days;
    
    private static final char[] specialCharacters = {
        '~', '`', '!', '@', '#', '$', '%', '^',
        '&', '*', '(', ')', '_', '-', '+', '=', '{', '}', '[', ']', '|', '\\',
        ';', ':', '\'', '"', '<', '.', ',', '>', '/', '?', ' '
    };
    
    boolean done = false;
    boolean paused = false;

    public boolean isFound() {
        return found;
    }

    public void setPaused(boolean paused) {
        this.paused = paused;
    }

    public boolean isPaused() {
        return paused;
    }

    public void setFound(boolean found) {
        this.found = found;
    }

    public synchronized void setEndtime(long endtime) {
        this.endtime = endtime;
    }

    public int getCounter() {
        return count;
    }

    public long getRemainder() {
        return getNumberOfPossibilities() - count;
    }

    public long getNumberOfPossibilities() {
        long possibilities = 0;
        for (int i = minLength; i <= maxLength; i++) {
            possibilities += (long) Math.pow(characters.size(), i);
        }
        return possibilities;
    }

    public void addExtendedSet() {
        for (char c = (char) 0; c <= (char) 31; c++) {
            characters.add(String.valueOf(c));
        }
    }

    public void addStandardCharacterSet() {
        for (char c = (char) 32; c <= (char) 127; c++) {
            characters.add(String.valueOf(c));
        }
    }

    public void addLowerCaseLetters() {
        for (char c = 'a'; c <= 'z'; c++) {
            characters.add(String.valueOf(c));
        }
    }

    public void addDigits() {
        for (int c = 0; c <= 9; c++) {
            characters.add(String.valueOf(c));
        }
    }

    public void addUpperCaseLetters() {
        for (char c = 'A'; c <= 'Z'; c++) {
            characters.add(String.valueOf(c));
        }
    }

    public void addSpecialCharacters() {
        for (char c : specialCharacters) {
            characters.add(String.valueOf(c));
        }
    }

    public void setMaxLength(int i) {
        maxLength = i;
    }

    public void setMinLength(int i) {
        minLength = i;
    }

    public int getPerSecond() {
        int i;
        try {
            i = (int) (getCounter() / calculateTimeDifference());
        } catch (Exception ex) {
            // Ignore division by zero
            return 0;
        }
        return i;
    }

    public String calculateTimeElapsed() {
        long timeTaken = calculateTimeDifference();
        seconds = (int) timeTaken;
        if (seconds > 60) {
            minutes = seconds / 60;
            if (minutes * 60 > seconds) {
                minutes = minutes - 1;
            }

            if (minutes > 60) {
                hours = minutes / 60;
                if (hours * 60 > minutes) {
                    hours = hours - 1;
                }
            }

            if (hours > 24) {
                days = hours / 24;
                if (days * 24 > hours) {
                    days = days - 1;
                }
            }
            seconds -= (minutes * 60);
            minutes -= (hours * 60);
            hours -= (days * 24);
            days -= (hours * 24);
        }
        return "Time elapsed: " + days + "days " + hours + "h " + minutes + "min " + seconds + "s";
    }

    private long calculateTimeDifference() {
        return (long) ((endtime - starttime) * (1 * Math.pow(10, -9)));
    }

    public boolean excludeChars(String s) {
        char[] arrayChars = s.toCharArray();
        
        for (int i = 0; i < arrayChars.length; i++) {
            characters.remove(Character.toString(arrayChars[i]));
        }
        
        return characters.size() >= maxLength;
    }

    public int getMaxLength() {
        return maxLength;
    }

    public int getMinLength() {
        return minLength;
    }

    public void setIsDone(Boolean b) {
        done = b;
    }

    public boolean isDone() {
        return done;
    }
}
