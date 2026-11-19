
import java.math.BigDecimal;
import java.math.RoundingMode;

enum Currency {
    EUR(2), USD(2), JPY(0), KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0)
            throw new IllegalArgumentException("Negative amount not allowed");

        this.minor = minor;
        this.currency = currency;
    }

    static Money of(String decimal, Currency c) {
        BigDecimal amount = new BigDecimal(decimal);

        if (amount.signum() < 0)
            throw new IllegalArgumentException("Negative amount");

        amount = amount.setScale(
            c.minorUnits,
            RoundingMode.UNNECESSARY
        );

        long minor = amount
            .movePointRight(c.minorUnits)
            .longValueExact();

        return new Money(minor, c);
    }

    Money plus(Money o) {
        requireSame(o);
        return new Money(
            Math.addExact(minor, o.minor), currency
        );
    }

    Money times(Quantity q) {
        return new Money(
            Math.multiplyExact(minor, q.value()), currency
        );
    }

    Money minus(Percentage p) {
        BigDecimal discount = BigDecimal.valueOf(minor)
            .multiply(BigDecimal.valueOf(p.getBasisPoints()))
            .divide(
                BigDecimal.valueOf(10000),
                0,
                RoundingMode.HALF_EVEN
            );

        long discountAmount = discount.longValueExact();

        return new Money(minor - discountAmount, currency);
    }

    private void requireSame(Money o) {
        if (currency != o.currency)
            throw new IllegalArgumentException("Currency mismatch");
    }

    @Override
    public String toString() {
        return currency + " " +
            BigDecimal.valueOf(minor, currency.minorUnits)
                .toPlainString();
    }
}

final class Percentage {
    private final int basisPoints;

    private Percentage(int basisPoints) {
        this.basisPoints = basisPoints;
    }

    static Percentage ofBasisPoints(int bp) {
        if (bp < 0 || bp > 10000)
            throw new IllegalArgumentException(
                "Percentage must be between 0 and 100"
            );

        return new Percentage(bp);
    }
    public int getBasisPoints() {
    return basisPoints;
}
}

final class Quantity {
    private final int value;

    private Quantity(int value) {
        this.value = value;
    }

    static Quantity of(int v) {
        if (v < 1)
            throw new IllegalArgumentException(
                "Quantity must be at least 1"
            );

        return new Quantity(v);
    }

    int value() {
        return value;
    }
}

public class Main {
    public static void main(String[] args) {

        Money price = Money.of("100.00", Currency.USD);

        Percentage discount =
            Percentage.ofBasisPoints(1500);

        Quantity quantity = Quantity.of(2);

        Money discountedPrice = price.minus(discount);

        Money total = discountedPrice.times(quantity);

        System.out.println("Original Price: " + price);
        System.out.println("Discount: 15%");
        System.out.println("Discounted Price: " + discountedPrice);
        System.out.println("Quantity: " + quantity.value());
        System.out.println("Final Total: " + total);

        Money price2 = Money.of("50.00", Currency.USD);

        System.out.println("Added Money: " + price.plus(price2));

        System.out.println("\nValidation Tests:");

        try {
            Money.of("-100.00", Currency.USD);
        } catch (IllegalArgumentException e) {
            System.out.println("Negative price rejected");
        }

        try {
            Quantity.of(0);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid quantity rejected");
        }

        try {
            Percentage.ofBasisPoints(15000);
        } catch (IllegalArgumentException e) {
            System.out.println("Invalid percentage rejected");
        }

        try {
            Money eur = Money.of("100.00", Currency.EUR);
            price.plus(eur);
        } catch (IllegalArgumentException e) {
            System.out.println("Currency mismatch rejected");
        }

        try {
            Money.of("100.123", Currency.USD);
        } catch (ArithmeticException e) {
            System.out.println("Excess precision rejected");
        }
    }
}
