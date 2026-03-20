package kata;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Characterization tests for LegacyCheckoutCalculator.
 *
 * These tests capture the CURRENT behavior of the implementation — including
 * quirks, integer-division truncation, and surprising interactions — so that
 * any future change to the production code is caught immediately.
 *
 * The implementation is treated as the source of truth.  Do not "fix" a
 * failing test by changing expected values without first understanding why
 * the production behavior changed.
 *
 * Formula (all integer arithmetic):
 *   discountedSubtotal = subtotal * (100 - discountPercent) / 100
 *   taxCents           = discountedSubtotal * taxPercent / 100
 *   total              = discountedSubtotal + shippingCents + taxCents
 */
class LegacyCheckoutCalculatorTest {

    private LegacyCheckoutCalculator calculator;

    @BeforeEach
    void setUp() {
        calculator = new LegacyCheckoutCalculator();
    }

    // ------------------------------------------------------------------
    // Helper — keeps test bodies concise while still being explicit.
    // ------------------------------------------------------------------
    private int calc(String customerType, int subtotalCents,
                     String country, String couponCode, boolean blackFriday) {
        return calculator.calculateTotalCents(
                new Order(customerType, subtotalCents, country, couponCode, blackFriday));
    }

    // ==================================================================
    // Customer types — all other variables held constant (IT, no coupon,
    // no Black Friday, subtotal chosen to produce a clean number).
    // ==================================================================
    @Nested
    class CustomerTypes {

        @Test
        void regular_customer_gets_no_discount() {
            // 0% discount → discounted=5000, ship=700 (IT), tax=1100 (22%)
            int total = calc("regular", 5000, "IT", null, false);
            assertEquals(6800, total);
        }

        @Test
        void new_customer_gets_no_discount() {
            // Same pricing as "regular"
            int total = calc("new", 5000, "IT", null, false);
            assertEquals(6800, total);
        }

        @Test
        void unknown_customer_type_gets_no_discount() {
            // Falls through to the else branch — treated like "regular"
            int total = calc("guest", 5000, "IT", null, false);
            assertEquals(6800, total);
        }

        @Test
        void null_customer_type_gets_no_discount() {
            // safe() converts null → "" which matches the else branch
            int total = calc(null, 5000, "IT", null, false);
            assertEquals(6800, total);
        }

        @Test
        void vip_customer_gets_15_percent_discount() {
            // 15% off → discounted=8500, ship=700 (IT)
            // QUIRK: VIP in Italy pays 20% tax (not 22%) — see tax tests
            // tax = 8500 * 20 / 100 = 1700
            int total = calc("vip", 10000, "IT", null, false);
            assertEquals(10900, total);
        }

        @Test
        void premium_customer_below_100_euros_gets_5_percent_discount() {
            // subtotal < 10000 → 5% off → discounted=9499 (int div: 9999*95/100)
            // tax = 9499 * 22 / 100 = 2089
            int total = calc("premium", 9999, "IT", null, false);
            assertEquals(12288, total);
        }

        @Test
        void premium_customer_at_100_euros_gets_10_percent_discount() {
            // subtotal >= 10000 → 10% off → discounted=9000
            // tax = 9000 * 22 / 100 = 1980
            int total = calc("premium", 10000, "IT", null, false);
            assertEquals(11680, total);
        }

        @Test
        void employee_gets_30_percent_discount() {
            // 30% off → discounted=3500, ship=700 (IT, no surcharge for IT)
            // tax = 3500 * 22 / 100 = 770
            int total = calc("employee", 5000, "IT", null, false);
            assertEquals(4970, total);
        }
    }

    // ==================================================================
    // Coupons — applied additively on top of the customer-type discount.
    // Note: the coupon block is an if/else-if chain, so only one coupon
    // can be active at a time.
    // ==================================================================
    @Nested
    class Coupons {

        @Test
        void SAVE10_adds_10_percent_when_subtotal_meets_threshold() {
            // regular(0%) + SAVE10(10%) = 10% → discounted=4500
            // tax = 4500 * 22 / 100 = 990
            int total = calc("regular", 5000, "IT", "SAVE10", false);
            assertEquals(6190, total);
        }

        @Test
        void SAVE10_does_not_apply_below_50_euro_threshold() {
            // subtotal=4999 < 5000 → coupon not applied → discountPercent=0
            // discounted=4999, tax=4999*22/100=1099
            int total = calc("regular", 4999, "IT", "SAVE10", false);
            assertEquals(6798, total);
        }

        @Test
        void VIPONLY_adds_5_percent_for_vip_customers() {
            // vip(15%) + VIPONLY(5%) = 20% → discounted=8000
            // VIP+IT tax override: 20% → tax=1600
            int total = calc("vip", 10000, "IT", "VIPONLY", false);
            assertEquals(10300, total);
        }

