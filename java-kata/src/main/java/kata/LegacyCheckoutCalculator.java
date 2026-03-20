package kata;

import java.util.Map;

public class LegacyCheckoutCalculator {

    private static final Map<String, Integer> SHIPPING_BY_COUNTRY = Map.of(
            "IT", 700,
            "DE", 900,
            "US", 1500
    );

    private static final Map<String, Integer> TAX_BY_COUNTRY = Map.of(
            "IT", 22,
            "DE", 19,
            "US", 7
    );

    public int calculateTotalCents(Order order) {
        PricingContext pricing = new PricingContext(
                normalizeString(order.customerType()),
                normalizeString(order.country()),
                normalizeString(order.couponCode()),
                order.subtotalCents(),
                order.blackFriday()
        );

        int totalDiscountPercent = customerTypeDiscountPercent(pricing);
        totalDiscountPercent += couponDiscountPercent(pricing);
        if (pricing.isBlackFriday()) {
            totalDiscountPercent += blackFridayDiscountPercent(pricing);
        }
        if (totalDiscountPercent > 40) {
            totalDiscountPercent = 40;
        }

        int discountedSubtotalCents = pricing.subtotalCents() * (100 - totalDiscountPercent) / 100;
        int shippingCents = calculateShippingCents(pricing, discountedSubtotalCents);
        int taxRatePercent = effectiveTaxRatePercent(pricing);

        int taxCents = discountedSubtotalCents * taxRatePercent / 100;
        int orderTotalCents = discountedSubtotalCents + shippingCents + taxCents;

        if (orderTotalCents < 0) {
            return 0;
        }
        return orderTotalCents;
    }

    private int effectiveTaxRatePercent(PricingContext pricing) {
        int taxRatePercent = TAX_BY_COUNTRY.getOrDefault(pricing.country(), 0);

        if (pricing.customerType().equals("vip") && pricing.country().equals("IT")) {
            taxRatePercent = 20;
        }

        if (pricing.coupon().equals("TAXFREE") && !pricing.country().equals("IT")) {
            taxRatePercent = 0;
        }

        return taxRatePercent;
    }

    private int calculateShippingCents(PricingContext pricing, int discountedSubtotalCents) {
        int shippingCents = SHIPPING_BY_COUNTRY.getOrDefault(pricing.country(), 2500);

        if (pricing.isBlackFriday() && pricing.country().equals("US")) {
            shippingCents += 300;
        }
        if (pricing.coupon().equals("FREESHIP") && discountedSubtotalCents >= 8000) {
            shippingCents = 0;
        }
        if (isEligibleForFreeShipping(pricing.customerType(), discountedSubtotalCents)) {
            shippingCents = 0;
        }
        if (pricing.customerType().equals("employee") && !pricing.country().equals("IT")) {
            shippingCents += 500;
        }

        return shippingCents;
    }

    private boolean isEligibleForFreeShipping(String customerType, int discountedSubtotalCents) {
        if ((customerType.equals("vip") || customerType.equals("partner")) && discountedSubtotalCents >= 15000) {
            return true;
        }
        return customerType.equals("premium") && discountedSubtotalCents >= 20000;
    }

    private int blackFridayDiscountPercent(PricingContext pricing) {
        if (pricing.customerType().equals("partner"))  return 3;
        if (pricing.customerType().equals("employee")) return 0;
        return 5;
    }

    private int couponDiscountPercent(PricingContext pricing) {
        if (pricing.coupon().equals("SAVE10") && pricing.subtotalCents() >= 5000)                         return 10;
        if (pricing.coupon().equals("VIPONLY") && pricing.customerType().equals("vip"))                   return 5;
        if (pricing.coupon().equals("BULK") && pricing.subtotalCents() >= 20000)                          return 7;
        if (pricing.coupon().equals("PARTNER5") && pricing.customerType().equals("partner")
                && pricing.subtotalCents() >= 12000)                                                      return 5;
        return 0;
    }

    private int customerTypeDiscountPercent(PricingContext pricing) {
        if (pricing.customerType().equals("vip"))      return 15;
        if (pricing.customerType().equals("premium"))  return pricing.subtotalCents() >= 10000 ? 10 : 5;
        if (pricing.customerType().equals("employee")) return 30;
        if (pricing.customerType().equals("partner"))  return 12;
        return 0;
    }

    private String normalizeString(String value) {
        return value == null ? "" : value.trim();
    }

    private record PricingContext(
            String customerType,
            String country,
            String coupon,
            int subtotalCents,
            boolean isBlackFriday
    ) {}
}
