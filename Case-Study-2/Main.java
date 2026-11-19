
import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.*;

// Currency
enum Currency {
    EUR(2), USD(2), JPY(0), KWD(3);

    final int minorUnits;

    Currency(int minorUnits) {
        this.minorUnits = minorUnits;
    }
}

// Money value object
final class Money {
    private final long minor;
    private final Currency currency;

    private Money(long minor, Currency currency) {
        if (minor < 0) {
            throw new IllegalArgumentException("Money cannot be negative");
        }
        this.minor = minor;
        this.currency = currency;
    }

    static Money of(String amount, Currency currency) {
        BigDecimal value = new BigDecimal(amount);
        if (value.signum() < 0) {
            throw new IllegalArgumentException("Negative amount");
        }

        long minor = value.movePointRight(currency.minorUnits)
                .setScale(0, RoundingMode.UNNECESSARY)
                .longValueExact();

        return new Money(minor, currency);
    }

    static Money zero(Currency currency) {
        return new Money(0, currency);
    }

    Money plus(Money other) {
        if (currency != other.currency) {
            throw new IllegalArgumentException("Currency mismatch");
        }
        return new Money(Math.addExact(minor, other.minor), currency);
    }

    Money times(Quantity quantity) {
        return new Money(
                Math.multiplyExact(minor, quantity.value()),
                currency
        );
    }

    public String toString() {
        return currency + " " +
                BigDecimal.valueOf(minor, currency.minorUnits)
                        .toPlainString();
    }
}

// Quantity value object
final class Quantity {
    private final int value;

    private Quantity(int value) {
        if (value < 1) {
            throw new IllegalArgumentException("Quantity must be positive");
        }
        this.value = value;
    }

    static Quantity of(int value) {
        return new Quantity(value);
    }

    int value() {
        return value;
    }
}

// ID value objects
final class OrderId {
    private final String value;

    OrderId(String value) {
        this.value = value;
    }

    public String toString() {
        return value;
    }
}

final class ProductId {
    private final String value;

    ProductId(String value) {
        this.value = value;
    }

    public boolean equals(Object obj) {
        if (!(obj instanceof ProductId)) return false;
        return value.equals(((ProductId) obj).value);
    }

    public int hashCode() {
        return value.hashCode();
    }

    public String toString() {
        return value;
    }
}

// Tracking number value object
final class TrackingNumber {
    private final String value;

    TrackingNumber(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Tracking number required");
        }
        this.value = value;
    }

    public String toString() {
        return value;
    }
}

// Order line value object
final class OrderLine {
    private final ProductId productId;
    private final Money unitPrice;
    private final Quantity quantity;

    OrderLine(ProductId productId, Money unitPrice, Quantity quantity) {
        this.productId = productId;
        this.unitPrice = unitPrice;
        this.quantity = quantity;
    }

    OrderLine combine(OrderLine other) {
        if (!unitPrice.toString().equals(other.unitPrice.toString())) {
            throw new IllegalArgumentException("Product price mismatch");
        }

        return new OrderLine(
                productId,
                unitPrice,
                Quantity.of(Math.addExact(
                        quantity.value(), other.quantity.value()))
        );
    }

    Money total() {
        return unitPrice.times(quantity);
    }

    public String toString() {
        return productId + " | Quantity: " + quantity.value()
                + " | Total: " + total();
    }
}

// Payment reference
final class PaymentRef {
    private final String value;

    PaymentRef(String value) {
        if (value == null || value.trim().isEmpty()) {
            throw new IllegalArgumentException("Payment reference required");
        }
        this.value = value;
    }

    public String toString() {
        return value;
    }
}

// Shipment value object
final class Shipment {
    private final TrackingNumber trackingNumber;
    private final Instant shippedAt;

    Shipment(TrackingNumber trackingNumber, Instant shippedAt) {
        this.trackingNumber = trackingNumber;
        this.shippedAt = shippedAt;
    }

    public String toString() {
        return "Tracking: " + trackingNumber + ", Shipped At: " + shippedAt;
    }
}

// Order status
enum Status {
    DRAFT, PLACED, PAID, SHIPPED, DELIVERED, CANCELLED
}

// Order aggregate root
final class Order {
    private final OrderId id;
    private final Currency currency;

    private Status status = Status.DRAFT;

    private final Map<ProductId, OrderLine> lines =
            new LinkedHashMap<>();

    private Optional<PaymentRef> payment = Optional.empty();
    private Optional<Shipment> shipment = Optional.empty();
    private Optional<String> cancellationReason = Optional.empty();

