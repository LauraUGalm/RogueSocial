package com.roguesocial.social;

public class NotEnoughGoldException extends RuntimeException {

    public NotEnoughGoldException(long have, int need) {
        super("That costs " + need + " gold and you have " + have + " in the bank");
    }
}
