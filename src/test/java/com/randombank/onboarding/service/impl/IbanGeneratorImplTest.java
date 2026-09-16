package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.service.IbanGenerator;
import org.junit.jupiter.api.RepeatedTest;

import java.math.BigInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

class IbanGeneratorImplTest {

    private final IbanGenerator ibanGenerator = new IbanGeneratorImpl();

    @RepeatedTest(50)
    void shouldGenerateValidDutchIban() {
        String iban = ibanGenerator.generateIban();

        assertEquals(18, iban.length(), "NL IBAN must be 18 characters");
        assertTrue(iban.startsWith("NL"), "IBAN must start with NL");
        assertTrue(isMod97Valid(iban), "IBAN check digits must satisfy MOD-97: " + iban);
    }

    private boolean isMod97Valid(String iban) {
        String rearranged = iban.substring(4) + iban.substring(0, 4);
        StringBuilder numeric = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            numeric.append(Character.isDigit(c) ? String.valueOf(c) : Character.getNumericValue(c));
        }
        return new BigInteger(numeric.toString()).mod(BigInteger.valueOf(97)).intValue() == 1;
    }
}