        @Test
        void VIPONLY_has_no_effect_for_non_vip_customers() {
            // regular(0%) + VIPONLY: customerType != "vip" → no extra discount
            int total = calc("regular", 10000, "IT", "VIPONLY", false);
            assertEquals(12900, total);
        }

        @Test
        void BULK_adds_7_percent_when_subtotal_meets_threshold() {
            // regular(0%) + BULK(7%) = 7% → discounted=18600
            // tax = 18600 * 22 / 100 = 4092
            int total = calc("regular", 20000, "IT", "BULK", false);
            assertEquals(23392, total);
        }

        @Test
        void BULK_does_not_apply_below_200_euro_threshold() {
            // subtotal=19999 < 20000 → BULK not applied → discountPercent=0
            // discounted=19999, tax=19999*22/100=4399
            int total = calc("regular", 19999, "IT", "BULK", false);
            assertEquals(25098, total);
        }

        @Test
        void FREESHIP_waives_shipping_when_discounted_subtotal_meets_threshold() {
            // regular(0%) → discounted=8000, FREESHIP + 8000 >= 8000 → ship=0
            // US tax = 8000 * 7 / 100 = 560
            int total = calc("regular", 8000, "US", "FREESHIP", false);
            assertEquals(8560, total);
        }

        @Test
        void FREESHIP_does_not_waive_shipping_when_discounted_subtotal_below_threshold() {
            // discounted=7999 < 8000 → shipping stays 1500 (US)
            // tax = 7999 * 7 / 100 = 559
            int total = calc("regular", 7999, "US", "FREESHIP", false);
            assertEquals(10058, total);
        }

        @Test
        void TAXFREE_zeroes_tax_in_non_italian_countries() {
            // regular, DE: taxPercent=19, TAXFREE + !IT → taxPercent=0
            // discounted=5000, ship=900, tax=0
            int total = calc("regular", 5000, "DE", "TAXFREE", false);
            assertEquals(5900, total);
        }

        @Test
        void TAXFREE_has_no_effect_in_Italy_quirk() {
            // QUIRK: condition is (!country.equals("IT")), so TAXFREE is silently
            // ignored when the order ships to Italy.
            // Same result as no coupon for a regular customer in IT.
            int total = calc("regular", 5000, "IT", "TAXFREE", false);
            assertEquals(6800, total);
        }

        @Test
        void unknown_coupon_code_has_no_effect() {
            int total = calc("regular", 5000, "IT", "BOGUS123", false);
            assertEquals(6800, total);
        }

        @Test
        void null_coupon_code_has_no_effect() {
            // safe() converts null → ""
            int total = calc("regular", 5000, "IT", null, false);
            assertEquals(6800, total);
        }
    }

    // ==================================================================
    // Black Friday — adds 5 % discount and a US shipping surcharge.
    // Note: employees never receive the extra 5% discount, but the US
    // surcharge (+300) is applied regardless of customer type.
    // ==================================================================
    @Nested
    class BlackFriday {

        @Test
        void black_friday_adds_5_percent_discount_for_vip_customers() {
            // vip(15%) + BF(5%) = 20% → discounted=4000
            // ship=700 (IT, VIP: 4000 < 15000 → no free ship)
            // VIP+IT tax: 20% → tax=800
            int total = calc("vip", 5000, "IT", null, true);
            assertEquals(5500, total);
        }

        @Test
        void black_friday_adds_5_percent_discount_for_regular_customers_in_Italy() {
            // regular(0%) + BF(5%) = 5% → discounted=4750
            // ship=700, tax=4750*22/100=1045
            int total = calc("regular", 5000, "IT", null, true);
            assertEquals(6495, total);
        }

        @Test
        void black_friday_does_not_add_discount_for_employee_customers() {
            // employee(30%), BF check: customerType=="employee" → skipped
            // discounted=3500, ship=700 (IT), tax=770
            int total = calc("employee", 5000, "IT", null, true);
            assertEquals(4970, total);
        }

        @Test
        void black_friday_adds_300_shipping_surcharge_in_the_US() {
            // regular(0%) + BF(5%) = 5% → discounted=4750
            // ship=1500 (US) + 300 BF surcharge = 1800
            // tax = 4750 * 7 / 100 = 332
            int total = calc("regular", 5000, "US", null, true);
            assertEquals(6882, total);
        }

        @Test
        void black_friday_US_surcharge_also_applies_to_employee_customers() {
            // QUIRK: the BF+US shipping surcharge is NOT gated on customer type.
            // Employees get no discount uplift, but still pay the +300 surcharge.
            // employee(30%) → discounted=3500
            // ship=1500 (US) + 300 (BF+US) = 1800, then employee+!IT → +500 → 2300
            // tax = 3500 * 7 / 100 = 245
            int total = calc("employee", 5000, "US", null, true);
            assertEquals(6045, total);
        }
    }

