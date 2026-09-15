package com.company.bizocr.validator;

import java.util.Map;
import java.util.regex.Pattern;

public class BankAccountValidator {

    private static final Pattern GENERIC_ACCOUNT_PATTERN = Pattern.compile("^[0-9]{8,18}$");

    private static final Map<String, int[]> BANK_ACCOUNT_LENGTHS = Map.ofEntries(
            Map.entry("VCB", new int[]{10, 13}),
            Map.entry("VIETCOMBANK", new int[]{10, 13}),
            Map.entry("TCB", new int[]{14}),
            Map.entry("TECHCOMBANK", new int[]{14}),
            Map.entry("BIDV", new int[]{14}),
            Map.entry("MB", new int[]{10, 12, 13}),
            Map.entry("MBBANK", new int[]{10, 12, 13}),
            Map.entry("CTG", new int[]{12}),
            Map.entry("VIETINBANK", new int[]{12}),
            Map.entry("VBA", new int[]{13}),
            Map.entry("AGRIBANK", new int[]{13})
    );

    public static boolean isValid(String accountNumber, String bankCode) {
        if (accountNumber == null) return false;
        String cleanAcc = accountNumber.replaceAll("[^0-9]", "");

        if (!GENERIC_ACCOUNT_PATTERN.matcher(cleanAcc).matches()) {
            return false;
        }

        if (bankCode != null && !bankCode.isBlank()) {
            int[] validLengths = BANK_ACCOUNT_LENGTHS.get(bankCode.toUpperCase());
            if (validLengths != null) {
                int len = cleanAcc.length();
                for (int validLen : validLengths) {
                    if (len == validLen) return true;
                }
                return false;
            }
        }
        return true;
    }
}