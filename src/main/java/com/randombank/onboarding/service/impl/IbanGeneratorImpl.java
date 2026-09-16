package com.randombank.onboarding.service.impl;

import com.randombank.onboarding.service.IbanGenerator;
import org.springframework.stereotype.Service;

import java.security.SecureRandom;
import java.util.random.RandomGenerator;

/**
 * Implementation of IbanGenerator.
 * Generates Dutch IBAN (NL format) according to IBAN standard.
 */
@Service
class IbanGeneratorImpl implements IbanGenerator {

    private static final String COUNTRY_CODE = "NL";
    private static final String BANK_CODE = "RABO";

    private static final long MIN_ACCOUNT_NUMBER = 1_000_000_000L;
    private static final long MAX_ACCOUNT_NUMBER = 9_999_999_999L;

    private final RandomGenerator random = new SecureRandom();

    @Override
    public String generateIban() {
        // Generate random 10-digit account number
        long accountNumber = generateRandomAccountNumber();

        // Build IBAN without check digits (kk = 00 initially)
        String ibanWithoutCheckDigits = COUNTRY_CODE + "00" + BANK_CODE + String.format("%010d", accountNumber);

        // Calculate check digits
        int checkDigits = calculateCheckDigits(ibanWithoutCheckDigits);

        // Build final IBAN with check digits
        return COUNTRY_CODE + String.format("%02d", checkDigits) + BANK_CODE + String.format("%010d", accountNumber);
    }

    private long generateRandomAccountNumber() {
        // Generate random 10-digit number (1000000000 to 9999999999)
        return random.nextLong(MIN_ACCOUNT_NUMBER, MAX_ACCOUNT_NUMBER + 1);
    }

    private int calculateCheckDigits(String ibanWithoutCheckDigits) {
        // Move country code and check digits to the end
        String rearranged = ibanWithoutCheckDigits.substring(4) + ibanWithoutCheckDigits.substring(0, 4);

        // Convert letters to numbers (A=10, B=11, ..., Z=35)
        StringBuilder numericString = new StringBuilder();
        for (char c : rearranged.toCharArray()) {
            if (Character.isDigit(c)) {
                numericString.append(c);
            } else {
                numericString.append(Character.getNumericValue(c));
            }
        }

        // Calculate mod 97
        int mod = 0;
        for (char c : numericString.toString().toCharArray()) {
            mod = (mod * 10 + Character.getNumericValue(c)) % 97;
        }

        // Check digits = 98 - mod
        return 98 - mod;
    }
}

