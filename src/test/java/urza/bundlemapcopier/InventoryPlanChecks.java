package urza.bundlemapcopier;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/** Dependency-free checks, also run by the JUnit wrapper. */
public final class InventoryPlanChecks {
    private static final class Stack {
        final String kind;
        int amount;
        Stack(String kind, int amount) { this.kind = kind; this.amount = amount; }
    }
    private static final InventoryPlan.Stacks<Stack> OPS = new InventoryPlan.Stacks<>() {
        public Stack copy(Stack s) { return new Stack(s.kind, s.amount); }
        public int amount(Stack s) { return s.amount; }
        public Stack withAmount(Stack s, int amount) { return new Stack(s.kind, amount); }
    };

    public static void main(String[] args) {
        List<Stack> input = new ArrayList<>(Arrays.asList(
                new Stack("source", 1), new Stack("map", 32), new Stack("map", 64),
                new Stack("bundle", 1), new Stack("bundle", 1), new Stack("emerald", 8)));
        InventoryPlan<Stack> plan = new InventoryPlan<>(input, OPS, 0);
        check(plan.consume(s -> s.kind.equals("map"), 96), "split-stack map payment");
        check(plan.consume(s -> s.kind.equals("emerald"), 4), "fee payment");
        check(plan.takeOne(s -> s.kind.equals("bundle")) != null, "first bundle");
        check(plan.takeOne(s -> s.kind.equals("bundle")) != null, "second bundle");
        check(plan.addUnstackable(new Stack("copied", 1)), "first output into freed slot");
        check(plan.addUnstackable(new Stack("copied", 1)), "second output into freed slot");
        check(input.get(1).amount == 32 && input.get(5).amount == 8, "live inputs untouched");
        check(plan.result().get(5).amount == 4, "correct remaining fee");
        check(!plan.consume(s -> s.kind.equals("source"), 1), "held source protected");
        check(plan.takeOne(s -> s.kind.equals("source")) == null, "source cannot be taken");
        Stack exposed = plan.result().get(0);
        exposed.amount = 99;
        check(plan.result().get(0).amount == 1, "results do not expose internal stacks");

        InventoryPlan<Stack> shortfall = new InventoryPlan<>(input, OPS, 0);
        check(!shortfall.consume(s -> s.kind.equals("map"), 97), "insufficient resources rejected");
        check(shortfall.result().get(1).amount == 32, "failed payment is atomic within plan");
        check(shortfall.consume(s -> s.kind.equals("map"), 96), "first payment accepted");
        check(!shortfall.consume(s -> s.kind.equals("map"), 1), "same resource cannot pay twice");
        check(input.get(2).amount == 64, "discarded partial plan cannot charge player");
        check(shortfall.consume(s -> false, 0), "zero optional fee accepted");

        InventoryPlan<Stack> full = new InventoryPlan<>(input, OPS, 0);
        check(!full.addUnstackable(new Stack("copied", 1)), "full inventory rejects output");
        check(input.get(3).kind.equals("bundle"), "failed output leaves input intact");
        InventoryPlan<Stack> stackableBundles = new InventoryPlan<>(
                Arrays.asList(new Stack("source", 1), new Stack("bundle", 3), null), OPS, 0);
        check(stackableBundles.takeOne(s -> s.kind.equals("bundle")).amount == 1, "take exactly one");
        check(stackableBundles.result().get(1).amount == 2, "preserve remainder of custom stack");
        check(stackableBundles.addUnstackable(new Stack("copied", 1)), "one free output slot");
        check(!stackableBundles.addUnstackable(new Stack("copied", 1)), "never overwrite occupied slots");
        System.out.println("InventoryPlan: 22 checks passed");
    }

    private static void check(boolean condition, String message) {
        if (!condition) throw new AssertionError(message);
    }
}
