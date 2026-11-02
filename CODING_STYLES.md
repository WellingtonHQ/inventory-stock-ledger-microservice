# Code Styles

Rules for keeping this codebase readable and maintainable. Applies to all `.java` files in `rest-service/src/`, `kafka-consumer/src/`, and any future modules.

Rules are ordered for serial enforcement: structural rules that create or
move code come first, then the rules that annotate everything that now
exists, then formatting rules in the order they constrain each other, and
mechanical cleanup last. Enforce them one at a time, in this order.

---

## 1. Class Ordering

Structure classes from highest order to lowest: constants at the top, fields next, constructors, public APIs and entry points, internal helpers (`private`) below. Separate sections with comment dividers.

**Do:**
```java
public class InventoryService {

    private static final BigDecimal ZERO = BigDecimal.ZERO;
    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final DSLContext dsl;
    private final DataSource dataSource;

    // ---------------------------------------------------------------------------
    // Constructors
    // ---------------------------------------------------------------------------

    public InventoryService(DSLContext dsl, DataSource dataSource) { ... }

    // ---------------------------------------------------------------------------
    // Public API
    // ---------------------------------------------------------------------------

    public TransactionResult recordTransaction(...) { ... }

    // ---------------------------------------------------------------------------
    // Private helpers
    // ---------------------------------------------------------------------------

    private void updateSnapshot(String skuId) { ... }
}
```

---

## 2. Method Length

A method should not exceed ~100 lines. If it does, break it into smaller private methods with clear names that describe their purpose.

**Do:**
```java
public TransactionResult recordTransaction(TransactionRequest request) {
    validateRequest(request);
    TransactionRecord transaction = createTransactionRecord(request);
    persistTransaction(transaction);
    updateSnapshot(transaction.getSkuId());
    return new TransactionResult(true, transaction.getTransactionId());
}
```

**Don't:** a single 150-line method that validates, constructs, persists, updates snapshots, and sends events inline.

---

## 3. Early Returns over Nested Ifs

Use guard clauses to handle edge cases first, keeping the happy path at the shallowest indentation. Don't wrap the main logic inside an `if`.

**Do:**
```java
public TransactionResult recordTransaction(TransactionRequest request) {
    if (request == null || StringUtils.isBlank(request.getSkuId())) {
        return TransactionResult.invalid("SKU id must not be blank");
    }

    // happy path, unindented
    persistTransaction(request);
    updateSnapshot(request.getSkuId());
    return TransactionResult.success();
}
```

**Don't:**
```java
public TransactionResult recordTransaction(TransactionRequest request) {
    if (request != null && StringUtils.isNotBlank(request.getSkuId())) {  // wraps body
        persistTransaction(request);
        updateSnapshot(request.getSkuId());
        return TransactionResult.success();
    }
    return TransactionResult.invalid("SKU id must not be blank");
}
```

---

## 4. Indentation Depth

Keep nesting to a maximum of 3 levels (4 including the `method` line). If logic requires deeper indentation, extract it into a new method or use early returns with guard clauses.

**Do:**
```java
public void handleConsumerRecord(ConsumerRecord<String, String> record) {
    if (record.value() == null) {              // level 1 — guard clause
        return;
    }

    try {                                       // level 1
        processPayload(record.value());          // level 2
    } catch (JsonProcessingException e) {       // level 2
        log.error("Failed to parse record: {}", record.key(), e); // level 3
    }
}
```

**Don't:** nesting `if` → `for` → `try` → `if` in a row (5 levels deep).

---

## 5. Magic Numbers → Named Constants

Extract bare numbers that carry meaning into `private static final` or package-level constants with a name that explains their purpose. Use `BigDecimal` for monetary values, never `double`.

**Do:**
```java
private static final BigDecimal DEFAULT_UNIT_COST = BigDecimal.ZERO;
private static final int MAX_BATCH_SIZE = 500;
private static final Duration CONSUMER_POLL_TIMEOUT = Duration.ofSeconds(1);
```

**Don't:** scatter `0.0`, `500`, `1000` throughout the code without context.

---

## 6. Multi-Field Value Groups Become Records

When 3 or more related values are used as a data carrier — a method return
value, an element of a returned collection, or a value passed between
methods — define a named object (a `record`) to capture them. Positional
groupings are for pairs only; one-off local extraction of a small
computation (e.g. parsing a match) may stay separate local variables.

**Do:**
```java
public record StockMovement(
    String skuId,
    TransactionType type,
    int quantityChange
) { }

public List<StockMovement> processBatch(List<TransactionRequest> requests) { ... }
```

