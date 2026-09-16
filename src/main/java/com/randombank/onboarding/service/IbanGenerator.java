package com.randombank.onboarding.service;

/**
 * Generates Dutch IBAN (NL format) according to IBAN standard.
 * Format: NLkkBBBBxxxxxxxxxx
 * - NL: country code
 * - kk: check digits (MOD-97)
 * - BBBB: bank code (fixed as RABO for demo)
 * - xxxxxxxxxx: account number (10 digits)
 */
public interface IbanGenerator {

    String generateIban();
}