    Order(OrderId id, Currency currency) {
        this.id = id;
        this.currency = currency;
    }

    private void requireStatus(Status expected) {
        if (status != expected) {
            throw new IllegalStateException(
                    "Operation not allowed in status: " + status
            );
        }
    }

    // Add products only in DRAFT state
    void addLine(ProductId productId, Money price, Quantity quantity) {
        requireStatus(Status.DRAFT);

        if (price == null || quantity == null || productId == null) {
            throw new IllegalArgumentException("Invalid order line");
        }

        if (!priceCurrencyMatches(price)) {
            throw new IllegalArgumentException("Order currency mismatch");
        }

        OrderLine newLine = new OrderLine(productId, price, quantity);

        lines.merge(productId, newLine, OrderLine::combine);
    }

    private boolean priceCurrencyMatches(Money price) {
        return price.toString().startsWith(currency.toString() + " ");
    }

    // Place order
    void place() {
        requireStatus(Status.DRAFT);

        if (lines.isEmpty()) {
            throw new IllegalStateException("Cannot place an empty order");
        }

        status = Status.PLACED;
    }

    // Pay for order
    void pay(PaymentRef paymentRef) {
        requireStatus(Status.PLACED);

        payment = Optional.of(paymentRef);
        status = Status.PAID;
    }

    // Ship order
    void ship(TrackingNumber trackingNumber, Instant shippedAt) {
        requireStatus(Status.PAID);

        shipment = Optional.of(new Shipment(trackingNumber, shippedAt));
        status = Status.SHIPPED;
    }

    // Deliver order
    void deliver(Instant deliveredAt) {
        requireStatus(Status.SHIPPED);
        status = Status.DELIVERED;
    }

    // Cancel order before shipment
    void cancel(String reason) {
        if (status != Status.DRAFT &&
            status != Status.PLACED &&
            status != Status.PAID) {
            throw new IllegalStateException(
                    "Order cannot be cancelled in status: " + status
            );
        }

        if (reason == null || reason.trim().isEmpty()) {
            throw new IllegalArgumentException("Cancellation reason required");
        }

        cancellationReason = Optional.of(reason);
        status = Status.CANCELLED;
    }

    // Calculate total from order lines
    Money total() {
        Money result = Money.zero(currency);

        for (OrderLine line : lines.values()) {
            result = result.plus(line.total());
        }

        return result;
    }

    List<OrderLine> lines() {
        return Collections.unmodifiableList(
                new ArrayList<>(lines.values())
        );
    }

    Status getStatus() {
        return status;
    }

    public String toString() {
        return "Order ID: " + id +
                "\nStatus: " + status +
                "\nLines: " + lines() +
                "\nTotal: " + total() +
                "\nShipment: " + shipment;
    }
}

// Main class
public class Main {
    public static void main(String[] args) {

        Order order = new Order(
                new OrderId("ORD101"),
                Currency.USD
        );

        // Add products
        order.addLine(
                new ProductId("P101"),
                Money.of("500.00", Currency.USD),
                Quantity.of(2)
        );

        order.addLine(
                new ProductId("P102"),
                Money.of("300.00", Currency.USD),
                Quantity.of(1)
        );

        // Add the same product again
        order.addLine(
                new ProductId("P101"),
                Money.of("500.00", Currency.USD),
                Quantity.of(1)
        );

        System.out.println("ORDER DETAILS");
        System.out.println(order);

        // Order lifecycle
        order.place();
        System.out.println("\nAfter placing: " + order.getStatus());

        order.pay(new PaymentRef("PAY123"));
        System.out.println("After payment: " + order.getStatus());

        order.ship(
                new TrackingNumber("TRACK123"),
                Instant.now()
        );
        System.out.println("After shipping: " + order.getStatus());

        order.deliver(Instant.now());
        System.out.println("After delivery: " + order.getStatus());

        // Test invalid cancellation
        try {
            order.cancel("Customer request");
        } catch (IllegalStateException e) {
            System.out.println("\nError: " + e.getMessage());
        }

        // Test cancellation of another order
        Order order2 = new Order(
                new OrderId("ORD102"),
                Currency.USD
        );

        order2.addLine(
                new ProductId("P103"),
                Money.of("200.00", Currency.USD),
                Quantity.of(1)
        );

        order2.cancel("Customer changed mind");

        System.out.println("\nSecond Order Status: " +
                order2.getStatus());
    }
}