**Don't:**
```java
public String[] parseLine(String line) { ... }  // caller unpacks result[0], result[1], result[2]
```

---

## 7. Javadoc on Public APIs Only

Require Javadoc for public classes and public methods that coordinate behavior or expose an API. Skip them for trivial getters, setters, and single-line helpers.

**Do:**
```java
/**
 * Records a stock movement transaction and updates the corresponding SKU snapshot.
 * Persists atomically via jOOQ transaction context.
 */
public TransactionResult recordTransaction(TransactionRequest request) { ... }

// No Javadoc needed — trivial getter
public String getSkuId() { return skuId; }
```

---

## 8. Method Parameters

When a method has more than 2 parameters, or the signature exceeds the line limit, put each parameter on its own line with a trailing comma.

**Do:**
```java
public void processTransaction(
    String skuId,
    TransactionType type,
    int quantityChange,
    BigDecimal unitCost,
    Optional<String> reasonCode
) { ... }
```

**Don't:**
```java
public void processTransaction(String skuId, TransactionType type, int quantityChange, BigDecimal unitCost, Optional<String> reasonCode) { ... }
```

---

## 9. Import Ordering

Order imports in three groups separated by blank lines: standard library (`java.*`, `javax.*`), third-party packages, local modules. Sort alphabetically within each group. Use explicit imports over wildcard imports.

**Do:**
```java
import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import org.jooq.DSLContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import com.sirwellington.target.model.TransactionRequest;
import com.sirwellington.target.repository.InventoryRepository;
```

**Don't:** intermix groups or leave them in arbitrary order.

---

## 10. jOOQ Queries Over Raw SQL

Use jOOQ's fluent API for all database queries unless raw SQL is explicitly required (e.g., complex window functions). Prefer the generated table and field constants (`InventoryTransactions.*`) over string literals.

**Do:**
```java
dsl.insertInto(INVENTORY_TRANSACTIONS)
    .columns(
        INVENTORY_TRANSACTIONS.SKU_ID,
        INVENTORY_TRANSACTIONS.TRANSACTION_TYPE,
        INVENTORY_TRANSACTIONS.QUANTITY_CHANGE,
        INVENTORY_TRANSACTIONS.UNIT_COST
    )
    .values(skuId, type.name(), quantityChange, unitCost)
    .execute();
```

**Don't:**
```java
try (Connection conn = dataSource.getConnection()) {
    String sql = "INSERT INTO inventory_transactions (sku_id, transaction_type, ...) VALUES (?, ?, ...)";
    try (PreparedStatement ps = conn.prepareStatement(sql)) { ... }
}
```

---

## 11. SLF4J Parameterized Logging

Use SLF4J `{}` placeholders exclusively for log messages. Never use string concatenation or `String.format` inside log calls. Log at the appropriate level: `INFO` for business events, `DEBUG` for tracing, `ERROR` with exceptions for failures.

**Do:**
```java
log.info("Transaction recorded: id={}, sku={}", transactionId, skuId);
log.error("Failed to persist transaction for {}", skuId, exception);
```

**Don't:**
```java
log.info("Transaction recorded: id=" + transactionId + ", sku=" + skuId);       // concatenation
logger.error(String.format("Failed for %s", skuId));                             // String.format
System.out.println("debug output");                                              // System.out
```

---

## 12. Line Length Cap

Hard cap at **120 characters**. If a line exceeds it, break it — wrap strings across lines, put method arguments on separate lines, or extract to a variable.

**Do:**
```java
log.info(
    "Recording transaction: sku={}, type={}, quantity={}",
    request.getSkuId(),
    request.getTransactionType(),
    request.getQuantityChange()
);
```

**Don't:**
```java
log.info("Recording transaction: sku={}, type={}, quantity={} and here is some more explanation that pushes the line well over 120 characters", request.getSkuId(), request.getTransactionType(), request.getQuantityChange());
```

---

## 13. Alphabetical Sorting

Sort lists of equal elements alphabetically — imports, enum members, list literals, config keys, and comment enumerations — unless business logic dictates a specific order.

**Do:**
```java
public enum TransactionType {
    ADJUSTMENT_OUT,
    DAMAGED_GOODS,
    PURCHASE_RECEIPT,
    RETURN_TO_SUPPLIER,
    SALE_SHIPMENT
}
```

**Don't:**
```java
public enum TransactionType {
    SALE_SHIPMENT,
    PURCHASE_RECEIPT,
    ADJUSTMENT_OUT,
    DAMAGED_GOODS,
    RETURN_TO_SUPPLIER
}
```
