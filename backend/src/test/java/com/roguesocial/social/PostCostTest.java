package com.roguesocial.social;

import static org.assertj.core.api.Assertions.assertThat;

import org.junit.jupiter.api.Test;

class PostCostTest {

    @Test
    void spacesAreFree() {
        assertThat(PostCost.of("Hello World")).isEqualTo(10);
        assertThat(PostCost.of("  Hello\tWorld\n\r\n")).isEqualTo(10);
        assertThat(PostCost.of("Hello World")).as("no-break space").isEqualTo(10);
        assertThat(PostCost.of("   ")).isZero();
        assertThat(PostCost.of("")).isZero();
    }

    @Test
    void punctuationCosts() {
        assertThat(PostCost.of("Hi, you!")).isEqualTo(7);
    }

    @Test
    void anEmojiIsOneSymbol() {
        assertThat(PostCost.of("👍")).isEqualTo(1);
        assertThat(PostCost.of("👍🏽")).as("with a skin tone").isEqualTo(1);
        assertThat(PostCost.of("🇨🇦")).as("a flag is two regional letters").isEqualTo(1);
        assertThat(PostCost.of("👩‍👩‍👧")).as("a family joined by zero-width joiners").isEqualTo(1);
        assertThat(PostCost.of("❤️")).as("with an emoji presentation selector").isEqualTo(1);
        assertThat(PostCost.of("gg 👍🏽👍🏽")).isEqualTo(4);
    }

    @Test
    void anAccentedLetterIsOneSymbol() {
        assertThat(PostCost.of("café")).isEqualTo(4);
        assertThat(PostCost.of("café")).as("e followed by a combining accent").isEqualTo(4);
    }
}
