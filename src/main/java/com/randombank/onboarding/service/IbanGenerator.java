package com.randombank.onboarding.service;

import org.springframework.stereotype.Service;

/**
 * Generates Dutch IBAN (NL format) according to IBAN standard.
 * Format: NLkkBBBBxxxxxxxxxx
 * - NL: country code
 * - kk: check digits (MOD-97)
 * - BBBB: bank code (fixed as RABO for demo)
 * - xxxxxxxxxx: account number (10 digits)
 */
@Service
public class IbanGenerator {

    private static final String COUNTRY_CODE = "NL";
    private static final String BANK_CODE = "RABO";
    private static final int ACCOUNT_NUMBER_LENGTH = 10;

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
        return 1000000000L + (long) (Math.random() * 9000000000L);
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

