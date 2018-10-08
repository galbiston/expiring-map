# Expiring Map

Small library to provide in-memory storage which expires entries if unused for a period of time.
Size of map, duration until expiry and frequency of cleaning can all be controlled.

## Getting Started
Expiring Map can be accessed as a library using Maven etc. from Maven Central.

```
<dependency>
    <groupId>io.github.galbiston</groupId>
    <artifactId>expiring-map</artifactId>
    <version>1.0.1</version>
</dependency>
```

* Constructor: `ExpiringMap<String, String> expiringMap = new ExpiringMap<>("MyMap", 10000, 2000);`

* Start Expiry: `expiringMap.startExpiry();`

* Stop Expiry: `expiringMap.stopExpiry();`

* Put: `expiringMap.put("KeyA", "ValueA");`

* Get: `String value = expiringMap.get("KeyA");`

* Contains Key: `boolean isContained = expiringMap.containsKey("KeyA");`

* Delay between warnings the map is full: `expiringMap.setFullMapWarningInterval(10000);`