    // ==================================================================
    // Shipping — base rates per country, employee surcharge, and the
    // free-shipping thresholds (with their integer-division boundaries).
    // ==================================================================
    @Nested
    class Shipping {

        @Test
        void italy_base_shipping_is_700_cents() {
            // discounted=100, ship=700, tax=100*22/100=22
            int total = calc("regular", 100, "IT", null, false);
            assertEquals(822, total);
        }

        @Test
        void germany_base_shipping_is_900_cents() {
            // discounted=100, ship=900, tax=100*19/100=19
            int total = calc("regular", 100, "DE", null, false);
            assertEquals(1019, total);
        }

        @Test
        void usa_base_shipping_is_1500_cents() {
            // discounted=100, ship=1500, tax=100*7/100=7
            int total = calc("regular", 100, "US", null, false);
            assertEquals(1607, total);
        }

        @Test
        void unknown_country_base_shipping_is_2500_cents() {
            // discounted=100, ship=2500, tax=0 (unknown country)
            int total = calc("regular", 100, "FR", null, false);
            assertEquals(2600, total);
        }

        @Test
        void employee_outside_italy_pays_500_cent_shipping_surcharge() {
            // employee(30%) → discounted=3500, ship=900 (DE) + 500 = 1400
            // tax = 3500 * 19 / 100 = 665
            int total = calc("employee", 5000, "DE", null, false);
            assertEquals(5565, total);
        }

        @Test
        void employee_in_italy_does_not_pay_shipping_surcharge() {
            // employee(30%) → discounted=3500, ship=700 (IT, no surcharge)
            int total = calc("employee", 5000, "IT", null, false);
            assertEquals(4970, total);
        }

        @Test
        void vip_gets_free_shipping_when_discounted_subtotal_reaches_15000() {
            // vip(15%): 17648 * 85 / 100 = 1500080 / 100 = 15000 (exact)
            // ship → 0, VIP+IT tax 20% → 3000
            int total = calc("vip", 17648, "IT", null, false);
            assertEquals(18000, total);
        }

        @Test
        void vip_does_not_get_free_shipping_one_cent_below_integer_division_boundary() {
            // QUIRK (integer division): 17647 * 85 / 100 = 1499995 / 100 = 14999
            // discounted=14999 < 15000 → shipping NOT waived → ship = 700
            // tax = 14999 * 20 / 100 = 2999
            int total = calc("vip", 17647, "IT", null, false);
            assertEquals(18698, total);
        }

        @Test
        void premium_gets_free_shipping_when_discounted_subtotal_reaches_20000() {
            // premium(10%): 24000 * 90 / 100 = 21600
            // ship → 0, tax = 21600 * 22 / 100 = 4752
            int total = calc("premium", 24000, "IT", null, false);
            assertEquals(26352, total);
        }

        @Test
        void premium_does_not_get_free_shipping_one_cent_below_integer_division_boundary() {
            // QUIRK (integer division): 22222 * 90 / 100 = 1999980 / 100 = 19999
            // discounted=19999 < 20000 → shipping NOT waived → ship = 700
            // tax = 19999 * 22 / 100 = 4399
            int total = calc("premium", 22222, "IT", null, false);
            assertEquals(25098, total);
        }
    }

    // ==================================================================
    // Taxes — per-country rates and the overrides.
    // ==================================================================
    @Nested
    class Taxes {

        @Test
        void italy_applies_22_percent_tax() {
            // discounted=10000, ship=700, tax=2200
            int total = calc("regular", 10000, "IT", null, false);
            assertEquals(12900, total);
        }

        @Test
        void germany_applies_19_percent_tax() {
            // discounted=10000, ship=900, tax=1900
            int total = calc("regular", 10000, "DE", null, false);
            assertEquals(12800, total);
        }

        @Test
        void usa_applies_7_percent_tax() {
            // discounted=10000, ship=1500, tax=700
            int total = calc("regular", 10000, "US", null, false);
            assertEquals(12200, total);
        }

        @Test
        void unknown_country_applies_0_percent_tax() {
            // discounted=10000, ship=2500, tax=0
            int total = calc("regular", 10000, "FR", null, false);
            assertEquals(12500, total);
        }

        @Test
        void vip_customers_in_italy_pay_20_percent_tax_not_22_percent() {
            // QUIRK: VIP+IT overrides the standard 22% with 20%.
            // vip(15%) → discounted=8500, ship=700, tax=8500*20/100=1700
            int total = calc("vip", 10000, "IT", null, false);
            assertEquals(10900, total);
        }

        @Test
        void TAXFREE_coupon_zeroes_tax_in_Germany() {
            // regular, DE: taxPercent=19 → TAXFREE applied → taxPercent=0
            // discounted=5000, ship=900, tax=0
            int total = calc("regular", 5000, "DE", "TAXFREE", false);
            assertEquals(5900, total);
        }
    }

