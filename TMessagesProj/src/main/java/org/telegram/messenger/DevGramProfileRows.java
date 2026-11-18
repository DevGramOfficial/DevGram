package org.telegram.messenger;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashSet;

/** Remap existing profile row identifiers together so binding, clicks and scroll targets agree. */
public final class DevGramProfileRows {
    public final int[] positions;
    public final int count;
    public DevGramProfileRows(int count, int[] movable, int[] desired, int[] hidden) {
        this(count, movable, desired, hidden, new int[0], new int[0]);
    }
    /** Native actions stay below appearance rows, even when shared media changes slots. */
    public DevGramProfileRows(int count, int[] movable, int[] desired, int[] hidden, int[] footerRows, int[] contentRows) {
        HashSet<Integer> removed = new HashSet<>(), moving = new HashSet<>();
        for (int value : hidden) if (value >= 0 && value < count) removed.add(value);
        for (int value : movable) if (value >= 0 && value < count && !removed.contains(value)) moving.add(value);
        ArrayList<Integer> order = new ArrayList<>(), selected = new ArrayList<>();
        for (int value : desired) if (moving.contains(value) && !selected.contains(value)) selected.add(value);
        for (int i = 0; i < count; i++) if (moving.contains(i) && !selected.contains(i)) selected.add(i);
        int cursor = 0;
        for (int i = 0; i < count; i++) if (!removed.contains(i)) order.add(moving.contains(i) ? selected.get(cursor++) : i);
        HashSet<Integer> footer = new HashSet<>(), content = new HashSet<>();
        for (int value : footerRows) if (value >= 0 && value < count && !removed.contains(value)) footer.add(value);
        for (int value : contentRows) if (value >= 0 && value < count && !removed.contains(value) && !footer.contains(value)) content.add(value);
        if (!footer.isEmpty()) {
            ArrayList<Integer> actions = new ArrayList<>();
            int originalPosition = order.size();
            for (int i = 0; i < order.size(); i++) if (footer.contains(order.get(i))) {
                originalPosition = Math.min(originalPosition, i);
                actions.add(order.get(i));
            }
            order.removeAll(footer);
            int position = Math.min(originalPosition, order.size());
            for (int i = 0; i < order.size(); i++) if (content.contains(order.get(i))) position = Math.max(position, i + 1);
            order.addAll(position, actions);
        }
        positions = new int[count]; Arrays.fill(positions, -1);
        for (int i = 0; i < order.size(); i++) positions[order.get(i)] = i;
        this.count = order.size();
    }
    public int row(int value) { return value >= 0 && value < positions.length ? positions[value] : -1; }
    /** Preview the same movable-slot permutation as the adapter, including fixed separators. */
    public static float[] dragOffsets(int[] heights, int[] movable, int from, int to) {
        float[] offsets = new float[heights.length];
        if (from < 0 || to < 0 || from >= movable.length || to >= movable.length || from == to) return offsets;
        for (int i = 0; i < movable.length; i++) {
            if (movable[i] < 0 || movable[i] >= heights.length || i > 0 && movable[i] <= movable[i - 1]) return offsets;
        }
        ArrayList<Integer> order = new ArrayList<>();
        for (int i = 0; i < movable.length; i++) order.add(i);
        order.add(to, order.remove(from));
        int[] destination = new int[heights.length], projected = heights.clone();
        for (int i = 0; i < destination.length; i++) destination[i] = i;
        for (int i = 0; i < movable.length; i++) {
            int original = movable[order.get(i)];
            destination[original] = movable[i];
            projected[movable[i]] = heights[original];
        }
        float[] before = new float[heights.length + 1], after = new float[heights.length + 1];
        for (int i = 0; i < heights.length; i++) {
            before[i + 1] = before[i] + Math.max(0, heights[i]);
            after[i + 1] = after[i] + Math.max(0, projected[i]);
        }
        for (int i = 0; i < heights.length; i++) offsets[i] = after[destination[i]] - before[i];
        return offsets;
    }
    public int rangeStart(int start, int end) {
        int minimum = Integer.MAX_VALUE;
        for (int i = Math.max(0, start); start >= 0 && i <= end && i < positions.length; i++) if (positions[i] >= 0) minimum = Math.min(minimum, positions[i]);
        return minimum == Integer.MAX_VALUE ? -1 : minimum;
    }
    public int rangeEnd(int start, int end) {
        int maximum = -1;
        for (int i = Math.max(0, start); start >= 0 && i <= end && i < positions.length; i++) maximum = Math.max(maximum, positions[i]);
        return maximum;
    }
}
