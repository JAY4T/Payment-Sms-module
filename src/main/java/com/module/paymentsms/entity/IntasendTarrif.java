package com.module.paymentsms.entity;

// Intasend's own name for "who pays the transaction cost" on a checkout, sent per channel as
// <channel>_tarrif (mobile_tarrif, card_tarrif, ...) when a payment is created.
//
// BUSINESS_PAYS - the collecting business (LigiOpen) absorbs the gateway fee out of its own
//                 proceeds; the customer pays only the advertised price.
// CUSTOMER_PAYS - the fee is added to what the customer pays.
//
// Intasend holds an account-level default, but sending it explicitly on every checkout makes the
// choice per-transaction (so it can be different for a shop order and a ticket order) instead of
// silently depending on whatever the dashboard happens to be set to.
public enum IntasendTarrif {

    BUSINESS_PAYS("BUSINESS-PAYS"),
    CUSTOMER_PAYS("CUSTOMER-PAYS");

    private final String value;

    IntasendTarrif(String value) {
        this.value = value;
    }

    public String getValue() {
        return value;
    }

    // Tolerates the casings an integrator is likely to send (hyphenated as Intasend documents it,
    // the enum name itself, or lowercase) plus "LIGIOPEN" as a friendlier alias for BUSINESS_PAYS,
    // since that is what LigiOpen's own config key is called.
    public static IntasendTarrif fromValue(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        String normalized = value.trim().toUpperCase().replace('_', '-');
        if ("LIGIOPEN".equals(normalized) || "BUSINESS".equals(normalized)) {
            return BUSINESS_PAYS;
        }
        for (IntasendTarrif tarrif : values()) {
            if (tarrif.value.equalsIgnoreCase(normalized) || tarrif.name().replace('_', '-').equalsIgnoreCase(normalized)) {
                return tarrif;
            }
        }
        throw new IllegalArgumentException("Unknown fee bearing mode: " + value
                + " (expected BUSINESS-PAYS, CUSTOMER-PAYS or LIGIOPEN)");
    }
}
