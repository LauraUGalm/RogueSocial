package com.roguesocial.user;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PasswordRulesTest {

    private static final String GUESSABLE =
            "not a common password or an easy pattern, like passwordpassword or qwertyuiop";

    @Test
    void longIsEnoughWithoutSymbolsOrCapitals() {
        assertThat(PasswordRules.problems("P4xKZw3e6cPzMH9", "laura")).as("a browser's suggestion").isEmpty();
        assertThat(PasswordRules.problems("troll bait on floor three", "laura")).as("a passphrase").isEmpty();
        assertThat(PasswordRules.problems("écureuil dans la forêt", "laura")).isEmpty();
    }

    @Test
    void shortIsTooShort() {
        assertThat(PasswordRules.problems("Tr0ll-Bait-42!", "laura")).containsExactly("at least 15 characters");
    }

    @Test
    void commonPasswordsAreGuessableEvenDressedUp() {
        assertThat(PasswordRules.problems("Password1234567", "laura")).containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("!!ILoveYouILoveYou!!", "laura")).containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("Brad Bradford 2026", "laura")).containsExactly(GUESSABLE);
    }

    @Test
    void easyPatternsAreGuessable() {
        assertThat(PasswordRules.problems("aaaaaaaaaaaaaaaa", "laura")).as("one key").containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("abababababababab", "laura")).as("few keys").containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("dungeondungeon!!", "laura")).as("said twice").containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("123456789012345", "laura")).as("counting").containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("qwertyuiopasdfg", "laura")).as("keyboard").containsExactly(GUESSABLE);
        assertThat(PasswordRules.problems("onmlkjihgfedcba", "laura")).as("backwards").containsExactly(GUESSABLE);
    }

    @Test
    void theUsernameCantBeInside() {
        assertThat(PasswordRules.problems("my name is laura and I dig", "laura"))
                .containsExactly("not your username inside it");
    }

    @Test
    void bcryptsSeventyTwoByteLimitIsEnforced() {
        String words = "troll bait on floor three, a bat in the corridor, gold under the stairs!!";
        assertThat(words).hasSize(73);
        assertThat(PasswordRules.problems(words.substring(0, 72), "laura")).isEmpty();
        assertThat(PasswordRules.problems(words, "laura"))
                .containsExactly("at most 72 characters (fewer with emoji or accented letters)");
    }

    @Test
    void nothingAtAll() {
        assertThat(PasswordRules.problems(null, "laura")).containsExactly("A password is needed");
        assertThat(PasswordRules.problems("", "laura")).containsExactly("A password is needed");
    }
}
