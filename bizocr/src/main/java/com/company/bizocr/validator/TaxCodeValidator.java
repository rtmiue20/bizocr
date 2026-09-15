package com.company.bizocr.validator;

import java.util.regex.Pattern;

public class TaxCodeValidator {

    private static final Pattern MST_PATTERN = Pattern.compile("^[0-9]{10}(-[0-9]{3}|[0-9]{3})?$");
    private static final int[] WEIGHTS = {31, 29, 23, 19, 17, 13, 7, 5, 3};

    public static boolean isValid(String mst) {
        if (mst == null) return false;

        String cleanMst = mst.trim().replaceAll("\\s+", "");
        if (!MST_PATTERN.matcher(cleanMst).matches()) {
            return false;
        }

        String base10 = cleanMst.substring(0, 10);
        int sum = 0;
        for (int i = 0; i < 9; i++) {
            sum += Character.getNumericValue(base10.charAt(i)) * WEIGHTS[i];
        }

        int remainder = sum % 11;
        int expectedCheckDigit = 10 - remainder;
        if (expectedCheckDigit == 10) {
            expectedCheckDigit = 0;
        }

        int actualCheckDigit = Character.getNumericValue(base10.charAt(9));
        return actualCheckDigit == expectedCheckDigit;
    }
}