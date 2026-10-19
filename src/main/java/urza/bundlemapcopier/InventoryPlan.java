// Like a Python module path: this class belongs to the matching package/folder.
package urza.bundlemapcopier;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.function.Predicate;

/** Detached inventory simulation. Never edits the input or exposes mutable stacks. */
// No public modifier means only code in this package can use this class.
// <T> is a generic type parameter: the caller chooses what kind of stack T is.
// final on a class means another class cannot extend (subclass) it.
final class InventoryPlan<T> {
    // A small interface describing the operations this class needs from stack objects.
    // T lets these methods accept and return the same caller-chosen stack type.
    interface Stacks<T> {
        T copy(T stack);
        int amount(T stack);
        T withAmount(T stack, int amount);
    }

    // final prevents reassignment of these fields; it does not make the referenced lists immutable.
    private final List<T> slots;
    private final Stacks<T> stacks;
    private final int protectedSlot;

    // This is the constructor: it runs when a new InventoryPlan is created.
    // List<T> means a list whose elements have type T (similar to list[T] in Python).
    InventoryPlan(List<T> source, Stacks<T> stacks, int protectedSlot) {
        // this.stacks means the field on this object; stacks alone is the constructor parameter.
        this.stacks = stacks;
        this.protectedSlot = protectedSlot;
        // new creates an object; the capacity argument reserves room but does not add elements.
        this.slots = new ArrayList<>(source.size());
        // The enhanced for loop visits each element, similar to `for stack in source` in Python.
        for (T stack : source) {
            // ?: is Java's conditional expression: condition ? value-if-true : value-if-false.
            slots.add(stack == null ? null : stacks.copy(stack));
        }
    }

    // Predicate<T> is a function-like object that tests a T and returns true or false.
    // Java puts the return type before the method name; boolean is the primitive true/false type.
    boolean consume(Predicate<T> matches, int amount) {
        // throw stops this call with an exception; new IllegalArgumentException(...) creates it.
        if (amount < 0) throw new IllegalArgumentException("Negative cost");
        // long is a larger integer type than int, useful when summing many stack amounts.
        long available = 0;
        // This traditional for loop has initializer; condition; update, separated by semicolons.
        for (int i = 0; i < slots.size(); i++) {
            T stack = slots.get(i);
            // && means AND and short-circuits: later checks run only if earlier ones pass.
            if (i != protectedSlot && stack != null && matches.test(stack)) {
                available += stacks.amount(stack);
            }
        }
        // return sends a value to the caller; false here exits before changing any slots.
        if (available < amount) return false;
        for (int i = 0; i < slots.size() && amount > 0; i++) {
            T stack = slots.get(i);
            // || means OR; ! means NOT. continue skips to the next loop iteration.
            if (i == protectedSlot || stack == null || !matches.test(stack)) continue;
            // Math.min chooses the smaller value so this stack is not over-consumed.
            int taken = Math.min(amount, stacks.amount(stack));
            int left = stacks.amount(stack) - taken;
            // set replaces a list element; the ?: expression removes empty stacks using null.
            slots.set(i, left == 0 ? null : stacks.withAmount(stack, left));
            amount -= taken;
        }
        return true;
    }

    // Return type T means one stack is returned; null is used when no matching stack exists.
    T takeOne(Predicate<T> matches) {
        // The index-based loop is useful here because each item is updated by its slot number.
        for (int i = 0; i < slots.size(); i++) {
            T stack = slots.get(i);
            // == compares primitive ints here; stack == null checks whether the reference is absent.
            if (i == protectedSlot || stack == null || !matches.test(stack)) continue;
            // Calling a method uses a dot: stacks.withAmount(...) passes the desired amount.
            T result = stacks.withAmount(stack, 1);
            int left = stacks.amount(stack) - 1;
            slots.set(i, left == 0 ? null : stacks.withAmount(stack, left));
            return result; // Returning ends the method immediately.
        }
        return null;
    }

    // boolean is used because this method reports success/failure, not the placed stack.
    boolean addUnstackable(T stack) {
        for (int i = 0; i < slots.size(); i++) {
            // A free slot is represented by null; != means "not equal to".
            if (i != protectedSlot && slots.get(i) == null) {
                slots.set(i, stacks.copy(stack));
                return true;
            }
        }
        return false;
    }

    List<T> result() {
        // Build a separate list so callers do not receive the internal mutable list.
        List<T> result = new ArrayList<>(slots.size());
        // add(...) appends an element, like list.append(...) in Python.
        for (T stack : slots) result.add(stack == null ? null : stacks.copy(stack));
        // This wrapper prevents callers from adding/removing/replacing list entries.
        return Collections.unmodifiableList(result);
    }
}