    // ==================================================================
    // Discount cap — discountPercent is clamped when it exceeds 40.
    // ==================================================================
    @Nested
    class DiscountCap {

        @Test
        void discount_stays_at_40_percent_when_employee_applies_SAVE10() {
            // employee(30%) + SAVE10(10%) = 40 — the cap check is (> 40),
            // so 40 is NOT clamped; this is the highest achievable discount.
            // discounted = 10000 * 60 / 100 = 6000
            // ship = 700 (IT, employee), tax = 6000 * 22 / 100 = 1320
            int total = calc("employee", 10000, "IT", "SAVE10", false);
            assertEquals(8020, total);
        }
    }

    // ==================================================================
    // Edge cases — zero subtotal, whitespace trimming, integer division.
    // ==================================================================
    @Nested
    class EdgeCases {

        @Test
        void zero_subtotal_returns_shipping_only() {
            // discounted=0, tax=0, only Italy base shipping remains
            int total = calc("regular", 0, "IT", null, false);
            assertEquals(700, total);
        }

        @Test
        void customer_type_with_surrounding_whitespace_is_trimmed_to_vip() {
            // safe() calls trim(), so " vip " is recognised as "vip"
            int total = calc(" vip ", 10000, "IT", null, false);
            assertEquals(10900, total);
        }

        @Test
        void coupon_code_with_surrounding_whitespace_is_trimmed_to_SAVE10() {
            // safe() calls trim(), so " SAVE10 " is recognised as "SAVE10"
            int total = calc("regular", 5000, "IT", " SAVE10 ", false);
            assertEquals(6190, total);
        }

        @Test
        void integer_division_truncates_discounted_subtotal_to_zero_for_very_small_amounts() {
            // vip(15%): 1 * 85 / 100 = 0 (integer truncation)
            // discountedSubtotal=0 → tax=0; ship=700 (VIP, 0 < 15000 → no free ship)
            int total = calc("vip", 1, "IT", null, false);
            assertEquals(700, total);
        }
    }

    // ==================================================================
    // Partner customer type
    // ==================================================================
    @Nested
    class PartnerCustomerType {

        @Test
        void partner_gets_12_percent_base_discount() {
            // partner(12%) → discounted=8800, ship=700 (IT, 8800 < 15000 → no free ship)
            // tax = 8800 * 22 / 100 = 1936
            int total = calc("partner", 10000, "IT", null, false);
            assertEquals(11436, total);
        }

        @Test
        void PARTNER5_adds_5_percent_for_partner_at_threshold() {
            // partner(12%) + PARTNER5(5%) = 17% → discounted = 12000 * 83 / 100 = 9960
            // ship = 700, tax = 9960 * 22 / 100 = 2191
            int total = calc("partner", 12000, "IT", "PARTNER5", false);
            assertEquals(12851, total);
        }

        @Test
        void PARTNER5_does_not_apply_below_12000_threshold() {
            // subtotal=11999 < 12000 → PARTNER5 ignored → partner(12%) only
            // discounted = 11999 * 88 / 100 = 10559
            // ship = 700, tax = 10559 * 22 / 100 = 2322
            int total = calc("partner", 11999, "IT", "PARTNER5", false);
            assertEquals(13581, total);
        }

        @Test
        void PARTNER5_has_no_effect_for_non_partner_customers() {
            // regular(0%) + PARTNER5: customerType != "partner" → coupon ignored
            // discounted = 12000, ship = 700, tax = 12000 * 22 / 100 = 2640
            int total = calc("regular", 12000, "IT", "PARTNER5", false);
            assertEquals(15340, total);
        }

        @Test
        void black_friday_adds_only_3_percent_for_partner_not_5() {
            // partner(12%) + BF(3%) = 15% → discounted = 10000 * 85 / 100 = 8500
            // ship = 700, tax = 8500 * 22 / 100 = 1870
            int total = calc("partner", 10000, "IT", null, true);
            assertEquals(11070, total);
        }

        @Test
        void partner_gets_free_shipping_when_discounted_subtotal_reaches_15000() {
            // partner(12%): 17046 * 88 / 100 = 15000 (exact)
            // ship → 0, tax = 15000 * 22 / 100 = 3300
            int total = calc("partner", 17046, "IT", null, false);
            assertEquals(18300, total);
        }

        @Test
        void partner_does_not_get_free_shipping_one_cent_below_integer_division_boundary() {
            // partner(12%): 17045 * 88 / 100 = 14999 (integer truncation)
            // 14999 < 15000 → shipping NOT waived → ship = 700
            // tax = 14999 * 22 / 100 = 3299
            int total = calc("partner", 17045, "IT", null, false);
            assertEquals(18998, total);
        }
    }
}
