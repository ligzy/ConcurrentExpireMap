# ConcurrentExpireMap

A Java high performance concurrent map implementation that expires entries.

## Design

`ConcurrentExpireMap` consists of a data structure to store the map entries
and a list of background `Cleaners` to remove the expired entries.

It allows any concurrent data structure implementing `ConcurrentMap`
interface to be used. User can select an appropriate data structure based
on the workload. Java `ConcurrentHashMap is a good choice in many cases.
Tune load factor and concurrency level properly.

A set of background daemons called `Cleaners` will remove the expired entries
from the map. The `Cleaners` rely on a hash table of `TreeSet` to track
expiring entries. Tune the number of cleaners.

A monotonic expiration time based on `System.nanoTimes` is recorded in the map
entry. The best possible expiration precision is achieve via the use of
Object `wait/notify`.

### Algorithm Complexity

API   |Map Operation|Cleaner Operation|Combined|
------|-------------|-----------------|--------|
get   |O(1)         |                 |O(1)    |
put   |O(1)         |O(1)             |O(1)    |
remove|O(1)         |O(1)             |O(1)    |

TODO: Please note O(1) for Cleaner operations can only be achieved if the hash
table has enough buckets, however the current code couples the number of
cleaner threads with the number of buckets, thus preventing it from scaling.

### Deadlock

Not possible because multiple locks are not held at the same time.

## Notes

### Relax Expiration Precision

If we allow expired entries to stay a little longer, we can group entries
into bins of milliseconds or even seconds, instead of nanoseconds.
This will batch more expired entries together thus allow Cleaners to run more
efficiently at the cost of extra memory consumption. Algorithm complexity may
also to reduced due to amortization.

### Maximum Size

TODO. This is useful to limit the memory usage.

### Back Pressure

If any resource is in shortage, we have to decide how to apply back pressure.

* Blocking: Callers will be blocked until the resource is available.
* Retry: Throw a designated exception. Callers can catch and retry based on
  various retry policies.

### Maximum Expiration Timeout Value

If the maximum expiration timeout value is reasonably small, we might be able
to use a much simpler data structure, e.g., a circular buffer.

## Testing

A mock implementation of `ExpireMap` is created to assist testing.

### Unit testing

- Add an entry, get() should return the value.
- And and then remove the entry, get() should return null.
- An entry added should expire: sleep long enough, get() should return null.
- Expiration of many entries at various time.
- Remove an entry expired.
- Expire an entry removed.
- Removed and then re-added entry should NOT be expired by the first timeout.

### Concurrency testing

A simple concurrency test have been created. More concurrency tests, stress
tests, capacity tests are needed.

### Performance testing

TODO